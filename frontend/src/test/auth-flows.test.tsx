import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider, RequireAuth, useAuth } from '../app/auth';
import SignUp from '../features/auth/SignUp';
import OtpStep from '../features/auth/OtpStep';
import ForgotPassword from '../features/auth/ForgotPassword';
import EmailVerify from '../features/auth/EmailVerify';
import AdminLogin from '../admin/pages/AdminLogin';
import Totp from '../admin/pages/Totp';

/** Auth contract tests (I-4/I-5/I-6/I-10): every corrected frontend call is
 *  asserted against the mocked fetch layer — path, method and payload. */

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

interface Call { url: string; method: string; body: unknown }
let calls: Call[] = [];
// The fetch mock is async, so a route may also return a promise — used by
// the slow-response test to hold one response back.
let route: (url: string, method: string, body: unknown) => Response | Promise<Response> | undefined;

const fetchMock = vi.fn();
function defaultRoute(url: string): Response | undefined {
  if (url === '/api/csrf') return json({});
  if (url === '/api/auth/me' || url === '/api/admin/auth/me') return json({ message: 'unauthenticated' }, 401);
  return undefined;
}
function qc() { return new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } }); }
function renderWithProviders(ui: React.ReactNode, initialEntries: (string | { pathname: string; state?: unknown })[] = ['/']) {
  return render(<QueryClientProvider client={qc()}><AuthProvider><MemoryRouter initialEntries={initialEntries}>{ui}</MemoryRouter></AuthProvider></QueryClientProvider>);
}

beforeEach(() => {
  calls = [];
  sessionStorage.clear();
  route = (url) => defaultRoute(url);
  fetchMock.mockImplementation(async (input: unknown, init?: RequestInit) => {
    const url = String(input);
    const method = init?.method || 'GET';
    calls.push({ url, method, body: init?.body });
    return route(url, method, init?.body) ?? json({ message: 'no mock route for ' + url }, 404);
  });
  vi.stubGlobal('fetch', fetchMock);
});
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); vi.useRealTimers(); });

function jsonBody(call: Call): Record<string, unknown> {
  return JSON.parse(String(call.body)) as Record<string, unknown>;
}

describe('SignUp (I-6)', () => {
  function catalogRoute(url: string, method: string): Response | undefined {
    if (url === '/api/public/universities') return json([{ id: 1, name: 'Metropolitan University' }, { id: 2, name: 'University of Dhaka' }]);
    if (url.startsWith('/api/public/departments')) return json([{ id: 10, universityId: 1, name: 'CSE' }]);
    if (url.startsWith('/api/public/batches')) return json([{ id: 100, universityId: 1, departmentId: 10, label: '45' }]);
    if (url === '/api/auth/register' && method === 'POST') return json({ id: 42, fullName: 'Test User', state: 'REGISTERED', verificationToken: 'tok-42' }, 201);
    return defaultRoute(url);
  }
  it('loads the catalog and submits universityId/departmentId/batchId + one idCard, then goes to verification', async () => {
    route = catalogRoute;
    const user = userEvent.setup();
    renderWithProviders(<Routes><Route path="/signup" element={<SignUp />} /><Route path="/verification" element={<div>VERIFICATION PAGE</div>} /></Routes>, ['/signup']);
    await user.type(screen.getByLabelText('Full name'), 'Test User');
    await user.type(screen.getByLabelText('Phone'), '01712345678');
    await user.type(screen.getByLabelText('Personal email'), 'test@example.com');
    await user.click(screen.getByRole('button', { name: /Continue/ }));
    // Phase 2.2: university/department/batch are searchable selectors over
    // the same catalogue endpoints — pick from the filtered list.
    await user.click(screen.getByLabelText('University'));
    await user.click(await screen.findByRole('option', { name: 'Metropolitan University' }));
    expect(calls.some((c) => c.url === '/api/public/departments?universityId=1')).toBe(true);
    await user.click(screen.getByLabelText('Department'));
    await user.click(await screen.findByRole('option', { name: 'CSE' }));
    await user.click(screen.getByLabelText('Batch'));
    await user.click(await screen.findByRole('option', { name: '45' }));
    await user.type(screen.getByLabelText('University Address'), '123 Test Road, Dhaka');
    await user.click(screen.getByRole('button', { name: /Continue/ }));
    await user.type(screen.getByLabelText('Student ID number'), 'CSE-001');
    await user.type(screen.getByLabelText('Password', { exact: true }), 'Password1');
    await user.type(screen.getByLabelText('Confirm password'), 'Password1');
    // One student ID card image only (D-177/D-178): no back side, no
    // profile photo at registration — the photo is uploaded later from
    // Settings → Edit Profile.
    await user.upload(screen.getByLabelText('Student ID card image'), new File(['id'], 'id-card.jpg', { type: 'image/jpeg' }));
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    await screen.findByText('VERIFICATION PAGE');
    const reg = calls.find((c) => c.url === '/api/auth/register');
    expect(reg?.method).toBe('POST');
    const fd = reg?.body as FormData;
    expect(fd.get('universityId')).toBe('1');
    expect(fd.get('departmentId')).toBe('10');
    expect(fd.get('batchId')).toBe('100');
    expect(fd.get('phone')).toBe('01712345678');
    expect((fd.get('idCard') as File).name).toBe('id-card.jpg');
    expect(fd.get('idCardBack')).toBeNull();
    expect(fd.get('profilePhoto')).toBeNull();
    expect(fd.get('confirmPassword')).toBeNull();
    expect(fd.get('idCardImage')).toBeNull();
    // The registration token scopes the verification flow; the phone-OTP
    // hand-off is gone with the phone step (D-173).
    expect(sessionStorage.getItem('unilink:verificationToken')).toBe('tok-42');
    expect(sessionStorage.getItem('unilink:otpUserId')).toBeNull();
    expect(calls.some((c) => c.url.startsWith('/api/public/batches?universityId=1'))).toBe(true);
  });
  it('rejects a non-Bangladeshi phone number before any register call', async () => {
    route = catalogRoute;
    const user = userEvent.setup();
    renderWithProviders(<Routes><Route path="/signup" element={<SignUp />} /></Routes>, ['/signup']);
    await user.type(screen.getByLabelText('Full name'), 'Test User');
    await user.type(screen.getByLabelText('Phone'), '01234567890');
    await user.type(screen.getByLabelText('Personal email'), 'test@example.com');
    await user.click(screen.getByRole('button', { name: /Continue/ }));
    await screen.findByText('Enter a valid Bangladeshi mobile number, e.g. 01712345678');
    expect(calls.some((c) => c.url === '/api/auth/register')).toBe(false);
  });
});

describe('Signup catalogue chain (2026-11-24)', () => {
  const catalog = (url: string) => {
    if (url === '/api/public/universities') return json([{ id: 1, name: 'Metropolitan University' }, { id: 2, name: 'University of Dhaka' }]);
    if (url === '/api/public/departments?universityId=1') return json([{ id: 10, universityId: 1, name: 'CSE' }, { id: 11, universityId: 1, name: 'EEE' }]);
    if (url === '/api/public/departments?universityId=2') return json([{ id: 20, universityId: 2, name: 'Physics' }]);
    if (url.startsWith('/api/public/batches')) return json([{ id: 100, universityId: 1, departmentId: 10, label: '45' }]);
    return defaultRoute(url);
  };
  async function toStep1(user: ReturnType<typeof userEvent.setup>) {
    renderWithProviders(<Routes><Route path="/signup" element={<SignUp />} /></Routes>, ['/signup']);
    await user.type(screen.getByLabelText('Full name'), 'Test User');
    await user.type(screen.getByLabelText('Phone'), '01712345678');
    await user.type(screen.getByLabelText('Personal email'), 'test@example.com');
    await user.click(screen.getByRole('button', { name: /Continue/ }));
  }
  it('retries a transient catalogue failure and the university list appears', async () => {
    let attempts = 0;
    route = (url: string) => {
      if (url === '/api/public/universities') { attempts += 1; return attempts === 1 ? json({ message: 'boom' }, 500) : catalog(url); }
      return catalog(url);
    };
    const user = userEvent.setup();
    await toStep1(user);
    await user.click(screen.getByLabelText('University'));
    await screen.findByRole('option', { name: 'Metropolitan University' }, { timeout: 5000 });
    expect(attempts).toBeGreaterThanOrEqual(2);
  });
  it('handles an empty university response with a retry option', async () => {
    route = (url: string) => {
      if (url === '/api/public/universities') return json([]);
      return catalog(url);
    };
    const user = userEvent.setup();
    await toStep1(user);
    await screen.findByText('No universities are available right now.');
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });
  // Real timers: the retry backoffs sum to ~60 s, so this test waits for the
  // whole cycle to exhaust before asserting the error state and the manual
  // retry. Fake timers deadlock with user-event + react-query here.
  it('shows an error with a retry button when the catalogue keeps failing, and retrying loads it', async () => {
    let uniFails = true;
    route = (url: string) => {
      if (url === '/api/public/universities') return uniFails ? json({ message: 'boom' }, 500) : catalog(url);
      return catalog(url);
    };
    const user = userEvent.setup();
    await toStep1(user);
    await screen.findByText(/Could not load universities/, {}, { timeout: 90000 });
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
    uniFails = false;
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    await user.click(screen.getByLabelText('University'));
    await screen.findByRole('option', { name: 'Metropolitan University' });
  }, 90000);
  it('loads departments for the chosen university and batches for the chosen department, and resets children on change', async () => {
    route = catalog;
    const user = userEvent.setup();
    await toStep1(user);
    await user.click(screen.getByLabelText('University'));
    await user.click(await screen.findByRole('option', { name: 'Metropolitan University' }));
    expect(calls.some((c) => c.url === '/api/public/departments?universityId=1')).toBe(true);
    await user.click(screen.getByLabelText('Department'));
    await user.click(await screen.findByRole('option', { name: 'CSE' }));
    expect(calls.some((c) => c.url.startsWith('/api/public/batches?universityId=1&departmentId=10'))).toBe(true);
    await user.click(screen.getByLabelText('Batch'));
    await user.click(await screen.findByRole('option', { name: '45' }));
    expect(screen.getByLabelText('Batch')).toHaveValue('45');
    // Changing the department resets the batch. (The searchable select
    // filters by the current value, so clear the text first to see all options.)
    await user.clear(screen.getByLabelText('Department'));
    await user.click(screen.getByLabelText('Department'));
    await user.click(await screen.findByRole('option', { name: 'EEE' }));
    expect(screen.getByLabelText('Batch')).toHaveValue('');
    // Changing the university resets department and batch.
    await user.clear(screen.getByLabelText('University'));
    await user.click(screen.getByLabelText('University'));
    await user.click(await screen.findByRole('option', { name: 'University of Dhaka' }));
    expect(screen.getByLabelText('Department')).toHaveValue('');
    expect(screen.getByLabelText('Batch')).toHaveValue('');
    expect(calls.some((c) => c.url === '/api/public/departments?universityId=2')).toBe(true);
  });
  it('a slow response for the previous university never overwrites the current dropdown', async () => {
    let resolveMu: (v: Response) => void;
    const muDepts = new Promise<Response>((res) => { resolveMu = res; });
    route = (url: string) => {
      if (url === '/api/public/universities') return catalog(url);
      if (url === '/api/public/departments?universityId=1') return muDepts;
      if (url === '/api/public/departments?universityId=2') return catalog(url);
      return catalog(url);
    };
    const user = userEvent.setup();
    await toStep1(user);
    await user.click(screen.getByLabelText('University'));
    await user.click(await screen.findByRole('option', { name: 'Metropolitan University' }));
    // Switch university before MU's departments arrive. (The searchable
    // select filters by the current value, so clear the text first.)
    await user.clear(screen.getByLabelText('University'));
    await user.click(screen.getByLabelText('University'));
    await user.click(await screen.findByRole('option', { name: 'University of Dhaka' }));
    await user.click(screen.getByLabelText('Department'));
    await screen.findByRole('option', { name: 'Physics' });
    // MU's slow response arrives now — it must not leak into the DU list.
    resolveMu!(json([{ id: 10, universityId: 1, name: 'CSE' }]));
    await new Promise((r) => setTimeout(r, 50));
    expect(screen.queryByRole('option', { name: 'CSE' })).toBeNull();
    expect(screen.getByRole('option', { name: 'Physics' })).toBeInTheDocument();
  });
});

describe('OtpStep (I-5)', () => {
  it('verifies with {userId, otp} at /auth/verify-phone and resends at /auth/otp/resend', async () => {
    sessionStorage.setItem('unilink:otpUserId', '42');
    route = (url, method) => {
      if (url === '/api/auth/verify-phone' && method === 'POST') return json({ id: 42, state: 'REGISTERED' });
      if (url === '/api/auth/otp/resend' && method === 'POST') return json({ status: 'OTP resent', channel: 'SMS' });
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithProviders(<Routes><Route path="/otp" element={<OtpStep />} /><Route path="/pending" element={<div>PENDING PAGE</div>} /></Routes>, ['/otp']);
    await user.type(screen.getByLabelText('6-digit OTP'), '123456');
    await user.click(screen.getByRole('button', { name: 'Resend OTP' }));
    await screen.findByText(/A new OTP was sent by SMS/);
    const resend = calls.find((c) => c.url === '/api/auth/otp/resend');
    expect(resend?.method).toBe('POST');
    expect(jsonBody(resend!)).toEqual({ userId: 42 });
    expect(screen.getByRole('button', { name: /Resend OTP \(60s\)/ })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Verify OTP' }));
    await screen.findByText('PENDING PAGE');
    const verify = calls.find((c) => c.url === '/api/auth/verify-phone');
    expect(verify?.method).toBe('POST');
    expect(jsonBody(verify!)).toEqual({ userId: 42, otp: '123456' });
    expect(sessionStorage.getItem('unilink:otpUserId')).toBeNull();
  });
  it('asks the user to sign up again when no registration user id is stored', async () => {
    renderWithProviders(<Routes><Route path="/otp" element={<OtpStep />} /></Routes>, ['/otp']);
    await screen.findByText(/could not find your new account/);
    expect(calls.some((c) => c.url === '/api/auth/verify-phone')).toBe(false);
  });
  it('shows a resend-verification-email option when registration reported the email was not sent', async () => {
    sessionStorage.setItem('unilink:otpUserId', '42');
    sessionStorage.setItem('unilink:emailSent', 'false');
    route = (url, method) => {
      if (url === '/api/auth/email-verification/resend' && method === 'POST') return json({ status: 'Verification email resent' });
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithProviders(<Routes><Route path="/otp" element={<OtpStep />} /></Routes>, ['/otp']);
    await screen.findByText(/could not send your verification email/);
    await user.click(screen.getByRole('button', { name: 'Resend verification email' }));
    await screen.findByText('Verification email resent. Check your inbox.');
    const resend = calls.find((c) => c.url === '/api/auth/email-verification/resend');
    expect(resend?.method).toBe('POST');
    expect(jsonBody(resend!)).toEqual({ userId: 42 });
  });
});

describe('EmailVerify resend (real SMTP)', () => {
  it('offers a resend form when no token is present and posts the typed email', async () => {
    route = (url, method) => {
      if (url === '/api/auth/email-verification/resend' && method === 'POST') return json({ status: 'Verification email resent' });
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithProviders(<Routes><Route path="/verify-email" element={<EmailVerify />} /></Routes>, ['/verify-email']);
    await screen.findByText('No verification token was provided in the link.');
    await user.type(screen.getByLabelText('Email address'), 'test@example.com');
    await user.click(screen.getByRole('button', { name: 'Resend verification email' }));
    await screen.findByText(/a new verification link was sent/);
    const resend = calls.find((c) => c.url === '/api/auth/email-verification/resend');
    expect(resend?.method).toBe('POST');
    expect(jsonBody(resend!)).toEqual({ email: 'test@example.com' });
  });
});

describe('ForgotPassword (I-5, Phase 2.4)', () => {
  const okRoute = (url: string, method?: string) => {
    if (url === '/api/auth/password-reset/request' && method === 'POST') return json({ status: 'If the email exists, a reset code was sent' });
    if (url === '/api/auth/password-reset/confirm' && method === 'POST') return json({ status: 'Password changed. Please log in again.' });
    return defaultRoute(url);
  };
  async function toPasswordStep(user: ReturnType<typeof userEvent.setup>) {
    renderWithProviders(<Routes><Route path="/forgot-password" element={<ForgotPassword />} /></Routes>, ['/forgot-password']);
    await user.type(screen.getByLabelText('Personal email'), 'test@example.com');
    await user.click(screen.getByRole('button', { name: 'Send verification code' }));
    await screen.findByText('Enter verification code');
    // D-189: the code screen must not claim delivery — the backend
    // answers "If the email exists, a reset code was sent" and the
    // provider may still have failed.
    expect(screen.getByText(/If an account exists for/)).toBeInTheDocument();
    expect(screen.getByText(/t•••@example\.com/)).toBeInTheDocument();
    const boxes = screen.getAllByRole('textbox');
    expect(boxes).toHaveLength(6);
    for (let i = 0; i < 6; i++) await user.type(boxes[i], '123456'[i]);
    await user.click(screen.getByRole('button', { name: 'Verify code' }));
    await screen.findByText('Set a new password');
  }

  it('uses request then confirm — the code step itself makes no API call', async () => {
    route = okRoute;
    const user = userEvent.setup();
    await toPasswordStep(user);
    // "Verify code" advanced locally: still only the request call exists.
    expect(calls.filter((c) => c.url.startsWith('/api/auth/password-reset'))).toHaveLength(1);
    await user.type(screen.getByLabelText('New password'), 'short');
    await user.click(screen.getByRole('button', { name: 'Reset password' }));
    await screen.findByText('Password must be at least 8 characters with an upper-case letter, a lower-case letter and a digit.');
    expect(calls.some((c) => c.url === '/api/auth/password-reset/confirm')).toBe(false);
    await user.clear(screen.getByLabelText('New password'));
    await user.type(screen.getByLabelText('New password'), 'Password1');
    await user.type(screen.getByLabelText('Confirm new password'), 'Password1');
    await user.click(screen.getByRole('button', { name: 'Reset password' }));
    await screen.findByText('Password updated!');
    const req = calls.find((c) => c.url === '/api/auth/password-reset/request');
    expect(jsonBody(req!)).toEqual({ email: 'test@example.com' });
    const confirm = calls.find((c) => c.url === '/api/auth/password-reset/confirm');
    expect(jsonBody(confirm!)).toEqual({ email: 'test@example.com', otp: '123456', newPassword: 'Password1' });
  });

  it('returns to the code step with the invalid-code state when confirm rejects the code', async () => {
    route = (url, method) => {
      if (url === '/api/auth/password-reset/confirm' && method === 'POST') return json({ code: 'OTP_INVALID', message: 'Incorrect code' }, 400);
      return okRoute(url, method);
    };
    const user = userEvent.setup();
    await toPasswordStep(user);
    await user.type(screen.getByLabelText('New password'), 'Password1');
    await user.type(screen.getByLabelText('Confirm new password'), 'Password1');
    await user.click(screen.getByRole('button', { name: 'Reset password' }));
    expect(await screen.findByText('Invalid verification code. Please double-check and try again.')).toBeInTheDocument();
    for (const b of screen.getAllByRole('textbox')) expect((b as HTMLInputElement).value).toBe('');
  });

  it('shows the expired state on the code step when confirm reports OTP_EXPIRED', async () => {
    route = (url, method) => {
      if (url === '/api/auth/password-reset/confirm' && method === 'POST') return json({ code: 'OTP_EXPIRED', message: 'Reset code has expired.' }, 400);
      return okRoute(url, method);
    };
    const user = userEvent.setup();
    await toPasswordStep(user);
    await user.type(screen.getByLabelText('New password'), 'Password1');
    await user.type(screen.getByLabelText('Confirm new password'), 'Password1');
    await user.click(screen.getByRole('button', { name: 'Reset password' }));
    expect(await screen.findByText('Reset code has expired.')).toBeInTheDocument();
  });
});

describe('EmailVerify (I-5)', () => {
  it('calls /auth/verify-email, not /auth/email/verify', async () => {
    route = (url) => {
      if (url === '/api/auth/verify-email?token=abc123') return json({ id: 42, emailVerified: true });
      return defaultRoute(url);
    };
    renderWithProviders(<Routes><Route path="/verify-email" element={<EmailVerify />} /></Routes>, ['/verify-email?token=abc123']);
    await screen.findByText(/Your email is verified/);
    expect(calls.some((c) => c.url === '/api/auth/verify-email?token=abc123')).toBe(true);
    expect(calls.some((c) => c.url.includes('/auth/email/verify'))).toBe(false);
  });
});

describe('Admin login + TOTP (I-4)', () => {
  it('logs in at /admin/auth/login and follows the stage to the TOTP page', async () => {
    route = (url, method) => {
      if (url === '/api/admin/auth/login' && method === 'POST') return json({ adminId: 7, email: 'admin@x.com', stage: 'NEEDS_ENROLMENT', otpauthUrl: 'otpauth://totp/UniLink:admin?secret=ABC' });
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithProviders(<Routes><Route path="/admin/login" element={<AdminLogin />} /><Route path="/admin/totp" element={<Totp />} /></Routes>, ['/admin/login']);
    await user.type(screen.getByLabelText('Admin email or ID'), 'admin@x.com');
    await user.type(screen.getByLabelText('Password'), 'AdminPassword123');
    await user.click(screen.getByRole('button', { name: 'Sign in to Console' }));
    await screen.findByText('TOTP verification');
    expect(screen.getByText(/otpauth:\/\/totp\/UniLink:admin/)).toBeInTheDocument();
    const login = calls.find((c) => c.url === '/api/admin/auth/login');
    expect(jsonBody(login!)).toEqual({ email: 'admin@x.com', password: 'AdminPassword123' });
  });
  it('shows the uniform invalid-credentials message on a 401 and renders the portal chrome (Phase 2.5)', async () => {
    route = (url, method) => {
      if (url === '/api/admin/auth/login' && method === 'POST') return json({ code: 'INVALID_CREDENTIALS', message: 'Email or password is incorrect' }, 401);
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithProviders(<Routes><Route path="/admin/login" element={<AdminLogin />} /></Routes>, ['/admin/login']);
    expect(screen.getByText('ADMIN PORTAL')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to Student Site' })).toHaveAttribute('href', '/');
    expect(screen.getByText('Sign in to Admin Console')).toBeInTheDocument();
    expect(screen.getByText('Forgot credentials? Contact system administrator.')).toBeInTheDocument();
    expect(screen.getByText('UniLink Administration · All login attempts are monitored and logged.')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /sign up/i })).not.toBeInTheDocument();
    await user.type(screen.getByLabelText('Admin email or ID'), 'admin@x.com');
    await user.type(screen.getByLabelText('Password'), 'AdminPassword123');
    await user.click(screen.getByRole('button', { name: 'Sign in to Console' }));
    expect(await screen.findByText('Invalid admin credentials. Please verify your email and password.')).toBeInTheDocument();
  });
  it('enrols at /admin/auth/totp/enrol, shows recovery codes once, then verifies', async () => {
    route = (url, method) => {
      if (url === '/api/admin/auth/totp/enrol' && method === 'POST') return json({ status: 'enrolled', recoveryCodes: ['1111-2222', '3333-4444'] });
      if (url === '/api/admin/auth/totp/verify' && method === 'POST') return json({ adminId: 7, email: 'admin@x.com', role: 'PLATFORM_ADMIN', mfaVerified: true });
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithProviders(<Routes><Route path="/admin/totp" element={<Totp />} /><Route path="/admin" element={<div>ADMIN HOME</div>} /></Routes>,
      [{ pathname: '/admin/totp', state: { stage: 'NEEDS_ENROLMENT', otpauthUrl: 'otpauth://totp/x' } }]);
    await user.type(screen.getByLabelText(/confirms enrolment/), '123456');
    await user.click(screen.getByRole('button', { name: 'Enrol TOTP' }));
    await screen.findByText('1111-2222');
    expect(screen.getByText(/shown only once/)).toBeInTheDocument();
    const enrol = calls.find((c) => c.url === '/api/admin/auth/totp/enrol');
    expect(jsonBody(enrol!)).toEqual({ code: '123456' });
    await user.click(screen.getByRole('button', { name: 'Verify and enter admin' }));
    await screen.findByText('ADMIN HOME');
    const verify = calls.find((c) => c.url === '/api/admin/auth/totp/verify');
    expect(jsonBody(verify!)).toEqual({ code: '123456' });
  });
});

describe('AuthProvider + RequireAuth (I-10)', () => {
  function Probe() {
    const { user, ready } = useAuth();
    return <div>{ready ? 'READY' : 'NOT READY'}|{user ? `USER:${user.email ?? user.fullName}` : 'NO USER'}</div>;
  }
  it('primes the CSRF cookie and restores the session from /auth/me', async () => {
    route = (url) => {
      if (url === '/api/auth/me') return json({ id: 5, fullName: 'Test User', email: 't@x.com', state: 'VERIFIED' });
      return defaultRoute(url);
    };
    render(<QueryClientProvider client={qc()}><AuthProvider><Probe /><RequireAuth fallback={<div>FALLBACK</div>}><div>GUARDED</div></RequireAuth></AuthProvider></QueryClientProvider>);
    await screen.findByText(/USER:t@x\.com/);
    expect(screen.getByText('GUARDED')).toBeInTheDocument();
    expect(calls.some((c) => c.url === '/api/csrf')).toBe(true);
    expect(calls.some((c) => c.url === '/api/auth/me')).toBe(true);
  });
  it('shows the fallback when there is no session', async () => {
    render(<QueryClientProvider client={qc()}><AuthProvider><Probe /><RequireAuth fallback={<div>FALLBACK</div>}><div>GUARDED</div></RequireAuth></AuthProvider></QueryClientProvider>);
    await screen.findByText('FALLBACK');
    expect(screen.queryByText('GUARDED')).toBeNull();
  });
  it('restores an MFA-verified admin from /admin/auth/me, but not one still at the TOTP step', async () => {
    route = (url) => {
      if (url === '/api/admin/auth/me') return json({ adminId: 7, email: 'admin@x.com', role: 'PLATFORM_ADMIN', mfaVerified: true });
      return defaultRoute(url);
    };
    const { unmount } = render(<QueryClientProvider client={qc()}><AuthProvider restorePath="/admin/auth/me"><Probe /><RequireAuth fallback={<div>FALLBACK</div>}><div>GUARDED</div></RequireAuth></AuthProvider></QueryClientProvider>);
    await screen.findByText(/USER:admin@x\.com/);
    expect(screen.getByText('GUARDED')).toBeInTheDocument();
    unmount();
    route = (url) => {
      if (url === '/api/admin/auth/me') return json({ adminId: 7, email: 'admin@x.com', role: 'PLATFORM_ADMIN', mfaVerified: false });
      return defaultRoute(url);
    };
    render(<QueryClientProvider client={qc()}><AuthProvider restorePath="/admin/auth/me"><Probe /><RequireAuth fallback={<div>FALLBACK</div>}><div>GUARDED</div></RequireAuth></AuthProvider></QueryClientProvider>);
    await screen.findByText('FALLBACK');
  });
});
