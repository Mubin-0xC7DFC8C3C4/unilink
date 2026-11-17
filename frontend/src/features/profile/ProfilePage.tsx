import { useState } from 'react'; import { useParams, Link } from 'react-router-dom'; import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiGet, apiPost, apiDelete, api, errorMessage } from '../../api/client'; import { PageHeader, Skeleton, ErrorState, ApiUnavailable, VerifiedBadge, Button, RuledList } from '../../design/primitives';
import type { UserProfile, ProjectItem, EducationItem, AchievementItem, Post } from '../../api/types';
/** Profile reads use /api/profiles/… (GET /profiles/me, GET /profiles/{id}),
 *  follow is POST/DELETE /profiles/{id}/follow, activity is
 *  GET /profiles/{id}/activity, and the portfolio lists come from
 *  GET /profiles/{id}/projects|education|achievements. */
function PortfolioSection({userId, isMe}: {userId: string|number; isMe: boolean}){
  const qc=useQueryClient();
  const projects = useQuery({ queryKey:['projects',userId], queryFn: ()=>apiGet<ProjectItem[]>(`/profiles/${userId}/projects`), retry:false });
  const education = useQuery({ queryKey:['education',userId], queryFn: ()=>apiGet<EducationItem[]>(`/profiles/${userId}/education`), retry:false });
  const achievements = useQuery({ queryKey:['achievements',userId], queryFn: ()=>apiGet<AchievementItem[]>(`/profiles/${userId}/achievements`), retry:false });
  const [kind,setKind]=useState<'project'|'education'|'achievement'>('project');
  const [title,setTitle]=useState(''); const [desc,setDesc]=useState(''); const [url,setUrl]=useState(''); const [year,setYear]=useState('');
  const [editId,setEditId]=useState<string|number|null>(null);
  const refresh=()=>{ qc.invalidateQueries({queryKey:['projects',userId]}); qc.invalidateQueries({queryKey:['education',userId]}); qc.invalidateQueries({queryKey:['achievements',userId]}); };
  const save = useMutation({ mutationFn: ()=>{
    const path = kind==='project'?'/profiles/me/projects':kind==='education'?'/profiles/me/education':'/profiles/me/achievements';
    const body = kind==='project'?{title, description: desc, url}:kind==='education'?{institution: title, degree: desc, year: year?Number(year):undefined}:{title, description: desc, year: year?Number(year):undefined};
    return editId ? api(`${path}/${editId}`,{method:'PUT', body: JSON.stringify(body)}) : apiPost(path, body);
  }, onSuccess: ()=>{ refresh(); setTitle(''); setDesc(''); setUrl(''); setYear(''); setEditId(null); } });
  const del = useMutation({ mutationFn: ({k,id}:{k:string;id:string|number})=>apiDelete(`/profiles/me/${k}/${id}`), onSuccess: refresh });
  const startEdit=(k:'project'|'education'|'achievement', item:any)=>{ setKind(k); setEditId(item.id);
    if(k==='project'){ setTitle(item.title||''); setDesc(item.description||''); setUrl(item.url||''); }
    if(k==='education'){ setTitle(item.institution||''); setDesc(item.degree||''); setYear(item.year?String(item.year):''); }
    if(k==='achievement'){ setTitle(item.title||''); setDesc(item.description||''); setYear(item.year?String(item.year):''); } };
  const err = save.error || del.error;
  return <><h2 className="headline" style={{marginTop:32}}>Portfolio</h2>
  <h3 className="title">Projects</h3>{projects.isPending?<Skeleton/>:projects.isError?<ErrorState message={errorMessage(projects.error)}/>:((projects.data||[]).length===0?<p className="secondary">No projects yet.</p>:<RuledList>{(projects.data||[]).map(p=><li key={p.id}><strong>{p.title}</strong><p className="secondary">{p.description||''} {p.url?<>· <a href={p.url}>{p.url}</a></>:null}</p>{isMe && <><Button onClick={()=>startEdit('project',p)}>Edit</Button>{' '}<Button onClick={()=>del.mutate({k:'projects',id:p.id})}>Delete</Button></>}</li>)}</RuledList>)}
  <h3 className="title">Education</h3>{education.isPending?<Skeleton/>:education.isError?<ErrorState message={errorMessage(education.error)}/>:((education.data||[]).length===0?<p className="secondary">No education entries yet.</p>:<RuledList>{(education.data||[]).map(e=><li key={e.id}><strong>{e.institution||''}</strong><p className="secondary">{e.degree||''}{e.year?` · ${e.year}`:''}</p>{isMe && <><Button onClick={()=>startEdit('education',e)}>Edit</Button>{' '}<Button onClick={()=>del.mutate({k:'education',id:e.id})}>Delete</Button></>}</li>)}</RuledList>)}
  <h3 className="title">Achievements</h3>{achievements.isPending?<Skeleton/>:achievements.isError?<ErrorState message={errorMessage(achievements.error)}/>:((achievements.data||[]).length===0?<p className="secondary">No achievements yet.</p>:<RuledList>{(achievements.data||[]).map(a=><li key={a.id}><strong>{a.title}</strong><p className="secondary">{a.description||''}{a.year?` · ${a.year}`:''}</p>{isMe && <><Button onClick={()=>startEdit('achievement',a)}>Edit</Button>{' '}<Button onClick={()=>del.mutate({k:'achievements',id:a.id})}>Delete</Button></>}</li>)}</RuledList>)}
  {isMe && <section className="card-group" style={{marginTop:16}}><h3 className="title">{editId?'Edit':'Add'} portfolio item</h3>
  <div style={{display:'flex',gap:8,marginBottom:8}}>{(['project','education','achievement'] as const).map(k=><button key={k} className={kind===k?'btn btn-primary btn-sm':'btn btn-sm'} onClick={()=>{setKind(k); setEditId(null);}}>{k}</button>)}</div>
  <label htmlFor="pf-title">{kind==='education'?'Institution':'Title'}</label><input id="pf-title" className="input-search" value={title} onChange={e=>setTitle(e.target.value)} style={{width:'100%',marginBottom:8}} />
  <label htmlFor="pf-desc">{kind==='project'?'Description':kind==='education'?'Degree':'Description'}</label><input id="pf-desc" className="input-search" value={desc} onChange={e=>setDesc(e.target.value)} style={{width:'100%',marginBottom:8}} />
  {kind==='project' && <><label htmlFor="pf-url">Link</label><input id="pf-url" className="input-search" value={url} onChange={e=>setUrl(e.target.value)} style={{width:'100%',marginBottom:8}} /></>}
  {kind!=='project' && <><label htmlFor="pf-year">Year</label><input id="pf-year" type="number" className="input-search" value={year} onChange={e=>setYear(e.target.value)} style={{marginBottom:8}} /></>}
  {err && <p role="alert" className="field-error">{errorMessage(err)}</p>}
  <div style={{display:'flex',gap:8}}><Button variant="primary" onClick={()=>save.mutate()} disabled={!title.trim()||save.isPending}>{editId?'Save changes':'Add'}</Button>{editId && <Button onClick={()=>{setEditId(null); setTitle(''); setDesc(''); setUrl(''); setYear('');}}>Cancel</Button>}</div></section>}</>;
}
export default function ProfilePage(){ const {id}=useParams(); const isMe = id==='me';
  const qc=useQueryClient();
  const q = useQuery({ queryKey:['user',id], queryFn: ()=>apiGet<UserProfile>(isMe?'/profiles/me':`/profiles/${id}`), retry:false });
  const uid = isMe ? (q.data?.userId ?? q.data?.id) : id;
  const activity = useQuery({ queryKey:['user-activity',id], queryFn: ()=>apiGet<Post[]>(isMe?'/profiles/me/activity':`/profiles/${id}/activity`), retry:false, enabled: !!uid });
  const follow = useMutation({ mutationFn: ()=>apiPost(`/profiles/${id}/follow`,{}), onSuccess: ()=>qc.invalidateQueries({queryKey:['user',id]}) });
  const unfollow = useMutation({ mutationFn: ()=>apiDelete(`/profiles/${id}/follow`), onSuccess: ()=>qc.invalidateQueries({queryKey:['user',id]}) });
  const u = q.data||{} as UserProfile;
  return <div className="container">{q.isPending?<Skeleton/>:q.isError?(q.error as any)?.status===404?<ApiUnavailable endpoint={isMe?'/profiles/me':`/profiles/${id}`} onRetry={()=>q.refetch()}/>:<ErrorState message={errorMessage(q.error)} onRetry={()=>q.refetch()}/>:<><PageHeader eyebrow="Profile" title={u.fullName||'Profile'} lead={`${u.universityName||u.university||''} ${u.departmentName||u.department?`· ${u.departmentName||u.department}`:''} ${u.batchLabel||u.batch?`· Batch ${u.batchLabel||u.batch}`:''}`} action={u.state==='VERIFIED'||u.verified?<VerifiedBadge/>:undefined} />
  <div className="card-group"><div className="row"><span>Email</span><span>{u.email||'—'}</span></div><div className="row"><span>Headline</span><span>{u.headline||'—'}</span></div><div className="row"><span>Bio</span><span>{u.bio||'—'}</span></div><div className="row"><span>Skills</span><span>{(u.skills||[]).join(', ')||'—'}</span></div><div className="row"><span>Sectors</span><span>{(u.sectors||[]).join(', ')||'—'}</span></div><div className="row"><span>Followers</span><span>{u.followers??'—'}</span></div><div className="row"><span>Following</span><span>{u.following??'—'}</span></div><div className="row"><span>Links</span><span>{u.links||'—'}</span></div></div>
  {!isMe && <><Button onClick={()=>follow.mutate()} disabled={follow.isPending}>{follow.isSuccess?'Following':'Follow'}</Button>{' '}<Button onClick={()=>unfollow.mutate()} disabled={unfollow.isPending}>Unfollow</Button>{(follow.isError||unfollow.isError) && <p role="alert" className="field-error">{errorMessage(follow.error||unfollow.error)}</p>}</>}
  {isMe && <Link className="btn" to="/settings">Edit profile →</Link>}
  {uid!=null && <PortfolioSection userId={uid} isMe={isMe} />}
  <h2 className="headline" style={{marginTop:32}}>Activity</h2>{activity.isPending?<Skeleton/>:activity.isError?<ErrorState message={errorMessage(activity.error)} onRetry={()=>activity.refetch()}/>:(activity.data||[]).length===0?<p className="secondary">No activity yet.</p>:<RuledList>{(activity.data||[]).map((a:any)=><li key={a.id}><Link to={`/posts/${a.id}`}>{a.title||'Post'}</Link><p className="secondary">{a.body||''}</p></li>)}</RuledList>}</>}</div>;
}
