import { useQuery } from '@tanstack/react-query'; import { apiGet, errorMessage } from '../../api/client';
import { PageHeader, StatStrip, Skeleton, ErrorState } from '../../design/primitives';
export default function Dashboard(){ const q=useQuery({queryKey:['admin-dashboard'], queryFn:()=>apiGet<any>('/admin/dashboard'), retry:false}); const d=q.data||{};
  return <div><PageHeader eyebrow="Admin Portal" title="Dashboard" lead="Real counts only — every number below comes from the backend. Moderators see their university only." />
  {q.isPending?<Skeleton/>:q.isError?<ErrorState message={errorMessage(q.error)} onRetry={()=>q.refetch()}/>
  :<><StatStrip stats={[{label:'Users',value:String(d.totalUsers??'—')},{label:'Pending verifications',value:String(d.pendingVerifications??'—')},{label:'Reports awaiting review',value:String(d.reportsAwaitingReview??'—')},{label:'Active events',value:String(d.activeEvents??'—')}]}/>
  <p className="secondary">New users per day: {d.newUsersToday!=null?String(d.newUsersToday):'—'}. Overdue verification alerts are listed in the verification queue.</p></>}</div>;
}
