import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { ApiError } from '../../api/types';
import { apiGet, apiPost, errorMessage } from '../../api/client';
import { Button } from '../../design/primitives';
import { OtpBoxes } from './OtpBoxes';

/** Registration verification flow (owner brief 2026-11-25 §4–§6):
 *  Step A — progress while the backend runs OCR, polling the real
 *  status through the registration token (recoverable after refresh);
 *  Step B — 6-digit email OTP code; Step C — success with an explicit
 *  "Go to Log in →" (no auto-redirect, no auto-login). A failed or
 *  inconclusive OCR outcome shows its own screen instead. The 30-second
 *  countdown is visual only — the poll, never the timer, decides the
 *  outcome. */
const TOKEN_KEY = 'unilink:verificationToken';
interface RegistrationStatus { ocrStatus: string; requestStatus: string; state: string; ocrMatchScore?: number | null; reason?: string | null; emailOtpSent: boolean; emailVerified: boolean }
type Stage = 'missing' | 'verifying' | 'otp' | 'success' | 'manual' | 'failed';
const fmt = (s: number) => `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;

export default function VerificationFlow(){
  const nav = useNavigate();
  const [token] = useState<string | null>(() => { try { return sessionStorage.getItem(TOKEN_KEY); } catch { return null; } });
  const [stage, setStage] = useState<Stage>(token ? 'verifying' : 'missing');
  const [remaining, setRemaining] = useState(30);
  const [code, setCode] = useState('');
  const [otpError, setOtpError] = useState('');
  const [cooldown, setCooldown] = useState(0);
  const [resent, setResent] = useState(false);
  const q = useQuery({ queryKey: ['registration-status', token], queryFn: () => apiGet<RegistrationStatus>(`/auth/registration/status?token=${encodeURIComponent(token ?? '')}`),
    enabled: token !== null && (stage === 'verifying' || stage === 'otp'), refetchInterval: stage === 'verifying' ? 2000 : false, retry: false });

  useEffect(() => {
    const s = q.data; if (!s) return;
    if (s.emailVerified) { setStage('success'); return; }
    if (s.ocrStatus === 'AUTO_VERIFIED') { setStage('otp'); return; }
    if (s.ocrStatus === 'VERIFICATION_FAILED') { setStage('failed'); return; }
    if (s.ocrStatus === 'MANUAL_REVIEW') { setStage('manual'); return; }
    setStage('verifying');
  }, [q.data]);
  // Step A countdown: visual only, one second at a time.
  useEffect(() => {
    if (stage !== 'verifying' || remaining <= 0) return;
    const t = setTimeout(() => setRemaining((r) => r - 1), 1000);
    return () => clearTimeout(t);
  }, [stage, remaining]);
  // Step B: the code is sent when OCR completes, so the resend timer
  // starts at 60 s when the OTP screen first appears.
  useEffect(() => { if (stage === 'otp') setCooldown(60); }, [stage]);
  useEffect(() => {
    if (cooldown <= 0) return;
    const t = setTimeout(() => setCooldown((c) => c - 1), 1000);
    return () => clearTimeout(t);
  }, [cooldown]);

  const verify = useMutation({ mutationFn: () => apiPost<{ emailVerified: boolean; state: string }>('/auth/email-otp/verify', { token, otp: code }),
    onSuccess: () => setStage('success'),
    onError: (e) => setOtpError(e instanceof ApiError && e.code === 'OTP_INVALID' ? 'Invalid verification code. Please double-check and try again.' : errorMessage(e)) });
  const resend = useMutation({ mutationFn: () => apiPost('/auth/email-otp/resend', { token }),
    onSuccess: () => { setCooldown(60); setCode(''); setOtpError(''); setResent(true); },
    onError: (e) => setOtpError(errorMessage(e)) });
  const goToLogin = () => { try { sessionStorage.removeItem(TOKEN_KEY); } catch { /* best effort */ } nav('/login'); };

  if (stage === 'missing') return <div className="container"><div className="verify-center">
    <h1 className="headline">Verification not found</h1>
    <p className="secondary">We could not find your registration — the sign-up step may not have finished, or this page was opened in a different browser tab.</p>
    <p><Link className="btn btn-primary" to="/signup">Sign up again</Link></p></div></div>;

  if (q.isError && stage === 'verifying') return <div className="container"><div className="verify-center">
    <h1 className="headline">Verification not found</h1>
    <p className="secondary">This verification has expired or is no longer valid. Please sign up again.</p>
    <p><Link className="btn btn-primary" to="/signup">Sign up again</Link></p></div></div>;

  if (stage === 'verifying') return <div className="container"><div className="verify-center">
    <div className="spinner" role="status" aria-label="Verification in progress" />
    <h1 className="headline">Verification in progress...</h1>
    <p className="secondary">Please wait while we verify your student credentials. Do not close or refresh this window.</p>
    {remaining > 0
      ? <p><strong>Time remaining: {fmt(remaining)}</strong></p>
      : <p className="secondary">Still verifying — this can take a minute. You can safely leave this page; your application is saved.</p>}
  </div></div>;

  if (stage === 'otp') return <div className="container"><div className="verify-center">
    <h1 className="headline">Enter verification code</h1>
    <p className="secondary">We&apos;ve sent a 6-digit verification code to your personal email.</p>
    {q.data && !q.data.emailOtpSent && <p role="alert" className="field-error" style={{ textAlign: 'center' }}>We could not send the code to your email. Use the resend button below to try again.</p>}
    <form onSubmit={(e) => { e.preventDefault(); setOtpError(''); verify.mutate(); }}>
      <div style={{ margin: '24px 0 8px' }}><OtpBoxes value={code} onChange={(c) => { setCode(c); setOtpError(''); }} invalid={otpError !== ''} disabled={verify.isPending} autoFocus /></div>
      {otpError !== '' && <p role="alert" className="field-error" style={{ textAlign: 'center' }}>{otpError}</p>}
      {verify.isError && otpError === '' && <p role="alert" className="field-error" style={{ textAlign: 'center' }}>{errorMessage(verify.error)}</p>}
      <div style={{ marginTop: 16 }}><Button type="submit" variant="primary" disabled={verify.isPending || code.length !== 6}>{verify.isPending ? 'Verifying…' : 'Verify code'}</Button></div>
    </form>
    <p className="secondary" style={{ marginTop: 16 }}>Didn&apos;t receive the code? {cooldown > 0
      ? <span>Resend Code ({fmt(cooldown)})</span>
      : <button type="button" className="btn btn-sm" onClick={() => resend.mutate()} disabled={resend.isPending}>Resend Code</button>}</p>
    {resent && <p role="status">A new code was sent. Check your inbox.</p>}
    {resend.isError && <p role="alert" className="field-error" style={{ textAlign: 'center' }}>{errorMessage(resend.error)}</p>}
  </div></div>;

  if (stage === 'success') return <div className="container"><div className="verify-center">
    <div className="success-ring" aria-hidden="true">✓</div>
    <h1 className="headline">Verification Successful!</h1>
    <p className="secondary">Your student identity and email have been verified. Your UniLink account is now active and ready to use.</p>
    <p style={{ marginTop: 24 }}><Button variant="primary" onClick={goToLogin}>Go to Log in →</Button></p>
  </div></div>;

  if (stage === 'manual') return <div className="container"><div className="verify-center">
    <h1 className="headline">Under manual review</h1>
    <p className="secondary">We couldn&apos;t verify your ID card automatically. An administrator will review your application and you will be notified of the decision.</p>
    {q.data?.reason && <p className="secondary">{q.data.reason}</p>}
    <p style={{ marginTop: 24 }}><Link className="btn btn-primary" to="/login">Log in to check your application</Link></p>
  </div></div>;

  return <div className="container"><div className="verify-center">
    <h1 className="headline">Verification failed</h1>
    <p className="secondary">{q.data?.reason ?? 'We could not verify your student ID card.'}</p>
    <p className="secondary">Log in and resubmit a clearer photo of your ID card from Pending Review.</p>
    <p style={{ marginTop: 24 }}><Link className="btn btn-primary" to="/login">Log in to resubmit</Link></p>
  </div></div>;
}
