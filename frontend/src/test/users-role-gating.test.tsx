import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import userEvent from '@testing-library/user-event';
import { AuthProvider } from '../app/auth';
import Users from '../admin/pages/Users';

/** D-118: SUSPEND/BAN buttons are Platform Admin only (server enforces 403
 *  too); moderators see only the actions they may perform. Mocked-fetch
 *  harness, same pattern as worker-d.test.tsx — nothing hits a real backend. */

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const fetchMock = vi.fn();
function qc() { return new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } }); }

function renderAs(role: string) {
  fetchMock.mockImplementation(async (input: unknown) => {
    const url = String(input);
    if (url === '/api/csrf') return json({});
    if (url === '/api/admin/auth/me') return json({ adminId: 1, email: 'admin@example.com', role, mfaVerified: true });
    if (url === '/api/admin/users/1') return json({ id: 1, fullName: 'Stu Dent', email: 'stu@example.com', state: 'VERIFIED' });
    if (url.startsWith('/api/admin/users')) return json([{ id: 1, fullName: 'Stu Dent', email: 'stu@example.com', state: 'VERIFIED' }]);
    return json({ message: 'no mock route for ' + url }, 404);
  });
  return render(
    <QueryClientProvider client={qc()}>
      <AuthProvider restorePath="/admin/auth/me" idleMinutes={105}>
        <MemoryRouter><Users /></MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>
  );
}

beforeEach(() => { vi.stubGlobal('fetch', fetchMock); });
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); });

describe('Users role gating (D-118)', () => {
  it('moderator sees recommend/revoke/reset actions but no Suspend or Ban', async () => {
    const user = userEvent.setup();
    renderAs('UNIVERSITY_MODERATOR');
    await waitFor(() => expect(screen.getByText('Stu Dent')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: 'View' }));
    await screen.findByRole('button', { name: 'Recommend suspension' });
    expect(screen.queryByText('Suspend account')).toBeNull();
    expect(screen.queryByText('Ban account')).toBeNull();
    expect(screen.getByText('Recommend suspension')).toBeInTheDocument();
    expect(screen.getByText('Revoke verification')).toBeInTheDocument();
    expect(screen.getByText('Force password reset')).toBeInTheDocument();
  });

  it('Platform Admin sees Suspend and Ban', async () => {
    const user = userEvent.setup();
    renderAs('PLATFORM_ADMIN');
    await waitFor(() => expect(screen.getByText('Stu Dent')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: 'View' }));
    expect(await screen.findByText('Suspend account')).toBeInTheDocument();
    expect(screen.getByText('Ban account')).toBeInTheDocument();
  });
});
