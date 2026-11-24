import { useEffect, useState } from 'react'; import { useMutation } from '@tanstack/react-query'; import { useNavigate, Link } from 'react-router-dom';
import { apiPost, errorMessage } from '../../api/client'; import { Button, Field, Input, PageHeader } from '../../design/primitives';
/** FR-AUTH-04: verify the phone OTP the registration step sent.
 *  Backend contract is POST /api/auth/verify-phone {userId, otp}; the user id
 *  comes from the registration response (stored in session storage, never
 *  typed in by hand). Resend is POST /api/auth/otp/resend {userId} with a
 *  60-second cooldown and per-user/per-IP caps enforced server-side. */
const OTP_USER_KEY = 'unilink:otpUserId';
export default function OtpStep(){ const [code,setCode]=useState(''); const [cooldown,setCooldown]=useState(0); const nav=useNavigate();
  const [userId] = useState<number|undefined>(()=>{ const v = sessionStorage.getItem(OTP_USER_KEY); return v===null ? undefined : Number(v); });
  useEffect(()=>{ if(cooldown<=0) return; const t=setTimeout(()=>setCooldown(c=>c-1),1000); return ()=>clearTimeout(t); },[cooldown]);
  const resend = useMutation({ mutationFn: ()=>apiPost<{status?:string; channel?:string}>('/auth/otp/resend',{userId}), onSuccess: ()=>setCooldown(60) });
  const verify = useMutation({ mutationFn: ()=>apiPost('/auth/verify-phone',{userId, otp: code}), onSuccess: ()=>{ sessionStorage.removeItem(OTP_USER_KEY); nav('/pending'); } });
  // Registration reports whether the verification email actually went out
  // (emailSent=false when SMTP delivery failed). Offer a resend here
  // instead of silently leaving the user without a link.
  const [emailSent, setEmailSent] = useState<boolean>(()=> sessionStorage.getItem('unilink:emailSent') !== 'false');
  const resendEmail = useMutation({ mutationFn: ()=>apiPost('/auth/email-verification/resend',{userId}), onSuccess: ()=>{ setEmailSent(true); sessionStorage.setItem('unilink:emailSent','true'); } });
  return <div className="container" style={{maxWidth:560}}><PageHeader eyebrow="Verification · OTP" title="Enter your OTP" lead="The OTP was sent to your phone (or email if SMS failed) when you signed up. It expires in 5 minutes and allows at most 5 attempts (FR-AUTH-04)." />
  {userId===undefined
    ? <p role="alert">We could not find your new account — the sign-up step may not have finished. Please <Link to="/signup">sign up again</Link>.</p>
    : <form onSubmit={e=>{e.preventDefault(); verify.mutate();}}>
    <Field label="6-digit OTP" htmlFor="otp"><Input id="otp" inputMode="numeric" value={code} onChange={e=>setCode(e.target.value)} /></Field>
    {verify.isError && <p role="alert" className="field-error">{errorMessage(verify.error)}</p>}
    {resend.isError && <p role="alert" className="field-error">{errorMessage(resend.error)}</p>}
    {!emailSent && <><p role="alert" className="field-error">We could not send your verification email. Your account was created — you can still verify your phone number, and resend the email below.</p><Button type="button" onClick={()=>resendEmail.mutate()} disabled={resendEmail.isPending}>Resend verification email</Button></>}
    {resendEmail.isSuccess && <p role="status">Verification email resent. Check your inbox.</p>}
    {resendEmail.isError && <p role="alert" className="field-error">{errorMessage(resendEmail.error)}</p>}
    {resend.isSuccess && <p role="status">A new OTP was sent{resend.data?.channel ? ` by ${resend.data.channel}` : ''}.</p>}
    <div style={{display:'flex',gap:8}}><Button type="button" onClick={()=>resend.mutate()} disabled={resend.isPending || cooldown>0}>{cooldown>0 ? `Resend OTP (${cooldown}s)` : 'Resend OTP'}</Button><Button type="submit" variant="primary" disabled={verify.isPending}>Verify OTP</Button></div></form>}
  <p className="secondary" style={{marginTop:16}}>Also check your email for the verification link: <Link to="/verify-email">Verify email</Link>.</p></div>; }
