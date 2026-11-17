import { useState } from 'react'; import { useMutation } from '@tanstack/react-query'; import { Link } from 'react-router-dom'; import { apiPost, errorMessage } from '../../api/client';
import { Button, Field, Input, PageHeader } from '../../design/primitives';
/** FR-AUTH-11: two backend steps — POST /api/auth/password-reset/request
 *  {email}, then POST /api/auth/password-reset/confirm {email, otp, newPassword}.
 *  There is no separate verify step on the backend; the code is checked when
 *  the new password is set. The request always succeeds silently server-side
 *  (it never reveals whether the email exists). */
export default function ForgotPassword(){ const [email,setEmail]=useState(''); const [code,setCode]=useState(''); const [pw,setPw]=useState(''); const [pwError,setPwError]=useState(''); const [step,setStep]=useState(1);
  const request = useMutation({ mutationFn: ()=>apiPost('/auth/password-reset/request',{email}), onSuccess: ()=>setStep(2) });
  const confirm = useMutation({ mutationFn: ()=>apiPost('/auth/password-reset/confirm',{email, otp: code, newPassword: pw}), onSuccess: ()=>setStep(3) });
  const submitConfirm = (e: React.FormEvent)=>{ e.preventDefault();
    if (pw.length<8 || !/[A-Z]/.test(pw) || !/[a-z]/.test(pw) || !/[0-9]/.test(pw)){ setPwError('Password must be at least 8 characters with an upper-case letter, a lower-case letter and a digit.'); return; }
    setPwError(''); confirm.mutate(); };
  return <div className="container" style={{maxWidth:480}}><PageHeader eyebrow="Account recovery" title="Forgot password" lead="Request a reset code by email, then enter the code with a new password. Signing in after a reset ends other sessions." />
  {step===1 && <form onSubmit={e=>{e.preventDefault(); request.mutate();}}><Field label="Email" htmlFor="email"><Input id="email" type="email" value={email} onChange={e=>setEmail(e.target.value)} /></Field>{request.isError && <p role="alert" className="field-error">{errorMessage(request.error)}</p>}<Button variant="primary" type="submit">Send reset code</Button></form>}
  {step===2 && <form onSubmit={submitConfirm}><p className="secondary">If the email exists, a reset code was sent to it. The code expires in 15 minutes.</p>
  <Field label="Reset code" htmlFor="code"><Input id="code" value={code} onChange={e=>setCode(e.target.value)} /></Field>
  <Field label="New password (8+ characters, upper, lower, digit)" error={pwError || undefined} htmlFor="pw"><Input id="pw" type="password" value={pw} onChange={e=>setPw(e.target.value)} /></Field>
  {confirm.isError && <p role="alert" className="field-error">{errorMessage(confirm.error)}</p>}<Button variant="primary" type="submit">Reset password</Button></form>}
  {step===3 && <p role="status">Your password has been reset. You can now <Link to="/login">log in</Link>.</p>}</div>; }
