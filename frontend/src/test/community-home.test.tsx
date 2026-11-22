import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import Feed from '../features/feed/Feed';

/** Phase 3 — Community Home. Real mode is asserted against the mocked
 *  fetch layer (paths + payloads); Prototype Preview mode (feed request
 *  failed) must stay fully local — no reaction/report/bookmark calls. */

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

interface Call { url: string; method: string; body: unknown }
let calls: Call[] = [];
let route: (url: string, method: string) => Response | undefined;

const fetchMock = vi.fn();
function defaultRoute(url: string): Response | undefined {
  if (url === '/api/csrf') return json({});
  if (url === '/api/auth/me') return json({ message: 'unauthenticated' }, 401);
  return undefined;
}
function renderFeed() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={qc}><MemoryRouter initialEntries={['/feed']}><Feed /></MemoryRouter></QueryClientProvider>);
}
function jsonBody(call: Call): Record<string, unknown> {
  return JSON.parse(String(call.body)) as Record<string, unknown>;
}

const PROFILE = { userId: 5, fullName: 'Ayesha Rahman', departmentName: 'CSE', universityName: 'Metropolitan University', batchLabel: 'Batch 27', state: 'VERIFIED', followers: 96, following: 128, sectors: [] };

beforeEach(() => {
  calls = [];
  route = (url) => defaultRoute(url);
  fetchMock.mockImplementation(async (input: unknown, init?: RequestInit) => {
    const url = String(input);
    const method = init?.method || 'GET';
    calls.push({ url, method, body: init?.body });
    return route(url, method) ?? json({ message: 'no mock route for ' + url }, 404);
  });
  vi.stubGlobal('fetch', fetchMock);
});
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); });

describe('Community Home — real mode', () => {
  it('shows the real profile card + greeting, and wires Like/Save/Report/Follow/comments to the real contracts', async () => {
    route = (url, method) => {
      if (url === '/api/public/sectors') return json([]);
      if (url.startsWith('/api/feed')) return json({ items: [{ id: 11, title: 'Library hours extended', body: 'The central library now stays open until 10 pm.', authorId: 7, authorName: 'Tanvir Hasan', sectors: [], reactionCount: 3, commentCount: 2, createdAt: '2026-11-19T10:00:00Z' }], nextCursor: null });
      if (url === '/api/community/context') return json({ universityName: 'Metropolitan University', departmentName: 'CSE' });
      if (url === '/api/profiles/me') return json(PROFILE);
      if (url === '/api/bookmarks' && method === 'GET') return json([{ targetType: 'POST', targetId: 11 }]);
      if (url === '/api/events/upcoming') return json([]);
      if (url === '/api/reactions?reaction=LIKE' && method === 'POST') return json({ status: 'ok' });
      if (url === '/api/reports' && method === 'POST') return json({ id: 1 }, 201);
      if (url === '/api/profiles/7/follow' && method === 'POST') return json({ status: 'following' });
      if (url === '/api/posts/11/comments' && method === 'GET') return json([{ authorName: 'Nusrat Jahan', body: 'Thanks for sharing.' }]);
      if (url === '/api/posts/11/comments' && method === 'POST') return json({ id: 3 }, 201);
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    renderFeed();
    // The profile card comes from the real profile API. (The greeting
    // header block was removed in the owner's 2026-11-21 cleanup.)
    expect(await screen.findByText('Ayesha Rahman')).toBeInTheDocument();
    expect(screen.getByText('CSE')).toBeInTheDocument();
    expect(screen.getByText('Metropolitan University · Batch 27')).toBeInTheDocument();
    // Followers/following counts are the API's real fields.
    expect(screen.getByText('Followers')).toBeInTheDocument();
    expect(screen.getByText('96')).toBeInTheDocument();
    expect(screen.getByText('128')).toBeInTheDocument();
    // Verified badges show (profile state VERIFIED; post authors are
    // verified by platform design).
    expect(screen.getAllByText('Verified').length).toBeGreaterThan(1);
    // The post renders as a card; Save starts active from GET /bookmarks.
    expect(await screen.findByText('Library hours extended')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Saved' })).toHaveAttribute('aria-pressed', 'true');
    // Suggested students never render in real mode (no such endpoint).
    expect(screen.queryByText('Suggested students')).toBeNull();
    // Like posts the real reaction and flips its active state.
    await user.click(screen.getByRole('button', { name: /Like/ }));
    await waitFor(() => expect(screen.getByRole('button', { name: /Like/ })).toHaveAttribute('aria-pressed', 'true'));
    const like = calls.find((c) => c.url === '/api/reactions?reaction=LIKE');
    expect(like).toBeDefined();
    expect(jsonBody(like!)).toEqual({ targetType: 'POST', targetId: 11 });
    // Follow calls the real profile follow endpoint.
    await user.click(screen.getByRole('button', { name: '+ Follow' }));
    expect(await screen.findByRole('button', { name: 'Following' })).toBeInTheDocument();
    expect(calls.some((c) => c.url === '/api/profiles/7/follow' && c.method === 'POST')).toBe(true);
    // Comment opens the inline drawer, reads the real comments and posts
    // through the same endpoint the post page uses.
    await user.click(screen.getByRole('button', { name: /Comment/ }));
    expect(await screen.findByText('Thanks for sharing.')).toBeInTheDocument();
    await user.type(screen.getByLabelText('Add a comment'), 'Great news.');
    await user.click(screen.getByRole('button', { name: 'Post' }));
    await waitFor(() => expect(calls.some((c) => c.url === '/api/posts/11/comments' && c.method === 'POST')).toBe(true));
    const commentCall = calls.find((c) => c.url === '/api/posts/11/comments' && c.method === 'POST');
    expect(jsonBody(commentCall!)).toEqual({ body: 'Great news.', mentionUserIds: [] });
    // Report lives in the card's "..." overflow menu (owner cleanup).
    await user.click(screen.getByRole('button', { name: 'More actions' }));
    await user.click(screen.getByRole('menuitem', { name: 'Report post' }));
    await user.click(screen.getByRole('button', { name: 'Submit report' }));
    expect(await screen.findByText('Reported.')).toBeInTheDocument();
    const report = calls.find((c) => c.url === '/api/reports');
    expect(jsonBody(report!)).toEqual({ targetType: 'POST', targetId: 11, reason: 'spam' });
    // Hide dismisses the card locally; Undo brings it back.
    await user.click(screen.getByRole('button', { name: 'Hide post' }));
    expect(await screen.findByText('Post hidden')).toBeInTheDocument();
    expect(screen.queryByText('Library hours extended')).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Undo' }));
    expect(await screen.findByText('Library hours extended')).toBeInTheDocument();
  }, 30_000);
});

describe('Community Home — Prototype Preview (feed unavailable)', () => {
  it('shows one banner and clean sample cards; every sample interaction stays local', async () => {
    route = (url) => {
      if (url === '/api/public/sectors') return json([]);
      if (url.startsWith('/api/feed')) return json({ message: 'down' }, 500);
      if (url === '/api/community/context') return json({});
      if (url === '/api/profiles/me') return json(PROFILE);
      if (url === '/api/bookmarks') return json([]);
      if (url === '/api/events/upcoming') return json([]);
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    const { container } = renderFeed();
    // The Prototype Preview banner was removed in the owner's
    // 2026-11-21 cleanup: the sample cards render with no banner.
    expect(screen.queryByText(/Prototype Preview/)).toBeNull();
    expect(await screen.findByText('Forming a team for the inter-university programming contest')).toBeInTheDocument();
    expect(container.textContent).not.toContain('[SAMPLE]');
    expect(container.textContent).not.toContain('Sample reaction line');
    // Singular comment counts are pluralised correctly.
    expect(container.textContent).toContain('1 comment');
    expect(container.textContent).not.toContain('1 comments');
    // Like toggles locally — no reaction call is made for a sample post.
    const likeButtons = screen.getAllByRole('button', { name: /Like/ });
    await user.click(likeButtons[0]);
    expect(likeButtons[0]).toHaveAttribute('aria-pressed', 'true');
    expect(calls.some((c) => c.url.includes('/reactions'))).toBe(false);
    // Save toggles locally too.
    const saveButtons = screen.getAllByRole('button', { name: 'Save' });
    await user.click(saveButtons[0]);
    expect(saveButtons[0]).toHaveTextContent('Saved');
    expect(calls.filter((c) => c.url === '/api/bookmarks' && c.method !== 'GET')).toHaveLength(0);
    // Comment opens the inline thread and accepts a local comment.
    const commentButtons = screen.getAllByRole('button', { name: /Comment/ });
    await user.click(commentButtons[1]);
    expect(await screen.findByText(/I can join for the graph topics/)).toBeInTheDocument();
    await user.type(screen.getByLabelText('Add a comment'), 'I would like to join too.');
    await user.click(screen.getByRole('button', { name: 'Post' }));
    expect(await screen.findByText('I would like to join too.')).toBeInTheDocument();
    expect(calls.some((c) => c.url.includes('/comments'))).toBe(false);
    // Report through the primary action resolves locally.
    await user.click(screen.getAllByRole('button', { name: 'More actions' })[0]);
    await user.click(screen.getByRole('menuitem', { name: 'Report post' }));
    await user.click(screen.getByRole('button', { name: 'Submit report' }));
    expect(await screen.findByText('Reported.')).toBeInTheDocument();
    expect(calls.some((c) => c.url === '/api/reports')).toBe(false);
    // Suggested students exist only in this preview, with local Follow.
    expect(screen.getByText('Suggested students')).toBeInTheDocument();
    expect(screen.getByText('Sadia Islam')).toBeInTheDocument();
    const followButtons = screen.getAllByRole('button', { name: 'Follow' });
    await user.click(followButtons[0]);
    expect(followButtons[0]).toHaveTextContent('Following');
    // Sort by Top reorders the sample posts by reaction count, locally.
    await user.selectOptions(screen.getByLabelText('Sort by'), 'Top');
    await waitFor(() => {
      const text = container.textContent ?? '';
      expect(text.indexOf('Photography Society: winter campus walk')).toBeLessThan(text.indexOf('Final-year project update'));
    });
    // The composer modal prepends a local post and never calls the API.
    await user.click(screen.getByRole('button', { name: 'Start a post' }));
    expect(await screen.findByRole('dialog', { name: 'Create a post' })).toBeInTheDocument();
    await user.type(screen.getByLabelText('Post title'), 'My first post');
    await user.type(screen.getByLabelText('Post body'), 'Hello, department!');
    await user.click(within(screen.getByRole('dialog', { name: 'Create a post' })).getByRole('button', { name: 'Post' }));
    expect(await screen.findByText('My first post')).toBeInTheDocument();
    expect(calls.some((c) => c.url === '/api/posts' && c.method === 'POST')).toBe(false);
  }, 30_000);

  it('collapses to a 68px icon rail — not hidden — when the header toggle fires', async () => {
    route = (url) => {
      if (url.startsWith('/api/feed')) return json({ items: [], nextCursor: null });
      if (url === '/api/community/context') return json({});
      if (url === '/api/profiles/me') return json(PROFILE);
      if (url === '/api/bookmarks') return json([]);
      if (url === '/api/events/upcoming') return json([]);
      return defaultRoute(url);
    };
    const { container } = renderFeed();
    expect(await screen.findByText('Ayesha Rahman')).toBeInTheDocument();
    fireEvent(window, new CustomEvent('unilink:toggle-sidebar'));
    expect(container.querySelector('.ch-layout')?.classList.contains('collapsed')).toBe(true);
    // All five section links stay reachable in the rail.
    for (const name of ['Home', 'My Network', 'Events', 'Saved']) {
      expect(screen.getByRole('link', { name })).toBeInTheDocument();
    }
  });

  it('collapses and expands via the toggle inside the sidebar navigation card', async () => {
    route = (url) => {
      if (url.startsWith('/api/feed')) return json({ items: [], nextCursor: null });
      if (url === '/api/community/context') return json({});
      if (url === '/api/profiles/me') return json(PROFILE);
      if (url === '/api/bookmarks') return json([]);
      if (url === '/api/events/upcoming') return json([]);
      return defaultRoute(url);
    };
    const user = userEvent.setup();
    const { container } = renderFeed();
    expect(await screen.findByText('Ayesha Rahman')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Collapse sidebar' }));
    expect(container.querySelector('.ch-layout')?.classList.contains('collapsed')).toBe(true);
    await user.click(screen.getByRole('button', { name: 'Expand sidebar' }));
    expect(container.querySelector('.ch-layout')?.classList.contains('collapsed')).toBe(false);
  });

  it('handles an unavailable profile gracefully, without inventing details', async () => {
    route = (url) => {
      if (url === '/api/public/sectors') return json([]);
      if (url.startsWith('/api/feed')) return json({ items: [], nextCursor: null });
      if (url === '/api/community/context') return json({});
      if (url === '/api/profiles/me') return json({ message: 'down' }, 500);
      if (url === '/api/bookmarks') return json([]);
      if (url === '/api/events/upcoming') return json([]);
      return defaultRoute(url);
    };
    renderFeed();
    expect(await screen.findByText('Your profile')).toBeInTheDocument();
    expect(screen.getByText('Profile details are unavailable right now.')).toBeInTheDocument();
    expect(screen.queryByText(/Prototype Preview/)).toBeNull();
  });
});

describe('Community Home — reactions tray and threaded comments', () => {
  it('hovering Like opens the tray, selecting Insightful updates the button and summary; replies nest under a comment', async () => {
    route = (url) => defaultRoute(url); // feed unmocked -> Prototype Preview samples
    const user = userEvent.setup();
    renderFeed();
    expect(await screen.findByText(/Final-year project update/)).toBeInTheDocument();
    // Hover the first post's Like button: the tray appears after ~300ms.
    const likeButtons = screen.getAllByRole('button', { name: 'Like' });
    await user.hover(likeButtons[0]);
    const insightful = await screen.findByRole('button', { name: 'React: Insightful' }, { timeout: 3000 });
    await user.click(insightful);
    const active = await screen.findByRole('button', { name: /Insightful/ });
    expect(active).toHaveAttribute('aria-pressed', 'true');
    expect(active).toHaveTextContent('💡 Insightful');
    // Summary reflects the added reaction (24 -> 25) with emoji.
    expect(screen.getByText(/💡👍 25/)).toBeInTheDocument();
    // Comments: reply to the first comment and see it nested.
    const commentButtons = screen.getAllByRole('button', { name: /Comment/ });
    await user.click(commentButtons[0]);
    expect(await screen.findByText(/The gate board view is a great touch/)).toBeInTheDocument();
    await user.click(screen.getAllByRole('button', { name: 'Reply' })[0]);
    await user.type(screen.getByLabelText('Reply to Tanvir Hasan'), 'Interpolation sounds neat — please share the code.');
    const postButtons = screen.getAllByRole('button', { name: 'Post' });
    await user.click(postButtons[postButtons.length - 1]);
    expect(await screen.findByText('Interpolation sounds neat — please share the code.')).toBeInTheDocument();
  }, 30000);
});
