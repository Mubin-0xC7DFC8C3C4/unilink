import { useState } from 'react'; import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'; import { AdminList } from '../AdminList';
import { apiGet, apiPost, api, errorMessage } from '../../api/client'; import { Button } from '../../design/primitives';
/** Reads come from GET /api/public/* (the admin reads never existed under
 *  /admin/{tab}). Departments/batches need a universityId, so those tabs
 *  have a university picker. Creates/deactivates/renames use the
 *  /api/admin/master-data/* endpoints; department-catalog is reference data
 *  with no create endpoint, so that tab stays read-only. */
const TABS=[['universities','Universities'],['department-catalog','Department catalog'],['departments','Departments'],['batches','Batches'],['sectors','Sectors']] as const;
export default function MasterData(){ const [tab,setTab]=useState<string>('universities'); const [uniId,setUniId]=useState(''); const qc=useQueryClient();
  const unis = useQuery({ queryKey:['public-universities'], queryFn: ()=>apiGet<any[]>('/public/universities'), retry:false });
  const catalog = useQuery({ queryKey:['public-catalog'], queryFn: ()=>apiGet<any[]>('/public/department-catalog'), retry:false, enabled: tab==='departments' });
  const depts = useQuery({ queryKey:['public-departments',uniId], queryFn: ()=>apiGet<any[]>(`/public/departments?universityId=${uniId}`), retry:false, enabled: !!uniId && (tab==='batches') });
  const effUni = uniId || (unis.data?.[0]?.id ? String(unis.data[0].id) : '');
  const path = tab==='universities' ? '/public/universities'
    : tab==='department-catalog' ? '/public/department-catalog'
    : tab==='departments' ? `/public/departments?universityId=${effUni}`
    : tab==='batches' ? `/public/batches?universityId=${effUni}`
    : '/public/sectors';
  const [name,setName]=useState(''); const [shortName,setShortName]=useState(''); const [domains,setDomains]=useState('');
  const [catalogId,setCatalogId]=useState(''); const [deptId,setDeptId]=useState(''); const [label,setLabel]=useState(''); const [startYear,setStartYear]=useState('');
  const [editing,setEditing]=useState<{id:string|number;name:string}|null>(null);
  const refresh=()=>qc.invalidateQueries({queryKey:['admin-master',tab]});
  const create=useMutation({ mutationFn: ()=>{
    if(tab==='universities') return apiPost('/admin/master-data/universities',{name, shortName, emailDomains: domains});
    if(tab==='departments') return apiPost('/admin/master-data/departments',{universityId: Number(effUni), catalogId: Number(catalogId), name});
    if(tab==='batches') return apiPost('/admin/master-data/batches',{universityId: Number(effUni), departmentId: Number(deptId), label, startYear: startYear?Number(startYear):undefined});
    return apiPost('/admin/master-data/sectors',{name});
  }, onSuccess: ()=>{ refresh(); setName(''); setShortName(''); setDomains(''); setCatalogId(''); setDeptId(''); setLabel(''); setStartYear(''); } });
  const deactivate=useMutation({ mutationFn: (id:string|number)=>apiPost(`/admin/master-data/${tab}/${id}/deactivate`,{}), onSuccess: refresh });
  const rename=useMutation({ mutationFn: ()=>api(`/admin/master-data/${tab}/${editing!.id}`,{method:'PUT', body: JSON.stringify(tab==='batches'?{label: editing!.name}:{name: editing!.name})}), onSuccess: ()=>{ setEditing(null); refresh(); } });
  const err = create.error||deactivate.error||rename.error;
  return <div><div role="tablist" aria-label="Master data" style={{display:'flex',gap:8,flexWrap:'wrap',marginBottom:16}}>{TABS.map(([k,label])=><button key={k} role="tab" aria-selected={tab===k} className={tab===k?'btn btn-primary btn-sm':'btn btn-sm'} onClick={()=>{setTab(k); setEditing(null);}}>{label}</button>)}</div>
  {(tab==='departments'||tab==='batches') && <div style={{marginBottom:8}}><label htmlFor="md-uni">University </label><select id="md-uni" value={effUni} onChange={e=>setUniId(e.target.value)} className="input-search">{(unis.data||[]).map((u:any)=><option key={u.id} value={u.id}>{u.name}</option>)}</select></div>}
  <AdminList<any> eyebrow="Admin Portal" title="Master data" lead="Universities (with email domains), department catalog, departments, batches and sector tags. Referenced rows are deactivated, never hard-deleted." queryKey={['admin-master',tab]} path={path} emptyTitle="No rows" emptyBody="No rows in this table yet." render={items=><table className="table"><thead><tr><th>Name</th><th>Detail</th><th>Active</th><th>Actions</th></tr></thead><tbody>{items.map((r:any)=><tr key={r.id}><td>{r.name||r.label||r.id}</td><td>{r.emailDomains||r.shortName||r.code||''}</td><td>{r.active===false?'No':'Yes'}</td><td>{tab!=='department-catalog' && <><Button onClick={()=>setEditing({id:r.id,name:r.name||r.label||''})}>Rename</Button>{' '}<Button onClick={()=>deactivate.mutate(r.id)} disabled={r.active===false}>Deactivate</Button></>}</td></tr>)}</tbody></table>} />
  {err && <p role="alert" className="field-error">{errorMessage(err)}</p>}
  {editing && <section className="card-group" style={{marginTop:16}}><h2 className="title">Rename row</h2><div style={{display:'flex',gap:8}}><input aria-label="New name" className="input-search" value={editing.name} onChange={e=>setEditing({...editing,name:e.target.value})} style={{flex:1}} /><Button variant="primary" onClick={()=>rename.mutate()} disabled={!editing.name.trim()}>Save</Button><Button onClick={()=>setEditing(null)}>Cancel</Button></div></section>}
  {tab!=='department-catalog' && <section className="card-group" style={{marginTop:16}}><h2 className="title">Add {tab==='batches'?'batch':tab.slice(0,-1)}</h2>
  {(tab==='universities'||tab==='departments'||tab==='sectors') && <><label htmlFor="md-name">Name</label><input id="md-name" className="input-search" value={name} onChange={e=>setName(e.target.value)} style={{width:'100%',marginBottom:8}} /></>}
  {tab==='universities' && <><label htmlFor="md-short">Short name</label><input id="md-short" className="input-search" value={shortName} onChange={e=>setShortName(e.target.value)} style={{width:'100%',marginBottom:8}} /><label htmlFor="md-dom">Email domains</label><input id="md-dom" className="input-search" value={domains} onChange={e=>setDomains(e.target.value)} placeholder="e.g. mu.ac.bd" style={{width:'100%',marginBottom:8}} /></>}
  {tab==='departments' && <><label htmlFor="md-cat">Department catalog</label><select id="md-cat" value={catalogId} onChange={e=>setCatalogId(e.target.value)} className="input-search" style={{marginBottom:8}}><option value="">Select…</option>{(catalog.data||[]).map((c:any)=><option key={c.id} value={c.id}>{c.name}</option>)}</select></>}
  {tab==='batches' && <><label htmlFor="md-dept">Department</label><select id="md-dept" value={deptId} onChange={e=>setDeptId(e.target.value)} className="input-search" style={{marginBottom:8}}><option value="">Select…</option>{(depts.data||[]).map((d:any)=><option key={d.id} value={d.id}>{d.name}</option>)}</select><label htmlFor="md-label">Label</label><input id="md-label" className="input-search" value={label} onChange={e=>setLabel(e.target.value)} style={{width:'100%',marginBottom:8}} /><label htmlFor="md-year">Start year</label><input id="md-year" type="number" className="input-search" value={startYear} onChange={e=>setStartYear(e.target.value)} style={{marginBottom:8}} /></>}
  <Button variant="primary" onClick={()=>create.mutate()} disabled={create.isPending}>Add</Button></section>}</div>;
}
