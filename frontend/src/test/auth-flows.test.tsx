import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
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
let route: (url: string, method: string, body: unknown) => Response | undefined;

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
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); });

function jsonBody(call: Call): Record<string, unknown> {
  return JSON.parse(String(call.body)) as Record<string, unknown>;
}

describe('SignUp (I-6)', () => {
  function catalogRoute(url: string, method: string): Response | undefined {
    if (url === '/api/public/universities') return json([{ id: 1, name: 'Metropolitan University' }, { id: 2, name: 'University of Dhaka' }]);
    if (url.startsWith('/api/public/departments')) return json([{ id: 10, universityId: 1, name: 'CSE' }]);
    if (url.startsWith('/api/public/batches')) return json([{ id: 100, universityId: 1, departmentId: 10, label: '45' }]);
    if (url === '/api/auth/register' && method === 'POST') return json({ id: 42, fullName: 'Test User', state: 'REGISTERED' }, 201);
    return defaultRoute(url);
  }
  it('loads the catalog and submits universityId/departmentId/batchId + idCard', async () => {
    route = catalogRoute;
    const user = userEvent.setup();
    renderWithProviders(<Routes><Route path="/signup" element={<SignUp />} /><Route path="/otp" element={<div>OTP PAGE</div>} /></Routes>, ['/signup']);
    await user.type(screen.getByLabelText('Full name'), 'Test User');
    await user.type(screen.getByLabelText('Phone'), '01712345678');
    await user.type(screen.getByLabelText('Email'), 'test@example.com');
    await user.click(screen.getByRole('button', { name: /Continue/ }));
    await user.selectOptions(screen.getByLabelText('University'), '1');
    expect(calls.some((c) => c.url === '/api/public/departments?universityId=1')).toBe(true);
    await screen.findByText('CSE');
    await user.selectOptions(screen.getByLabelText('Department'), '10');
    await screen.findByText('45');
    await user.selectOptions(screen.getByLabelText('Batch'), '100');
    await user.type(screen.getByLabelText('Address'), '123 Test Road, Dhaka');
    await user.click(screen.getByRole('button', { name: /Continue/ }));
    await user.type(screen.getByLabelText('Student ID number'), 'CSE-001');
    await user.type(screen.getByLabelText('Password'), 'Password1');
    await user.upload(screen.getByLabelText('Student ID card image'), new File(['id'], 'id.jpg', { type: 'image/jpeg' }));
    await user.upload(screen.getByLabelText('Profile photo'), new File(['photo'], 'photo.jpg', { type: 'image/jpeg' }));
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    await screen.findByText('OTP PAGE');
    const reg = calls.find((c) => c.url === '/api/auth/register');
    expect(reg?.method).toBe('POST');
    const fd = reg?.body as FormData;
    expect(fd.get('universityId')).toBe('1');
    expect(fd.get('departmentId')).toBe('10');
    expect(fd.get('batchId')).toBe('100');
    expect(fd.get('phone')).toBe('01712345678');
    expect((fd.get('idCard') as File).name).toBe('id.jpg');
    expect(fd.get('idCardImage')).toBeNull();
    expect(sessionStorage.getItem('unilink:otpUserId')).toBe('42');
  });
  it('rejects a non-Bangladeshi phone number before any register call', async () => {
    route = catalogRoute;
    const user = userEvent.setup();
    renderWithProviders(<Routes><Route path="/signup" element={<SignUp />} /></Routes>, ['/signup']);
    await user.type(screen.getByLabelText('Full name'), 'Test User');
    await user.type(screen.getByLabelText('Phone'), '01234567890');
    await user.type(screen.getByLabelText('Email'), 'test@example.com');
    await user.click(screen.getByRole('button', { name: /Continue/ }));
    await screen.findByText('Enter a valid Bangladeshi mobile number, e.g. 01712345678');
    expect(calls.some((c) => c.url === '/api/auth/register')).toBe(false);
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
});

describe('ForgotPassword (I-5)', () => {
  it('uses request then confirm — no separate verify step', async () => {
    route = (url, method) => {
      if (url === '/api/auth/password-reset/request' && method === 'POST') return json({ status: 'If the email exists, a reset code was sent' });
      if (url === '/api/auth/password-reset/confirm' && method === 'POST') return json({ status: 'Password changed. Please log in again.' });
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithProviders(<Routes><Route path="/forgot-password" element={<ForgotPassword />} /></Routes>, ['/forgot-password']);
    await user.type(screen.getByLabelText('Email'), 'test@example.com');
    await user.click(screen.getByRole('button', { name: 'Send reset code' }));
    await screen.findByLabelText('Reset code');
    await user.type(screen.getByLabelText('Reset code'), '123456');
    await user.type(screen.getByLabelText(/New password/), 'short');
    await user.click(screen.getByRole('button', { name: 'Reset password' }));
    await screen.findByText(/at least 8 characters/);
    expect(calls.some((c) => c.url === '/api/auth/password-reset/confirm')).toBe(false);
    await user.clear(screen.getByLabelText(/New password/));
    await user.type(screen.getByLabelText(/New password/), 'Password1');
    await user.click(screen.getByRole('button', { name: 'Reset password' }));
    await screen.findByText(/Your password has been reset/);
    const req = calls.find((c) => c.url === '/api/auth/password-reset/request');
    expect(jsonBody(req!)).toEqual({ email: 'test@example.com' });
    const confirm = calls.find((c) => c.url === '/api/auth/password-reset/confirm');
    expect(jsonBody(confirm!)).toEqual({ email: 'test@example.com', otp: '123456', newPassword: 'Password1' });
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
    await user.type(screen.getByLabelText('Email'), 'admin@x.com');
    await user.type(screen.getByLabelText('Password'), 'AdminPassword123');
    await user.click(screen.getByRole('button', { name: 'Log in' }));
    await screen.findByText('TOTP verification');
    expect(screen.getByText(/otpauth:\/\/totp\/UniLink:admin/)).toBeInTheDocument();
    const login = calls.find((c) => c.url === '/api/admin/auth/login');
    expect(jsonBody(login!)).toEqual({ email: 'admin@x.com', password: 'AdminPassword123' });
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
