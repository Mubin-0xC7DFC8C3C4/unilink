import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '../app/auth';
import { ThemeProvider } from '../app/ThemeContext';
import Login from '../features/auth/Login';

/** Phase 2.1 (owner instruction 2026-11-20): the rebuilt Student Login page —
 *  brand block, show/hide password control, validation, auth links, and the
 *  existing /auth/login contract (401 names the actual cause). */

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

interface Call { url: string; method: string; body: unknown }
let calls: Call[] = [];
let route: (url: string, method: string, body: unknown) => Response | undefined;

const fetchMock = vi.fn();
function defaultRoute(url: string): Response | undefined {
  if (url === '/api/csrf') return json({});
  if (url === '/api/auth/me') return json({ message: 'unauthenticated' }, 401);
  return undefined;
}
function qc() { return new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } }); }
function renderLogin() {
  return render(<QueryClientProvider client={qc()}><ThemeProvider><AuthProvider><MemoryRouter initialEntries={['/login']}><Routes><Route path="/login" element={<Login />} /><Route path="/feed" element={<div>FEED</div>} /></Routes></MemoryRouter></AuthProvider></ThemeProvider></QueryClientProvider>);
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

describe('Student Login page (Phase 2.1)', () => {
  it('renders the UniLink brand, both fields and the auth links', () => {
    renderLogin();
    expect(screen.getByText('UniLink')).toBeInTheDocument();
    expect(screen.getByLabelText('Email address')).toBeInTheDocument();
    expect(screen.getByLabelText('Password')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Forgot password?' })).toHaveAttribute('href', '/forgot-password');
    // Owner polish (2026-11-20): the sign-up line now reads "Don't have an
    // account? Sign up" — the link itself is named "Sign up".
    const line = screen.getByText(/Don't have an account\?/).closest('p') as HTMLElement;
    expect(within(line).getByRole('link', { name: 'Sign up' })).toHaveAttribute('href', '/signup');
  });

  it('show/hide toggles the password field type', async () => {
    const user = userEvent.setup();
    renderLogin();
    const password = screen.getByLabelText('Password') as HTMLInputElement;
    expect(password.type).toBe('password');
    await user.click(screen.getByRole('button', { name: 'Show password' }));
    expect(password.type).toBe('text');
    await user.click(screen.getByRole('button', { name: 'Hide password' }));
    expect(password.type).toBe('password');
  });

  it('blocks an empty submit with validation errors and never calls the API', async () => {
    const user = userEvent.setup();
    renderLogin();
    await user.click(screen.getByRole('button', { name: 'Log in' }));
    // Empty fields get the brief's "please enter" messages (an empty
    // email is missing, not malformed).
    expect(await screen.findByText(/Please enter your email address\./)).toBeInTheDocument();
    expect(await screen.findByText(/Please enter your password\./)).toBeInTheDocument();
    expect(calls.some((c) => c.url === '/api/auth/login')).toBe(false);
  });

  it('shows the credential error on a 401 and stays on the page', async () => {
    route = (url, method) => {
      if (url === '/api/auth/login' && method === 'POST') return json({ message: 'unauthenticated' }, 401);
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderLogin();
    await user.type(screen.getByLabelText('Email address'), 't@x.com');
    await user.type(screen.getByLabelText('Password'), 'WrongPass1');
    await user.click(screen.getByRole('button', { name: 'Log in' }));
    expect(await screen.findByText(/Incorrect email or password/)).toBeInTheDocument();
    expect(screen.queryByText('FEED')).not.toBeInTheDocument();
  });
});
