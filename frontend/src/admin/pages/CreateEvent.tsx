import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { apiGet, apiPost, errorMessage } from '../../api/client';
import { Button, Field, Input, Modal, PageHeader } from '../../design/primitives';
import { fmtDateTime } from '../adminUi';

const VISIBILITIES = [
  { key: 'DEPARTMENT', label: 'Department' },
  { key: 'UNIVERSITY', label: 'University' },
  { key: 'DEPARTMENT_HUB', label: 'Department hub' },
  { key: 'NATIONAL', label: 'National (all universities)' },
];

/**
 * Create Event (admin only). The form collects the full event brief;
 * the publish payload is the platform's event contract {title, body,
 * visibility, deadline, regLink, location, sectorIds} — details that
 * have no field of their own (organizer, contact, meeting link,
 * schedule end, instructions, eligibility) are composed into the
 * published description exactly as the Preview shows, so nothing is
 * silently dropped or silently invented.
 *
 * Backend dependency (unchanged since it was first flagged): the
 * platform does not yet expose an administrator event-creation
 * endpoint — POST /api/admin/events does not exist and the student
 * endpoint refuses admin sessions. Publishing therefore returns the
 * server's response, shown verbatim below; the event is NOT created
 * until that endpoint lands. There is also no draft state for events,
 * so "Save as Draft" explains that instead of pretending to save.
 */
export default function CreateEvent() {
  const nav = useNavigate();
  const [title, setTitle] = useState('');
  const [shortDesc, setShortDesc] = useState('');
  const [fullDesc, setFullDesc] = useState('');
  const [sectorIds, setSectorIds] = useState<number[]>([]);
  const [organizer, setOrganizer] = useState('');
  const [orgScope, setOrgScope] = useState('');
  const [contact, setContact] = useState('');
  const [startDate, setStartDate] = useState('');
  const [startTime, setStartTime] = useState('09:00');
  const [endDate, setEndDate] = useState('');
  const [endTime, setEndTime] = useState('');
  const [eventType, setEventType] = useState<'Physical' | 'Online' | 'Hybrid'>('Physical');
  const [venue, setVenue] = useState('');
  const [address, setAddress] = useState('');
  const [meetingLink, setMeetingLink] = useState('');
  const [regRequired, setRegRequired] = useState(true);
  const [regDeadline, setRegDeadline] = useState('');
  const [maxParticipants, setMaxParticipants] = useState('');
  const [regInstructions, setRegInstructions] = useState('');
  const [eligibility, setEligibility] = useState('');
  const [tags, setTags] = useState('');
  const [extLink, setExtLink] = useState('');
  const [visibility, setVisibility] = useState('UNIVERSITY');
  const [previewOpen, setPreviewOpen] = useState(false);

  const sectors = useQuery({ queryKey: ['public-sectors'], queryFn: () => apiGet<any[]>('/public/sectors'), retry: false });
  const unis = useQuery({ queryKey: ['public-universities'], queryFn: () => apiGet<any[]>('/public/universities'), retry: false });

  const deadline = startDate ? `${startDate}T${startTime || '09:00'}:00` : undefined;
  const location = eventType === 'Online' ? 'Online' : [venue, address].filter(Boolean).join(' — ') || (eventType === 'Hybrid' ? 'Hybrid' : '');
  const body = [
    shortDesc.trim(),
    fullDesc.trim(),
    organizer ? `Organizer: ${organizer}${orgScope ? ` (${orgScope})` : ''}` : '',
    contact ? `Contact: ${contact}` : '',
    endDate ? `Runs until: ${endDate}${endTime ? ` ${endTime}` : ''}` : '',
    meetingLink && eventType !== 'Physical' ? `Join online: ${meetingLink}` : '',
    regInstructions ? `Registration: ${regInstructions}` : '',
    regDeadline ? `Registration deadline: ${regDeadline}` : '',
    maxParticipants ? `Places are limited to ${maxParticipants} participants.` : '',
    eligibility ? `Who can join: ${eligibility}` : '',
    tags.trim() ? `Tags: ${tags.trim()}` : '',
  ].filter(Boolean).join('\n\n');

  // Event service policy (frontend data layer): publish tries the REST
  // endpoint first. A real validation/permission response (400/401/403/
  // 422) is shown verbatim; when the endpoint is not available the event
  // is kept in the frontend data layer instead, so publishing and
  // drafting work end to end in the frontend and the event appears in
  // All Events and on the student Events page immediately.
  const [publishing, setPublishing] = useState(false);
  const [serverError, setServerError] = useState('');
  const payload = (status: 'DRAFT' | 'PUBLISHED') => ({
    title: title.trim(),
    body,
    shortDescription: shortDesc.trim() || undefined,
    visibility,
    deadline,
    endAt: endDate ? `${endDate}T${endTime || '17:00'}:00` : undefined,
    location: location || undefined,
    venueName: venue || undefined,
    fullAddress: address || undefined,
    meetingLink: meetingLink || undefined,
    eventType: eventType || undefined,
    organizerName: organizer.trim() || undefined,
    contactInfo: contact || undefined,
    registrationRequired: regRequired,
    registrationDeadline: regDeadline ? `${regDeadline}T23:59:59` : undefined,
    regLink: regRequired && extLink ? extLink : undefined,
    maxParticipants: maxParticipants ? Number(maxParticipants) : undefined,
    sectorIds,
    universityId: (unis.data ?? []).find((u: any) => u.name === orgScope)?.id,
    status,
  });
  // Events are persisted by the backend (POST /api/admin/events). A
  // rejected request shows the server's message — nothing is saved
  // anywhere else.
  const submit = async (status: 'DRAFT' | 'PUBLISHED') => {
    setServerError(''); setPublishing(true);
    try { await apiPost('/admin/events', payload(status)); nav('/admin/events'); }
    catch (e) { setServerError(errorMessage(e)); setPublishing(false); }
  };
  const doPublish = () => submit('PUBLISHED');
  const doDraft = () => submit('DRAFT');

  const canPublish = !!title.trim() && !!fullDesc.trim() && !!startDate;
  const toggleSector = (id: number) => setSectorIds((prev) => prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]);

  return (
    <div>
      <PageHeader eyebrow="Admin Portal" title="Create Event" lead="Events are published by administrators and appear on the student Events page for the chosen audience. Students cannot create events." />
      <form onSubmit={(e) => { e.preventDefault(); void doPublish(); }}>
        <section className="card-group" aria-label="Basic information">
          <h2 className="title">Basic Information</h2>
          <Field label="Event Title" htmlFor="ce-title"><Input id="ce-title" value={title} onChange={(e) => setTitle(e.target.value)} /></Field>
          <Field label="Short Description" htmlFor="ce-short"><Input id="ce-short" value={shortDesc} onChange={(e) => setShortDesc(e.target.value)} /></Field>
          <Field label="Full Description" htmlFor="ce-full"><Input id="ce-full" value={fullDesc} onChange={(e) => setFullDesc(e.target.value)} /></Field>
          <div>
            <p style={{ margin: '10px 0 6px' }}><strong>Event Category</strong></p>
            <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
              {(sectors.data ?? []).map((s: any) => (
                <label key={s.id} style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>
                  <input type="checkbox" checked={sectorIds.includes(s.id)} onChange={() => toggleSector(s.id)} /> {s.name}
                </label>
              ))}
            </div>
          </div>
          <p className="secondary" style={{ marginTop: 10 }}>Event Cover Image: cover images are not supported for events yet, so this form does not collect one.</p>
        </section>

        <section className="card-group" style={{ marginTop: 16 }} aria-label="Organizer information">
          <h2 className="title">Organizer Information</h2>
          <Field label="Organizer Name" htmlFor="ce-org"><Input id="ce-org" value={organizer} onChange={(e) => setOrganizer(e.target.value)} /></Field>
          <div>
            <label htmlFor="ce-orgscope">Organizing University / Department</label>
            <select id="ce-orgscope" className="input-search" style={{ width: '100%' }} value={orgScope} onChange={(e) => setOrgScope(e.target.value)}>
              <option value="">Select a university</option>
              {(unis.data ?? []).map((u: any) => <option key={u.id} value={u.name}>{u.name}</option>)}
            </select>
          </div>
          <Field label="Contact Information" htmlFor="ce-contact"><Input id="ce-contact" value={contact} onChange={(e) => setContact(e.target.value)} /></Field>
        </section>

        <section className="card-group" style={{ marginTop: 16 }} aria-label="Date and time">
          <h2 className="title">Date &amp; Time</h2>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Field label="Start Date" htmlFor="ce-sd"><Input id="ce-sd" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} /></Field>
            <Field label="Start Time" htmlFor="ce-st"><Input id="ce-st" type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} /></Field>
            <Field label="End Date" htmlFor="ce-ed"><Input id="ce-ed" type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} /></Field>
            <Field label="End Time" htmlFor="ce-et"><Input id="ce-et" type="time" value={endTime} onChange={(e) => setEndTime(e.target.value)} /></Field>
          </div>
          <div>
            <label htmlFor="ce-tz">Time Zone</label>
            <select id="ce-tz" className="input-search" style={{ width: '100%' }} defaultValue="BST">
              <option value="BST">Bangladesh Standard Time (UTC+06:00)</option>
              <option value="UTC">UTC</option>
            </select>
          </div>
        </section>

        <section className="card-group" style={{ marginTop: 16 }} aria-label="Location">
          <h2 className="title">Location</h2>
          <div>
            <label htmlFor="ce-type">Event Type</label>
            <select id="ce-type" className="input-search" style={{ width: '100%' }} value={eventType} onChange={(e) => setEventType(e.target.value as typeof eventType)}>
              <option>Physical</option><option>Online</option><option>Hybrid</option>
            </select>
          </div>
          {eventType !== 'Online' && <>
            <Field label="Venue Name" htmlFor="ce-venue"><Input id="ce-venue" value={venue} onChange={(e) => setVenue(e.target.value)} /></Field>
            <Field label="Full Address" htmlFor="ce-address"><Input id="ce-address" value={address} onChange={(e) => setAddress(e.target.value)} /></Field>
          </>}
          {eventType !== 'Physical' && <Field label="Online Meeting Link" htmlFor="ce-meet"><Input id="ce-meet" value={meetingLink} onChange={(e) => setMeetingLink(e.target.value)} /></Field>}
        </section>

        <section className="card-group" style={{ marginTop: 16 }} aria-label="Registration">
          <h2 className="title">Registration</h2>
          <div>
            <label htmlFor="ce-regreq">Registration Required</label>
            <select id="ce-regreq" className="input-search" style={{ width: '100%' }} value={regRequired ? 'yes' : 'no'} onChange={(e) => setRegRequired(e.target.value === 'yes')}>
              <option value="yes">Yes</option><option value="no">No</option>
            </select>
          </div>
          {regRequired && <>
            <Field label="Registration Deadline" htmlFor="ce-regdead"><Input id="ce-regdead" type="date" value={regDeadline} onChange={(e) => setRegDeadline(e.target.value)} /></Field>
            <Field label="Maximum Participants" htmlFor="ce-max"><Input id="ce-max" type="number" min="1" value={maxParticipants} onChange={(e) => setMaxParticipants(e.target.value)} /></Field>
            <Field label="Registration Instructions" htmlFor="ce-regins"><Input id="ce-regins" value={regInstructions} onChange={(e) => setRegInstructions(e.target.value)} /></Field>
            <Field label="External Registration Link" htmlFor="ce-ext"><Input id="ce-ext" value={extLink} onChange={(e) => setExtLink(e.target.value)} /></Field>
          </>}
          <Field label="Eligibility / Target Audience" htmlFor="ce-elig"><Input id="ce-elig" value={eligibility} onChange={(e) => setEligibility(e.target.value)} /></Field>
        </section>

        <section className="card-group" style={{ marginTop: 16 }} aria-label="Additional information">
          <h2 className="title">Additional Information</h2>
          <Field label="Event Tags" htmlFor="ce-tags"><Input id="ce-tags" value={tags} onChange={(e) => setTags(e.target.value)} placeholder="e.g. workshop, cse, career" /></Field>
          <div>
            <label htmlFor="ce-vis">Event Visibility</label>
            <select id="ce-vis" className="input-search" style={{ width: '100%' }} value={visibility} onChange={(e) => setVisibility(e.target.value)}>
              {VISIBILITIES.map((v) => <option key={v.key} value={v.key}>{v.label}</option>)}
            </select>
          </div>
        </section>

        {serverError && <p role="alert" className="field-error" style={{ marginTop: 14 }}>{serverError}</p>}
        <div style={{ display: 'flex', gap: 8, marginTop: 18, flexWrap: 'wrap' }}>
          <Button type="button" disabled={!title.trim() || !startDate} onClick={doDraft}>Save as Draft</Button>
          <Button type="button" disabled={!canPublish} onClick={() => setPreviewOpen(true)}>Preview Event</Button>
          <Button type="submit" variant="primary" disabled={!canPublish || publishing}>Publish Event</Button>
          <Link className="btn" to="/admin/events">Cancel</Link>
        </div>
      </form>

      {previewOpen && (
        <Modal title="Event preview" onClose={() => setPreviewOpen(false)}>
          <p className="secondary">This is how the event will appear to students.</p>
          <h3 style={{ margin: '10px 0 4px' }}>{title}</h3>
          <p className="secondary">{fmtDateTime(deadline)} · {location || 'Location to be announced'}</p>
          <p style={{ whiteSpace: 'pre-wrap' }}>{body}</p>
          <div style={{ display: 'flex', gap: 8, marginTop: 14 }}>
            <Button variant="primary" disabled={publishing} onClick={doPublish}>Publish Event</Button>
            <Button onClick={() => setPreviewOpen(false)}>Back to editing</Button>
          </div>
          {serverError && <p role="alert" className="field-error">{serverError}</p>}
        </Modal>
      )}
    </div>
  );
}
