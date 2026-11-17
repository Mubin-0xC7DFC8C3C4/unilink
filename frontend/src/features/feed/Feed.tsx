import { useState } from 'react'; import { Link } from 'react-router-dom'; import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiGet, apiPost, apiDelete, errorMessage } from '../../api/client'; import { LEVELS, SECTORS, type Post, type SectorTag, type CommunityContext } from '../../api/types';
import { Button, EmptyState, ErrorState, PageHeader, Pill, Skeleton } from '../../design/primitives';
/** Feed level HUB maps to the backend visibility DEPARTMENT_HUB (PostService
 *  only accepts DEPARTMENT/UNIVERSITY/DEPARTMENT_HUB/NATIONAL). */
function levelToVisibility(level: string){ return level==='HUB' ? 'DEPARTMENT_HUB' : level; }
export default function Feed(){
  const [level,setLevel]=useState<string>('DEPARTMENT'); const [sector,setSector]=useState(''); const [cursor,setCursor]=useState<string|null>(null); const [cursors,setCursors]=useState<(string|null)[]>([null]);
  const qc=useQueryClient();
  const sectorsQ = useQuery({ queryKey:['public-sectors'], queryFn: ()=>apiGet<SectorTag[]>('/public/sectors'), retry:false });
  const sectors = sectorsQ.data ?? [];
  const sectorId = sector ? Number(sector) : null;
  const sectorName = sectors.find(s=>String(s.id)===sector)?.name;
  const ctx = useQuery({ queryKey:['community-context'], queryFn: ()=>apiGet<CommunityContext>('/community/context'), retry:false });
  const me = useQuery({ queryKey:['my-profile'], queryFn: ()=>apiGet<{sectors?:string[]}>('/profiles/me'), retry:false });
  const followingSector = !!sectorName && (me.data?.sectors ?? []).includes(sectorName);
  const q = useQuery({ queryKey:['feed',level,sector,cursor], queryFn: ()=>apiGet<{items:Post[]; nextCursor?:string|null}>(`/feed?level=${level}${sectorId?`&sector=${sectorId}`:''}${cursor?`&cursor=${encodeURIComponent(cursor)}`:''}`), retry:false });
  const items = q.data?.items ?? [];
  const composer = useMutation({ mutationFn: (body:{title:string;body:string})=>apiPost('/posts', {...body, visibility: levelToVisibility(level), sectorIds: sectorId?[sectorId]:[]}), onSuccess: ()=>{ setTitle(''); setBody(''); qc.invalidateQueries({queryKey:['feed']}); } });
  const followSector = useMutation({ mutationFn: ()=>apiPost(`/profiles/me/sectors/${sectorId}`,{}), onSuccess: ()=>qc.invalidateQueries({queryKey:['my-profile']}) });
  const unfollowSector = useMutation({ mutationFn: ()=>apiDelete(`/profiles/me/sectors/${sectorId}`), onSuccess: ()=>qc.invalidateQueries({queryKey:['my-profile']}) });
  const [title,setTitle]=useState(''); const [body,setBody]=useState('');
  return <div className="container"><PageHeader eyebrow="Community" title="Feed" lead="Department is the default level. Switch levels or filter by sector; older items load with keyset pagination." />
  {ctx.data && <p className="secondary">{ctx.data.universityName||''}{ctx.data.departmentName?` · ${ctx.data.departmentName}`:''}</p>}
  <div role="tablist" aria-label="Feed level" style={{display:'flex',gap:8,flexWrap:'wrap'}}>{LEVELS.map(l=><button key={l} role="tab" aria-selected={level===l} className={level===l?'btn btn-primary btn-sm':'btn btn-sm'} onClick={()=>{setLevel(l); setCursor(null); setCursors([null]);}}>{l==='HUB'?'National Department Hub':l.charAt(0)+l.slice(1).toLowerCase()}</button>)}</div>
  <div style={{marginTop:16,display:'flex',gap:8,alignItems:'center',flexWrap:'wrap'}}><label htmlFor="sector">Sector</label><select id="sector" value={sector} onChange={e=>setSector(e.target.value)} className="input-search"><option value="">All sectors</option>{(sectors.length?sectors.map(s=>({id:String(s.id),name:s.name})):SECTORS.map(s=>({id:'',name:s}))).map(s=><option key={s.name} value={s.id} disabled={!s.id}>{s.name}</option>)}</select>
  {sectorId && <Button onClick={()=>followingSector?unfollowSector.mutate():followSector.mutate()} disabled={followSector.isPending||unfollowSector.isPending}>{followingSector?'Unfollow sector':'Follow sector'}</Button>}
  {(followSector.isError||unfollowSector.isError) && <span role="alert" className="field-error">{errorMessage(followSector.error||unfollowSector.error)}</span>}</div>
  <section aria-label="Create a post" className="card-group" style={{marginTop:24}}><h2 className="title">New post</h2>
    <form onSubmit={e=>{e.preventDefault(); if(!title.trim()||!body.trim()) return; composer.mutate({title,body});}}>
    <input aria-label="Post title" placeholder="Title" value={title} onChange={e=>setTitle(e.target.value)} className="input-search" style={{width:'100%',marginBottom:8}} />
    <textarea aria-label="Post body" placeholder="Share something with your community…" value={body} onChange={e=>setBody(e.target.value)} rows={3} style={{width:'100%'}} />
    {composer.isError && <p role="alert" className="field-error">{errorMessage(composer.error)}</p>}
    {composer.isSuccess && <p role="status">Posted.</p>}
    <div style={{marginTop:8}}><Button type="submit" variant="primary" disabled={composer.isPending || !title.trim() || !body.trim()}>Post</Button></div></form></section>
  {q.isPending?<Skeleton/>:q.isError?<ErrorState message={errorMessage(q.error)} onRetry={()=>q.refetch()}/>
  :items.length===0?<EmptyState title="No posts yet" body="Nothing has been posted at this level yet. Be the first to post, or try another level."/>
  :<><ul className="ruled-list">{items.map(p=><li key={p.id}><Link to={`/posts/${p.id}`} style={{textDecoration:'none'}}><strong>{p.title||'Untitled post'}</strong></Link><p className="secondary">{p.body||''}</p><p className="body-sm secondary">{p.authorName||p.author?.fullName||'Student'} {(p.sectors||[]).map(s=><Pill key={s}>{s}</Pill>)} {p.commentCount!=null?`· ${p.commentCount} comments`:''}</p></li>)}</ul>
  <div style={{display:'flex',gap:8,marginTop:16}}><Button disabled={cursors.length<=1} onClick={()=>{const prev=[...cursors]; prev.pop(); setCursors(prev); setCursor(prev[prev.length-1]);}}>Previous</Button><Button disabled={!q.data?.nextCursor} onClick={()=>{const n=q.data!.nextCursor!; setCursors([...cursors,n]); setCursor(n);}}>Next →</Button></div></>}
  </div>;
}
