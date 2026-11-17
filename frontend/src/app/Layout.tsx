import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useTheme } from './ThemeContext';
import { useAuth } from './auth';
import { apiPost } from '../api/client';
export function Layout(){
  const { theme, toggle } = useTheme(); const { user, setUser } = useAuth(); const nav = useNavigate();
  const logout = async ()=>{ try{ await apiPost('/auth/logout'); }catch{} setUser(null); nav('/login'); };
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
    <footer className="container" style={{padding:'32px 16px'}}><p className="muted-caption">UniLink — verified student network for Bangladesh universities. Your data is stored on the server, not in this browser.</p></footer>
  </div>;
}
