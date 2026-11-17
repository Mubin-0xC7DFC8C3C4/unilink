import { useForm } from 'react-hook-form'; import { z } from 'zod'; import { zodResolver } from '@hookform/resolvers/zod';
import { useNavigate } from 'react-router-dom'; import { useMutation } from '@tanstack/react-query';
import { apiPost, errorMessage } from '../../api/client'; import { Button, Field, Input, PageHeader } from '../../design/primitives';
const schema=z.object({ email: z.string().email(), password: z.string().min(12,'Admin passwords are at least 12 characters') });
export default function AdminLogin(){ const nav=useNavigate();
  const { register, handleSubmit, formState:{errors} }=useForm<z.infer<typeof schema>>({resolver:zodResolver(schema)});
  const mut=useMutation({ mutationFn:(d:z.infer<typeof schema>)=>apiPost<{totpRequired?:boolean}>('/admin/login',d), onSuccess:(data)=>nav(data.totpRequired===false?'/admin':'/admin/totp') });
  return <div className="container" style={{maxWidth:480,paddingTop:48}}><PageHeader eyebrow="Admin Portal" title="Admin log in" lead="Email and password, then mandatory two-factor authentication (TOTP). Admin accounts are created only by a Platform Admin — there is no public registration." />
  <form onSubmit={handleSubmit(d=>mut.mutate(d))} noValidate><Field label="Email" error={errors.email?.message} htmlFor="email"><Input id="email" type="email" {...register('email')} autoComplete="email"/></Field>
  <Field label="Password" error={errors.password?.message} htmlFor="password"><Input id="password" type="password" {...register('password')} autoComplete="current-password"/></Field>
  {mut.isError && <p role="alert" className="field-error">{errorMessage(mut.error)}</p>}
  <Button type="submit" variant="primary" disabled={mut.isPending}>Log in</Button></form></div>;
}
