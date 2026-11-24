import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '../app/auth';
import { ThemeProvider } from '../app/ThemeContext';
import { Layout } from '../app/Layout';

/** Owner decision (2026-11-20): the signed-out auth pages (login, sign up,
 *  forgot password, OTP, pending) show a minimal header — brand plus
 *  Log in / Sign up only. App navigation, Notifications and the theme
 *  toggle appear on the app's other routes. */

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const fetchMock = vi.fn();
function qc() { return new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } }); }
function renderAt(path: string) {
  return render(<QueryClientProvider client={qc()}><ThemeProvider><AuthProvider><MemoryRouter initialEntries={[path]}><Routes><Route element={<Layout />}><Route path="/" element={<div>LANDING PAGE</div>} /><Route path="/login" element={<div>LOGIN PAGE</div>} /><Route path="/feed" element={<div>FEED PAGE</div>} /></Route></Routes></MemoryRouter></AuthProvider></ThemeProvider></QueryClientProvider>);
}

beforeEach(() => {
  sessionStorage.clear();
  fetchMock.mockImplementation(async (input: unknown) => {
    const url = String(input);
    if (url === '/api/csrf') return json({});
    if (url === '/api/auth/me') return json({ message: 'unauthenticated' }, 401);
    return json({ message: 'no mock route for ' + url }, 404);
  });
  vi.stubGlobal('fetch', fetchMock);
});
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); });

describe('Layout header (owner decision 2026-11-20)', () => {
  it('shows only brand + Log in / Sign up on the login route', () => {
    renderAt('/login');
    expect(screen.getByRole('link', { name: 'UniLink' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Log in' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Sign up' })).toBeInTheDocument();
    for (const name of ['Feed', 'Events', 'Teams', 'Messages', 'Search', 'Notifications', 'Workspace', 'Settings']) {
      expect(screen.queryByRole('link', { name })).toBeNull();
    }
    expect(screen.queryByPlaceholderText('Search people, posts, projects')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Toggle sidebar' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Account menu' })).toBeNull();
  });

  it('matches the reference header on app routes: logo first (hamburger is mobile-only), centred search, icon-only right side', () => {
    renderAt('/feed');
    // The hamburger opens the mobile drawer only: it is display:none at
    // desktop widths (absent from the accessibility tree here), and the
    // desktop sidebar toggle lives inside the Community sidebar itself.
    expect(screen.queryByRole('button', { name: 'Toggle sidebar' })).toBeNull();
    expect(document.querySelector('.hdr-toggle')).not.toBeNull();
    expect(screen.getByRole('link', { name: 'UniLink' })).toBeInTheDocument();
    // Centred global search bar with the reference placeholder.
    expect(screen.getByPlaceholderText('Search people, posts, projects')).toBeInTheDocument();
    // Icons only on the right: workspace grid (a link), notifications
    // bell and settings gear (buttons — the bell opens the notifications
    // popover and the gear opens the Settings modal; neither navigates
    // to a page since 2026-11-21).
    expect(screen.getByRole('link', { name: 'Workspace' })).toBeInTheDocument();
    for (const name of ['Notifications', 'Settings']) {
      expect(screen.getByRole('button', { name })).toBeInTheDocument();
    }
    // No text navigation links in the top bar (Feed/Events/Teams/Messages are gone).
    for (const name of ['Feed', 'Events', 'Teams', 'Messages', 'Profile', 'Sign out']) {
      expect(screen.queryByRole('link', { name })).toBeNull();
    }
    expect(screen.queryByRole('button', { name: 'Toggle theme' })).toBeNull();
    // Anonymous here, so Log in / Sign up show instead of the account avatar.
    expect(screen.getByRole('link', { name: 'Log in' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Account menu' })).toBeNull();
  });

  it('shows only brand + Log in / Sign up on the landing page while logged out (2026-11-24)', () => {
    renderAt('/');
    expect(screen.getByRole('link', { name: 'UniLink' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Log in' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Sign up' })).toBeInTheDocument();
    expect(screen.queryByPlaceholderText('Search people, posts, projects')).toBeNull();
    expect(screen.queryByRole('link', { name: 'Workspace' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Notifications' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Settings' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Account menu' })).toBeNull();
  });

  it('keeps the full header on the landing page while logged in (2026-11-24)', async () => {
    fetchMock.mockImplementation(async (input: unknown) => {
      const url = String(input);
      if (url === '/api/csrf') return json({});
      if (url === '/api/auth/me') return json({ id: 5, fullName: 'Ayesha Rahman', email: 'ayesha@gmail.com', state: 'VERIFIED' });
      if (url === '/api/notifications') return json([]);
      return json({ message: 'no mock route for ' + url }, 404);
    });
    renderAt('/');
    expect(await screen.findByPlaceholderText('Search people, posts, projects')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Workspace' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Notifications' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Settings' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Account menu' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Log in' })).toBeNull();
    expect(screen.queryByRole('link', { name: 'Sign up' })).toBeNull();
  });

  it('the bell opens the notifications popover and the gear opens the Settings modal (no page navigation)', async () => {
    fetchMock.mockImplementation(async (input: unknown) => {
      const url = String(input);
      if (url === '/api/csrf') return json({});
      if (url === '/api/auth/me') return json({ id: 5, fullName: 'Ayesha Rahman', email: 'ayesha@gmail.com', state: 'VERIFIED' });
      if (url === '/api/notifications') return json([{ id: 1, title: 'Your student verification was approved', body: 'Welcome to UniLink.', createdAt: '2026-11-18T09:00:00', readFlag: false }]);
      return json({ message: 'no mock route for ' + url }, 404);
    });
    const user = userEvent.setup();
    renderAt('/feed');
    await user.click(screen.getByRole('button', { name: 'Notifications' }));
    const pop = await screen.findByRole('dialog', { name: 'Notifications' });
    expect(pop).toBeInTheDocument();
    expect(screen.getByText('Unread notices (1)')).toBeInTheDocument();
    expect(screen.getByText('Your student verification was approved')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Mark all as read' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'All notifications' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Settings' }));
    const dialog = await screen.findByRole('dialog', { name: 'Settings' });
    expect(dialog).toBeInTheDocument();
    for (const tab of ['Edit Profile', 'Appearance', 'Behavior', 'Notifications', 'Privacy']) {
      expect(within(dialog).getByRole('button', { name: tab })).toBeInTheDocument();
    }
  });
});
