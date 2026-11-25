import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiGet, apiPost, errorMessage } from '../../api/client';
import { Button, PageHeader, Skeleton, ErrorState, EmptyState } from '../../design/primitives';
import { ConfirmDialog, StatusBadge, fmtDateTime } from '../adminUi';

/**
 * All Posts (D-204 C): every student post and event in scope — not
 * just the reported ones (GET /admin/posts, paginated) — with author,
 * content, creation time, moderation status and report count.
 * HIDE / REMOVE / RESTORE reuse the moderation decision endpoint
 * (POST /admin/moderation/decide, targetType POST) with a mandatory
 * reason, exactly like the reports queue.
 */
const PAGE_SIZE = 50;
interface AdminPostPage { content: any[]; page: number; size: number; totalElements: number; totalPages: number }

export default function AllPosts() {
  const [page, setPage] = useState(0);
  const [confirm, setConfirm] = useState<{ post: any; action: string } | null>(null);
  const qc = useQueryClient();
  const q = useQuery({
    queryKey: ['admin-all-posts', page],
    queryFn: () => apiGet<AdminPostPage>(`/admin/posts?page=${page}&size=${PAGE_SIZE}`),
    retry: false,
  });
  const decide = useMutation({
    mutationFn: ({ post, action, reason }: { post: any; action: string; reason: string }) =>
      apiPost('/admin/moderation/decide', { targetType: 'POST', targetId: post.id, action, reason }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['admin-all-posts'] }); setConfirm(null); },
  });
  const data = q.data;
  const rows = data?.content ?? [];
  const totalPages = Math.max(1, data?.totalPages ?? 1);
  return (
    <div>
      <PageHeader eyebrow="Admin Portal" title="All Posts" lead="Every post in your scope — reported or not — with its author, content, moderation status and how many times it was reported. Hide, remove or restore a post with a reason; decisions are recorded in the activity log." />
      {q.isPending ? <Skeleton /> : q.isError ? <ErrorState message={errorMessage(q.error)} onRetry={() => q.refetch()} />
        : rows.length === 0 ? <EmptyState title="No posts" body="No posts have been published yet." />
        : <>
          <div className="adm-table-wrap">
            <table className="table" aria-label="All posts">
              <thead><tr><th>Author</th><th>Content</th><th>Type</th><th>Status</th><th>Reports</th><th>Created</th><th>Actions</th></tr></thead>
              <tbody>{rows.map((p: any) => (
                <tr key={p.id}>
                  <td><strong>{p.authorName || '—'}</strong><br /><span className="secondary">{p.authorEmail || ''}</span></td>
                  <td>{p.title && <strong>{p.title}</strong>}{p.title && p.body && <br />}{p.body ? (p.body.length > 180 ? `${p.body.slice(0, 180)}…` : p.body) : '—'}</td>
                  <td>{p.type || '—'}</td>
                  <td><StatusBadge status={p.status} /></td>
                  <td>{p.reportCount ?? 0}</td>
                  <td>{fmtDateTime(p.createdAt)}</td>
                  <td>
                    <span style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                      <Button onClick={() => setConfirm({ post: p, action: 'HIDE' })}>Hide</Button>
                      <Button onClick={() => setConfirm({ post: p, action: 'REMOVE' })}>Remove</Button>
                      <Button onClick={() => setConfirm({ post: p, action: 'RESTORE' })}>Restore</Button>
                    </span>
                  </td>
                </tr>
              ))}</tbody>
            </table>
          </div>
          <div className="adm-pager">
            <Button disabled={page === 0} onClick={() => setPage((p) => p - 1)}>← Previous</Button>
            <span className="secondary">Page {page + 1} of {totalPages} · {data?.totalElements ?? 0} posts</span>
            <Button disabled={page >= totalPages - 1} onClick={() => setPage((p) => p + 1)}>Next →</Button>
          </div>
        </>}
      {decide.isError && <p role="alert" className="field-error">{errorMessage(decide.error)}</p>}
      {confirm && (
        <ConfirmDialog
          title={`${confirm.action === 'HIDE' ? 'Hide post' : confirm.action === 'REMOVE' ? 'Remove post' : 'Restore post'} — ${confirm.post.title || `Post #${confirm.post.id}`}`}
          message={<p>This is recorded in the activity log.</p>}
          confirmLabel={confirm.action === 'HIDE' ? 'Hide post' : confirm.action === 'REMOVE' ? 'Remove post' : 'Restore post'}
          reasonLabel="Decision reason (required)"
          busy={decide.isPending}
          onCancel={() => setConfirm(null)}
          onConfirm={(reason) => decide.mutate({ post: confirm.post, action: confirm.action, reason })} />
      )}
    </div>
  );
}
