import { useState } from 'react'; import { ListPage } from '../shared'; import { RuledList } from '../../design/primitives';
/** The backend requires ?from=&to= (dates, not ?month=). `to` is the first
 *  day of the next month so events on the last day are included (the
 *  backend compares deadline <= to). */
function monthRange(month: string){
  const [y,m] = month.split('-').map(Number);
  const from = `${month}-01`;
  const to = m===12 ? `${y+1}-01-01` : `${y}-${String(m+1).padStart(2,'0')}-01`;
  return {from, to};
}
export default function CalendarPage(){ const [month,setMonth]=useState(new Date().toISOString().slice(0,7));
  const {from,to} = monthRange(month);
  return <div><div className="container" style={{paddingTop:16}}><label htmlFor="month">Month</label>{' '}<input id="month" type="month" value={month} onChange={e=>setMonth(e.target.value)} className="input-search" /></div>
  <ListPage<any> eyebrow="Calendar" title="Calendar" lead="Events for the selected month." queryKey={['calendar',month]} path={`/calendar?from=${from}&to=${to}`} emptyTitle="No events this month" emptyBody="No events are scheduled in this month." render={items=><RuledList>{items.map((e:any)=><li key={e.id}><strong>{e.title}</strong><p className="secondary">{e.deadline||''}{e.location?` · ${e.location}`:''}</p></li>)}</RuledList>} /></div>;
}
