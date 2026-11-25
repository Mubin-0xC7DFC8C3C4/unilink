import { useState } from 'react'; import { useMutation, useQuery } from '@tanstack/react-query'; import { Link } from 'react-router-dom';
import { apiGet, apiPost, errorMessage } from '../../api/client'; import { Button, Field, PageHeader, Skeleton, ErrorState } from '../../design/primitives';
import { IdUpload } from './SignUp';
/** D-190: a rejected application can be resubmitted with new photos of
 *  the front and/or the back of the ID card. Previously this screen sent
 *  an empty FormData — no image at all — so a rejection for a bad photo
 *  could never be fixed from here. A side that is not re-uploaded is
 *  carried over from the stored image (the backend keeps it). */
const ID_MAX_MB = 5;
const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
export default function Pending(){
  const q = useQuery({ queryKey:['verification-status'], queryFn: ()=>apiGet<{state:string; rejectionReason?:string}>('/auth/verification/status'), retry:false });
  const [idFile, setIdFile] = useState<File | null>(null);
  const [idBackFile, setIdBackFile] = useState<File | null>(null);
  const [fileErrorMsg, setFileErrorMsg] = useState<string | undefined>(undefined);
  const pick = (side: 'front' | 'back') => (f: File | null) => {
    const set = side === 'front' ? setIdFile : setIdBackFile;
    if (f === null) { set(null); setFileErrorMsg(undefined); return; }
    if (!IMAGE_TYPES.includes(f.type)) { setFileErrorMsg('Use a JPG, PNG or WebP image.'); return; }
    if (f.size > ID_MAX_MB * 1024 * 1024) { setFileErrorMsg(`Image must be ${ID_MAX_MB} MB or smaller.`); return; }
    setFileErrorMsg(undefined); set(f);
  };
  const resubmit = useMutation({ mutationFn: () => {
    const fd = new FormData();
    if (idFile) fd.append('idCard', idFile);
    if (idBackFile) fd.append('idCardBack', idBackFile);
    return apiPost('/auth/verification/resubmit', fd);
  }});
  return <div className="container"><PageHeader eyebrow="Verification" title="Pending Review" lead="Your application is waiting for an admin or university moderator. You will be notified of the decision. Rejected applications can be resubmitted up to 3 times in 24 hours (FR-AUTH-08)." />
  {q.isPending?<Skeleton/>:q.isError?<ErrorState message={errorMessage(q.error)} onRetry={()=>q.refetch()}/>: <div className="card-group"><div className="row"><span>Status</span><strong>{(q.data as any).state || 'PENDING_REVIEW'}</strong></div>{(q.data as any).rejectionReason && <div className="row"><span>Reason</span><span>{(q.data as any).rejectionReason}</span></div>}</div>}
  {(q.data as any)?.state==='REJECTED' && <><p className="secondary">Your application was rejected. You may upload new photos of your ID card and resubmit — any side you don’t re-upload stays as it was.</p>
    <Field label="Student ID card — front" error={fileErrorMsg} valid={idFile !== null && !fileErrorMsg} validText="Looks good" htmlFor="resubmitIdCard">
      <IdUpload id="resubmitIdCard" file={idFile} maxMB={ID_MAX_MB} actionLabel="Upload new front photo" onPick={pick('front')} />
    </Field>
    <Field label="Student ID card — back" error={fileErrorMsg} valid={idBackFile !== null && !fileErrorMsg} validText="Looks good" htmlFor="resubmitIdCardBack">
      <IdUpload id="resubmitIdCardBack" file={idBackFile} maxMB={ID_MAX_MB} actionLabel="Upload new back photo" onPick={pick('back')} />
    </Field>
    <Button variant="primary" onClick={()=>resubmit.mutate()} disabled={resubmit.isPending}>Resubmit application</Button>{resubmit.isError && <p role="alert" className="field-error">{errorMessage(resubmit.error)}</p>}{resubmit.isSuccess && <p role="status">Resubmitted — your application is pending review again.</p>}</>}
  <p style={{marginTop:16}}><Link to="/login">Back to log in</Link></p></div>;
}
