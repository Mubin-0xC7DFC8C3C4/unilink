import { useState } from 'react'; import { useForm } from 'react-hook-form'; import { z } from 'zod'; import { zodResolver } from '@hookform/resolvers/zod';
import { useNavigate } from 'react-router-dom'; import { useMutation, useQuery } from '@tanstack/react-query';
import { apiGet, apiPost, errorMessage } from '../../api/client';
import { BD_PHONE_MESSAGE, BD_PHONE_RE, type CatalogBatch, type CatalogDepartment, type CatalogUniversity } from '../../api/types';
import { Button, Field, Input, PageHeader } from '../../design/primitives';
/** FR-AUTH-01/02: university/department/batch are catalog ids (Long on the
 *  backend), not free text; phone is a Bangladeshi mobile number. */
export const signUpSchema = z.object({ fullName: z.string().min(2,'Enter your full name'), phone: z.string().regex(BD_PHONE_RE, BD_PHONE_MESSAGE), email: z.string().email('Enter a valid email address'), universityId: z.number({invalid_type_error:'Select your university'}), departmentId: z.number({invalid_type_error:'Select your department'}), batchId: z.number().optional(), address: z.string().min(5,'Enter your address'), studentId: z.string().min(1,'Enter your student ID number'), password: z.string().min(8,'Password must be at least 8 characters').regex(/[A-Z]/,'Password needs an uppercase letter').regex(/[a-z]/,'Password needs a lowercase letter').regex(/[0-9]/,'Password needs a digit') });
export type SignUpForm = z.infer<typeof signUpSchema>;
const asId = (v: string) => v === '' ? undefined : Number(v);
export default function SignUp(){
  const [step,setStep]=useState(0); const nav=useNavigate();
  const { register, handleSubmit, formState:{errors}, trigger, watch, resetField } = useForm<SignUpForm>({ resolver: zodResolver(signUpSchema) });
  const universityId = watch('universityId'); const departmentId = watch('departmentId');
  const unis = useQuery({ queryKey:['catalog-universities'], queryFn: ()=>apiGet<CatalogUniversity[]>('/public/universities') });
  const depts = useQuery({ queryKey:['catalog-departments', universityId], queryFn: ()=>apiGet<CatalogDepartment[]>(`/public/departments?universityId=${universityId}`), enabled: universityId!==undefined });
  const batches = useQuery({ queryKey:['catalog-batches', universityId, departmentId], queryFn: ()=>apiGet<CatalogBatch[]>(`/public/batches?universityId=${universityId}${departmentId!==undefined?`&departmentId=${departmentId}`:''}`), enabled: universityId!==undefined });
  const [idFile,setIdFile]=useState<File|null>(null); const [photoFile,setPhotoFile]=useState<File|null>(null);
  const mut = useMutation({ mutationFn: async (data: SignUpForm)=>{ const fd=new FormData();
    fd.append('fullName',data.fullName); fd.append('phone',data.phone); fd.append('email',data.email);
    fd.append('universityId',String(data.universityId)); fd.append('departmentId',String(data.departmentId));
    if (data.batchId!==undefined) fd.append('batchId',String(data.batchId));
    fd.append('address',data.address); fd.append('studentId',data.studentId); fd.append('password',data.password);
    if(idFile) fd.append('idCard',idFile); if(photoFile) fd.append('profilePhoto',photoFile);
    return apiPost<{id?:number}>('/auth/register', fd); },
    onSuccess: (data)=>{ if (data.id!==undefined) sessionStorage.setItem('unilink:otpUserId', String(data.id)); nav('/otp'); } });
  const steps: (keyof SignUpForm)[][] = [['fullName','phone','email'],['universityId','departmentId','batchId','address'],['studentId','password']];
  const next = async ()=>{ if(await trigger(steps[step])) setStep(s=>s+1); };
  const onSubmit = (d: SignUpForm)=>{ if(!idFile){ alert('Please attach your student ID card image.'); return; } if(!photoFile){ alert('Please attach a profile photo.'); return; } mut.mutate(d); };
  return <div className="container" style={{maxWidth:640}}><PageHeader eyebrow={`Sign up · Step ${step+1} of 3`} title="Create your UniLink account" lead="FR-AUTH-01: full name, phone, email, university, department, batch, address, student ID, ID card image and profile photo. Password: 8+ characters with upper, lower and digit." />
  <form onSubmit={handleSubmit(onSubmit)} noValidate aria-label="Sign up form">
    {step===0 && <><Field label="Full name" error={errors.fullName?.message} htmlFor="fullName"><Input id="fullName" {...register('fullName')} autoComplete="name"/></Field>
    <Field label="Phone" error={errors.phone?.message} htmlFor="phone"><Input id="phone" type="tel" {...register('phone')} autoComplete="tel" placeholder="01712345678"/></Field>
    <Field label="Email" error={errors.email?.message} htmlFor="email"><Input id="email" type="email" {...register('email')} autoComplete="email"/></Field></>}
    {step===1 && <><Field label="University" error={errors.universityId?.message} htmlFor="universityId"><select id="universityId" {...register('universityId', { setValueAs: asId, onChange: ()=>{ resetField('departmentId'); resetField('batchId'); } })}>
      <option value="">Select your university</option>{(unis.data||[]).map(u=><option key={u.id} value={u.id}>{u.name}</option>)}</select>{unis.isError && <p className="field-error" role="alert">Could not load universities. Check your connection and try again.</p>}</Field>
    <Field label="Department" error={errors.departmentId?.message} htmlFor="departmentId"><select id="departmentId" {...register('departmentId', { setValueAs: asId, onChange: ()=>{ resetField('batchId'); } })} disabled={universityId===undefined}>
      <option value="">Select your department</option>{(depts.data||[]).map(d=><option key={d.id} value={d.id}>{d.name}</option>)}</select></Field>
    <Field label="Batch" error={errors.batchId?.message} htmlFor="batchId"><select id="batchId" {...register('batchId', { setValueAs: asId })} disabled={universityId===undefined}>
      <option value="">Select your batch (optional)</option>{(batches.data||[]).map(b=><option key={b.id} value={b.id}>{b.label}</option>)}</select></Field>
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
