import { useState } from 'react'; import { Link } from 'react-router-dom'; import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ListPage } from '../shared'; import { RuledList, Button } from '../../design/primitives'; import { apiPost, errorMessage } from '../../api/client'; import type { EventItem } from '../../api/types';
/** The backend CreateEvent has no `type` field (every event post has
 *  type EVENT; the kind lives in the title/body) and requires `body`.
 *  Visibility HUB maps to DEPARTMENT_HUB, as for posts. The deadline is
 *  sent as end-of-day local time: the backend reads a LocalDateTime. */
function toVisibility(v: string){ return v==='HUB' ? 'DEPARTMENT_HUB' : v; }
export default function EventsPage(){
  const [tab,setTab]=useState<'upcoming'|'past'>('upcoming'); const qc=useQueryClient();
  const [title,setTitle]=useState(''); const [body,setBody]=useState(''); const [deadline,setDeadline]=useState('');
  const [visibility,setVisibility]=useState('DEPARTMENT'); const [regLink,setRegLink]=useState(''); const [location,setLocation]=useState('');
  const create = useMutation({ mutationFn: ()=>apiPost('/events',{title, body, visibility: toVisibility(visibility), deadline: deadline?`${deadline}T23:59:59`:undefined, regLink: regLink||undefined, location: location||undefined}), onSuccess: ()=>{ qc.invalidateQueries({queryKey:['events']}); setTitle(''); setBody(''); setDeadline(''); setRegLink(''); setLocation(''); } });
  return <div><ListPage<EventItem> eyebrow="Events" title="Events" lead="Seminars, workshops, competitions and hackathons. Upcoming events are listed by nearest deadline (FR-EVT-07)." queryKey={['events',tab]} path={tab==='upcoming'?'/events/upcoming':'/events?past=true'} emptyTitle={tab==='upcoming'?'No upcoming events':'No past events'} emptyBody="Create an event or check back later." render={items=><><div role="tablist" aria-label="Event archive"><button role="tab" aria-selected={tab==='upcoming'} className={tab==='upcoming'?'btn btn-primary btn-sm':'btn btn-sm'} onClick={()=>setTab('upcoming')}>Upcoming</button>{' '}<button role="tab" aria-selected={tab==='past'} className={tab==='past'?'btn btn-primary btn-sm':'btn btn-sm'} onClick={()=>setTab('past')}>Past archive</button></div><RuledList>{items.map(e=><li key={e.id}><Link to={`/events/${e.id}`}><strong>{e.title}</strong></Link><p className="secondary">{e.eventDeadline||e.deadline?`Deadline ${e.eventDeadline||e.deadline}`:''} {e.visibility?`· ${e.visibility}`:''} {e.location?`· ${e.location}`:''}</p></li>)}</RuledList></>} />
  <div className="container"><section className="card-group" style={{marginTop:24}}><h2 className="title">Create an event</h2><form onSubmit={e=>{e.preventDefault(); create.mutate();}}>
  <label htmlFor="ev-title">Title</label><input id="ev-title" className="input-search" value={title} onChange={e=>setTitle(e.target.value)} style={{width:'100%',marginBottom:8}} />
  <label htmlFor="ev-body">Description</label><textarea id="ev-body" className="input-search" value={body} onChange={e=>setBody(e.target.value)} rows={3} style={{width:'100%',marginBottom:8}} />
  <label htmlFor="ev-vis">Visibility</label><select id="ev-vis" value={visibility} onChange={e=>setVisibility(e.target.value)} className="input-search" style={{marginBottom:8}}>{['DEPARTMENT','UNIVERSITY','HUB','NATIONAL'].map(v=><option key={v}>{v}</option>)}</select>
  <label htmlFor="ev-deadline">Deadline</label><input id="ev-deadline" type="date" className="input-search" value={deadline} onChange={e=>setDeadline(e.target.value)} style={{marginBottom:8}} />
  <label htmlFor="ev-loc">Location</label><input id="ev-loc" className="input-search" value={location} onChange={e=>setLocation(e.target.value)} style={{width:'100%',marginBottom:8}} />
  <label htmlFor="ev-reg">Registration link</label><input id="ev-reg" className="input-search" value={regLink} onChange={e=>setRegLink(e.target.value)} style={{width:'100%',marginBottom:8}} />
  {create.isError && <p role="alert" className="field-error">{errorMessage(create.error)}</p>}
  <Button type="submit" variant="primary" disabled={!title.trim()||!body.trim()||!deadline||create.isPending}>Create event</Button></form></section>
  <p><Link to="/calendar">View calendar →</Link></p></div></div>;
}
