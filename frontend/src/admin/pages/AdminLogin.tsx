import { useForm } from 'react-hook-form'; import { z } from 'zod'; import { zodResolver } from '@hookform/resolvers/zod';
import { useNavigate } from 'react-router-dom'; import { useMutation } from '@tanstack/react-query';
import { apiPost, errorMessage } from '../../api/client'; import { Button, Field, Input, PageHeader, SessionExpiredNotice } from '../../design/primitives';
const schema=z.object({ email: z.string().email(), password: z.string().min(12,'Admin passwords are at least 12 characters') });
/** FR-ADM-01: POST /api/admin/auth/login returns {stage, otpauthUrl?} —
 *  NEEDS_ENROLMENT on first login (otpauthUrl to scan), NEEDS_TOTP otherwise.
 *  The TOTP page is always next; MFA is not complete until /totp/verify. */
export default function AdminLogin(){ const nav=useNavigate();
  const { register, handleSubmit, formState:{errors} }=useForm<z.infer<typeof schema>>({resolver:zodResolver(schema)});
  const mut=useMutation({ mutationFn:(d:z.infer<typeof schema>)=>apiPost<{stage?:string; otpauthUrl?:string}>('/admin/auth/login',d), onSuccess:(data)=>nav('/admin/totp', { state: { stage: data.stage, otpauthUrl: data.otpauthUrl } }) });
  return <div className="container" style={{maxWidth:480,paddingTop:48}}><PageHeader eyebrow="Admin Portal" title="Admin log in" lead="Email and password, then mandatory two-factor authentication (TOTP). Admin accounts are created only by a Platform Admin — there is no public registration." />
  <SessionExpiredNotice />
  <form onSubmit={handleSubmit(d=>mut.mutate(d))} noValidate><Field label="Email" error={errors.email?.message} htmlFor="email"><Input id="email" type="email" {...register('email')} autoComplete="email"/></Field>
  <Field label="Password" error={errors.password?.message} htmlFor="password"><Input id="password" type="password" {...register('password')} autoComplete="current-password"/></Field>
  {/* As in Login: a 401 here is a failed sign-in, not an expired session. */}
  {mut.isError && <p role="alert" className="field-error">{(mut.error as any)?.status===401 ? 'Incorrect email or password. Please try again.' : errorMessage(mut.error)}</p>}
  <Button type="submit" variant="primary" disabled={mut.isPending}>Log in</Button></form></div>;
}
