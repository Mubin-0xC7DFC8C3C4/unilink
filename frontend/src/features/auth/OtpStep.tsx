import { useState } from 'react'; import { useMutation } from '@tanstack/react-query'; import { useNavigate, Link } from 'react-router-dom';
import { apiPost, errorMessage } from '../../api/client'; import { Button, Field, Input, PageHeader } from '../../design/primitives';
export default function OtpStep(){ const [code,setCode]=useState(''); const [phone,setPhone]=useState(''); const nav=useNavigate();
  const send = useMutation({ mutationFn: ()=>apiPost('/auth/otp/send',{phone}) });
  const verify = useMutation({ mutationFn: ()=>apiPost('/auth/otp/verify',{phone, code}), onSuccess: ()=>nav('/pending') });
  return <div className="container" style={{maxWidth:560}}><PageHeader eyebrow="Verification · OTP" title="Enter your OTP" lead="The OTP is sent to your phone or email, expires in 5 minutes, and allows at most 5 attempts (FR-AUTH-04)." />
  <form onSubmit={e=>{e.preventDefault(); verify.mutate();}}><Field label="Phone or email used at sign-up" htmlFor="phone"><Input id="phone" value={phone} onChange={e=>setPhone(e.target.value)} /></Field>
  <Field label="6-digit OTP" htmlFor="otp"><Input id="otp" inputMode="numeric" value={code} onChange={e=>setCode(e.target.value)} /></Field>
  {verify.isError && <p role="alert" className="field-error">{errorMessage(verify.error)}</p>}
  <div style={{display:'flex',gap:8}}><Button type="button" onClick={()=>send.mutate()} disabled={!phone||send.isPending}>Send OTP</Button><Button type="submit" variant="primary" disabled={verify.isPending}>Verify OTP</Button></div></form>
  <p className="secondary" style={{marginTop:16}}>Also check your email for the verification link: <Link to="/verify-email">Verify email</Link>.</p></div>; }
