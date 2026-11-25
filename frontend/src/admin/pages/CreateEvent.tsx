import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiPost, errorMessage } from '../../api/client';
import { PageHeader } from '../../design/primitives';
import EventForm from './EventForm';

/**
 * Create Event (admin only). The form (EventForm) collects the full
 * event brief; the publish payload is the platform's event contract —
 * details with no field of their own are composed into the published
 * description exactly as the Preview shows. Events are persisted by
 * the backend (POST /admin/events); a rejected request shows the
 * server's message — nothing is saved anywhere else.
 */
export default function CreateEvent() {
  const nav = useNavigate();
  const [publishing, setPublishing] = useState(false);
  const [serverError, setServerError] = useState('');
  const submit = async (payload: Record<string, unknown>) => {
    setServerError(''); setPublishing(true);
    try { await apiPost('/admin/events', payload); nav('/admin/events'); }
    catch (e) { setServerError(errorMessage(e)); setPublishing(false); }
  };
  return (
    <div>
      <PageHeader eyebrow="Admin Portal" title="Create Event" lead="Events are published by administrators and appear on the student Events page for the chosen audience. Students cannot create events." />
      <EventForm submitLabel="Publish Event" draftLabel="Save as Draft" busy={publishing} serverError={serverError} onSubmit={submit} onCancel={() => nav('/admin/events')} />
    </div>
  );
}
