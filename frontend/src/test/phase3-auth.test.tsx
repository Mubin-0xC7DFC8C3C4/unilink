import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '../app/auth';
import { ThemeProvider } from '../app/ThemeContext';
import { ApiError } from '../api/types';
import { errorMessage } from '../api/client';
import Login from '../features/auth/Login';
import SettingsPage from '../features/settings/SettingsPage';
import Totp from '../admin/pages/Totp';
import AdminChangePassword from '../admin/pages/AdminChangePassword';

/** Phase 3 Worker A (D-093/D-094/D-098): must-change-password redirects and
 *  the change-password screens, asserted against the mocked fetch layer. */

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
  return render(<QueryClientProvider client={qc()}><ThemeProvider><AuthProvider><MemoryRouter initialEntries={initialEntries}>{ui}</MemoryRouter></AuthProvider></ThemeProvider></QueryClientProvider>);
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

describe('Student login with mustChangePassword (D-094/D-098)', () => {
  it('redirects to /settings instead of /feed when the flag is set', async () => {
    route = (url, method) => {
      if (url === '/api/auth/login' && method === 'POST')
        return json({ id: 5, fullName: 'Test User', email: 't@x.com', state: 'VERIFIED', mustChangePassword: true });
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithProviders(<Routes><Route path="/login" element={<Login />} /><Route path="/feed" element={<div>FEED</div>} /><Route path="/settings" element={<div>SETTINGS</div>} /></Routes>, ['/login']);
    await user.type(screen.getByLabelText('Email'), 't@x.com');
    await user.type(screen.getByLabelText('Password'), 'TempPass123');
    await user.click(screen.getByRole('button', { name: 'Log in' }));
    await screen.findByText('SETTINGS');
    expect(screen.queryByText('FEED')).not.toBeInTheDocument();
  });

  it('goes to /feed when the flag is absent', async () => {
    route = (url, method) => {
      if (url === '/api/auth/login' && method === 'POST')
        return json({ id: 5, fullName: 'Test User', email: 't@x.com', state: 'VERIFIED', mustChangePassword: false });
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithProviders(<Routes><Route path="/login" element={<Login />} /><Route path="/feed" element={<div>FEED</div>} /><Route path="/settings" element={<div>SETTINGS</div>} /></Routes>, ['/login']);
    await user.type(screen.getByLabelText('Email'), 't@x.com');
    await user.type(screen.getByLabelText('Password'), 'Student123');
    await user.click(screen.getByRole('button', { name: 'Log in' }));
    await screen.findByText('FEED');
  });
});

describe('Student change-password screen (D-093/D-098)', () => {
  it('shows the must-change banner and posts old+new to /auth/change-password', async () => {
    route = (url, method) => {
      if (url === '/api/auth/me') return json({ id: 5, fullName: 'Test User', email: 't@x.com', state: 'VERIFIED', mustChangePassword: true });
      if (url === '/api/auth/change-password' && method === 'POST') return json({ status: 'Password changed. All sessions were invalidated; please log in again.' });
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithProviders(<Routes><Route path="/settings" element={<SettingsPage />} /><Route path="/login" element={<div>LOGIN PAGE</div>} /></Routes>, ['/settings']);
    await screen.findByText(/You must change your password before continuing/);
    await user.type(screen.getByLabelText('Current password'), 'TempPass123');
    await user.type(screen.getByLabelText('New password'), 'NewStudent1');
    await user.click(screen.getByRole('button', { name: 'Change password' }));
    await screen.findByText('LOGIN PAGE');
    const call = calls.find((c) => c.url === '/api/auth/change-password');
    expect(call).toBeDefined();
    expect(jsonBody(call!)).toEqual({ oldPassword: 'TempPass123', newPassword: 'NewStudent1' });
  });
});

describe('Admin TOTP verify with mustChangePassword (D-094/D-098)', () => {
  it('redirects to /admin/change-password instead of the dashboard', async () => {
    route = (url, method) => {
      if (url === '/api/admin/auth/totp/verify' && method === 'POST')
        return json({ adminId: 7, email: 'admin@x.com', role: 'PLATFORM_ADMIN', mfaVerified: true, mustChangePassword: true });
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithProviders(<Routes><Route path="/admin/totp" element={<Totp />} /><Route path="/admin/change-password" element={<div>CHANGE PASSWORD</div>} /><Route path="/admin" element={<div>ADMIN HOME</div>} /></Routes>,
      [{ pathname: '/admin/totp', state: { stage: 'NEEDS_TOTP' } }]);
    await user.type(screen.getByLabelText('TOTP or recovery code'), '123456');
    await user.click(screen.getByRole('button', { name: 'Verify and enter admin' }));
    await screen.findByText('CHANGE PASSWORD');
    expect(screen.queryByText('ADMIN HOME')).not.toBeInTheDocument();
  });
});

describe('Admin change-password screen (D-093)', () => {
  it('posts old+new to /admin/auth/change-password and returns to admin login', async () => {
    route = (url, method) => {
      if (url === '/api/admin/auth/change-password' && method === 'POST') return json({ status: 'Password changed. All sessions were invalidated; please log in again.' });
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithProviders(<Routes><Route path="/admin/change-password" element={<AdminChangePassword />} /><Route path="/admin/login" element={<div>ADMIN LOGIN</div>} /></Routes>, ['/admin/change-password']);
    await user.type(screen.getByLabelText('Current password'), 'TempPass123456');
    await user.type(screen.getByLabelText(/New password/), 'NewAdminPass123');
    await user.click(screen.getByRole('button', { name: 'Change password' }));
    await screen.findByText('ADMIN LOGIN');
    const call = calls.find((c) => c.url === '/api/admin/auth/change-password');
    expect(call).toBeDefined();
    expect(jsonBody(call!)).toEqual({ oldPassword: 'TempPass123456', newPassword: 'NewAdminPass123' });
  });
});

describe('PASSWORD_CHANGE_REQUIRED error text (D-094)', () => {
  it('surfaces the server message instead of the generic 403 text', () => {
    expect(errorMessage(new ApiError(403, { code: 'PASSWORD_CHANGE_REQUIRED', message: 'You must change your password before using UniLink.' })))
      .toBe('You must change your password before using UniLink.');
    expect(errorMessage(new ApiError(403, { code: 'FORBIDDEN', message: 'Access denied' })))
      .toBe('You do not have permission to do that.');
  });
});
