import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider, useAuth } from '../app/auth';
import SettingsPage from '../features/settings/SettingsPage';
import TeamsPage from '../features/teams/TeamsPage';
import MessagesPage from '../features/messages/MessagesPage';
import MasterData from '../admin/pages/MasterData';
import AdminEvents from '../admin/pages/AdminEvents';
import Audit from '../admin/pages/Audit';
import NotificationsPage from '../features/notifications/NotificationsPage';
import PostDetail from '../features/posts/PostDetail';
import Dashboard from '../admin/pages/Dashboard';
import Analytics from '../admin/pages/Analytics';
import Feed from '../features/feed/Feed';
import SavedPage from '../features/posts/SavedPage';
import Users from '../admin/pages/Users';

/** Fix-worker tests: one test per fix (1–11); fix 12 is backend test files
 *  only and is covered by the backend suite. Same mocked-fetch harness as
 *  api-contracts.test.tsx — nothing here hits a real backend. */

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
function renderWithProviders(ui: React.ReactNode, initialEntries: string[] = ['/']) {
  return render(<QueryClientProvider client={qc()}><MemoryRouter initialEntries={initialEntries}>{ui}</MemoryRouter></QueryClientProvider>);
}
function renderWithAuth(ui: React.ReactNode, initialEntries: string[] = ['/']) {
  return render(<QueryClientProvider client={qc()}><AuthProvider><MemoryRouter initialEntries={initialEntries}>{ui}</MemoryRouter></AuthProvider></QueryClientProvider>);
}
function jsonBody(call: Call): Record<string, unknown> {
  return JSON.parse(String(call.body)) as Record<string, unknown>;
}
function lastPost(url: string): Call | undefined {
  return [...calls].reverse().find((c) => c.url === url && c.method === 'POST');
}

beforeEach(() => {
  calls = [];
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

describe('Fix 1 — account deletion signs the client out', () => {
  function Probe() { const { user } = useAuth(); return <div>{user ? 'SIGNED IN' : 'SIGNED OUT'}</div>; }
  it('clears auth state and navigates to /login on delete success', async () => {
    route = (url, method) => {
      if (url === '/api/auth/me') return json({ id: 1, fullName: 'Test User', state: 'VERIFIED' });
      if (url === '/api/me/delete' && method === 'POST') return json({});
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithAuth(<Routes>
      <Route path="/settings" element={<><Probe /><SettingsPage /></>} />
      <Route path="/login" element={<><Probe /><div>LOGIN PAGE</div></>} />
    </Routes>, ['/settings']);
    await screen.findByText('SIGNED IN');
    await user.click(screen.getByRole('button', { name: 'Delete my account…' }));
    await user.type(screen.getByLabelText('Password'), 'Password1');
    await user.click(screen.getByRole('button', { name: 'Delete account' }));
    await waitFor(() => expect(lastPost('/api/me/delete')).toBeDefined());
    await screen.findByText('LOGIN PAGE');
    expect(screen.getByText('SIGNED OUT')).toBeTruthy();
  });
});

describe('Fix 2 — teams only fetch requests for teams the caller created', () => {
  it('makes no requests call for a non-owned team', async () => {
    route = (url) => {
      if (url === '/api/auth/me') return json({ id: 7 });
      if (url === '/api/teams') return json([
        { id: 2, name: 'Hackers', creatorId: 99 },
        { id: 3, name: 'Coders', creatorId: 7 },
      ]);
      if (url === '/api/teams/3/requests') return json([{ id: 5, userId: 9, status: 'PENDING' }]);
      if (url === '/api/teams/2/requests') return json([{ id: 6, userId: 10, status: 'PENDING' }]);
      return defaultRoute(url);
    };
    renderWithProviders(<TeamsPage />);
    await screen.findByText('Hackers');
    await screen.findByText('Coders');
    await screen.findByRole('button', { name: 'Accept 9' });
    expect(calls.some((c) => c.url === '/api/teams/3/requests')).toBe(true);
    expect(calls.some((c) => c.url === '/api/teams/2/requests')).toBe(false);
  });
});

describe('Fix 3 — member Remove only for the room admin, never the creator', () => {
  function roomRoute(meId: number) {
    return (url: string) => {
      if (url === '/api/rooms') return json([{ id: 1, publicId: 'r1', name: 'Temp room', type: 'TEMP', memberIds: [7, 8, 9] }]);
      if (url === '/api/auth/me') return json({ id: meId });
      if (url === '/api/rooms/r1/messages') return json([]);
      return defaultRoute(url);
    };
  }
  it('admin (creator, first member) sees Remove for other members but not self', async () => {
    route = roomRoute(7);
    const user = userEvent.setup();
    const { unmount } = renderWithProviders(<MessagesPage />);
    await screen.findByText('Temp room');
    await user.click(screen.getByRole('button', { name: 'Temp room' }));
    await waitFor(() => expect(screen.getAllByRole('button', { name: 'Remove' }).length).toBe(2));
    unmount();
  });
  it('a non-admin member sees no Remove button at all', async () => {
    route = roomRoute(8);
    const user = userEvent.setup();
    const { unmount } = renderWithProviders(<MessagesPage />);
    await screen.findByText('Temp room');
    await user.click(screen.getByRole('button', { name: 'Temp room' }));
    await screen.findByText(/Members:/);
    expect(screen.queryAllByRole('button', { name: 'Remove' }).length).toBe(0);
    unmount();
  });
});

describe('Fix 4 — MasterData Add disabled until required fields are chosen', () => {
  it('departments Add stays disabled without a catalog, then POSTs catalogId once chosen', async () => {
    route = (url, method) => {
      if (url === '/api/public/universities') return json([{ id: 1, name: 'Metropolitan University', shortName: 'MU', emailDomains: 'mu.ac.bd' }]);
      if (url === '/api/public/department-catalog') return json([{ id: 5, name: 'CSE' }]);
      if (url === '/api/public/departments?universityId=1') return json([]);
      if (url === '/api/admin/master-data/departments' && method === 'POST') return json({ id: 9 }, 201);
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithProviders(<MasterData />);
    await screen.findByText('Metropolitan University');
    await user.click(screen.getByRole('tab', { name: 'Departments' }));
    const add = screen.getByRole('button', { name: 'Add' });
    expect(add).toBeDisabled();
    await user.type(screen.getByLabelText('Name'), 'CSE Dept');
    expect(add).toBeDisabled();
    await waitFor(() => expect((screen.getByLabelText('Department catalog') as HTMLSelectElement).options.length).toBeGreaterThan(1));
    await user.selectOptions(screen.getByLabelText('Department catalog'), '5');
    expect(add).toBeEnabled();
    await user.click(add);
    await waitFor(() => expect(lastPost('/api/admin/master-data/departments')).toBeDefined());
    expect(jsonBody(lastPost('/api/admin/master-data/departments')!)).toEqual({ universityId: 1, catalogId: 5, name: 'CSE Dept' });
  });
});

describe('Fix 5 — AdminEvents Hide', () => {
  it('Hide patches {status: HIDE}', async () => {
    route = (url, method) => {
      if (url === '/api/admin/events') return json([{ id: 5, title: 'Fake event', status: 'ACTIVE', visibility: 'UNIVERSITY', eventDeadline: '2026-12-01T23:59:59' }]);
      if (url === '/api/admin/events/5' && method === 'PATCH') return json({ id: 5, status: 'HIDDEN_ADMIN' });
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithProviders(<AdminEvents />);
    await screen.findByText('Fake event');
    await user.click(screen.getByRole('button', { name: 'Hide' }));
    await waitFor(() => expect(calls.some((c) => c.url === '/api/admin/events/5' && c.method === 'PATCH')).toBe(true));
    const patch = [...calls].reverse().find((c) => c.url === '/api/admin/events/5' && c.method === 'PATCH')!;
    expect(jsonBody(patch)).toEqual({ status: 'HIDE' });
  });
});

describe('Fix 6 — audit export is labelled unfiltered and carries the limit', () => {
  it('export link has ?limit=200 and says it ignores the filter', async () => {
    route = (url) => {
      if (url === '/api/admin/audit-logs?limit=200') return json([{ id: 1, action: 'VERIFICATION_APPROVED', actorAdminId: 2 }]);
      return defaultRoute(url);
    };
    renderWithProviders(<Audit />);
    await screen.findByText('VERIFICATION_APPROVED');
    expect(screen.getByRole('link', { name: 'Export CSV (unfiltered)' }).getAttribute('href')).toBe('/api/admin/audit-logs/export?limit=200');
    expect(screen.getByText(/ignores the action filter/)).toBeTruthy();
  });
});

describe('Fix 7 — notification preferences report per-type status', () => {
  it('a failed 2nd of 3 still shows the two that applied', async () => {
    route = (url, method, body) => {
      if (url === '/api/notifications') return json([]);
      if (url === '/api/notifications/preferences' && method === 'POST') {
        return jsonBody({ url, method, body } as Call).type === 'REPORT_AUTO_HIDE'
          ? json({ message: 'boom' }, 500) : json({});
      }
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithProviders(<NotificationsPage />);
    await screen.findByText('No notifications');
    await user.click(screen.getByRole('button', { name: 'Save preferences' }));
    await screen.findByText('VERIFICATION_DECISION: saved');
    expect(screen.getByText('TEAM_JOIN_ACCEPTED: saved')).toBeTruthy();
    expect(screen.getByText('REPORT_AUTO_HIDE: failed — not saved, try again')).toBeTruthy();
    expect(screen.getByText(/Some preferences could not be saved: REPORT_AUTO_HIDE/)).toBeTruthy();
    expect(screen.queryByText('Preferences saved.')).toBeNull();
  });
  it('all three succeeding still reports "Preferences saved."', async () => {
    route = (url, method) => {
      if (url === '/api/notifications') return json([]);
      if (url === '/api/notifications/preferences' && method === 'POST') return json({});
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithProviders(<NotificationsPage />);
    await screen.findByText('No notifications');
    await user.click(screen.getByRole('button', { name: 'Save preferences' }));
    await screen.findByText('Preferences saved.');
    expect(screen.getByText('VERIFICATION_DECISION: saved')).toBeTruthy();
  });
});

describe('Fix 8 + 10 — PostDetail mentions and comment authorName', () => {
  function postRoute() {
    return (url: string, method: string) => {
      if (url === '/api/posts/1') return json({ id: 1, title: 'Hello', body: 'Body text', authorId: 7, status: 'ACTIVE' });
      if (url === '/api/posts/1/comments' && method !== 'POST') return json([]);
      if (url === '/api/posts/1/attachments') return json([]);
      if (url === '/api/auth/me') return json({ id: 7 });
      if (url.startsWith('/api/search')) return json({ users: [{ id: 9, fullName: 'Ayesha Khan' }] });
      if (url === '/api/posts/1/comments' && method === 'POST') return json({ id: 11 }, 201);
      return defaultRoute(url);
    };
  }
  it('clicking a mention suggestion sends its id as mentionUserIds', async () => {
    route = postRoute();
    const user = userEvent.setup();
    renderWithProviders(<Routes><Route path="/posts/:id" element={<PostDetail />} /></Routes>, ['/posts/1']);
    await screen.findByText('Hello');
    await user.type(screen.getByLabelText('Add a comment'), '@Aye');
    await user.click(await screen.findByRole('button', { name: '@Ayesha Khan' }));
    expect((screen.getByLabelText('Add a comment') as HTMLInputElement).value).toBe('@Ayesha Khan ');
    await user.click(screen.getByRole('button', { name: 'Comment' }));
    await waitFor(() => expect(lastPost('/api/posts/1/comments')).toBeDefined());
    expect(jsonBody(lastPost('/api/posts/1/comments')!)).toEqual({ body: '@Ayesha Khan ', mentionUserIds: [9] });
  });
  it('renders authorName from CommentDto and falls back to Student only when absent', async () => {
    route = (url: string, method: string) => {
      if (url === '/api/posts/1') return json({ id: 1, title: 'Hello', body: 'Body text', authorId: 7, status: 'ACTIVE' });
      if (url === '/api/posts/1/comments') return json([
        { id: 1, body: 'Nice post', authorName: 'Ayesha Khan' },
        { id: 2, body: 'Anonymous here' },
      ]);
      if (url === '/api/posts/1/attachments') return json([]);
      if (url === '/api/auth/me') return json({ id: 7 });
      return defaultRoute(url);
    };
    renderWithProviders(<Routes><Route path="/posts/:id" element={<PostDetail />} /></Routes>, ['/posts/1']);
    await screen.findByText('Ayesha Khan');
    expect(screen.getByText('Student')).toBeTruthy();
    expect(screen.getByText('Nice post')).toBeTruthy();
  });
});

describe('Fix 9 — Dashboard / Analytics read the real backend fields', () => {
  it('dashboard renders totalUsers / reportsAwaitingReview / newUsersToday', async () => {
    route = (url) => {
      if (url === '/api/admin/dashboard') return json({ totalUsers: 42, pendingVerifications: 3, reportsAwaitingReview: 5, activeEvents: 7, newUsersToday: 2 });
      return defaultRoute(url);
    };
    renderWithProviders(<Dashboard />);
    await screen.findByText('Users');
    expect(screen.getByText('42')).toBeTruthy();
    expect(screen.getByText('3')).toBeTruthy();
    expect(screen.getByText('5')).toBeTruthy();
    expect(screen.getByText('7')).toBeTruthy();
    expect(screen.getByText(/New users per day:/).textContent).toContain('2');
  });
  it('analytics renders verificationTurnaroundHours and derives eventCount from eventsPerSector', async () => {
    route = (url) => {
      if (url === '/api/admin/analytics') return json({
        activeUniversities: 4, verificationTurnaroundHours: 36,
        eventsPerSector: [{ sector: 'AI/ML', c: 3 }, { sector: 'Web Development', c: 2 }],
        usersPerDay30: [{ d: '2026-11-16', c: 5 }],
      });
      return defaultRoute(url);
    };
    const { container } = renderWithProviders(<Analytics />);
    await screen.findByText('Active universities');
    const nums = [...container.querySelectorAll('.stat-num')].map((el) => el.textContent);
    expect(nums).toEqual(['4', '36', '5']);
    expect(screen.getByText('hours (average)')).toBeTruthy();
    expect(screen.getByText(/User growth:/).textContent).toContain('2026-11-16');
  });
});

describe('Fix 10 — Feed reads sectors/commentCount/body; SavedPage reads title', () => {
  it('feed shows sector pills and commentCount from the real fields, never content', async () => {
    route = (url) => {
      if (url === '/api/public/sectors') return json([{ id: 7, name: 'AI/ML' }]);
      if (url.startsWith('/api/feed')) return json({ items: [{ id: 1, title: 'Hello', body: 'Real body', content: 'STALE content', authorName: 'Ayesha', sectors: ['AI/ML', 'Data Science'], commentCount: 4 }], nextCursor: null });
      if (url === '/api/community/context') return json({});
      if (url === '/api/profiles/me') return json({ sectors: [] });
      return defaultRoute(url);
    };
    const { container } = renderWithProviders(<Feed />);
    await screen.findByText('Real body');
    expect(screen.queryByText('STALE content')).toBeNull();
    expect([...container.querySelectorAll('.pill')].map((el) => el.textContent)).toEqual(['AI/ML', 'Data Science']);
    expect(container.textContent).toContain('· 4 comments');
  });
  it('saved page renders BookmarkDto title, with a fallback only when absent', async () => {
    route = (url) => {
      if (url === '/api/bookmarks') return json([
        { id: 1, targetType: 'POST', targetId: 3, title: 'Saved post title' },
        { id: 2, targetType: 'POST', targetId: 4, title: null },
      ]);
      return defaultRoute(url);
    };
    renderWithProviders(<SavedPage />);
    await screen.findByText('Saved post title');
    expect(screen.getByText('Saved item 4')).toBeTruthy();
  });
});

describe('Fix 11 — MasterData does not fetch departments/batches without a university id', () => {
  it('no departments fetch while universities are still empty', async () => {
    route = (url) => {
      if (url === '/api/public/universities') return json([]);
      if (url === '/api/public/department-catalog') return json([]);
      if (url.startsWith('/api/public/departments')) return json([]);
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithProviders(<MasterData />);
    await waitFor(() => expect(calls.some((c) => c.url === '/api/public/universities')).toBe(true));
    await user.click(screen.getByRole('tab', { name: 'Departments' }));
    await waitFor(() => expect(calls.some((c) => c.url === '/api/public/department-catalog')).toBe(true));
    expect(calls.some((c) => c.url.startsWith('/api/public/departments?universityId='))).toBe(false);
  });
  it('fetches departments with the first university id once universities load', async () => {
    route = (url) => {
      if (url === '/api/public/universities') return json([{ id: 1, name: 'Metropolitan University', shortName: 'MU', emailDomains: 'mu.ac.bd' }]);
      if (url === '/api/public/department-catalog') return json([]);
      if (url === '/api/public/departments?universityId=1') return json([]);
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithProviders(<MasterData />);
    await screen.findByText('Metropolitan University');
    await user.click(screen.getByRole('tab', { name: 'Departments' }));
    await waitFor(() => expect(calls.some((c) => c.url === '/api/public/departments?universityId=1')).toBe(true));
    expect(calls.some((c) => c.url === '/api/public/departments?universityId=')).toBe(false);
  });
});

describe('Verification follow-up — FORCE_RESET shows the temporary password once', () => {
  it('shows the temp password returned by the user-action endpoint', async () => {
    route = (url, method) => {
      if (url === '/api/admin/users') return json([{ id: 9, fullName: 'Bad Actor', email: 'b@x.edu', state: 'VERIFIED' }]);
      if (url === '/api/admin/users/9/FORCE_RESET' && method === 'POST') return json({ user: { id: 9 }, temporaryPassword: 'a1b2c3d4e5f6A1' });
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithProviders(<Users />);
    await screen.findByText('Bad Actor');
    await user.type(screen.getByLabelText('Reason'), 'password reset');
    await user.click(screen.getByRole('button', { name: 'FORCE_RESET' }));
    await waitFor(() => expect(lastPost('/api/admin/users/9/FORCE_RESET')).toBeDefined());
    expect(await screen.findByText(/a1b2c3d4e5f6A1/)).toBeInTheDocument();
    expect(screen.getByText(/shown once/)).toBeInTheDocument();
  });
  it('shows no temp password for other actions', async () => {
    route = (url, method) => {
      // D-118: SUSPEND is Platform Admin only, so the page needs a
      // Platform Admin in the auth context to render the button.
      if (url === '/api/admin/auth/me') return json({ adminId: 1, email: 'pa@example.com', role: 'PLATFORM_ADMIN', mfaVerified: true });
      if (url === '/api/admin/users') return json([{ id: 9, fullName: 'Bad Actor', email: 'b@x.edu', state: 'VERIFIED' }]);
      if (url === '/api/admin/users/9/SUSPEND' && method === 'POST') return json({ user: { id: 9 }, temporaryPassword: null });
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    render(<QueryClientProvider client={qc()}><AuthProvider restorePath="/admin/auth/me"><MemoryRouter><Users /></MemoryRouter></AuthProvider></QueryClientProvider>);
    await screen.findByText('Bad Actor');
    await user.type(screen.getByLabelText('Reason'), 'spam');
    await user.click(await screen.findByRole('button', { name: 'SUSPEND' }));
    await waitFor(() => expect(lastPost('/api/admin/users/9/SUSPEND')).toBeDefined());
    expect(screen.queryByText(/shown once/)).not.toBeInTheDocument();
  });
});
