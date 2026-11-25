import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from '../app/auth';
import Feed from '../features/feed/Feed';
import NetworkPage from '../features/network/NetworkPage';
import SearchPage from '../features/search/SearchPage';
import ProfilePage from '../features/profile/ProfilePage';

/** Slice D (D-200, D-201): feed author links, NetworkPage follower
 *  lists from a bare array (Defect 1 regression), the real connection
 *  inbox, search sections + pager, and profile connection actions.
 *  Same fetch-router harness as community-pages.test.tsx. */
type RouteFn = (url: string, method?: string) => { status: number; body: unknown } | undefined;
let route: RouteFn;
let calls: { url: string; method: string; body?: string }[] = [];
const json = (body: unknown, status = 200) => ({ status, body });
const defaultRoute = (url: string) => (url === '/api/csrf' ? json({}) : undefined);

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
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); });

function renderWithClient(el: React.ReactElement, entries: string[] = ['/']) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={qc}><AuthProvider><MemoryRouter initialEntries={entries}>{el}</MemoryRouter></AuthProvider></QueryClientProvider>);
}

describe('Feed — author names link to profiles (D-200)', () => {
  it('renders the post author name as a link to /profile/{authorId}', async () => {
    route = (url) => {
      if (url === '/api/public/sectors') return json([]);
      if (url.startsWith('/api/feed')) return json({ items: [{ id: 11, title: 'Library hours extended', body: 'The central library now stays open until 10 pm.', authorId: 7, authorName: 'Tanvir Hasan', sectors: [], reactionCount: 3, commentCount: 0, createdAt: '2026-11-19T10:00:00Z' }], nextCursor: null });
      if (url === '/api/community/context') return json({ universityName: 'Metropolitan University', departmentName: 'CSE' });
      if (url === '/api/profiles/me') return json({ userId: 5, fullName: 'Ayesha Rahman', departmentName: 'CSE', universityName: 'Metropolitan University', batchLabel: 'Batch 27', state: 'VERIFIED', followers: 96, following: 128, sectors: [] });
      if (url === '/api/bookmarks') return json([]);
      if (url === '/api/events/upcoming') return json([]);
      if (url === '/api/posts/11/comments') return json([{ id: 21, authorId: 9, authorName: 'Nusrat Jahan', body: 'Thanks for sharing.' }]);
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithClient(<Feed />, ['/feed']);
    const authorLink = await screen.findByRole('link', { name: 'Tanvir Hasan' });
    expect(authorLink).toHaveAttribute('href', '/profile/7');
    // Comment authors link too, once the thread is opened.
    await user.click(screen.getByRole('button', { name: 'Comment' }));
    const commentLink = await screen.findByRole('link', { name: 'Nusrat Jahan' });
    expect(commentLink).toHaveAttribute('href', '/profile/9');
  });
});

describe('My Network — follower list and connection inbox (D-200)', () => {
  it('renders followers from the bare array the endpoint returns (Defect 1 regression)', async () => {
    route = (url) => {
      if (url === '/api/profiles/me') return json({ userId: 5, fullName: 'Ayesha Rahman', followers: 1, following: 0 });
      if (url === '/api/teams') return json([]);
      if (url === '/api/connections/requests/incoming') return json([]);
      if (url === '/api/connections/requests/outgoing') return json([]);
      if (url === '/api/profiles/suggestions') return json([]);
      if (url.startsWith('/api/profiles/5/followers')) return json([{ userId: 7, fullName: 'Sadia Islam', department: 'CSE', university: 'Metropolitan University', batch: 'Batch 28', followedByViewer: false }]);
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithClient(<NetworkPage />, ['/network']);
    await screen.findByText('Followers');
    const viewButtons = screen.getAllByRole('button', { name: 'View' });
    await user.click(viewButtons[0]);
    expect(await screen.findByText('Sadia Islam')).toBeInTheDocument();
    expect(screen.queryByText('No followers yet')).toBeNull();
  });

  it('accepts an incoming connection request through the real contract', async () => {
    route = (url, method) => {
      if (url === '/api/profiles/me') return json({ userId: 5, fullName: 'Ayesha Rahman', followers: 0, following: 0 });
      if (url === '/api/teams') return json([]);
      if (url === '/api/connections/requests/incoming') return json([{ id: 3, requesterId: 7, requesterName: 'Sadia Islam', addresseeId: 5, addresseeName: 'Ayesha Rahman', status: 'PENDING', message: 'Hi, let us connect.' }]);
      if (url === '/api/connections/requests/outgoing') return json([]);
      if (url === '/api/profiles/suggestions') return json([]);
      if (url === '/api/connections/requests/3/accept' && method === 'POST') return json({ status: 'accepted' });
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithClient(<NetworkPage />, ['/network']);
    expect(await screen.findByText('Sadia Islam')).toBeInTheDocument();
    expect(screen.getByText(/Hi, let us connect/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Accept' }));
    await waitFor(() => expect(calls.some((c) => c.url === '/api/connections/requests/3/accept' && c.method === 'POST')).toBe(true));
  });
});

describe('Search — sections, count, pager, connection actions (D-201, D-200)', () => {
  it('shows Events / Universities / Departments sections, a result count, and pages with the cursor', async () => {
    route = (url) => {
      if (url === '/api/public/sectors') return json([]);
      if (url.startsWith('/api/search')) return json({
        posts: [
          { id: 11, title: 'Robotics night', body: 'Build night.', type: 'EVENT', location: 'Central Library', organizerName: 'CSE Club' },
          { id: 12, title: 'Robotics tips', body: 'Some tips.', type: 'POST' },
        ],
        users: [{ id: 7, fullName: 'Sadia Islam', matchedSkills: ['Robotics'] }],
        universities: [{ id: 1, name: 'Metropolitan University', shortName: 'MU', category: 'PRIVATE' }],
        departments: [{ id: 2, name: 'CSE — MU' }],
        postsNextCursor: '12', usersNextCursor: null, total: 5,
      });
      if (url === '/api/connections/status/7') return json({ status: 'NONE', requestId: null });
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithClient(<SearchPage />, ['/search?q=Robotics']);
    expect(await screen.findByText('5 results')).toBeInTheDocument();
    expect(screen.getByText('Events')).toBeInTheDocument();
    expect(screen.getByText('Robotics night')).toBeInTheDocument();
    expect(screen.getByText('Central Library')).toBeInTheDocument();
    expect(screen.getByText('Universities')).toBeInTheDocument();
    expect(screen.getByText(/Metropolitan University/)).toBeInTheDocument();
    expect(screen.getByText('Departments')).toBeInTheDocument();
    expect(screen.getByText('CSE — MU')).toBeInTheDocument();
    expect(screen.getByText(/Skills: Robotics/)).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Connect' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Next →' }));
    await waitFor(() => expect(calls.some((c) => c.url.includes('/api/search') && c.url.includes('cursor=12'))).toBe(true));
  });
});

describe('Profile — connection actions (D-200)', () => {
  it('shows Connect for a stranger and sends the real request', async () => {
    route = (url, method) => {
      if (url === '/api/auth/me') return json({ id: 5, fullName: 'Ayesha Rahman', state: 'VERIFIED' });
      if (url === '/api/profiles/7') return json({ userId: 7, fullName: 'Sadia Islam', state: 'VERIFIED', departmentName: 'CSE', universityName: 'Metropolitan University', followers: 3, following: 2, skills: [] });
      if (url === '/api/profiles/7/activity-items') return json([]);
      if (url === '/api/connections/status/7') return json({ status: 'NONE', requestId: null });
      if (url === '/api/connections/requests' && method === 'POST') return json({ id: 9, status: 'PENDING' }, 201);
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithClient(<Routes><Route path="/profile/:id" element={<ProfilePage />} /></Routes>, ['/profile/7']);
    expect(await screen.findByText('Sadia Islam')).toBeInTheDocument();
    await user.click(await screen.findByRole('button', { name: 'Connect' }));
    await waitFor(() => expect(calls.some((c) => c.url === '/api/connections/requests' && c.method === 'POST'
      && JSON.parse(c.body ?? '{}').addresseeId === 7)).toBe(true));
  });
});
