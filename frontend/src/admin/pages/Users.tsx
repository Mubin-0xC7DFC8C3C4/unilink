import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { apiGet, apiPost, errorMessage } from '../../api/client';
import { Button, Modal, PageHeader, Skeleton, ErrorState, EmptyState } from '../../design/primitives';
import { useAuth } from '../../app/auth';
import { ConfirmDialog, StatusBadge, Tabs, fmtDateTime } from '../adminUi';

const PAGE_SIZE = 10;
const STATE_TABS = [
  { key: 'ALL', label: 'All' },
  { key: 'VERIFIED', label: 'Verified' },
  { key: 'REGISTERED', label: 'Pending' },
  { key: 'SUSPENDED', label: 'Suspended' },
  { key: 'BANNED', label: 'Banned' },
];

/**
 * All Students. Data: GET /admin/users?q= (scope-enforced server-side).
 * Account actions: POST /admin/users/{id}/{SUSPEND|BAN|REACTIVATE|
 * REVOKE|FORCE_RESET} with a mandatory reason, each behind a
 * confirmation dialog. SUSPEND/BAN are Platform Admin only (the server
 * returns 403 for moderators); moderators instead recommend suspension
 * (POST /admin/users/{id}/recommend-suspension), which a Platform Admin
 * decides from the recommendations list on this page.
 */
export default function Users() {
  const [params] = useSearchParams();
  const [search, setSearch] = useState(params.get('q') ?? '');
  const [tab, setTab] = useState('ALL');
  const [uniFilter, setUniFilter] = useState('');
  const [deptFilter, setDeptFilter] = useState('');
  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [confirm, setConfirm] = useState<{ action: string; user: any } | null>(null);
  const [resetResult, setResetResult] = useState<{ id: string | number; temp: string } | null>(null);
  const qc = useQueryClient();
  const { user } = useAuth();
  const isPlatformAdmin = user?.role === 'PLATFORM_ADMIN';

  const q = useQuery({ queryKey: ['admin-users', search], queryFn: () => apiGet<any[]>(`/admin/users${search ? `?q=${encodeURIComponent(search)}` : ''}`), retry: false });
  const unis = useQuery({ queryKey: ['public-universities'], queryFn: () => apiGet<any[]>('/public/universities'), retry: false });
  const depts = useQuery({ queryKey: ['public-departments', uniFilter], queryFn: () => apiGet<any[]>(`/public/departments?universityId=${uniFilter}`), retry: false, enabled: !!uniFilter });
  const detail = useQuery({ queryKey: ['admin-user', selectedId], queryFn: () => apiGet<any>(`/admin/users/${selectedId}`), retry: false, enabled: selectedId != null });
  // D-204 (D): per-user activity — audit rows for this student (profile
  // and photo/cover updates included) plus their login attempts.
  const activityQ = useQuery({ queryKey: ['admin-user-activity', selectedId], queryFn: () => apiGet<any>(`/admin/users/${selectedId}/activity`), retry: false, enabled: selectedId != null });
  const recs = useQuery({ queryKey: ['admin-suspension-recs'], queryFn: () => apiGet<any[]>('/admin/suspension-recommendations'), retry: false, enabled: isPlatformAdmin });

  const uniName = useMemo(() => { const m = new Map<number, string>(); (unis.data ?? []).forEach((u: any) => m.set(u.id, u.name)); return m; }, [unis.data]);

  const all = Array.isArray(q.data) ? q.data : [];
  // Department names for the rows actually shown: the catalogue is
  // per-university, so fetch the departments of every university that
  // appears in the list and merge them into one name map.
  const uniIdsInList = [...new Set(all.map((u) => u.universityId).filter(Boolean))].join(',');
  const deptMapQ = useQuery({
    queryKey: ['dept-names', uniIdsInList], enabled: !!uniIdsInList, retry: false,
    queryFn: async () => { const lists = await Promise.all(uniIdsInList.split(',').map((id) => apiGet<any[]>(`/public/departments?universityId=${id}`))); return lists.flat(); },
  });
  const deptName = useMemo(() => { const m = new Map<number, string>(); (deptMapQ.data ?? []).forEach((d: any) => m.set(d.id, d.name)); (depts.data ?? []).forEach((d: any) => m.set(d.id, d.name)); return m; }, [deptMapQ.data, depts.data]);
  const filtered = all.filter((u) =>
    (tab === 'ALL' || u.state === tab)
    && (!uniFilter || String(u.universityId) === uniFilter)
    && (!deptFilter || String(u.departmentId) === deptFilter));
  const pageItems = filtered.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));

  const invalidate = () => { qc.invalidateQueries({ queryKey: ['admin-users'] }); qc.invalidateQueries({ queryKey: ['admin-user'] }); };
  const act = useMutation({
    mutationFn: ({ id, action, reason }: { id: string | number; action: string; reason: string }) => apiPost<{ temporaryPassword?: string }>(`/admin/users/${id}/${action}`, { reason }),
    onSuccess: (d, v) => { invalidate(); setConfirm(null); if (d.temporaryPassword) setResetResult({ id: v.id, temp: d.temporaryPassword }); },
  });
  const recommend = useMutation({
    mutationFn: ({ id, reason }: { id: string | number; reason: string }) => apiPost(`/admin/users/${id}/recommend-suspension`, { reason }),
    onSuccess: () => { setConfirm(null); qc.invalidateQueries({ queryKey: ['admin-suspension-recs'] }); },
  });
  const decideRec = useMutation({
    mutationFn: ({ id, action, reason }: { id: number; action: string; reason: string }) => apiPost(`/admin/suspension-recommendations/${id}/decide`, { action, reason }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['admin-suspension-recs'] }); invalidate(); },
  });

  const actionButtons = (u: any) => {
    const btns: { action: string; label: string }[] = [];
    if (isPlatformAdmin && u.state === 'VERIFIED') { btns.push({ action: 'SUSPEND', label: 'Suspend account' }); btns.push({ action: 'BAN', label: 'Ban account' }); }
    if (!isPlatformAdmin && u.state === 'VERIFIED') btns.push({ action: 'RECOMMEND', label: 'Recommend suspension' });
    if (u.state === 'SUSPENDED' || u.state === 'BANNED') btns.push({ action: 'REACTIVATE', label: 'Restore account' });
    if (u.state === 'VERIFIED') btns.push({ action: 'REVOKE', label: 'Revoke verification' });
    btns.push({ action: 'FORCE_RESET', label: 'Force password reset' });
    return btns;
  };

  const sel = detail.data;

  return (
    <div>
      <PageHeader eyebrow="Admin Portal" title="All Students" lead="Search and manage student accounts in your scope. Sensitive actions always ask for confirmation and a reason, and are written to the activity log." />
      <Tabs tabs={STATE_TABS.map((t) => ({ ...t, count: t.key === 'ALL' ? all.length : all.filter((u) => u.state === t.key).length }))} active={tab} onChange={(k) => { setTab(k); setPage(0); }} />
      <div className="adm-toolbar">
        <input aria-label="Search students" className="input-search grow" placeholder="Search by name, email or student ID" value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }} />
        <select aria-label="Filter by university" className="input-search" value={uniFilter} onChange={(e) => { setUniFilter(e.target.value); setDeptFilter(''); setPage(0); }}>
          <option value="">All universities</option>
          {(unis.data ?? []).map((u: any) => <option key={u.id} value={u.id}>{u.name}</option>)}
        </select>
        <select aria-label="Filter by department" className="input-search" value={deptFilter} onChange={(e) => { setDeptFilter(e.target.value); setPage(0); }} disabled={!uniFilter}>
          <option value="">All departments</option>
          {(depts.data ?? []).map((d: any) => <option key={d.id} value={d.id}>{d.name}</option>)}
        </select>
      </div>
      {q.isPending ? <Skeleton /> : q.isError ? <ErrorState message={errorMessage(q.error)} onRetry={() => q.refetch()} />
        : filtered.length === 0 ? <EmptyState title="No students found" body="No accounts match the current search and filters." />
        : <>
          <div className="adm-table-wrap">
            <table className="table">
              <thead><tr><th>Student Name</th><th>Student ID</th><th>University</th><th>Department</th><th>Email</th><th>Verification Status</th><th>Account Status</th><th>Registration Date</th><th>Actions</th></tr></thead>
              <tbody>{pageItems.map((u) => (
                <tr key={u.id}>
                  <td><strong>{u.fullName}</strong></td>
                  <td>{u.studentId || '—'}</td>
                  <td>{uniName.get(u.universityId) ?? '—'}</td>
                  <td>{deptName.get(u.departmentId) ?? '—'}</td>
                  <td>{u.email}</td>
                  <td><StatusBadge status={u.state === 'VERIFIED' ? 'VERIFIED' : u.state === 'REGISTERED' ? 'PENDING' : u.state} /></td>
                  <td>{u.state === 'SUSPENDED' ? 'Suspended' : u.state === 'BANNED' ? 'Banned' : 'Active'}</td>
                  <td>{fmtDateTime(u.createdAt)}</td>
                  <td><Button onClick={() => setSelectedId(u.id)}>View</Button></td>
                </tr>
              ))}</tbody>
            </table>
          </div>
          <div className="adm-pager">
            <Button disabled={page === 0} onClick={() => setPage((p) => p - 1)}>← Previous</Button>
            <span className="secondary">Page {page + 1} of {pageCount} · {filtered.length} students</span>
            <Button disabled={page >= pageCount - 1} onClick={() => setPage((p) => p + 1)}>Next →</Button>
          </div>
        </>}
      {act.isError && <p role="alert" className="field-error">{errorMessage(act.error)}</p>}
      {recommend.isError && <p role="alert" className="field-error">{errorMessage(recommend.error)}</p>}
      {resetResult && <p role="status">Temporary password for student {String(resetResult.id)} (shown once — share it securely, it is not stored): <strong>{resetResult.temp}</strong> <Button onClick={() => setResetResult(null)}>Dismiss</Button></p>}

      {selectedId != null && (
        <Modal title={sel ? `Student details — ${sel.fullName}` : 'Student details'} onClose={() => setSelectedId(null)}>
          {detail.isPending ? <Skeleton /> : detail.isError ? <ErrorState message={errorMessage(detail.error)} onRetry={() => detail.refetch()} /> : sel && <>
            <div className="row"><span>Student ID</span><span>{sel.studentId || '—'}</span></div>
            <div className="row"><span>Personal email</span><span>{sel.email}</span></div>
            <div className="row"><span>Phone</span><span>{sel.phone || '—'}</span></div>
            <div className="row"><span>University</span><span>{uniName.get(sel.universityId) ?? '—'}</span></div>
            <div className="row"><span>Department</span><span>{deptName.get(sel.departmentId) ?? '—'}</span></div>
            <div className="row"><span>Address</span><span>{sel.address || '—'}</span></div>
            <div className="row"><span>Verification status</span><span><StatusBadge status={sel.state} /></span></div>
            <div className="row"><span>Registered</span><span>{fmtDateTime(sel.createdAt)}</span></div>
            <h3 style={{ marginTop: 16 }}>Activity</h3>
            {activityQ.isPending ? <Skeleton /> : activityQ.isError ? <p className="secondary">Activity could not be loaded.</p> : <>
              {(activityQ.data?.auditLogs ?? []).length === 0 && (activityQ.data?.loginAttempts ?? []).length === 0
                ? <p className="secondary">No recorded activity for this student yet.</p>
                : <>
                  {(activityQ.data?.auditLogs ?? []).length > 0 && <>
                    <p style={{ margin: '8px 0 4px' }}><strong>Audit log</strong></p>
                    {(activityQ.data.auditLogs as any[]).map((a: any) => (
                      <div className="row" key={a.id}><span>{fmtDateTime(a.createdAt)}</span><span>{a.action}{a.reason ? ` — ${a.reason}` : ''}</span></div>
                    ))}
                  </>}
                  {(activityQ.data?.loginAttempts ?? []).length > 0 && <>
                    <p style={{ margin: '12px 0 4px' }}><strong>Login attempts</strong></p>
                    {(activityQ.data.loginAttempts as any[]).map((a: any, i: number) => (
                      <div className="row" key={a.id ?? i}><span>{fmtDateTime(a.createdAt)}</span><span>{a.success ? 'Success' : 'Failed'}{a.ip ? ` · ${a.ip}` : ''}</span></div>
                    ))}
                  </>}
                </>}
            </>}
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 14 }}>
              {actionButtons(sel).map((b) => (
                <Button key={b.action} variant={b.action === 'SUSPEND' || b.action === 'BAN' ? 'primary' : undefined} onClick={() => setConfirm({ action: b.action, user: sel })}>{b.label}</Button>
              ))}
            </div>
          </>}
        </Modal>
      )}

      {isPlatformAdmin && (recs.data ?? []).some((r: any) => r.status === 'PENDING') && (
        <section className="card-group" style={{ marginTop: 20 }} aria-label="Suspension recommendations">
          <h2 className="title">Suspension recommendations from moderators</h2>
          {(recs.data ?? []).filter((r: any) => r.status === 'PENDING').map((r: any) => (
            <div key={r.id} className="row"><span>Student {r.userId} — {r.reason}</span>
              <span style={{ display: 'flex', gap: 6 }}>
                <Button onClick={() => decideRec.mutate({ id: r.id, action: 'ACCEPT', reason: 'Recommendation accepted' })}>Accept & suspend</Button>
                <Button onClick={() => decideRec.mutate({ id: r.id, action: 'REJECT', reason: 'Recommendation rejected' })}>Reject</Button>
              </span>
            </div>
          ))}
          {decideRec.isError && <p role="alert" className="field-error">{errorMessage(decideRec.error)}</p>}
        </section>
      )}

      {confirm && (
        <ConfirmDialog
          title={`${confirm.action === 'RECOMMEND' ? 'Recommend suspension' : confirm.action.charAt(0) + confirm.action.slice(1).toLowerCase().replace('_', ' ')} — ${confirm.user.fullName}`}
          message={<p>Are you sure? This action is recorded in the activity log{confirm.action === 'FORCE_RESET' ? ' and signs the student out everywhere' : ''}.</p>}
          confirmLabel="Confirm"
          reasonLabel="Reason (required)"
          busy={act.isPending || recommend.isPending}
          onCancel={() => setConfirm(null)}
          onConfirm={(reason) => {
            if (confirm.action === 'RECOMMEND') recommend.mutate({ id: confirm.user.id, reason });
            else act.mutate({ id: confirm.user.id, action: confirm.action, reason });
          }}
        />
      )}
    </div>
  );
}
