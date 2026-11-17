import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useTheme } from '../app/ThemeContext';
import { apiPost } from '../api/client';
const NAV=[['Dashboard','/admin'],['Verification queue','/admin/verifications'],['Users','/admin/users'],['Moderation','/admin/moderation'],['Appeals','/admin/appeals'],['Events','/admin/events'],['Announcements','/admin/announcements'],['Master data','/admin/master-data'],['Moderators','/admin/moderators'],['Analytics','/admin/analytics'],['Audit log','/admin/audit']];
export function AdminLayout(){ const {theme,toggle}=useTheme(); const nav=useNavigate();
  const logout=async()=>{ try{ await apiPost('/admin/logout'); }catch{} nav('/admin/login'); };
  return <div className="admin-layout"><aside className="admin-rail" aria-label="Admin navigation"><Link to="/admin" style={{fontWeight:500,textDecoration:'none'}}>UniLink Admin</Link><nav style={{marginTop:16}}>{NAV.map(([label,href])=><NavLink key={href} to={href} end={href==='/admin'}>{label}</NavLink>)}</nav><div style={{marginTop:24}}><button className="btn btn-sm" onClick={toggle}>Theme: {theme}</button>{' '}<button className="btn btn-sm" onClick={logout}>Sign out</button></div></aside><main className="admin-main"><Outlet/></main></div>;
}
