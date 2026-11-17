import { useEffect, useRef, useState } from 'react'; import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiGet, apiPost, apiDelete, errorMessage } from '../../api/client'; import { StompClient, newIdempotencyKey } from '../../api/stomp'; import { Button, EmptyState, ErrorState, PageHeader, Skeleton } from '../../design/primitives';

type Room = { id: number; publicId: string; type?: string; name?: string; memberIds?: number[]; closed?: boolean };
type ChatMessage = { id: number; publicId?: string; roomId?: number; senderId?: number | null; senderName?: string; body?: string; text?: string; createdAt?: string; read?: boolean; hasAttachment?: boolean };

/** D-052: a deleted account's messages stay, sender shown as "Deleted user" (senderId is null after deletion). */
function senderLabel(m: ChatMessage, meId: number | null): string {
  if (m.senderId == null) return 'Deleted user';
  if (meId != null && m.senderId === meId) return 'You';
  return m.senderName || 'Student';
}
/** I-9: read ticks render only on your own messages, from the server's
 *  `read` field (true only when every other member has read the message):
 *  ✓ = sent, not yet read by the other(s); ✓✓ = read. Nothing is invented
 *  client-side: a missing `read` renders as ✓. */
function readTick(m: ChatMessage, meId: number | null): string {
  if (meId == null || m.senderId !== meId) return '';
  return m.read ? '✓✓' : '✓';
}

export default function MessagesPage(){
  const [room, setRoom] = useState<Room | null>(null);
  const [text, setText] = useState('');
  const [live, setLive] = useState<'idle' | 'connecting' | 'live' | 'reconnecting'>('idle');
  const qc = useQueryClient();
  const stompRef = useRef<StompClient | null>(null);
  const markedRef = useRef('');
  const [roomSearch,setRoomSearch]=useState('');
  const [tempName,setTempName]=useState('');
  const [inviteId,setInviteId]=useState('');
  const [file,setFile]=useState<File|null>(null);

  const rooms = useQuery({ queryKey: ['rooms'], queryFn: () => apiGet<Room[]>('/rooms'), retry: false });
  const searchedRooms = useQuery({ queryKey: ['rooms-search',roomSearch], queryFn: () => apiGet<Room[]>(`/rooms/search?q=${encodeURIComponent(roomSearch)}`), enabled: roomSearch.trim().length>0, retry: false });
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
  // A chosen file is sent as a message with an attachment instead (multipart).
  const send = useMutation({
    mutationFn: async (body: string) => {
      if (file && room){
        const fd = new FormData(); fd.append('file', file); if (body) fd.append('body', body);
        return apiPost<ChatMessage>(`/rooms/${room.publicId}/attachments`, fd);
      }
      const key = newIdempotencyKey();
      const client = stompRef.current;
      if (room && client?.isConnected && client.send(`/app/rooms.${room.publicId}.send`, JSON.stringify({ body, idempotencyKey: key }))) return null;
      return apiPost<ChatMessage>(`/rooms/${room!.publicId}/messages`, { body, idempotencyKey: key });
    },
    onSuccess: (created) => {
      setText(''); setFile(null);
      if (created && room) qc.setQueryData<ChatMessage[]>(['room-messages', room.publicId], (old) => {
        const list = Array.isArray(old) ? old : [];
        if (list.some((m) => m.id === created.id)) return list;
        return [...list, created];
      });
    },
  });

  const markRead = useMutation({ mutationFn: ({ publicId, messageId }: { publicId: string; messageId: number }) => apiPost(`/rooms/${publicId}/read?messageId=${messageId}`, {}) });
  const openDeptRoom = useMutation({ mutationFn: ()=>apiPost<Room>('/rooms/department',{}), onSuccess: (r)=>{ qc.invalidateQueries({queryKey:['rooms']}); setRoom(r); } });
  const openUniRoom = useMutation({ mutationFn: ()=>apiPost<Room>('/rooms/university',{}), onSuccess: (r)=>{ qc.invalidateQueries({queryKey:['rooms']}); setRoom(r); } });
  const createTemp = useMutation({ mutationFn: ()=>apiPost<Room>('/rooms/temp',{name: tempName, memberIds: []}), onSuccess: (r)=>{ setTempName(''); qc.invalidateQueries({queryKey:['rooms']}); setRoom(r); } });
  const invite = useMutation({ mutationFn: ()=>apiPost<Room>(`/rooms/${room!.publicId}/invite`,{userId: Number(inviteId)}), onSuccess: (r)=>{ setInviteId(''); setRoom(r); qc.invalidateQueries({queryKey:['rooms']}); } });
  const removeMember = useMutation({ mutationFn: (uid:number)=>apiDelete(`/rooms/${room!.publicId}/members/${uid}`), onSuccess: ()=>qc.invalidateQueries({queryKey:['rooms']}) });
  const closeRoom = useMutation({ mutationFn: ()=>apiPost<Room>(`/rooms/${room!.publicId}/close`,{}), onSuccess: (r)=>{ setRoom(r); qc.invalidateQueries({queryKey:['rooms']}); } });
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

  const ritems = roomSearch.trim() ? (Array.isArray(searchedRooms.data) ? searchedRooms.data : []) : (Array.isArray(rooms.data) ? rooms.data : (rooms.data as any)?.items ?? []);
  const meId = typeof me.data?.id === 'number' ? me.data.id : null;
  // Remove is admin-only and never for the creator (backend: removeRoomMember
  // requires the room admin and rejects CANNOT_REMOVE_CREATOR). RoomDto
  // carries no creatorId or member roles, so the admin is inferred from the
  // first member: the server adds the room creator first, as ADMIN
  // (MessagingService.createTempRoom). If the backend ever sends a
  // creatorId, prefer it here instead of memberIds[0].
  const creatorId = (room?.memberIds?.[0] ?? null) as number | null;
  const isRoomAdmin = room?.type === 'TEMP' && meId != null && creatorId != null && meId === creatorId;
  const roomErr = rooms.error || searchedRooms.error || openDeptRoom.error || openUniRoom.error || createTemp.error || invite.error || removeMember.error || closeRoom.error;
  return <div className="container"><PageHeader eyebrow="Messages" title="Messages" lead="Department, university, private, team and temporary rooms. Messages persist on the server first, then broadcast over WebSocket (/ws, STOMP)." />
  <div style={{display:'flex',gap:8,flexWrap:'wrap',marginBottom:16}}><input aria-label="Search rooms" className="input-search" value={roomSearch} onChange={e=>setRoomSearch(e.target.value)} placeholder="Search rooms…" /><Button onClick={()=>openDeptRoom.mutate()} disabled={openDeptRoom.isPending}>Department room</Button><Button onClick={()=>openUniRoom.mutate()} disabled={openUniRoom.isPending}>University room</Button></div>
  <div style={{display:'flex',gap:8,flexWrap:'wrap',marginBottom:16}}><input aria-label="Temporary room name" className="input-search" value={tempName} onChange={e=>setTempName(e.target.value)} placeholder="New temporary room name" /><Button onClick={()=>createTemp.mutate()} disabled={!tempName.trim()||createTemp.isPending}>Create temporary room</Button></div>
  {roomErr && <p role="alert" className="field-error">{errorMessage(roomErr)}</p>}
  <div className="split"><section aria-label="Rooms">{rooms.isPending?<Skeleton/>:rooms.isError?<ErrorState message={errorMessage(rooms.error)} onRetry={()=>rooms.refetch()}/>:ritems.length===0?<EmptyState title="No rooms yet" body="Your department and university rooms appear here once you are verified."/>:<ul className="ruled-list">{ritems.map((r: Room)=><li key={r.id}><button className="btn btn-sm" onClick={()=>{ setRoom(r); setText(''); }} aria-current={room?.publicId===r.publicId}>{r.name||`Room ${r.id}`}</button><span className="secondary"> · {r.type||''}{r.closed?' · closed':''}</span></li>)}</ul>}</section>
  <section aria-label="Chat window">{!room?<EmptyState title="Select a room" body="Choose a room on the left to read and send messages."/>:msgs.isPending?<Skeleton/>:msgs.isError?<ErrorState message={errorMessage(msgs.error)} onRetry={()=>msgs.refetch()}/>:<><p className="secondary" role="status">{live==='live'?'Live — new messages appear here as they arrive.':live==='connecting'?'Connecting to live messages…':live==='reconnecting'?'Connection lost — reconnecting…':'Live messages unavailable — sending goes through the server.'}</p>
  {room.type==='TEMP' && <div style={{display:'flex',gap:8,flexWrap:'wrap',marginBottom:8,alignItems:'center'}}><input aria-label="Invite user id" className="input-search" value={inviteId} onChange={e=>setInviteId(e.target.value)} placeholder="Invite user id" style={{width:140}} /><Button onClick={()=>invite.mutate()} disabled={!inviteId.trim()||invite.isPending||room.closed}>Invite</Button><Button onClick={()=>closeRoom.mutate()} disabled={closeRoom.isPending||room.closed}>Close room</Button></div>}
  {room.type==='TEMP' && (room.memberIds||[]).length>0 && <p className="secondary">Members: {(room.memberIds||[]).map(uid=><span key={uid} style={{marginRight:8}}>{uid}{isRoomAdmin && uid!==creatorId && <button className="btn btn-sm" onClick={()=>removeMember.mutate(uid)} disabled={room.closed}>Remove</button>}</span>)}</p>}
  <ul className="ruled-list">{mitems.map((m: ChatMessage)=><li key={m.id}><strong>{senderLabel(m, meId)}</strong> {readTick(m, meId)}{m.hasAttachment?' 📎':''}<p>{m.body||m.text||''}</p></li>)}</ul>{markRead.isError && <p role="alert" className="field-error">Read receipts could not be saved. Your messages were still delivered.</p>}<form onSubmit={e=>{e.preventDefault(); if(text.trim()||file) send.mutate(text.trim());}} style={{display:'flex',gap:8,marginTop:8,flexWrap:'wrap'}}><input aria-label="Message" className="input-search" style={{flex:1}} value={text} onChange={e=>setText(e.target.value)} placeholder="Write a message…" /><input aria-label="Attach a file" type="file" onChange={e=>setFile(e.target.files?.[0]||null)} /><Button type="submit" variant="primary" disabled={send.isPending||room.closed}>Send</Button></form>{send.isError && <p role="alert" className="field-error">{errorMessage(send.error)}</p>}</>}</section></div></div>;
}
