import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiGet, apiPost, errorMessage } from '../../api/client';
import { Button, EmptyState, PageHeader, Skeleton } from '../../design/primitives';
import type { UserProfile } from '../../api/types';

interface TeamDto { id: number; name: string; creatorId: number; maxSize?: number; status?: string }
interface JoinRequestDto { id: number; teamId: number; userId: number; status: string; message?: string }

const NW_CSS = `
.nw-banner{border:1px solid var(--border);border-radius:var(--radius-md);padding:10px 14px;font-size:.9rem;margin-bottom:16px}
.nw-section{margin-top:28px}
.nw-row{display:flex;gap:12px;align-items:center;border:1px solid var(--border);border-radius:var(--radius-md);padding:12px 14px;margin-bottom:10px}
.nw-avatar{width:44px;height:44px;border-radius:50%;background:var(--text-primary);color:var(--background);display:inline-flex;align-items:center;justify-content:center;font-weight:600;flex:none}
.nw-main{flex:1;min-width:0}
.nw-name{font-weight:600;margin:0}
.nw-meta{color:var(--text-secondary);font-size:.88rem;margin:2px 0 0}
.nw-actions{display:flex;gap:8px;flex:none}
.nw-counts{display:flex;gap:32px;border:1px solid var(--border);border-radius:var(--radius-md);padding:16px}
.nw-counts strong{font-size:1.3rem;display:block}
`;

function initialsOf(name: string) {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  return parts.length ? (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase() : '?';
}

const SAMPLE_REQUESTS = [
  { id: 'sr-1', name: 'Sadia Islam', meta: 'CSE · Batch 28 · Metropolitan University', team: 'Bus Tracker Team' },
  { id: 'sr-2', name: 'Rakib Ahmed', meta: 'CSE · Batch 27 · Metropolitan University', team: 'Contest Team Alpha' },
];
const SAMPLE_SUGGESTIONS = [
  { id: 'ss-1', name: 'Imran Hossain', meta: 'CSE · Batch 27 · Metropolitan University' },
  { id: 'ss-2', name: 'Sharmin Akter', meta: 'CSE · Batch 28 · Metropolitan University' },
];

/**
 * My Network. What the backend supports today, wired for real:
 * - Requests: team join requests for teams the student created —
 *   GET /teams, GET /teams/{id}/requests, decided through
 *   POST /teams/requests/{id}/decide {accept} (the Teams page's own
 *   contract). UniLink follows are instant, so there is no follow
 *   request queue in the backend; the requests shown here are the
 *   platform's real request inbox.
 * - Your network: follower/following counts from GET /profiles/me,
 *   with expandable lists from GET /profiles/{id}/followers and
 *   /profiles/{id}/following, and real suggestions from
 *   GET /profiles/suggestions (department-first). The API exposes counts only — no follower/following list endpoint
 *   exists yet, so no list is fabricated.
 * - Suggested students: no suggestions endpoint exists; sample
 *   suggestions render only in the preview state (when the live
 *   requests data is unavailable), under the preview banner.
 */
export default function NetworkPage() {
  const qc = useQueryClient();
  const me = useQuery({ queryKey: ['my-profile'], queryFn: () => apiGet<UserProfile>('/profiles/me'), retry: false });
  const teamsQ = useQuery({ queryKey: ['teams'], queryFn: () => apiGet<TeamDto[]>('/teams'), retry: false });
  const myId = (me.data as any)?.userId as number | undefined;
  const ownedTeams = (teamsQ.data ?? []).filter((t) => myId != null && t.creatorId === myId);
  const requestQueries = useQueries({
    queries: ownedTeams.map((t) => ({ queryKey: ['team-requests', t.id], queryFn: () => apiGet<JoinRequestDto[]>(`/teams/${t.id}/requests`), retry: false })),
  });
  const requests = ownedTeams.flatMap((t, i) => (requestQueries[i]?.data ?? []).filter((r) => r.status === 'PENDING').map((r) => ({ ...r, teamName: t.name })));
  const requesterIds = [...new Set(requests.map((r) => r.userId))];
  const requesterQueries = useQueries({
    queries: requesterIds.map((id) => ({ queryKey: ['profile', id], queryFn: () => apiGet<UserProfile>(`/profiles/${id}`), retry: false })),
  });
  const requesterName = (userId: number) => {
    const idx = requesterIds.indexOf(userId);
    return requesterQueries[idx]?.data?.fullName || `Student ${userId}`;
  };
  const requesterMeta = (userId: number) => {
    const idx = requesterIds.indexOf(userId);
    const p = requesterQueries[idx]?.data;
    return p ? [p.departmentName, p.batchLabel, p.universityName].filter(Boolean).join(' · ') : '';
  };
  const decide = useMutation({
    mutationFn: ({ rid, accept }: { rid: number; accept: boolean }) => apiPost(`/teams/requests/${rid}/decide`, { accept }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['team-requests'] }); qc.invalidateQueries({ queryKey: ['teams'] }); },
  });

  const [listOpen, setListOpen] = useState<'followers' | 'following' | null>(null);
  const followersQ = useQuery({ queryKey: ['my-followers', myId], queryFn: () => apiGet<any>(`/profiles/${myId}/followers?size=50`), retry: false, enabled: !!myId && listOpen === 'followers' });
  const followingQ = useQuery({ queryKey: ['my-following', myId], queryFn: () => apiGet<any>(`/profiles/${myId}/following?size=50`), retry: false, enabled: !!myId && listOpen === 'following' });
  const suggestionsQ = useQuery({ queryKey: ['suggestions'], queryFn: () => apiGet<any[]>('/profiles/suggestions'), retry: false });
  const followMut = useMutation({
    mutationFn: (id: number) => apiPost(`/profiles/${id}/follow`, {}),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['suggestions'] }); qc.invalidateQueries({ queryKey: ['my-profile'] }); },
  });
  const personRow = (p: any, action?: React.ReactNode) => (
    <div key={p.userId} className="nw-row">
      <span className="nw-avatar" aria-hidden="true">{initialsOf(p.fullName || 'Student')}</span>
      <div className="nw-main"><p className="nw-name"><Link to={`/profile/${p.userId}`}>{p.fullName}</Link></p><p className="nw-meta">{[p.department, p.university, p.batch].filter(Boolean).join(' · ')}</p></div>
      {action && <div className="nw-actions">{action}</div>}
    </div>
  );
  const preview = teamsQ.isError;
  const [gone, setGone] = useState<Set<string>>(new Set());
  const [followed, setFollowed] = useState<Set<string>>(new Set());
  const dismiss = (id: string) => setGone((s) => new Set(s).add(id));

  return (
    <div className="container">
      <style>{NW_CSS}</style>
      <PageHeader eyebrow="Community" title="My Network" lead="Manage your team invitations and academic connections." />
      {preview && <div className="nw-banner" role="note"><strong>Preview</strong> — live network data is unavailable, so sample requests and suggestions are shown instead. They are not real student activity.</div>}

      <h2 className="headline nw-section">Requests</h2>
      {preview ? (
        SAMPLE_REQUESTS.filter((r) => !gone.has(r.id)).length === 0
          ? <EmptyState title="No pending requests" body="Sample requests you accept or ignore disappear from this list." />
          : SAMPLE_REQUESTS.filter((r) => !gone.has(r.id)).map((r) => (
            <div key={r.id} className="nw-row">
              <span className="nw-avatar" aria-hidden="true">{initialsOf(r.name)}</span>
              <div className="nw-main"><p className="nw-name">{r.name}</p><p className="nw-meta">{r.meta} · wants to join {r.team}</p></div>
              <div className="nw-actions"><Button variant="primary" className="btn-sm" onClick={() => dismiss(r.id)}>Accept</Button><Button className="btn-sm" onClick={() => dismiss(r.id)}>Ignore</Button></div>
            </div>
          ))
      ) : teamsQ.isPending ? <Skeleton />
      : requests.length === 0 ? <EmptyState title="No pending requests" body="When a student asks to join a team you created, the request appears here for you to accept or ignore." />
      : requests.map((r) => (
        <div key={r.id} className="nw-row">
          <span className="nw-avatar" aria-hidden="true">{initialsOf(requesterName(r.userId))}</span>
          <div className="nw-main"><p className="nw-name"><Link to={`/profile/${r.userId}`}>{requesterName(r.userId)}</Link></p><p className="nw-meta">{[requesterMeta(r.userId), `wants to join ${r.teamName}`].filter(Boolean).join(' · ')}</p>{r.message && <p className="nw-meta">“{r.message}”</p>}</div>
          <div className="nw-actions"><Button variant="primary" className="btn-sm" disabled={decide.isPending} onClick={() => decide.mutate({ rid: r.id, accept: true })}>Accept</Button><Button className="btn-sm" disabled={decide.isPending} onClick={() => decide.mutate({ rid: r.id, accept: false })}>Ignore</Button></div>
        </div>
      ))}
      {decide.isError && <p role="alert" className="field-error">{errorMessage(decide.error)}</p>}

      <h2 className="headline nw-section">Your network</h2>
      {me.isPending ? <Skeleton /> : (
        <div className="nw-counts">
          <span><strong>{typeof me.data?.followers === 'number' ? me.data.followers : '—'}</strong>Followers <Button className="btn-sm" aria-expanded={listOpen === 'followers'} onClick={() => setListOpen(listOpen === 'followers' ? null : 'followers')}>View</Button></span>
          <span><strong>{typeof me.data?.following === 'number' ? me.data.following : '—'}</strong>Following <Button className="btn-sm" aria-expanded={listOpen === 'following'} onClick={() => setListOpen(listOpen === 'following' ? null : 'following')}>View</Button></span>
        </div>
      )}
      {listOpen === 'followers' && (followersQ.isPending ? <Skeleton /> : ((followersQ.data?.items ?? []).length === 0 ? <EmptyState title="No followers yet" body="Students who follow you will appear here." /> : (followersQ.data?.items ?? []).map((p: any) => personRow(p))))}
      {listOpen === 'following' && (followingQ.isPending ? <Skeleton /> : ((followingQ.data?.items ?? []).length === 0 ? <EmptyState title="Not following anyone yet" body="Students you follow will appear here." /> : (followingQ.data?.items ?? []).map((p: any) => personRow(p))))}

      <h2 className="headline nw-section">Suggested students</h2>
      {preview ? (
        SAMPLE_SUGGESTIONS.map((s) => (
          <div key={s.id} className="nw-row">
            <span className="nw-avatar" aria-hidden="true">{initialsOf(s.name)}</span>
            <div className="nw-main"><p className="nw-name">{s.name}</p><p className="nw-meta">{s.meta}</p></div>
            <div className="nw-actions"><Button className="btn-sm" aria-pressed={followed.has(s.id)} onClick={() => setFollowed((f) => { const n = new Set(f); if (n.has(s.id)) n.delete(s.id); else n.add(s.id); return n; })}>{followed.has(s.id) ? 'Following' : 'Follow'}</Button></div>
          </div>
        ))
      ) : suggestionsQ.isPending ? <Skeleton />
      : (suggestionsQ.data ?? []).length === 0 ? <EmptyState title="No suggestions right now" body="Suggestions appear here as more students from your department and batch join UniLink." />
      : (suggestionsQ.data ?? []).map((p: any) => personRow(p, <Button className="btn-sm" disabled={followMut.isPending} onClick={() => followMut.mutate(p.userId)}>Follow</Button>))}
      {followMut.isError && <p role="alert" className="field-error">{errorMessage(followMut.error)}</p>}
    </div>
  );
}
