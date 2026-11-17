import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { useTheme } from '../app/ThemeContext';
import { useAuth } from '../app/auth';
import { apiPost, errorMessage } from '../api/client';
const NAV=[['Dashboard','/admin'],['Verification queue','/admin/verifications'],['Users','/admin/users'],['Moderation','/admin/moderation'],['Appeals','/admin/appeals'],['Events','/admin/events'],['Announcements','/admin/announcements'],['Master data','/admin/master-data'],['Moderators','/admin/moderators'],['Analytics','/admin/analytics'],['Audit log','/admin/audit']];
export function AdminLayout(){ const {theme,toggle}=useTheme(); const nav=useNavigate(); const { setUser }=useAuth();
  const [logoutError, setLogoutError] = useState<string|null>(null);
  // See Layout.tsx: a failed sign-out is surfaced, not swallowed.
  const logout=async()=>{ try{ await apiPost('/admin/auth/logout'); }catch(e){ setLogoutError(errorMessage(e)); } setUser(null); nav('/admin/login'); };
  return <div className="admin-layout"><aside className="admin-rail" aria-label="Admin navigation"><Link to="/admin" style={{fontWeight:500,textDecoration:'none'}}>UniLink Admin</Link><nav style={{marginTop:16}}>{NAV.map(([label,href])=><NavLink key={href} to={href} end={href==='/admin'}>{label}</NavLink>)}</nav><div style={{marginTop:24}}><button className="btn btn-sm" onClick={toggle}>Theme: {theme}</button>{' '}<button className="btn btn-sm" onClick={logout}>Sign out</button></div></aside><main className="admin-main"><Outlet/>{logoutError && <p role="alert" className="field-error">Sign-out did not complete on the server: {logoutError} Your server session may still be active.</p>}</main></div>;
}
