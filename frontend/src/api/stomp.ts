/**
 * Minimal STOMP 1.2 client over the browser-native WebSocket API (no dependencies).
 *
 * Contract (verified against the backend, 2026-11-17):
 * - Native WebSocket URL: /ws/websocket (SockJS endpoint /ws, same origin, so the
 *   UL_SESSION cookie authenticates the handshake automatically).
 * - Broker: /topic + /queue, app prefix /app, user prefix /user (WebSocketConfig).
 * - Subscribe: /topic/room.{roomPublicId} — MessageDto JSON
 *   {id, publicId, roomId, senderId, body, createdAt}.
 * - Send: /app/rooms.{publicId}.send — note the DOT, not a slash — with a JSON
 *   body {body, idempotencyKey} (StompController @MessageMapping("/rooms.{publicId}.send")).
 * - Heart-beats are negotiated as 10s/10s; a lone newline frame is a heartbeat.
 * - Subscriptions survive reconnects: they are re-sent after every CONNECTED.
 * - send() returns false while the socket is down, so the caller can fall back
 *   to REST instead of losing the message (never both — no duplicate sends).
 */
export type StompStatus = 'connecting' | 'connected' | 'reconnecting' | 'closed';
export interface StompFrame { command: string; headers: Record<string, string>; body: string; }
export type StompMessageHandler = (frame: StompFrame) => void;

const HEARTBEAT_MS = 10000;
const DEAD_AFTER_MS = 25000;
const MAX_BACKOFF_MS = 30000;

function escapeHeader(v: string): string {
  return v.replace(/\\/g, '\\\\').replace(/\r/g, '\\r').replace(/\n/g, '\\n').replace(/:/g, '\\c');
}
function unescapeHeader(v: string): string {
  return v.replace(/\\c/g, ':').replace(/\\n/g, '\n').replace(/\\r/g, '\r').replace(/\\\\/g, '\\');
}
function encodeFrame(command: string, headers: Record<string, string>, body = ''): string {
  const head = Object.entries(headers).map(([k, v]) => `${k}:${escapeHeader(v)}`).join('\n');
  return `${command}\n${head}\n\n${body}\0`;
}
function parseFrame(raw: string): StompFrame {
  const lines = raw.split('\n');
  const command = (lines.shift() ?? '').trim();
  const headers: Record<string, string> = {};
  let line: string | undefined;
  while ((line = lines.shift()) !== undefined && line !== '') {
    const i = line.indexOf(':');
    if (i > 0) headers[line.slice(0, i)] = unescapeHeader(line.slice(i + 1));
  }
  return { command, headers, body: lines.join('\n') };
}

/** Idempotency key for message sends (server dedupes on it); falls back if crypto.randomUUID is unavailable. */
export function newIdempotencyKey(): string {
  const c = (globalThis as unknown as { crypto?: { randomUUID?: () => string } }).crypto;
  if (c && typeof c.randomUUID === 'function') return c.randomUUID();
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}-${Math.random().toString(36).slice(2, 10)}`;
}

export class StompClient {
  readonly url: string;
  onStatus: (s: StompStatus) => void = () => {};
  onError: (e: Event | Error) => void = () => {};
  private ws: WebSocket | null = null;
  private buffer = '';
  private connected = false;
  private wantOpen = false;
  private subSeq = 0;
  private subs = new Map<string, { destination: string; handler: StompMessageHandler }>();
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  private watchdogTimer: ReturnType<typeof setInterval> | null = null;
  private backoffMs = 1000;
  private lastFrameAt = 0;

  constructor(path = '/ws/websocket') {
    const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    this.url = `${proto}//${window.location.host}${path}`;
  }

  get isConnected(): boolean { return this.connected; }

  connect(): void {
    this.wantOpen = true;
    this.openSocket();
  }

  disconnect(): void {
    this.wantOpen = false;
    this.clearTimers();
    if (this.reconnectTimer) { clearTimeout(this.reconnectTimer); this.reconnectTimer = null; }
    if (this.ws) {
      try { this.ws.send(encodeFrame('DISCONNECT', {})); } catch { /* already closing */ }
      try { this.ws.close(); } catch { /* already closed */ }
      this.ws = null;
    }
    this.connected = false;
    this.setStatus('closed');
  }

  /** Subscribe; returns an unsubscribe function. Works before connect (sent on CONNECTED) and survives reconnects. */
  subscribe(destination: string, handler: StompMessageHandler): () => void {
    const id = `sub-${++this.subSeq}`;
    this.subs.set(id, { destination, handler });
    if (this.connected) this.sendFrame(encodeFrame('SUBSCRIBE', { id, destination }));
    let done = false;
    return () => {
      if (done) return;
      done = true;
      this.subs.delete(id);
      if (this.connected) this.sendFrame(encodeFrame('UNSUBSCRIBE', { id }));
    };
  }

  /** Send a frame. Returns false when the socket is not connected (caller falls back to REST). */
  send(destination: string, body: string, headers: Record<string, string> = {}): boolean {
    if (!this.connected) return false;
    return this.sendFrame(encodeFrame('SEND', { destination, 'content-type': 'application/json', ...headers }, body));
  }

  private setStatus(s: StompStatus): void { this.onStatus(s); }

  private openSocket(): void {
    if (!this.wantOpen || this.ws) return;
    this.setStatus(this.backoffMs > 1000 ? 'reconnecting' : 'connecting');
    let ws: WebSocket;
    try { ws = new WebSocket(this.url); } catch { this.scheduleReconnect(); return; }
    this.ws = ws;
    ws.onopen = () => {
      this.sendFrame(encodeFrame('CONNECT', { 'accept-version': '1.2', host: window.location.host, 'heart-beat': `${HEARTBEAT_MS},${HEARTBEAT_MS}` }));
    };
    ws.onmessage = (ev: MessageEvent) => {
      if (typeof ev.data !== 'string') return;
      this.lastFrameAt = Date.now();
      this.buffer += ev.data;
      this.drainBuffer();
    };
    ws.onerror = (e) => { this.onError(e); };
    ws.onclose = () => {
      if (this.ws === ws) this.ws = null;
      this.connected = false;
      this.clearTimers();
      if (this.wantOpen) { this.setStatus('reconnecting'); this.scheduleReconnect(); }
    };
  }

  private drainBuffer(): void {
    for (;;) {
      // A newline may follow a frame's NULL terminator; it is not part of a frame.
      this.buffer = this.buffer.replace(/^\n/, '');
      if (this.buffer === '') return;
      const nul = this.buffer.indexOf('\0');
      if (nul < 0) return; // partial frame — wait for the rest
      const raw = this.buffer.slice(0, nul);
      this.buffer = this.buffer.slice(nul + 1);
      if (raw.trim() === '') continue; // heartbeat
      this.handleFrame(parseFrame(raw));
    }
  }

  private handleFrame(f: StompFrame): void {
    if (f.command === 'CONNECTED') {
      this.connected = true;
      this.backoffMs = 1000;
      this.lastFrameAt = Date.now();
      this.startHeartbeat();
      // The broker drops subscriptions on reconnect: re-send them all.
      for (const [id, s] of this.subs) this.sendFrame(encodeFrame('SUBSCRIBE', { id, destination: s.destination }));
      this.setStatus('connected');
      return;
    }
    if (f.command === 'MESSAGE') {
      const sub = this.subs.get(f.headers.subscription ?? '');
      if (sub) { try { sub.handler(f); } catch { /* a bad handler must not kill the socket */ } }
      return;
    }
    if (f.command === 'ERROR') {
      this.onError(new Error(f.headers.message || f.body || 'STOMP error'));
    }
  }

  private startHeartbeat(): void {
    this.clearTimers();
    this.heartbeatTimer = setInterval(() => {
      if (this.ws && this.ws.readyState === WebSocket.OPEN) { try { this.ws.send('\n'); } catch { /* close will trigger reconnect */ } }
    }, HEARTBEAT_MS);
    this.watchdogTimer = setInterval(() => {
      if (this.connected && Date.now() - this.lastFrameAt > DEAD_AFTER_MS) {
        try { this.ws?.close(); } catch { /* ignore */ }
      }
    }, HEARTBEAT_MS);
  }

  private clearTimers(): void {
    if (this.heartbeatTimer) { clearInterval(this.heartbeatTimer); this.heartbeatTimer = null; }
    if (this.watchdogTimer) { clearInterval(this.watchdogTimer); this.watchdogTimer = null; }
  }

  private scheduleReconnect(): void {
    if (!this.wantOpen || this.reconnectTimer) return;
    const delay = this.backoffMs;
    this.backoffMs = Math.min(this.backoffMs * 2, MAX_BACKOFF_MS);
    this.reconnectTimer = setTimeout(() => { this.reconnectTimer = null; this.openSocket(); }, delay);
  }

  private sendFrame(frame: string): boolean {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return false;
    try { this.ws.send(frame); return true; } catch { return false; }
  }
}
