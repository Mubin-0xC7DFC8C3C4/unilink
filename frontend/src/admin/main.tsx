import React from 'react'; import ReactDOM from 'react-dom/client';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '../design/tokens.css';
import { ThemeProvider } from '../app/ThemeContext'; import { AuthProvider } from '../app/auth';
import { AdminLayout } from './AdminLayout';
import AdminLogin from './pages/AdminLogin'; import Totp from './pages/Totp';
import Dashboard from './pages/Dashboard'; import Verifications from './pages/Verifications'; import Users from './pages/Users';
import Moderation from './pages/Moderation'; import Appeals from './pages/Appeals'; import AdminEvents from './pages/AdminEvents';
import Announcements from './pages/Announcements'; import MasterData from './pages/MasterData'; import Moderators from './pages/Moderators';
import Analytics from './pages/Analytics'; import Audit from './pages/Audit';
const qc=new QueryClient({defaultOptions:{queries:{retry:1}}});
function NotFound(){ return <div><h1 className="display">Page not found</h1></div>; }
ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><QueryClientProvider client={qc}><ThemeProvider><AuthProvider><BrowserRouter basename="/admin"><Routes>
<Route path="login" element={<AdminLogin/>}/><Route path="totp" element={<Totp/>}/>
<Route element={<AdminLayout/>}><Route index element={<Dashboard/>}/><Route path="verifications" element={<Verifications/>}/><Route path="users" element={<Users/>}/><Route path="moderation" element={<Moderation/>}/><Route path="appeals" element={<Appeals/>}/><Route path="events" element={<AdminEvents/>}/><Route path="announcements" element={<Announcements/>}/><Route path="master-data" element={<MasterData/>}/><Route path="moderators" element={<Moderators/>}/><Route path="analytics" element={<Analytics/>}/><Route path="audit" element={<Audit/>}/><Route path="*" element={<NotFound/>}/></Route>
</Routes></BrowserRouter></AuthProvider></ThemeProvider></QueryClientProvider></React.StrictMode>);
