import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { apiGet, errorMessage } from '../../api/client';
import { EmptyState, ErrorState, PageHeader, RuledList, Skeleton } from '../../design/primitives';

/** Saved: the account's bookmarks from the API (GET /bookmarks).
 *  Saving a post anywhere in the app writes through the same API, so
 *  this list is always in sync. */
export default function SavedPage() {
  const q = useQuery({ queryKey: ['bookmarks'], queryFn: () => apiGet<any>('/bookmarks'), retry: false });
  const merged: any[] = Array.isArray(q.data) ? q.data : (q.data?.items ?? q.data?.content ?? []);
  return (
    <div className="container">
      <PageHeader eyebrow="Bookmarks" title="Saved" lead="Posts and events you bookmarked." />
      {q.isPending ? <Skeleton />
        : merged.length === 0 ? (q.isError ? <ErrorState message={errorMessage(q.error)} onRetry={() => q.refetch()} /> : <EmptyState title="Nothing saved yet" body="Bookmark a post or event and it will appear here." />)
        : <RuledList>{merged.map((b: any) => <li key={b.id}><Link to={`/posts/${b.targetId || b.id}`}>{b.title || `Saved item ${b.targetId || b.id}`}</Link></li>)}</RuledList>}
    </div>
  );
}
