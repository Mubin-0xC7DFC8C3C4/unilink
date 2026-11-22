import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ApiError } from '../api/types';
import { apiGet, errorMessage, sessionExpiredRedirectPath, redirectOnSessionExpired, SESSION_EXPIRED_KEY } from '../api/client';
import { ApiUnavailable, EndpointUnavailable, ErrorState, SessionExpiredNotice } from '../design/primitives';
import { AuthProvider } from '../app/auth';
import Login from '../features/auth/Login';

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

function qc() { return new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } }); }

beforeEach(() => { sessionStorage.clear(); window.history.pushState({}, '', '/'); });
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); sessionStorage.clear(); });

describe('errorMessage status mapping', () => {
  it('401 says the session expired, not a generic sign-in error', () => {
    expect(errorMessage(new ApiError(401, { message: 'x' }))).toMatch(/session has expired/i);
    expect(errorMessage(new ApiError(401, { message: 'x' }))).toMatch(/log in again/i);
  });
  it('400 shows the server validation message, never a generic one when the server sent one', () => {
    expect(errorMessage(new ApiError(400, { code: 'INVALID_VISIBILITY', message: 'Visibility must be DEPARTMENT, UNIVERSITY, DEPARTMENT_HUB or NATIONAL' })))
      .toMatch(/Visibility must be/);
  });
  it('400 with no server message falls back to a validation hint, not "Request failed (400)"', () => {
    expect(errorMessage(new ApiError(400, {}))).not.toMatch(/Request failed/);
    expect(errorMessage(new ApiError(400, {}))).toMatch(/not accepted/i);
  });
  it('422 with only a generic message surfaces the first field error', () => {
    expect(errorMessage(new ApiError(422, { code: 'VALIDATION_FAILED', message: 'Validation failed', fieldErrors: [{ field: 'phone', message: 'must be a valid Bangladeshi mobile number' }] })))
      .toMatch(/phone: must be a valid Bangladeshi mobile number/);
  });
  it('404 is a genuine not-found — never "endpoint is not available yet"', () => {
    const m = errorMessage(new ApiError(404, { message: 'Not found' }));
    expect(m).toMatch(/not found/i);
    expect(m).not.toMatch(/not available yet/i);
    expect(m).not.toMatch(/endpoint/i);
  });
  it('403 is a forbidden message', () => {
    expect(errorMessage(new ApiError(403, { message: 'Access denied' }))).toMatch(/permission/i);
  });
  it('429 is a rate-limit message', () => {
    expect(errorMessage(new ApiError(429, { message: 'slow down' }))).toMatch(/Too many requests/i);
  });
  it('500 is generic with no fabricated data and no server internals', () => {
    const m = errorMessage(new ApiError(500, { code: 'INTERNAL_ERROR', message: 'Something went wrong' }));
    expect(m).toMatch(/Something went wrong on the server/);
    expect(m).not.toMatch(/NullPointer|Map\.of|stack/i);
  });
  it('network errors keep the connection message', () => {
    expect(errorMessage(new ApiError(0, { code: 'NETWORK_ERROR', message: 'Network error — the server could not be reached.' }))).toMatch(/could not be reached/i);
  });
});

describe('sessionExpiredRedirectPath (401 redirect, no loops)', () => {
  it('a 401 on a guarded student page goes to /login', () => {
    expect(sessionExpiredRedirectPath('/feed', '/feed')).toBe('/login');
    expect(sessionExpiredRedirectPath('/posts/1', '/posts/1')).toBe('/login');
  });
  it('a 401 on a guarded admin page goes to /admin/login', () => {
    expect(sessionExpiredRedirectPath('/admin/dashboard', '/admin/users')).toBe('/admin/login');
  });
  it('no redirect for the sign-in calls themselves (a failed login is wrong credentials, not an expired session)', () => {
    expect(sessionExpiredRedirectPath('/auth/login', '/login')).toBeNull();
    expect(sessionExpiredRedirectPath('/admin/auth/login', '/admin/login')).toBeNull();
    expect(sessionExpiredRedirectPath('/admin/auth/totp/verify', '/admin/totp')).toBeNull();
  });
  it('no redirect for session restore or CSRF priming (anonymous visitors must not bounce)', () => {
    expect(sessionExpiredRedirectPath('/auth/me', '/')).toBeNull();
    expect(sessionExpiredRedirectPath('/admin/auth/me', '/admin')).toBeNull();
    expect(sessionExpiredRedirectPath('/csrf', '/feed')).toBeNull();
  });
  it('no redirect when already on an auth page (no loop)', () => {
    expect(sessionExpiredRedirectPath('/feed', '/login')).toBeNull();
    expect(sessionExpiredRedirectPath('/admin/dashboard', '/admin/login')).toBeNull();
    expect(sessionExpiredRedirectPath('/admin/dashboard', '/admin/totp')).toBeNull();
    expect(sessionExpiredRedirectPath('/auth/verification/status', '/pending')).toBeNull();
  });
});

describe('api() 401 behaviour', () => {
  it('a 401 on a guarded call sets the session-expired flag and throws ApiError 401', async () => {
    window.history.pushState({}, '', '/feed');
    const fetchMock = vi.fn(async () => json({ message: 'unauthenticated' }, 401));
    vi.stubGlobal('fetch', fetchMock);
    await expect(apiGet('/feed')).rejects.toMatchObject({ status: 401 });
    expect(sessionStorage.getItem(SESSION_EXPIRED_KEY)).toBe('1');
  });
  it('a 401 on the login call sets no flag (no false "session expired" on a bad password)', async () => {
    window.history.pushState({}, '', '/login');
    const fetchMock = vi.fn(async () => json({ code: 'INVALID_CREDENTIALS', message: 'Invalid credentials' }, 401));
    vi.stubGlobal('fetch', fetchMock);
    await expect(apiGet('/auth/login')).rejects.toMatchObject({ status: 401 });
    expect(sessionStorage.getItem(SESSION_EXPIRED_KEY)).toBeNull();
  });
  it('a 400 throws with the server message attached', async () => {
    const fetchMock = vi.fn(async () => json({ code: 'INVALID_VISIBILITY', message: 'Visibility is required' }, 400));
    vi.stubGlobal('fetch', fetchMock);
    const err = await apiGet('/posts').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(errorMessage(err)).toMatch(/Visibility is required/);
  });
  it('a 500 throws; the rendered message is generic', async () => {
    const fetchMock = vi.fn(async () => json({ code: 'INTERNAL_ERROR', message: 'Something went wrong' }, 500));
    vi.stubGlobal('fetch', fetchMock);
    const err = await apiGet('/feed').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(errorMessage(err)).toBe('Something went wrong on the server. Please try again.');
  });
  it('redirectOnSessionExpired is a no-op for exempt paths', () => {
    window.history.pushState({}, '', '/feed');
    redirectOnSessionExpired('/auth/me');
    expect(sessionStorage.getItem(SESSION_EXPIRED_KEY)).toBeNull();
  });
});

describe('error states', () => {
  it('ApiUnavailable now renders a genuine not-found, not "not available yet"', () => {
    render(<ApiUnavailable endpoint="/posts/42" onRetry={() => {}} />);
    expect(screen.getByText('Not found')).toBeInTheDocument();
    expect(screen.queryByText(/not available yet/i)).toBeNull();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });
  it('EndpointUnavailable keeps the reserved wording for genuinely unimplemented endpoints', () => {
    render(<EndpointUnavailable endpoint="/admin/audit/export" />);
    expect(screen.getByText(/not available yet/i)).toBeInTheDocument();
  });
  it('ErrorState shows the message and a retry affordance only when one is given', async () => {
    const onRetry = vi.fn();
    const { rerender } = render(<ErrorState message="Something went wrong on the server. Please try again." onRetry={onRetry} />);
    expect(screen.getByText(/Something went wrong on the server/)).toBeInTheDocument();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(onRetry).toHaveBeenCalledTimes(1);
    rerender(<ErrorState message="Something went wrong on the server. Please try again." />);
    expect(screen.queryByRole('button', { name: 'Try again' })).toBeNull();
  });
  it('SessionExpiredNotice shows once when the flag is set, then clears it', () => {
    sessionStorage.setItem(SESSION_EXPIRED_KEY, '1');
    render(<SessionExpiredNotice />);
    expect(screen.getByText(/Your session has expired\. Please log in again\./)).toBeInTheDocument();
    expect(sessionStorage.getItem(SESSION_EXPIRED_KEY)).toBeNull();
  });
  it('SessionExpiredNotice renders nothing when no session expired', () => {
    const { container } = render(<SessionExpiredNotice />);
    expect(container).toBeEmptyDOMElement();
  });
});

describe('login no longer swallows its error', () => {
  function renderLogin() {
    return render(
      <QueryClientProvider client={qc()}><AuthProvider><MemoryRouter initialEntries={['/login']}>
        <Routes><Route path="/login" element={<Login />} /><Route path="/feed" element={<div>FEED</div>} /></Routes>
      </MemoryRouter></AuthProvider></QueryClientProvider>,
    );
  }
  it('a failed login surfaces the server error instead of failing silently', async () => {
    const fetchMock = vi.fn(async (input: unknown) => {
      const url = String(input);
      if (url === '/api/csrf' || url === '/api/auth/me') return json({ message: 'unauthenticated' }, 401);
      if (url === '/api/auth/login') return json({ code: 'INVALID_CREDENTIALS', message: 'Invalid credentials' }, 400);
      return json({}, 404);
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderLogin();
    await user.type(screen.getByLabelText('Email address'), 'a@b.edu');
    await user.type(screen.getByLabelText('Password'), 'WrongPassword1');
    await user.click(screen.getByRole('button', { name: 'Log in' }));
    await screen.findByText(/Invalid credentials/);
    expect(screen.queryByText('FEED')).toBeNull();
  });
  it('shows the session-expired message when redirected here by a 401', async () => {
    sessionStorage.setItem(SESSION_EXPIRED_KEY, '1');
    const fetchMock = vi.fn(async () => json({ message: 'unauthenticated' }, 401));
    vi.stubGlobal('fetch', fetchMock);
    renderLogin();
    await screen.findByText(/Your session has expired\. Please log in again\./);
    expect(sessionStorage.getItem(SESSION_EXPIRED_KEY)).toBeNull();
  });
  it('a 401 on login itself is reported as wrong credentials, not an expired session', async () => {
    const fetchMock = vi.fn(async (input: unknown) => {
      const url = String(input);
      if (url === '/api/csrf' || url === '/api/auth/me') return json({ message: 'unauthenticated' }, 401);
      if (url === '/api/auth/login') return json({ message: 'unauthenticated' }, 401);
      return json({}, 404);
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderLogin();
    await user.type(screen.getByLabelText('Email address'), 'a@b.edu');
    await user.type(screen.getByLabelText('Password'), 'WrongPassword1');
    await user.click(screen.getByRole('button', { name: 'Log in' }));
    await screen.findByText(/Incorrect email or password/);
    await waitFor(() => expect(sessionStorage.getItem(SESSION_EXPIRED_KEY)).toBeNull());
  });
});
