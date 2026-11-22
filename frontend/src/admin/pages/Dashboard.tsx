import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { apiGet, errorMessage } from '../../api/client';
import { PageHeader, Skeleton, ErrorState, EmptyState } from '../../design/primitives';
import { fmtDateTime, humanStatus } from '../adminUi';

/**
 * Executive dashboard. Every number comes from the existing admin
 * endpoints: GET /admin/dashboard (scoped counts), GET /admin/analytics
 * (account states + 30-day registrations) and GET /admin/audit-logs
 * (recent administrative activity). Nothing is estimated or invented;
 * a metric the endpoints do not provide is simply not shown.
 */
export default function Dashboard() {
  const dash = useQuery({ queryKey: ['admin-dashboard'], queryFn: () => apiGet<any>('/admin/dashboard'), retry: false });
  const analytics = useQuery({ queryKey: ['admin-analytics'], queryFn: () => apiGet<any>('/admin/analytics'), retry: false });
  const audit = useQuery({ queryKey: ['admin-audit', 'recent'], queryFn: () => apiGet<any[]>('/admin/audit-logs?limit=8'), retry: false });

  const d = dash.data ?? {};
  const byState: { state: string; c: number }[] = Array.isArray(analytics.data?.usersByState) ? analytics.data.usersByState : [];
  const totalStudents = byState.reduce((n, r) => n + (Number(r.c) || 0), 0);
  const suspended = byState.find((r) => r.state === 'SUSPENDED')?.c ?? 0;
  const perDay: { d: string; c: number }[] = Array.isArray(analytics.data?.usersPerDay30) ? analytics.data.usersPerDay30 : [];
  const maxDay = Math.max(1, ...perDay.map((r) => Number(r.c) || 0));
  const recent = Array.isArray(audit.data) ? audit.data : [];

  const stats = [
    { label: 'Total Students', value: analytics.isSuccess ? String(totalStudents) : '—' },
    { label: 'Verified Students', value: d.totalUsers != null ? String(d.totalUsers) : '—' },
    { label: 'Pending Verifications', value: d.pendingVerifications != null ? String(d.pendingVerifications) : '—' },
    { label: 'Suspended Students', value: analytics.isSuccess ? String(suspended) : '—' },
    { label: 'Pending Reports', value: d.reportsAwaitingReview != null ? String(d.reportsAwaitingReview) : '—' },
    { label: 'Upcoming Events', value: d.activeEvents != null ? String(d.activeEvents) : '—' },
    { label: 'Pending Appeals', value: d.pendingAppeals != null ? String(d.pendingAppeals) : '—' },
    { label: 'New Students Today', value: d.newUsersToday != null ? String(d.newUsersToday) : '—' },
  ];

  return (
    <div>
      <PageHeader eyebrow="Admin Portal" title="Dashboard" lead="The current state of the platform. Moderators see their own university only; Platform Admins see everything." />
      {dash.isPending ? <Skeleton /> : dash.isError ? <ErrorState message={errorMessage(dash.error)} onRetry={() => dash.refetch()} /> : (
        <>
          <div className="adm-cards">
            {stats.map((s) => <div key={s.label} className="adm-stat"><div className="adm-stat-value">{s.value}</div><div className="adm-stat-label">{s.label}</div></div>)}
          </div>
          <div className="split">
            <section className="card-group" aria-label="Registrations in the last 30 days">
              <h2 className="title">Student registrations — last 30 days</h2>
              {analytics.isPending ? <Skeleton /> : perDay.length === 0
                ? <p className="secondary">No registrations were recorded in the last 30 days.</p>
                : <>
                  <div style={{ display: 'flex', gap: 3, alignItems: 'flex-end', height: 90 }} aria-hidden="true">
                    {perDay.map((r) => <div key={String(r.d)} title={`${r.d}: ${r.c}`} style={{ flex: 1, minWidth: 4, height: `${Math.max(6, (Number(r.c) / maxDay) * 100)}%`, background: 'var(--primary)', borderRadius: '3px 3px 0 0', opacity: 0.85 }} />)}
                  </div>
                  <p className="secondary" style={{ marginTop: 8 }}>{perDay.reduce((n, r) => n + (Number(r.c) || 0), 0)} new students in this period. Verification decisions take {analytics.data?.verificationTurnaroundHours != null ? `${Math.round(Number(analytics.data.verificationTurnaroundHours))} hours` : '—'} on average.</p>
                </>}
              <p style={{ marginTop: 10 }}><Link to="/admin/verifications">Review the verification queue →</Link></p>
            </section>
            <section className="card-group" aria-label="Recent activity">
              <h2 className="title">Recent activity</h2>
              {audit.isPending ? <Skeleton /> : recent.length === 0
                ? <EmptyState title="No activity yet" body="Administrative actions will appear here as they happen." />
                : <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: 10 }}>
                  {recent.map((a) => (
                    <li key={a.id} style={{ borderBottom: '1px solid var(--border)', paddingBottom: 8 }}>
                      <p style={{ margin: 0 }}><strong>{humanStatus(a.action) === a.action ? a.action.replaceAll('_', ' ') : a.action}</strong> · {a.targetType} {a.targetId}</p>
                      <p className="secondary" style={{ margin: '2px 0 0' }}>{fmtDateTime(a.createdAt)}{a.reason ? ` — ${a.reason}` : ''}</p>
                    </li>
                  ))}
                </ul>}
              <p style={{ marginTop: 10 }}><Link to="/admin/audit">Open the full activity log →</Link></p>
            </section>
          </div>
        </>
      )}
    </div>
  );
}
