import { useEffect, useRef, useState } from 'react'; import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiGet, apiPost, errorMessage } from '../../api/client'; import { StompClient, newIdempotencyKey } from '../../api/stomp'; import { Button, EmptyState, ErrorState, PageHeader, Skeleton, ApiUnavailable } from '../../design/primitives';

type Room = { id: number; publicId: string; type?: string; name?: string; memberIds?: number[] };
type ChatMessage = { id: number; publicId?: string; roomId?: number; senderId?: number | null; senderName?: string; body?: string; text?: string; createdAt?: string; read?: boolean };

/** D-052: a deleted account's messages stay, sender shown as "Deleted user" (senderId is null after deletion). */
function senderLabel(m: ChatMessage, meId: number | null): string {
  if (m.senderId == null) return 'Deleted user';
  if (meId != null && m.senderId === meId) return 'You';
  return m.senderName || 'Student';
}

export default function MessagesPage(){
  const [room, setRoom] = useState<Room | null>(null);
  const [text, setText] = useState('');
  const [live, setLive] = useState<'idle' | 'connecting' | 'live' | 'reconnecting'>('idle');
  const qc = useQueryClient();
  const stompRef = useRef<StompClient | null>(null);
  const markedRef = useRef('');

  const rooms = useQuery({ queryKey: ['rooms'], queryFn: () => apiGet<Room[]>('/rooms'), retry: false });
  const me = useQuery({ queryKey: ['me'], queryFn: () => apiGet<{ id: number }>('/auth/me'), retry: false });
  const msgs = useQuery({ queryKey: ['room-messages', room?.publicId], queryFn: () => apiGet<ChatMessage[]>(`/rooms/${room!.publicId}/messages`), enabled: !!room, retry: false });

  // One STOMP client for the page lifetime; the session cookie authenticates /ws.
  useEffect(() => {
    const client = new StompClient('/ws/websocket');
    client.onStatus = (s) => setLive(s === 'connected' ? 'live' : s === 'closed' ? 'idle' : s);
    client.connect();
    stompRef.current = client;
    return () => { client.disconnect(); stompRef.current = null; };
  }, []);

  // Subscribe to the open room's topic; live messages append to the cached history (dedupe by id,
  // so the broadcast echo of our own send — and of a REST fallback send — never duplicates).
  useEffect(() => {
    if (!room) return;
    const client = stompRef.current;
    if (!client) return;
    const unsub = client.subscribe(`/topic/room.${room.publicId}`, (frame) => {
      let incoming: ChatMessage;
      try { incoming = JSON.parse(frame.body); } catch { return; }
      qc.setQueryData<ChatMessage[]>(['room-messages', room.publicId], (old) => {
        const list = Array.isArray(old) ? old : [];
        if (incoming.id != null && list.some((m) => m.id === incoming.id)) return list;
        return [...list, incoming];
      });
    });
    return unsub;
  }, [room, qc]);

  // Send over STOMP when live; REST is the fallback only while the socket is down (never both).
  const send = useMutation({
    mutationFn: async (body: string) => {
      const key = newIdempotencyKey();
      const client = stompRef.current;
      if (room && client?.isConnected && client.send(`/app/rooms.${room.publicId}.send`, JSON.stringify({ body, idempotencyKey: key }))) return null;
      return apiPost<ChatMessage>(`/rooms/${room!.publicId}/messages`, { body, idempotencyKey: key });
    },
    onSuccess: (created) => {
      setText('');
      if (created && room) qc.setQueryData<ChatMessage[]>(['room-messages', room.publicId], (old) => {
        const list = Array.isArray(old) ? old : [];
        if (list.some((m) => m.id === created.id)) return list;
        return [...list, created];
      });
    },
  });

  const markRead = useMutation({ mutationFn: ({ publicId, messageId }: { publicId: string; messageId: number }) => apiPost(`/rooms/${publicId}/read?messageId=${messageId}`, {}) });
  const mitems = Array.isArray(msgs.data) ? msgs.data : (msgs.data as any)?.items ?? [];
  // Mark the latest message read once per room (the read endpoint needs its id).
  useEffect(() => {
    if (!room || mitems.length === 0) return;
    const last = mitems[mitems.length - 1];
    const key = `${room.publicId}:${last.id}`;
    if (markedRef.current === key) return;
    markedRef.current = key;
    markRead.mutate({ publicId: room.publicId, messageId: last.id });
  }, [room, mitems, markRead]);

  const ritems = Array.isArray(rooms.data) ? rooms.data : (rooms.data as any)?.items ?? [];
  const meId = typeof me.data?.id === 'number' ? me.data.id : null;
  return <div className="container"><PageHeader eyebrow="Messages" title="Messages" lead="Department, university, private, team and temporary rooms. Messages persist on the server first, then broadcast over WebSocket (/ws, STOMP)." />
  <div className="split"><section aria-label="Rooms">{rooms.isPending?<Skeleton/>:rooms.isError?(rooms.error as any)?.status===404?<ApiUnavailable endpoint="/rooms"/>:<ErrorState message={errorMessage(rooms.error)}/>:ritems.length===0?<EmptyState title="No rooms yet" body="Your department and university rooms appear here once you are verified."/>:<ul className="ruled-list">{ritems.map((r: Room)=><li key={r.id}><button className="btn btn-sm" onClick={()=>{ setRoom(r); setText(''); }} aria-current={room?.publicId===r.publicId}>{r.name||`Room ${r.id}`}</button><span className="secondary"> · {r.type||''}</span></li>)}</ul>}</section>
  <section aria-label="Chat window">{!room?<EmptyState title="Select a room" body="Choose a room on the left to read and send messages."/>:msgs.isPending?<Skeleton/>:msgs.isError?<ErrorState message={errorMessage(msgs.error)}/>:<><p className="secondary" role="status">{live==='live'?'Live — new messages appear here as they arrive.':live==='connecting'?'Connecting to live messages…':live==='reconnecting'?'Connection lost — reconnecting…':'Live messages unavailable — sending goes through the server.'}</p><ul className="ruled-list">{mitems.map((m: ChatMessage)=><li key={m.id}><strong>{senderLabel(m, meId)}</strong> {m.read ? '✓✓' : '✓'}<p>{m.body||m.text||''}</p></li>)}</ul><form onSubmit={e=>{e.preventDefault(); if(text.trim()) send.mutate(text.trim());}} style={{display:'flex',gap:8,marginTop:8}}><input aria-label="Message" className="input-search" style={{flex:1}} value={text} onChange={e=>setText(e.target.value)} placeholder="Write a message…" /><Button type="submit" variant="primary" disabled={send.isPending}>Send</Button></form>{send.isError && <p role="alert" className="field-error">{errorMessage(send.error)}</p>}</>}</section></div></div>;
}
