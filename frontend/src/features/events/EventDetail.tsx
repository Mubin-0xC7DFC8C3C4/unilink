import { useState } from 'react'; import { useParams } from 'react-router-dom'; import { useQuery, useMutation } from '@tanstack/react-query';
import { apiGet, apiPost, errorMessage } from '../../api/client'; import { Button, PageHeader, Skeleton, ErrorState, ApiUnavailable } from '../../design/primitives';
export default function EventDetail(){ const {id}=useParams(); const [looking,setLooking]=useState(false);
  const q = useQuery({ queryKey:['event',id], queryFn: ()=>apiGet<any>(`/events/${id}`), retry:false });
  const rsvp = useMutation({ mutationFn: (s:string)=>apiPost(`/events/${id}/rsvp`,{status:s}) });
  const teamReq = useMutation({ mutationFn: ()=>apiPost(`/events/${id}/team-request`,{lookingForTeammates:looking}) });
  return <div className="container">{q.isPending?<Skeleton/>:q.isError?(q.error as any)?.status===404?<ApiUnavailable endpoint={`/events/${id}`}/>:<ErrorState message={errorMessage(q.error)}/>:<><PageHeader eyebrow={`Event · ${q.data?.type||'Event'}`} title={q.data?.title||'Event'} lead={q.data?.description||''} />
  <p className="secondary">Deadline: {q.data?.deadline||'Not set'} · Visibility: {q.data?.visibility||'—'}{q.data?.regLink?<> · <a href={q.data.regLink}>External registration</a></>:null}{q.data?.prizes?` · Prizes: ${q.data.prizes}`:''}{q.data?.eligibility?` · Eligibility: ${q.data.eligibility}`:''}</p>
  <div style={{display:'flex',gap:8,flexWrap:'wrap'}}><Button onClick={()=>rsvp.mutate('INTERESTED')}>Interested</Button><Button variant="primary" onClick={()=>rsvp.mutate('GOING')}>Going</Button><Button aria-pressed={looking} onClick={()=>setLooking(v=>!v)}>{looking?'Looking for teammates: on':'Looking for teammates: off'}</Button><Button onClick={()=>teamReq.mutate()} disabled={teamReq.isPending}>Request a team</Button></div>
  {(rsvp.isError||teamReq.isError) && <p role="alert" className="field-error">{errorMessage(rsvp.error||teamReq.error)}</p>}{teamReq.isSuccess && <p role="status">Team request sent.</p>}</>}</div>;
}
