import { useParams, Link } from 'react-router-dom'; import { useQuery, useMutation } from '@tanstack/react-query';
import { apiGet, apiPost, errorMessage } from '../../api/client'; import { PageHeader, Skeleton, ErrorState, ApiUnavailable, VerifiedBadge, Button, RuledList } from '../../design/primitives';
export default function ProfilePage(){ const {id}=useParams(); const isMe = id==='me';
  const q = useQuery({ queryKey:['user',id], queryFn: ()=>apiGet<any>(isMe?'/me/profile':`/users/${id}`), retry:false });
  const activity = useQuery({ queryKey:['user-activity',id], queryFn: ()=>apiGet<any[]>(isMe?'/me/activity':`/users/${id}/activity`), retry:false });
  const follow = useMutation({ mutationFn: ()=>apiPost(`/users/${id}/follow`,{}) });
  const u = q.data||{};
  return <div className="container">{q.isPending?<Skeleton/>:q.isError?(q.error as any)?.status===404?<ApiUnavailable endpoint={isMe?'/me/profile':`/users/${id}`}/>:<ErrorState message={errorMessage(q.error)}/>:<><PageHeader eyebrow="Profile" title={u.fullName||'Profile'} lead={`${u.university||''} ${u.department?`· ${u.department}`:''} ${u.batch?`· Batch ${u.batch}`:''}`} action={u.verified?<VerifiedBadge/>:undefined} />
  <div className="card-group"><div className="row"><span>Email</span><span>{u.email||'—'}</span></div><div className="row"><span>Skills</span><span>{(u.skills||[]).join(', ')||'—'}</span></div><div className="row"><span>Projects</span><span>{(u.projects||[]).length||0}</span></div><div className="row"><span>Education</span><span>{(u.education||[]).map((e:any)=>e.institution||e.school||'').join(', ')||'—'}</span></div><div className="row"><span>Achievements</span><span>{(u.achievements||[]).join(', ')||'—'}</span></div><div className="row"><span>Links</span><span>{(u.links||[]).join(', ')||'—'}</span></div></div>
  {!isMe && <><Button onClick={()=>follow.mutate()} disabled={follow.isPending}>{follow.isSuccess?'Following':'Follow'}</Button>{follow.isError && <p role="alert" className="field-error">{errorMessage(follow.error)}</p>}</>}
  {isMe && <Link className="btn" to="/settings">Edit profile →</Link>}
  <h2 className="headline" style={{marginTop:32}}>Activity</h2>{activity.isPending?<Skeleton/>:activity.isError?<p className="secondary">Activity is not available yet.</p>:(activity.data||[]).length===0?<p className="secondary">No activity yet.</p>:<RuledList>{(activity.data||[]).map((a:any,i:number)=><li key={a.id||i}>{a.title||a.text||JSON.stringify(a)}</li>)}</RuledList>}</>}</div>;
}
