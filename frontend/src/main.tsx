import React from 'react'; import ReactDOM from 'react-dom/client';
import { BrowserRouter, Link, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import './design/tokens.css';
import { ThemeProvider } from './app/ThemeContext'; import { AuthProvider, RequireAuth } from './app/auth'; import { Layout } from './app/Layout';
import Landing from './features/auth/Landing'; import SignUp from './features/auth/SignUp'; import OtpStep from './features/auth/OtpStep';
import EmailVerify from './features/auth/EmailVerify'; import Pending from './features/auth/Pending'; import Login from './features/auth/Login';
import ForgotPassword from './features/auth/ForgotPassword'; import PublicAppeal from './features/auth/PublicAppeal';
import Feed from './features/feed/Feed'; import PostDetail from './features/posts/PostDetail'; import EventsPage from './features/events/EventsPage';
import EventDetail from './features/events/EventDetail'; import TeamsPage from './features/teams/TeamsPage'; import SearchPage from './features/search/SearchPage';
import ProfilePage from './features/profile/ProfilePage'; import MessagesPage from './features/messages/MessagesPage';
import NotificationsPage from './features/notifications/NotificationsPage'; import SavedPage from './features/posts/SavedPage';
import SettingsPage from './features/settings/SettingsPage'; import CalendarPage from './features/events/CalendarPage';
const qc = new QueryClient({ defaultOptions:{ queries:{ retry:1, staleTime: 30_000 } } });
function NotFound(){ return <div className="container"><h1 className="display">Page not found</h1><p className="secondary">The page you asked for does not exist.</p></div>; }
/** I-10: every route that needs a session is guarded; the public auth pages
 *  (landing, sign-up, OTP, email verify, pending, log in, forgot password)
 *  stay open. /pending stays unguarded on purpose: registration does not
 *  create a session, so the status check there runs unauthenticated. */
function NeedLogin(){ return <div className="container"><h1 className="display">Please log in</h1><p className="secondary">You need to be signed in to see this page.</p><p><Link className="btn btn-primary" to="/login">Log in</Link></p></div>; }
function Guard({children}:{children:React.ReactNode}){ return <RequireAuth fallback={<NeedLogin/>}>{children}</RequireAuth>; }
ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><QueryClientProvider client={qc}><ThemeProvider><AuthProvider><BrowserRouter><Routes><Route element={<Layout/>}>
<Route index element={<Landing/>}/><Route path="signup" element={<SignUp/>}/><Route path="otp" element={<OtpStep/>}/><Route path="verify-email" element={<EmailVerify/>}/>
<Route path="pending" element={<Pending/>}/><Route path="login" element={<Login/>}/><Route path="forgot-password" element={<ForgotPassword/>}/><Route path="appeals/public" element={<PublicAppeal/>}/>
<Route path="feed" element={<Guard><Feed/></Guard>}/><Route path="posts/:id" element={<Guard><PostDetail/></Guard>}/><Route path="events" element={<Guard><EventsPage/></Guard>}/><Route path="events/:id" element={<Guard><EventDetail/></Guard>}/>
<Route path="calendar" element={<Guard><CalendarPage/></Guard>}/><Route path="teams" element={<Guard><TeamsPage/></Guard>}/><Route path="search" element={<Guard><SearchPage/></Guard>}/>
<Route path="profile/:id" element={<Guard><ProfilePage/></Guard>}/><Route path="messages" element={<Guard><MessagesPage/></Guard>}/><Route path="notifications" element={<Guard><NotificationsPage/></Guard>}/>
<Route path="saved" element={<Guard><SavedPage/></Guard>}/><Route path="settings" element={<Guard><SettingsPage/></Guard>}/>
<Route path="*" element={<NotFound/>}/></Route></Routes></BrowserRouter></AuthProvider></ThemeProvider></QueryClientProvider></React.StrictMode>);
