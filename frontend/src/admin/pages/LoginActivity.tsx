import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { apiGet, errorMessage } from '../../api/client';
import { Button, PageHeader, Skeleton, ErrorState, EmptyState } from '../../design/primitives';
import { fmtDateTime } from '../adminUi';

/**
 * Login Activity (D-204 B): every sign-in attempt the backend records
 * (GET /admin/login-attempts, paginated) — email, IP address, success,
 * timestamp. University moderators see only attempts for students of
 * their own university (enforced server-side). Passwords, OTPs and
 * tokens are never recorded and never shown here.
 */
const PAGE_SIZE = 50;
interface LoginAttemptPage { content: any[]; page: number; size: number; totalElements: number; totalPages: number }

export default function LoginActivity() {
  const [page, setPage] = useState(0);
  const q = useQuery({
    queryKey: ['admin-login-attempts', page],
    queryFn: () => apiGet<LoginAttemptPage>(`/admin/login-attempts?page=${page}&size=${PAGE_SIZE}`),
    retry: false,
  });
  const data = q.data;
  const rows = data?.content ?? [];
  const totalPages = Math.max(1, data?.totalPages ?? 1);
  return (
    <div>
      <PageHeader eyebrow="Admin Portal" title="Login Activity" lead="Every sign-in attempt, newest first — email, IP address, whether it succeeded, and when. Use it to spot brute-force or credential-stuffing patterns against student accounts." />
      {q.isPending ? <Skeleton /> : q.isError ? <ErrorState message={errorMessage(q.error)} onRetry={() => q.refetch()} />
        : rows.length === 0 ? <EmptyState title="No login attempts" body="No sign-in attempts have been recorded yet." />
        : <>
          <div className="adm-table-wrap">
            <table className="table" aria-label="Login attempts">
              <thead><tr><th>Email</th><th>IP address</th><th>Result</th><th>Time</th></tr></thead>
              <tbody>{rows.map((a: any, i: number) => (
                <tr key={a.id ?? `${a.email}-${a.createdAt}-${i}`}>
                  <td>{a.email}</td>
                  <td>{a.ip || '—'}</td>
                  <td>{a.success ? 'Success' : 'Failed'}</td>
                  <td>{fmtDateTime(a.createdAt)}</td>
                </tr>
              ))}</tbody>
            </table>
          </div>
          <div className="adm-pager">
            <Button disabled={page === 0} onClick={() => setPage((p) => p - 1)}>← Previous</Button>
            <span className="secondary">Page {page + 1} of {totalPages} · {data?.totalElements ?? 0} attempts</span>
            <Button disabled={page >= totalPages - 1} onClick={() => setPage((p) => p + 1)}>Next →</Button>
          </div>
        </>}
    </div>
  );
}
