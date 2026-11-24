import { useEffect, useState } from 'react'; import { useForm } from 'react-hook-form'; import { z } from 'zod'; import { zodResolver } from '@hookform/resolvers/zod';
import { Link, useNavigate } from 'react-router-dom'; import { useMutation, useQuery } from '@tanstack/react-query';
import { apiGet, apiPost, errorMessage } from '../../api/client';
import { BD_PHONE_MESSAGE, BD_PHONE_RE, type CatalogBatch, type CatalogDepartment, type CatalogUniversity } from '../../api/types';
import { Button, Field, Input, PageHeader } from '../../design/primitives';
import { SearchableSelect } from './SearchableSelect';
/** FR-AUTH-01/02: university/department/batch are catalog ids (Long on the
 *  backend), not free text; phone is a Bangladeshi mobile number.
 *  Phase 2.2 (owner instruction 2026-11-20): confirmPassword is a
 *  frontend-only check — it is validated here and never sent to the API,
 *  whose RegisterForm has no such field. Student ID keeps NO fixed format:
 *  formats differ by university, so only presence is required. */
export const signUpSchema = z.object({ fullName: z.string().min(2,'Enter your full name'), phone: z.string().regex(BD_PHONE_RE, BD_PHONE_MESSAGE), email: z.string().email('Enter a valid email address'), universityId: z.number({invalid_type_error:'Select your university'}), departmentId: z.number({invalid_type_error:'Select your department'}), batchId: z.number().optional(), address: z.string().min(5,'Enter your address'), studentId: z.string().min(1,'Enter your student ID number'), password: z.string().min(8,'Password must be at least 8 characters').regex(/[A-Z]/,'Password needs an uppercase letter').regex(/[a-z]/,'Password needs a lowercase letter').regex(/[0-9]/,'Password needs a digit'), confirmPassword: z.string().min(1,'Re-enter your password') })
  .refine((d) => d.password === d.confirmPassword, { message: 'Passwords do not match', path: ['confirmPassword'] });
export type SignUpForm = z.infer<typeof signUpSchema>;

/** Upload limits from D-029a (the backend enforces the same caps). */
const ID_MAX_MB = 5; const PHOTO_MAX_MB = 2;
const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
function fileError(f: File, maxMB: number): string | null {
  if (!IMAGE_TYPES.includes(f.type)) return 'Use a JPG, PNG or WebP image.';
  if (f.size > maxMB * 1024 * 1024) return `Image must be ${maxMB} MB or smaller.`;
  return null;
}

/** One upload field: preview, replace and remove, with type/size validation
 *  before a file is accepted. Used for the ID front, ID back and the
 *  profile photo so the three behave identically. */
function IdUpload({ id, file, maxMB, actionLabel, onPick }: { id: string; file: File | null; maxMB: number; actionLabel: string; onPick: (f: File | null) => void }) {
  const [url, setUrl] = useState('');
  useEffect(() => { if (!file) { setUrl(''); return; } let u = ''; try { u = URL.createObjectURL(file); } catch { u = ''; } setUrl(u); return () => { if (u) URL.revokeObjectURL(u); }; }, [file]);
  return <div style={{ border: '1px dashed var(--border)', borderRadius: 'var(--radius-sm)', padding: 12 }}>
    <input id={id} type="file" accept="image/jpeg,image/png,image/webp" hidden onChange={(e) => { onPick(e.target.files?.[0] ?? null); e.target.value = ''; }} />
    {file ? <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
      {url !== '' && <img src={url} alt="" style={{ width: 72, height: 48, objectFit: 'cover', borderRadius: 4, border: '1px solid var(--border)' }} />}
      <div style={{ flex: 1, minWidth: 140 }}><div style={{ fontWeight: 600 }}>{file.name}</div><div className="body-sm secondary">{Math.max(1, Math.round(file.size / 1024))} KB · ready</div></div>
      <label htmlFor={id} className="btn btn-sm" style={{ cursor: 'pointer' }}>Replace</label>
      <button type="button" className="btn btn-sm" onClick={() => onPick(null)}>Remove</button>
    </div> : <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
      <label htmlFor={id} className="btn btn-sm" style={{ cursor: 'pointer' }}>{actionLabel}</label>
      <span className="body-sm secondary">JPG, PNG or WebP, up to {maxMB} MB.</span>
    </div>}
  </div>;
}

export default function SignUp(){
  const [step,setStep]=useState(0); const nav=useNavigate();
  const { register, handleSubmit, formState:{errors}, trigger, watch, resetField, setValue } = useForm<SignUpForm>({ resolver: zodResolver(signUpSchema) });
  const universityId = watch('universityId'); const departmentId = watch('departmentId');
  const unis = useQuery({ queryKey:['catalog-universities'], queryFn: ()=>apiGet<CatalogUniversity[]>('/public/universities') });
  const depts = useQuery({ queryKey:['catalog-departments', universityId], queryFn: ()=>apiGet<CatalogDepartment[]>(`/public/departments?universityId=${universityId}`), enabled: universityId!==undefined });
  const batches = useQuery({ queryKey:['catalog-batches', universityId, departmentId], queryFn: ()=>apiGet<CatalogBatch[]>(`/public/batches?universityId=${universityId}${departmentId!==undefined?`&departmentId=${departmentId}`:''}`), enabled: universityId!==undefined });
  const [frontFile,setFrontFile]=useState<File|null>(null); const [backFile,setBackFile]=useState<File|null>(null); const [photoFile,setPhotoFile]=useState<File|null>(null);
  const [fileErrors,setFileErrors]=useState<{front?:string;back?:string;photo?:string}>({});
  const pickFile = (slot: 'front'|'back'|'photo', maxMB: number, setFile: (f: File|null)=>void) => (f: File|null) => {
    if (f === null) { setFile(null); setFileErrors((e)=>({...e,[slot]:undefined})); return; }
    const err = fileError(f, maxMB);
    if (err) { setFileErrors((e)=>({...e,[slot]:err})); return; }
    setFileErrors((e)=>({...e,[slot]:undefined})); setFile(f);
  };
  const [showPw,setShowPw]=useState(false); const [showConfirm,setShowConfirm]=useState(false);
  // Owner polish (2026-11-20): the confirm-password error (including
  // "Passwords do not match") is shown only once the user has actually
  // typed in the confirmation field, or after a real submit attempt.
  // Local state tracks both: RHF's own flags cannot be used here —
  // verified in the browser (2026-11-20) that trigger() during step
  // navigation flips formState.isSubmitted to true, which surfaced the
  // stored error on arrival at step 3.
  const [confirmTouched,setConfirmTouched]=useState(false);
  const [submitAttempted,setSubmitAttempted]=useState(false);
  const confirmReg = register('confirmPassword');
  const passwordReg = register('password');
  const pw = watch('password') || '';
  const pwChecks = [{label:'At least 8 characters', ok: pw.length>=8},{label:'An uppercase letter', ok: /[A-Z]/.test(pw)},{label:'A lowercase letter', ok: /[a-z]/.test(pw)},{label:'A digit', ok: /[0-9]/.test(pw)}];
  const pwScore = pwChecks.filter((c)=>c.ok).length;
  const pwLabel = pwScore<=1 ? 'Weak' : pwScore===2 ? 'Fair' : pwScore===3 ? 'Good' : 'Strong';
  const mut = useMutation({ mutationFn: async (data: SignUpForm)=>{ const fd=new FormData();
    fd.append('fullName',data.fullName); fd.append('phone',data.phone); fd.append('email',data.email);
    fd.append('universityId',String(data.universityId)); fd.append('departmentId',String(data.departmentId));
    if (data.batchId!==undefined) fd.append('batchId',String(data.batchId));
    fd.append('address',data.address); fd.append('studentId',data.studentId); fd.append('password',data.password);
    // BACKEND LIMITATION (owner decision A1, 2026-11-20): /auth/register
    // accepts a single idCard file. The FRONT side is submitted as idCard
    // today; the back side is collected and validated in the UI only and
    // joins this payload unchanged when the backend accepts a second file.
    // The form states this next to the upload fields — the back side is
    // never claimed to be stored or verified.
    if(frontFile) fd.append('idCard',frontFile); if(photoFile) fd.append('profilePhoto',photoFile);
    return apiPost<{id?:number; emailSent?:boolean}>('/auth/register', fd); },
    // emailSent tells the OTP step whether the verification email actually
    // went out (false when SMTP delivery failed) so it can offer a resend
    // instead of leaving the user to discover it later.
    onSuccess: (data)=>{ if (data.id!==undefined) sessionStorage.setItem('unilink:otpUserId', String(data.id)); sessionStorage.setItem('unilink:emailSent', String(data.emailSent ?? true)); nav('/otp'); } });
  const steps: (keyof SignUpForm)[][] = [['fullName','phone','email'],['universityId','departmentId','batchId','address'],['studentId','password','confirmPassword']];
  const next = async ()=>{ if(await trigger(steps[step])) setStep(s=>s+1); };
  const onSubmit = (d: SignUpForm)=>{ const fe: {front?:string;back?:string;photo?:string} = {};
    if(!frontFile) fe.front='Please attach the front side of your ID card.';
    if(!backFile) fe.back='Please attach the back side of your ID card.';
    if(!photoFile) fe.photo='Please attach a profile photo.';
    setFileErrors(fe);
    if(fe.front||fe.back||fe.photo) return;
    mut.mutate(d); };
  const STEP_LEADS = ['Your full name and student ID must match your student ID card. After submitting, you will verify your phone number, then an admin reviews your ID.', 'Select your university, department and batch.', 'Your student ID, ID card photos and password.'];
  return <div className="container" style={{maxWidth:640}}><PageHeader eyebrow={`Sign up · Step ${step+1} of 3`} title="Create your UniLink account" lead={STEP_LEADS[step]} />
  <form onSubmit={(e)=>{ setSubmitAttempted(true); void handleSubmit(onSubmit)(e); }} noValidate aria-label="Sign up form">
    {step===0 && <><Field label="Full name" error={errors.fullName?.message} htmlFor="fullName"><Input id="fullName" {...register('fullName')} autoComplete="name"/><p className="body-sm secondary" style={{margin:'6px 0 0'}}>Use the exact name printed on your student ID card.</p></Field>
    <Field label="Phone" error={errors.phone?.message} htmlFor="phone"><Input id="phone" type="tel" {...register('phone')} autoComplete="tel" placeholder="017XXXXXXXX"/></Field>
    <Field label="Personal email" error={errors.email?.message} htmlFor="email"><Input id="email" type="email" {...register('email')} autoComplete="email"/></Field></>}
    {step===1 && <><Field label="University" error={errors.universityId?.message} htmlFor="universityId"><SearchableSelect id="universityId" options={(unis.data||[]).map(u=>({id:u.id,name:u.name}))} value={universityId} placeholder="Search and select your university" onChange={(id)=>{ setValue('universityId', id as number, { shouldDirty: true }); resetField('departmentId'); resetField('batchId'); }} />{unis.isError && <p className="field-error" role="alert">Could not load universities. Check your connection and try again.</p>}</Field>
    <Field label="Department" error={errors.departmentId?.message} htmlFor="departmentId"><SearchableSelect id="departmentId" options={(depts.data||[]).map(d=>({id:d.id,name:d.name}))} value={departmentId} placeholder={universityId===undefined ? 'Select your university first' : 'Search and select your department'} disabled={universityId===undefined} onChange={(id)=>{ setValue('departmentId', id as number, { shouldDirty: true }); resetField('batchId'); }} /></Field>
    <Field label="Batch" error={errors.batchId?.message} htmlFor="batchId"><SearchableSelect id="batchId" options={(batches.data||[]).map(b=>({id:b.id,name:b.label}))} value={watch('batchId')} placeholder="Select your batch (optional)" disabled={universityId===undefined} onChange={(id)=>{ setValue('batchId', id, { shouldDirty: true }); }} /></Field>
    <Field label="Address" error={errors.address?.message} htmlFor="address"><Input id="address" {...register('address')} autoComplete="street-address"/></Field></>}
    {step===2 && <><Field label="Student ID number" error={errors.studentId?.message} htmlFor="studentId"><Input id="studentId" {...register('studentId')}/><p className="body-sm secondary" style={{margin:'6px 0 0'}}>Enter the number exactly as printed on your ID card. Formats differ by university — for example 241-115-129.</p></Field>
    <Field label="Student ID card — Front side" error={fileErrors.front} htmlFor="idCardFront"><IdUpload id="idCardFront" file={frontFile} maxMB={ID_MAX_MB} actionLabel="Upload front side" onPick={pickFile('front', ID_MAX_MB, setFrontFile)} /></Field>
    <Field label="Student ID card — Back side" error={fileErrors.back} htmlFor="idCardBack"><IdUpload id="idCardBack" file={backFile} maxMB={ID_MAX_MB} actionLabel="Upload back side" onPick={pickFile('back', ID_MAX_MB, setBackFile)} /></Field>
    <Field label="Profile photo" error={fileErrors.photo} htmlFor="photo"><IdUpload id="photo" file={photoFile} maxMB={PHOTO_MAX_MB} actionLabel="Upload profile photo" onPick={pickFile('photo', PHOTO_MAX_MB, setPhotoFile)} /></Field>
    <Field label="Password" error={errors.password?.message} htmlFor="password"><div style={{display:'flex',gap:8,alignItems:'flex-start'}}><div style={{flex:1}}><Input id="password" type={showPw?'text':'password'} {...passwordReg} onChange={(e)=>{ void passwordReg.onChange(e); if (confirmTouched) void trigger('confirmPassword'); }} autoComplete="new-password"/></div><button type="button" className="btn btn-sm" onClick={()=>setShowPw(v=>!v)} aria-pressed={showPw} aria-label={showPw?'Hide password':'Show password'}>{showPw?'Hide':'Show'}</button></div>
    {pw.length>0 && <div style={{marginTop:8}}><div style={{display:'flex',gap:4}}>{pwChecks.map((c,i)=><span key={i} style={{flex:1,height:4,borderRadius:2,background: c.ok ? 'var(--text-primary)' : 'var(--border)'}} />)}</div><p className="body-sm secondary" style={{margin:'6px 0 0'}}>Password strength: {pwLabel}. {pwChecks.filter(c=>!c.ok).map(c=>c.label).join(' · ')}{pwScore===4 ? 'All requirements met.' : ''}</p></div>}</Field>
    <Field label="Confirm password" error={(confirmTouched || submitAttempted) ? errors.confirmPassword?.message : undefined} htmlFor="confirmPassword"><div style={{display:'flex',gap:8,alignItems:'flex-start'}}><div style={{flex:1}}><Input id="confirmPassword" type={showConfirm?'text':'password'} {...confirmReg} onChange={(e)=>{ void confirmReg.onChange(e); setConfirmTouched(true); void trigger('confirmPassword'); }} autoComplete="new-password"/></div><button type="button" className="btn btn-sm" onClick={()=>setShowConfirm(v=>!v)} aria-pressed={showConfirm} aria-label={showConfirm?'Hide confirm password':'Show confirm password'}>{showConfirm?'Hide':'Show'}</button></div></Field></>}
    {mut.isError && <p role="alert" className="field-error">{errorMessage(mut.error)}</p>}
    <div style={{display:'flex',gap:8}}>{step>0 && <Button type="button" onClick={()=>setStep(s=>s-1)}>Back</Button>}
    {step<2 ? <Button type="button" variant="primary" onClick={next}>Continue →</Button> : <Button type="submit" variant="primary" disabled={mut.isPending}>{mut.isPending?'Creating account…':'Create account'}</Button>}</div>
  </form>
  <p className="secondary" style={{marginTop:16}}>Already registered? <Link to="/login">Log in</Link></p></div>;
}
