import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { api, apiGet, errorMessage } from '../../api/client';
import { ErrorState, PageHeader, Skeleton } from '../../design/primitives';
import EventForm, { type EventFormValues } from './EventForm';

/**
 * Edit Event (D-204 E): the same EventForm as Create Event, prefilled
 * from GET /admin/events/{id} and saved with the full PUT
 * /admin/events/{id}. The backend PUT updates every field the form
 * holds (title, description, visibility, deadline, location,
 * organizer, registration, sectors); publish/cancel stay separate
 * lifecycle actions on the All Events page, and a cancelled event
 * cannot be edited (the server returns 400 EVENT_CANCELLED).
 *
 * Prefill limit: the event DTO carries title, body, visibility,
 * deadline, location, registration link, organizer and sector names
 * only — end time, venue/address split, contact, registration
 * deadline, maximum participants and registration instructions are
 * not in the DTO, so those fields start empty and the admin fills
 * them in. The full description text is always shown for editing.
 */
function toInitial(e: any, sectorIds: number[]): Partial<EventFormValues> {
  const deadline = typeof e.eventDeadline === 'string' ? e.eventDeadline : '';
  const [startDate = '', startTime = '09:00'] = deadline ? deadline.split('T') : [];
  return {
    title: e.title ?? '',
    fullDesc: e.body ?? '',
    sectorIds,
    organizer: e.organizerName ?? '',
    startDate,
    startTime: startTime.slice(0, 5) || '09:00',
    eventType: e.location === 'Online' ? 'Online' : 'Physical',
    venue: e.location && e.location !== 'Online' ? e.location : '',
    extLink: e.regLink ?? '',
    visibility: e.visibility ?? 'UNIVERSITY',
  };
}

export default function EditEvent() {
  const { id } = useParams();
  const nav = useNavigate();
  const [saving, setSaving] = useState(false);
  const [serverError, setServerError] = useState('');
  const q = useQuery({ queryKey: ['admin-event', id], queryFn: () => apiGet<any>(`/admin/events/${id}`), retry: false });
  const sectorsQ = useQuery({ queryKey: ['public-sectors'], queryFn: () => apiGet<any[]>('/public/sectors'), retry: false });
  const e = q.data;
  const sectorIds = (e?.sectors ?? []).map((name: string) => (sectorsQ.data ?? []).find((s: any) => s.name === name)?.id).filter((x: any) => x != null);
  const submit = async (payload: Record<string, unknown>) => {
    setServerError(''); setSaving(true);
    try { await api(`/admin/events/${id}`, { method: 'PUT', body: JSON.stringify(payload) }); nav('/admin/events'); }
    catch (err) { setServerError(errorMessage(err)); setSaving(false); }
  };
  const loading = q.isPending || sectorsQ.isPending;
  return (
    <div>
      <PageHeader eyebrow="Admin Portal" title="Edit Event" lead="Change any field of this event. Saving updates the event everywhere students see it; it does not change whether the event is a draft or published." />
      {loading ? <Skeleton /> : q.isError ? <ErrorState message={errorMessage(q.error)} onRetry={() => q.refetch()} /> : e && (
        <EventForm
          initial={toInitial(e, sectorIds)}
          submitLabel="Save changes"
          submitStatus={e.eventStatus === 'DRAFT' || e.status === 'DRAFT' ? 'DRAFT' : 'PUBLISHED'}
          busy={saving}
          serverError={serverError}
          onSubmit={submit}
          onCancel={() => nav('/admin/events')}
        />
      )}
    </div>
  );
}
