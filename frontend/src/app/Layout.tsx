import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import SettingsModal, { type SettingsTab } from '../features/settings/SettingsModal';
import { useAuth } from './auth';
import { apiGet, apiPost, errorMessage } from '../api/client';
import { IconBell, IconGear, IconGrid, IconMenu, IconSearch } from '../design/icons';

const HEADER_CSS = `
.hdr-inner{max-width:1280px;gap:12px}
.hdr-brand{display:flex;align-items:center;gap:8px;text-decoration:none;color:inherit;flex:none}
.hdr-logo{width:30px;height:30px;border-radius:7px;background:var(--primary);color:var(--on-primary);display:inline-flex;align-items:center;justify-content:center;font-weight:700;font-size:15px}
.hdr-brand-name{font-weight:600;font-size:17px}
.hdr-icon-btn{position:relative;display:inline-flex;align-items:center;justify-content:center;width:38px;height:38px;border-radius:50%;border:1px solid var(--border);background:var(--background);color:inherit;cursor:pointer;text-decoration:none;flex:none;padding:0}
.hdr-badge{position:absolute;top:-4px;right:-4px;min-width:17px;height:17px;border-radius:999px;background:var(--primary);color:var(--on-primary);font-size:10px;font-weight:700;display:inline-flex;align-items:center;justify-content:center;padding:0 4px}
.hdr-search{position:relative;display:flex;align-items:center;flex:1 1 auto;max-width:430px;margin:0 auto}
.hdr-search-icon{position:absolute;left:12px;color:var(--text-secondary);display:inline-flex;pointer-events:none}
.hdr-search input{width:100%;border-radius:999px;padding-left:36px;padding-right:36px}
.hdr-kbd{position:absolute;right:12px;border:1px solid var(--border);border-radius:5px;padding:0 6px;font-size:11px;color:var(--text-secondary);font-family:inherit}
.hdr-actions{display:flex;gap:8px;align-items:center;margin-left:auto;flex:none}
.hdr-avatar{width:38px;height:38px;border-radius:50%;border:none;background:var(--primary);color:var(--on-primary);font-weight:600;cursor:pointer;flex:none;padding:0}
.hdr-menu-wrap{position:relative}
.hdr-menu-scrim{position:fixed;inset:0;z-index:29}
.hdr-menu{position:absolute;right:0;top:calc(100% + 8px);background:var(--background);border:1px solid var(--border);border-radius:var(--radius-md);padding:4px;z-index:30;min-width:180px;box-shadow:0 8px 24px rgba(0,0,0,.14);display:flex;flex-direction:column}
.hdr-menu a,.hdr-menu button{display:block;width:100%;text-align:left;background:none;border:none;padding:8px 10px;cursor:pointer;color:inherit;font:inherit;text-decoration:none;box-sizing:border-box}
.hdr-pop{position:absolute;right:0;top:calc(100% + 8px);width:360px;max-width:92vw;background:var(--background);border:1px solid var(--border);border-radius:var(--radius-md);box-shadow:0 12px 32px rgba(0,0,0,.16);z-index:30;padding:12px}
.hdr-pop-head{display:flex;justify-content:space-between;align-items:baseline;gap:10px;margin-bottom:8px}
.hdr-pop-list{max-height:46vh;overflow:auto;display:flex;flex-direction:column}
.hdr-notif{display:flex;gap:10px;align-items:flex-start;text-align:left;background:none;border:none;border-top:1px solid var(--border);padding:10px 4px;cursor:pointer;color:inherit;font:inherit}
.hdr-notif:first-child{border-top:none}
.hdr-notif-main{flex:1;min-width:0;display:flex;flex-direction:column;gap:2px}
.hdr-notif-body{color:var(--text-secondary);font-size:.88rem}
.hdr-notif-date{color:var(--text-secondary);font-size:.8rem}
.hdr-notif-dot{width:8px;height:8px;border-radius:50%;background:var(--primary);flex:none;margin-top:6px}
.hdr-pop-foot{display:flex;gap:8px;justify-content:space-between;border-top:1px solid var(--border);padding-top:10px;margin-top:4px}
.hdr-toggle{display:none}
@media (max-width:900px){.hdr-toggle{display:inline-flex}}
@media (max-width:720px){.hdr-brand-name{display:none}.hdr-kbd{display:none}}
`;

function initialsOf(name?: string) {
  const p = (name || '').trim().split(/\s+/).filter(Boolean);
  return p.length ? (p[0][0] + (p.length > 1 ? p[p.length - 1][0] : '')).toUpperCase() : 'U';
}

export function Layout(){
  const { user, setUser } = useAuth(); const nav = useNavigate(); const loc = useLocation();
  // D-094/D-098: a session can carry mustChangePassword (the flag is
  // reported by /me). Since 2026-11-21 Settings is a modal, not a page:
  // open it on the Behavior tab, which holds the change-password form.
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [settingsTab, setSettingsTab] = useState<SettingsTab>('Edit Profile');
  const openSettings = (tab: SettingsTab = 'Edit Profile') => { setSettingsTab(tab); setSettingsOpen(true); };
  useEffect(()=>{ if (user?.mustChangePassword) openSettings('Behavior'); }, [user?.mustChangePassword]);
  // Other surfaces (the profile page's Edit profile button) ask the
  // shell to open Settings through this event.
  useEffect(()=>{
    const SETTINGS_TABS: SettingsTab[] = ['Edit Profile', 'Appearance', 'Behavior', 'Notifications', 'Privacy'];
    const onOpen = (e: Event) => { const t = (e as CustomEvent).detail?.tab as SettingsTab | undefined; openSettings(t && SETTINGS_TABS.includes(t) ? t : 'Edit Profile'); };
    window.addEventListener('unilink:open-settings', onOpen);
    return ()=>window.removeEventListener('unilink:open-settings', onOpen);
  }, []);
  // Owner decision (2026-11-20): the signed-out auth pages show a minimal
  // header — brand plus Log in / Sign up only. The app chrome belongs to
  // the signed-in experience and is removed from these routes entirely
  // (not hidden placeholders). '/verification-preview' (Phase 2.3's
  // prototype journey) belongs to the same signed-out set.
  // Owner decision (2026-11-24, round 4): the public landing page (/)
  // ALWAYS gets the dedicated static public header — brand plus Log in /
  // Sign up only. The header must never depend on auth state here: while
  // the session restore is pending `user` is null (minimal header), and
  // once /auth/me answers the restored user flipped it to the full app
  // header — the 2-second revert. A signed-in user on / now keeps the
  // public header too; the full header belongs to the app routes only.
  const AUTH_ROUTES = ['/login', '/signup', '/forgot-password', '/otp', '/pending', '/verification-preview'];
  const minimalHeader = AUTH_ROUTES.includes(loc.pathname) || loc.pathname === '/';
  // Owner polish (2026-11-20): during verification the header carries the
  // brand only — no Log in / Sign up buttons.
  const bareHeader = loc.pathname === '/verification-preview';
  const [logoutError, setLogoutError] = useState<string|null>(null);
  const [searchText, setSearchText] = useState('');
  const [menuOpen, setMenuOpen] = useState(false);
  const [notifOpen, setNotifOpen] = useState(false);
  const [showAllNotifs, setShowAllNotifs] = useState(false);
  const qc = useQueryClient();
  const searchRef = useRef<HTMLInputElement>(null);
  // A failed sign-out is surfaced, not swallowed: the local session is
  // still cleared (the user asked to leave) but the failure is shown so
  // a dead logout endpoint can never hide behind a silent redirect.
  const logout = async ()=>{ try{ await apiPost('/auth/logout'); }catch(e){ setLogoutError(errorMessage(e)); } setUser(null); setMenuOpen(false); nav('/login'); };
  // The header search's "/" shortcut: pressing / anywhere outside a form
  // field focuses the global search (reference design).
  useEffect(()=>{
    if (minimalHeader) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== '/') return;
      const t = e.target as HTMLElement | null;
      if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.tagName === 'SELECT' || t.isContentEditable)) return;
      e.preventDefault(); searchRef.current?.focus();
    };
    window.addEventListener('keydown', onKey);
    return ()=>window.removeEventListener('keydown', onKey);
  }, [minimalHeader]);
  // Unread badge on the bell: counted from the real notifications list;
  // any failure simply shows no badge.
  const notifQ = useQuery({ queryKey:['notifications'], queryFn: ()=>apiGet<any>('/notifications'), enabled: !!user && !minimalHeader, retry:false });
  const notifItems: any[] = Array.isArray(notifQ.data) ? notifQ.data : (notifQ.data?.items ?? []);
  const isUnread = (n: any) => !!n && (n.read===false || n.readFlag===false);
  const unread = notifItems.filter(isUnread).length;
  const visibleNotifs = (showAllNotifs ? notifItems : notifItems.filter(isUnread)).slice(0, 10);
  const markRead = useMutation({ mutationFn: (id: string|number)=>apiPost(`/notifications/${id}/read`, {}), onSuccess: ()=>qc.invalidateQueries({ queryKey:['notifications'] }) });
  const markAllRead = useMutation({ mutationFn: ()=>apiPost('/notifications/read-all', {}), onSuccess: ()=>qc.invalidateQueries({ queryKey:['notifications'] }) });
  const noticeDate = (iso?: string) => { if (!iso) return ''; const d = new Date(iso); return Number.isNaN(d.getTime()) ? '' : d.toLocaleDateString('en-GB', { day:'numeric', month:'short', year:'numeric' }); };
  // The notifications popover closes on Escape, like the menus and modals.
  useEffect(()=>{
    if (!notifOpen) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setNotifOpen(false); };
    window.addEventListener('keydown', onKey);
    return ()=>window.removeEventListener('keydown', onKey);
  }, [notifOpen]);
  const toggleSidebar = ()=>window.dispatchEvent(new CustomEvent('unilink:toggle-sidebar'));
  const brand = <Link to="/" className="hdr-brand"><span className="hdr-logo" aria-hidden="true">U</span><span className="hdr-brand-name">UniLink</span></Link>;
  return <div style={{display:'flex',flexDirection:'column',minHeight:'100vh'}}><style>{HEADER_CSS}</style><header className="header"><div className="header-inner hdr-inner">
    {minimalHeader ? <>
      {brand}
      <div style={{marginLeft:'auto',display:'flex',gap:8,alignItems:'center'}}>
        {bareHeader ? null : <><Link className="btn btn-sm" to="/login">Log in</Link><Link className="btn btn-sm btn-primary" to="/signup">Sign up</Link></>}
      </div>
    </> : <>
      <button type="button" className="hdr-icon-btn hdr-toggle" aria-label="Toggle sidebar" onClick={toggleSidebar}><IconMenu /></button>
      {brand}
      <form role="search" className="hdr-search" onSubmit={e=>{e.preventDefault(); nav(`/search?q=${encodeURIComponent(searchText.trim())}`);}}>
        <span className="hdr-search-icon"><IconSearch /></span>
        <input ref={searchRef} type="search" aria-label="Search" placeholder="Search people, posts, projects" value={searchText} onChange={e=>setSearchText(e.target.value)} className="input-search" />
        <kbd className="hdr-kbd" aria-hidden="true">/</kbd>
      </form>
      <div className="hdr-actions">
        <NavLink to="/workspace" aria-label="Workspace" className="hdr-icon-btn"><IconGrid /></NavLink>
        <div className="hdr-menu-wrap">
          <button type="button" className="hdr-icon-btn" aria-label="Notifications" aria-haspopup="dialog" aria-expanded={notifOpen} onClick={()=>setNotifOpen(o=>!o)}><IconBell />{unread>0 && <span className="hdr-badge">{unread}</span>}</button>
          {notifOpen && <>
            <div className="hdr-menu-scrim" onClick={()=>setNotifOpen(false)} aria-hidden="true" />
            <div className="hdr-pop" role="dialog" aria-label="Notifications">
              <div className="hdr-pop-head"><strong>Notifications</strong><span className="secondary">{showAllNotifs ? 'All notifications' : 'Unread notices'}{!showAllNotifs && unread>0 ? ` (${unread})` : ''}</span></div>
              <div className="hdr-pop-list">
                {visibleNotifs.length===0 ? <p className="secondary" style={{padding:'8px 4px'}}>{showAllNotifs ? 'No notifications yet.' : 'No unread notices.'}</p> : visibleNotifs.map(n=>(
                  <button key={n.id} type="button" className="hdr-notif" onClick={()=>{ if (isUnread(n)) markRead.mutate(n.id); }}>
                    <span className="hdr-notif-main"><strong>{n.title||n.message||'Notification'}</strong>{n.body && <span className="hdr-notif-body">{n.body}</span>}<span className="hdr-notif-date">{noticeDate(n.createdAt)}</span></span>
                    {isUnread(n) && <span className="hdr-notif-dot" aria-label="Unread" />}
                  </button>
                ))}
              </div>
              <div className="hdr-pop-foot">
                <button type="button" className="btn btn-sm" onClick={()=>markAllRead.mutate()} disabled={markAllRead.isPending || unread===0}>Mark all as read</button>
                <button type="button" className="btn btn-sm" onClick={()=>setShowAllNotifs(v=>!v)}>{showAllNotifs ? 'Unread only' : 'All notifications'}</button>
              </div>
            </div>
          </>}
        </div>
        <button type="button" aria-label="Settings" className="hdr-icon-btn" onClick={()=>openSettings('Edit Profile')}><IconGear /></button>
        {user ? <div className="hdr-menu-wrap">
          <button type="button" className="hdr-avatar" aria-label="Account menu" aria-haspopup="menu" aria-expanded={menuOpen} onClick={()=>setMenuOpen(o=>!o)}>{initialsOf((user as any).fullName)}</button>
          {menuOpen && <>
            <div className="hdr-menu-scrim" onClick={()=>setMenuOpen(false)} aria-hidden="true" />
            <div className="hdr-menu" role="menu">
              <Link role="menuitem" to="/profile/me" onClick={()=>setMenuOpen(false)}>Profile</Link>
              <button type="button" role="menuitem" onClick={()=>{ setMenuOpen(false); openSettings('Edit Profile'); }}>Settings</button>
              <button type="button" role="menuitem" onClick={logout}>Sign out</button>
            </div>
          </>}
        </div> : <><Link className="btn btn-sm" to="/login">Log in</Link><Link className="btn btn-sm btn-primary" to="/signup">Sign up</Link></>}
      </div>
    </>}
    </div></header>
    {!minimalHeader && !!user && <SettingsModal open={settingsOpen} initialTab={settingsTab} onClose={()=>setSettingsOpen(false)} />}
    <main style={{flex:1}}><Outlet/></main>
    {logoutError && <p role="alert" className="field-error container">Sign-out did not complete on the server: {logoutError} Your server session may still be active — sign in again and sign out, or wait for it to expire.</p>}
    <footer className="container" style={{padding:'32px 16px'}}><p className="muted-caption">UniLink — Verified Student Network for Bangladesh Universities</p></footer>
  </div>;
}
