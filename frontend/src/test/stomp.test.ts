import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { StompClient, newIdempotencyKey } from '../api/stomp';

/** Minimal stand-in for the browser WebSocket so the frame protocol can be driven by hand. */
class FakeWebSocket {
  static CONNECTING = 0; static OPEN = 1; static CLOSING = 2; static CLOSED = 3;
  static instances: FakeWebSocket[] = [];
  url: string;
  readyState = 0;
  sent: string[] = [];
  onopen: ((ev: Event) => void) | null = null;
  onmessage: ((ev: MessageEvent) => void) | null = null;
  onerror: ((ev: Event) => void) | null = null;
  onclose: ((ev: CloseEvent) => void) | null = null;
  constructor(url: string) { this.url = url; FakeWebSocket.instances.push(this); }
  send(data: string) { this.sent.push(data); }
  close() { this.readyState = 3; this.onclose?.(new CloseEvent('close')); }
  open() { this.readyState = 1; this.onopen?.(new Event('open')); }
  receive(data: string) { this.onmessage?.(new MessageEvent('message', { data }) as MessageEvent); }
}

describe('StompClient', () => {
  const RealWebSocket = globalThis.WebSocket;
  beforeEach(() => {
    FakeWebSocket.instances = [];
    (globalThis as unknown as { WebSocket: unknown }).WebSocket = FakeWebSocket;
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.clearAllTimers();
    vi.useRealTimers();
    (globalThis as unknown as { WebSocket: unknown }).WebSocket = RealWebSocket;
  });

  function liveClient() {
    const client = new StompClient('/ws/websocket');
    const statuses: string[] = [];
    client.onStatus = (s) => statuses.push(s);
    client.connect();
    const sock = FakeWebSocket.instances[0];
    sock.open();
    sock.receive('CONNECTED\nversion:1.2\nheart-beat:10000,10000\n\n\0');
    return { client, sock, statuses };
  }

  it('sends CONNECT on open, SUBSCRIBE after CONNECTED, and dispatches MESSAGE frames', () => {
    const client = new StompClient('/ws/websocket');
    client.connect();
    const sock = FakeWebSocket.instances[0];
    expect(sock.url).toMatch(/^ws:\/\/[^/]+\/ws\/websocket$/);
    sock.open();
    expect(sock.sent[0]).toContain('CONNECT\n');
    expect(sock.sent[0]).toContain('accept-version:1.2');
    expect(sock.sent[0]).toContain('heart-beat:10000,10000');
    const seen: unknown[] = [];
    client.subscribe('/topic/room.abc', (f) => seen.push(JSON.parse(f.body)));
    expect(sock.sent).toHaveLength(1); // not subscribed until CONNECTED
    sock.receive('CONNECTED\nversion:1.2\nheart-beat:10000,10000\n\n\0');
    expect(sock.sent[1]).toContain('SUBSCRIBE\n');
    expect(sock.sent[1]).toContain('destination:/topic/room.abc');
    sock.receive('MESSAGE\nsubscription:sub-1\nmessage-id:m1\ndestination:/topic/room.abc\n\n{"id":7,"senderId":null,"body":"hi"}\0');
    expect(seen).toEqual([{ id: 7, senderId: null, body: 'hi' }]);
    client.disconnect();
  });

  it('send() is false while down and sends a SEND frame with the dot destination when live', () => {
    const down = new StompClient('/ws/websocket');
    expect(down.send('/app/rooms.abc.send', '{}')).toBe(false);
    const { client, sock } = liveClient();
    const ok = client.send('/app/rooms.abc.send', JSON.stringify({ body: 'hello', idempotencyKey: 'k1' }));
    expect(ok).toBe(true);
    const frame = sock.sent[sock.sent.length - 1];
    expect(frame).toContain('SEND\n');
    expect(frame).toContain('destination:/app/rooms.abc.send');
    expect(frame).toContain('"body":"hello"');
    client.disconnect();
  });

  it('reconnects with backoff and re-subscribes after the socket closes', () => {
    const { client, sock } = liveClient();
    client.subscribe('/topic/room.abc', () => {});
    expect(sock.sent).toHaveLength(2); // CONNECT + SUBSCRIBE
    sock.close();
    expect(FakeWebSocket.instances).toHaveLength(1);
    vi.advanceTimersByTime(999);
    expect(FakeWebSocket.instances).toHaveLength(1);
    vi.advanceTimersByTime(1);
    expect(FakeWebSocket.instances).toHaveLength(2);
    const sock2 = FakeWebSocket.instances[1];
    sock2.open();
    expect(sock2.sent[0]).toContain('CONNECT\n');
    sock2.receive('CONNECTED\nversion:1.2\nheart-beat:10000,10000\n\n\0');
    expect(sock2.sent[1]).toContain('SUBSCRIBE\n');
    expect(sock2.sent[1]).toContain('destination:/topic/room.abc');
    client.disconnect();
  });

  it('sends heartbeats while connected', () => {
    const { client, sock } = liveClient();
    const before = sock.sent.length;
    vi.advanceTimersByTime(10000);
    expect(sock.sent.slice(before)).toEqual(['\n']);
    client.disconnect();
  });

  it('newIdempotencyKey returns distinct non-empty keys', () => {
    const a = newIdempotencyKey();
    const b = newIdempotencyKey();
    expect(typeof a).toBe('string');
    expect(a.length).toBeGreaterThan(0);
    expect(a).not.toBe(b);
  });
});
