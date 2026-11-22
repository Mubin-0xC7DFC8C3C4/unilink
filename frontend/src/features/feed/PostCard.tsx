import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, apiGet, apiPost, errorMessage } from '../../api/client';
import { IconBookmark, IconComment, IconDots, IconLike, IconShare, IconX } from '../../design/icons';
import { useAuth } from '../../app/auth';
import type { SamplePost } from './sampleFeed';

/** The normalised shape PostCard renders — Feed maps real API posts and
 *  (in Prototype Preview) sample posts onto it. */
export interface CardPost {
  id: string | number;
  title?: string;
  body?: string;
  authorId?: number;
  authorName: string;
  subtitle?: string;
  timeLabel?: string;
  scopeLabel?: string;
  contextLine?: string;
  media?: boolean;
  sectors: string[];
  reactionCount: number;
  commentCount?: number;
  viewerReaction?: string | null;
  savedByViewer?: boolean;
}

function initials(name: string) {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  return (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
}

/** The verified badge shown next to author names. Posting on UniLink
 *  requires a verified student account (the backend enforces it on every
 *  write), so a post's author is a verified student by platform design. */
export function VerifiedBadge() {
  return <span className="ch-verified"><span aria-hidden="true">✓</span> Verified</span>;
}

const REPORT_REASONS = [
  { value: 'spam', label: 'Spam' },
  { value: 'fake', label: 'Fake' },
  { value: 'abuse', label: 'Abuse' },
  { value: 'other', label: 'Other' },
];

const LONG_BODY = 300;

/** The five feed reactions (owner instruction 2026-11-21). The
 *  backend reaction contract accepts LIKE only today (PostService
 *  validates the value), so LIKE also syncs to POST/DELETE /reactions
 *  while the other four are held as the student's session state —
 *  exactly how the feed already treats viewer reaction state, since
 *  the feed DTO carries none. */
export const REACTIONS = [
  { key: 'LIKE', emoji: '👍', label: 'Like' },
  { key: 'CELEBRATE', emoji: '🎉', label: 'Celebrate' },
  { key: 'INSIGHTFUL', emoji: '💡', label: 'Insightful' },
  { key: 'SUPPORT', emoji: '🤝', label: 'Support' },
  { key: 'CURIOUS', emoji: '🤔', label: 'Curious' },
] as const;
export type ReactionKey = typeof REACTIONS[number]['key'];

export interface UiReply { key: string; authorName: string; timeLabel?: string; body: string; liked: boolean }
export interface UiComment { key: string; authorName: string; meta?: string; timeLabel?: string; body: string; liked: boolean; replies: UiReply[] }

function relTime(iso?: string): string | undefined {
  if (!iso) return undefined;
  const t = Date.parse(iso);
  if (Number.isNaN(t)) return undefined;
  const mins = Math.max(1, Math.round((Date.now() - t) / 60000));
  if (mins < 60) return `${mins}m`;
  const hours = Math.round(mins / 60);
  if (hours < 24) return `${hours}h`;
  return `${Math.round(hours / 24)}d`;
}

/**
 * One Community Home post card (LinkedIn-style structure, UniLink warm
 * monochrome): an optional context line, the author header (avatar, name
 * + verified badge, academic subtitle, timestamp · scope, Follow /
 * more-actions / hide controls), the body with a "...more" expansion,
 * an optional media block, a summary row (reaction count on the left,
 * comment count on the right) and a borderless action row — Like,
 * Comment, Share, Save — spread evenly across the card. Report lives
 * in the "..." menu.
 *
 * Real posts use the existing backend contracts: POST/DELETE
 * /reactions?reaction=LIKE, POST/DELETE /bookmarks, POST /reports,
 * POST/DELETE /profiles/{authorId}/follow, and comments read and written
 * inline through GET/POST /posts/{id}/comments. The feed response
 * carries no viewer state and no reaction counts, so Like and Follow
 * start inactive and reflect the student's own actions this session;
 * Save starts from the real GET /bookmarks list Feed passes in.
 *
 * Preview posts (Prototype Preview only) keep every interaction local.
 * Hide is a local, per-session dismissal in both modes (with Undo in
 * Feed). There is no repost endpoint in the backend, so the fourth
 * action is Share — the system share sheet, or the post's link copied
 * to the clipboard — never a decorative Repost button.
 */
export default function PostCard({ post, preview = false, sample, initiallySaved = false, isOwn = false, onHide }: { post: CardPost; preview?: boolean; sample?: SamplePost; initiallySaved?: boolean; isOwn?: boolean; onHide?: (id: string | number) => void }) {
  const qc = useQueryClient();
  const [reaction, setReaction] = useState<ReactionKey | null>(() => preview ? null : ((post.viewerReaction as ReactionKey | null) ?? null));
  const [likeCount, setLikeCount] = useState(post.reactionCount);
  const [trayOpen, setTrayOpen] = useState(false);
  const trayTimer = useRef<number | null>(null);
  const pressTimer = useRef<number | null>(null);
  const longPressed = useRef(false);
  const { user } = useAuth();
  const meName = user?.fullName || 'You';
  const [saved, setSaved] = useState(() => initiallySaved || (!preview && !!post.savedByViewer));
  const [following, setFollowing] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [reportOpen, setReportOpen] = useState(false);
  const [reportReason, setReportReason] = useState('spam');
  const [reported, setReported] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [commentsOpen, setCommentsOpen] = useState(false);
  const [shareNote, setShareNote] = useState<string | null>(null);
  const [expanded, setExpanded] = useState(false);
  const [previewComments, setPreviewComments] = useState<UiComment[]>(() => (sample?.comments ?? []).map((c, i) => ({ key: `s${i}`, authorName: c.authorName, meta: c.meta, timeLabel: c.timeLabel, body: c.body, liked: false, replies: [] })));
  const [likedComments, setLikedComments] = useState<Set<string>>(new Set());
  const [replyTo, setReplyTo] = useState<string | null>(null);
  const [replyDraft, setReplyDraft] = useState('');
  const [extraComments, setExtraComments] = useState(0);
  const [draft, setDraft] = useState('');

  const target = { targetType: 'POST', targetId: Number(post.id) };
  const reactMut = useMutation({
    mutationFn: (next: ReactionKey | null) => next
      ? apiPost(`/reactions?reaction=${next}`, target)
      : api('/reactions', { method: 'DELETE', body: JSON.stringify(target) }),
    onError: (e) => setActionError(errorMessage(e)),
  });
  /** Select or clear the student's reaction. Every transition syncs
   *  through the reactions endpoint (all five types persist server-side;
   *  the profile Activity feed reads them back from the API). */
  const applyReaction = (next: ReactionKey | null) => {
    setTrayOpen(false);
    const applyLocal = () => {
      if (reaction === null && next !== null) setLikeCount((c) => c + 1);
      if (reaction !== null && next === null) setLikeCount((c) => c - 1);
      setReaction(next);
      setActionError(null);
    };
    if (preview) { applyLocal(); return; }
    reactMut.mutate(next, { onSuccess: applyLocal });
  };
  const openTraySoon = () => { if (trayTimer.current) window.clearTimeout(trayTimer.current); trayTimer.current = window.setTimeout(() => setTrayOpen(true), 300); };
  const closeTraySoon = () => { if (trayTimer.current) window.clearTimeout(trayTimer.current); trayTimer.current = window.setTimeout(() => setTrayOpen(false), 250); };
  const saveMut = useMutation({
    mutationFn: () => saved
      ? api('/bookmarks', { method: 'DELETE', body: JSON.stringify(target) })
      : apiPost('/bookmarks', target),
    onSuccess: () => { setSaved(!saved); setActionError(null); qc.invalidateQueries({ queryKey: ['bookmarks'] }); },
    onError: (e) => setActionError(errorMessage(e)),
  });
  const reportMut = useMutation({
    mutationFn: () => apiPost('/reports', { ...target, reason: reportReason }),
    onSuccess: () => { setReported(true); setReportOpen(false); setActionError(null); },
    onError: (e) => setActionError(errorMessage(e)),
  });
  const followMut = useMutation({
    mutationFn: () => following
      ? api(`/profiles/${post.authorId}/follow`, { method: 'DELETE' })
      : apiPost(`/profiles/${post.authorId}/follow`, {}),
    onSuccess: () => { setFollowing(!following); setActionError(null); },
    onError: (e) => setActionError(errorMessage(e)),
  });
  const commentsQ = useQuery({
    queryKey: ['post-comments', post.id],
    queryFn: () => apiGet<any>(`/posts/${post.id}/comments`),
    enabled: commentsOpen && !preview,
    retry: false,
  });
  const realComments: { id?: number; authorName?: string; body: string; createdAt?: string; parentId?: number | null }[] = Array.isArray(commentsQ.data) ? commentsQ.data : (commentsQ.data?.items ?? []);
  const addCommentMut = useMutation({
    mutationFn: async () => { const text = draft.trim(); await apiPost(`/posts/${post.id}/comments`, { body: text, mentionUserIds: [] as number[] }); return text; },
    onSuccess: () => { setDraft(''); setExtraComments((c) => c + 1); qc.invalidateQueries({ queryKey: ['post-comments', post.id] }); },
    onError: (e) => setActionError(errorMessage(e)),
  });

  const toggleSave = () => {
    setMenuOpen(false);
    if (preview) { setSaved(!saved); return; }
    saveMut.mutate();
  };
  const toggleFollow = () => {
    if (preview) { setFollowing(!following); return; }
    followMut.mutate();
  };
  const submitReport = () => {
    if (preview) { setReported(true); setReportOpen(false); return; }
    reportMut.mutate();
  };
  // Share is the one repost-adjacent action the platform can perform
  // honestly today: the system share sheet where available, otherwise
  // the post's link copied to the clipboard. (There is no repost
  // endpoint in the backend.)
  const sharePost = async () => {
    const url = `${window.location.origin}/posts/${post.id}`;
    try {
      if (navigator.share) { await navigator.share({ title: post.title || 'UniLink post', url }); setShareNote('Shared.'); }
      else if (navigator.clipboard) { await navigator.clipboard.writeText(url); setShareNote('Link copied — share it anywhere.'); }
    } catch { /* the student dismissed the share sheet */ }
  };

  const displayComments: UiComment[] = preview ? previewComments : realComments.filter((c) => !c.parentId).map((c, i) => { const key = String(c.id ?? `r${i}`); const replies: UiReply[] = realComments.filter((r) => r.parentId != null && String(r.parentId) === key).map((r) => ({ key: String(r.id), authorName: r.authorName || 'Student', timeLabel: relTime(r.createdAt), body: r.body, liked: likedComments.has(String(r.id)) })); return { key, authorName: c.authorName || 'Student', timeLabel: relTime(c.createdAt), body: c.body, liked: likedComments.has(key), replies }; });
  const replyTotal = displayComments.reduce((n, c) => n + c.replies.length, 0);
  const commentCount = preview ? previewComments.length + replyTotal : (post.commentCount ?? 0) + extraComments + replyTotal;
  const summaryEmojis = (() => { const list: string[] = []; const mine = REACTIONS.find((r) => r.key === reaction); if (mine) list.push(mine.emoji); if (!list.includes('👍')) list.push('👍'); if (likeCount >= 10 && !list.includes('💡')) list.push('💡'); return list.slice(0, 3); })();
  const myReaction = REACTIONS.find((r) => r.key === reaction);
  const toggleCommentLike = (key: string) => {
    if (preview) { setPreviewComments((cs) => cs.map((c) => c.key === key ? { ...c, liked: !c.liked } : c)); return; }
    setLikedComments((prev) => { const next = new Set(prev); if (next.has(key)) next.delete(key); else next.add(key); return next; });
  };
  const toggleReplyLike = (commentKey: string, replyKey: string) => {
    const flip = (r: UiReply) => r.key === replyKey ? { ...r, liked: !r.liked } : r;
    if (preview) { setPreviewComments((cs) => cs.map((c) => c.key === commentKey ? { ...c, replies: c.replies.map(flip) } : c)); return; }
    setLikedComments((prev) => { const next = new Set(prev); if (next.has(replyKey)) next.delete(replyKey); else next.add(replyKey); return next; });
  };
  const submitReply = (commentKey: string) => {
    const text = replyDraft.trim();
    if (!text) return;
    if (preview) {
      const reply: UiReply = { key: `u${Date.now()}`, authorName: meName, timeLabel: 'Just now', body: text, liked: false };
      setPreviewComments((cs) => cs.map((c) => c.key === commentKey ? { ...c, replies: [...c.replies, reply] } : c));
      setReplyDraft(''); setReplyTo(null);
      return;
    }
    // Replies persist through the comments API with parentId; the
    // thread re-renders from the refetched list.
    apiPost(`/posts/${post.id}/comments`, { body: text, mentionUserIds: [] as number[], parentId: Number(commentKey) })
      .then(() => { setReplyDraft(''); setReplyTo(null); setExtraComments((c) => c + 1); qc.invalidateQueries({ queryKey: ['post-comments', post.id] }); })
      .catch((e) => setActionError(errorMessage(e)));
  };
  const metaText = [post.timeLabel, post.scopeLabel].filter(Boolean).join(' · ');
  const longBody = (post.body?.length ?? 0) > LONG_BODY;
  const showFollow = preview || (!!post.authorId && !isOwn);

  return (
    <article className="ch-card">
      {post.contextLine && <p className="ch-context">{post.contextLine}</p>}
      <div className="ch-card-head">
        <span className="ch-avatar" aria-hidden="true">{initials(post.authorName)}</span>
        <div className="ch-head-main">
          <p className="ch-author"><span className="ch-author-name">{post.authorName}</span><VerifiedBadge /></p>
          {post.subtitle && <p className="ch-meta">{post.subtitle}</p>}
          <p className="ch-meta">{metaText}</p>
        </div>
        <div className="ch-head-actions">
          {showFollow && <button type="button" className="ch-follow-btn" aria-pressed={following} onClick={toggleFollow} disabled={!preview && followMut.isPending}>{following ? 'Following' : '+ Follow'}</button>}
          <div className="ch-menu-wrap">
            <button type="button" className="ch-icon-btn" aria-label="More actions" aria-haspopup="menu" aria-expanded={menuOpen} onClick={() => setMenuOpen((o) => !o)}><IconDots /></button>
            {menuOpen && <>
              <div className="ch-menu-scrim" onClick={() => setMenuOpen(false)} aria-hidden="true" />
              <div className="ch-menu" role="menu">
                <button type="button" role="menuitem" onClick={toggleSave}>{saved ? 'Saved' : 'Save post'}</button>
                <button type="button" role="menuitem" onClick={() => { setMenuOpen(false); setReportOpen(true); }}>Report post</button>
              </div>
            </>}
          </div>
          {onHide && <button type="button" className="ch-icon-btn" aria-label="Hide post" onClick={() => onHide(post.id)}><IconX /></button>}
        </div>
      </div>
      <h3 className="ch-title">{preview ? (post.title || 'Untitled post') : <Link to={`/posts/${post.id}`}>{post.title || 'Untitled post'}</Link>}</h3>
      {post.body && <p className={`ch-body${longBody && !expanded ? ' clamped' : ''}`}>{post.body}</p>}
      {longBody && <button type="button" className="ch-more" onClick={() => setExpanded((x) => !x)}>{expanded ? 'Show less' : '...more'}</button>}
      {post.media && <div className="ch-media" aria-hidden="true" />}
      {(likeCount > 0 || commentCount > 0) && (
        <div className="ch-summary-row">
          <span className="ch-summary-left">{likeCount > 0 && <>{summaryEmojis.join('')} {likeCount}</>}</span>
          <span>{commentCount > 0 ? `${commentCount} comment${commentCount === 1 ? '' : 's'}` : ''}</span>
        </div>
      )}
      <div className="ch-actions">
        <div className="ch-like-wrap"
          onMouseEnter={openTraySoon} onMouseLeave={closeTraySoon}>
          {trayOpen && (
            <div className="ch-tray" role="group" aria-label="Choose a reaction">
              {REACTIONS.map((r) => (
                <button key={r.key} type="button" title={r.label} aria-label={`React: ${r.label}`} onClick={() => applyReaction(r.key)}>{r.emoji}</button>
              ))}
            </div>
          )}
          <button type="button" aria-pressed={!!reaction} className={reaction ? 'ch-action is-active' : 'ch-action'}
            onPointerDown={() => { longPressed.current = false; pressTimer.current = window.setTimeout(() => { longPressed.current = true; setTrayOpen(true); }, 450); }}
            onPointerUp={() => { if (pressTimer.current) window.clearTimeout(pressTimer.current); }}
            onPointerLeave={() => { if (pressTimer.current) window.clearTimeout(pressTimer.current); }}
            onClick={() => { if (longPressed.current) { longPressed.current = false; return; } applyReaction(reaction ? null : 'LIKE'); }}
            disabled={!preview && reactMut.isPending}>
            {myReaction ? <>{myReaction.emoji} {myReaction.label}</> : <><IconLike size={16} /> Like</>}
          </button>
        </div>
        <button type="button" className="ch-action" aria-expanded={commentsOpen} onClick={() => setCommentsOpen((o) => !o)}><IconComment size={16} /> Comment</button>
        <button type="button" className="ch-action" onClick={sharePost}><IconShare size={16} /> Share</button>
        <button type="button" aria-pressed={saved} className={saved ? 'ch-action is-active' : 'ch-action'} onClick={toggleSave} disabled={!preview && saveMut.isPending}><IconBookmark size={15} /> {saved ? 'Saved' : 'Save'}</button>
      </div>
      {shareNote && <p role="status" className="ch-note">{shareNote}</p>}
      {actionError && <p role="alert" className="field-error">{actionError}</p>}
      {reportOpen && !reported && (
        <div className="ch-report">
          <label htmlFor={`report-reason-${post.id}`}>Report reason</label>
          <select id={`report-reason-${post.id}`} value={reportReason} onChange={(e) => setReportReason(e.target.value)} className="input-search">
            {REPORT_REASONS.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
          </select>
          <button type="button" className="btn btn-sm btn-primary" onClick={submitReport} disabled={!preview && reportMut.isPending}>Submit report</button>
          <button type="button" className="btn btn-sm" onClick={() => setReportOpen(false)}>Cancel</button>
        </div>
      )}
      {reported && <p role="status" className="ch-note">Reported.</p>}
      {commentsOpen && (
        <div className="ch-comments">
          <form className="ch-composer" onSubmit={(e) => { e.preventDefault(); if (!draft.trim()) return; if (preview) { setPreviewComments((cs) => [...cs, { key: `u${Date.now()}`, authorName: meName, timeLabel: 'Just now', body: draft.trim(), liked: false, replies: [] }]); setDraft(''); } else { addCommentMut.mutate(); } }}>
            <span className="ch-avatar ch-avatar-sm" aria-hidden="true">{initials(meName)}</span>
            <input aria-label="Add a comment" value={draft} onChange={(e) => setDraft(e.target.value)} className="input-search" placeholder="Add a comment..." />
            <button type="submit" className="btn btn-sm btn-primary" disabled={!draft.trim() || (!preview && addCommentMut.isPending)}>Post</button>
          </form>
          {!preview && commentsQ.isPending
            ? <p className="secondary">Loading comments…</p>
            : displayComments.length === 0
              ? <p className="secondary">No comments yet. Start the discussion.</p>
              : <ul className="ch-comment-list">{displayComments.map((c) => (
                <li key={c.key} className="ch-comment">
                  <span className="ch-avatar ch-avatar-sm" aria-hidden="true">{initials(c.authorName)}</span>
                  <div className="ch-comment-main">
                    <p className="ch-comment-head"><strong>{c.authorName}</strong><VerifiedBadge /></p>
                    {(c.meta || c.timeLabel) && <p className="ch-meta">{[c.meta, c.timeLabel].filter(Boolean).join(' · ')}</p>}
                    <p className="ch-comment-body">{c.body}</p>
                    <div className="ch-comment-actions">
                      <button type="button" aria-pressed={c.liked} className={c.liked ? 'is-active' : ''} onClick={() => toggleCommentLike(c.key)}>Like</button>
                      <button type="button" aria-expanded={replyTo === c.key} onClick={() => { setReplyTo(replyTo === c.key ? null : c.key); setReplyDraft(''); }}>Reply</button>
                    </div>
                    {c.replies.length > 0 && <ul className="ch-replies">{c.replies.map((r) => (
                      <li key={r.key} className="ch-comment">
                        <span className="ch-avatar ch-avatar-sm" aria-hidden="true">{initials(r.authorName)}</span>
                        <div className="ch-comment-main">
                          <p className="ch-comment-head"><strong>{r.authorName}</strong><VerifiedBadge /></p>
                          {r.timeLabel && <p className="ch-meta">{r.timeLabel}</p>}
                          <p className="ch-comment-body">{r.body}</p>
                          <div className="ch-comment-actions">
                            <button type="button" aria-pressed={r.liked} className={r.liked ? 'is-active' : ''} onClick={() => toggleReplyLike(c.key, r.key)}>Like</button>
                          </div>
                        </div>
                      </li>
                    ))}</ul>}
                    {replyTo === c.key && (
                      <form className="ch-reply-form" onSubmit={(e) => { e.preventDefault(); submitReply(c.key); }}>
                        <span className="ch-avatar ch-avatar-sm" aria-hidden="true">{initials(meName)}</span>
                        <input aria-label={`Reply to ${c.authorName}`} value={replyDraft} onChange={(e) => setReplyDraft(e.target.value)} className="input-search" placeholder={`Reply to ${c.authorName}...`} autoFocus />
                        <button type="submit" className="btn btn-sm btn-primary" disabled={!replyDraft.trim()}>Post</button>
                      </form>
                    )}
                  </div>
                </li>
              ))}</ul>}
        </div>
      )}
    </article>
  );
}
