import { useState } from 'react'; import { useParams, Link, useNavigate } from 'react-router-dom'; import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiGet, apiPost, apiDelete, errorMessage } from '../../api/client'; import { Skeleton, ErrorState, ApiUnavailable, Button, Modal } from '../../design/primitives';
import { useAuth } from '../../app/auth';
import { parseLinks } from './links';
import { LiveRepoViewer } from '../workspace/RepoViewer';
import type { UserProfile, ProjectItem } from '../../api/types';
/** Profile reads use /api/profiles/… (GET /profiles/me, GET /profiles/{id}),
 *  follow is POST/DELETE /profiles/{id}/follow, activity is
 *  GET /profiles/{id}/activity-items.
 *  Layout (owner reference, 2026-11-21): warm cover banner with an
 *  overlapping avatar, name + "✓ Verified student", headline, academic
 *  line; below, a two-column body — About Me / Skills / Activity on the
 *  left, Links on the right. The page is a READ-ONLY showcase (owner
 *  instruction 2026-11-21): all editing lives in Settings → Edit Profile.
 *  D-202: the Education and Achievements sections were removed from this
 *  page (the portfolio manager in Settings and its endpoints are
 *  untouched). D-203 re-adds Pinned Projects — but ONLY for project rows
 *  pinned to a real GitHub repository (githubOwner/githubRepo set); those
 *  open in the LiveRepoViewer with real GitHub data, never the retired
 *  fixture viewer. The section is hidden entirely when there is nothing
 *  pinned. The uploaded cover is rendered from GET /profiles/{id}/cover,
 *  falling back to the solid banner. Every value shown comes from the
 *  profile DTO; fields the API does not carry (repository language/
 *  stars/forks, location) are not shown. */
const PF_CSS = `
.pf-cover{height:148px;background:#E8E0C8;border-radius:var(--radius-md) var(--radius-md) 0 0}
.pf-cover-img{display:block;width:100%;height:148px;object-fit:cover;border-radius:var(--radius-md) var(--radius-md) 0 0}
[data-theme="dark"] .pf-cover{background:#38331F}
.pf-head{display:flex;gap:16px;align-items:flex-start;padding:0 20px}
.pf-avatar{width:92px;height:92px;border-radius:50%;background:var(--primary);color:var(--on-primary);display:inline-flex;align-items:center;justify-content:center;font-weight:600;font-size:1.5rem;border:4px solid var(--background);box-sizing:content-box;margin-top:-46px;flex:none}
.pf-photo{width:92px;height:92px;border-radius:50%;object-fit:cover;border:4px solid var(--background);margin-top:-46px;flex:none}
.pf-head-main{flex:1;min-width:0;padding-top:12px}
.pf-name{font-size:1.45rem;font-weight:650;margin:0}
.pf-badge{display:inline-flex;gap:3px;align-items:center;border:1px solid var(--border);border-radius:999px;padding:1px 8px;font-size:.72rem;font-weight:600;color:var(--text-secondary);margin-left:8px;vertical-align:middle;white-space:nowrap}
.pf-headline{color:var(--text-secondary);margin:4px 0 0}
.pf-academic{color:var(--text-secondary);font-size:.9rem;margin:2px 0 0}
.pf-counts{color:var(--text-secondary);font-size:.88rem;margin:6px 0 0}
.pf-counts strong{color:var(--text-primary)}
.pf-head-action{padding-top:14px;flex:none}
.pf-grid{display:grid;grid-template-columns:minmax(0,1fr) 320px;gap:20px;margin-top:22px;align-items:start}
.pf-proj-btn{display:block;width:100%;text-align:left;background:none;border:none;border-bottom:1px solid var(--border);cursor:pointer;color:inherit;font:inherit;padding:2px 0 12px;margin-bottom:12px}
.pf-proj-top{display:flex;gap:8px;align-items:center}
.pf-public{border:1px solid var(--border);border-radius:999px;padding:0 8px;font-size:.7rem;font-weight:600}
.pf-proj-open{color:var(--text-secondary);font-size:.85rem}
.pf-signout{margin-top:20px;display:flex;gap:16px;align-items:center;justify-content:space-between;flex-wrap:wrap}
.pf-repo-backdrop{position:fixed;inset:0;background:color-mix(in srgb, var(--text-primary) 45%, transparent);display:flex;align-items:flex-start;justify-content:center;overflow:auto;padding:32px 16px;z-index:60}
.pf-repo-modal{position:relative;background:var(--surface);border:1px solid var(--border);border-radius:var(--radius-md);padding:24px;width:min(1060px,96vw)}
.pf-repo-modal .rv-head{padding-right:38px}
.pf-repo-close{position:absolute;top:10px;right:12px;background:none;border:none;font-size:1.35rem;cursor:pointer;color:inherit;line-height:1;padding:6px;z-index:2}
@media (max-width:900px){.pf-grid{grid-template-columns:1fr}}
.pf-card{border:1px solid var(--border);border-radius:var(--radius-md);padding:16px;margin-bottom:16px}
.pf-card h2{font-size:1.05rem;margin:0 0 10px}
.pf-pills{display:flex;gap:6px;flex-wrap:wrap}
.pf-proj{border-top:1px solid var(--border);padding:11px 0}
.pf-proj:first-of-type{border-top:none;padding-top:2px}
.pf-proj p{margin:3px 0 0;color:var(--text-secondary)}
.pf-row{border-top:1px solid var(--border);padding:9px 0}
.pf-row:first-of-type{border-top:none;padding-top:2px}
.pf-row p{margin:2px 0 0;color:var(--text-secondary)}
`;
function initialsOf(name: string){ const p=name.trim().split(/\s+/).filter(Boolean); return p.length? (p[0][0]+(p.length>1?p[p.length-1][0]:'')).toUpperCase() : '?'; }

export default function ProfilePage(){ const {id}=useParams(); const isMe = id==='me';
  const nav = useNavigate(); const { setUser } = useAuth();
  const [signOutOpen, setSignOutOpen] = useState(false);
  // Mirrors the header menu's sign-out: the server session is ended,
  // local state is cleared either way, and the student lands on /login.
  const doSignOut = async () => { try { await apiPost('/auth/logout'); } catch { /* local sign-out still proceeds */ } setUser(null); setSignOutOpen(false); nav('/login'); };
  const qc=useQueryClient();
  const [photoOk,setPhotoOk]=useState(true);
  const [coverOk,setCoverOk]=useState(true);
  const q = useQuery({ queryKey:['user',id], queryFn: ()=>apiGet<UserProfile>(isMe?'/profiles/me':`/profiles/${id}`), retry:false });
  const uid = isMe ? (q.data?.userId ?? q.data?.id) : id;
  // D-200: mutual connection status with this student (Connect / Accept
  // / Pending / Cancel / Connected + Remove). Own profile shows none.
  const connQ = useQuery({ queryKey:['connection-status', uid], queryFn: ()=>apiGet<{status:string; requestId?:number|null}>(`/connections/status/${uid}`), retry:false, enabled: !isMe && uid!=null });
  const connStatus = connQ.data?.status;
  const connRequestId = connQ.data?.requestId;
  const connect = useMutation({ mutationFn: ()=>apiPost('/connections/requests',{addresseeId: Number(uid)}), onSuccess: ()=>qc.invalidateQueries({queryKey:['connection-status', uid]}) });
  const acceptConn = useMutation({ mutationFn: ()=>apiPost(`/connections/requests/${connRequestId}/accept`,{}), onSuccess: ()=>qc.invalidateQueries({queryKey:['connection-status', uid]}) });
  const rejectConn = useMutation({ mutationFn: ()=>apiPost(`/connections/requests/${connRequestId}/reject`,{}), onSuccess: ()=>qc.invalidateQueries({queryKey:['connection-status', uid]}) });
  const cancelConn = useMutation({ mutationFn: ()=>apiPost(`/connections/requests/${connRequestId}/cancel`,{}), onSuccess: ()=>qc.invalidateQueries({queryKey:['connection-status', uid]}) });
  const removeConn = useMutation({ mutationFn: ()=>apiDelete(`/connections/${uid}`), onSuccess: ()=>qc.invalidateQueries({queryKey:['connection-status', uid]}) });
  const activity = useQuery({ queryKey:['user-activity-items',id], queryFn: ()=>apiGet<any[]>(isMe?'/profiles/me/activity-items':`/profiles/${id}/activity-items`), retry:false, enabled: !!uid });
  // D-203: pinned GitHub repositories. Own profile reads /profiles/me/
  // projects (never /profiles/{numeric id}/projects); other profiles
  // read /profiles/{id}/projects. Only rows with githubOwner+githubRepo
  // are shown — manual free-text projects stay in Settings.
  const [openPinned, setOpenPinned] = useState<ProjectItem | null>(null);
  const projectsQ = useQuery({ queryKey:['user-projects',id], queryFn: ()=>apiGet<ProjectItem[]>(isMe?'/profiles/me/projects':`/profiles/${id}/projects`), retry:false, enabled: !!uid });
  const pinned = (projectsQ.data ?? []).filter((p) => p.githubOwner && p.githubRepo);
  const unpin = useMutation({ mutationFn: (pid: string|number)=>apiDelete(`/profiles/me/projects/${pid}/pin`), onSuccess: ()=>qc.invalidateQueries({queryKey:['user-projects',id]}) });
  const follow = useMutation({ mutationFn: ()=>apiPost(`/profiles/${id}/follow`,{}), onSuccess: ()=>qc.invalidateQueries({queryKey:['user',id]}) });
  const unfollow = useMutation({ mutationFn: ()=>apiDelete(`/profiles/${id}/follow`), onSuccess: ()=>qc.invalidateQueries({queryKey:['user',id]}) });
  const u = q.data||{} as UserProfile;
  const skills = [...new Set([...(u.skills||[]), ...(u.sectors||[])])];
  return <div className="container"><style>{PF_CSS}</style>{q.isPending?<Skeleton/>:q.isError?(q.error as any)?.status===404?<ApiUnavailable endpoint={isMe?'/profiles/me':`/profiles/${id}`} onRetry={()=>q.refetch()}/>:<ErrorState message={errorMessage(q.error)} onRetry={()=>q.refetch()}/>:<>
  {uid!=null && u.hasCover && coverOk ? <img className="pf-cover-img" src={`/api/profiles/${uid}/cover`} alt="" onError={()=>setCoverOk(false)} /> : <div className="pf-cover" aria-hidden="true" />}
  <div className="pf-head">
    {uid!=null && photoOk ? <img className="pf-photo" src={`/api/profiles/${uid}/photo`} alt="" onError={()=>setPhotoOk(false)} /> : <span className="pf-avatar" aria-hidden="true">{initialsOf(u.fullName||'?')}</span>}
    <div className="pf-head-main">
      <h1 className="pf-name">{u.fullName||'Profile'}{(u.state==='VERIFIED'||u.verified) && <span className="pf-badge">✓ Verified student</span>}</h1>
      {u.headline && <p className="pf-headline">{u.headline}</p>}
      <p className="pf-academic">{[u.departmentName||u.department, u.universityName||u.university, u.batchLabel||u.batch].filter(Boolean).join(' · ')}</p>
      {(typeof u.followers==='number' || typeof u.following==='number') && <p className="pf-counts"><strong>{u.followers??0}</strong> Followers · <strong>{u.following??0}</strong> Following</p>}
    </div>
    {!isMe && <div className="pf-head-action">
      <Button variant="primary" onClick={()=>follow.mutate()} disabled={follow.isPending}>{follow.isSuccess?'Following':'Follow'}</Button>{' '}<Button onClick={()=>unfollow.mutate()} disabled={unfollow.isPending}>Unfollow</Button>{' '}
      {connStatus==='CONNECTED' && <><span className="pf-badge">✓ Connected</span>{' '}<Button onClick={()=>removeConn.mutate()} disabled={removeConn.isPending}>Remove connection</Button></>}
      {connStatus==='PENDING_OUTGOING' && <><span className="pf-badge">Request sent</span>{' '}<Button onClick={()=>cancelConn.mutate()} disabled={cancelConn.isPending || connRequestId==null}>Cancel request</Button></>}
      {connStatus==='PENDING_INCOMING' && <><Button variant="primary" onClick={()=>acceptConn.mutate()} disabled={acceptConn.isPending || connRequestId==null}>Accept connection</Button>{' '}<Button onClick={()=>rejectConn.mutate()} disabled={rejectConn.isPending || connRequestId==null}>Decline</Button></>}
      {connStatus==='NONE' && <Button onClick={()=>connect.mutate()} disabled={connect.isPending}>Connect</Button>}
    </div>}
  </div>
  {!isMe && (follow.isError||unfollow.isError) && <p role="alert" className="field-error">{errorMessage(follow.error||unfollow.error)}</p>}
  {!isMe && (connect.isError||acceptConn.isError||rejectConn.isError||cancelConn.isError||removeConn.isError) && <p role="alert" className="field-error">{errorMessage(connect.error||acceptConn.error||rejectConn.error||cancelConn.error||removeConn.error)}</p>}
  <div className="pf-grid">
    <div>
      <section className="pf-card"><h2>About Me</h2>{u.bio ? <p style={{margin:0}}>{u.bio}</p> : <p className="secondary" style={{margin:0}}>{isMe?'Add a short bio with Edit Profile.':'No bio yet.'}</p>}</section>
      <section className="pf-card"><h2>Skills</h2>{skills.length===0 ? <p className="secondary" style={{margin:0}}>No skills listed yet.</p> : <div className="pf-pills">{skills.map(s=><span key={s} className="pill">{s}</span>)}</div>}</section>
      {pinned.length > 0 && <section className="pf-card"><h2>Pinned Projects</h2>
        {pinned.map((p) => (
          <div key={p.id} className="pf-proj">
            <button type="button" className="pf-proj-btn" onClick={() => setOpenPinned(p)}>
              <span className="pf-proj-top"><strong>{p.githubOwner} / {p.githubRepo}</strong><span className="pf-public">Public</span></span>
              {p.description && <p>{p.description}</p>}
              <span className="pf-proj-open">Open repository →</span>
            </button>
            {isMe && <Button disabled={unpin.isPending} onClick={() => unpin.mutate(p.id)}>Unpin</Button>}
          </div>
        ))}
        {unpin.isError && <p role="alert" className="field-error">{errorMessage(unpin.error)}</p>}
      </section>}
      <section className="pf-card"><h2>Activity</h2>
        {activity.isPending?<Skeleton/>:activity.isError?<ErrorState message={errorMessage(activity.error)} onRetry={()=>activity.refetch()}/>:(()=>{
          // Unified activity from the API: posts, comments and
          // reactions in one chronological stream (GET
          // /profiles/{id}/activity-items) — real server data for
          // every profile, not a browser-side record.
          const firstName = (u.fullName||'You').split(' ')[0];
          const fmt = (t:number) => new Date(t).toLocaleString('en-GB', { day:'numeric', month:'short', hour:'numeric', minute:'2-digit' });
          const items = (activity.data||[]).map((a:any, i:number)=>{
            const at = a.createdAt?Date.parse(a.createdAt):0;
            if (a.kind==='reaction') return { key:`rx-${i}`, node: <div className="pf-row"><p style={{margin:0}}><strong>{firstName}</strong> reacted {a.reaction?`(${a.reaction}) `:''}to a post</p>{a.postTitle && <p><Link to={`/posts/${a.postId}`}>{a.postTitle}</Link></p>}<p>{fmt(at)}</p></div> };
            if (a.kind==='comment') return { key:`cm-${i}`, node: <div className="pf-row"><p style={{margin:0}}><strong>{firstName}</strong> commented on a post</p>{a.text && <p>“{a.text}”</p>}{a.postTitle && <p><Link to={`/posts/${a.postId}`}>{a.postTitle}</Link></p>}<p>{fmt(at)}</p></div> };
            return { key:`po-${i}`, node: <div className="pf-row"><Link to={`/posts/${a.postId}`}>{a.postTitle||'Post'}</Link><p>{fmt(at)}</p></div> };
          });
          return items.length===0 ? <p className="secondary" style={{margin:0}}>No activity yet.</p> : items.map((i)=><div key={i.key}>{i.node}</div>);
        })()}
      </section>
    </div>
    <div>
      <section className="pf-card"><h2>Links</h2>
        {(() => { const L = parseLinks(u.links); const rows: [string, string][] = [];
          if (L.github) rows.push(['GitHub', L.github]); if (L.portfolio) rows.push(['Portfolio', L.portfolio]); if (L.linkedin) rows.push(['LinkedIn', L.linkedin]);
          L.other.forEach((o) => rows.push(['', o]));
          return rows.length === 0 ? <p className="secondary" style={{margin:'0 0 8px'}}>{isMe?'Add your links in Settings → Edit Profile.':'No links shared.'}</p>
            : rows.map(([label, url], i) => <p key={i} style={{margin:'0 0 6px'}}>{label && <strong>{label}: </strong>}{/^https?:\/\//.test(url) ? <a href={url} target="_blank" rel="noreferrer">{url}</a> : url}</p>);
        })()}
        <p style={{margin:0}}><Link to="/workspace">GitHub — connect in Workspace →</Link></p>
      </section>
    </div>
  </div>
  {isMe && <section className="pf-card pf-signout"><div><h2 style={{margin:'0 0 4px'}}>Sign out</h2><p className="secondary" style={{margin:0}}>Sign out of UniLink on this device.</p></div><Button onClick={()=>setSignOutOpen(true)}>Sign out</Button></section>}
  {signOutOpen && <Modal title="Sign out of UniLink?" onClose={()=>setSignOutOpen(false)}>
    <p>Are you sure you want to sign out? You will need to log in again to access your account.</p>
    <div style={{display:'flex',gap:8,marginTop:14}}><Button onClick={()=>setSignOutOpen(false)}>Cancel</Button><Button variant="primary" onClick={doSignOut}>Sign out</Button></div>
  </Modal>}
  {openPinned && <div className="pf-repo-backdrop" onClick={()=>setOpenPinned(null)}>
    <div className="pf-repo-modal" role="dialog" aria-label={`${openPinned.githubOwner} / ${openPinned.githubRepo}`} onClick={(e)=>e.stopPropagation()}>
      <button type="button" className="pf-repo-close" aria-label="Close repository" onClick={()=>setOpenPinned(null)}>×</button>
      <LiveRepoViewer owner={openPinned.githubOwner!} name={openPinned.githubRepo!} />
    </div>
  </div>}
  </>}</div>;
}
