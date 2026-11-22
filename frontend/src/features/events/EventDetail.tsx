import { useState } from 'react'; import { useParams, useNavigate } from 'react-router-dom'; import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiGet, apiPost, apiDelete, api, errorMessage } from '../../api/client'; import { Button, PageHeader, Skeleton, ErrorState, ApiUnavailable } from '../../design/primitives'; import type { EventItem } from '../../api/types';
import { formatEventDateTime, eventVisibilityLabel } from './eventFormat';
export default function EventDetail(){ const {id}=useParams(); const nav=useNavigate(); const qc=useQueryClient();
  const q = useQuery({ queryKey:['event',id], queryFn: ()=>apiGet<EventItem>(`/events/${id}`), retry:false });
  const me = useQuery({ queryKey:['me'], queryFn: ()=>apiGet<{id:number}>('/auth/me'), retry:false });
  const isOwner = me.data?.id!=null && q.data?.authorId!=null && Number(me.data.id)===Number(q.data.authorId);
  const rsvp = useMutation({ mutationFn: (s:string)=>apiPost(`/events/${id}/respond?response=${s}`,{}) });
  const teamReq = useMutation({ mutationFn: ()=>apiPost(`/events/${id}/team-request`,{}), onSuccess: ()=>qc.invalidateQueries({queryKey:['teams']}) });
  const del = useMutation({ mutationFn: ()=>apiDelete(`/events/${id}`), onSuccess: ()=>nav('/events') });
  const [editing,setEditing]=useState(false);
  const [title,setTitle]=useState(''); const [body,setBody]=useState(''); const [deadline,setDeadline]=useState('');
  const [regLink,setRegLink]=useState(''); const [location,setLocation]=useState(''); const [visibility,setVisibility]=useState('DEPARTMENT');
  const edit = useMutation({ mutationFn: ()=>api(`/events/${id}`,{method:'PATCH', body: JSON.stringify({title: title||undefined, body: body||undefined, deadline: deadline?`${deadline}T23:59:59`:undefined, regLink: regLink||undefined, location: location||undefined, visibility: visibility==='HUB'?'DEPARTMENT_HUB':visibility})}), onSuccess: ()=>{ setEditing(false); qc.invalidateQueries({queryKey:['event',id]}); } });
  const startEdit=()=>{ setTitle(q.data?.title||''); setBody(q.data?.body||''); setDeadline((q.data?.eventDeadline||'').slice(0,10)); setRegLink(q.data?.regLink||''); setLocation(q.data?.location||''); setVisibility(q.data?.visibility||'DEPARTMENT'); setEditing(true); };
  return <div className="container">{q.isPending?<Skeleton/>:q.isError?(q.error as any)?.status===404?<ApiUnavailable endpoint={`/events/${id}`} onRetry={()=>q.refetch()}/>:<ErrorState message={errorMessage(q.error)} onRetry={()=>q.refetch()}/>:<><PageHeader eyebrow="Event" title={q.data?.title||'Event'} lead={q.data?.body||''} />
  <p className="secondary">Deadline: {formatEventDateTime(q.data?.eventDeadline||q.data?.deadline)||'Not set'} · Visibility: {eventVisibilityLabel(q.data?.visibility)}{q.data?.location?` · ${q.data.location}`:''}{q.data?.regLink?<> · <a href={q.data.regLink}>External registration</a></>:null}</p>
  <div style={{display:'flex',gap:8,flexWrap:'wrap'}}><Button onClick={()=>rsvp.mutate('INTERESTED')}>Interested</Button><Button variant="primary" onClick={()=>rsvp.mutate('GOING')}>Going</Button><Button onClick={()=>teamReq.mutate()} disabled={teamReq.isPending}>Request a team</Button>
  {isOwner && <><Button onClick={startEdit}>Edit event</Button><Button onClick={()=>{ if(confirm('Delete this event?')) del.mutate(); }} disabled={del.isPending}>Delete event</Button></>}</div>
  {(rsvp.isError||teamReq.isError||del.isError||edit.isError) && <p role="alert" className="field-error">{errorMessage(rsvp.error||teamReq.error||del.error||edit.error)}</p>}
  {teamReq.isSuccess && <p role="status">Team created for this event — find it on the Teams page.</p>}
  {editing && <section className="card-group" style={{marginTop:24}}><h2 className="title">Edit event</h2>
  <label htmlFor="ed-title">Title</label><input id="ed-title" className="input-search" value={title} onChange={e=>setTitle(e.target.value)} style={{width:'100%',marginBottom:8}} />
  <label htmlFor="ed-body">Description</label><textarea id="ed-body" className="input-search" value={body} onChange={e=>setBody(e.target.value)} rows={3} style={{width:'100%',marginBottom:8}} />
  <label htmlFor="ed-vis">Visibility</label><select id="ed-vis" value={visibility} onChange={e=>setVisibility(e.target.value)} className="input-search" style={{marginBottom:8}}>{['DEPARTMENT','UNIVERSITY','HUB','NATIONAL'].map(v=><option key={v}>{v}</option>)}</select>
  <label htmlFor="ed-deadline">Deadline</label><input id="ed-deadline" type="date" className="input-search" value={deadline} onChange={e=>setDeadline(e.target.value)} style={{marginBottom:8}} />
  <label htmlFor="ed-loc">Location</label><input id="ed-loc" className="input-search" value={location} onChange={e=>setLocation(e.target.value)} style={{width:'100%',marginBottom:8}} />
  <label htmlFor="ed-reg">Registration link</label><input id="ed-reg" className="input-search" value={regLink} onChange={e=>setRegLink(e.target.value)} style={{width:'100%',marginBottom:8}} />
  <div style={{display:'flex',gap:8}}><Button variant="primary" onClick={()=>edit.mutate()} disabled={edit.isPending}>Save changes</Button><Button onClick={()=>setEditing(false)}>Cancel</Button></div></section>}</>}</div>;
}
