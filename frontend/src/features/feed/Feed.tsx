import { useState } from 'react'; import { Link } from 'react-router-dom'; import { useQuery } from '@tanstack/react-query';
import { apiGet } from '../../api/client'; import { type Post, type UserProfile, type EventItem } from '../../api/types';
import { Button, EmptyState, Skeleton } from '../../design/primitives';
import PostCard, { type CardPost } from './PostCard';
import CommunitySidebar, { useCommunitySidebar } from './CommunitySidebar';
import ComposerModal, { type PreviewPostDraft } from './ComposerModal';
import { SAMPLE_POSTS, SAMPLE_SUGGESTED, type SampleStudent } from './sampleFeed';


function formatDate(iso?: string){ if(!iso) return undefined; const d=new Date(iso); if(Number.isNaN(d.getTime())) return undefined; return d.toLocaleDateString('en-GB',{day:'numeric',month:'short',year:'numeric'}); }
function initialsOf(name: string){ const p=name.trim().split(/\s+/).filter(Boolean); return p.length? (p[0][0]+(p.length>1?p[p.length-1][0]:'')).toUpperCase() : '?'; }
function scopeLabel(visibility?: string){ switch(visibility){ case 'UNIVERSITY': return 'University'; case 'DEPARTMENT_HUB': return 'National Department Hub'; case 'NATIONAL': return 'National'; default: return 'Department'; } }
const CH_CSS = `
.ch-layout{display:grid;grid-template-columns:264px minmax(0,1fr) 300px;gap:20px;align-items:start;margin-top:16px}
.ch-layout.collapsed{grid-template-columns:68px minmax(0,1fr) 300px}
.ch-trigger{border:1px solid var(--border);border-radius:var(--radius-md);padding:14px 16px;margin-top:20px}
.ch-trigger-row{display:flex;align-items:center;gap:10px}
.ch-trigger-pill{flex:1;border:1px solid var(--border);border-radius:999px;padding:10px 16px;background:none;cursor:pointer;color:var(--text-secondary);font:inherit;font-size:.9rem;text-align:left}
.ch-trigger-pill:hover{background:var(--surface-muted)}
.ch-sort{display:flex;align-items:center;gap:10px;margin-top:16px}
.ch-sort-line{flex:1;border-top:1px solid var(--border)}
.ch-sort label{display:inline-flex;align-items:center;gap:6px;font-size:.85rem;color:var(--text-secondary)}
.ch-sort select{font:inherit;font-size:.85rem;color:inherit}
.ch-post-note{display:flex;gap:10px;align-items:center;justify-content:space-between;border:1px solid var(--border);border-radius:var(--radius-md);padding:8px 12px;font-size:.9rem;margin-top:12px}
.ch-post-note button{background:none;border:none;cursor:pointer;color:inherit;font:inherit}
.ch-hidden-bar{display:flex;gap:10px;align-items:center;justify-content:space-between;border:1px dashed var(--border);border-radius:var(--radius-md);padding:10px 16px;margin-bottom:16px;font-size:.9rem;color:var(--text-secondary)}
.ch-hidden-bar button{background:none;border:1px solid var(--border);border-radius:999px;padding:3px 12px;cursor:pointer;color:inherit;font:inherit;font-size:.82rem}
.cm-backdrop{position:fixed;inset:0;background:rgba(20,20,15,.45);z-index:80;display:flex;align-items:flex-start;justify-content:center;padding:48px 16px;overflow:auto}
.cm-modal{position:relative;background:var(--background);border:1px solid var(--border);border-radius:var(--radius-md);width:100%;max-width:560px;padding:20px;box-shadow:0 16px 48px rgba(0,0,0,.25)}
.cm-close{position:absolute;top:12px;right:12px;background:none;border:none;cursor:pointer;color:inherit;display:inline-flex;padding:6px}
.cm-head{display:flex;gap:10px;align-items:center;margin-bottom:12px}
.cm-name{font-weight:600;margin:0}
.cm-title{width:100%;margin-bottom:8px;box-sizing:border-box}
.cm-body{width:100%;border:none;resize:vertical;font:inherit;padding:4px 0;box-sizing:border-box;background:none;color:inherit}
.cm-body:focus{outline:none}
.cm-files{display:flex;gap:8px;flex-wrap:wrap;margin-top:10px}
.cm-file{display:inline-flex;gap:6px;align-items:center;border:1px solid var(--border);border-radius:var(--radius-sm);padding:4px 8px;font-size:.82rem}
.cm-file button{background:none;border:none;cursor:pointer;color:inherit;font:inherit;padding:0 2px}
.cm-thumb{width:36px;height:36px;object-fit:cover;border-radius:4px}
.cm-foot{display:flex;align-items:center;justify-content:space-between;gap:10px;border-top:1px solid var(--border);margin-top:14px;padding-top:12px}
.cm-attach{display:flex;gap:4px}
.cm-attach button{background:none;border:1px solid var(--border);border-radius:50%;width:36px;height:36px;display:inline-flex;align-items:center;justify-content:center;cursor:pointer;color:inherit}
.ch-context{font-size:.85rem;color:var(--text-secondary);margin:0 0 10px;padding-bottom:10px;border-bottom:1px solid var(--border)}
.ch-head-main{min-width:0;flex:1}
.ch-head-main .ch-meta{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.ch-author{display:flex;align-items:center}
.ch-author-name{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.ch-head-actions{display:flex;gap:0;align-items:flex-start;flex:none}
.ch-follow-btn{background:none;border:none;cursor:pointer;color:inherit;font:inherit;font-weight:600;font-size:.8rem;padding:6px 4px;white-space:nowrap}
.ch-icon-btn{background:none;border:none;cursor:pointer;color:var(--text-secondary);display:inline-flex;padding:4px;border-radius:50%}
.ch-menu-wrap{position:relative}
.ch-menu-scrim{position:fixed;inset:0;z-index:39}
.ch-menu{position:absolute;right:0;top:calc(100% + 4px);background:var(--background);border:1px solid var(--border);border-radius:var(--radius-md);padding:4px;z-index:40;min-width:150px;box-shadow:0 8px 24px rgba(0,0,0,.14);display:flex;flex-direction:column}
.ch-menu button{display:block;width:100%;text-align:left;background:none;border:none;padding:8px 10px;cursor:pointer;color:inherit;font:inherit}
.ch-body.clamped{display:-webkit-box;-webkit-line-clamp:4;-webkit-box-orient:vertical;overflow:hidden}
.ch-more{background:none;border:none;cursor:pointer;color:var(--text-secondary);font:inherit;font-size:.88rem;padding:0;margin:0 0 4px}
.ch-media{height:220px;border-radius:var(--radius-sm);background:linear-gradient(135deg,#E8E0C8,#D9CFB2);margin:10px 0 4px}
[data-theme="dark"] .ch-media{background:linear-gradient(135deg,#38331F,#2A2718)}
.ch-card{border:1px solid var(--border);border-radius:var(--radius-md);padding:14px;margin-bottom:16px}
.ch-card .ch-author{font-size:.9rem}
.ch-card .ch-verified{font-size:.64rem;padding:0 4px;margin-left:2px}
.ch-card-head{display:flex;gap:6px;align-items:flex-start}
.ch-author{font-weight:600;margin:0}
.ch-meta{color:var(--text-secondary);font-size:.85rem;margin:2px 0 0}
.ch-title{margin:10px 0 6px;font-size:1.05rem}
.ch-title a{text-decoration:none;color:inherit}
.ch-body{margin:0 0 8px;white-space:pre-wrap}
.ch-pills{margin:4px 0 0;display:flex;gap:6px;flex-wrap:wrap}
.ch-summary-row{display:flex;justify-content:space-between;align-items:center;font-size:.8rem;color:var(--text-secondary);margin-top:10px;min-height:16px}
.ch-summary-left{display:inline-flex;gap:4px;align-items:center}
.ch-actions{display:flex;border-top:1px solid var(--border);margin-top:8px;padding-top:4px}
.ch-action{flex:1;background:none;border:none;border-radius:var(--radius-sm);padding:8px 4px;cursor:pointer;color:inherit;font:inherit;font-size:.82rem;display:inline-flex;align-items:center;justify-content:center;gap:6px;white-space:nowrap}
.ch-action:hover{background:var(--surface-muted)}
.ch-action.is-active{font-weight:700}
.ch-note{margin:8px 0 0;font-size:.9rem}
.ch-report{display:flex;gap:8px;align-items:center;margin-top:12px;flex-wrap:wrap}
.ch-comments{margin-top:12px;border-top:1px solid var(--border);padding-top:12px}
.ch-like-wrap{position:relative;flex:1;display:flex}
.ch-like-wrap .ch-action{flex:1}
.ch-tray{position:absolute;bottom:calc(100% + 4px);left:50%;transform:translateX(-50%);display:flex;gap:2px;background:var(--surface);border:1px solid var(--border);border-radius:999px;padding:4px 6px;box-shadow:0 10px 28px rgb(20 20 15 / .2);z-index:40}
.ch-tray button{background:none;border:none;cursor:pointer;font-size:1.2rem;line-height:1;padding:5px 6px;border-radius:50%;transition:transform .12s ease}
.ch-tray button:hover{transform:scale(1.3)}
.ch-avatar-sm{width:32px;height:32px;font-size:.72rem;flex:none}
.ch-composer{display:flex;gap:8px;align-items:center;margin-bottom:12px}
.ch-composer input{flex:1;border-radius:999px;min-width:0}
.ch-comment{display:flex;gap:10px}
.ch-comment-main{flex:1;min-width:0}
.ch-comment-head{margin:0;display:flex;gap:8px;align-items:center;flex-wrap:wrap}
.ch-comment-body{margin:2px 0 0}
.ch-comment-actions{display:flex;gap:14px;margin-top:4px}
.ch-comment-actions button{background:none;border:none;padding:0;cursor:pointer;color:var(--text-secondary);font:inherit;font-size:.78rem;font-weight:600}
.ch-comment-actions button.is-active{color:var(--text-primary);font-weight:700}
.ch-replies{list-style:none;margin:10px 0 0;padding:0 0 0 14px;border-left:2px solid var(--border);display:flex;flex-direction:column;gap:10px}
.ch-reply-form{display:flex;gap:8px;align-items:center;margin-top:8px}
.ch-reply-form input{flex:1;border-radius:999px;min-width:0}
.ch-comment-list{list-style:none;margin:0 0 12px;padding:0;display:flex;flex-direction:column;gap:10px}
.ch-comment-list p{margin:2px 0 0}
.ch-rail{position:sticky;top:88px;display:flex;flex-direction:column;gap:20px}
.ch-rail-card{border:1px solid var(--border);border-radius:var(--radius-md);padding:16px}
.ch-event-list{list-style:none;margin:12px 0 0;padding:0;display:flex;flex-direction:column;gap:12px}
.ch-event-list a{text-decoration:none}
.ch-all-events{display:block;text-align:center;margin-top:14px}
.ch-suggest-row{display:flex;gap:10px;align-items:center;padding:10px 0;border-top:1px solid var(--border)}
.ch-suggest-row:first-of-type{border-top:none}
.ch-follow{margin-left:auto;border:1px solid var(--border);background:none;border-radius:999px;padding:5px 14px;cursor:pointer;font:inherit;font-size:.85rem;flex:none}
.ch-follow.is-active{background:var(--primary);color:var(--on-primary);border-color:var(--primary)}
@media (max-width:1100px){.ch-layout{grid-template-columns:248px minmax(0,1fr)}.ch-layout.collapsed{grid-template-columns:68px minmax(0,1fr)}.ch-rail{grid-column:2;position:static}.ch-layout.collapsed .ch-rail{grid-column:2}}
@media (max-width:900px){.ch-layout{grid-template-columns:minmax(0,1fr)}.ch-layout.collapsed{grid-template-columns:minmax(0,1fr)}.ch-rail{grid-column:1}.ch-layout.collapsed .ch-rail{grid-column:1}}
`;

function SuggestedRow({ s }: { s: SampleStudent }){
  const [following,setFollowing]=useState(false);
  return <div className="ch-suggest-row"><span className="ch-avatar" aria-hidden="true">{initialsOf(s.name)}</span><div style={{minWidth:0}}><p className="ch-author">{s.name}</p><p className="ch-meta">{s.meta}</p></div><button type="button" className={`ch-follow${following?' is-active':''}`} aria-pressed={following} onClick={()=>setFollowing(f=>!f)}>{following?'Following':'Follow'}</button></div>;
}

/** Community Home (Phase 3, reference layout): the signed-in landing page.
 *  Three columns — profile + navigation sidebar (collapsed to a 68px icon
 *  rail by the toggle inside its own navigation card; on mobile the
 *  header hamburger — shown only at mobile widths — opens it as a
 *  drawer), the
 *  department community feed, and a right rail with upcoming events and,
 *  in Prototype Preview only, suggested students. All data is the real
 *  API data; when the feed request itself fails, the page falls back to
 *  the clearly-bannered Prototype Preview (see sampleFeed.ts). The feed
 *  is the department level by default (FR-COM-07); the reference design
 *  carries no level or sector switcher on this page. */
export default function Feed(){
  const [cursor,setCursor]=useState<string|null>(null); const [cursors,setCursors]=useState<(string|null)[]>([null]);
  const sb = useCommunitySidebar(); const [photoOk,setPhotoOk]=useState(true);
  const [modalOpen,setModalOpen]=useState(false);
  const [sortBy,setSortBy]=useState<'Recent'|'Top'>('Recent');
  const [hiddenIds,setHiddenIds]=useState<Set<string|number>>(new Set());
  const [localPosts,setLocalPosts]=useState<CardPost[]>([]);
  const [postNote,setPostNote]=useState<string|null>(null);
  const me = useQuery({ queryKey:['my-profile'], queryFn: ()=>apiGet<UserProfile>('/profiles/me'), retry:false });
  const q = useQuery({ queryKey:['feed','DEPARTMENT',cursor,sortBy], queryFn: ()=>apiGet<{items:Post[]; nextCursor?:string|null}>(`/feed?level=DEPARTMENT&sort=${sortBy==='Top'?'top':'recent'}${cursor && sortBy!=='Top' ?`&cursor=${encodeURIComponent(cursor)}`:''}`), retry:false });
  const items = q.data?.items ?? [];
  const bookmarks = useQuery({ queryKey:['bookmarks'], queryFn: ()=>apiGet<any>('/bookmarks'), retry:false });
  const bookmarkItems: any[] = Array.isArray(bookmarks.data) ? bookmarks.data : (bookmarks.data?.items ?? []);
  const savedIds = new Set(bookmarkItems.filter(b=>b?.targetType==='POST').map(b=>Number(b.targetId ?? b.id)));
  const eventsQ = useQuery({ queryKey:['events-upcoming'], queryFn: ()=>apiGet<any>('/events/upcoming'), retry:false });
  const upcoming: EventItem[] = Array.isArray(eventsQ.data) ? eventsQ.data : (eventsQ.data?.items ?? []);
  const preview = q.isError;
  const profile = me.data;
  const hidePost = (id: string|number)=>setHiddenIds(s=>new Set(s).add(id));
  const unhidePost = (id: string|number)=>setHiddenIds(s=>{ const n=new Set(s); n.delete(id); return n; });
  const addPreviewPost = (draft: PreviewPostDraft)=>{
    setLocalPosts(ps=>[{ id: `local-${Date.now()}`, title: draft.title, body: draft.body, authorName: profile?.fullName || 'You', timeLabel: 'Just now', scopeLabel: 'Department', sectors: [], reactionCount: 0, commentCount: 0, media: draft.hasMedia }, ...ps]);
    setPostNote('Posted — preview only: in Prototype Preview new posts are kept on this device and never sent to the server.');
  };
  const renderCard = (card: CardPost, opts?: { sample?: (typeof SAMPLE_POSTS)[number]; saved?: boolean; own?: boolean })=>{
    if (hiddenIds.has(card.id)) return <div key={card.id} className="ch-hidden-bar"><span>Post hidden</span><button type="button" onClick={()=>unhidePost(card.id)}>Undo</button></div>;
    return <PostCard key={card.id} post={card} preview={preview} sample={opts?.sample} initiallySaved={opts?.saved} isOwn={opts?.own} onHide={hidePost} />;
  };

  const myAvatar = profile?.fullName
    ? (profile.userId && photoOk ? <img className="ch-photo" style={{width:40,height:40,border:'none'}} src={`/api/profiles/${profile.userId}/photo`} alt="" onError={()=>setPhotoOk(false)} /> : <span className="ch-avatar" aria-hidden="true">{initialsOf(profile.fullName)}</span>)
    : <span className="ch-avatar" aria-hidden="true">?</span>;
  return <div className="container"><style>{CH_CSS}</style>
  <div className={`ch-layout${sb.collapsed?' collapsed':''}`}>
    <CommunitySidebar collapsed={sb.collapsed} drawerOpen={sb.drawerOpen} onToggleCollapse={sb.toggleCollapse} onCloseDrawer={sb.closeDrawer} />
    <section aria-label="Community feed">
      <div className="ch-trigger">
        <div className="ch-trigger-row">
          {myAvatar}
          <button type="button" className="ch-trigger-pill" onClick={()=>setModalOpen(true)}>Start a post</button>
        </div>
      </div>
      <div className="ch-sort">
        <span className="ch-sort-line" aria-hidden="true" />
        <label>Sort by: <select aria-label="Sort by" value={sortBy} onChange={e=>setSortBy(e.target.value as 'Recent'|'Top')}><option value="Recent">Recent</option><option value="Top">Top</option></select></label>
      </div>
      {postNote && <p role="status" className="ch-post-note"><span>{postNote}</span><button type="button" aria-label="Dismiss" onClick={()=>setPostNote(null)}>×</button></p>}
      <div style={{marginTop:16}}>
      {q.isPending ? <Skeleton/>
      : preview ? <><div role="note" style={{border:'1px solid var(--border)',borderRadius:'var(--radius-md)',padding:'10px 14px',fontSize:'.9rem',marginBottom:16}}><strong>Prototype Preview</strong> — sample data. The live feed is unavailable, so these posts are examples, not real student activity.</div>{localPosts.map(c=>renderCard(c))}
          {(sortBy==='Top' ? [...SAMPLE_POSTS].sort((a,b)=>b.reactionCount-a.reactionCount) : SAMPLE_POSTS).map(sp=>renderCard({ id: sp.id, title: sp.title, body: sp.body, authorName: sp.authorName, subtitle: sp.authorMeta, timeLabel: sp.timeLabel, scopeLabel: sp.scopeLabel, contextLine: sp.contextLine, media: sp.media, sectors: sp.sectors, reactionCount: sp.reactionCount, commentCount: sp.comments.length }, { sample: sp }))}</>
      : items.length===0 && localPosts.length===0 ? <EmptyState title="No posts yet" body="Nothing has been posted in your department yet. Be the first to post."/>
      : <>{items.map(p=>renderCard({ id: p.id, title: p.title, body: p.body, authorId: p.authorId, authorName: p.authorName||p.author?.fullName||'Student', subtitle: [(p as any).authorDepartment, (p as any).authorBatch].filter(Boolean).join(' · ') || undefined, timeLabel: formatDate(p.createdAt), scopeLabel: scopeLabel(p.visibility), sectors: p.sectors||[], reactionCount: p.reactionCount ?? 0, commentCount: p.commentCount, viewerReaction: (p as any).viewerReaction ?? null, savedByViewer: (p as any).savedByViewer ?? false }, { saved: savedIds.has(Number(p.id)), own: !!profile?.userId && p.authorId===profile.userId }))}
        <div style={{display:'flex',gap:8,marginTop:16}}><Button disabled={cursors.length<=1} onClick={()=>{const prev=[...cursors]; prev.pop(); setCursors(prev); setCursor(prev[prev.length-1]);}}>Previous</Button><Button disabled={!q.data?.nextCursor} onClick={()=>{const n=q.data!.nextCursor!; setCursors([...cursors,n]); setCursor(n);}}>Next →</Button></div></>}
      </div>
      <ComposerModal open={modalOpen} authorName={profile?.fullName} avatar={myAvatar} preview={preview} onClose={()=>setModalOpen(false)} onPosted={(note)=>setPostNote(note)} onPreviewPost={addPreviewPost} />
    </section>
    <aside className="ch-rail" aria-label="Right sidebar">
      <div className="ch-rail-card">
        <h2 className="title" style={{marginTop:0}}>Upcoming events</h2>
        {eventsQ.isPending ? <Skeleton/> : eventsQ.isError ? <p className="secondary">Events are unavailable right now.</p> : upcoming.length===0 ? <p className="secondary">No upcoming events.</p>
        : <ul className="ch-event-list">{upcoming.slice(0,3).map(e=><li key={e.id}><Link to={`/events/${e.id}`}><strong>{e.title}</strong></Link><p className="ch-meta">{(e.eventDeadline||e.deadline)?`Deadline ${e.eventDeadline||e.deadline}`:''}{e.location?` · ${e.location}`:''}</p></li>)}</ul>}
        <Link className="btn ch-all-events" to="/events">All events →</Link>
      </div>
      {preview && <div className="ch-rail-card">
        <h2 className="title" style={{marginTop:0}}>Suggested students</h2>
        <div>{SAMPLE_SUGGESTED.map(s=><SuggestedRow key={s.name} s={s} />)}</div>
        <Link className="btn ch-all-events" to="/network">Open My Network →</Link>
      </div>}
    </aside>
  </div>
  </div>;
}
