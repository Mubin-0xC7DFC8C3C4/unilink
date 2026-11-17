import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
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
    if (url.startsWith('/api/admin/users')) return json([{ id: 1, fullName: 'Stu Dent', email: 'stu@example.com', state: 'VERIFIED' }]);
    return json({ message: 'no mock route for ' + url }, 404);
  });
  return render(
    <QueryClientProvider client={qc()}>
      <AuthProvider restorePath="/admin/auth/me" idleMinutes={105}>
        <Users />
      </AuthProvider>
    </QueryClientProvider>
  );
}

beforeEach(() => { vi.stubGlobal('fetch', fetchMock); });
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); });

describe('Users role gating (D-118)', () => {
  it('moderator sees REACTIVATE/REVOKE/FORCE_RESET but no SUSPEND or BAN', async () => {
    renderAs('UNIVERSITY_MODERATOR');
    await waitFor(() => expect(screen.getByText('Stu Dent')).toBeInTheDocument());
    expect(screen.queryByText('SUSPEND')).toBeNull();
    expect(screen.queryByText('BAN')).toBeNull();
    expect(screen.getByText('REACTIVATE')).toBeInTheDocument();
    expect(screen.getByText('REVOKE')).toBeInTheDocument();
    expect(screen.getByText('FORCE_RESET')).toBeInTheDocument();
  });

  it('Platform Admin sees SUSPEND and BAN', async () => {
    renderAs('PLATFORM_ADMIN');
    await waitFor(() => expect(screen.getByText('Stu Dent')).toBeInTheDocument());
    expect(screen.getByText('SUSPEND')).toBeInTheDocument();
    expect(screen.getByText('BAN')).toBeInTheDocument();
  });
});
