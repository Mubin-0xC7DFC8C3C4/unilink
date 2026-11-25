import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '../app/auth';
import Feed from '../features/feed/Feed';
import AdminEvents from '../admin/pages/AdminEvents';
import CreateEvent from '../admin/pages/CreateEvent';
import CalendarPage from '../features/events/CalendarPage';
import SearchPage from '../features/search/SearchPage';
import TeamsPage from '../features/teams/TeamsPage';
import ProfilePage from '../features/profile/ProfilePage';
import SettingsModal from '../features/settings/SettingsModal';
import { ThemeProvider } from '../app/ThemeContext';
import MessagesPage from '../features/messages/MessagesPage';
import Moderation from '../admin/pages/Moderation';
import Appeals from '../admin/pages/Appeals';
import Users from '../admin/pages/Users';
import Audit from '../admin/pages/Audit';
import MasterData from '../admin/pages/MasterData';
import Moderators from '../admin/pages/Moderators';
import Announcements from '../admin/pages/Announcements';

/** Phase 2 contract tests (I-7/I-8/I-9): every corrected frontend call is
 *  asserted against the mocked fetch layer — path, method and payload.
 *  Nothing here hits a real backend. */

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

describe('Feed (I-8)', () => {
  // Reference design (owner, 2026-11-20): Community Home carries no level
  // tabs and no sector dropdown — the feed is the department level by
  // default (FR-COM-07) and the composer posts at that level. The two
  // pre-redesign tests that drove the removed switcher are replaced by
  // these composer-contract tests against the same endpoints.
  const feedRoute = (url: string, method?: string) => {
    if (url.startsWith('/api/feed')) return json({ items: [], nextCursor: null });
    if (url === '/api/community/context') return json({ universityName: 'Metropolitan University', departmentName: 'CSE' });
    if (url === '/api/profiles/me') return json({ sectors: [] });
    if (url === '/api/public/sectors') return json([{ id: 7, name: 'AI/ML' }]);
    if (url === '/api/bookmarks') return json([]);
    if (url === '/api/events/upcoming') return json([]);
    if (url === '/api/posts' && method === 'POST') return json({ id: 1 }, 201);
    return defaultRoute(url);
  };
  it('reads the department feed and posts visibility + sectorIds via the composer modal', async () => {
    route = feedRoute;
    const user = userEvent.setup();
    renderWithProviders(<Feed />);
    await waitFor(() => expect(calls.some((c) => c.url === '/api/feed?level=DEPARTMENT&sort=recent')).toBe(true));
    await user.click(screen.getByRole('button', { name: 'Start a post' }));
    expect(await screen.findByRole('dialog', { name: 'Create a post' })).toBeInTheDocument();
    await user.type(screen.getByLabelText('Post title'), 'Hello');
    await user.type(screen.getByLabelText('Post body'), 'World');
    await user.click(screen.getByRole('button', { name: 'Post' }));
    await waitFor(() => expect(lastPost('/api/posts')).toBeDefined());
    const body = jsonBody(lastPost('/api/posts')!);
    expect(body).toEqual({ title: 'Hello', body: 'World', visibility: 'DEPARTMENT', sectorIds: [] });
    expect(body).not.toHaveProperty('level');
    expect(body).not.toHaveProperty('sector');
  });
});

describe('EventsPage + CalendarPage (I-8)', () => {
  it('creates an event from the Admin Portal with body and no type, deadline as a LocalDateTime', async () => {
    route = (url, method) => {
      if (url === '/api/events/upcoming') return json([]);
      if (url === '/api/admin/events' && method === 'POST') return json({ id: 5 }, 201);
      if (url === '/api/admin/events') return json([]);
      if (url === '/api/public/sectors') return json([]);
      if (url === '/api/public/universities') return json([]);
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithProviders(<CreateEvent />);
    await user.type(screen.getByLabelText('Event Title'), 'Hackathon');
    await user.type(screen.getByLabelText('Full Description'), 'A weekend hackathon');
    await user.type(screen.getByLabelText('Start Date'), '2026-12-01');
    await user.click(screen.getByRole('button', { name: 'Publish Event' }));
    await waitFor(() => expect(lastPost('/api/admin/events')).toBeDefined());
    const body = jsonBody(lastPost('/api/admin/events')!);
    expect(body.title).toBe('Hackathon');
    expect(body.body).toBe('A weekend hackathon');
    expect(body.deadline).toBe('2026-12-01T09:00:00');
    expect(body).not.toHaveProperty('type');
  });
  it('calendar sends from/to, not month', async () => {
    route = (url) => {
      if (url.startsWith('/api/calendar')) return json([]);
      return defaultRoute(url);
    };
    renderWithProviders(<CalendarPage />);
    const month = screen.getByLabelText('Month') as HTMLInputElement;
    // Month inputs don't take typed input reliably in jsdom; a change event
    // is what the component listens to.
    fireEvent.change(month, { target: { value: '2026-11' } });
    await waitFor(() => expect(calls.some((c) => c.url === '/api/calendar?from=2026-11-01&to=2026-12-01')).toBe(true));
    expect(calls.some((c) => c.url.includes('month='))).toBe(false);
  });
});

describe('SearchPage (I-8)', () => {
  it('maps people to type=users, sends the sector id, and renders Map{posts, users}', async () => {
    route = (url) => {
      if (url === '/api/public/sectors') return json([{ id: 3, name: 'Data Science' }]);
      if (url.startsWith('/api/search')) return json({ posts: [{ id: 1, title: 'ML post' }], users: [{ id: 9, fullName: 'Ayesha Khan' }] });
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithProviders(<SearchPage />);
    await waitFor(() => expect((screen.getByLabelText('Sector') as HTMLSelectElement).options[1]?.value).toBe('3'));
    await user.type(screen.getByLabelText('Search'), 'ayesha');
    await user.selectOptions(screen.getByLabelText('Type'), 'people');
    await user.selectOptions(screen.getByLabelText('Sector'), '3');
    await user.click(screen.getByRole('button', { name: /Search/i }));
    await waitFor(() => expect(calls.some((c) => c.url === '/api/search?q=ayesha&type=users&sector=3')).toBe(true));
    await screen.findByText('Ayesha Khan');
    await screen.findByText('ML post');
  });
});

describe('TeamsPage (I-8)', () => {
  it('decides a join request with {accept: true}', async () => {
    route = (url, method) => {
      if (url === '/api/auth/me') return json({ id: 7 });
      if (url === '/api/teams') return json([{ id: 2, name: 'Hackers', creatorId: 7, memberIds: [] }]);
      if (url === '/api/teams/2/requests') return json([{ id: 5, userId: 9, status: 'PENDING' }]);
      if (url === '/api/teams/requests/5/decide' && method === 'POST') return json({});
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithProviders(<TeamsPage />);
    await screen.findByText('Hackers');
    // The per-team requests query resolves after the team list renders.
    await user.click(await screen.findByRole('button', { name: 'Accept 9' }));
    await waitFor(() => expect(lastPost('/api/teams/requests/5/decide')).toBeDefined());
    expect(jsonBody(lastPost('/api/teams/requests/5/decide')!)).toEqual({ accept: true });
  });
});

describe('ProfilePage (I-8)', () => {
  it('follows via POST /profiles/{id}/follow and loads activity', async () => {
    route = (url, method) => {
      if (url === '/api/profiles/9') return json({ id: 9, userId: 9, fullName: 'Ayesha Khan', email: 'a@mu.ac.bd', state: 'VERIFIED', followers: 3, following: 1 });
      if (url === '/api/profiles/9/activity-items') return json([{ kind: 'post', postId: 1, postTitle: 'First post', text: null, reaction: null, createdAt: '2026-11-20T10:00:00' }]);
      if (url === '/api/profiles/9/projects') return json([]);
      if (url === '/api/profiles/9/education') return json([]);
      if (url === '/api/profiles/9/achievements') return json([]);
      if (url === '/api/profiles/9/follow' && method === 'POST') return json({});
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithProviders(<Routes><Route path="/profiles/:id" element={<ProfilePage />} /></Routes>, ['/profiles/9']);
    await screen.findByText('Ayesha Khan');
    expect(calls.some((c) => c.url === '/api/profiles/9/activity-items')).toBe(true);
    await screen.findByText('First post');
    await user.click(screen.getByRole('button', { name: 'Follow' }));
    await waitFor(() => expect(lastPost('/api/profiles/9/follow')).toBeDefined());
  });
});

describe('Notification preferences in the Settings modal (I-8)', () => {
  it('saves one POST /notifications/preferences per real type', async () => {
    route = (url, method) => {
      if (url === '/api/notifications') return json([]);
      if (url === '/api/notifications/preferences' && method === 'POST') return json({});
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithProviders(<ThemeProvider><AuthProvider><SettingsModal open initialTab="Notifications" onClose={() => {}} /></AuthProvider></ThemeProvider>);
    await user.click(screen.getByRole('button', { name: 'Save preferences' }));
    await waitFor(() => expect(calls.filter((c) => c.url === '/api/notifications/preferences' && c.method === 'POST').length).toBe(6));
    const types = calls.filter((c) => c.url === '/api/notifications/preferences').map((c) => jsonBody(c).type);
    expect(types.sort()).toEqual(['CONNECTION_ACCEPTED', 'CONNECTION_REJECTED', 'CONNECTION_REQUEST', 'REPORT_AUTO_HIDE', 'TEAM_JOIN_ACCEPTED', 'VERIFICATION_DECISION']);
    expect(calls.filter((c) => c.url === '/api/notifications/preferences').every((c) => 'emailEnabled' in jsonBody(c) && 'inAppEnabled' in jsonBody(c))).toBe(true);
  });
});

describe('MessagesPage read ticks (I-9)', () => {
  it('renders ✓ for unread-by-other and ✓✓ only when read is true, only on own messages', async () => {
    route = (url) => {
      if (url === '/api/rooms') return json([{ id: 1, publicId: 'r1', name: 'Dept room', type: 'DEPARTMENT' }]);
      if (url === '/api/auth/me') return json({ id: 7 });
      if (url === '/api/rooms/r1/messages') return json([
        { id: 1, publicId: 'm1', roomId: 1, senderId: 7, body: 'unread own', createdAt: '2026-11-17T10:00:00', read: false, hasAttachment: false },
        { id: 2, publicId: 'm2', roomId: 1, senderId: 7, body: 'read own', createdAt: '2026-11-17T10:01:00', read: true, hasAttachment: false },
        { id: 3, publicId: 'm3', roomId: 1, senderId: 8, body: 'other person', createdAt: '2026-11-17T10:02:00', read: true, hasAttachment: false },
      ]);
      if (url.startsWith('/api/rooms/r1/read')) return json({});
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    const { unmount } = renderWithProviders(<MessagesPage />);
    await screen.findByText('Dept room');
    await user.click(screen.getByRole('button', { name: 'Dept room' }));
    await screen.findByText('unread own');
    const ownUnread = screen.getByText('unread own').closest('li')!;
    const ownRead = screen.getByText('read own').closest('li')!;
    const other = screen.getByText('other person').closest('li')!;
    expect(ownUnread.textContent).toContain('✓');
    expect(ownUnread.textContent).not.toContain('✓✓');
    expect(ownRead.textContent).toContain('✓✓');
    expect(other.textContent).not.toContain('✓');
    unmount();
  });
});

describe('Admin pages (I-8)', () => {
  it('moderation queue loads from /admin/moderation-queue and decide sends targetType/targetId/action/reason', async () => {
    route = (url, method) => {
      if (url === '/api/admin/moderation-queue') return json([{ id: 1, targetType: 'POST', targetId: 3, reason: 'spam', status: 'PENDING' }]);
      if (url === '/api/admin/moderation/decide' && method === 'POST') return json({});
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithProviders(<Moderation />);
    await screen.findByText('spam');
    await user.click(screen.getByRole('button', { name: 'Hide content' }));
    await user.type(screen.getByLabelText('Decision reason (required)'), 'confirmed spam');
    await user.click(screen.getByRole('button', { name: 'Record decision' }));
    await waitFor(() => expect(lastPost('/api/admin/moderation/decide')).toBeDefined());
    expect(jsonBody(lastPost('/api/admin/moderation/decide')!)).toEqual({ targetType: 'POST', targetId: 3, action: 'HIDE', reason: 'confirmed spam' });
  });
  it('appeals decide sends ACCEPT for Overturn and REJECT for Uphold, with a reason', async () => {
    route = (url, method) => {
      if (url === '/api/admin/appeals') return json([{ id: 4, targetType: 'POST', targetId: 3, status: 'PENDING' }]);
      if (url === '/api/admin/appeals/4/decide' && method === 'POST') return json({});
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithProviders(<Appeals />);
    await screen.findByText('4');
    await user.type(screen.getByLabelText('Decision reason'), 'evidence reviewed');
    await user.click(screen.getByRole('button', { name: 'Overturn' }));
    await waitFor(() => expect(lastPost('/api/admin/appeals/4/decide')).toBeDefined());
    expect(jsonBody(lastPost('/api/admin/appeals/4/decide')!)).toEqual({ action: 'ACCEPT', reason: 'evidence reviewed' });
  });
  it('user actions send UPPERCASE values in the path', async () => {
    route = (url, method) => {
      // D-118: SUSPEND is Platform Admin only, so the page needs a
      // Platform Admin in the auth context to render the button.
      if (url === '/api/admin/auth/me') return json({ adminId: 1, email: 'pa@example.com', role: 'PLATFORM_ADMIN', mfaVerified: true });
      if (url === '/api/admin/users') return json([{ id: 9, fullName: 'Bad Actor', email: 'b@x.edu', state: 'VERIFIED' }]);
      if (url === '/api/admin/users/9') return json({ id: 9, fullName: 'Bad Actor', email: 'b@x.edu', state: 'VERIFIED' });
      if (url === '/api/admin/users/9/SUSPEND' && method === 'POST') return json({});
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    render(<QueryClientProvider client={qc()}><MemoryRouter><AuthProvider restorePath="/admin/auth/me"><Users /></AuthProvider></MemoryRouter></QueryClientProvider>);
    await screen.findByText('Bad Actor');
    await user.click(screen.getByRole('button', { name: 'View' }));
    await user.click(await screen.findByRole('button', { name: 'Suspend account' }));
    await user.type(screen.getByLabelText('Reason (required)'), 'spam');
    await user.click(screen.getByRole('button', { name: 'Confirm' }));
    await waitFor(() => expect(lastPost('/api/admin/users/9/SUSPEND')).toBeDefined());
    expect(calls.some((c) => c.url.includes('/suspend'))).toBe(false);
  });
  it('audit loads GET /admin/audit-logs and links the CSV export', async () => {
    route = (url) => {
      if (url === '/api/admin/audit-logs?limit=200') return json([{ id: 1, action: 'VERIFICATION_APPROVED', actorAdminId: 2 }]);
      return defaultRoute(url);
    };
    renderWithProviders(<Audit />);
    await screen.findByText('VERIFICATION_APPROVED');
    expect(calls.some((c) => c.url === '/api/admin/audit-logs?limit=200')).toBe(true);
    expect(screen.getByRole('link', { name: 'Export CSV (unfiltered)' }).getAttribute('href')).toBe('/api/admin/audit-logs/export?limit=200');
  });
  it('master data reads come from GET /api/public/*', async () => {
    route = (url) => {
      if (url === '/api/public/universities') return json([{ id: 1, name: 'Metropolitan University', shortName: 'MU', emailDomains: 'mu.ac.bd' }]);
      return defaultRoute(url);
    };
    renderWithProviders(<MasterData />);
    await screen.findByText('Metropolitan University');
    expect(calls.some((c) => c.url === '/api/public/universities')).toBe(true);
    expect(calls.some((c) => c.url.startsWith('/api/admin/universities'))).toBe(false);
  });
  it('moderators list comes from GET /api/admin/moderators, not a public endpoint', async () => {
    route = (url) => {
      if (url === '/api/admin/moderators') return json([{ id: 1, email: 'mod@mu.ac.bd', role: 'MODERATOR', universityId: 1, active: true }]);
      if (url === '/api/public/universities') return json([]);
      return defaultRoute(url);
    };
    renderWithProviders(<Moderators />);
    await screen.findByText('mod@mu.ac.bd');
    expect(calls.some((c) => c.url === '/api/admin/moderators')).toBe(true);
    expect(calls.some((c) => c.url.includes('/public/moderators'))).toBe(false);
  });
  it('announcement create sends scope GLOBAL', async () => {
    route = (url, method) => {
      if (url === '/api/admin/announcements') return json([]);
      if (url === '/api/public/universities') return json([]);
      if (url === '/api/admin/announcements' && method === 'POST') return json({ id: 1 }, 201);
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithProviders(<Announcements />);
    await screen.findByText('No announcements');
    await user.type(screen.getByLabelText('Title'), 'Welcome');
    await user.type(screen.getByLabelText('Body'), 'Hello all');
    await user.click(screen.getByRole('button', { name: 'Publish announcement' }));
    await waitFor(() => expect(lastPost('/api/admin/announcements')).toBeDefined());
    const body = jsonBody(lastPost('/api/admin/announcements')!);
    expect(body.scope).toBe('GLOBAL');
    expect(body.title).toBe('Welcome');
  });
  it('admin event Remove patches {status: REMOVE}', async () => {
    route = (url, method) => {
      if (url === '/api/admin/events') return json([{ id: 5, title: 'Fake event', status: 'ACTIVE', visibility: 'UNIVERSITY', eventDeadline: '2026-12-01T23:59:59' }]);
      if (url === '/api/admin/events/5' && method === 'PATCH') return json({ id: 5, status: 'REMOVED' });
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderWithProviders(<AdminEvents />);
    await screen.findByText('Fake event');
    await user.click(screen.getByRole('button', { name: 'Remove' }));
    await user.click(await screen.findByRole('button', { name: 'Remove event' }));
    await waitFor(() => expect(calls.some((c) => c.url === '/api/admin/events/5' && c.method === 'PATCH')).toBe(true));
    const patch = [...calls].reverse().find((c) => c.url === '/api/admin/events/5' && c.method === 'PATCH')!;
    expect(jsonBody(patch)).toEqual({ status: 'REMOVE' });
  });
});
