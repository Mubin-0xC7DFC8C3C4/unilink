import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { apiGet, apiPost, errorMessage } from '../../api/client';
import { Button, PageHeader, Skeleton, ErrorState, EmptyState } from '../../design/primitives';
import { ConfirmDialog, StatusBadge, Tabs, fmtDateTime } from '../adminUi';

/**
 * Reports & Moderation and Post Management share one data source —
 * GET /admin/moderation-queue (reported and auto-hidden content) — and
 * one decision endpoint, POST /admin/moderation/decide {targetType,
 * targetId, action, reason} with actions HIDE / REMOVE / RESTORE /
 * EDIT_REQUEST and a mandatory reason. "Reports" shows the whole
 * queue; "Post Management" shows the post and event reports with the
 * same decision controls. There is no separate all-posts listing in
 * the admin API, so unreported posts are not listed here.
 */
export default function Moderation() {
  const [params, setParams] = useSearchParams();
  const view = params.get('view') === 'posts' ? 'posts' : 'reports';
  const qc = useQueryClient();
  const [confirm, setConfirm] = useState<{ item: any; action: string } | null>(null);
  const q = useQuery({ queryKey: ['admin-moderation'], queryFn: () => apiGet<any[]>('/admin/moderation-queue'), retry: false });
  const decide = useMutation({
    mutationFn: ({ targetType, targetId, action, reason }: { targetType: string; targetId: number; action: string; reason: string }) => apiPost('/admin/moderation/decide', { targetType, targetId, action, reason }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['admin-moderation'] }); setConfirm(null); },
  });
  const all = Array.isArray(q.data) ? q.data : [];
  const items = view === 'posts' ? all.filter((m) => m.targetType === 'POST' || m.targetType === 'EVENT') : all;
  const ACTIONS = [
    { key: 'HIDE', label: 'Hide content' },
    { key: 'REMOVE', label: 'Remove content' },
    { key: 'RESTORE', label: 'Restore content' },
    { key: 'EDIT_REQUEST', label: 'Request edit' },
  ];
  const targetLabel = (m: any) => m.targetType === 'POST' ? `Post #${m.targetId}` : m.targetType === 'EVENT' ? `Event #${m.targetId}` : m.targetType === 'COMMENT' ? `Comment #${m.targetId}` : `${m.targetType} #${m.targetId}`;

  return (
    <div>
      <PageHeader eyebrow="Admin Portal" title={view === 'posts' ? 'Post Management' : 'Reports & Moderation'}
        lead={view === 'posts'
          ? 'Posts and events that students reported or that were hidden automatically. Decide each one with a reason — decisions are recorded in the activity log.'
          : 'Every reported post, comment and profile in your scope, newest reports first. Review a report and record a moderation decision with a reason.'} />
      <Tabs tabs={[{ key: 'reports', label: 'Reports queue', count: all.length }, { key: 'posts', label: 'Reported posts', count: all.filter((m) => m.targetType === 'POST' || m.targetType === 'EVENT').length }]}
        active={view} onChange={(k) => setParams(k === 'posts' ? { view: 'posts' } : {})} />
      {q.isPending ? <Skeleton /> : q.isError ? <ErrorState message={errorMessage(q.error)} onRetry={() => q.refetch()} />
        : items.length === 0 ? <EmptyState title="Queue is clear" body="Nothing is waiting for a moderation decision." />
        : <div className="adm-table-wrap"><table className="table">
          <thead><tr><th>Reported content</th><th>Reporting student</th><th>Report reason</th><th>Report date</th><th>Current status</th><th>Actions</th></tr></thead>
          <tbody>{items.map((m: any) => (
            <tr key={m.id}>
              <td><strong>{targetLabel(m)}</strong></td>
              <td>Student #{m.reporterId}</td>
              <td>{m.reason || '—'}</td>
              <td>{fmtDateTime(m.createdAt)}</td>
              <td><StatusBadge status={m.status} /></td>
              <td><span style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                {ACTIONS.map((a) => <Button key={a.key} onClick={() => setConfirm({ item: m, action: a.key })}>{a.label}</Button>)}
              </span></td>
            </tr>
          ))}</tbody>
        </table></div>}
      {decide.isError && <p role="alert" className="field-error">{errorMessage(decide.error)}</p>}
      {confirm && (
        <ConfirmDialog title={`${confirm.action.replace('_', ' ')} — ${targetLabel(confirm.item)}`}
          message={<p>Report reason: {confirm.item.reason || '—'}. The decision and your reason are recorded in the activity log.</p>}
          confirmLabel="Record decision" reasonLabel="Decision reason (required)" busy={decide.isPending}
          onCancel={() => setConfirm(null)}
          onConfirm={(reason) => decide.mutate({ targetType: confirm.item.targetType, targetId: confirm.item.targetId, action: confirm.action, reason })} />
      )}
    </div>
  );
}
