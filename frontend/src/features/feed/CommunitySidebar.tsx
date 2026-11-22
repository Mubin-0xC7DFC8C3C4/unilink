import { useEffect, useState } from 'react'; import { Link, NavLink } from 'react-router-dom'; import { useQuery } from '@tanstack/react-query';
import { apiGet } from '../../api/client'; import type { UserProfile } from '../../api/types';
import { VerifiedBadge } from './PostCard';
import { IconBookmark, IconCalendar, IconHome, IconUsers } from '../../design/icons';

function initialsOf(name: string){ const p=name.trim().split(/\s+/).filter(Boolean); return p.length? (p[0][0]+(p.length>1?p[p.length-1][0]:'')).toUpperCase() : '?'; }

export const SIDEBAR_CSS = `
.ch-side{position:sticky;top:88px;display:flex;flex-direction:column;gap:16px}
.ch-profile{border:1px solid var(--border);border-radius:var(--radius-md);overflow:hidden}
.ch-profile-banner{height:64px;background:#E8E0C8}
[data-theme="dark"] .ch-profile-banner{background:#38331F}
.ch-profile-body{padding:0 16px 16px;margin-top:-28px;text-align:center}
.ch-profile-body .ch-photo{margin:0 auto}
.ch-profile-name{font-weight:600;margin:8px 0 0;font-size:1.02rem}
.ch-avatar{width:40px;height:40px;border-radius:50%;background:var(--primary);color:var(--on-primary);display:inline-flex;align-items:center;justify-content:center;font-weight:600;flex:none}
.ch-avatar.xl{width:56px;height:56px;font-size:1.05rem;border:3px solid var(--background);box-sizing:content-box}
.ch-photo{width:56px;height:56px;border-radius:50%;object-fit:cover;display:block;border:3px solid var(--background)}
.ch-verified{display:inline-flex;gap:3px;align-items:center;border:1px solid var(--border);border-radius:999px;padding:1px 8px;font-size:.72rem;font-weight:600;color:var(--text-secondary);margin-left:6px;vertical-align:middle;white-space:nowrap}
.ch-counts{display:flex;gap:20px;margin-top:12px;justify-content:center}
.ch-counts span{font-size:.85rem;color:var(--text-secondary)}
.ch-counts strong{color:var(--text-primary);font-size:.95rem}
.ch-view-profile{display:block;text-align:center;margin-top:14px}
.ch-navcard{border:1px solid var(--border);border-radius:var(--radius-md);padding:8px}
.ch-nav{display:flex;flex-direction:column;gap:2px}
.ch-nav a{display:flex;gap:10px;align-items:center;padding:9px 10px;border-radius:var(--radius-sm);text-decoration:none;color:inherit;border:1px solid transparent}
.ch-nav a[aria-current="page"]{background:var(--surface-muted);border-color:var(--border);font-weight:600}
.ch-nav-toggle{display:flex;gap:10px;align-items:center;width:100%;margin-top:6px;padding:9px 10px;border:none;border-top:1px solid var(--border);background:none;cursor:pointer;color:var(--text-secondary);font:inherit;text-align:left}
.ch-drawer-close{display:none}
.ch-meta{color:var(--text-secondary);font-size:.85rem;margin:2px 0 0}
.ch-scrim{display:none}
@media (min-width:901px){
.ch-side.is-collapsed .ch-profile{display:none}
.ch-side.is-collapsed .ch-navcard{padding:8px 4px}
.ch-side.is-collapsed .ch-nav a{justify-content:center;padding:9px 0}
.ch-side.is-collapsed .ch-nav-label{display:none}
.ch-side.is-collapsed .ch-nav-toggle{justify-content:center}
}
@media (max-width:900px){.ch-nav-toggle{display:none}.ch-side{position:fixed;top:0;left:0;bottom:0;width:300px;max-width:86vw;background:var(--background);z-index:50;transform:translateX(-105%);transition:transform .2s ease;padding:16px;overflow:auto;border-right:1px solid var(--border)}.ch-side.open{transform:none}.ch-drawer-close{display:inline-flex}.ch-scrim{display:block;position:fixed;inset:0;background:rgba(0,0,0,.35);z-index:40}}
`;

/** Sidebar state shared by the pages that use the Community shell:
 *  the header hamburger (Layout) broadcasts `unilink:toggle-sidebar` —
 *  on mobile it opens the drawer, on desktop it collapses the rail. */
export function useCommunitySidebar(){
  const [collapsed,setCollapsed]=useState(false); const [drawerOpen,setDrawerOpen]=useState(false);
  useEffect(()=>{
    const onToggle=()=>{ if (window.innerWidth<=900) setDrawerOpen(o=>!o); else setCollapsed(c=>!c); };
    window.addEventListener('unilink:toggle-sidebar', onToggle);
    return ()=>window.removeEventListener('unilink:toggle-sidebar', onToggle);
  },[]);
  return { collapsed, drawerOpen, toggleCollapse: ()=>setCollapsed(c=>!c), closeDrawer: ()=>setDrawerOpen(false) };
}

/** The Community shell's left column: the signed-in student's profile
 *  card (real GET /profiles/me) and the section navigation — Home,
 *  My Network, Events, Saved. Collapsed on desktop it becomes the
 *  68px icon rail; on mobile it is the drawer. Used by Community Home
 *  and My Network so navigation stays identical across the module. */
export default function CommunitySidebar({ collapsed, drawerOpen, onToggleCollapse, onCloseDrawer }: { collapsed: boolean; drawerOpen: boolean; onToggleCollapse: () => void; onCloseDrawer: () => void }){
  const [photoOk,setPhotoOk]=useState(true);
  const me = useQuery({ queryKey:['my-profile'], queryFn: ()=>apiGet<UserProfile>('/profiles/me'), retry:false });
  const profile = me.data;
  return <>
    <style>{SIDEBAR_CSS}</style>
    {drawerOpen && <div className="ch-scrim" onClick={onCloseDrawer} aria-hidden="true" />}
    <aside className={`ch-side${collapsed?' is-collapsed':''}${drawerOpen?' open':''}`} aria-label="Community sidebar">
      <div className="ch-profile">
        <div className="ch-profile-banner" aria-hidden="true" />
        <div className="ch-profile-body">
        {me.isPending ? <p className="secondary">Loading profile…</p>
        : profile?.fullName ? <>
          {profile.userId && photoOk ? <img className="ch-photo" src={`/api/profiles/${profile.userId}/photo`} alt="" onError={()=>setPhotoOk(false)} /> : <span className="ch-avatar xl" aria-hidden="true">{initialsOf(profile.fullName)}</span>}
          <p className="ch-profile-name">{profile.fullName}{profile.state==='VERIFIED' && <VerifiedBadge />}</p>
          {profile.departmentName && <p className="ch-meta">{profile.departmentName}</p>}
          {(profile.universityName || profile.batchLabel) && <p className="ch-meta">{[profile.universityName, profile.batchLabel].filter(Boolean).join(' · ')}</p>}
          {typeof profile.followers==='number' && typeof profile.following==='number' && <div className="ch-counts"><span><strong>{profile.followers}</strong> Followers</span><span><strong>{profile.following}</strong> Following</span></div>}
          <Link className="btn ch-view-profile" to="/profile/me">View profile</Link>
        </> : <>
          <span className="ch-avatar xl" aria-hidden="true">?</span>
          <p className="ch-profile-name">Your profile</p>
          <p className="ch-meta">Profile details are unavailable right now.</p>
          <Link className="btn ch-view-profile" to="/profile/me">View profile</Link>
        </>}
        </div>
      </div>
      <div className="ch-navcard">
        <nav className="ch-nav" aria-label="Community sections">
          <NavLink to="/feed"><IconHome /><span className="ch-nav-label">Home</span></NavLink>
          <NavLink to="/network"><IconUsers /><span className="ch-nav-label">My Network</span></NavLink>
          <NavLink to="/events"><IconCalendar /><span className="ch-nav-label">Events</span></NavLink>
          <NavLink to="/saved"><IconBookmark /><span className="ch-nav-label">Saved</span></NavLink>
        </nav>
        <button type="button" className="ch-nav-toggle" aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'} aria-expanded={!collapsed} onClick={onToggleCollapse}>
          <span aria-hidden="true">{collapsed ? '»' : '«'}</span><span className="ch-nav-label">{collapsed ? 'Expand' : 'Collapse'}</span>
        </button>
      </div>
      <button type="button" className="btn btn-sm ch-drawer-close" onClick={onCloseDrawer}>Close</button>
    </aside>
  </>;
}
