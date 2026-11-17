import { useForm } from 'react-hook-form'; import { z } from 'zod'; import { zodResolver } from '@hookform/resolvers/zod';
import { Link, useNavigate } from 'react-router-dom'; import { useMutation } from '@tanstack/react-query';
import { apiPost, errorMessage } from '../../api/client'; import { Button, Field, Input, PageHeader, SessionExpiredNotice } from '../../design/primitives'; import { useAuth } from '../../app/auth';
const schema = z.object({ email: z.string().email('Enter a valid email address'), password: z.string().min(1,'Enter your password') });
export default function Login(){ const nav=useNavigate(); const { setUser }=useAuth();
  const { register, handleSubmit, formState:{errors} } = useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema) });
  const mut = useMutation({ mutationFn: (d: z.infer<typeof schema>)=>apiPost<{fullName?:string; email?:string; state?:string}>('/auth/login', d), onSuccess:(data, vars)=>{ setUser({fullName:data.fullName, email: vars.email, verified: data.state==='VERIFIED'}); nav('/feed'); } });
  return <div className="container" style={{maxWidth:480}}><PageHeader eyebrow="Welcome back" title="Log in to UniLink" />
  <SessionExpiredNotice />
  <form onSubmit={handleSubmit(d=>mut.mutate(d))} noValidate><Field label="Email" error={errors.email?.message} htmlFor="email"><Input id="email" type="email" {...register('email')} autoComplete="email"/></Field>
  <Field label="Password" error={errors.password?.message} htmlFor="password"><Input id="password" type="password" {...register('password')} autoComplete="current-password"/></Field>
  {/* A 401 here is a failed sign-in, not an expired session: errorMessage's
      401 text ("session expired") would mislead, so login failures name
      the actual cause. */}
  {mut.isError && <p role="alert" className="field-error">{(mut.error as any)?.status===401 ? 'Incorrect email or password. Please try again.' : errorMessage(mut.error)}</p>}
  <Button type="submit" variant="primary" disabled={mut.isPending}>{mut.isPending?'Logging in…':'Log in'}</Button></form>
  <p className="secondary" style={{marginTop:16}}><Link to="/forgot-password">Forgot password?</Link> · <Link to="/signup">Create an account</Link></p></div>; }
