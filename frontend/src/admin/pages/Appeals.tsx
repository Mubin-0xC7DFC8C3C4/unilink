import { useState } from 'react'; import { useMutation, useQueryClient } from '@tanstack/react-query'; import { AdminList } from '../AdminList'; import { apiPost, errorMessage } from '../../api/client'; import { Button } from '../../design/primitives';
/** Decide: POST /api/admin/appeals/{id}/decide {action: ACCEPT|REJECT,
 *  reason}. Uphold = reject the appeal (the hiding/suspension stands);
 *  Overturn = accept the appeal. A reason is mandatory. */
export default function Appeals(){ const qc=useQueryClient(); const [reason,setReason]=useState('');
  const act=useMutation({ mutationFn: ({id,action}:{id:string|number;action:'ACCEPT'|'REJECT'})=>apiPost(`/admin/appeals/${id}/decide`,{action,reason}), onSuccess: ()=>qc.invalidateQueries({queryKey:['admin-appeals']}) });
  return <div><div style={{marginBottom:8}}><label htmlFor="ap-reason">Decision reason </label><input id="ap-reason" className="input-search" value={reason} onChange={e=>setReason(e.target.value)} placeholder="Reason (required)" /></div>
  <AdminList<any> eyebrow="Admin Portal" title="Appeals" lead="Appeals against hiding or suspension. A suspended user can appeal without a session (appeal link)." queryKey={['admin-appeals']} path="/admin/appeals" emptyTitle="No appeals" emptyBody="No appeals are waiting." render={items=><table className="table"><thead><tr><th>Appeal</th><th>Against</th><th>Status</th><th>Actions</th></tr></thead><tbody>{items.map((a:any)=><tr key={a.id}><td>{a.id}</td><td>{a.targetType||''} {a.targetId||''}</td><td>{a.status||'—'}</td><td><Button onClick={()=>act.mutate({id:a.id,action:'REJECT'})} disabled={!reason.trim()}>Uphold</Button>{' '}<Button onClick={()=>act.mutate({id:a.id,action:'ACCEPT'})} disabled={!reason.trim()}>Overturn</Button></td></tr>)}</tbody></table>} />
  {act.isError && <p role="alert" className="field-error">{errorMessage(act.error)}</p>}</div>;
}
