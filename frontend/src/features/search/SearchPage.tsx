import { useEffect, useState } from 'react'; import { Link, useSearchParams } from 'react-router-dom'; import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiDelete, apiGet, apiPost, errorMessage } from '../../api/client'; import { Button, EmptyState, ErrorState, PageHeader, Skeleton } from '../../design/primitives'; import { SECTORS, type SectorTag } from '../../api/types';
/** The backend returns a Map {posts, users, universities, departments,
 *  postsNextCursor, usersNextCursor, postsTotal, usersTotal, total} —
 *  not an array. `type` accepts only all/posts/users (anything else is
 *  a 400 INVALID_TYPE), and `sector` is a sector id. Cursors are last
 *  ids (keyset pagination, D-201); `limit` is clamped to 1..50
 *  server-side. Events are posts of type EVENT, shown in their own
 *  section with location when the API carries it. */
interface PostHit { id:number; title?:string; body?:string; type?:string; location?:string; organizerName?:string }
interface UserHit { id:number; fullName?:string; matchedSkills?:string[] }
interface CatalogueHit { id:number; name:string; shortName?:string; category?:string; universityId?:number }
interface ConnectionStatus { status:string; requestId?:number|null }
interface SearchResult { posts?: PostHit[]; users?: UserHit[]; universities?: CatalogueHit[]; departments?: CatalogueHit[]; postsNextCursor?:string|null; usersNextCursor?:string|null; total?:number }
export default function SearchPage(){ const [params]=useSearchParams(); const paramQ=params.get('q')??''; const [q,setQ]=useState(paramQ); const [type,setType]=useState(''); const [sector,setSector]=useState(''); const [submitted,setSubmitted]=useState(paramQ);
  // The header's global search bar lands here with ?q= — run the same real
  // search with it, and follow the parameter if it changes while mounted.
  // A new header query restarts the pager from the first page.
  useEffect(()=>{ setQ(paramQ); setSubmitted(paramQ); setPages([{post:null,user:null}]); },[paramQ]);
  const qc = useQueryClient();
  const sectorsQ = useQuery({ queryKey:['public-sectors'], queryFn: ()=>apiGet<SectorTag[]>('/public/sectors'), retry:false });
  const sectors = sectorsQ.data ?? [];
  const backendType = type==='people' ? 'users' : type==='posts' ? 'posts' : 'all';
  // Keyset pager: pages are (posts cursor, users cursor) pairs — one
  // shared page step advances both sections. A section with no next
  // cursor keeps its cursor, which re-fetches an empty page for it.
  const [pages,setPages]=useState<{post:string|null; user:string|null}[]>([{post:null,user:null}]);
  const { post: postCursor, user: userCursor } = pages[pages.length-1];
  const page = pages.length - 1;
  const resetPager = ()=>{ setPages([{post:null,user:null}]); };
  const res = useQuery({ queryKey:['search',submitted,backendType,sector,postCursor,userCursor], queryFn: ()=>apiGet<SearchResult>(`/search?q=${encodeURIComponent(submitted)}&type=${backendType}${sector?`&sector=${sector}`:''}${postCursor?`&cursor=${encodeURIComponent(postCursor)}`:''}${userCursor?`&usersCursor=${encodeURIComponent(userCursor)}`:''}`), enabled: !!submitted, retry:false });
  const postHits = res.data?.posts ?? [];
  const userHits = res.data?.users ?? [];
  const eventHits = postHits.filter((r)=>r.type==='EVENT');
  const plainPosts = postHits.filter((r)=>r.type!=='EVENT');
  const universities = res.data?.universities ?? [];
  const departments = res.data?.departments ?? [];
  const total = res.data?.total ?? (postHits.length + userHits.length + universities.length + departments.length);
  const hasNext = !!(res.data?.postsNextCursor || res.data?.usersNextCursor);
  // Connection status for each person hit, so Connect / Accept /
  // Pending / Cancel actions render inline (D-200).
  const statusQueries = useQueries({
    queries: userHits.map((u) => ({ queryKey:['connection-status', u.id], queryFn: ()=>apiGet<ConnectionStatus>(`/connections/status/${u.id}`), retry:false })),
  });
  const statusOf = (id:number) => statusQueries[userHits.findIndex((u)=>u.id===id)]?.data;
  const connectMut = useMutation({
    mutationFn: (id:number)=>apiPost('/connections/requests',{addresseeId:id}),
    onSuccess: ()=>{ qc.invalidateQueries({ queryKey:['connection-status'] }); },
  });
  const acceptMut = useMutation({
    mutationFn: (requestId:number)=>apiPost(`/connections/requests/${requestId}/accept`,{}),
    onSuccess: ()=>{ qc.invalidateQueries({ queryKey:['connection-status'] }); },
  });
  const cancelMut = useMutation({
    mutationFn: (requestId:number)=>apiPost(`/connections/requests/${requestId}/cancel`,{}),
    onSuccess: ()=>{ qc.invalidateQueries({ queryKey:['connection-status'] }); },
  });
  const removeMut = useMutation({
    mutationFn: (id:number)=>apiDelete(`/connections/${id}`),
    onSuccess: ()=>{ qc.invalidateQueries({ queryKey:['connection-status'] }); },
  });
  const connectionAction = (u:UserHit) => {
    const s = statusOf(u.id);
    if (!s) return null;
    if (s.status==='CONNECTED') return <Button className="btn-sm" disabled={removeMut.isPending} onClick={()=>removeMut.mutate(u.id)}>Remove connection</Button>;
    if (s.status==='PENDING_OUTGOING') return <Button className="btn-sm" disabled={cancelMut.isPending} onClick={()=>s.requestId!=null && cancelMut.mutate(s.requestId)}>Cancel request</Button>;
    if (s.status==='PENDING_INCOMING') return <Button variant="primary" className="btn-sm" disabled={acceptMut.isPending} onClick={()=>s.requestId!=null && acceptMut.mutate(s.requestId)}>Accept</Button>;
    if (s.status==='NONE') return <Button className="btn-sm" disabled={connectMut.isPending} onClick={()=>connectMut.mutate(u.id)}>Connect</Button>;
    return null;
  };
  const mutError = connectMut.error || acceptMut.error || cancelMut.error || removeMut.error;
  return <div className="container"><PageHeader eyebrow="Search" title="Search" lead="Search people and posts by keyword, sector or skill." />
  <form role="search" onSubmit={e=>{e.preventDefault(); resetPager(); setSubmitted(q);}} style={{display:'flex',gap:8,flexWrap:'wrap'}}><input aria-label="Search" className="input-search" value={q} onChange={e=>setQ(e.target.value)} placeholder="Keyword, name or skill" style={{flex:1,minWidth:200}} />
  <select aria-label="Type" value={type} onChange={e=>setType(e.target.value)} className="input-search"><option value="">People and posts</option><option value="people">People</option><option value="posts">Posts</option></select>
  <select aria-label="Sector" value={sector} onChange={e=>setSector(e.target.value)} className="input-search"><option value="">All sectors</option>{(sectors.length?sectors.map(s=>({id:String(s.id),name:s.name})):SECTORS.map(s=>({id:'',name:s}))).map(s=><option key={s.name} value={s.id} disabled={!s.id}>{s.name}</option>)}</select>
  <Button type="submit" variant="primary">Search</Button></form>
  {!submitted?<EmptyState title="Search UniLink" body="Enter a keyword to search people and posts."/>:res.isPending?<Skeleton/>:res.isError?<ErrorState message={errorMessage(res.error)} onRetry={()=>res.refetch()}/>:total===0?<EmptyState title="No results" body="Try a different keyword, sector or type."/>:<>
  <p className="secondary" role="status" style={{marginTop:16}}>{total} result{total===1?'':'s'}{page>0?` · page ${page+1}`:''}</p>
  {userHits.length>0 && <><h2 className="headline" style={{marginTop:24}}>People</h2><ul className="ruled-list">{userHits.map((r)=><li key={`u-${r.id}`}><Link to={`/profile/${r.id}`}>{r.fullName||`Student ${r.id}`}</Link>{(r.matchedSkills?.length??0)>0 && <span className="secondary"> · Skills: {r.matchedSkills!.join(', ')}</span>}{' '}{connectionAction(r)}</li>)}</ul></>}
  {eventHits.length>0 && <><h2 className="headline" style={{marginTop:24}}>Events</h2><ul className="ruled-list">{eventHits.map((r)=><li key={`e-${r.id}`}><Link to={`/posts/${r.id}`}>{r.title||`Event ${r.id}`}</Link>{r.location && <p className="secondary">{r.location}</p>}{r.organizerName && <p className="secondary">Organised by {r.organizerName}</p>}</li>)}</ul></>}
  {plainPosts.length>0 && <><h2 className="headline" style={{marginTop:24}}>Posts</h2><ul className="ruled-list">{plainPosts.map((r)=><li key={`p-${r.id}`}><Link to={`/posts/${r.id}`}>{r.title||`Post ${r.id}`}</Link><p className="secondary">{r.body||''}</p></li>)}</ul></>}
  {universities.length>0 && <><h2 className="headline" style={{marginTop:24}}>Universities</h2><ul className="ruled-list">{universities.map((r)=><li key={`uni-${r.id}`}>{r.name}{r.shortName?` (${r.shortName})`:''}{r.category?` · ${r.category}`:''}</li>)}</ul></>}
  {departments.length>0 && <><h2 className="headline" style={{marginTop:24}}>Departments</h2><ul className="ruled-list">{departments.map((r)=><li key={`d-${r.id}`}>{r.name}</li>)}</ul></>}
  {mutError && <p role="alert" className="field-error">{errorMessage(mutError)}</p>}
  <div style={{display:'flex',gap:8,marginTop:16}}><Button disabled={page===0} onClick={()=>setPages((p)=>p.slice(0,-1))}>Previous</Button><Button disabled={!hasNext} onClick={()=>setPages((p)=>[...p, { post: res.data?.postsNextCursor ?? postCursor, user: res.data?.usersNextCursor ?? userCursor }])}>Next →</Button></div></>}
  </div>;
}
