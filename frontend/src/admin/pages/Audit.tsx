import { useState } from 'react'; import { useQuery } from '@tanstack/react-query'; import { apiGet, errorMessage } from '../../api/client';
import { Button, EmptyState, ErrorState, PageHeader, Skeleton } from '../../design/primitives';
import { fmtDateTime } from '../adminUi';
/** Audit log: GET /api/admin/audit-logs (not /admin/audit). The backend
 *  takes only ?limit, so the action filter is applied client-side.
 *  Export is GET /api/admin/audit-logs/export (CSV) and accepts only
 *  ?limit too — no action filter — so the export is labelled unfiltered
 *  and carries the same limit as the list instead of silently returning
 *  a different set of rows than the filtered view shows.
 *  D-204 (F): the chain check (GET /admin/audit-logs/verify) verifies
 *  every row's hash — metadata and IP included — and reports the first
 *  broken row, if any. Rows written before that change verify by
 *  hash-chain linkage only and are counted as legacy, not broken. */
const LIMIT = 200;
export default function Audit(){ const [action,setAction]=useState('');
  const q=useQuery({queryKey:['admin-audit'], queryFn:()=>apiGet<any[]>(`/admin/audit-logs?limit=${LIMIT}`), retry:false});
  const items=(Array.isArray(q.data)?q.data:[]).filter(a=>!action || (a.action||'').includes(action));
  const [verifyResult,setVerifyResult]=useState<any>(null);
  const [verifyError,setVerifyError]=useState<string|null>(null);
  const [verifying,setVerifying]=useState(false);
  const runVerify = async () => { setVerifying(true); setVerifyError(null); try { setVerifyResult(await apiGet<any>('/admin/audit-logs/verify')); } catch (e) { setVerifyError(errorMessage(e)); } setVerifying(false); };
  return <div><PageHeader eyebrow="Admin Portal" title="Activity Logs" lead="Every administrative action, in order. This log is read-only — entries can never be edited or deleted." />
  <section className="card-group" aria-label="Audit chain verification" style={{ marginBottom: 16 }}>
    <h2 className="title">Audit chain verification</h2>
    <p className="secondary" style={{ margin: '0 0 10px' }}>Recomputes every row's hash (action, target, metadata and IP address) and checks the hash chain. A broken row means a log entry was altered after it was written.</p>
    <Button onClick={runVerify} disabled={verifying}>{verifying ? 'Verifying…' : 'Verify chain'}</Button>
    {verifyError && <p role="alert" className="field-error">{verifyError}</p>}
    {verifyResult && (
      verifyResult.ok
        ? <p role="status" style={{ margin: '10px 0 0' }}>Chain verified — {verifyResult.totalRows} rows checked ({verifyResult.verifiedRows} hash-verified, {verifyResult.legacyRows} legacy rows verified by linkage only).</p>
        : <p role="alert" className="field-error" style={{ margin: '10px 0 0' }}>Chain broken at row #{verifyResult.brokenRowId} — that entry (or one before it) was altered. {verifyResult.totalRows} rows checked.</p>
    )}
  </section>
  <div style={{marginBottom:8}}><label htmlFor="au">Filter by action </label><input id="au" className="input-search" value={action} onChange={e=>setAction(e.target.value)} placeholder="e.g. VERIFICATION_APPROVED" />{' '}<a className="btn btn-sm" href={`/api/admin/audit-logs/export?limit=${LIMIT}`}>Export CSV (unfiltered)</a><p className="secondary">The export ignores the action filter — it always contains the latest {LIMIT} entries across all actions.</p></div>
  {q.isPending?<Skeleton/>:q.isError?<ErrorState message={errorMessage(q.error)} onRetry={()=>q.refetch()}/>
  :items.length===0?<EmptyState title="No audit entries" body="No audit entries match this filter."/>
  :<table className="table"><thead><tr><th>Timestamp</th><th>Administrator</th><th>Action</th><th>Target</th><th>Details</th></tr></thead><tbody>{items.map((a:any)=><tr key={a.id}><td>{fmtDateTime(a.createdAt)}</td><td>Admin #{a.actorAdminId||'—'}</td><td>{a.action}</td><td>{a.targetType||''} {a.targetId||''}</td><td>{a.reason||'—'}</td></tr>)}</tbody></table>}</div>;
}
