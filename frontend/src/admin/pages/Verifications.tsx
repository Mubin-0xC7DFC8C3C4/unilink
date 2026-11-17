import { useState, useEffect } from 'react'; import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiGet, apiPost, errorMessage } from '../../api/client'; import { Button, Field, Input, PageHeader, Skeleton, ErrorState, EmptyState } from '../../design/primitives'; import type { VerificationItem } from '../../api/types';
export default function Verifications(){ const [status,setStatus]=useState(''); const [selected,setSelected]=useState<VerificationItem|null>(null); const [reason,setReason]=useState(''); const qc=useQueryClient();
  const q=useQuery({queryKey:['admin-verifications',status], queryFn:()=>apiGet<VerificationItem[]>(`/admin/verification-queue${status?`?status=${status}`:''}`), retry:false});
  const decide=useMutation({ mutationFn: ({id,action}:{id:string|number;action:'approve'|'reject'})=>apiPost(`/admin/verification/${id}/${action}`,{reason: action==='reject'?reason:undefined}), onSuccess: ()=>{ qc.invalidateQueries({queryKey:['admin-verifications']}); setSelected(null); setReason(''); } });
  useEffect(()=>{ const h=(e:KeyboardEvent)=>{ if(!selected) return; if(e.key==='a'&&reason==='' ) {} if(e.key==='A'){ decide.mutate({id:selected.id,action:'approve'}); } }; window.addEventListener('keydown',h); return ()=>window.removeEventListener('keydown',h); },[selected,decide,reason]);
  const items=Array.isArray(q.data)?q.data:(q.data as any)?.items??[];
  return <div><PageHeader eyebrow="Admin Portal" title="Verification queue" lead="Review entered data, ID image, OCR text and score, previous attempts and the email-domain hint. Approve, or reject with a mandatory reason. Keyboard: press A to approve the selected request (reject always needs a reason)." />
  <label htmlFor="vf">Filter</label>{' '}<select id="vf" value={status} onChange={e=>setStatus(e.target.value)} className="input-search"><option value="">All</option><option value="PENDING_REVIEW">Pending</option><option value="APPROVED">Approved</option><option value="REJECTED">Rejected</option></select>
  {q.isPending?<Skeleton/>:q.isError?<ErrorState message={errorMessage(q.error)} onRetry={()=>q.refetch()}/>
  :items.length===0?<EmptyState title="Queue is empty" body="No verification requests match this filter."/>:
  <div className="split" style={{marginTop:16}}><table className="table" aria-label="Verification queue"><thead><tr><th>Name</th><th>Email</th><th>University</th><th>Status</th><th></th></tr></thead><tbody>{items.map((v:any)=><tr key={v.id}><td>{v.fullName}</td><td>{v.email}</td><td>{v.university||'—'}</td><td>{v.status||'PENDING_REVIEW'}</td><td><Button onClick={()=>setSelected(v)}>Review</Button></td></tr>)}</tbody></table>
  <section aria-label="Review pane">{selected?<div className="card-group"><h2 className="title">Review: {selected.fullName}</h2><div className="row"><span>Email</span><span>{selected.email}</span></div><div className="row"><span>Student ID</span><span>{selected.studentId||'—'}</span></div><div className="row"><span>OCR score</span><span>{selected.ocrMatchScore!=null?String(selected.ocrMatchScore):'Not available'}</span></div><div className="row"><span>OCR text</span><span>{selected.ocrText||'Not available'}</span></div><div className="row"><span>Previous attempts</span><span>{selected.previousAttempts??'—'}</span></div><p className="secondary">Email-domain hint: check the domain matches the university's registered domains (master data).</p>
  <Field label="Reason (mandatory for rejection)" htmlFor="reason"><Input id="reason" value={reason} onChange={e=>setReason(e.target.value)} /></Field>
  <IdImage requestId={selected.id} name={selected.fullName} />
  <div style={{display:'flex',gap:8}}><Button variant="primary" onClick={()=>decide.mutate({id:selected.id,action:'approve'})} disabled={decide.isPending}>Approve (A)</Button><Button onClick={()=>decide.mutate({id:selected.id,action:'reject'})} disabled={!reason.trim()||decide.isPending}>Reject</Button></div>
  {decide.isError && <p role="alert" className="field-error">{errorMessage(decide.error)}</p>}</div>:<p className="secondary">Select a request to review it.</p>}</section></div>}</div>;
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
