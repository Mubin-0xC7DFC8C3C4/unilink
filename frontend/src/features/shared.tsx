import { useQuery } from '@tanstack/react-query';
import { apiGet, errorMessage } from '../api/client';
import { ApiUnavailable, EmptyState, ErrorState, PageHeader, Skeleton } from '../design/primitives';
export function useList<T>(key: string[], path: string) { return useQuery({ queryKey: key, queryFn: () => apiGet<T>(path), retry: false }); }
export function ListPage<T>({eyebrow,title,lead,queryKey,path,emptyTitle,emptyBody,render}:{eyebrow:string;title:string;lead?:string;queryKey:string[];path:string;emptyTitle:string;emptyBody:string;render:(items:T[])=>React.ReactNode}) {
  const q = useList<T[]>(queryKey, path);
  const items = Array.isArray(q.data) ? q.data : (q.data as any)?.items ?? (q.data as any)?.content ?? [];
  return <div className="container"><PageHeader eyebrow={eyebrow} title={title} lead={lead} />
  {q.isPending ? <Skeleton/> : q.isError ? (q.error as any)?.status===404 ? <ApiUnavailable endpoint={path} onRetry={()=>q.refetch()} /> : <ErrorState message={errorMessage(q.error)} onRetry={()=>q.refetch()} />
  : items.length===0 ? <EmptyState title={emptyTitle} body={emptyBody} /> : <>{render(items)}</>}
  </div>;
}
