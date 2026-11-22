import { useState } from 'react'; import { useQuery } from '@tanstack/react-query'; import { apiGet, errorMessage } from '../../api/client';
import { EmptyState, ErrorState, PageHeader, Skeleton } from '../../design/primitives';
import { fmtDateTime } from '../adminUi';
/** Audit log: GET /api/admin/audit-logs (not /admin/audit). The backend
 *  takes only ?limit, so the action filter is applied client-side.
 *  Export is GET /api/admin/audit-logs/export (CSV) and accepts only
 *  ?limit too — no action filter — so the export is labelled unfiltered
 *  and carries the same limit as the list instead of silently returning
 *  a different set of rows than the filtered view shows. */
const LIMIT = 200;
export default function Audit(){ const [action,setAction]=useState('');
  const q=useQuery({queryKey:['admin-audit'], queryFn:()=>apiGet<any[]>(`/admin/audit-logs?limit=${LIMIT}`), retry:false});
  const items=(Array.isArray(q.data)?q.data:[]).filter(a=>!action || (a.action||'').includes(action));
  return <div><PageHeader eyebrow="Admin Portal" title="Activity Logs" lead="Every administrative action, in order. This log is read-only — entries can never be edited or deleted." />
  <div style={{marginBottom:8}}><label htmlFor="au">Filter by action </label><input id="au" className="input-search" value={action} onChange={e=>setAction(e.target.value)} placeholder="e.g. VERIFICATION_APPROVED" />{' '}<a className="btn btn-sm" href={`/api/admin/audit-logs/export?limit=${LIMIT}`}>Export CSV (unfiltered)</a><p className="secondary">The export ignores the action filter — it always contains the latest {LIMIT} entries across all actions.</p></div>
  {q.isPending?<Skeleton/>:q.isError?<ErrorState message={errorMessage(q.error)} onRetry={()=>q.refetch()}/>
  :items.length===0?<EmptyState title="No audit entries" body="No audit entries match this filter."/>
  :<table className="table"><thead><tr><th>Timestamp</th><th>Administrator</th><th>Action</th><th>Target</th><th>Details</th></tr></thead><tbody>{items.map((a:any)=><tr key={a.id}><td>{fmtDateTime(a.createdAt)}</td><td>Admin #{a.actorAdminId||'—'}</td><td>{a.action}</td><td>{a.targetType||''} {a.targetId||''}</td><td>{a.reason||'—'}</td></tr>)}</tbody></table>}</div>;
}
