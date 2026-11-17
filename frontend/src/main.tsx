import React from 'react'; import ReactDOM from 'react-dom/client';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import './design/tokens.css';
import { ThemeProvider } from './app/ThemeContext'; import { AuthProvider } from './app/auth'; import { Layout } from './app/Layout';
import Landing from './features/auth/Landing'; import SignUp from './features/auth/SignUp'; import OtpStep from './features/auth/OtpStep';
import EmailVerify from './features/auth/EmailVerify'; import Pending from './features/auth/Pending'; import Login from './features/auth/Login';
import ForgotPassword from './features/auth/ForgotPassword';
import Feed from './features/feed/Feed'; import PostDetail from './features/posts/PostDetail'; import EventsPage from './features/events/EventsPage';
import EventDetail from './features/events/EventDetail'; import TeamsPage from './features/teams/TeamsPage'; import SearchPage from './features/search/SearchPage';
import ProfilePage from './features/profile/ProfilePage'; import MessagesPage from './features/messages/MessagesPage';
import NotificationsPage from './features/notifications/NotificationsPage'; import SavedPage from './features/posts/SavedPage';
import SettingsPage from './features/settings/SettingsPage'; import CalendarPage from './features/events/CalendarPage';
const qc = new QueryClient({ defaultOptions:{ queries:{ retry:1, staleTime: 30_000 } } });
function NotFound(){ return <div className="container"><h1 className="display">Page not found</h1><p className="secondary">The page you asked for does not exist.</p></div>; }
ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><QueryClientProvider client={qc}><ThemeProvider><AuthProvider><BrowserRouter><Routes><Route element={<Layout/>}>
<Route index element={<Landing/>}/><Route path="signup" element={<SignUp/>}/><Route path="otp" element={<OtpStep/>}/><Route path="verify-email" element={<EmailVerify/>}/>
<Route path="pending" element={<Pending/>}/><Route path="login" element={<Login/>}/><Route path="forgot-password" element={<ForgotPassword/>}/>
<Route path="feed" element={<Feed/>}/><Route path="posts/:id" element={<PostDetail/>}/><Route path="events" element={<EventsPage/>}/><Route path="events/:id" element={<EventDetail/>}/>
<Route path="calendar" element={<CalendarPage/>}/><Route path="teams" element={<TeamsPage/>}/><Route path="search" element={<SearchPage/>}/>
<Route path="profile/:id" element={<ProfilePage/>}/><Route path="messages" element={<MessagesPage/>}/><Route path="notifications" element={<NotificationsPage/>}/>
<Route path="saved" element={<SavedPage/>}/><Route path="settings" element={<SettingsPage/>}/>
<Route path="*" element={<NotFound/>}/></Route></Routes></BrowserRouter></AuthProvider></ThemeProvider></QueryClientProvider></React.StrictMode>);
