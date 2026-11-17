import { useQuery } from '@tanstack/react-query'; import { apiGet, errorMessage } from '../../api/client';
import { PageHeader, StatStrip, Skeleton, ErrorState, ApiUnavailable } from '../../design/primitives';
export default function Dashboard(){ const q=useQuery({queryKey:['admin-dashboard'], queryFn:()=>apiGet<any>('/admin/dashboard'), retry:false}); const d=q.data||{};
  return <div><PageHeader eyebrow="Admin Portal" title="Dashboard" lead="Real counts only — every number below comes from the backend. Moderators see their university only." />
  {q.isPending?<Skeleton/>:q.isError?(q.error as any)?.status===404?<ApiUnavailable endpoint="/admin/dashboard"/>:<ErrorState message={errorMessage(q.error)}/>
  :<><StatStrip stats={[{label:'Users',value:String(d.userCount??d.users??'—')},{label:'Pending verifications',value:String(d.pendingVerifications??'—')},{label:'Reports awaiting review',value:String(d.pendingReports??'—')},{label:'Active events',value:String(d.activeEvents??'—')}]}/>
  <p className="secondary">New users per day: {d.newUsersPerDay!=null?String(d.newUsersPerDay):'—'}. Overdue verification alerts are listed in the verification queue.</p></>}</div>;
}
