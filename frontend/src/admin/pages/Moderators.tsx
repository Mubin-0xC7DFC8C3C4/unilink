import { useState } from 'react'; import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'; import { AdminList } from '../AdminList';
import { apiGet, apiPost, api, errorMessage } from '../../api/client'; import { Button, Field, Input } from '../../design/primitives';
/** List: GET /api/admin/moderators (there is no public moderators endpoint —
 *  admin accounts must not be exposed publicly). Create, edit (university
 *  assignment), deactivate and reset-credentials all use /api/admin/moderators/*. */
export default function Moderators(){ const qc=useQueryClient();
  const [email,setEmail]=useState(''); const [uniId,setUniId]=useState(''); const [password,setPassword]=useState('');
  const [resetResult,setResetResult]=useState<{id:string|number;temp:string}|null>(null);
  const [editing,setEditing]=useState<{id:string|number;universityId:string}|null>(null);
  const unis = useQuery({ queryKey:['public-universities'], queryFn: ()=>apiGet<any[]>('/public/universities'), retry:false });
  const create=useMutation({ mutationFn: ()=>apiPost('/admin/moderators',{email, universityId: Number(uniId), password}), onSuccess: ()=>{ qc.invalidateQueries({queryKey:['admin-moderators']}); setEmail(''); setPassword(''); } });
  const deactivate=useMutation({ mutationFn: (id:string|number)=>apiPost(`/admin/moderators/${id}/deactivate`,{reason:'Deactivated from the Moderators page'}), onSuccess: ()=>qc.invalidateQueries({queryKey:['admin-moderators']}) });
  const reset=useMutation({ mutationFn: (id:string|number)=>apiPost<{temporaryPassword?:string}>(`/admin/moderators/${id}/reset-credentials`,{}), onSuccess: (d, id)=>{ if(d.temporaryPassword) setResetResult({id, temp: d.temporaryPassword}); } });
  const edit=useMutation({ mutationFn: ()=>api(`/admin/moderators/${editing!.id}`,{method:'PUT', body: JSON.stringify({universityId: Number(editing!.universityId)})}), onSuccess: ()=>{ setEditing(null); qc.invalidateQueries({queryKey:['admin-moderators']}); } });
  const err = create.error||deactivate.error||reset.error||edit.error;
  return <div><AdminList<any> eyebrow="Admin Portal" title="Moderators" lead="University moderators: create, edit, deactivate, assign to one university, reset credentials." queryKey={['admin-moderators']} path="/admin/moderators" emptyTitle="No moderators" emptyBody="No moderators have been created yet." render={items=><table className="table"><thead><tr><th>Email</th><th>Role</th><th>University</th><th>Active</th><th>Actions</th></tr></thead><tbody>{items.map((m:any)=><tr key={m.id}><td>{m.email}</td><td>{m.role||'—'}</td><td>{m.universityId||'—'}</td><td>{m.active===false?'No':'Yes'}</td><td style={{display:'flex',gap:4,flexWrap:'wrap'}}><Button onClick={()=>setEditing({id:m.id, universityId: m.universityId?String(m.universityId):''})}>Assign university</Button><Button onClick={()=>reset.mutate(m.id)}>Reset credentials</Button><Button onClick={()=>deactivate.mutate(m.id)} disabled={m.active===false}>Deactivate</Button></td></tr>)}</tbody></table>} />
  {resetResult && <p role="status">Temporary password for moderator {String(resetResult.id)} (shown once — share it securely, it is not stored): <strong>{resetResult.temp}</strong> <Button onClick={()=>setResetResult(null)}>Dismiss</Button></p>}
  {err && <p role="alert" className="field-error">{errorMessage(err)}</p>}
  {editing && <section className="card-group" style={{marginTop:16}}><h2 className="title">Assign university</h2><div style={{display:'flex',gap:8}}><select aria-label="University" value={editing.universityId} onChange={e=>setEditing({...editing,universityId:e.target.value})} className="input-search">{(unis.data||[]).map((u:any)=><option key={u.id} value={u.id}>{u.name}</option>)}</select><Button variant="primary" onClick={()=>edit.mutate()}>Save</Button><Button onClick={()=>setEditing(null)}>Cancel</Button></div></section>}
  <section className="card-group" style={{marginTop:16}}><h2 className="title">Create moderator</h2>
  <Field label="Email" htmlFor="mo-email"><Input id="mo-email" value={email} onChange={e=>setEmail(e.target.value)} /></Field>
  <label htmlFor="mo-uni">University</label><select id="mo-uni" value={uniId} onChange={e=>setUniId(e.target.value)} className="input-search" style={{marginBottom:8}}><option value="">Select…</option>{(unis.data||[]).map((u:any)=><option key={u.id} value={u.id}>{u.name}</option>)}</select>
  <Field label="Password (at least 12 characters)" htmlFor="mo-pw"><Input id="mo-pw" type="password" value={password} onChange={e=>setPassword(e.target.value)} /></Field>
  <Button variant="primary" onClick={()=>create.mutate()} disabled={!email.trim()||!uniId||password.length<12||create.isPending}>Create moderator</Button></section></div>;
}
