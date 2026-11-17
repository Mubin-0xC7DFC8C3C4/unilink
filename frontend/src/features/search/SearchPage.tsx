import { useState } from 'react'; import { Link } from 'react-router-dom'; import { useQuery } from '@tanstack/react-query';
import { apiGet, errorMessage } from '../../api/client'; import { Button, EmptyState, ErrorState, PageHeader, Skeleton, ApiUnavailable } from '../../design/primitives'; import { SECTORS } from '../../api/types';
export default function SearchPage(){ const [q,setQ]=useState(''); const [type,setType]=useState(''); const [sector,setSector]=useState(''); const [submitted,setSubmitted]=useState('');
  const res = useQuery({ queryKey:['search',submitted,type,sector], queryFn: ()=>apiGet<any[]>(`/search?q=${encodeURIComponent(submitted)}${type?`&type=${type}`:''}${sector?`&sector=${encodeURIComponent(sector)}`:''}`), enabled: !!submitted, retry:false });
  const items = Array.isArray(res.data)?res.data:(res.data as any)?.items??[];
  return <div className="container"><PageHeader eyebrow="Search" title="Search" lead="Search people and posts by keyword, sector or skill." />
  <form role="search" onSubmit={e=>{e.preventDefault(); setSubmitted(q);}} style={{display:'flex',gap:8,flexWrap:'wrap'}}><input aria-label="Search" className="input-search" value={q} onChange={e=>setQ(e.target.value)} placeholder="Keyword, name or skill" style={{flex:1,minWidth:200}} />
  <select aria-label="Type" value={type} onChange={e=>setType(e.target.value)} className="input-search"><option value="">People and posts</option><option value="people">People</option><option value="posts">Posts</option></select>
  <select aria-label="Sector" value={sector} onChange={e=>setSector(e.target.value)} className="input-search"><option value="">All sectors</option>{SECTORS.map(s=><option key={s}>{s}</option>)}</select>
  <Button type="submit" variant="primary">Search</Button></form>
  {!submitted?<EmptyState title="Search UniLink" body="Enter a keyword to search people and posts."/>:res.isPending?<Skeleton/>:res.isError?(res.error as any)?.status===404?<ApiUnavailable endpoint="/search"/>:<ErrorState message={errorMessage(res.error)}/>:items.length===0?<EmptyState title="No results" body="Try a different keyword, sector or type."/>:<ul className="ruled-list">{items.map((r:any)=><li key={r.id}><Link to={r.type==='people'||r.fullName?`/profile/${r.id}`:`/posts/${r.id}`}>{r.fullName||r.title||r.id}</Link><p className="secondary">{r.university||r.department||r.body||''}</p></li>)}</ul>}
  </div>;
}
