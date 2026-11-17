import { useQuery } from '@tanstack/react-query'; import { apiGet, errorMessage } from '../api/client';
import { EmptyState, ErrorState, PageHeader, Skeleton } from '../design/primitives';
export function AdminList<T>({eyebrow,title,lead,queryKey,path,enabled=true,emptyTitle,emptyBody,render}:{eyebrow:string;title:string;lead?:string;queryKey:string[];path:string;enabled?:boolean;emptyTitle:string;emptyBody:string;render:(items:T[])=>React.ReactNode}){
  // The path is part of the key: callers whose path depends on loaded data
  // (MasterData's departments/batches path carries the university id) must
  // refetch when the path changes, not keep the first (possibly empty-id)
  // fetch. Prefix invalidation on queryKey still matches.
  const q=useQuery({queryKey:[...queryKey,path], queryFn:()=>apiGet<T[]>(path), retry:false, enabled});
  const items=Array.isArray(q.data)?q.data:(q.data as any)?.items??(q.data as any)?.content??[];
  return <div><PageHeader eyebrow={eyebrow} title={title} lead={lead}/>
  {q.isPending?<Skeleton/>:q.isError?<ErrorState message={errorMessage(q.error)} onRetry={()=>q.refetch()}/>
  :items.length===0?<EmptyState title={emptyTitle} body={emptyBody}/>:render(items)}</div>;
}
