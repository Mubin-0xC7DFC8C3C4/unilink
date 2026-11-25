import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiDelete, apiGet, apiPost, errorMessage } from '../../api/client';
import { Button, EmptyState, PageHeader, Skeleton } from '../../design/primitives';
import type { UserProfile } from '../../api/types';

interface TeamDto { id: number; name: string; creatorId: number; maxSize?: number; status?: string }
interface JoinRequestDto { id: number; teamId: number; userId: number; status: string; message?: string }
interface ConnectionRequestDto { id: number; requesterId: number; requesterName?: string; addresseeId: number; addresseeName?: string; status: string; message?: string }

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

/** Sample rows render ONLY under the Preview banner (when live network
 *  data is genuinely unavailable) and their buttons are disabled — they
 *  never pretend to act (D-200: the old local-only Accept/Ignore/Follow
 *  buttons are gone). */
const SAMPLE_REQUESTS = [
  { id: 'sr-1', name: 'Sadia Islam', meta: 'CSE · Batch 28 · Metropolitan University', team: 'Bus Tracker Team' },
  { id: 'sr-2', name: 'Rakib Ahmed', meta: 'CSE · Batch 27 · Metropolitan University', team: 'Contest Team Alpha' },
];
const SAMPLE_SUGGESTIONS = [
  { id: 'ss-1', name: 'Imran Hossain', meta: 'CSE · Batch 27 · Metropolitan University' },
  { id: 'ss-2', name: 'Sharmin Akter', meta: 'CSE · Batch 28 · Metropolitan University' },
];

/**
 * My Network (D-200). What the backend supports, wired for real:
 * - Connection requests: GET /connections/requests/incoming and
 *   /outgoing; decided through POST /connections/requests/{id}/accept,
 *   /reject and /cancel. A connection is mutual; accepting does not
 *   auto-follow.
 * - Team requests: team join requests for teams the student created —
 *   GET /teams, GET /teams/{id}/requests, decided through
 *   POST /teams/requests/{id}/decide {accept}.
 * - Your network: follower/following counts from GET /profiles/me,
 *   with expandable lists from GET /profiles/{id}/followers and
 *   /profiles/{id}/following. Those endpoints return a bare list, so
 *   the page reads the array directly (the old `.items` read could
 *   never render a list).
 * - Suggested students: real suggestions from GET /profiles/suggestions
 *   (department-first) with a real Follow button.
 */
export default function NetworkPage() {
  const qc = useQueryClient();
  const me = useQuery({ queryKey: ['my-profile'], queryFn: () => apiGet<UserProfile>('/profiles/me'), retry: false });
  const incomingQ = useQuery({ queryKey: ['connection-requests-incoming'], queryFn: () => apiGet<ConnectionRequestDto[]>('/connections/requests/incoming'), retry: false });
  const outgoingQ = useQuery({ queryKey: ['connection-requests-outgoing'], queryFn: () => apiGet<ConnectionRequestDto[]>('/connections/requests/outgoing'), retry: false });
  const incoming = incomingQ.data ?? [];
  const outgoing = outgoingQ.data ?? [];
  const decideConnection = useMutation({
    mutationFn: ({ rid, accept }: { rid: number; accept: boolean }) => apiPost(`/connections/requests/${rid}/${accept ? 'accept' : 'reject'}`, {}),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['connection-requests-incoming'] }); qc.invalidateQueries({ queryKey: ['connection-requests-outgoing'] }); },
  });
  const cancelConnection = useMutation({
    mutationFn: (rid: number) => apiPost(`/connections/requests/${rid}/cancel`, {}),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['connection-requests-outgoing'] }); },
  });
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
  const followersQ = useQuery({ queryKey: ['my-followers', myId], queryFn: () => apiGet<any[]>(`/profiles/${myId}/followers?size=50`), retry: false, enabled: !!myId && listOpen === 'followers' });
  const followingQ = useQuery({ queryKey: ['my-following', myId], queryFn: () => apiGet<any[]>(`/profiles/${myId}/following?size=50`), retry: false, enabled: !!myId && listOpen === 'following' });
  const suggestionsQ = useQuery({ queryKey: ['suggestions'], queryFn: () => apiGet<any[]>('/profiles/suggestions'), retry: false });
  const followMut = useMutation({
    mutationFn: (id: number) => apiPost(`/profiles/${id}/follow`, {}),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['suggestions'] }); qc.invalidateQueries({ queryKey: ['my-profile'] }); },
  });
  const unfollowMut = useMutation({
    mutationFn: (id: number) => apiDelete(`/profiles/${id}/follow`),
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
  const followers: any[] = followersQ.data ?? [];
  const following: any[] = followingQ.data ?? [];

  return (
    <div className="container">
      <style>{NW_CSS}</style>
      <PageHeader eyebrow="Community" title="My Network" lead="Manage your team invitations and academic connections." />
      {preview && <div className="nw-banner" role="note"><strong>Preview</strong> — live network data is unavailable, so sample requests and suggestions are shown instead. They are not real student activity.</div>}

      <h2 className="headline nw-section">Connection requests</h2>
      {preview ? SAMPLE_REQUESTS.map((r) => (
        <div key={r.id} className="nw-row">
          <span className="nw-avatar" aria-hidden="true">{initialsOf(r.name)}</span>
          <div className="nw-main"><p className="nw-name">{r.name}</p><p className="nw-meta">{r.meta} · wants to connect</p></div>
          <div className="nw-actions"><Button variant="primary" className="btn-sm" disabled>Accept</Button><Button className="btn-sm" disabled>Ignore</Button></div>
        </div>
      )) : incomingQ.isPending || outgoingQ.isPending ? <Skeleton />
      : incoming.length === 0 && outgoing.length === 0 ? <EmptyState title="No connection requests" body="When a student asks to connect, the request appears here for you to accept or ignore." />
      : (<>
        {incoming.map((r) => (
          <div key={r.id} className="nw-row">
            <span className="nw-avatar" aria-hidden="true">{initialsOf(r.requesterName || 'Student')}</span>
            <div className="nw-main"><p className="nw-name"><Link to={`/profile/${r.requesterId}`}>{r.requesterName || `Student ${r.requesterId}`}</Link></p><p className="nw-meta">wants to connect with you</p>{r.message && <p className="nw-meta">“{r.message}”</p>}</div>
            <div className="nw-actions"><Button variant="primary" className="btn-sm" disabled={decideConnection.isPending} onClick={() => decideConnection.mutate({ rid: r.id, accept: true })}>Accept</Button><Button className="btn-sm" disabled={decideConnection.isPending} onClick={() => decideConnection.mutate({ rid: r.id, accept: false })}>Ignore</Button></div>
          </div>
        ))}
        {outgoing.map((r) => (
          <div key={`out-${r.id}`} className="nw-row">
            <span className="nw-avatar" aria-hidden="true">{initialsOf(r.addresseeName || 'Student')}</span>
            <div className="nw-main"><p className="nw-name"><Link to={`/profile/${r.addresseeId}`}>{r.addresseeName || `Student ${r.addresseeId}`}</Link></p><p className="nw-meta">Connection request sent — waiting for a reply</p>{r.message && <p className="nw-meta">“{r.message}”</p>}</div>
            <div className="nw-actions"><Button className="btn-sm" disabled={cancelConnection.isPending} onClick={() => cancelConnection.mutate(r.id)}>Cancel</Button></div>
          </div>
        ))}
      </>)}
      {(decideConnection.isError || cancelConnection.isError) && <p role="alert" className="field-error">{errorMessage((decideConnection.error || cancelConnection.error) as any)}</p>}

      <h2 className="headline nw-section">Team requests</h2>
      {preview ? (
        SAMPLE_REQUESTS.map((r) => (
          <div key={r.id} className="nw-row">
            <span className="nw-avatar" aria-hidden="true">{initialsOf(r.name)}</span>
            <div className="nw-main"><p className="nw-name">{r.name}</p><p className="nw-meta">{r.meta} · wants to join {r.team}</p></div>
            <div className="nw-actions"><Button variant="primary" className="btn-sm" disabled>Accept</Button><Button className="btn-sm" disabled>Ignore</Button></div>
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
      {listOpen === 'followers' && (followersQ.isPending ? <Skeleton /> : (followers.length === 0 ? <EmptyState title="No followers yet" body="Students who follow you will appear here." /> : followers.map((p: any) => personRow(p))))}
      {listOpen === 'following' && (followingQ.isPending ? <Skeleton /> : (following.length === 0 ? <EmptyState title="Not following anyone yet" body="Students you follow will appear here." /> : following.map((p: any) => personRow(p, <Button className="btn-sm" onClick={() => unfollowMut.mutate(p.userId)}>Unfollow</Button>))))}

      <h2 className="headline nw-section">Suggested students</h2>
      {preview ? (
        SAMPLE_SUGGESTIONS.map((s) => (
          <div key={s.id} className="nw-row">
            <span className="nw-avatar" aria-hidden="true">{initialsOf(s.name)}</span>
            <div className="nw-main"><p className="nw-name">{s.name}</p><p className="nw-meta">{s.meta}</p></div>
            <div className="nw-actions"><Button className="btn-sm" disabled>Follow</Button></div>
          </div>
        ))
      ) : suggestionsQ.isPending ? <Skeleton />
      : (suggestionsQ.data ?? []).length === 0 ? <EmptyState title="No suggestions right now" body="Suggestions appear here as more students from your department and batch join UniLink." />
      : (suggestionsQ.data ?? []).map((p: any) => personRow(p, p.followedByViewer
        ? <Button className="btn-sm" disabled={unfollowMut.isPending} onClick={() => unfollowMut.mutate(p.userId)}>Unfollow</Button>
        : <Button className="btn-sm" disabled={followMut.isPending} onClick={() => followMut.mutate(p.userId)}>Follow</Button>))}
      {followMut.isError && <p role="alert" className="field-error">{errorMessage(followMut.error)}</p>}
    </div>
  );
}
