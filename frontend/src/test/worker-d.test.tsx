import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import PublicAppeal from '../features/auth/PublicAppeal';
import Verifications from '../admin/pages/Verifications';

/** Worker D (Phase 3): I-13 public suspension-appeal page and I-17
 *  duplicate-phone badge. Same mocked-fetch harness as fix-worker.test.tsx —
 *  nothing here hits a real backend. */

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

interface Call { url: string; method: string; body: unknown }
let calls: Call[] = [];
let route: (url: string, method: string, body: unknown) => Response | undefined;

const fetchMock = vi.fn();
function qc() { return new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } }); }
function renderWithProviders(ui: React.ReactNode, initialEntries: string[] = ['/']) {
  return render(<QueryClientProvider client={qc()}><MemoryRouter initialEntries={initialEntries}>{ui}</MemoryRouter></QueryClientProvider>);
}

beforeEach(() => {
  calls = [];
  route = () => undefined;
  fetchMock.mockImplementation(async (input: unknown, init?: RequestInit) => {
    const url = String(input);
    const method = init?.method || 'GET';
    calls.push({ url, method, body: init?.body });
    return route(url, method, init?.body) ?? json({ message: 'no mock route for ' + url }, 404);
  });
  vi.stubGlobal('fetch', fetchMock);
});
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); });

describe('I-13 — public suspension-appeal page', () => {
  it('shows an error when the link has no token and posts nothing', () => {
    renderWithProviders(<PublicAppeal />, ['/appeals/public']);
    expect(screen.getByText(/No appeal link was provided/i)).toBeInTheDocument();
    expect(calls.filter((c) => c.method === 'POST')).toHaveLength(0);
  });

  it('submits the reason with the token and shows confirmation', async () => {
    route = (url, method) => {
      if (url === '/api/appeals/public?token=abc123' && method === 'POST') return json({ status: 'PENDING' });
      return undefined;
    };
    renderWithProviders(<PublicAppeal />, ['/appeals/public?token=abc123']);
    await userEvent.type(screen.getByLabelText(/Why should the suspension be reconsidered/i), 'My account was flagged by mistake');
    await userEvent.click(screen.getByRole('button', { name: /Submit appeal/i }));
    await waitFor(() => expect(screen.getByText(/Appeal submitted/i)).toBeInTheDocument());
    const post = calls.find((c) => c.method === 'POST');
    expect(post?.url).toBe('/api/appeals/public?token=abc123');
    const body = JSON.parse(String(post?.body));
    expect(body).toEqual({ targetType: 'USER', targetId: null, reason: 'My account was flagged by mistake' });
  });

  it('keeps the submit button disabled until a reason is typed', () => {
    renderWithProviders(<PublicAppeal />, ['/appeals/public?token=abc123']);
    expect(screen.getByRole('button', { name: /Submit appeal/i })).toBeDisabled();
  });

  it('shows the server error when the token is invalid', async () => {
    route = (url, method) => {
      if (url === '/api/appeals/public?token=stale' && method === 'POST')
        return json({ code: 'INVALID_TOKEN', message: 'This appeal link is invalid or has expired' }, 400);
      return undefined;
    };
    renderWithProviders(<PublicAppeal />, ['/appeals/public?token=stale']);
    await userEvent.type(screen.getByLabelText(/Why should the suspension be reconsidered/i), 'please review');
    await userEvent.click(screen.getByRole('button', { name: /Submit appeal/i }));
    await waitFor(() => expect(screen.getByText(/invalid or has expired/i)).toBeInTheDocument());
  });
});

describe('I-17 — duplicate-phone badge in the verification queue', () => {
  const queue = [
    { id: 1, fullName: 'Dup A', email: 'a@example.com', university: 'MU', studentId: 'DUPA', status: 'PENDING_REVIEW', duplicatePhone: true, previousAttempts: 0 },
    { id: 2, fullName: 'Solo B', email: 'b@example.com', university: 'MU', studentId: 'DUPB', status: 'PENDING_REVIEW', duplicatePhone: false, previousAttempts: 0 },
  ];
  beforeEach(() => {
    route = (url) => {
      if (url.startsWith('/api/admin/verification-queue')) return json(queue);
      return undefined;
    };
  });

  it('shows a Duplicate phone badge only on the flagged row', async () => {
    renderWithProviders(<Verifications />);
    await waitFor(() => expect(screen.getByText('Dup A')).toBeInTheDocument());
    const badges = screen.getAllByText('Duplicate phone');
    expect(badges).toHaveLength(1);
    expect(screen.getByText('Solo B').closest('tr')).not.toHaveTextContent('Duplicate phone');
  });

  it('shows the duplicate-phone warning in the review pane', async () => {
    renderWithProviders(<Verifications />);
    await waitFor(() => expect(screen.getByText('Dup A')).toBeInTheDocument());
    await userEvent.click(screen.getAllByRole('button', { name: /Review/i })[0]);
    await waitFor(() => expect(screen.getByText(/Duplicate phone: another account shares this applicant's phone number/i)).toBeInTheDocument());
  });
});
