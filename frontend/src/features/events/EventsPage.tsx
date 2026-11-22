import { useState } from 'react'; import { Link } from 'react-router-dom'; import { useMutation, useQuery } from '@tanstack/react-query';
import { EmptyState, ErrorState, PageHeader, Pill, Skeleton } from '../../design/primitives'; import { apiGet, apiPost, errorMessage } from '../../api/client'; import type { EventItem } from '../../api/types';
import { formatEventDateTime, eventVisibilityLabel } from './eventFormat';
/** Student Events page: browse-only — upcoming/past events as bordered
 *  cards with formatted deadlines, visibility badges, the real RSVP
 *  endpoint behind "Interested" (POST /events/{id}/respond?response=
 *  INTERESTED; the list DTO carries no viewer RSVP state, so the button
 *  starts inactive and reflects this session's clicks) and View
 *  details. Events published from the Admin Portal appear here
 *  immediately because both sides read the same server data. Event
 *  CREATION lives only in the Admin Portal: students never see a
 *  create button or form here. */
const EV_CSS = `
.ev-card{border:1px solid var(--border);border-radius:var(--radius-md);padding:16px;margin-bottom:14px}
.ev-card-top{display:flex;justify-content:space-between;gap:12px;align-items:flex-start}
.ev-card-top a{text-decoration:none;color:inherit}
.ev-meta{color:var(--text-secondary);font-size:.9rem;margin:6px 0 0}
.ev-actions{display:flex;gap:8px;margin-top:12px}
.ev-actions .is-active{background:var(--primary);color:var(--on-primary);border-color:var(--primary)}
`;
function InterestedButton({ eventId }: { eventId: number | string }){
  const [interested,setInterested]=useState(false);
  const rsvp = useMutation({ mutationFn: ()=>apiPost(`/events/${eventId}/respond?response=INTERESTED`,{}), onSuccess: ()=>setInterested(true) });
  return <button type="button" className={`btn btn-sm${interested?' is-active':''}`} aria-pressed={interested} disabled={rsvp.isPending} onClick={()=>{ if(!interested) rsvp.mutate(); }}>{interested?'Interested ✓':'Interested'}</button>;
}

export default function EventsPage(){
  const [tab,setTab]=useState<'upcoming'|'past'>('upcoming');
  const q = useQuery({ queryKey:['events',tab], queryFn: ()=>apiGet<EventItem[]>(tab==='upcoming'?'/events/upcoming':'/events?past=true'), retry:false });
  const serverItems: EventItem[] = Array.isArray(q.data) ? q.data : ((q.data as any)?.items ?? []);
  const byDeadline = (a: EventItem, b: EventItem) => Date.parse(a.eventDeadline || a.deadline || '') - Date.parse(b.eventDeadline || b.deadline || '');
  const merged = [...serverItems].sort((x,y)=> tab==='upcoming' ? byDeadline(x,y) : byDeadline(y,x));
  return <div className="container"><style>{EV_CSS}</style>
  <PageHeader eyebrow="Events" title="Events" lead="Seminars, workshops, competitions, and hackathons listed by nearest deadline." />
  <div role="tablist" aria-label="Event archive" style={{marginBottom:16}}><button role="tab" aria-selected={tab==='upcoming'} className={tab==='upcoming'?'btn btn-primary btn-sm':'btn btn-sm'} onClick={()=>setTab('upcoming')}>Upcoming</button>{' '}<button role="tab" aria-selected={tab==='past'} className={tab==='past'?'btn btn-primary btn-sm':'btn btn-sm'} onClick={()=>setTab('past')}>Past archive</button></div>
  {q.isPending ? <Skeleton/> : merged.length===0 ? (q.isError ? <ErrorState message={errorMessage(q.error)} onRetry={()=>q.refetch()}/> : <EmptyState title={tab==='upcoming'?'No upcoming events':'No past events'} body="Check back later for new events."/>) : merged.map((e)=>{ const when = formatEventDateTime(e.eventDeadline || (e as any).deadline); return (
    <article key={e.id} className="ev-card">
      <div className="ev-card-top"><Link to={`/events/${e.id}`}><strong>{e.title}</strong></Link><Pill>{eventVisibilityLabel(e.visibility)}</Pill></div>
      <p className="ev-meta">{[when, e.location].filter(Boolean).join(' · ')}</p>
      <div className="ev-actions">
        <InterestedButton eventId={e.id} />
        <Link className="btn btn-sm" to={`/events/${e.id}`}>View details</Link>
      </div>
    </article> ); })}
  <p><Link to="/calendar">View calendar →</Link></p>
  </div>;
}
