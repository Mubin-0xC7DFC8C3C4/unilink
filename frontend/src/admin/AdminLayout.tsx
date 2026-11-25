import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../app/auth';
import { apiGet, apiPost, errorMessage } from '../api/client';
import { Button, Modal } from '../design/primitives';
import { IconBell, IconGear, IconSearch } from '../design/icons';
import { ADM_SHARED_CSS, fmtDateTime } from './adminUi';

const ADM_CSS = `
.adm-shell{display:flex;min-height:100vh;background:var(--bg)}
.adm-side{width:232px;flex:none;border-right:1px solid var(--border);background:var(--surface);display:flex;flex-direction:column;padding:16px 12px;position:sticky;top:0;height:100vh;z-index:50}
.adm-side.is-collapsed{width:68px;padding:16px 8px}
.adm-brand{display:flex;gap:10px;align-items:center;padding:2px 6px 14px;border-bottom:1px solid var(--border)}
.adm-logo{width:34px;height:34px;border-radius:8px;background:var(--primary);color:var(--on-primary);display:inline-flex;align-items:center;justify-content:center;font-weight:800;flex:none}
.adm-brand-name{font-weight:700;line-height:1.1}
.adm-portal-badge{display:inline-block;margin-top:3px;border:1px solid var(--border);border-radius:999px;padding:0 7px;font-size:.62rem;font-weight:700;letter-spacing:.06em}
.adm-nav{flex:1;overflow-y:auto;margin-top:10px}
.adm-group{margin-top:14px}
.adm-group-label{font-size:.68rem;font-weight:700;letter-spacing:.07em;color:var(--text-secondary);padding:0 10px;margin-bottom:4px}
.adm-link{display:flex;gap:10px;align-items:center;padding:8px 10px;border-radius:var(--radius-sm);text-decoration:none;color:inherit;font-size:.92rem}
.adm-link:hover{background:var(--surface-muted)}
.adm-link[aria-current="page"]{background:var(--surface-muted);font-weight:700}
.adm-link svg{flex:none}
.adm-link .adm-count{margin-left:auto;background:var(--primary);color:var(--on-primary);border-radius:999px;font-size:.7rem;font-weight:700;padding:0 7px}
.adm-side.is-collapsed .adm-link{justify-content:center;padding:9px 0}
.adm-side.is-collapsed .adm-link .adm-link-label,.adm-side.is-collapsed .adm-group-label,.adm-side.is-collapsed .adm-brand-text,.adm-side.is-collapsed .adm-link .adm-count{display:none}
.adm-collapse{margin-top:12px;border-top:1px solid var(--border);padding-top:12px}
.adm-body{flex:1;min-width:0;display:flex;flex-direction:column}
.adm-header{height:60px;border-bottom:1px solid var(--border);background:var(--surface);display:flex;gap:14px;align-items:center;padding:0 20px;position:sticky;top:0;z-index:40}
.adm-header .adm-search{flex:1;display:flex;justify-content:center}
.adm-header .adm-search form{position:relative;width:min(430px,100%)}
.adm-header .adm-search input{width:100%;border-radius:999px;padding-left:34px}
.adm-header .adm-search .adm-search-icon{position:absolute;left:11px;top:50%;transform:translateY(-50%);color:var(--text-secondary);display:inline-flex}
.adm-icon-btn{position:relative;background:none;border:1px solid var(--border);border-radius:50%;width:38px;height:38px;display:inline-flex;align-items:center;justify-content:center;cursor:pointer;color:inherit;flex:none}
.adm-icon-btn .adm-dot{position:absolute;top:-4px;right:-6px;background:var(--primary);color:var(--on-primary);border-radius:999px;font-size:.66rem;font-weight:700;padding:0 6px}
.adm-avatar-btn{width:38px;height:38px;border-radius:50%;background:var(--primary);color:var(--on-primary);border:none;font-weight:700;cursor:pointer;flex:none}
.adm-pop-wrap{position:relative}
.adm-pop{position:absolute;right:0;top:calc(100% + 10px);width:min(360px,90vw);background:var(--surface);border:1px solid var(--border);border-radius:var(--radius-md);box-shadow:0 16px 40px rgb(20 20 15 / .18);z-index:60;padding:8px}
.adm-pop h3{margin:6px 8px 8px;font-size:.95rem}
.adm-pop-item{padding:8px;border-radius:var(--radius-sm)}
.adm-pop-item:hover{background:var(--surface-muted)}
.adm-pop-item p{margin:0}
.adm-pop-item .adm-pop-time{color:var(--text-secondary);font-size:.78rem}
.adm-pop-menu button{display:block;width:100%;text-align:left;background:none;border:none;padding:9px 10px;cursor:pointer;color:inherit;font:inherit;border-radius:var(--radius-sm)}
.adm-pop-menu button:hover{background:var(--surface-muted)}
.adm-main{flex:1;padding:24px;min-width:0;max-width:1240px;width:100%;margin:0 auto}
.adm-burger{display:none}
.adm-scrim{display:none}
@media (max-width:900px){
  .adm-side{position:fixed;left:0;top:0;transform:translateX(-105%);transition:transform .2s ease;width:250px}
  .adm-side.is-open{transform:none;box-shadow:0 0 60px rgb(20 20 15 / .3)}
  .adm-side.is-collapsed{width:250px;padding:16px 12px}
  .adm-side.is-collapsed .adm-link{justify-content:flex-start;padding:8px 10px}
  .adm-side.is-collapsed .adm-link .adm-link-label,.adm-side.is-collapsed .adm-group-label,.adm-side.is-collapsed .adm-brand-text{display:block}
  .adm-burger{display:inline-flex}
  .adm-scrim{display:block;position:fixed;inset:0;background:rgb(20 20 15 / .4);z-index:45}
  .adm-main{padding:16px}
  .adm-collapse{display:none}
}
${ADM_SHARED_CSS}
`;

function NavIcon({ d }: { d: string }) {
  return <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={d} /></svg>;
}
const I = {
  dashboard: 'M4 4h7v7H4zM13 4h7v4h-7zM13 10h7v10h-7zM4 13h7v7H4z',
  students: 'M16 19v-1a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v1M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM22 19v-1a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75',
  verify: 'M12 2l8 3v6c0 5-3.5 9.3-8 11-4.5-1.7-8-6-8-11V5zM9 12l2 2 4-4',
  identity: 'M4 7V5a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-2M9 7h6M9 11h6M9 15h4',
  appeals: 'M4 21V4M4 5h13l-2.5 4L17 13H4',
  posts: 'M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z',
  reports: 'M12 9v4M12 17h.01M10.3 3.9L1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z',
  events: 'M8 2v4M16 2v4M3 9h18M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z',
  plus: 'M12 5v14M5 12h14',
  announce: 'M3 11l18-7-4 16-6-4-3 4v-5zM11 16l-8-5',
  university: 'M3 21h18M5 21v-8M9.5 21v-8M14.5 21v-8M19 21v-8M2 10l10-6 10 6z',
  department: 'M4 21V8l6-5v18M10 21V11l10 3v7M2 21h20',
  roles: 'M12 2l8 3v6c0 5-3.5 9.3-8 11-4.5-1.7-8-6-8-11V5zM12 8v5M9.5 10.5h5',
  logs: 'M8 6h13M8 12h13M8 18h13M3.5 6h.01M3.5 12h.01M3.5 18h.01',
  chart: 'M4 20V10M10 20V4M16 20v-7M22 20H2',
};

interface NavItem { label: string; to: string; icon: string; end?: boolean; badge?: number; platformOnly?: boolean }
interface NavGroup { label: string; items: NavItem[] }

export function AdminLayout() {
  const nav = useNavigate();
  const loc = useLocation();
  const { setUser, user } = useAuth();
  const [collapsed, setCollapsed] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [bellOpen, setBellOpen] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [signOutOpen, setSignOutOpen] = useState(false);
  const [logoutError, setLogoutError] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [readLocally, setReadLocally] = useState(false);

  const isPlatformAdmin = user?.role === 'PLATFORM_ADMIN';
  const dashQ = useQuery({ queryKey: ['admin-dashboard'], queryFn: () => apiGet<any>('/admin/dashboard'), retry: false });
  const alertsQ = useQuery({ queryKey: ['admin-alerts'], queryFn: () => apiGet<any[]>('/admin/alerts'), retry: false });
  const alerts = Array.isArray(alertsQ.data) ? alertsQ.data : [];
  const unread = readLocally ? 0 : alerts.filter((a) => !a.readFlag).length;
  const pendingCount = typeof dashQ.data?.pendingVerifications === 'number' ? dashQ.data.pendingVerifications : undefined;

  // A restored session can carry mustChangePassword (reported by
  // /api/admin/auth/me). Redirect to the change-password screen from
  // anywhere else; the screen itself is exempt (no loop).
  useEffect(() => { if (user?.mustChangePassword && loc.pathname !== '/admin/change-password') nav('/admin/change-password', { replace: true }); }, [user?.mustChangePassword, loc.pathname, nav]);
  useEffect(() => { setDrawerOpen(false); setBellOpen(false); setMenuOpen(false); }, [loc.pathname]);

  const logout = async () => { try { await apiPost('/admin/auth/logout'); } catch (e) { setLogoutError(errorMessage(e)); } setUser(null); setSignOutOpen(false); nav('/admin/login'); };

  const groups: NavGroup[] = [
    { label: 'Overview', items: [
      { label: 'Dashboard', to: '/admin', icon: I.dashboard, end: true },
      { label: 'Analytics', to: '/admin/analytics', icon: I.chart },
    ] },
    { label: 'Student Management', items: [
      { label: 'All Students', to: '/admin/users', icon: I.students },
      { label: 'Pending Verification', to: '/admin/verifications', icon: I.verify, badge: pendingCount },
      { label: 'Identity Changes', to: '/admin/identity-changes', icon: I.identity },
      { label: 'Appeals', to: '/admin/appeals', icon: I.appeals },
    ] },
    { label: 'Community Management', items: [
      { label: 'All Posts', to: '/admin/posts', icon: I.posts },
      { label: 'Post Management', to: '/admin/moderation?view=posts', icon: I.posts },
      { label: 'Reports & Moderation', to: '/admin/moderation', icon: I.reports },
      { label: 'Announcements', to: '/admin/announcements', icon: I.announce },
    ] },
    { label: 'Event Management', items: [
      { label: 'All Events', to: '/admin/events', icon: I.events, end: true },
      { label: 'Create Event', to: '/admin/events/new', icon: I.plus },
    ] },
    { label: 'Platform Management', items: [
      { label: 'University Management', to: '/admin/master-data?tab=universities', icon: I.university },
      { label: 'Department Management', to: '/admin/master-data?tab=departments', icon: I.department },
    ] },
    { label: 'Administration', items: [
      { label: 'Admin Users & Roles', to: '/admin/moderators', icon: I.roles, platformOnly: true },
      { label: 'Login Activity', to: '/admin/login-activity', icon: I.logs },
      { label: 'Activity Logs', to: '/admin/audit', icon: I.logs },
      { label: 'Admin Settings', to: '/admin/settings', icon: '' },
    ] },
  ];

  const initials = (user?.email || 'A').slice(0, 2).toUpperCase();

  return (
    <div className="adm-shell">
      <style>{ADM_CSS}</style>
      {drawerOpen && <div className="adm-scrim" onClick={() => setDrawerOpen(false)} aria-hidden="true" />}
      <aside className={`adm-side${collapsed ? ' is-collapsed' : ''}${drawerOpen ? ' is-open' : ''}`} aria-label="Admin navigation">
        <div className="adm-brand">
          <span className="adm-logo" aria-hidden="true">U</span>
          <span className="adm-brand-text"><span className="adm-brand-name">UniLink</span><br /><span className="adm-portal-badge">ADMIN PORTAL</span></span>
        </div>
        <nav className="adm-nav">
          {groups.map((g) => {
            const items = g.items.filter((i) => !i.platformOnly || isPlatformAdmin);
            if (items.length === 0) return null;
            return (
              <div key={g.label} className="adm-group">
                <p className="adm-group-label">{g.label.toUpperCase()}</p>
                {items.map((i) => (
                  <NavLink key={i.label} to={i.to} end={i.end} className="adm-link" title={i.label}>
                    {i.label === 'Admin Settings' ? <IconGear size={18} /> : <NavIcon d={i.icon} />}
                    <span className="adm-link-label">{i.label}</span>
                    {i.badge != null && i.badge > 0 && <span className="adm-count">{i.badge}</span>}
                  </NavLink>
                ))}
              </div>
            );
          })}
        </nav>
        <div className="adm-collapse">
          <button type="button" className="btn btn-sm" onClick={() => setCollapsed((c) => !c)} aria-expanded={!collapsed}>{collapsed ? '» Expand' : '« Collapse'}</button>
        </div>
      </aside>
      <div className="adm-body">
        <header className="adm-header">
          <button type="button" className="adm-icon-btn adm-burger" aria-label="Open navigation" onClick={() => setDrawerOpen(true)}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h16" /></svg>
          </button>
          <div className="adm-search">
            <form onSubmit={(e) => { e.preventDefault(); nav(`/admin/users?q=${encodeURIComponent(search.trim())}`); }} role="search">
              <span className="adm-search-icon"><IconSearch size={16} /></span>
              <input aria-label="Search students" className="input-search" placeholder="Search students by name, email or ID" value={search} onChange={(e) => setSearch(e.target.value)} />
            </form>
          </div>
          <div className="adm-pop-wrap">
            <button type="button" className="adm-icon-btn" aria-label="Notifications" aria-expanded={bellOpen} onClick={() => { setBellOpen((o) => !o); setMenuOpen(false); }}>
              <IconBell size={19} />{unread > 0 && <span className="adm-dot">{unread}</span>}
            </button>
            {bellOpen && <>
              <div style={{ position: 'fixed', inset: 0, zIndex: 55 }} onClick={() => setBellOpen(false)} aria-hidden="true" />
              <div className="adm-pop" role="dialog" aria-label="Notifications" style={{ zIndex: 60 }}>
                <h3>Notifications</h3>
                {alerts.length === 0
                  ? <p className="secondary" style={{ margin: '4px 8px 10px' }}>No alerts right now.</p>
                  : alerts.slice(0, 10).map((a) => (
                    <div key={a.id} className="adm-pop-item">
                      <p>{a.message}</p>
                      <p className="adm-pop-time">{fmtDateTime(a.createdAt)}{a.readFlag || readLocally ? '' : ' · Unread'}</p>
                    </div>
                  ))}
                {alerts.length > 0 && <div style={{ padding: '6px 8px' }}><Button onClick={() => setReadLocally(true)}>Mark all as read</Button></div>}
              </div>
            </>}
          </div>
          <div className="adm-pop-wrap">
            <button type="button" className="adm-avatar-btn" aria-label="Admin menu" aria-expanded={menuOpen} onClick={() => { setMenuOpen((o) => !o); setBellOpen(false); }}>{initials}</button>
            {menuOpen && <>
              <div style={{ position: 'fixed', inset: 0, zIndex: 55 }} onClick={() => setMenuOpen(false)} aria-hidden="true" />
              <div className="adm-pop adm-pop-menu" role="menu" style={{ width: 220, zIndex: 60 }}>
                <p className="secondary" style={{ margin: '6px 10px' }}>{user?.email}<br />{isPlatformAdmin ? 'Platform Admin' : 'University Moderator'}</p>
                <button type="button" role="menuitem" onClick={() => nav('/admin/settings')}>Admin Profile</button>
                <button type="button" role="menuitem" onClick={() => nav('/admin/settings')}>Admin Settings</button>
                <button type="button" role="menuitem" onClick={() => { setMenuOpen(false); setSignOutOpen(true); }}>Sign Out</button>
              </div>
            </>}
          </div>
        </header>
        <main className="adm-main">
          <Outlet />
          {logoutError && <p role="alert" className="field-error">Sign-out did not complete on the server: {logoutError} Your server session may still be active.</p>}
        </main>
      </div>
      {signOutOpen && (
        <Modal title="Sign out of Admin Portal?" onClose={() => setSignOutOpen(false)}>
          <p>Are you sure you want to sign out? You will need to log in again — with two-factor authentication — to return.</p>
          <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
            <Button variant="primary" onClick={logout}>Sign Out</Button>
            <Button onClick={() => setSignOutOpen(false)}>Cancel</Button>
          </div>
        </Modal>
      )}
    </div>
  );
}
