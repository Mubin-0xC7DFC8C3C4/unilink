import { useEffect, useState } from 'react'; import { useForm } from 'react-hook-form'; import { z } from 'zod'; import { zodResolver } from '@hookform/resolvers/zod';
import { Link, useNavigate } from 'react-router-dom'; import { useMutation, useQuery } from '@tanstack/react-query';
import { apiGet, apiPost, errorMessage } from '../../api/client';
import { BD_PHONE_MESSAGE, BD_PHONE_RE, type CatalogDepartment, type CatalogUniversity } from '../../api/types';
import { Button, Field, Input, PageHeader } from '../../design/primitives';
import { SearchableSelect } from './SearchableSelect';
/** FR-AUTH-01/02 (owner brief 2026-11-25 §1/§2): the ten registration
 *  fields — full name, personal email, phone, university, department,
 *  batch, address, student ID, password, and TWO student ID
 *  card images (front + back, D-190). The address may be any valid
 *  address (D-196): university, present, permanent or home — it is
 *  never required to match the university's location. The profile photo is NOT collected
 *  here any more (D-178): students upload it from Settings → Edit
 *  Profile after logging in. Batch is free text (D-194): the user types
 *  it ("44", "2022-23", "Fall 2024") — the old batch dropdown is gone.
 *  Department is a verified dropdown row OR a typed label (D-193):
 *  exactly one is sent, and the choice is enforced in next()/onSubmit
 *  (not in the schema — the two fields are independent, so a schema
 *  refine would surface on a hidden step). Password rules are the
 *  backend's exact rules (D-182): ≥8 chars, upper, lower, digit — no
 *  special character (D-161, SRS FR-AUTH-03). confirmPassword is a
 *  frontend-only check, never sent to the API. */
export const BATCH_RE = /^[A-Za-z0-9][A-Za-z0-9 \-/().]{0,31}$/;
export const DEPARTMENT_LABEL_RE = /^[A-Za-z0-9][A-Za-z0-9 \-/().,&]{0,254}$/;
export const signUpSchema = z.object({
  fullName: z.string().min(1, 'Full name is required.').min(2, 'Enter your full name'),
  phone: z.string().min(1, 'Phone number is required.').regex(BD_PHONE_RE, BD_PHONE_MESSAGE),
  email: z.string().min(1, 'Please enter your personal email address.').email('Enter a valid email address'),
  universityId: z.number({ invalid_type_error: 'Please select your university.' }),
  departmentId: z.number().optional(),
  departmentLabel: z.string().max(255, 'Department must be 255 characters or fewer.').refine((v) => v === '' || DEPARTMENT_LABEL_RE.test(v), 'Department may only contain letters, digits, spaces and - / . ( ) , &').optional(),
  batch: z.string().min(1, 'Please enter your batch/session.').max(32, 'Batch/session must be 32 characters or fewer.').regex(BATCH_RE, 'Batch/session may only contain letters, digits, spaces, hyphens, slashes, dots or parentheses'),
  address: z.string().min(1, 'Your address is required.').min(5, 'Enter your address'),
  studentId: z.string().min(1, 'Student ID number is required.'),
  password: z.string().min(8, 'Password must be at least 8 characters').regex(/[A-Z]/, 'Password needs an uppercase letter').regex(/[a-z]/, 'Password needs a lowercase letter').regex(/[0-9]/, 'Password needs a digit'),
  confirmPassword: z.string().min(1, 'Re-enter your password'),
})
  .refine((d) => d.password === d.confirmPassword, { message: 'Passwords do not match', path: ['confirmPassword'] });
export type SignUpForm = z.infer<typeof signUpSchema>;

/** Upload limits from D-029a (the backend enforces the same caps). */
const ID_MAX_MB = 5;
const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
function fileError(f: File, maxMB: number): string | null {
  if (!IMAGE_TYPES.includes(f.type)) return 'Use a JPG, PNG or WebP image.';
  if (f.size > maxMB * 1024 * 1024) return `Image must be ${maxMB} MB or smaller.`;
  return null;
}

/** One upload field: preview, replace and remove, with type/size validation
 *  before a file is accepted. Shared with Pending (resubmission). */
export function IdUpload({ id, file, maxMB, actionLabel, onPick }: { id: string; file: File | null; maxMB: number; actionLabel: string; onPick: (f: File | null) => void }) {
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

// Catalogue resilience (2026-11-24): the Render free tier sleeps after
// ~15 idle minutes and a wake takes ~3m40s — far longer than the Pages
// proxy's ~35s retry budget and far longer than react-query's default
// few-second retries. A catalogue query must therefore keep retrying
// across the whole wake window instead of failing after a few seconds:
// the backoffs below plus each attempt's own (up to ~35s) proxy wait
// cover a full cold wake, and a failure still offers a manual retry
// instead of a dead end. Queries are keyed by their parent selection,
// so a slow response for a previous university/department can never
// overwrite the current dropdown.
const CATALOG_RETRY_DELAYS = [1000, 2000, 4000, 8000, 15000, 30000];
const catalogRetry = {
  retry: (failureCount: number) => failureCount < CATALOG_RETRY_DELAYS.length,
  retryDelay: (attemptIndex: number) => CATALOG_RETRY_DELAYS[Math.min(attemptIndex, CATALOG_RETRY_DELAYS.length - 1)],
};

export default function SignUp(){
  const [step, setStep] = useState(0); const nav = useNavigate();
  // Validation timing (brief §2B): validate on blur, then live after the
  // first submit attempt — never red errors on untouched fields at load.
  const { register, handleSubmit, formState: { errors, touchedFields }, trigger, watch, setValue } = useForm<SignUpForm>({ resolver: zodResolver(signUpSchema), mode: 'onBlur' });
  const universityId = watch('universityId'); const departmentId = watch('departmentId'); const departmentLabel = watch('departmentLabel');
  const unis = useQuery({ queryKey: ['catalog-universities'], queryFn: () => apiGet<CatalogUniversity[]>('/public/universities'), ...catalogRetry });
  const depts = useQuery({ queryKey: ['catalog-departments', universityId], queryFn: () => apiGet<CatalogDepartment[]>(`/public/departments?universityId=${universityId}`), enabled: universityId !== undefined, ...catalogRetry });
  // While the catalogue is still loading after 10 s the server is almost
  // certainly waking up — say so, instead of a silent spinner.
  const [wakeHint, setWakeHint] = useState(false);
  useEffect(() => { if (!unis.isPending) { setWakeHint(false); return; } const t = setTimeout(() => setWakeHint(true), 10000); return () => clearTimeout(t); }, [unis.isPending]);
  const [idFile, setIdFile] = useState<File | null>(null);
  const [idBackFile, setIdBackFile] = useState<File | null>(null);
  const [fileErrorMsg, setFileErrorMsg] = useState<string | undefined>(undefined);
  const [backFileErrorMsg, setBackFileErrorMsg] = useState<string | undefined>(undefined);
  const pickFile = (f: File | null) => {
    if (f === null) { setIdFile(null); setFileErrorMsg(undefined); return; }
    const err = fileError(f, ID_MAX_MB);
    if (err) { setFileErrorMsg(err); return; }
    setFileErrorMsg(undefined); setIdFile(f);
  };
  const pickBackFile = (f: File | null) => {
    if (f === null) { setIdBackFile(null); setBackFileErrorMsg(undefined); return; }
    const err = fileError(f, ID_MAX_MB);
    if (err) { setBackFileErrorMsg(err); return; }
    setBackFileErrorMsg(undefined); setIdBackFile(f);
  };
  // D-193: "My department isn't listed" swaps the dropdown for a typed
  // label. Exactly one of departmentId / departmentLabel is sent.
  const [deptOther, setDeptOther] = useState(false);
  const [deptError, setDeptError] = useState<string | undefined>(undefined);
  const deptChosen = departmentId !== undefined || (departmentLabel?.trim() ?? '') !== '';
  const [showPw, setShowPw] = useState(false); const [showConfirm, setShowConfirm] = useState(false);
  // Owner polish (2026-11-20): the confirm-password error (including
  // "Passwords do not match") is shown only once the user has actually
  // typed in the confirmation field, or after a real submit attempt.
  // Local state tracks both: RHF's own flags cannot be used here —
  // verified in the browser (2026-11-20) that trigger() during step
  // navigation flips formState.isSubmitted to true, which surfaced the
  // stored error on arrival at step 3.
  const [confirmTouched, setConfirmTouched] = useState(false);
  const [submitAttempted, setSubmitAttempted] = useState(false);
  const confirmReg = register('confirmPassword');
  const passwordReg = register('password');
  const pw = watch('password') || '';
  const confirmPw = watch('confirmPassword') || '';
  // The four backend password rules (D-182): live red ✗ / green ✓.
  const pwChecks = [{ label: 'At least 8 characters', ok: pw.length >= 8 }, { label: 'An uppercase letter', ok: /[A-Z]/.test(pw) }, { label: 'A lowercase letter', ok: /[a-z]/.test(pw) }, { label: 'A digit', ok: /[0-9]/.test(pw) }];
  const pwScore = pwChecks.filter((c) => c.ok).length;
  // A field counts as valid once it holds a value, has been blurred or
  // the form submitted, and carries no error. Selects are never
  // registered, so their "touched" is simply "a value was picked".
  const textValid = (name: keyof SignUpForm, value: string | undefined) =>
    !!value && (touchedFields[name] || submitAttempted) && !errors[name];
  const selectValid = (name: keyof SignUpForm, value: number | undefined) =>
    value !== undefined && !errors[name];
  const mut = useMutation({
    mutationFn: async (data: SignUpForm) => {
      const fd = new FormData();
      fd.append('fullName', data.fullName); fd.append('phone', data.phone); fd.append('email', data.email);
      fd.append('universityId', String(data.universityId));
      if (data.departmentId !== undefined) fd.append('departmentId', String(data.departmentId));
      if (data.departmentLabel?.trim()) fd.append('departmentLabel', data.departmentLabel.trim());
      fd.append('batch', data.batch);
      fd.append('address', data.address); fd.append('studentId', data.studentId); fd.append('password', data.password);
      if (idFile) fd.append('idCard', idFile);
      if (idBackFile) fd.append('idCardBack', idBackFile);
      return apiPost<{ id?: number; verificationToken?: string }>('/auth/register', fd);
    },
    // The registration token scopes the verification flow (Steps A–C):
    // the progress screen polls the real OCR status with it, and the
    // email OTP calls carry it instead of a user id.
    onSuccess: (data) => {
      if (data.verificationToken) sessionStorage.setItem('unilink:verificationToken', data.verificationToken);
      nav('/verification');
    },
  });
  const steps: (keyof SignUpForm)[][] = [['fullName', 'phone', 'email'], ['universityId', 'departmentId', 'departmentLabel', 'batch', 'address'], ['studentId', 'password', 'confirmPassword']];
  const next = async () => {
    // D-193: the department choice (dropdown or typed label) is checked
    // here, at the step boundary, as well as at submit — the schema
    // cannot express "exactly one of two fields" without surfacing on a
    // hidden step.
    if (step === 1 && !deptChosen) { setDeptError('Please select your department or type it.'); return; }
    setDeptError(undefined);
    if (await trigger(steps[step])) setStep((s) => s + 1);
  };
  const onSubmit = () => {
    if (!deptChosen) { setDeptError('Please select your department or type it.'); return; }
    if (!idFile) { setFileErrorMsg('Please upload the front of your student ID card.'); return; }
    if (!idBackFile) { setBackFileErrorMsg('Please upload the back of your student ID card.'); return; }
    // handleSubmit only calls this when the schema passes.
    mut.mutate(watch() as SignUpForm);
  };
  // Catalogue resets (2026-11-25): resetField() is a no-op for fields
  // that were never register()ed — university/department are controlled
  // through setValue/watch only — so children are cleared with
  // setValue(..., undefined). That was the real cause of the two failing
  // reset tests (department kept its old value). setValue's type rejects
  // undefined for the required number fields, so the helper casts it
  // (the runtime value is genuinely undefined). Batch is free text now
  // (D-194), so it is never reset by a catalogue change.
  const setCatalog = (name: 'universityId' | 'departmentId', id: number | undefined) => {
    if (id === undefined) setValue(name, undefined as never);
    else setValue(name, id, { shouldDirty: true });
  };
  const pickUniversity = (id: number | undefined) => { setCatalog('universityId', id); setCatalog('departmentId', undefined); setValue('departmentLabel', undefined as never); setDeptOther(false); setDeptError(undefined); };
  const pickDepartment = (id: number | undefined) => { setCatalog('departmentId', id); setDeptError(undefined); };
  const pickDeptOther = (other: boolean) => {
    setDeptOther(other); setDeptError(undefined);
    if (other) setCatalog('departmentId', undefined);
    else setValue('departmentLabel', undefined as never);
  };
  const STEP_LEADS = ['Your full name and student ID must match your student ID card. After submitting, we verify your ID automatically, then send a code to your personal email.', 'Select your university and department, then type your batch/session.', 'Your student ID, ID card photos and password.'];
  const fullName = watch('fullName'); const phone = watch('phone'); const email = watch('email'); const address = watch('address'); const studentId = watch('studentId'); const batch = watch('batch');
  return <div className="container" style={{ maxWidth: 640 }}><PageHeader eyebrow={`Sign up · Step ${step + 1} of 3`} title="Create your UniLink account" lead={STEP_LEADS[step]} />
  <form onSubmit={(e) => { setSubmitAttempted(true); void handleSubmit(onSubmit)(e); }} noValidate aria-label="Sign up form">
    {step === 0 && <>
      <Field label="Full name" error={errors.fullName?.message} valid={textValid('fullName', fullName)} validText="Looks good" htmlFor="fullName">
        <Input id="fullName" {...register('fullName')} autoComplete="name" aria-invalid={!!errors.fullName} aria-describedby={errors.fullName ? 'fullName-error' : undefined} />
        <p className="body-sm secondary" style={{ margin: '6px 0 0' }}>Use the exact name printed on your student ID card.</p>
      </Field>
      <Field label="Phone" error={errors.phone?.message} valid={textValid('phone', phone)} validText="Looks good" htmlFor="phone">
        <Input id="phone" type="tel" {...register('phone')} autoComplete="tel" placeholder="017XXXXXXXX" aria-invalid={!!errors.phone} aria-describedby={errors.phone ? 'phone-error' : undefined} />
      </Field>
      <Field label="Personal email" error={errors.email?.message} valid={textValid('email', email)} validText="Looks good" htmlFor="email">
        <Input id="email" type="email" {...register('email')} autoComplete="email" aria-invalid={!!errors.email} aria-describedby={errors.email ? 'email-error' : undefined} />
      </Field>
    </>}
    {step === 1 && <>
      <Field label="University" error={errors.universityId?.message} valid={selectValid('universityId', universityId)} validText="Looks good" htmlFor="universityId">
        <SearchableSelect id="universityId" options={(unis.data || []).map((u) => ({ id: u.id, name: u.name }))} value={universityId} placeholder="Search and select your university" onChange={pickUniversity} invalid={!!errors.universityId} ariaDescribedBy={errors.universityId ? 'universityId-error' : undefined} />
        {unis.isPending && wakeHint && <p className="body-sm secondary" role="status">The server is waking up — this can take a couple of minutes. The university list will appear automatically.</p>}
        {unis.isSuccess && (unis.data || []).length === 0 && <><p className="body-sm secondary" role="status">No universities are available right now.</p><button type="button" className="btn btn-sm" onClick={() => unis.refetch()}>Try again</button></>}
        {unis.isError && <><p className="field-error" role="alert">Could not load universities. The server may be waking up — it usually answers within a couple of minutes.</p><button type="button" className="btn btn-sm" onClick={() => unis.refetch()}>Try again</button></>}
      </Field>
      <Field label="Department" error={errors.departmentId?.message || errors.departmentLabel?.message || deptError} valid={deptChosen && !errors.departmentId && !errors.departmentLabel && !deptError} validText="Looks good" htmlFor={deptOther ? 'departmentLabel' : 'departmentId'}>
        {!deptOther ? <>
          <SearchableSelect id="departmentId" options={(depts.data || []).map((d) => ({ id: d.id, name: d.name }))} value={departmentId} placeholder={universityId === undefined ? 'Select your university first' : 'Search and select your department'} disabled={universityId === undefined} onChange={pickDepartment} invalid={!!errors.departmentId || !!deptError} ariaDescribedBy={errors.departmentId || deptError ? 'departmentId-error' : undefined} />
          {depts.isError && <><p className="field-error" role="alert">Could not load departments. The server may be waking up — try again.</p><button type="button" className="btn btn-sm" onClick={() => depts.refetch()}>Try again</button></>}
          {universityId !== undefined && (depts.data || []).length === 0 && !depts.isPending && !depts.isError && <p className="body-sm secondary" role="status">No verified departments are listed for this university yet — you can type your department instead.</p>}
          <p style={{ margin: '8px 0 0' }}><button type="button" className="btn btn-sm" onClick={() => pickDeptOther(true)} disabled={universityId === undefined}>My department isn’t listed — type it instead</button></p>
        </> : <>
          <Input id="departmentLabel" {...register('departmentLabel')} placeholder="e.g. Computer Science and Engineering" autoComplete="off" aria-invalid={!!errors.departmentLabel} aria-describedby={errors.departmentLabel ? 'departmentLabel-error' : undefined} />
          <p className="body-sm secondary" style={{ margin: '6px 0 0' }}>Type your department exactly as it appears on your ID card. It will be checked by an administrator.</p>
          <p style={{ margin: '8px 0 0' }}><button type="button" className="btn btn-sm" onClick={() => pickDeptOther(false)}>Choose from the list instead</button></p>
        </>}
      </Field>
      <Field label="Batch / Session" error={errors.batch?.message} valid={textValid('batch', batch)} validText="Looks good" htmlFor="batch">
        <Input id="batch" {...register('batch')} placeholder="e.g. 44, 2022-23, Fall 2024" autoComplete="off" aria-invalid={!!errors.batch} aria-describedby={errors.batch ? 'batch-error' : undefined} />
        <p className="body-sm secondary" style={{ margin: '6px 0 0' }}>Type your batch or session exactly as it appears on your ID card, if it is printed there.</p>
      </Field>
      <Field label="Your address" error={errors.address?.message} valid={textValid('address', address)} validText="Looks good" htmlFor="address">
        <Input id="address" {...register('address')} autoComplete="street-address" placeholder="Enter your address" aria-invalid={!!errors.address} aria-describedby={errors.address ? 'address-error' : undefined} />
      </Field>
    </>}
    {step === 2 && <>
      <Field label="Student ID number" error={errors.studentId?.message} valid={textValid('studentId', studentId)} validText="Looks good" htmlFor="studentId">
        <Input id="studentId" {...register('studentId')} aria-invalid={!!errors.studentId} aria-describedby={errors.studentId ? 'studentId-error' : undefined} />
        <p className="body-sm secondary" style={{ margin: '6px 0 0' }}>Enter the number exactly as printed on your ID card. Formats differ by university — for example 241-115-129.</p>
      </Field>
      <Field label="Student ID card — front" error={fileErrorMsg} valid={idFile !== null && !fileErrorMsg} validText="Looks good" htmlFor="idCard">
        <IdUpload id="idCard" file={idFile} maxMB={ID_MAX_MB} actionLabel="Upload front of ID card" onPick={pickFile} />
        <p className="body-sm secondary" style={{ margin: '6px 0 0' }}>The side with your photo, name and ID number.</p>
      </Field>
      <Field label="Student ID card — back" error={backFileErrorMsg} valid={idBackFile !== null && !backFileErrorMsg} validText="Looks good" htmlFor="idCardBack">
        <IdUpload id="idCardBack" file={idBackFile} maxMB={ID_MAX_MB} actionLabel="Upload back of ID card" onPick={pickBackFile} />
        <p className="body-sm secondary" style={{ margin: '6px 0 0' }}>The side with the university address and validity. Both sides are required — the front and back must be different photos.</p>
      </Field>
      <Field label="Password" error={errors.password?.message} valid={pwScore === 4 && (touchedFields.password || submitAttempted)} htmlFor="password">
        <div style={{ display: 'flex', gap: 8, alignItems: 'flex-start' }}><div style={{ flex: 1 }}><Input id="password" type={showPw ? 'text' : 'password'} {...passwordReg} onChange={(e) => { void passwordReg.onChange(e); if (confirmTouched) void trigger('confirmPassword'); }} autoComplete="new-password" aria-invalid={!!errors.password} aria-describedby={errors.password ? 'password-error' : undefined} /></div><button type="button" className="btn btn-sm" onClick={() => setShowPw((v) => !v)} aria-pressed={showPw} aria-label={showPw ? 'Hide password' : 'Show password'}>{showPw ? 'Hide' : 'Show'}</button></div>
        <ul className="pw-checks" aria-label="Password requirements">
          {pwChecks.map((c) => <li key={c.label} className={c.ok ? 'met' : 'unmet'}>{c.label}</li>)}
        </ul>
      </Field>
      <Field label="Confirm password" error={(confirmTouched || submitAttempted) ? errors.confirmPassword?.message : undefined} valid={confirmTouched && confirmPw !== '' && confirmPw === pw} validText="Passwords match." htmlFor="confirmPassword">
        <div style={{ display: 'flex', gap: 8, alignItems: 'flex-start' }}><div style={{ flex: 1 }}><Input id="confirmPassword" type={showConfirm ? 'text' : 'password'} {...confirmReg} onChange={(e) => { void confirmReg.onChange(e); setConfirmTouched(true); void trigger('confirmPassword'); }} autoComplete="new-password" aria-invalid={!!((confirmTouched || submitAttempted) && errors.confirmPassword)} aria-describedby={errors.confirmPassword ? 'confirmPassword-error' : undefined} /></div><button type="button" className="btn btn-sm" onClick={() => setShowConfirm((v) => !v)} aria-pressed={showConfirm} aria-label={showConfirm ? 'Hide confirm password' : 'Show confirm password'}>{showConfirm ? 'Hide' : 'Show'}</button></div>
      </Field>
    </>}
    {mut.isError && <p role="alert" className="field-error">{errorMessage(mut.error)}</p>}
    <div style={{ display: 'flex', gap: 8 }}>{step > 0 && <Button type="button" onClick={() => setStep((s) => s - 1)}>Back</Button>}
    {step < 2 ? <Button type="button" variant="primary" onClick={next}>Continue →</Button> : <Button type="submit" variant="primary" disabled={mut.isPending}>{mut.isPending ? 'Creating account…' : 'Create account'}</Button>}</div>
  </form>
  <p className="secondary" style={{ marginTop: 16 }}>Already registered? <Link to="/login">Log in</Link></p></div>;
}
