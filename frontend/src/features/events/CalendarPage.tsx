import { useState } from 'react'; import { ListPage } from '../shared'; import { RuledList } from '../../design/primitives';
export default function CalendarPage(){ const [month,setMonth]=useState(new Date().toISOString().slice(0,7));
  return <div><div className="container" style={{paddingTop:16}}><label htmlFor="month">Month</label>{' '}<input id="month" type="month" value={month} onChange={e=>setMonth(e.target.value)} className="input-search" /></div>
  <ListPage<any> eyebrow="Calendar" title="Calendar" lead="Events for the selected month." queryKey={['calendar',month]} path={`/calendar?month=${month}`} emptyTitle="No events this month" emptyBody="No events are scheduled in this month." render={items=><RuledList>{items.map((e:any)=><li key={e.id}><strong>{e.title}</strong><p className="secondary">{e.date||e.deadline||''}</p></li>)}</RuledList>} /></div>;
}
