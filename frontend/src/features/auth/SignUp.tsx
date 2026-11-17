import { useState } from 'react'; import { useForm } from 'react-hook-form'; import { z } from 'zod'; import { zodResolver } from '@hookform/resolvers/zod';
import { useNavigate } from 'react-router-dom'; import { useMutation } from '@tanstack/react-query';
import { apiPost, errorMessage } from '../../api/client'; import { Button, Field, Input, PageHeader } from '../../design/primitives';
export const signUpSchema = z.object({ fullName: z.string().min(2,'Enter your full name'), phone: z.string().min(11,'Enter a valid phone number'), email: z.string().email('Enter a valid email address'), university: z.string().min(2,'Select your university'), department: z.string().min(2,'Enter your department'), batch: z.string().min(1,'Enter your batch'), address: z.string().min(5,'Enter your address'), studentId: z.string().min(1,'Enter your student ID number'), password: z.string().min(8,'Password must be at least 8 characters').regex(/[A-Z]/,'Password needs an uppercase letter').regex(/[a-z]/,'Password needs a lowercase letter').regex(/[0-9]/,'Password needs a digit') });
export type SignUpForm = z.infer<typeof signUpSchema>;
export default function SignUp(){
  const [step,setStep]=useState(0); const nav=useNavigate();
  const { register, handleSubmit, formState:{errors}, trigger } = useForm<SignUpForm>({ resolver: zodResolver(signUpSchema) });
  const [idFile,setIdFile]=useState<File|null>(null); const [photoFile,setPhotoFile]=useState<File|null>(null);
  const mut = useMutation({ mutationFn: async (data: SignUpForm)=>{ const fd=new FormData(); Object.entries(data).forEach(([k,v])=>fd.append(k,v)); if(idFile) fd.append('idCardImage',idFile); if(photoFile) fd.append('profilePhoto',photoFile); return apiPost('/auth/register', fd); }, onSuccess: ()=>nav('/otp') });
  const steps: (keyof SignUpForm)[][] = [['fullName','phone','email'],['university','department','batch','address'],['studentId','password']];
  const next = async ()=>{ if(await trigger(steps[step])) setStep(s=>s+1); };
  const onSubmit = (d: SignUpForm)=>{ if(!idFile){ alert('Please attach your student ID card image.'); return; } if(!photoFile){ alert('Please attach a profile photo.'); return; } mut.mutate(d); };
  return <div className="container" style={{maxWidth:640}}><PageHeader eyebrow={`Sign up · Step ${step+1} of 3`} title="Create your UniLink account" lead="FR-AUTH-01: full name, phone, email, university, department, batch, address, student ID, ID card image and profile photo. Password: 8+ characters with upper, lower and digit." />
  <form onSubmit={handleSubmit(onSubmit)} noValidate aria-label="Sign up form">
    {step===0 && <><Field label="Full name" error={errors.fullName?.message} htmlFor="fullName"><Input id="fullName" {...register('fullName')} autoComplete="name"/></Field>
    <Field label="Phone" error={errors.phone?.message} htmlFor="phone"><Input id="phone" type="tel" {...register('phone')} autoComplete="tel"/></Field>
    <Field label="Email" error={errors.email?.message} htmlFor="email"><Input id="email" type="email" {...register('email')} autoComplete="email"/></Field></>}
    {step===1 && <><Field label="University" error={errors.university?.message} htmlFor="university"><Input id="university" {...register('university')} autoComplete="organization"/></Field>
    <Field label="Department" error={errors.department?.message} htmlFor="department"><Input id="department" {...register('department')}/></Field>
    <Field label="Batch" error={errors.batch?.message} htmlFor="batch"><Input id="batch" {...register('batch')}/></Field>
    <Field label="Address" error={errors.address?.message} htmlFor="address"><Input id="address" {...register('address')} autoComplete="street-address"/></Field></>}
    {step===2 && <><Field label="Student ID number" error={errors.studentId?.message} htmlFor="studentId"><Input id="studentId" {...register('studentId')}/></Field>
    <Field label="Password" error={errors.password?.message} htmlFor="password"><Input id="password" type="password" {...register('password')} autoComplete="new-password"/></Field>
    <Field label="Student ID card image" htmlFor="idCard"><Input id="idCard" type="file" accept="image/*" onChange={e=>setIdFile(e.target.files?.[0]||null)} /></Field>
    <Field label="Profile photo" htmlFor="photo"><Input id="photo" type="file" accept="image/*" onChange={e=>setPhotoFile(e.target.files?.[0]||null)} /></Field></>}
    {mut.isError && <p role="alert" className="field-error">{errorMessage(mut.error)}</p>}
    <div style={{display:'flex',gap:8}}>{step>0 && <Button type="button" onClick={()=>setStep(s=>s-1)}>Back</Button>}
    {step<2 ? <Button type="button" variant="primary" onClick={next}>Continue →</Button> : <Button type="submit" variant="primary" disabled={mut.isPending}>{mut.isPending?'Creating account…':'Create account'}</Button>}</div>
  </form></div>;
}
