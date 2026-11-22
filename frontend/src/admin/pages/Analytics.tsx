import { useQuery } from '@tanstack/react-query';
import { apiGet, errorMessage } from '../../api/client';
import { PageHeader, Skeleton, ErrorState } from '../../design/primitives';
import { humanStatus } from '../adminUi';

/**
 * Analytics: GET /admin/analytics — account states, registrations per
 * day (30 days), events per sector, average verification turnaround
 * and active universities. Rendered as tables and a bar strip; no
 * numbers are estimated or invented.
 */
export default function Analytics() {
  const q = useQuery({ queryKey: ['admin-analytics'], queryFn: () => apiGet<any>('/admin/analytics'), retry: false });
  const d = q.data ?? {};
  const byState: any[] = Array.isArray(d.usersByState) ? d.usersByState : [];
  const perDay: any[] = Array.isArray(d.usersPerDay30) ? d.usersPerDay30 : [];
  const perSector: any[] = Array.isArray(d.eventsPerSector) ? d.eventsPerSector : [];
  const maxDay = Math.max(1, ...perDay.map((r) => Number(r.c) || 0));
  const eventCount = perSector.reduce((n, r) => n + (Number(r.c) || 0), 0);

  return (
    <div>
      <PageHeader eyebrow="Admin Portal" title="Analytics" lead="Student growth, verification performance and event activity across your scope." />
      {q.isPending ? <Skeleton /> : q.isError ? <ErrorState message={errorMessage(q.error)} onRetry={() => q.refetch()} /> : <>
        <div className="adm-cards">
          <div className="adm-stat"><div className="adm-stat-value">{d.activeUniversities ?? '—'}</div><div className="adm-stat-label">Active universities</div></div>
          <div className="adm-stat"><div className="adm-stat-value">{d.verificationTurnaroundHours != null ? `${Math.round(Number(d.verificationTurnaroundHours))}h` : '—'}</div><div className="adm-stat-label">Average verification turnaround</div></div>
          <div className="adm-stat"><div className="adm-stat-value">{eventCount}</div><div className="adm-stat-label">Events published</div></div>
          <div className="adm-stat"><div className="adm-stat-value">{perDay.reduce((n, r) => n + (Number(r.c) || 0), 0)}</div><div className="adm-stat-label">Registrations (30 days)</div></div>
        </div>
        <div className="split">
          <section className="card-group" aria-label="Students by account status">
            <h2 className="title">Students by account status</h2>
            {byState.length === 0 ? <p className="secondary">No student data yet.</p> : (
              <table className="table"><thead><tr><th>Status</th><th>Students</th></tr></thead>
                <tbody>{byState.map((r) => <tr key={r.state}><td>{humanStatus(r.state)}</td><td>{r.c}</td></tr>)}</tbody></table>
            )}
          </section>
          <section className="card-group" aria-label="Events by category">
            <h2 className="title">Events by category</h2>
            {perSector.length === 0 ? <p className="secondary">No events have been published yet.</p> : (
              <table className="table"><thead><tr><th>Category</th><th>Events</th></tr></thead>
                <tbody>{perSector.map((r) => <tr key={r.sector}><td>{r.sector}</td><td>{r.c}</td></tr>)}</tbody></table>
            )}
          </section>
        </div>
        <section className="card-group" style={{ marginTop: 16 }} aria-label="Registrations per day">
          <h2 className="title">Registrations per day — last 30 days</h2>
          {perDay.length === 0 ? <p className="secondary">No registrations in this period.</p> : <>
            <div style={{ display: 'flex', gap: 3, alignItems: 'flex-end', height: 110 }} aria-hidden="true">
              {perDay.map((r) => <div key={String(r.d)} title={`${r.d}: ${r.c}`} style={{ flex: 1, minWidth: 4, height: `${Math.max(5, (Number(r.c) / maxDay) * 100)}%`, background: 'var(--primary)', borderRadius: '3px 3px 0 0', opacity: 0.85 }} />)}
            </div>
            <p className="secondary" style={{ marginTop: 8 }}>Daily new student accounts across your scope.</p>
          </>}
        </section>
      </>}
    </div>
  );
}
