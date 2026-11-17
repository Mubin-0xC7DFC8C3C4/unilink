import { useQuery } from '@tanstack/react-query'; import { apiGet, errorMessage } from '../../api/client'; import { PageHeader, StatStrip, Skeleton, ErrorState } from '../../design/primitives';
export default function Analytics(){ const q=useQuery({queryKey:['admin-analytics'], queryFn:()=>apiGet<any>('/admin/analytics'), retry:false}); const d=q.data||{};
  // The backend sends verificationTurnaroundHours (an average, not days or
  // a median) and usersPerDay30, and no eventCount: the event total is the
  // honest sum of the per-sector counts it does send.
  const eventCount = Array.isArray(d.eventsPerSector) ? d.eventsPerSector.reduce((n:number,r:any)=>n+(Number(r.c)||0),0) : null;
  return <div><PageHeader eyebrow="Admin Portal" title="Analytics" lead="User growth, verification turnaround, events per sector and active universities — SQL aggregates only, no fabricated numbers." />
  {q.isPending?<Skeleton/>:q.isError?<ErrorState message={errorMessage(q.error)} onRetry={()=>q.refetch()}/>
  :<><StatStrip stats={[{label:'Active universities',value:String(d.activeUniversities??'—')},{label:'Verification turnaround',value:String(d.verificationTurnaroundHours??'—'),note:'hours (average)'},{label:'Events',value:String(eventCount??'—')}]}/><p className="secondary">Events per sector: {d.eventsPerSector?JSON.stringify(d.eventsPerSector):'—'}. User growth: {d.usersPerDay30?JSON.stringify(d.usersPerDay30):'—'}.</p></>}</div>;
}
