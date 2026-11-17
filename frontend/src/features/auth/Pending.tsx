import { useMutation, useQuery } from '@tanstack/react-query'; import { Link } from 'react-router-dom';
import { apiGet, apiPost, errorMessage } from '../../api/client'; import { Button, PageHeader, Skeleton, ErrorState } from '../../design/primitives';
export default function Pending(){
  const q = useQuery({ queryKey:['verification-status'], queryFn: ()=>apiGet<{state:string; rejectionReason?:string}>('/auth/verification/status'), retry:false });
  const resubmit = useMutation({ mutationFn: ()=>apiPost('/auth/verification/resubmit', new FormData()) });
  return <div className="container"><PageHeader eyebrow="Verification" title="Pending Review" lead="Your application is waiting for an admin or university moderator. You will be notified of the decision. Rejected applications can be resubmitted up to 3 times in 24 hours (FR-AUTH-08)." />
  {q.isPending?<Skeleton/>:q.isError?<ErrorState message={errorMessage(q.error)} onRetry={()=>q.refetch()}/>: <div className="card-group"><div className="row"><span>Status</span><strong>{(q.data as any).state || 'PENDING_REVIEW'}</strong></div>{(q.data as any).rejectionReason && <div className="row"><span>Reason</span><span>{(q.data as any).rejectionReason}</span></div>}</div>}
  {(q.data as any)?.state==='REJECTED' && <><p className="secondary">Your application was rejected. You may correct your details and resubmit.</p><Button variant="primary" onClick={()=>resubmit.mutate()} disabled={resubmit.isPending}>Resubmit application</Button>{resubmit.isError && <p role="alert" className="field-error">{errorMessage(resubmit.error)}</p>}{resubmit.isSuccess && <p role="status">Resubmitted — your application is pending review again.</p>}</>}
  <p style={{marginTop:16}}><Link to="/login">Back to log in</Link></p></div>;
}
