import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api, apiGet, apiPost, errorMessage } from '../../api/client';
import { Button, Field, Input, PageHeader, Skeleton, ErrorState, EmptyState } from '../../design/primitives';
import { ConfirmDialog, StatusBadge, Tabs, fmtDateTime } from '../adminUi';

/**
 * All Events (admin). Server-backed end to end: GET /admin/events lists
 * every event; lifecycle runs through POST /admin/events/{id}/publish,
 * POST /admin/events/{id}/cancel and DELETE /admin/events/{id}; the
 * moderation PATCH (hide / remove / restore, deadline, visibility,
 * registration closed) is unchanged. Published events appear on the
 * student Events page immediately; drafts and cancelled events never
 * reach students.
 */
const VISIBILITIES = ['DEPARTMENT', 'UNIVERSITY', 'DEPARTMENT_HUB', 'NATIONAL'] as const;

function displayStatus(e: any, now: number): string {
  if (e.status === 'REMOVED') return 'REMOVED';
  if (e.status === 'HIDDEN_ADMIN') return 'HIDDEN';
  const es = e.eventStatus ?? (e.status === 'DRAFT' ? 'DRAFT' : 'PUBLISHED');
  if (es === 'DRAFT' || e.status === 'DRAFT') return 'DRAFT';
  if (es === 'CANCELLED' || e.status === 'CANCELLED') return 'CANCELLED';
  if (e.eventDeadline && Date.parse(e.eventDeadline) < now) return 'COMPLETED';
  return 'PUBLISHED';
}

export default function AdminEvents() {
  const qc = useQueryClient();
  const refresh = () => qc.invalidateQueries({ queryKey: ['admin-events'] });
  const [tab, setTab] = useState('ALL');
  const [selected, setSelected] = useState<any>(null);
  const [editing, setEditing] = useState<any>(null);
  const [deadline, setDeadline] = useState('');
  const [visibility, setVisibility] = useState('UNIVERSITY');
  const [confirm, setConfirm] = useState<{ event: any; action: string; label: string } | null>(null);

  const q = useQuery({ queryKey: ['admin-events'], queryFn: () => apiGet<any[]>('/admin/events'), retry: false });
  const regs = useQuery({
    queryKey: ['admin-event-registrations', selected?.id],
    queryFn: () => apiGet<any[]>(`/admin/events/${selected.id}/registrations`),
    retry: false, enabled: !!selected,
  });
  const patch = useMutation({
    mutationFn: ({ id, body }: { id: number; body: any }) => api(`/admin/events/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),
    onSuccess: () => { setEditing(null); setConfirm(null); refresh(); },
  });
  const lifecycle = useMutation({
    mutationFn: ({ id, action }: { id: number; action: 'publish' | 'cancel' | 'delete' }) =>
      action === 'delete' ? api(`/admin/events/${id}`, { method: 'DELETE' }) : apiPost(`/admin/events/${id}/${action}`, {}),
    onSuccess: () => { setConfirm(null); setSelected(null); refresh(); },
  });

  const now = Date.now();
  const items = Array.isArray(q.data) ? q.data : [];
  const rows = items.map((e: any) => ({ ...e, displayStatus: displayStatus(e, now) }));
  const count = (s: string) => rows.filter((r: any) => r.displayStatus === s).length;
  const visible = tab === 'ALL' ? rows : rows.filter((r: any) => r.displayStatus === tab);
  const busy = patch.isPending || lifecycle.isPending;
  const actionError = patch.isError ? patch.error : lifecycle.isError ? lifecycle.error : null;

  const confirmAction = () => {
    if (!confirm) return;
    const { event, action } = confirm;
    if (action === 'PUBLISH') lifecycle.mutate({ id: event.id, action: 'publish' });
    else if (action === 'CANCEL') lifecycle.mutate({ id: event.id, action: 'cancel' });
    else if (action === 'DELETE') lifecycle.mutate({ id: event.id, action: 'delete' });
    else patch.mutate({ id: event.id, body: { status: action } });
  };

  return (
    <div>
      <PageHeader eyebrow="Admin Portal" title="All Events"
        lead="Events are created and managed by administrators only. Students can browse published events and register — they cannot create or edit events."
        action={<Link className="btn btn-primary" to="/admin/events/new">+ Create Event</Link>} />
      {q.isPending ? <Skeleton /> : q.isError && items.length === 0 ? <ErrorState message={errorMessage(q.error)} onRetry={() => q.refetch()} /> : <>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(130px,1fr))', gap: 12, marginBottom: 16 }}>
          {[
            ['Total Events', rows.length], ['Published', count('PUBLISHED')], ['Drafts', count('DRAFT')],
            ['Upcoming', rows.filter((r: any) => r.displayStatus === 'PUBLISHED').length],
            ['Completed', count('COMPLETED')], ['Cancelled', count('CANCELLED')],
          ].map(([label, v]) => (
            <section key={label as string} className="card-group"><strong style={{ fontSize: 22 }}>{v as number}</strong><p className="secondary" style={{ margin: 0 }}>{label}</p></section>
          ))}
        </div>
        <Tabs active={tab} onChange={setTab} tabs={[
          { key: 'ALL', label: `All (${rows.length})` },
          { key: 'PUBLISHED', label: `Published (${count('PUBLISHED')})` },
          { key: 'DRAFT', label: `Draft (${count('DRAFT')})` },
          { key: 'COMPLETED', label: `Completed (${count('COMPLETED')})` },
          { key: 'CANCELLED', label: `Cancelled (${count('CANCELLED')})` },
          { key: 'HIDDEN', label: `Hidden (${count('HIDDEN')})` },
          { key: 'REMOVED', label: `Removed (${count('REMOVED')})` },
        ]} />
        {visible.length === 0 ? <EmptyState title="No events" body="No events match this filter." /> : (
          <table className="table">
            <thead><tr><th>Event Title</th><th>Category</th><th>Organizer</th><th>Event Date</th><th>Location</th><th>Registration</th><th>Publication Status</th><th>Actions</th></tr></thead>
            <tbody>
              {visible.map((e: any) => (
                <tr key={e.id}>
                  <td><strong>{e.title}</strong></td>
                  <td>{(e.sectors ?? []).join(', ') || '—'}</td>
                  <td>{e.organizerName || e.authorName || '—'}</td>
                  <td>{fmtDateTime(e.eventDeadline)}</td>
                  <td>{e.location || '—'}</td>
                  <td>{e.regLink ? 'External registration' : 'On UniLink'}</td>
                  <td><StatusBadge status={e.displayStatus} /></td>
                  <td>
                    <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                      <Button onClick={() => { setSelected(e); setEditing(null); }}>View</Button>
                      {e.displayStatus === 'DRAFT' && <Button onClick={() => setConfirm({ event: e, action: 'PUBLISH', label: 'Publish event' })}>Publish</Button>}
                      {e.displayStatus === 'PUBLISHED' && <Button onClick={() => setConfirm({ event: e, action: 'CANCEL', label: 'Cancel event' })}>Cancel event</Button>}
                      {(e.displayStatus === 'PUBLISHED' || e.displayStatus === 'COMPLETED') && <>
                        <Button onClick={() => { setEditing(e); setSelected(null); setDeadline(e.eventDeadline ? String(e.eventDeadline).slice(0, 16) : ''); setVisibility(e.visibility || 'UNIVERSITY'); }}>Edit</Button>
                        <Button onClick={() => setConfirm({ event: e, action: 'HIDE', label: 'Hide event' })}>Hide</Button>
                        <Button onClick={() => setConfirm({ event: e, action: 'REMOVE', label: 'Remove event' })}>Remove</Button>
                      </>}
                      {e.displayStatus === 'HIDDEN' && <Button onClick={() => setConfirm({ event: e, action: 'RESTORE', label: 'Publish again' })}>Publish again</Button>}
                      {(e.displayStatus === 'DRAFT' || e.displayStatus === 'CANCELLED') && <Button onClick={() => setConfirm({ event: e, action: 'DELETE', label: 'Delete event' })}>Delete</Button>}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {actionError && <p role="alert" className="field-error">{errorMessage(actionError)}</p>}

        {selected && (
          <section className="card-group" style={{ marginTop: 20 }} aria-label="Event details">
            <h2 className="title">{selected.title}</h2>
            <div className="row"><span>Organizer</span><span>{selected.organizerName || selected.authorName || '—'}</span></div>
            <div className="row"><span>Starts</span><span>{fmtDateTime(selected.eventDeadline)}</span></div>
            <div className="row"><span>Location</span><span>{selected.location || '—'}</span></div>
            <div className="row"><span>Visibility</span><span>{selected.visibility}</span></div>
            <div className="row"><span>Status</span><span><StatusBadge status={selected.displayStatus} /></span></div>
            <div className="row"><span>Registration</span><span>{selected.regLink ? `External — ${selected.regLink}` : 'On UniLink'}</span></div>
            {selected.body && <p style={{ whiteSpace: 'pre-wrap' }}>{selected.body}</p>}
            <h3 style={{ marginTop: 16 }}>Registrations</h3>
            {regs.isPending ? <Skeleton /> : regs.isError ? <p className="secondary">Registrations could not be loaded.</p>
              : (regs.data ?? []).length === 0 ? <p className="secondary">No registrations yet.</p>
              : <table className="table"><thead><tr><th>Student</th><th>Email</th><th>Status</th><th>Registered</th></tr></thead>
                  <tbody>{(regs.data ?? []).map((r: any) => <tr key={r.userId}><td>{r.fullName || '—'}</td><td>{r.email || '—'}</td><td>{r.response}</td><td>{fmtDateTime(r.createdAt)}</td></tr>)}</tbody></table>}
            <div style={{ display: 'flex', gap: 8, marginTop: 12, flexWrap: 'wrap' }}>
              <Button disabled={busy} onClick={() => patch.mutate({ id: selected.id, body: { registrationClosed: true } })}>Close registration</Button>
              <Button onClick={() => setSelected(null)}>Close</Button>
            </div>
            {patch.isError && <p role="alert" className="field-error">{errorMessage(patch.error)}</p>}
          </section>
        )}

        {editing && (
          <section className="card-group" style={{ marginTop: 20 }} aria-label="Edit event">
            <h2 className="title">Edit — {editing.title}</h2>
            <Field label="Registration deadline / start" htmlFor="ae-deadline">
              <Input id="ae-deadline" type="datetime-local" value={deadline} onChange={(e) => setDeadline(e.target.value)} />
            </Field>
            <Field label="Visibility" htmlFor="ae-visibility">
              <select id="ae-visibility" className="input-search" style={{ width: '100%' }} value={visibility} onChange={(e) => setVisibility(e.target.value)}>
                {VISIBILITIES.map((v) => <option key={v} value={v}>{v.replace('_', ' ')}</option>)}
              </select>
            </Field>
            <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
              <Button variant="primary" disabled={busy || !deadline} onClick={() => patch.mutate({ id: editing.id, body: { deadline: deadline.length === 16 ? `${deadline}:00` : deadline, visibility } })}>Save changes</Button>
              <Button onClick={() => setEditing(null)}>Cancel</Button>
            </div>
            {patch.isError && <p role="alert" className="field-error">{errorMessage(patch.error)}</p>}
          </section>
        )}

        {confirm && (
          <ConfirmDialog
            title={`${confirm.label} — ${confirm.event.title}`}
            message={<p>{confirm.action === 'DELETE'
              ? 'The event will be removed from the portal. This is recorded in the activity log.'
              : confirm.action === 'CANCEL'
                ? 'Students will no longer see this event, and registered students are notified. This is recorded in the activity log.'
                : 'This is recorded in the activity log.'}</p>}
            confirmLabel={confirm.label} busy={busy}
            onCancel={() => setConfirm(null)} onConfirm={confirmAction} />
        )}
      </>}
    </div>
  );
}
