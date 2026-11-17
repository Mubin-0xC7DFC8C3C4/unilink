import { useState } from 'react'; import { useMutation, useQueryClient } from '@tanstack/react-query'; import { AdminList } from '../AdminList'; import { apiPost, errorMessage } from '../../api/client'; import { Button } from '../../design/primitives';
/** Queue: GET /api/admin/moderation-queue. Decide: POST
 *  /api/admin/moderation/decide {targetType, targetId, action, reason}.
 *  Actions are UPPERCASE HIDE/REMOVE/RESTORE/EDIT_REQUEST (the backend has
 *  no WARN action) and a reason is mandatory. */
export default function Moderation(){ const qc=useQueryClient(); const [reason,setReason]=useState('');
  const act=useMutation({ mutationFn: ({targetType,targetId,action}:{targetType:string;targetId:number;action:string})=>apiPost('/admin/moderation/decide',{targetType,targetId,action,reason}), onSuccess: ()=>qc.invalidateQueries({queryKey:['admin-moderation']}) });
  return <div><div style={{marginBottom:8}}><label htmlFor="mod-reason">Reason </label><input id="mod-reason" className="input-search" value={reason} onChange={e=>setReason(e.target.value)} placeholder="Reason (required for decisions)" /></div>
  <AdminList<any> eyebrow="Admin Portal" title="Moderation queue" lead="Reported and auto-hidden posts, comments, messages (snapshot only) and profiles, ordered by report count then age." queryKey={['admin-moderation']} path="/admin/moderation-queue" emptyTitle="Queue is clear" emptyBody="No items await moderation." render={items=><table className="table"><thead><tr><th>Target</th><th>Reason</th><th>Status</th><th>Actions</th></tr></thead><tbody>{items.map((m:any)=><tr key={m.id}><td>{m.targetType||''} {m.targetId||m.id}</td><td>{m.reason||'—'}</td><td>{m.status||'—'}</td><td style={{display:'flex',gap:4,flexWrap:'wrap'}}>{(['HIDE','REMOVE','RESTORE','EDIT_REQUEST'] as const).map(a=><Button key={a} onClick={()=>act.mutate({targetType:m.targetType,targetId:m.targetId,action:a})} disabled={!reason.trim()}>{a}</Button>)}</td></tr>)}</tbody></table>} />
  {act.isError && <p role="alert" className="field-error">{errorMessage(act.error)}</p>}</div>;
}
