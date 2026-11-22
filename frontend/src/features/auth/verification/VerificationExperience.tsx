import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '../../../design/primitives';
import { VerificationError, mockVerificationAdapter, type VerificationAdapter } from './verificationAdapter';

/** Phase 2.3 — verification screens built to the owner's exact content
 *  specification (2026-11-20 correction): progress → code → success, and
 *  nothing else on screen. All behaviour runs on the isolated prototype
 *  adapter (verificationAdapter.ts) — the backend has no email-OTP or
 *  verification-progress support, no real email is sent, and these screens
 *  are not linked from the live sign-up flow. */

type Stage = 'progress' | 'code' | 'success';

const CODE_ERROR = 'Invalid verification code. Please double-check and try again.';
const RESEND_SECONDS = 30;

function maskEmail(email: string): string {
  const [local, domain] = email.split('@');
  if (!domain) return email;
  return `${local.slice(0, 1)}•••@${domain}`;
}
const mmss = (s: number) => `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;

/** The only non-palette colours on these screens, both specified by the
 *  owner for this UI: green for successful verification, red for errors.
 *  Scoped here so the design tokens stay untouched. */
const SCOPED_CSS = `
.verify-scope { --verify-success: #1a7f37; --verify-error: #c81e1e; }
@keyframes otp-shake { 10%, 90% { transform: translateX(-1px); } 20%, 80% { transform: translateX(2px); } 30%, 50%, 70% { transform: translateX(-3px); } 40%, 60% { transform: translateX(3px); } }
.otp-shake { animation: otp-shake 0.45s ease both; }
@keyframes verify-spin { to { transform: rotate(360deg); } }
.verify-spinner { width: 28px; height: 28px; border-radius: 50%; border: 3px solid var(--border); border-top-color: var(--text-primary); animation: verify-spin 0.8s linear infinite; margin: 0 auto 16px; }
@keyframes verify-draw { to { stroke-dashoffset: 0; } }
.verify-check-circle { stroke-dasharray: 302; stroke-dashoffset: 302; animation: verify-draw 0.7s ease forwards; }
.verify-check-mark { stroke-dasharray: 60; stroke-dashoffset: 60; animation: verify-draw 0.45s 0.55s ease forwards; }
`;

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
  return <div className={shaking ? 'otp-shake' : undefined} onAnimationEnd={onShakeEnd}
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

export default function VerificationExperience({ adapter = mockVerificationAdapter, progressSeconds = 30 }: { adapter?: VerificationAdapter; progressSeconds?: number }) {
  const [stage, setStage] = useState<Stage>('progress');
  const [secondsLeft, setSecondsLeft] = useState(progressSeconds);
  const [digits, setDigits] = useState<string[]>(Array(6).fill(''));
  const [error, setError] = useState(false);
  const [shaking, setShaking] = useState(false);
  const [busy, setBusy] = useState(false);
  const [resendIn, setResendIn] = useState(RESEND_SECONDS);
  const inputRefs = useRef<(HTMLInputElement | null)[]>([]);
  const email = maskEmail(adapter.getSubmittedRegistration().email);
  const code = digits.join('');
  const complete = code.length === 6;

  // Prototype readiness: stands in for the future server response. When it
  // resolves, the code is "sent" and the code screen opens.
  useEffect(() => {
    let cancelled = false;
    adapter.whenVerificationReady()
      .then(() => adapter.sendEmailOtp())
      .then(() => { if (!cancelled) { setStage('code'); setResendIn(RESEND_SECONDS); } })
      .catch(() => { /* the prototype adapter never rejects; a real one would surface an error state here */ });
    return () => { cancelled = true; };
  }, [adapter]);

  useEffect(() => {
    if (stage !== 'progress' || secondsLeft <= 0) return;
    const t = setTimeout(() => setSecondsLeft((s) => Math.max(0, s - 1)), 1000);
    return () => clearTimeout(t);
  }, [stage, secondsLeft]);

  useEffect(() => {
    if (stage !== 'code' || resendIn <= 0) return;
    const t = setTimeout(() => setResendIn((s) => s - 1), 1000);
    return () => clearTimeout(t);
  }, [stage, resendIn]);

  useEffect(() => { if (stage === 'code') inputRefs.current[0]?.focus(); }, [stage]);
  const navigate = useNavigate();
  // Brief flow: a successful verification continues straight into the
  // Community Home feed.
  useEffect(() => { if (stage !== 'success') return; const t = setTimeout(() => navigate('/feed'), 2000); return () => clearTimeout(t); }, [stage, navigate]);

  const applyDigits = (_index: number, next: string[], focusIndex: number | null) => {
    setDigits(next); setError(false);
    if (focusIndex !== null) inputRefs.current[focusIndex]?.focus();
  };

  const verify = async () => {
    if (!complete || busy) return;
    setBusy(true);
    try { await adapter.verifyEmailOtp(code); setStage('success'); }
    catch (err) {
      if (err instanceof VerificationError) {
        // The specification defines one error treatment; an expired demo
        // code follows the same path as an incorrect one.
        setError(true); setShaking(true); setDigits(Array(6).fill(''));
        setTimeout(() => inputRefs.current[0]?.focus(), 0);
      }
    } finally { setBusy(false); }
  };

  const resend = async () => {
    if (busy || resendIn > 0) return;
    setBusy(true);
    try { await adapter.sendEmailOtp(); setError(false); setDigits(Array(6).fill('')); setResendIn(RESEND_SECONDS); inputRefs.current[0]?.focus(); }
    finally { setBusy(false); }
  };

  return <div className="container verify-scope" style={{ maxWidth: 560, textAlign: 'center', paddingTop: 32 }}>
    <style>{SCOPED_CSS}</style>

    {stage === 'progress' && <>
      <div className="verify-spinner" aria-hidden="true" />
      <h1 className="headline">Verification in progress...</h1>
      <p className="lead">Please wait while we verify your student credentials. Do not close or refresh this window.</p>
      {secondsLeft > 0
        ? <p className="title" style={{ marginTop: 32 }}>Time remaining: {mmss(secondsLeft)}</p>
        : <p className="title" style={{ marginTop: 32 }}>Taking a bit longer than usual... Almost done!</p>}
    </>}

    {stage === 'code' && <>
      <h1 className="headline">Enter Verification Code</h1>
      <p className="lead">We’ve sent a 6-digit verification code to your personal email ({email}).</p>
      <OtpBoxes digits={digits} error={error} shaking={shaking} onShakeEnd={() => setShaking(false)} onDigit={applyDigits} inputRefs={inputRefs} />
      {error && <p role="alert" style={{ color: 'var(--verify-error)', margin: '8px 0 0' }}>{CODE_ERROR}</p>}
      <div style={{ marginTop: 24 }}>
        <Button type="button" variant="primary" disabled={!complete || busy} onClick={verify}>Verify Account</Button>
      </div>
      <p style={{ marginTop: 20 }}>Didn't receive the code?{' '}
        <button type="button" onClick={resend} disabled={busy || resendIn > 0}
          style={{ background: 'none', border: 'none', padding: 0, font: 'inherit', fontWeight: 600, textDecoration: 'underline', cursor: resendIn > 0 ? 'not-allowed' : 'pointer', opacity: resendIn > 0 ? 0.55 : 1 }}>
          {resendIn > 0 ? `Resend Code (${mmss(resendIn)})` : 'Resend Code'}
        </button>
      </p>
    </>}

    {stage === 'success' && <>
      <svg width="96" height="96" viewBox="0 0 96 96" role="img" aria-label="Verified" style={{ margin: '8px auto 16px', display: 'block' }}>
        <circle className="verify-check-circle" cx="48" cy="48" r="45" fill="none" stroke="var(--verify-success)" strokeWidth="3" />
        <path className="verify-check-mark" d="M30 49 L43 62 L67 36" fill="none" stroke="var(--verify-success)" strokeWidth="5" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      <h1 className="headline">Verification Successful!</h1>
      <p className="lead">Your student account has been verified. Redirecting to your dashboard...</p>
    </>}
  </div>;
}
