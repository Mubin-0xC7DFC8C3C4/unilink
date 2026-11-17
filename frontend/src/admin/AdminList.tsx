import { useQuery } from '@tanstack/react-query'; import { apiGet, errorMessage } from '../api/client';
import { EmptyState, ErrorState, PageHeader, Skeleton } from '../design/primitives';
export function AdminList<T>({eyebrow,title,lead,queryKey,path,emptyTitle,emptyBody,render}:{eyebrow:string;title:string;lead?:string;queryKey:string[];path:string;emptyTitle:string;emptyBody:string;render:(items:T[])=>React.ReactNode}){
  const q=useQuery({queryKey, queryFn:()=>apiGet<T[]>(path), retry:false});
  const items=Array.isArray(q.data)?q.data:(q.data as any)?.items??(q.data as any)?.content??[];
  return <div><PageHeader eyebrow={eyebrow} title={title} lead={lead}/>
  {q.isPending?<Skeleton/>:q.isError?<ErrorState message={errorMessage(q.error)} onRetry={()=>q.refetch()}/>
  :items.length===0?<EmptyState title={emptyTitle} body={emptyBody}/>:render(items)}</div>;
}
