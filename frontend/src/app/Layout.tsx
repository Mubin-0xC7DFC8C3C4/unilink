import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { useTheme } from './ThemeContext';
import { useAuth } from './auth';
import { apiPost, errorMessage } from '../api/client';
export function Layout(){
  const { theme, toggle } = useTheme(); const { user, setUser } = useAuth(); const nav = useNavigate(); const loc = useLocation();
  // D-094/D-098: a restored session can also carry mustChangePassword (the
  // flag is reported by /me). Redirect to Settings — which holds the
  // change-password form — from anywhere else, so the user never sits on
  // a page whose calls all 403. Settings itself is exempt (no loop).
  useEffect(()=>{ if (user?.mustChangePassword && loc.pathname !== '/settings') nav('/settings', { replace: true }); }, [user?.mustChangePassword, loc.pathname]);
  const [logoutError, setLogoutError] = useState<string|null>(null);
  // A failed sign-out is surfaced, not swallowed: the local session is
  // still cleared (the user asked to leave) but the failure is shown so
  // a dead logout endpoint can never hide behind a silent redirect.
  const logout = async ()=>{ try{ await apiPost('/auth/logout'); }catch(e){ setLogoutError(errorMessage(e)); } setUser(null); nav('/login'); };
  return <div><header className="header"><div className="header-inner">
    <Link to="/" style={{textDecoration:'none',fontWeight:500}}>UniLink</Link>
    <nav aria-label="Primary"><NavLink to="/feed">Feed</NavLink><NavLink to="/events">Events</NavLink><NavLink to="/teams">Teams</NavLink><NavLink to="/messages">Messages</NavLink><NavLink to="/search">Search</NavLink></nav>
    <div style={{marginLeft:'auto',display:'flex',gap:8,alignItems:'center'}}>
      <Link to="/notifications" aria-label="Notifications">Notifications</Link>
      <button className="btn btn-sm" onClick={toggle} aria-label="Toggle theme">Theme: {theme}</button>
      {user ? <><Link to="/profile/me">Profile</Link><Link to="/settings">Settings</Link><button className="btn btn-sm" onClick={logout}>Sign out</button></>
      : <><Link className="btn btn-sm" to="/login">Log in</Link><Link className="btn btn-sm btn-primary" to="/signup">Sign up</Link></>}
    </div></div></header>
    <main><Outlet/></main>
    {logoutError && <p role="alert" className="field-error container">Sign-out did not complete on the server: {logoutError} Your server session may still be active — sign in again and sign out, or wait for it to expire.</p>}
    <footer className="container" style={{padding:'32px 16px'}}><p className="muted-caption">UniLink — verified student network for Bangladesh universities. Your data is stored on the server, not in this browser.</p></footer>
  </div>;
}
