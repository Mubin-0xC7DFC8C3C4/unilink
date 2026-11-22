import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { AdminList } from '../AdminList';
import { apiPost, errorMessage } from '../../api/client';
import { Button } from '../../design/primitives';
import { ConfirmDialog, StatusBadge, fmtDateTime } from '../adminUi';

/**
 * Identity change requests: GET /admin/identity-changes, decided with
 * POST /admin/identity-changes/{id}/decide {action: APPROVE|REJECT,
 * reason}. A reason is required for every decision.
 */
export default function IdentityChanges() {
  const qc = useQueryClient();
  const [confirm, setConfirm] = useState<{ id: number; action: 'APPROVE' | 'REJECT'; name: string } | null>(null);
  const decide = useMutation({
    mutationFn: ({ id, action, reason }: { id: number; action: string; reason: string }) => apiPost(`/admin/identity-changes/${id}/decide`, { action, reason }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['admin-identity-changes'] }); setConfirm(null); },
  });
  return (
    <div>
      <AdminList<any> eyebrow="Admin Portal" title="Identity Changes" lead="Students sometimes register with the wrong university, department or student ID. Review each requested change here — approving updates the student's record."
        queryKey={['admin-identity-changes']} path="/admin/identity-changes" emptyTitle="No identity change requests" emptyBody="New requests will appear here."
        render={(items) => (
          <div className="adm-table-wrap"><table className="table">
            <thead><tr><th>Student</th><th>Requested change</th><th>Submitted</th><th>Status</th><th>Actions</th></tr></thead>
            <tbody>{items.map((r: any) => (
              <tr key={r.id}>
                <td><strong>{r.fullName}</strong><br /><span className="secondary">{r.email}</span></td>
                <td>{[r.newStudentId ? `Student ID → ${r.newStudentId}` : null, r.newUniversityId ? `University → #${r.newUniversityId}` : null, r.newDepartmentId ? `Department → #${r.newDepartmentId}` : null].filter(Boolean).join(' · ') || '—'}</td>
                <td>{fmtDateTime(r.createdAt)}</td>
                <td><StatusBadge status={r.status} /></td>
                <td>{r.status === 'PENDING' ? <span style={{ display: 'flex', gap: 6 }}>
                  <Button onClick={() => setConfirm({ id: r.id, action: 'APPROVE', name: r.fullName })}>Approve</Button>
                  <Button onClick={() => setConfirm({ id: r.id, action: 'REJECT', name: r.fullName })}>Reject</Button>
                </span> : <span className="secondary">{r.decidedAt ? fmtDateTime(r.decidedAt) : ''}</span>}</td>
              </tr>
            ))}</tbody>
          </table></div>
        )} />
      {decide.isError && <p role="alert" className="field-error">{errorMessage(decide.error)}</p>}
      {confirm && (
        <ConfirmDialog title={`${confirm.action === 'APPROVE' ? 'Approve' : 'Reject'} identity change — ${confirm.name}`}
          message={<p>Are you sure? The decision is recorded in the activity log.</p>}
          confirmLabel={confirm.action === 'APPROVE' ? 'Approve change' : 'Reject change'}
          reasonLabel="Reason (required)" busy={decide.isPending}
          onCancel={() => setConfirm(null)}
          onConfirm={(reason) => decide.mutate({ id: confirm.id, action: confirm.action, reason })} />
      )}
    </div>
  );
}
