import { useQuery } from '@tanstack/react-query'; import { apiGet, errorMessage } from '../../api/client'; import { PageHeader, StatStrip, Skeleton, ErrorState } from '../../design/primitives';
export default function Analytics(){ const q=useQuery({queryKey:['admin-analytics'], queryFn:()=>apiGet<any>('/admin/analytics'), retry:false}); const d=q.data||{};
  return <div><PageHeader eyebrow="Admin Portal" title="Analytics" lead="User growth, verification turnaround, events per sector and active universities — SQL aggregates only, no fabricated numbers." />
  {q.isPending?<Skeleton/>:q.isError?<ErrorState message={errorMessage(q.error)} onRetry={()=>q.refetch()}/>
  :<><StatStrip stats={[{label:'Active universities',value:String(d.activeUniversities??'—')},{label:'Verification turnaround',value:String(d.verificationTurnaroundDays??'—'),note:'days (median)'},{label:'Events',value:String(d.eventCount??'—')}]}/><p className="secondary">Events per sector: {d.eventsPerSector?JSON.stringify(d.eventsPerSector):'—'}. User growth: {d.userGrowth?JSON.stringify(d.userGrowth):'—'}.</p></>}</div>;
}
