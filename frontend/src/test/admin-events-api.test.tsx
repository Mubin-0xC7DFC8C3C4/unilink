import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import CreateEvent from '../admin/pages/CreateEvent';
import EventsPage from '../features/events/EventsPage';

/** Admin events are server-persisted (2026-11-22): Publish and Save as
 *  Draft both POST to /api/admin/events (drafts carry status DRAFT),
 *  and the student Events page renders exactly what the API returns. */

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
const fetchMock = vi.fn();
const posts: any[] = [];
function qc() { return new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } }); }

beforeEach(() => {
  posts.length = 0;
  vi.stubGlobal('fetch', fetchMock);
  fetchMock.mockImplementation(async (input: unknown, init?: any) => {
    const url = String(input); const method = (init?.method ?? 'GET').toUpperCase();
    if (url === '/api/csrf') return json({});
    if (url === '/api/public/sectors') return json([{ id: 6, name: 'Competitive Programming' }]);
    if (url === '/api/public/universities') return json([{ id: 1, name: 'Metropolitan University' }]);
    if (url === '/api/admin/events' && method === 'POST') { posts.push(JSON.parse(init.body)); return json({ id: 42 }, 201); }
    if (url === '/api/events/upcoming') return json([{ id: 7, title: 'Server Fest', eventDeadline: '2026-12-20T10:00:00', location: 'Main Auditorium', visibility: 'NATIONAL' }]);
    if (url.startsWith('/api/events')) return json([]);
    return json({ message: 'no mock route for ' + url }, 404);
  });
});
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); });

describe('admin event creation (API)', () => {
  it('Save as Draft posts status DRAFT to /api/admin/events', async () => {
    const user = userEvent.setup();
    render(<QueryClientProvider client={qc()}><MemoryRouter><CreateEvent /></MemoryRouter></QueryClientProvider>);
    await user.type(await screen.findByLabelText('Event Title'), 'Winter Fest');
    await user.type(screen.getByLabelText('Start Date'), '2026-12-20');
    await user.click(screen.getByRole('button', { name: 'Save as Draft' }));
    await vi.waitFor(() => expect(posts).toHaveLength(1));
    expect(posts[0]).toMatchObject({ title: 'Winter Fest', status: 'DRAFT' });
  });

  it('Publish posts status PUBLISHED with the composed body', async () => {
    const user = userEvent.setup();
    render(<QueryClientProvider client={qc()}><MemoryRouter><CreateEvent /></MemoryRouter></QueryClientProvider>);
    await user.type(await screen.findByLabelText('Event Title'), 'Hackathon Finals');
    await user.type(screen.getByLabelText('Full Description'), 'A weekend hackathon');
    await user.type(screen.getByLabelText('Start Date'), '2026-12-20');
    await user.click(screen.getByRole('button', { name: 'Publish Event' }));
    await vi.waitFor(() => expect(posts).toHaveLength(1));
    expect(posts[0]).toMatchObject({ title: 'Hackathon Finals', status: 'PUBLISHED' });
    expect(posts[0].body).toContain('A weekend hackathon');
    expect(posts[0].deadline).toBe('2026-12-20T09:00:00');
  });

  it('student Events page renders server events and has no create controls', async () => {
    render(<QueryClientProvider client={qc()}><MemoryRouter><EventsPage /></MemoryRouter></QueryClientProvider>);
    expect(await screen.findByText('Server Fest')).toBeInTheDocument();
    expect(screen.queryByText(/create event/i)).not.toBeInTheDocument();
  });
});
