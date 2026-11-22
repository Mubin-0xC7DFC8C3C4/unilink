import { useState } from 'react';
import { useForm } from 'react-hook-form'; import { z } from 'zod'; import { zodResolver } from '@hookform/resolvers/zod';
import { useNavigate } from 'react-router-dom'; import { useMutation } from '@tanstack/react-query';
import { apiPost, errorMessage } from '../../api/client'; import { Button, Field, Input, PageHeader, SessionExpiredNotice } from '../../design/primitives';
const schema=z.object({ email: z.string().email(), password: z.string().min(12,'Admin passwords are at least 12 characters') });
/** FR-ADM-01: POST /api/admin/auth/login returns {stage, otpauthUrl?} —
 *  NEEDS_ENROLMENT on first login (otpauthUrl to scan), NEEDS_TOTP otherwise.
 *  The TOTP page is always next; MFA is not complete until /totp/verify.
 *  Phase 2.5 restyle: admin chrome (portal badge, back-to-student-site,
 *  monitoring footer) lives in this page — the login route is not wrapped
 *  in AdminLayout. There is no public admin registration and no admin
 *  password-reset flow: forgot credentials are handled by contacting the
 *  system administrator, exactly as the page states. */
export default function AdminLogin(){ const nav=useNavigate();
  const [showPassword, setShowPassword] = useState(false);
  const { register, handleSubmit, formState:{errors} }=useForm<z.infer<typeof schema>>({resolver:zodResolver(schema)});
  const mut=useMutation({ mutationFn:(d:z.infer<typeof schema>)=>apiPost<{stage?:string; otpauthUrl?:string}>('/admin/auth/login',d), onSuccess:(data)=>nav('/admin/totp', { state: { stage: data.stage, otpauthUrl: data.otpauthUrl } }) });
  return <div style={{minHeight:'100vh', display:'flex', flexDirection:'column'}}>
  <header style={{borderBottom:'1px solid var(--border)'}}><div className="container" style={{display:'flex', alignItems:'center', gap:12, paddingTop:14, paddingBottom:14}}>
    <span style={{fontWeight:600, fontSize:18}}>UniLink</span>
    <span style={{border:'1px solid var(--border)', borderRadius:'var(--radius-full)', padding:'2px 10px', fontSize:11, fontWeight:600, letterSpacing:'0.08em', color:'var(--text-secondary)'}}>ADMIN PORTAL</span>
    <span style={{marginLeft:'auto'}}><a href="/">Back to Student Site</a></span>
  </div></header>
  <main style={{flex:1}}><div className="container" style={{maxWidth:480, paddingTop:48}}><PageHeader eyebrow="Restricted access" title="Sign in to Admin Console" lead="Authorized university administrators and moderators only." />
  <SessionExpiredNotice />
  <form onSubmit={handleSubmit(d=>mut.mutate(d))} noValidate><Field label="Admin email or ID" error={errors.email?.message} htmlFor="email"><Input id="email" type="email" {...register('email')} autoComplete="email"/></Field>
  <Field label="Password" error={errors.password?.message} htmlFor="password"><span style={{position:'relative', display:'block'}}><Input id="password" type={showPassword?'text':'password'} {...register('password')} autoComplete="current-password" style={{width:'100%', paddingRight:64}}/><button type="button" aria-pressed={showPassword} aria-label={showPassword?'Hide password':'Show password'} onClick={()=>setShowPassword(s=>!s)} style={{position:'absolute', right:6, top:'50%', transform:'translateY(-50%)', background:'none', border:'none', padding:'4px 6px', fontWeight:600, cursor:'pointer', color:'var(--text-secondary)'}}>{showPassword?'Hide':'Show'}</button></span></Field>
  {/* As in Login: a 401 here is a failed sign-in, not an expired session.
      The message is deliberately uniform — it never reveals whether the
      email or the password was wrong. Other failures (for example an
      IP allow-list block) keep their own server message. */}
  {mut.isError && <p role="alert" className="field-error">{(mut.error as any)?.status===401 ? 'Invalid admin credentials. Please verify your email and password.' : errorMessage(mut.error)}</p>}
  <Button type="submit" variant="primary" disabled={mut.isPending}>{mut.isPending?'Signing in…':'Sign in to Console'}</Button></form>
  <p className="secondary" style={{marginTop:16}}>Forgot credentials? Contact system administrator.</p></div></main>
  <footer style={{borderTop:'1px solid var(--border)', marginTop:48}}><div className="container" style={{paddingTop:16, paddingBottom:16}}><p className="secondary" style={{margin:0, textAlign:'center'}}>UniLink Administration · All login attempts are monitored and logged.</p></div></footer></div>;
}
