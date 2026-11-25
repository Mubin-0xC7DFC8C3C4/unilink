import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import NetworkPage from '../features/network/NetworkPage';
import WorkspacePage from '../features/workspace/WorkspacePage';
import ProfilePage from '../features/profile/ProfilePage';
import { Routes, Route } from 'react-router-dom';
import { AuthProvider } from '../app/auth';
import { within } from '@testing-library/react';

/**
 * The three views that replaced the Phase 3 placeholders (owner
 * instruction 2026-11-21): My Network (real team-join request inbox —
 * UniLink has no follow-request model — plus the API's follower
 * counts) and the Workspace GitHub connection view.
 * Same fetch-router harness as community-home.test.tsx.
 */
type Route = (url: string, method?: string) => { status: number; body: unknown } | undefined;
let route: Route;
let calls: { url: string; method: string; body?: string }[] = [];
const json = (body: unknown, status = 200) => ({ status, body });
const defaultRoute = (url: string) => (url === '/api/csrf' ? json({}) : undefined);
function jsonBody(call: { body?: string }) { return call.body ? JSON.parse(call.body) : undefined; }

beforeEach(() => {
  calls = [];
  route = defaultRoute;
  vi.stubGlobal('fetch', async (input: any, init?: any) => {
    const url = String(input).replace(/^.*(?=\/api\/)/, '');
    const method = init?.method ?? 'GET';
    calls.push({ url, method, body: typeof init?.body === 'string' ? init.body : undefined });
    const r = route(url, method) ?? { status: 404, body: { message: 'not mocked' } };
    return new Response(JSON.stringify(r.body), { status: r.status, headers: { 'Content-Type': 'application/json' } });
  });
});
afterEach(() => { vi.unstubAllGlobals(); });

function renderPage(el: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={qc}><MemoryRouter>{el}</MemoryRouter></QueryClientProvider>);
}

describe('My Network', () => {
  it('shows team join requests for my teams and decides them through the real contract', async () => {
    route = (url, method) => {
      if (url === '/api/profiles/me') return json({ userId: 5, fullName: 'Ayesha Rahman', followers: 96, following: 128 });
      if (url === '/api/teams') return json([{ id: 1, name: 'Bus Tracker Team', creatorId: 5 }]);
      if (url === '/api/teams/1/requests') return json([{ id: 9, teamId: 1, userId: 7, status: 'PENDING', message: 'I can help with maps.' }]);
      if (url === '/api/profiles/7') return json({ fullName: 'Sadia Islam', departmentName: 'CSE', batchLabel: 'Batch 28' });
      if (url === '/api/teams/requests/9/decide' && method === 'POST') return json({ status: 'ok' });
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderPage(<NetworkPage />);
    expect(await screen.findByText('Sadia Islam')).toBeInTheDocument();
    expect(screen.getByText(/wants to join Bus Tracker Team/)).toBeInTheDocument();
    expect(screen.getAllByText('96').length).toBeGreaterThan(0);
    await user.click(screen.getByRole('button', { name: 'Accept' }));
    await waitFor(() => expect(calls.some((c) => c.url === '/api/teams/requests/9/decide')).toBe(true));
    expect(jsonBody(calls.find((c) => c.url === '/api/teams/requests/9/decide')!)).toEqual({ accept: true });
  }, 30_000);
});

describe('Workspace', () => {
  it('shows the connect card when disconnected and requests the OAuth URL on Authorize', async () => {
    route = (url) => {
      if (url === '/api/workspace/github/status') return json({ configured: true, connected: false, username: null, message: 'Connect your GitHub account to browse your public repositories inside UniLink.' });
      if (url === '/api/workspace/github/authorize') return json({ url: 'https://github.com/login/oauth/authorize?client_id=test' });
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderPage(<WorkspacePage />);
    expect(await screen.findByText('GitHub Repository Integration')).toBeInTheDocument();
    expect(screen.getByText('Not connected')).toBeInTheDocument();
    expect(screen.getByText(/Read-only access to your/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Overview' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Authorize GitHub' }));
    await waitFor(() => expect(calls.some((c) => c.url === '/api/workspace/github/authorize')).toBe(true));
  });

  it('explains the configuration state instead of simulating a connection when GitHub is not configured', async () => {
    route = (url) => {
      if (url === '/api/workspace/github/status') return json({ configured: false, connected: false, username: null, message: 'GitHub integration is not configured on this server yet.' });
      return defaultRoute(url);
    };
    renderPage(<WorkspacePage />);
    expect(await screen.findByText('GitHub integration is not configured on this server yet.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Authorize GitHub' })).not.toBeInTheDocument();
  });

  it('lists real repositories when connected and opens the live viewer', async () => {
    route = (url) => {
      if (url === '/api/workspace/github/status') return json({ configured: true, connected: true, username: 'ayesha-dev', message: '' });
      if (url === '/api/workspace/github/repos') return json([{ owner: 'ayesha-dev', name: 'campus-bus-tracker', fullName: 'ayesha-dev/campus-bus-tracker', description: 'Real-time campus bus tracking.', language: 'TypeScript', stars: 48, forks: 9 }]);
      if (url === '/api/workspace/github/repos/ayesha-dev/campus-bus-tracker') return json({ repo: { owner: 'ayesha-dev', name: 'campus-bus-tracker', fullName: 'ayesha-dev/campus-bus-tracker', description: 'Real-time campus bus tracking.', language: 'TypeScript', stars: 48, forks: 9, htmlUrl: 'https://github.com/ayesha-dev/campus-bus-tracker' }, readme: '# Campus Bus Tracker', myUsername: 'ayesha-dev' });
      if (url.startsWith('/api/workspace/github/repos/ayesha-dev/campus-bus-tracker/contents')) return json([{ name: 'package.json', path: 'package.json', type: 'file', size: 512 }, { name: 'src', path: 'src', type: 'dir', size: null }]);
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderPage(<WorkspacePage />);
    expect(await screen.findByText(/Connected as @ayesha-dev/)).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: /campus-bus-tracker/ })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'View details' }));
    expect(await screen.findByText(/package\.json/)).toBeInTheDocument();
    // D-203: the README is rendered as Markdown, not raw text.
    expect(await screen.findByRole('heading', { name: 'Campus Bus Tracker' })).toBeInTheDocument();
    expect(screen.getByText(/Default branch: main/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '← Back to Workspace' }));
    expect(await screen.findByText('Your GitHub Workspace')).toBeInTheDocument();
  });

  it('pins a repository to an existing project row (D-203)', async () => {
    let pinned = false;
    route = (url, method) => {
      if (url === '/api/workspace/github/status') return json({ configured: true, connected: true, username: 'ayesha-dev', message: '' });
      if (url === '/api/workspace/github/repos') return json([{ owner: 'ayesha-dev', name: 'campus-bus-tracker', fullName: 'ayesha-dev/campus-bus-tracker', description: 'Real-time campus bus tracking.', language: 'TypeScript', stars: 48, forks: 9 }]);
      if (url === '/api/profiles/me/projects') return json(pinned
        ? [{ id: 3, title: 'Bus Tracker App', description: 'Real-time campus bus tracking.', githubOwner: 'ayesha-dev', githubRepo: 'campus-bus-tracker' }]
        : [{ id: 3, title: 'Bus Tracker App', description: 'Real-time campus bus tracking.', githubOwner: null, githubRepo: null }]);
      if (url === '/api/profiles/me/projects/3/pin' && method === 'POST') { pinned = true; return json({ id: 3, title: 'Bus Tracker App', githubOwner: 'ayesha-dev', githubRepo: 'campus-bus-tracker' }); }
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderPage(<WorkspacePage />);
    await user.click(await screen.findByRole('button', { name: 'Pin to Profile' }));
    expect(await screen.findByText(/Pin ayesha-dev \/ campus-bus-tracker to your profile/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Bus Tracker App' }));
    await waitFor(() => expect(calls.some((c) => c.url === '/api/profiles/me/projects/3/pin' && c.method === 'POST')).toBe(true));
    expect(jsonBody(calls.find((c) => c.url === '/api/profiles/me/projects/3/pin')!)).toEqual({ owner: 'ayesha-dev', repo: 'campus-bus-tracker' });
    expect(await screen.findByText('Pinned to profile')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Pin to Profile' })).not.toBeInTheDocument();
  });

  it('pins a repository by creating a new project row (D-203)', async () => {
    route = (url, method) => {
      if (url === '/api/workspace/github/status') return json({ configured: true, connected: true, username: 'ayesha-dev', message: '' });
      if (url === '/api/workspace/github/repos') return json([{ owner: 'ayesha-dev', name: 'campus-bus-tracker', fullName: 'ayesha-dev/campus-bus-tracker', description: 'Real-time campus bus tracking.', language: 'TypeScript', stars: 48, forks: 9 }]);
      if (url === '/api/profiles/me/projects' && method === 'POST') return json({ id: 9, title: 'campus-bus-tracker', githubOwner: null, githubRepo: null });
      if (url === '/api/profiles/me/projects') return json([]);
      if (url === '/api/profiles/me/projects/9/pin' && method === 'POST') return json({ id: 9, title: 'campus-bus-tracker', githubOwner: 'ayesha-dev', githubRepo: 'campus-bus-tracker' });
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderPage(<WorkspacePage />);
    await user.click(await screen.findByRole('button', { name: 'Pin to Profile' }));
    await user.click(await screen.findByRole('button', { name: 'New project “campus-bus-tracker”' }));
    await waitFor(() => expect(calls.some((c) => c.url === '/api/profiles/me/projects' && c.method === 'POST')).toBe(true));
    expect(jsonBody(calls.find((c) => c.url === '/api/profiles/me/projects' && c.method === 'POST')!)).toEqual({ title: 'campus-bus-tracker', description: 'Real-time campus bus tracking.', url: 'https://github.com/ayesha-dev/campus-bus-tracker' });
    await waitFor(() => expect(calls.some((c) => c.url === '/api/profiles/me/projects/9/pin' && c.method === 'POST')).toBe(true));
  });

  it('links out to GitHub to create a project (D-203)', async () => {
    route = (url) => {
      if (url === '/api/workspace/github/status') return json({ configured: true, connected: true, username: 'ayesha-dev', message: '' });
      if (url === '/api/workspace/github/repos') return json([]);
      if (url === '/api/profiles/me/projects') return json([]);
      return defaultRoute(url);
    };
    renderPage(<WorkspacePage />);
    const link = await screen.findByRole('link', { name: /Create Project on GitHub/ });
    expect(link.getAttribute('href')).toBe('https://github.com/new');
    expect(link.getAttribute('target')).toBe('_blank');
  });
});

describe('Student Profile — sign out (D-202)', () => {
  it('shows no Pinned Projects section and signs out through the confirmation popup', async () => {
    route = (url, method) => {
      if (url === '/api/auth/me') return json({ id: 5, fullName: 'Ayesha Rahman', state: 'VERIFIED' });
      if (url === '/api/auth/logout' && method === 'POST') return json({});
      if (url === '/api/profiles/me') return json({ userId: 5, fullName: 'Ayesha Rahman', state: 'VERIFIED', departmentName: 'CSE', universityName: 'Metropolitan University', followers: 96, following: 128, skills: [] });
      if (url === '/api/profiles/me/activity-items') return json([]);
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(<QueryClientProvider client={qc}><AuthProvider><MemoryRouter initialEntries={['/profile/me']}><Routes>
      <Route path="/profile/:id" element={<ProfilePage />} />
      <Route path="/login" element={<div>LOGIN PAGE</div>} />
    </Routes></MemoryRouter></AuthProvider></QueryClientProvider>);
    // D-202: the Education / Achievements sections were removed from
    // the profile page (the portfolio manager in Settings stays) — the
    // page must not fetch those lists or render them. D-203 re-adds
    // Pinned Projects, but ONLY when a project row is pinned to a real
    // GitHub repository; with nothing pinned the section stays hidden
    // (and the page reads /profiles/me/projects, never /profiles/5).
    await screen.findByText('Ayesha Rahman');
    expect(screen.queryByText('Pinned Projects')).toBeNull();
    expect(screen.queryByText('Education')).toBeNull();
    expect(screen.queryByText('Achievements')).toBeNull();
    expect(calls.some((c) => c.url === '/api/profiles/5/projects')).toBe(false);
    expect(calls.some((c) => c.url === '/api/profiles/5/education')).toBe(false);
    expect(calls.some((c) => c.url === '/api/profiles/5/achievements')).toBe(false);
    // Sign out: section button -> confirmation popup -> /login.
    await user.click(screen.getByRole('button', { name: 'Sign out' }));
    const confirm = await screen.findByRole('dialog', { name: 'Sign out of UniLink?' });
    expect(within(confirm).getByText(/Are you sure you want to sign out/)).toBeInTheDocument();
    await user.click(within(confirm).getByRole('button', { name: 'Sign out' }));
    expect(await screen.findByText('LOGIN PAGE')).toBeInTheDocument();
    expect(calls.some((c) => c.url === '/api/auth/logout' && c.method === 'POST')).toBe(true);
  });

  it('shows Pinned Projects for GitHub-pinned rows and opens the live viewer (D-203)', async () => {
    route = (url) => {
      if (url === '/api/auth/me') return json({ id: 5, fullName: 'Ayesha Rahman', state: 'VERIFIED' });
      if (url === '/api/profiles/me') return json({ userId: 5, fullName: 'Ayesha Rahman', state: 'VERIFIED', departmentName: 'CSE', universityName: 'Metropolitan University', followers: 96, following: 128, skills: [] });
      if (url === '/api/profiles/me/activity-items') return json([]);
      if (url === '/api/profiles/me/projects') return json([
        { id: 7, title: 'Bus Tracker', description: 'Real-time campus bus tracking.', githubOwner: 'ayesha-dev', githubRepo: 'campus-bus-tracker' },
        { id: 8, title: 'Draft notes', description: 'Not pinned yet.', githubOwner: null, githubRepo: null },
      ]);
      if (url === '/api/workspace/github/repos/ayesha-dev/campus-bus-tracker') return json({ repo: { owner: 'ayesha-dev', name: 'campus-bus-tracker', fullName: 'ayesha-dev/campus-bus-tracker', description: 'Real-time campus bus tracking.', language: 'TypeScript', stars: 48, forks: 9, htmlUrl: 'https://github.com/ayesha-dev/campus-bus-tracker', defaultBranch: 'develop' }, readme: '# Campus Bus Tracker', myUsername: 'ayesha-dev' });
      if (url.startsWith('/api/workspace/github/repos/ayesha-dev/campus-bus-tracker/contents')) return json([{ name: 'package.json', path: 'package.json', type: 'file', size: 512 }]);
      if (url.startsWith('/api/workspace/discussions')) return json([]);
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(<QueryClientProvider client={qc}><AuthProvider><MemoryRouter initialEntries={['/profile/me']}><Routes>
      <Route path="/profile/:id" element={<ProfilePage />} />
      <Route path="/login" element={<div>LOGIN PAGE</div>} />
    </Routes></MemoryRouter></AuthProvider></QueryClientProvider>);
    expect(await screen.findByText('Pinned Projects')).toBeInTheDocument();
    expect(screen.getByText('ayesha-dev / campus-bus-tracker')).toBeInTheDocument();
    // The unpinned free-text project is not shown in this section.
    expect(screen.queryByText('Draft notes')).toBeNull();
    await user.click(screen.getByRole('button', { name: /ayesha-dev \/ campus-bus-tracker/ }));
    // Live viewer with real GitHub data: default branch from the API,
    // README as Markdown, no fixture content.
    expect(await screen.findByText('⎇ develop')).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Campus Bus Tracker' })).toBeInTheDocument();
    expect(await screen.findByText(/package\.json/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Close repository' }));
    expect(await screen.findByText('Pinned Projects')).toBeInTheDocument();
  });
});
