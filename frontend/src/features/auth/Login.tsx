import { useState } from 'react';
import { useForm } from 'react-hook-form'; import { z } from 'zod'; import { zodResolver } from '@hookform/resolvers/zod';
import { Link, useNavigate } from 'react-router-dom'; import { useMutation } from '@tanstack/react-query';
import { apiPost, errorMessage } from '../../api/client'; import { Button, Field, Input, PageHeader, SessionExpiredNotice } from '../../design/primitives'; import { useAuth } from '../../app/auth';
const schema = z.object({ email: z.string().email('Enter a valid email address'), password: z.string().min(1,'Enter your password') });
export default function Login(){ const nav=useNavigate(); const { setUser }=useAuth();
  const [showPassword, setShowPassword] = useState(false);
  const { register, handleSubmit, formState:{errors} } = useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema) });
  const mut = useMutation({ mutationFn: (d: z.infer<typeof schema>)=>apiPost<{fullName?:string; email?:string; state?:string; mustChangePassword?:boolean}>('/auth/login', d), onSuccess:(data, vars)=>{ setUser({fullName:data.fullName, email: vars.email, verified: data.state==='VERIFIED', mustChangePassword: data.mustChangePassword===true}); // D-094/D-098: a temporary-password login goes straight to the
    // change-password screen instead of the feed (whose calls would 403).
    nav('/feed'); } });
  return <div className="container" style={{maxWidth:480}}>
  <div style={{display:'flex', alignItems:'center', gap:10, paddingTop:32}} aria-label="UniLink">
    <span aria-hidden="true" style={{width:32, height:32, borderRadius:8, background:'var(--text-primary)', color:'var(--background)', display:'grid', placeItems:'center', fontWeight:700}}>U</span>
    <span style={{fontWeight:700, fontSize:'var(--title)'}}>UniLink</span>
  </div>
  <PageHeader eyebrow="Welcome back" title="Log in to UniLink" />
  <SessionExpiredNotice />
  <form onSubmit={handleSubmit(d=>mut.mutate(d))} noValidate><Field label="Email address" error={errors.email?.message} htmlFor="email"><Input id="email" type="email" {...register('email')} autoComplete="email"/></Field>
  <Field label="Password" error={errors.password?.message} htmlFor="password"><span style={{position:'relative', display:'block'}}><Input id="password" type={showPassword?'text':'password'} {...register('password')} autoComplete="current-password" style={{width:'100%', paddingRight:64}}/><button type="button" aria-pressed={showPassword} aria-label={showPassword?'Hide password':'Show password'} onClick={()=>setShowPassword(s=>!s)} style={{position:'absolute', right:6, top:'50%', transform:'translateY(-50%)', background:'none', border:'none', padding:'4px 6px', fontWeight:600, cursor:'pointer', color:'var(--text-secondary)'}}>{showPassword?'Hide':'Show'}</button></span></Field>
  {/* A 401 here is a failed sign-in, not an expired session: errorMessage's
      401 text ("session expired") would mislead, so login failures name
      the actual cause. */}
  {mut.isError && <p role="alert" className="field-error">{(mut.error as any)?.status===401 ? 'Incorrect email or password. Please try again.' : errorMessage(mut.error)}</p>}
  <Button type="submit" variant="primary" disabled={mut.isPending}>{mut.isPending?'Logging in…':'Log in'}</Button></form>
  <p className="secondary" style={{marginTop:16}}><Link to="/forgot-password">Forgot password?</Link> · Don't have an account? <Link to="/signup">Sign up</Link></p></div>; }
