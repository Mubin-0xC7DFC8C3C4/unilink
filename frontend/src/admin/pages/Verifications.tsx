import { useState, useEffect } from 'react'; import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiGet, apiPost, errorMessage } from '../../api/client'; import { Button, Field, Input, PageHeader, Skeleton, ErrorState, EmptyState } from '../../design/primitives'; import type { VerificationItem } from '../../api/types';
import { ConfirmDialog, StatusBadge, Tabs, fmtDateTime } from '../adminUi';
export default function Verifications(){ const [status,setStatus]=useState('PENDING_REVIEW'); const [selected,setSelected]=useState<VerificationItem|null>(null); const [reason,setReason]=useState(''); const [confirmAction,setConfirmAction]=useState<'approve'|'reject'|null>(null); const qc=useQueryClient();
  const q=useQuery({queryKey:['admin-verifications',status], queryFn:()=>apiGet<VerificationItem[]>(`/admin/verification-queue${status?`?status=${status}`:''}`), retry:false});
  const decide=useMutation({ mutationFn: ({id,action}:{id:string|number;action:'approve'|'reject'})=>apiPost(`/admin/verification/${id}/${action}`,{reason: action==='reject'?reason:undefined}), onSuccess: ()=>{ qc.invalidateQueries({queryKey:['admin-verifications']}); setSelected(null); setReason(''); } });
  const items=Array.isArray(q.data)?q.data:(q.data as any)?.items??[];
  return <div><PageHeader eyebrow="Admin Portal" title="Pending Verification" lead="Review each student's details and ID card image, then approve or reject. Rejection always needs a reason, and every decision is recorded in the activity log." />
  <Tabs tabs={[{key:'PENDING_REVIEW',label:'Pending'},{key:'',label:'All'},{key:'APPROVED',label:'Approved'},{key:'REJECTED',label:'Rejected'}]} active={status} onChange={setStatus} />
  {q.isPending?<Skeleton/>:q.isError?<ErrorState message={errorMessage(q.error)} onRetry={()=>q.refetch()}/>
  :items.length===0?<EmptyState title="Queue is empty" body="No verification requests match this filter."/>:
  <div className="split" style={{marginTop:16}}><table className="table" aria-label="Verification queue"><thead><tr><th>Name</th><th>Email</th><th>University</th><th>Submitted</th><th>Status</th><th></th></tr></thead><tbody>{items.map((v:any)=><tr key={v.id}><td>{v.fullName}{v.duplicatePhone && <span className="pill" style={{marginLeft:8}} title="Another account shares this applicant's phone number">Duplicate phone</span>}</td><td>{v.email}</td><td>{v.university||'—'}</td><td>{fmtDateTime((v as any).submittedAt)}</td><td><StatusBadge status={v.status||'PENDING_REVIEW'}/></td><td><Button onClick={()=>setSelected(v)}>Review</Button></td></tr>)}</tbody></table>
  <section aria-label="Review pane">{selected?<div className="card-group"><h2 className="title">Review: {selected.fullName}</h2>{(selected as any).duplicatePhone && <p role="note" className="field-error">Duplicate phone: another account shares this applicant's phone number. Check carefully for duplicate accounts before approving.</p>}<div className="row"><span>Email</span><span>{selected.email}</span></div><div className="row"><span>Student ID</span><span>{selected.studentId||'—'}</span></div><div className="row"><span>OCR score</span><span>{selected.ocrMatchScore!=null?String(selected.ocrMatchScore):'Not available'}</span></div><div className="row"><span>OCR text</span><span>{selected.ocrText||'Not available'}</span></div><div className="row"><span>Previous attempts</span><span>{selected.previousAttempts??'—'}</span></div><p className="secondary">Check that the student's details match their university records before deciding.</p>
  <Field label="Reason (mandatory for rejection)" htmlFor="reason"><Input id="reason" value={reason} onChange={e=>setReason(e.target.value)} /></Field>
  <IdImage requestId={selected.id} name={selected.fullName} />
  <div style={{display:'flex',gap:8}}><Button variant="primary" onClick={()=>setConfirmAction('approve')} disabled={decide.isPending}>Approve</Button><Button onClick={()=>setConfirmAction('reject')} disabled={!reason.trim()||decide.isPending}>Reject</Button></div>
  {decide.isError && <p role="alert" className="field-error">{errorMessage(decide.error)}</p>}
  {confirmAction && <ConfirmDialog title={confirmAction==='approve' ? `Approve verification — ${selected.fullName}` : `Reject verification — ${selected.fullName}`}
    message={<p>{confirmAction==='approve' ? 'The student will be verified and can use UniLink fully. This is recorded in the activity log.' : <>The student will be told the verification was rejected. Reason given: <strong>{reason}</strong></>}</p>}
    confirmLabel={confirmAction==='approve' ? 'Approve student' : 'Reject verification'} busy={decide.isPending}
    onCancel={()=>setConfirmAction(null)} onConfirm={()=>{ decide.mutate({id:selected.id,action:confirmAction}); setConfirmAction(null); }} />}</div>:<p className="secondary">Select a request to review it.</p>}</section></div>}</div>;
}

/**
 * ID card image, streamed from the authorised backend endpoint only (D-053).
 * GET /api/admin/verification/{id}/id-image returns the JPEG bytes with
 * Cache-Control: no-store; the moderator-scope check runs server-side
 * (AdminService.verificationIdCardKey — 404 out of scope). The <img> is
 * same-origin, so the admin session cookie is sent automatically. No storage
 * key is ever exposed: none exists in any DTO.
 */
function IdImage({ requestId, name }: { requestId: string | number; name: string }) {
  const [state, setState] = useState<'loading' | 'ok' | 'error'>('loading');
  useEffect(() => { setState('loading'); }, [requestId]);
  const src = `/api/admin/verification/${requestId}/id-image`;
  return <div style={{ marginTop: 12 }}>
    <h3 className="title">ID card image</h3>
    {state === 'error'
      ? <p role="alert" className="field-error">ID image is not available — it may have been removed, or this request is outside your moderator scope.</p>
      : <><img src={src} alt={`ID card image for ${name}`} style={{ maxWidth: '100%', display: state === 'ok' ? 'block' : 'none' }} onLoad={() => setState('ok')} onError={() => setState('error')} />{state === 'loading' && <Skeleton label="ID image" />}</>}
  </div>;
}
