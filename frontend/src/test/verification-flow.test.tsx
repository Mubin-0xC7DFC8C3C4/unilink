import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '../app/auth';
import { ThemeProvider } from '../app/ThemeContext';
import VerificationFlow from '../features/auth/VerificationFlow';

/** Verification flow (owner brief 2026-11-25 §4–§6): Step A polls the
 *  real OCR status through the registration token (recoverable after
 *  refresh); Step B is a 6-digit email OTP code with 6 boxes; Step C is
 *  the success screen with an explicit "Go to Log in →" (no auto
 *  redirect, no auto-login). MANUAL_REVIEW and VERIFICATION_FAILED have
 *  their own screens. */

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const fetchMock = vi.fn();
const calls: { url: string; method: string; body?: unknown }[] = [];
let statusQueue: { status: number; body: unknown }[] = [];
let statusBody: unknown = null;
function qc() { return new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } }); }
function renderPage() {
  return render(<QueryClientProvider client={qc()}><ThemeProvider><AuthProvider><MemoryRouter initialEntries={['/verification']}><Routes><Route path="/verification" element={<VerificationFlow />} /><Route path="/login" element={<div>LOGIN PAGE</div>} /><Route path="/signup" element={<div>SIGNUP PAGE</div>} /></Routes></MemoryRouter></AuthProvider></ThemeProvider></QueryClientProvider>);
}

beforeEach(() => {
  calls.length = 0; statusQueue = [];
  statusBody = { ocrStatus: 'AUTO_VERIFIED', requestStatus: 'APPROVED', state: 'PENDING_REVIEW', reason: null, emailOtpSent: true, emailVerified: false };
  sessionStorage.setItem('unilink:verificationToken', 'tok-42');
  fetchMock.mockImplementation(async (input: unknown, init?: { method?: string; body?: unknown }) => {
    const url = String(input); const method = init?.method || 'GET';
    calls.push({ url, method, body: init?.body });
    if (url === '/api/csrf') return json({});
    if (url === '/api/auth/me') return json({ message: 'unauthenticated' }, 401);
    if (url.startsWith('/api/auth/registration/status')) {
      const next = statusQueue.shift();
      return next ? json(next.body, next.status) : json(statusBody);
    }
    if (url === '/api/auth/email-otp/verify' && method === 'POST') {
      const body = JSON.parse(String(init?.body));
      if (body.otp === '123456') return json({ emailVerified: true, state: 'VERIFIED' });
      return json({ code: 'OTP_INVALID', message: 'Invalid or expired code' }, 400);
    }
    if (url === '/api/auth/email-otp/resend' && method === 'POST') return json({ emailOtpSent: true });
    return json({ message: 'no mock route for ' + url }, 404);
  });
  vi.stubGlobal('fetch', fetchMock);
});
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); sessionStorage.clear(); });

async function typeCode(user: ReturnType<typeof userEvent.setup>, code: string) {
  await user.click(screen.getByLabelText('Digit 1 of 6'));
  await user.paste(code);
}

describe('VerificationFlow', () => {
  it('Step A: shows the progress screen with the 30 s countdown, then polls to the OTP step', async () => {
    statusQueue = [
      { status: 200, body: { ocrStatus: 'VERIFYING', requestStatus: 'AWAITING_VERIFICATION', state: 'REGISTERED', reason: null, emailOtpSent: false, emailVerified: false } },
      { status: 200, body: { ocrStatus: 'VERIFYING', requestStatus: 'AWAITING_VERIFICATION', state: 'REGISTERED', reason: null, emailOtpSent: false, emailVerified: false } },
      { status: 200, body: { ocrStatus: 'AUTO_VERIFIED', requestStatus: 'AWAITING_VERIFICATION', state: 'PENDING_REVIEW', reason: null, emailOtpSent: true, emailVerified: false } },
    ];
    renderPage();
    expect(screen.getByText('Verification in progress...')).toBeInTheDocument();
    expect(screen.getByText('Time remaining: 00:30')).toBeInTheDocument();
    expect(screen.getByText(/Do not close or refresh this window\./)).toBeInTheDocument();
    await screen.findByText('Enter verification code', {}, { timeout: 10000 });
    expect(screen.getByText("We've sent a 6-digit verification code to your personal email.")).toBeInTheDocument();
    expect(calls.filter((c) => c.url.startsWith('/api/auth/registration/status')).length).toBeGreaterThanOrEqual(3);
  });

  it('Step B: pasting a code fills the six boxes and verifying shows the success screen', async () => {
    const user = userEvent.setup(); renderPage();
    await screen.findByText('Enter verification code');
    await typeCode(user, '123456');
    expect(screen.getByLabelText('Digit 1 of 6')).toHaveValue('1');
    expect(screen.getByLabelText('Digit 6 of 6')).toHaveValue('6');
    await user.click(screen.getByRole('button', { name: 'Verify code' }));
    await screen.findByText('Verification Successful!');
    expect(screen.getByText('Your student identity and email have been verified. Your UniLink account is now active and ready to use.')).toBeInTheDocument();
    const verify = calls.find((c) => c.url === '/api/auth/email-otp/verify');
    expect(JSON.parse(String(verify?.body))).toEqual({ token: 'tok-42', otp: '123456' });
    // Step C: no auto-login — the account becomes usable only after the
    // student clicks through to the login page.
    expect(calls.some((c) => c.url === '/api/auth/login')).toBe(false);
    await user.click(screen.getByRole('button', { name: /Go to Log in/ }));
    await screen.findByText('LOGIN PAGE');
  });

  it('Step B: a wrong code shows the invalid-code message and stays on the OTP screen', async () => {
    const user = userEvent.setup(); renderPage();
    await screen.findByText('Enter verification code');
    await typeCode(user, '000000');
    await user.click(screen.getByRole('button', { name: 'Verify code' }));
    await screen.findByText('Invalid verification code. Please double-check and try again.');
    expect(screen.getByText('Enter verification code')).toBeInTheDocument();
  });

  it('Step B: the resend button is disabled for 60 s, then resends and confirms', async () => {
    const user = userEvent.setup(); renderPage();
    await screen.findByText('Enter verification code');
    expect(screen.queryByRole('button', { name: /Resend Code/ })).toBeNull();
    expect(screen.getByText(/Resend Code \(01:00\)/)).toBeInTheDocument();
    await new Promise((r) => setTimeout(r, 61000));
    await user.click(await screen.findByRole('button', { name: /Resend Code/ }));
    await screen.findByText('A new code was sent. Check your inbox.');
    expect(calls.some((c) => c.url === '/api/auth/email-otp/resend')).toBe(true);
  }, 90000);

  it('MANUAL_REVIEW shows the under-review screen with the reason', async () => {
    statusBody = { ocrStatus: 'MANUAL_REVIEW', requestStatus: 'PENDING_REVIEW', state: 'PENDING_REVIEW', reason: 'The ID card image was not clear enough to read automatically.', emailOtpSent: false, emailVerified: false };
    renderPage();
    await screen.findByText('Under manual review');
    expect(screen.getByText('The ID card image was not clear enough to read automatically.')).toBeInTheDocument();
  });

  it('VERIFICATION_FAILED shows the reason and directs the student to log in and resubmit', async () => {
    statusBody = { ocrStatus: 'VERIFICATION_FAILED', requestStatus: 'REJECTED', state: 'REJECTED', reason: 'The student ID on the card does not match your registration.', emailOtpSent: false, emailVerified: false };
    renderPage();
    await screen.findByText('Verification failed');
    expect(screen.getByText('The student ID on the card does not match your registration.')).toBeInTheDocument();
    expect(screen.getByText('Log in to resubmit')).toBeInTheDocument();
  });

  it('shows the not-found screen when no registration token is stored', async () => {
    sessionStorage.clear();
    renderPage();
    await screen.findByText('Verification not found');
    expect(screen.getByText('Sign up again')).toBeInTheDocument();
  });

  it('shows the expired screen when the token is unknown', async () => {
    statusQueue = [{ status: 404, body: { code: 'NOT_FOUND', message: 'Not found' } }];
    renderPage();
    await screen.findByText('This verification has expired or is no longer valid. Please sign up again.');
  });
});
