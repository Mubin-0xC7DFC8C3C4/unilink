import React from 'react'; import ReactDOM from 'react-dom/client';
import { BrowserRouter, Link, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '../design/tokens.css';
import { ThemeProvider } from '../app/ThemeContext'; import { AuthProvider, RequireAuth } from '../app/auth';
import { AdminLayout } from './AdminLayout';
import AdminLogin from './pages/AdminLogin'; import Totp from './pages/Totp';
import AdminChangePassword from './pages/AdminChangePassword';
import Dashboard from './pages/Dashboard'; import Verifications from './pages/Verifications'; import Users from './pages/Users';
import Moderation from './pages/Moderation'; import Appeals from './pages/Appeals'; import AdminEvents from './pages/AdminEvents';
import Announcements from './pages/Announcements'; import MasterData from './pages/MasterData'; import Moderators from './pages/Moderators';
import Analytics from './pages/Analytics'; import Audit from './pages/Audit';
import CreateEvent from './pages/CreateEvent'; import EditEvent from './pages/EditEvent'; import AdminSettings from './pages/AdminSettings'; import IdentityChanges from './pages/IdentityChanges';
import LoginActivity from './pages/LoginActivity'; import AllPosts from './pages/AllPosts';
const qc=new QueryClient({defaultOptions:{queries:{retry:1}}});
function NotFound(){ return <div><h1 className="display">Page not found</h1></div>; }
/** I-10: the admin section is guarded by the admin session (restored from
 *  GET /api/admin/auth/me, not the student /me). Login and TOTP stay open;
 *  the idle warning fires at 25 minutes — the server ends idle admin
 *  sessions at 30 minutes. */
function AdminNeedLogin(){ return <div className="container"><h1 className="display">Admin sign-in required</h1><p className="secondary">You need to be signed in as an admin, with two-factor authentication, to see this page.</p><p><Link className="btn btn-primary" to="/admin/login">Admin log in</Link></p></div>; }
function AdminGuard({children}:{children:React.ReactNode}){ return <RequireAuth fallback={<AdminNeedLogin/>}>{children}</RequireAuth>; }
ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><QueryClientProvider client={qc}><ThemeProvider><AuthProvider restorePath="/admin/auth/me" idleMinutes={25} idleNoticeText="You have been idle for a while. For security, idle admin sessions end after 30 minutes — save anything important, then keep working or log in again."><BrowserRouter><Routes>
<Route path="admin/login" element={<AdminLogin/>}/><Route path="admin/totp" element={<Totp/>}/>
<Route path="admin" element={<AdminGuard><AdminLayout/></AdminGuard>}><Route index element={<Dashboard/>}/><Route path="change-password" element={<AdminChangePassword/>}/><Route path="verifications" element={<Verifications/>}/><Route path="users" element={<Users/>}/><Route path="moderation" element={<Moderation/>}/><Route path="appeals" element={<Appeals/>}/><Route path="events" element={<AdminEvents/>}/><Route path="events/new" element={<CreateEvent/>}/><Route path="events/:id/edit" element={<EditEvent/>}/><Route path="posts" element={<AllPosts/>}/><Route path="login-activity" element={<LoginActivity/>}/><Route path="announcements" element={<Announcements/>}/><Route path="master-data" element={<MasterData/>}/><Route path="moderators" element={<Moderators/>}/><Route path="analytics" element={<Analytics/>}/><Route path="audit" element={<Audit/>}/><Route path="identity-changes" element={<IdentityChanges/>}/><Route path="settings" element={<AdminSettings/>}/><Route path="*" element={<NotFound/>}/></Route>
</Routes></BrowserRouter></AuthProvider></ThemeProvider></QueryClientProvider></React.StrictMode>);
