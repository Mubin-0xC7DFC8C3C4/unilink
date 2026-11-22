import { useEffect, useState } from 'react'; import { Link, useSearchParams } from 'react-router-dom'; import { useQuery } from '@tanstack/react-query';
import { apiGet, errorMessage } from '../../api/client'; import { Button, EmptyState, ErrorState, PageHeader, Skeleton } from '../../design/primitives'; import { SECTORS, type SectorTag } from '../../api/types';
/** The backend returns a Map {posts, users} — not an array. `type` accepts
 *  only all/posts/users, and `sector` is a sector id (it was ignored
 *  server-side before the Phase 2 fix). */
interface SearchResult { posts?: {id:number;title?:string;body?:string}[]; users?: {id:number;fullName?:string}[] }
export default function SearchPage(){ const [params]=useSearchParams(); const paramQ=params.get('q')??''; const [q,setQ]=useState(paramQ); const [type,setType]=useState(''); const [sector,setSector]=useState(''); const [submitted,setSubmitted]=useState(paramQ);
  // The header's global search bar lands here with ?q= — run the same real
  // search with it, and follow the parameter if it changes while mounted.
  useEffect(()=>{ setQ(paramQ); setSubmitted(paramQ); },[paramQ]);
  const sectorsQ = useQuery({ queryKey:['public-sectors'], queryFn: ()=>apiGet<SectorTag[]>('/public/sectors'), retry:false });
  const sectors = sectorsQ.data ?? [];
  const backendType = type==='people' ? 'users' : type==='posts' ? 'posts' : 'all';
  const res = useQuery({ queryKey:['search',submitted,backendType,sector], queryFn: ()=>apiGet<SearchResult>(`/search?q=${encodeURIComponent(submitted)}&type=${backendType}${sector?`&sector=${sector}`:''}`), enabled: !!submitted, retry:false });
  const postHits = res.data?.posts ?? [];
  const userHits = res.data?.users ?? [];
  const total = postHits.length + userHits.length;
  return <div className="container"><PageHeader eyebrow="Search" title="Search" lead="Search people and posts by keyword, sector or skill." />
  <form role="search" onSubmit={e=>{e.preventDefault(); setSubmitted(q);}} style={{display:'flex',gap:8,flexWrap:'wrap'}}><input aria-label="Search" className="input-search" value={q} onChange={e=>setQ(e.target.value)} placeholder="Keyword, name or skill" style={{flex:1,minWidth:200}} />
  <select aria-label="Type" value={type} onChange={e=>setType(e.target.value)} className="input-search"><option value="">People and posts</option><option value="people">People</option><option value="posts">Posts</option></select>
  <select aria-label="Sector" value={sector} onChange={e=>setSector(e.target.value)} className="input-search"><option value="">All sectors</option>{(sectors.length?sectors.map(s=>({id:String(s.id),name:s.name})):SECTORS.map(s=>({id:'',name:s}))).map(s=><option key={s.name} value={s.id} disabled={!s.id}>{s.name}</option>)}</select>
  <Button type="submit" variant="primary">Search</Button></form>
  {!submitted?<EmptyState title="Search UniLink" body="Enter a keyword to search people and posts."/>:res.isPending?<Skeleton/>:res.isError?<ErrorState message={errorMessage(res.error)} onRetry={()=>res.refetch()}/>:total===0?<EmptyState title="No results" body="Try a different keyword, sector or type."/>:<>
  {userHits.length>0 && <><h2 className="headline" style={{marginTop:24}}>People</h2><ul className="ruled-list">{userHits.map((r)=><li key={`u-${r.id}`}><Link to={`/profile/${r.id}`}>{r.fullName||`Student ${r.id}`}</Link></li>)}</ul></>}
  {postHits.length>0 && <><h2 className="headline" style={{marginTop:24}}>Posts</h2><ul className="ruled-list">{postHits.map((r)=><li key={`p-${r.id}`}><Link to={`/posts/${r.id}`}>{r.title||`Post ${r.id}`}</Link><p className="secondary">{r.body||''}</p></li>)}</ul></>}</>}
  </div>;
}
