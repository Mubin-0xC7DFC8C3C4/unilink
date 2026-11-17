import { useState } from 'react'; import { useQuery } from '@tanstack/react-query'; import { apiGet, errorMessage } from '../../api/client';
import { EmptyState, ErrorState, PageHeader, Skeleton } from '../../design/primitives';
/** Audit log: GET /api/admin/audit-logs (not /admin/audit). The backend
 *  takes only ?limit, so the action filter is applied client-side.
 *  Export is GET /api/admin/audit-logs/export (CSV). */
export default function Audit(){ const [action,setAction]=useState('');
  const q=useQuery({queryKey:['admin-audit'], queryFn:()=>apiGet<any[]>('/admin/audit-logs?limit=200'), retry:false});
  const items=(Array.isArray(q.data)?q.data:[]).filter(a=>!action || (a.action||'').includes(action));
  return <div><PageHeader eyebrow="Admin Portal" title="Audit log" lead="Read-only, append-only audit log with filters and export. Admins can never write, edit or delete audit rows." />
  <div style={{marginBottom:8}}><label htmlFor="au">Filter by action </label><input id="au" className="input-search" value={action} onChange={e=>setAction(e.target.value)} placeholder="e.g. VERIFICATION_APPROVED" />{' '}<a className="btn btn-sm" href="/api/admin/audit-logs/export">Export CSV</a></div>
  {q.isPending?<Skeleton/>:q.isError?<ErrorState message={errorMessage(q.error)} onRetry={()=>q.refetch()}/>
  :items.length===0?<EmptyState title="No audit entries" body="No audit entries match this filter."/>
  :<table className="table"><thead><tr><th>When</th><th>Actor</th><th>Action</th><th>Target</th><th>Reason</th></tr></thead><tbody>{items.map((a:any)=><tr key={a.id}><td>{a.createdAt||''}</td><td>{a.actorAdminId||'—'}</td><td>{a.action}</td><td>{a.targetType||''} {a.targetId||''}</td><td>{a.reason||'—'}</td></tr>)}</tbody></table>}</div>;
}
