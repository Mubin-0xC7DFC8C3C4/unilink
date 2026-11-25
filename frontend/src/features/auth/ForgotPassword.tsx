import { useEffect, useRef, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { apiPost, errorMessage } from '../../api/client';
import { Button, Field, Input, PageHeader } from '../../design/primitives';

/** Phase 2.4 — Forgot Password (FR-AUTH-11), a 3-step flow built on the
 *  two REAL backend endpoints:
 *    POST /auth/password-reset/request {email}          — always succeeds
 *      silently (never reveals whether the email exists); the code lives
 *      15 minutes, 5 attempts, resends are rate-limited server-side.
 *    POST /auth/password-reset/confirm {email, otp, newPassword}
 *      — the ONLY place the code is checked (there is no verify-only
 *      endpoint). Error codes: OTP_INVALID, OTP_EXPIRED,
 *      OTP_ATTEMPTS_EXCEEDED, OTP_NOT_FOUND, INVALID_CREDENTIALS,
 *      WEAK_PASSWORD (rule: 8+ chars with upper, lower and digit).
 *  Step 2's "Verify code" therefore advances locally; a wrong or expired
 *  code is reported by the confirm call at step 3 and returns the user
 *  to step 2 with the proper error state. The success screen is shown
 *  only after a real confirm succeeds — nothing here fakes a reset. */

type Stage = 'email' | 'code' | 'password' | 'success';
type CodeError = { kind: 'invalid' } | { kind: 'message'; text: string } | null;

const INVALID_CODE_TEXT = 'Invalid verification code. Please double-check and try again.';
const PASSWORD_RULE_TEXT = 'Password must be at least 8 characters with an upper-case letter, a lower-case letter and a digit.';
const RESEND_SECONDS = 30;

function maskEmail(email: string): string {
  const [local, domain] = email.split('@');
  if (!domain) return email;
  return `${local.slice(0, 1)}•••@${domain}`;
}
const mmss = (s: number) => `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;

/** Same functional colours and animations as the approved Verification
 *  screens (green = success, red = error), scoped to this page. */
const SCOPED_CSS = `
.fp-scope { --verify-success: #1a7f37; --verify-error: #c81e1e; }
@keyframes fp-shake { 10%, 90% { transform: translateX(-1px); } 20%, 80% { transform: translateX(2px); } 30%, 50%, 70% { transform: translateX(-3px); } 40%, 60% { transform: translateX(3px); } }
.fp-shake { animation: fp-shake 0.45s ease both; }
@keyframes fp-draw { to { stroke-dashoffset: 0; } }
.fp-check-circle { stroke-dasharray: 302; stroke-dashoffset: 302; animation: fp-draw 0.7s ease forwards; }
.fp-check-mark { stroke-dasharray: 60; stroke-dashoffset: 60; animation: fp-draw 0.45s 0.55s ease forwards; }
`;

/** The six-box code input from the approved Verification Code screen:
 *  auto-advance, backspace/arrow navigation, paste fills all boxes. */
function OtpBoxes({ digits, error, shaking, onShakeEnd, onDigit, inputRefs }: {
  digits: string[];
  error: boolean;
  shaking: boolean;
  onShakeEnd: () => void;
  onDigit: (index: number, digits: string[], focusIndex: number | null) => void;
  inputRefs: React.MutableRefObject<(HTMLInputElement | null)[]>;
}) {
  const setFrom = (start: number, chars: string[]) => {
    const next = [...digits];
    chars.forEach((c, k) => { if (start + k < 6) next[start + k] = c; });
    const filled = Math.min(start + chars.length, 6);
    onDigit(start, next, filled < 6 ? filled : 5);
  };
  return <div className={shaking ? 'fp-shake' : undefined} onAnimationEnd={onShakeEnd}
    style={{ display: 'flex', gap: 8, justifyContent: 'center', margin: '24px 0 8px' }}>
    {digits.map((d, i) => <input key={i} ref={(el) => { inputRefs.current[i] = el; }}
      aria-label={`Digit ${i + 1} of 6`} inputMode="numeric" autoComplete={i === 0 ? 'one-time-code' : 'off'}
      maxLength={1} value={d}
      style={{ width: 48, height: 56, textAlign: 'center', fontSize: 22, fontWeight: 600, background: 'var(--surface)', border: `1px solid ${error ? 'var(--verify-error)' : 'var(--border)'}`, borderRadius: 'var(--radius-sm)' }}
      onFocus={(e) => e.target.select()}
      onChange={(e) => {
        const v = e.target.value.replace(/\D/g, '');
        if (v === '') { const next = [...digits]; next[i] = ''; onDigit(i, next, null); return; }
        setFrom(i, [v[v.length - 1]]);
      }}
      onKeyDown={(e) => {
        if (e.key === 'Backspace') {
          e.preventDefault();
          if (digits[i] !== '') { const next = [...digits]; next[i] = ''; onDigit(i, next, null); }
          else if (i > 0) { const next = [...digits]; next[i - 1] = ''; onDigit(i, next, i - 1); }
        } else if (e.key === 'ArrowLeft' && i > 0) { e.preventDefault(); inputRefs.current[i - 1]?.focus(); }
        else if (e.key === 'ArrowRight' && i < 5) { e.preventDefault(); inputRefs.current[i + 1]?.focus(); }
      }}
      onPaste={(e) => {
        const text = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6);
        if (text === '') return;
        e.preventDefault();
        const next = Array(6).fill('');
        text.split('').forEach((c, k) => { next[k] = c; });
        onDigit(0, next, Math.min(text.length, 5));
      }} />)}
  </div>;
}

/** The Sign Up strength scoring, reused so both pages judge a password
 *  the same way; the requirements are the backend rule (FR-AUTH-03). */
const PW_REQUIREMENTS = [
  { label: 'At least 8 characters', met: (pw: string) => pw.length >= 8 },
  { label: 'An upper-case letter', met: (pw: string) => /[A-Z]/.test(pw) },
  { label: 'A lower-case letter', met: (pw: string) => /[a-z]/.test(pw) },
  { label: 'A number', met: (pw: string) => /\d/.test(pw) },
];
function scorePassword(pw: string): number { return PW_REQUIREMENTS.reduce((n, r) => n + (r.met(pw) ? 1 : 0), 0); }
const STRENGTH_LABELS = ['Weak', 'Weak', 'Fair', 'Good', 'Strong'];
const passwordRuleOk = (pw: string) => scorePassword(pw) === PW_REQUIREMENTS.length;

/** A borderless Show/Hide control inside the input, as approved on the
 *  Login page in the final polish. */
function PasswordField({ id, label, value, onChange, error, autoComplete }: {
  id: string; label: string; value: string; onChange: (v: string) => void; error?: string; autoComplete: string;
}) {
  const [show, setShow] = useState(false);
  return <Field label={label} error={error} htmlFor={id}>
    <span style={{ position: 'relative', display: 'block' }}>
      <Input id={id} type={show ? 'text' : 'password'} value={value} onChange={(e) => onChange(e.target.value)} autoComplete={autoComplete} style={{ width: '100%', paddingRight: 64 }} />
      <button type="button" aria-pressed={show} aria-label={show ? `Hide ${label.toLowerCase()}` : `Show ${label.toLowerCase()}`} onClick={() => setShow((s) => !s)}
        style={{ position: 'absolute', right: 6, top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', padding: '4px 6px', fontWeight: 600, cursor: 'pointer', color: 'var(--text-secondary)' }}>{show ? 'Hide' : 'Show'}</button>
    </span>
  </Field>;
}

export default function ForgotPassword() {
  const [stage, setStage] = useState<Stage>('email');
  const [email, setEmail] = useState('');
  const [emailError, setEmailError] = useState('');
  const [digits, setDigits] = useState<string[]>(Array(6).fill(''));
  const [codeError, setCodeError] = useState<CodeError>(null);
  const [shaking, setShaking] = useState(false);
  const [resendIn, setResendIn] = useState(RESEND_SECONDS);
  const [pw, setPw] = useState('');
  const [confirmPw, setConfirmPw] = useState('');
  const [confirmTouched, setConfirmTouched] = useState(false);
  const [step3Submitted, setStep3Submitted] = useState(false);
  const [pwError, setPwError] = useState('');
  const [formError, setFormError] = useState('');
  const inputRefs = useRef<(HTMLInputElement | null)[]>([]);
  const code = digits.join('');
  const codeComplete = code.length === 6;

  const requestCode = () => apiPost('/auth/password-reset/request', { email });
  const initialRequest = useMutation({
    mutationFn: requestCode,
    onSuccess: () => { setStage('code'); setResendIn(RESEND_SECONDS); setCodeError(null); setDigits(Array(6).fill('')); },
  });
  const resendRequest = useMutation({
    mutationFn: requestCode,
    onSuccess: () => { setResendIn(RESEND_SECONDS); setCodeError(null); setDigits(Array(6).fill('')); inputRefs.current[0]?.focus(); },
  });
  const confirm = useMutation({
    mutationFn: () => apiPost('/auth/password-reset/confirm', { email, otp: code, newPassword: pw }),
    onSuccess: () => setStage('success'),
    onError: (err) => {
      const errCode = (err as { code?: string })?.code;
      const backToCode = (e: CodeError) => { setCodeError(e); setDigits(Array(6).fill('')); setShaking(e?.kind === 'invalid'); setStage('code'); };
      if (errCode === 'OTP_INVALID') backToCode({ kind: 'invalid' });
      else if (errCode === 'OTP_EXPIRED') backToCode({ kind: 'message', text: 'Reset code has expired.' });
      else if (errCode === 'OTP_ATTEMPTS_EXCEEDED' || errCode === 'OTP_NOT_FOUND' || errCode === 'INVALID_CREDENTIALS') backToCode({ kind: 'message', text: errorMessage(err) });
      else if (errCode === 'WEAK_PASSWORD') setPwError(errorMessage(err));
      else setFormError(errorMessage(err));
    },
  });

  useEffect(() => {
    if (stage !== 'code' || resendIn <= 0) return;
    const t = setTimeout(() => setResendIn((s) => s - 1), 1000);
    return () => clearTimeout(t);
  }, [stage, resendIn]);

  useEffect(() => { if (stage === 'code') inputRefs.current[0]?.focus(); }, [stage]);

  const applyDigits = (_index: number, next: string[], focusIndex: number | null) => {
    setDigits(next); setCodeError(null);
    if (focusIndex !== null) inputRefs.current[focusIndex]?.focus();
  };

  const submitEmail = (e: React.FormEvent) => {
    e.preventDefault();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) { setEmailError('Enter a valid email address'); return; }
    setEmailError('');
    initialRequest.mutate();
  };

  const submitPassword = (e: React.FormEvent) => {
    e.preventDefault();
    setStep3Submitted(true);
    setFormError('');
    if (!passwordRuleOk(pw)) { setPwError(PASSWORD_RULE_TEXT); return; }
    setPwError('');
    if (confirmPw === '' || confirmPw !== pw) return; // the field error below explains
    confirm.mutate();
  };

  const pwScore = scorePassword(pw);
  const missing = PW_REQUIREMENTS.filter((r) => !r.met(pw));
  let confirmError: string | undefined;
  if (confirmTouched || step3Submitted) {
    if (confirmPw === '') confirmError = step3Submitted ? 'Re-enter your new password' : undefined;
    else if (confirmPw !== pw) confirmError = 'Passwords do not match';
  }

  return <div className="container fp-scope" style={{ maxWidth: 560 }}>
    <style>{SCOPED_CSS}</style>

    {stage === 'email' && <>
      <PageHeader eyebrow="Account recovery" title="Reset your password" lead="Enter the personal email associated with your UniLink account, and we’ll send you a verification code." />
      <form onSubmit={submitEmail} noValidate>
        <Field label="Personal email" error={emailError || undefined} htmlFor="email">
          <Input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" placeholder="e.g. mubin@gmail.com" />
        </Field>
        {initialRequest.isError && <p role="alert" className="field-error">{errorMessage(initialRequest.error)}</p>}
        <Button type="submit" variant="primary" disabled={initialRequest.isPending}>{initialRequest.isPending ? 'Sending…' : 'Send verification code'}</Button>
      </form>
      <p className="secondary" style={{ marginTop: 16 }}>Remember your password? <Link to="/login">Log in</Link></p>
    </>}

    {stage === 'code' && <div style={{ textAlign: 'center', paddingTop: 32 }}>
      <h1 className="headline">Enter verification code</h1>
      <p className="lead">If an account exists for {maskEmail(email)}, we&apos;ve sent a 6-digit verification code to it.</p>
      <OtpBoxes digits={digits} error={codeError !== null} shaking={shaking} onShakeEnd={() => setShaking(false)} onDigit={applyDigits} inputRefs={inputRefs} />
      {codeError && <p role="alert" style={{ color: 'var(--verify-error)', margin: '8px 0 0' }}>{codeError.kind === 'invalid' ? INVALID_CODE_TEXT : codeError.text}</p>}
      <div style={{ marginTop: 24 }}>
        <Button type="button" variant="primary" disabled={!codeComplete || resendRequest.isPending} onClick={() => { setCodeError(null); setStage('password'); }}>Verify code</Button>
      </div>
      <p style={{ marginTop: 20 }}>Didn't receive the code?{' '}
        <button type="button" onClick={() => resendRequest.mutate()} disabled={resendRequest.isPending || resendIn > 0}
          style={{ background: 'none', border: 'none', padding: 0, font: 'inherit', fontWeight: 600, textDecoration: 'underline', cursor: resendIn > 0 ? 'not-allowed' : 'pointer', opacity: resendIn > 0 ? 0.55 : 1 }}>
          {resendIn > 0 ? `Resend Code (${mmss(resendIn)})` : 'Resend Code'}
        </button>
      </p>
      {resendRequest.isError && <p role="alert" className="field-error">{errorMessage(resendRequest.error)}</p>}
    </div>}

    {stage === 'password' && <>
      <header style={{ paddingTop: 32 }}>
        <h1 className="display">Set a new password</h1>
        <p className="lead">Create a strong password that you don’t use on other websites.</p>
      </header>
      <form onSubmit={submitPassword} noValidate>
        <PasswordField id="newPassword" label="New password" value={pw} onChange={(v) => { setPw(v); setPwError(''); }} error={pwError || undefined} autoComplete="new-password" />
        {pw !== '' && <div style={{ marginTop: -8, marginBottom: 16 }}>
          <div style={{ display: 'flex', gap: 4 }} aria-hidden="true">{[0, 1, 2, 3].map((i) => <span key={i} style={{ height: 4, flex: 1, borderRadius: 2, background: i < pwScore ? 'var(--text-primary)' : 'var(--border)' }} />)}</div>
          <p className="body-sm secondary" style={{ marginTop: 6 }}>Password strength: {STRENGTH_LABELS[pwScore]}. {missing.length === 0 ? 'All requirements met.' : `Missing: ${missing.map((r) => r.label.toLowerCase()).join(', ')}.`}</p>
        </div>}
        <PasswordField id="confirmPassword" label="Confirm new password" value={confirmPw} onChange={(v) => { setConfirmPw(v); setConfirmTouched(true); }} error={confirmError} autoComplete="new-password" />
        {formError && <p role="alert" className="field-error">{formError}</p>}
        <Button type="submit" variant="primary" disabled={confirm.isPending}>{confirm.isPending ? 'Resetting…' : 'Reset password'}</Button>
      </form>
    </>}

    {stage === 'success' && <div style={{ textAlign: 'center', paddingTop: 32 }}>
      <svg width="96" height="96" viewBox="0 0 96 96" role="img" aria-label="Password updated" style={{ margin: '8px auto 16px', display: 'block' }}>
        <circle className="fp-check-circle" cx="48" cy="48" r="45" fill="none" stroke="var(--verify-success)" strokeWidth="3" />
        <path className="fp-check-mark" d="M30 49 L43 62 L67 36" fill="none" stroke="var(--verify-success)" strokeWidth="5" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      <h1 className="headline">Password updated!</h1>
      <p className="lead">Your password has been changed successfully. You can now log in with your new password.</p>
      <div style={{ marginTop: 24 }}><Link className="btn btn-primary" to="/login">Back to Log in</Link></div>
    </div>}
  </div>;
}
