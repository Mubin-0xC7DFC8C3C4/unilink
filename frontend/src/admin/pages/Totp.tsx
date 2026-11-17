import { useState } from 'react'; import { useMutation } from '@tanstack/react-query'; import { useLocation, useNavigate } from 'react-router-dom';
import { apiPost, errorMessage } from '../../api/client'; import { Button, Field, Input, PageHeader } from '../../design/primitives'; import { useAuth } from '../../app/auth';
/** FR-ADM-01 / FR-MOD-01: TOTP enrolment then verification.
 *  First login (stage NEEDS_ENROLMENT, otpauthUrl from the login response):
 *  scan the otpauth URL, then confirm with a code — POST /admin/auth/totp/enrol
 *  {code} returns the 10 recovery codes, shown once. Then (or on later logins,
 *  stage NEEDS_TOTP): POST /admin/auth/totp/verify {code} — a recovery code
 *  works here too. Only a successful verify completes MFA and signs the admin in. */
export default function Totp(){ const [code,setCode]=useState(''); const [recoveryCodes,setRecoveryCodes]=useState<string[]>([]);
  const nav=useNavigate(); const { setUser }=useAuth();
  const locState = (useLocation().state || {}) as { stage?: string; otpauthUrl?: string };
  const stage = locState.stage || 'NEEDS_TOTP'; const otpauthUrl = locState.otpauthUrl || '';
  const enrol=useMutation({ mutationFn: ()=>apiPost<{status?:string; recoveryCodes?:string[]}>('/admin/auth/totp/enrol',{code}), onSuccess:(d)=>setRecoveryCodes(d.recoveryCodes||[]) });
  const verify=useMutation({ mutationFn: ()=>apiPost<{adminId?:number; email?:string; role?:string; mustChangePassword?:boolean}>('/admin/auth/totp/verify',{code}), onSuccess:(d)=>{ setUser({id:d.adminId, email:d.email, role:d.role, mustChangePassword: d.mustChangePassword===true}); // D-094/D-098: a temporary-password login goes to the admin
    // change-password screen, not the dashboard (whose calls would 403).
    nav(d.mustChangePassword ? '/admin/change-password' : '/admin'); } });
  const enrolled = recoveryCodes.length>0;
  return <div className="container" style={{maxWidth:480,paddingTop:48}}><PageHeader eyebrow="Admin Portal · Two-factor" title="TOTP verification" lead="Enter the 6-digit code from your authenticator app. First login: enrol TOTP and save your recovery codes. A recovery code also works as the verification code." />
  {stage==='NEEDS_ENROLMENT' && !enrolled && <>
    {otpauthUrl
      ? <p role="status">First login — add this account to your authenticator app: <code style={{wordBreak:'break-all'}}>{otpauthUrl}</code></p>
      : <p className="secondary">First login — enrolment needs the otpauth URL from the login step. Please log in again.</p>}
    <form onSubmit={e=>{e.preventDefault(); enrol.mutate();}}><Field label="Code from your authenticator app (confirms enrolment)" htmlFor="enrol-code"><Input id="enrol-code" inputMode="numeric" value={code} onChange={e=>setCode(e.target.value)} /></Field>
    {enrol.isError && <p role="alert" className="field-error">{errorMessage(enrol.error)}</p>}<Button type="submit" variant="primary" disabled={enrol.isPending}>Enrol TOTP</Button></form></>}
  {enrolled && <div role="status" style={{marginTop:16}}><p><strong>Save your recovery codes now — they are shown only once.</strong> Each code works once, in place of a TOTP code.</p><ul>{recoveryCodes.map(c=><li key={c}><code>{c}</code></li>)}</ul></div>}
  {(stage!=='NEEDS_ENROLMENT' || enrolled) && <form onSubmit={e=>{e.preventDefault(); verify.mutate();}} style={{marginTop:16}}><Field label="TOTP or recovery code" htmlFor="totp"><Input id="totp" inputMode="numeric" value={code} onChange={e=>setCode(e.target.value)} /></Field>
  {verify.isError && <p role="alert" className="field-error">{errorMessage(verify.error)}</p>}<Button type="submit" variant="primary" disabled={verify.isPending}>Verify and enter admin</Button></form>}</div>;
}
