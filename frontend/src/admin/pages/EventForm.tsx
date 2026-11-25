import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { apiGet } from '../../api/client';
import { Button, Field, Input, Modal } from '../../design/primitives';
import { fmtDateTime } from '../adminUi';

const VISIBILITIES = [
  { key: 'DEPARTMENT', label: 'Department' },
  { key: 'UNIVERSITY', label: 'University' },
  { key: 'DEPARTMENT_HUB', label: 'Department hub' },
  { key: 'NATIONAL', label: 'National (all universities)' },
];

/**
 * Shared admin event form (D-204 E): the create page and the edit page
 * render the same fields and build the same payload — the publish
 * payload is the platform's event contract {title, body, visibility,
 * deadline, regLink, location, sectorIds, …} — details that have no
 * field of their own (organizer, contact, meeting link, schedule end,
 * instructions, eligibility) are composed into the published
 * description exactly as the Preview shows, so nothing is silently
 * dropped or silently invented. The parent decides what the submit
 * does with the payload (POST for create, PUT for edit).
 */
export interface EventFormValues {
  title: string; shortDesc: string; fullDesc: string; sectorIds: number[];
  organizer: string; orgScope: string; contact: string;
  startDate: string; startTime: string; endDate: string; endTime: string;
  eventType: 'Physical' | 'Online' | 'Hybrid';
  venue: string; address: string; meetingLink: string;
  regRequired: boolean; regDeadline: string; maxParticipants: string;
  regInstructions: string; eligibility: string; tags: string; extLink: string;
  visibility: string;
}

export const EMPTY_EVENT_FORM: EventFormValues = {
  title: '', shortDesc: '', fullDesc: '', sectorIds: [],
  organizer: '', orgScope: '', contact: '',
  startDate: '', startTime: '09:00', endDate: '', endTime: '',
  eventType: 'Physical',
  venue: '', address: '', meetingLink: '',
  regRequired: true, regDeadline: '', maxParticipants: '',
  regInstructions: '', eligibility: '', tags: '', extLink: '',
  visibility: 'UNIVERSITY',
};

interface EventFormProps {
  initial?: Partial<EventFormValues>;
  submitLabel: string;
  /** When set, a "Save as Draft" button is shown (create mode only). */
  draftLabel?: string;
  /** Status carried in the submit payload (default PUBLISHED). Edit mode
   *  passes the event's current status so a draft stays a draft. The
   *  backend PUT ignores status — publish/cancel are separate actions —
   *  but the payload should still say what it is. */
  submitStatus?: 'DRAFT' | 'PUBLISHED';
  busy?: boolean;
  serverError?: string;
  onSubmit: (payload: Record<string, unknown>) => void;
  onCancel: () => void;
}

export default function EventForm({ initial, submitLabel, draftLabel, submitStatus = 'PUBLISHED', busy, serverError, onSubmit, onCancel }: EventFormProps) {
  const v0 = { ...EMPTY_EVENT_FORM, ...initial };
  const [title, setTitle] = useState(v0.title);
  const [shortDesc, setShortDesc] = useState(v0.shortDesc);
  const [fullDesc, setFullDesc] = useState(v0.fullDesc);
  const [sectorIds, setSectorIds] = useState<number[]>(v0.sectorIds);
  const [organizer, setOrganizer] = useState(v0.organizer);
  const [orgScope, setOrgScope] = useState(v0.orgScope);
  const [contact, setContact] = useState(v0.contact);
  const [startDate, setStartDate] = useState(v0.startDate);
  const [startTime, setStartTime] = useState(v0.startTime);
  const [endDate, setEndDate] = useState(v0.endDate);
  const [endTime, setEndTime] = useState(v0.endTime);
  const [eventType, setEventType] = useState<'Physical' | 'Online' | 'Hybrid'>(v0.eventType);
  const [venue, setVenue] = useState(v0.venue);
  const [address, setAddress] = useState(v0.address);
  const [meetingLink, setMeetingLink] = useState(v0.meetingLink);
  const [regRequired, setRegRequired] = useState(v0.regRequired);
  const [regDeadline, setRegDeadline] = useState(v0.regDeadline);
  const [maxParticipants, setMaxParticipants] = useState(v0.maxParticipants);
  const [regInstructions, setRegInstructions] = useState(v0.regInstructions);
  const [eligibility, setEligibility] = useState(v0.eligibility);
  const [tags, setTags] = useState(v0.tags);
  const [extLink, setExtLink] = useState(v0.extLink);
  const [visibility, setVisibility] = useState(v0.visibility);
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

  const canPublish = !!title.trim() && !!fullDesc.trim() && !!startDate;
  const toggleSector = (id: number) => setSectorIds((prev) => prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]);

  return (
    <div>
      <form onSubmit={(e) => { e.preventDefault(); if (canPublish) onSubmit(payload(submitStatus)); }}>
        <section className="card-group" aria-label="Basic information">
          <h2 className="title">Basic Information</h2>
          <Field label="Event Title" htmlFor="ef-title"><Input id="ef-title" value={title} onChange={(e) => setTitle(e.target.value)} /></Field>
          <Field label="Short Description" htmlFor="ef-short"><Input id="ef-short" value={shortDesc} onChange={(e) => setShortDesc(e.target.value)} /></Field>
          <Field label="Full Description" htmlFor="ef-full"><Input id="ef-full" value={fullDesc} onChange={(e) => setFullDesc(e.target.value)} /></Field>
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
          <Field label="Organizer Name" htmlFor="ef-org"><Input id="ef-org" value={organizer} onChange={(e) => setOrganizer(e.target.value)} /></Field>
          <div>
            <label htmlFor="ef-orgscope">Organizing University / Department</label>
            <select id="ef-orgscope" className="input-search" style={{ width: '100%' }} value={orgScope} onChange={(e) => setOrgScope(e.target.value)}>
              <option value="">Select a university</option>
              {(unis.data ?? []).map((u: any) => <option key={u.id} value={u.name}>{u.name}</option>)}
            </select>
          </div>
          <Field label="Contact Information" htmlFor="ef-contact"><Input id="ef-contact" value={contact} onChange={(e) => setContact(e.target.value)} /></Field>
        </section>

        <section className="card-group" style={{ marginTop: 16 }} aria-label="Date and time">
          <h2 className="title">Date &amp; Time</h2>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Field label="Start Date" htmlFor="ef-sd"><Input id="ef-sd" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} /></Field>
            <Field label="Start Time" htmlFor="ef-st"><Input id="ef-st" type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} /></Field>
            <Field label="End Date" htmlFor="ef-ed"><Input id="ef-ed" type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} /></Field>
            <Field label="End Time" htmlFor="ef-et"><Input id="ef-et" type="time" value={endTime} onChange={(e) => setEndTime(e.target.value)} /></Field>
          </div>
          <div>
            <label htmlFor="ef-tz">Time Zone</label>
            <select id="ef-tz" className="input-search" style={{ width: '100%' }} defaultValue="BST">
              <option value="BST">Bangladesh Standard Time (UTC+06:00)</option>
              <option value="UTC">UTC</option>
            </select>
          </div>
        </section>

        <section className="card-group" style={{ marginTop: 16 }} aria-label="Location">
          <h2 className="title">Location</h2>
          <div>
            <label htmlFor="ef-type">Event Type</label>
            <select id="ef-type" className="input-search" style={{ width: '100%' }} value={eventType} onChange={(e) => setEventType(e.target.value as typeof eventType)}>
              <option>Physical</option><option>Online</option><option>Hybrid</option>
            </select>
          </div>
          {eventType !== 'Online' && <>
            <Field label="Venue Name" htmlFor="ef-venue"><Input id="ef-venue" value={venue} onChange={(e) => setVenue(e.target.value)} /></Field>
            <Field label="Full Address" htmlFor="ef-address"><Input id="ef-address" value={address} onChange={(e) => setAddress(e.target.value)} /></Field>
          </>}
          {eventType !== 'Physical' && <Field label="Online Meeting Link" htmlFor="ef-meet"><Input id="ef-meet" value={meetingLink} onChange={(e) => setMeetingLink(e.target.value)} /></Field>}
        </section>

        <section className="card-group" style={{ marginTop: 16 }} aria-label="Registration">
          <h2 className="title">Registration</h2>
          <div>
            <label htmlFor="ef-regreq">Registration Required</label>
            <select id="ef-regreq" className="input-search" style={{ width: '100%' }} value={regRequired ? 'yes' : 'no'} onChange={(e) => setRegRequired(e.target.value === 'yes')}>
              <option value="yes">Yes</option><option value="no">No</option>
            </select>
          </div>
          {regRequired && <>
            <Field label="Registration Deadline" htmlFor="ef-regdead"><Input id="ef-regdead" type="date" value={regDeadline} onChange={(e) => setRegDeadline(e.target.value)} /></Field>
            <Field label="Maximum Participants" htmlFor="ef-max"><Input id="ef-max" type="number" min="1" value={maxParticipants} onChange={(e) => setMaxParticipants(e.target.value)} /></Field>
            <Field label="Registration Instructions" htmlFor="ef-regins"><Input id="ef-regins" value={regInstructions} onChange={(e) => setRegInstructions(e.target.value)} /></Field>
            <Field label="External Registration Link" htmlFor="ef-ext"><Input id="ef-ext" value={extLink} onChange={(e) => setExtLink(e.target.value)} /></Field>
          </>}
          <Field label="Eligibility / Target Audience" htmlFor="ef-elig"><Input id="ef-elig" value={eligibility} onChange={(e) => setEligibility(e.target.value)} /></Field>
        </section>

        <section className="card-group" style={{ marginTop: 16 }} aria-label="Additional information">
          <h2 className="title">Additional Information</h2>
          <Field label="Event Tags" htmlFor="ef-tags"><Input id="ef-tags" value={tags} onChange={(e) => setTags(e.target.value)} placeholder="e.g. workshop, cse, career" /></Field>
          <div>
            <label htmlFor="ef-vis">Event Visibility</label>
            <select id="ef-vis" className="input-search" style={{ width: '100%' }} value={visibility} onChange={(e) => setVisibility(e.target.value)}>
              {VISIBILITIES.map((v) => <option key={v.key} value={v.key}>{v.label}</option>)}
            </select>
          </div>
        </section>

        {serverError && <p role="alert" className="field-error" style={{ marginTop: 14 }}>{serverError}</p>}
        <div style={{ display: 'flex', gap: 8, marginTop: 18, flexWrap: 'wrap' }}>
          {draftLabel && <Button type="button" disabled={!title.trim() || !startDate || busy} onClick={() => onSubmit(payload('DRAFT'))}>{draftLabel}</Button>}
          <Button type="button" disabled={!canPublish || busy} onClick={() => setPreviewOpen(true)}>Preview Event</Button>
          <Button type="submit" variant="primary" disabled={!canPublish || busy}>{submitLabel}</Button>
          <Button type="button" onClick={onCancel}>Cancel</Button>
        </div>
      </form>

      {previewOpen && (
        <Modal title="Event preview" onClose={() => setPreviewOpen(false)}>
          <p className="secondary">This is how the event will appear to students.</p>
          <h3 style={{ margin: '10px 0 4px' }}>{title}</h3>
          <p className="secondary">{fmtDateTime(deadline)} · {location || 'Location to be announced'}</p>
          <p style={{ whiteSpace: 'pre-wrap' }}>{body}</p>
          <div style={{ display: 'flex', gap: 8, marginTop: 14 }}>
            <Button variant="primary" disabled={busy} onClick={() => onSubmit(payload(submitStatus))}>{submitLabel}</Button>
            <Button onClick={() => setPreviewOpen(false)}>Back to editing</Button>
          </div>
          {serverError && <p role="alert" className="field-error">{serverError}</p>}
        </Modal>
      )}
    </div>
  );
}
