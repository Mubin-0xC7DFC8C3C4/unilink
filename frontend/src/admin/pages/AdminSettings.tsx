import { Link } from 'react-router-dom';
import { useAuth } from '../../app/auth';
import { useTheme } from '../../app/ThemeContext';
import { PageHeader } from '../../design/primitives';
import { Button } from '../../design/primitives';

/**
 * Admin Settings. Only settings the platform actually supports are
 * shown: the admin's own profile (from GET /admin/auth/me), password
 * changes (the existing MFA-verified flow), two-factor authentication
 * (the existing TOTP enrolment flow) and the portal theme. There is no
 * admin notification-preference or session-list service, so those
 * sections are not presented as if they existed.
 */
export default function AdminSettings() {
  const { user } = useAuth();
  const { theme, toggle } = useTheme();
  const roleLabel = user?.role === 'PLATFORM_ADMIN' ? 'Platform Admin' : 'University Moderator';
  return (
    <div>
      <PageHeader eyebrow="Admin Portal" title="Admin Settings" lead="Your administrator profile and security settings." />
      <section className="card-group" aria-label="Admin Profile">
        <h2 className="title">Admin Profile</h2>
        <div className="row"><span>Email</span><span>{user?.email ?? '—'}</span></div>
        <div className="row"><span>Role</span><span>{roleLabel}</span></div>
        <div className="row"><span>Scope</span><span>{user?.role === 'PLATFORM_ADMIN' ? 'All universities' : 'Your university only'}</span></div>
      </section>
      <section className="card-group" style={{ marginTop: 16 }} aria-label="Security and password">
        <h2 className="title">Security &amp; Password</h2>
        <p className="secondary">Change your password from the dedicated screen. Password changes require a current two-factor code, and temporary passwords expire after 24 hours.</p>
        <p><Link className="btn" to="/admin/change-password">Change password</Link></p>
      </section>
      <section className="card-group" style={{ marginTop: 16 }} aria-label="Two-factor authentication">
        <h2 className="title">Two-Factor Authentication</h2>
        <p className="secondary">UniLink administrators sign in with a password plus a time-based code (or a recovery code). Manage enrolment on the two-factor screen shown at sign-in.</p>
        <p><Link className="btn" to="/admin/totp">Open two-factor setup</Link></p>
      </section>
      <section className="card-group" style={{ marginTop: 16 }} aria-label="Active sessions">
        <h2 className="title">Active Sessions</h2>
        <div className="row"><span>This device</span><span>Signed in as {user?.email ?? '—'} · two-factor verified</span></div>
        <div className="row"><span>Session policy</span><span>Admin sessions end after 30 minutes of inactivity; signing out ends this session immediately.</span></div>
        <p className="secondary">Other signed-in devices are not listed here.</p>
      </section>
      <section className="card-group" style={{ marginTop: 16 }} aria-label="Appearance">
        <h2 className="title">Appearance</h2>
        <p className="secondary">Portal theme for this device.</p>
        <Button onClick={toggle}>Theme: {theme === 'dark' ? 'Dark' : 'Light'} — switch</Button>
      </section>
    </div>
  );
}
