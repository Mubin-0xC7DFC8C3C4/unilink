import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '../app/auth';
import { ThemeProvider } from '../app/ThemeContext';
import SettingsModal from '../features/settings/SettingsModal';
import ProfilePage from '../features/profile/ProfilePage';
import { parseLinks, composeLinks } from '../features/profile/links';

/** D-199/D-202 (Slice C): PATCH-only dirty saves, phoneVisible seeding,
 *  links round-trip, phone-change OTP flow, cover display, and the
 *  removal of the Education / Pinned Projects / Achievements sections
 *  from the profile page (PortfolioManager in Settings stays). */

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const fetchMock = vi.fn();
const calls: { url: string; method: string; body?: unknown }[] = [];
let profileFixture: Record<string, unknown> = {};
let phoneChangeMode: 'ok' | 'sms-unavailable' = 'ok';
let deferProfile: (() => void) | null = null;

function qc() { return new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } }); }

function renderModal(tab: 'Edit Profile' | 'Privacy' = 'Edit Profile') {
  return render(<QueryClientProvider client={qc()}><ThemeProvider><AuthProvider><MemoryRouter>
    <SettingsModal open initialTab={tab} onClose={() => { }} />
  </MemoryRouter></AuthProvider></ThemeProvider></QueryClientProvider>);
}

function renderProfilePage() {
  return render(<QueryClientProvider client={qc()}><ThemeProvider><AuthProvider><MemoryRouter initialEntries={['/profile/me']}>
    <Routes><Route path="/profile/:id" element={<ProfilePage />} /></Routes>
  </MemoryRouter></AuthProvider></ThemeProvider></QueryClientProvider>);
}

beforeEach(() => {
  calls.length = 0;
  phoneChangeMode = 'ok';
  deferProfile = null;
  profileFixture = {
    id: 7, userId: 7, fullName: 'Test Student', email: 'test@example.com', phone: '01711111111',
    state: 'VERIFIED', bio: 'old bio', headline: 'old headline',
    links: 'GitHub: https://github.com/test\nPortfolio: https://example.com\nhttps://my-blog.example/unlabelled',
    skills: [], sectors: [], followers: 0, following: 0, isOwner: true,
    universityName: 'Metropolitan University', departmentName: 'CSE', batchLabel: '45',
    hasPhoto: true, hasCover: true, phoneVisible: true,
  };
  fetchMock.mockImplementation(async (input: unknown, init?: { method?: string; body?: unknown }) => {
    const url = String(input); const method = init?.method || 'GET';
    calls.push({ url, method, body: init?.body });
    if (url === '/api/csrf') return json({});
    if (url === '/api/auth/me') return json({ message: 'unauthenticated' }, 401);
    if (url === '/api/profiles/me' && method === 'GET') {
      if (deferProfile) return new Promise<Response>((resolve) => { deferProfile = () => resolve(json(profileFixture)); });
      return json(profileFixture);
    }
    if (url === '/api/profiles/me/activity-items' && method === 'GET') return json([]);
    if (url === '/api/public/skills') return json([]);
    if (url === '/api/profiles/me/projects' || url === '/api/profiles/me/education' || url === '/api/profiles/me/achievements') return json([]);
    if (url === '/api/profiles/me' && method === 'PATCH') return json(profileFixture);
    if (url === '/api/profiles/me/phone-change' && method === 'POST') {
      if (phoneChangeMode === 'sms-unavailable')
        return json({ code: 'SMS_NOT_CONFIGURED', message: 'Phone change by SMS is not available yet' }, 503);
      return json({ status: 'OTP_SENT' });
    }
    if (url === '/api/profiles/me/phone-change/verify' && method === 'POST') {
      const body = JSON.parse(String(init?.body));
      if (body.otp === '123456') { profileFixture = { ...profileFixture, phone: '01712345678' }; return json({ status: 'verified', phone: '01712345678' }); }
      return json({ code: 'OTP_INVALID', message: 'Incorrect code' }, 400);
    }
    if (url === '/api/profiles/me/phone-change/cancel' && method === 'POST') return json({ status: 'cancelled' });
    if ((url === '/api/profiles/me/photo' || url === '/api/profiles/me/cover') && method === 'DELETE') return json({ status: 'deleted' });
    return json({ message: 'no mock route for ' + url }, 404);
  });
  vi.stubGlobal('fetch', fetchMock);
});
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); sessionStorage.clear(); });

describe('links round-trip', () => {
  it('composeLinks preserves unlabelled legacy lines', () => {
    const raw = 'GitHub: https://github.com/test\nhttps://my-blog.example/unlabelled\nSome plain note';
    const parsed = parseLinks(raw);
    expect(parsed.other).toEqual(['https://my-blog.example/unlabelled', 'Some plain note']);
    const composed = composeLinks({ github: parsed.github, portfolio: parsed.portfolio, linkedin: parsed.linkedin, other: parsed.other });
    expect(composed).toContain('https://my-blog.example/unlabelled');
    expect(composed).toContain('Some plain note');
    expect(parseLinks(composed).other).toEqual(parsed.other);
  });
});

describe('SettingsModal — PATCH saves and phoneVisible seeding', () => {
  it('seeds phoneVisible=true and does not reset it on save', async () => {
    const user = userEvent.setup();
    renderModal('Privacy');
    // Wait for the profile to load AND seed the checkbox — the element
    // exists (unchecked) before the query resolves.
    const checkbox = await screen.findByRole('checkbox', { name: 'Show my phone number', checked: true });
    expect((checkbox as HTMLInputElement).checked).toBe(true);
    await user.click(screen.getByRole('button', { name: 'Save privacy' }));
    // Nothing changed, so no PATCH goes out at all — the old code sent
    // {phoneVisible:false} here and silently turned visibility off.
    expect(calls.filter((c) => c.url === '/api/profiles/me' && c.method === 'PATCH')).toHaveLength(0);
    await user.click(checkbox);
    await user.click(screen.getByRole('button', { name: 'Save privacy' }));
    const patches = calls.filter((c) => c.url === '/api/profiles/me' && c.method === 'PATCH');
    expect(patches).toHaveLength(1);
    expect(JSON.parse(String(patches[0].body))).toEqual({ phoneVisible: false });
  });

  it('PATCH sends only dirty fields', async () => {
    const user = userEvent.setup();
    renderModal('Edit Profile');
    await screen.findByText(/Current number: 01711111111/);
    await user.click(screen.getByRole('button', { name: 'Save profile' }));
    expect(calls.filter((c) => c.url === '/api/profiles/me' && c.method === 'PATCH')).toHaveLength(0);
    const bio = screen.getByLabelText('Bio');
    await user.type(bio, '!');
    await user.click(screen.getByRole('button', { name: 'Save profile' }));
    const patches = calls.filter((c) => c.url === '/api/profiles/me' && c.method === 'PATCH');
    expect(patches).toHaveLength(1);
    expect(JSON.parse(String(patches[0].body))).toEqual({ bio: 'old bio!' });
  });

  it('Save is disabled while the profile query is pending', () => {
    deferProfile = () => { /* released by the test if needed */ };
    renderModal('Edit Profile');
    expect(screen.getByRole('button', { name: 'Save profile' })).toBeDisabled();
  });

  it('shows current photo/cover thumbnails with Remove buttons', async () => {
    renderModal('Edit Profile');
    await screen.findByText(/Current number: 01711111111/);
    expect(screen.getByAltText('Current profile photo')).toHaveAttribute('src', expect.stringContaining('/api/profiles/7/photo?v='));
    expect(screen.getByAltText('Current cover photo')).toHaveAttribute('src', expect.stringContaining('/api/profiles/7/cover?v='));
    expect(screen.getAllByRole('button', { name: 'Remove' })).toHaveLength(2);
  });

  it('PortfolioManager still renders in Settings', async () => {
    renderModal('Edit Profile');
    await screen.findByText(/Current number: 01711111111/);
    expect(screen.getByRole('heading', { name: 'Pinned Projects' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Education' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Achievements' })).toBeInTheDocument();
  });
});

describe('SettingsModal — phone change flow', () => {
  it('shows the OTP step and keeps the old number until success', async () => {
    const user = userEvent.setup();
    renderModal('Edit Profile');
    await screen.findByText(/Current number: 01711111111/);
    await user.type(screen.getByLabelText('New phone number'), '01712345678');
    await user.click(screen.getByRole('button', { name: 'Send verification code' }));
    await screen.findByText(/We sent a 6-digit code to 01712345678/);
    // The old number is still the current number during the OTP step.
    expect(screen.getByText(/Your current number 01711111111 stays in use until this verifies/)).toBeInTheDocument();
    expect(screen.getByText(/Current number: 01711111111/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Resend code \(\d+s\)/ })).toBeInTheDocument();
    await user.type(screen.getByLabelText('Verification code'), '123456');
    await user.click(screen.getByRole('button', { name: 'Verify and change number' }));
    await screen.findByText('Phone number changed.');
    await screen.findByText(/Current number: 01712345678/);
  });

  it('cancel returns to the idle step', async () => {
    const user = userEvent.setup();
    renderModal('Edit Profile');
    await screen.findByText(/Current number: 01711111111/);
    await user.type(screen.getByLabelText('New phone number'), '01712345678');
    await user.click(screen.getByRole('button', { name: 'Send verification code' }));
    await screen.findByText(/We sent a 6-digit code to 01712345678/);
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    await screen.findByRole('button', { name: 'Send verification code' });
    expect(calls.filter((c) => c.url === '/api/profiles/me/phone-change/cancel')).toHaveLength(1);
  });

  it('wrong OTP keeps the old number and shows the error', async () => {
    const user = userEvent.setup();
    renderModal('Edit Profile');
    await screen.findByText(/Current number: 01711111111/);
    await user.type(screen.getByLabelText('New phone number'), '01712345678');
    await user.click(screen.getByRole('button', { name: 'Send verification code' }));
    await screen.findByText(/We sent a 6-digit code to 01712345678/);
    await user.type(screen.getByLabelText('Verification code'), '000000');
    await user.click(screen.getByRole('button', { name: 'Verify and change number' }));
    await screen.findByText('Incorrect code');
    expect(screen.getByText(/Current number: 01711111111/)).toBeInTheDocument();
  });

  it('shows a clear unavailable state when no SMS gateway is configured', async () => {
    phoneChangeMode = 'sms-unavailable';
    const user = userEvent.setup();
    renderModal('Edit Profile');
    await screen.findByText(/Current number: 01711111111/);
    await user.type(screen.getByLabelText('New phone number'), '01712345678');
    await user.click(screen.getByRole('button', { name: 'Send verification code' }));
    await screen.findByText(/Phone change by SMS is not available yet/);
    expect(screen.getByText(/Current number: 01711111111/)).toBeInTheDocument();
  });
});

describe('ProfilePage — cover display and removed sections', () => {
  it('renders the cover image when present and drops the three sections', async () => {
    renderProfilePage();
    await screen.findByText('Test Student');
    const cover = document.querySelector('img.pf-cover-img');
    expect(cover).not.toBeNull();
    expect(cover?.getAttribute('src')).toBe('/api/profiles/7/cover');
    expect(screen.queryByText('Pinned Projects')).toBeNull();
    expect(screen.queryByText('Education')).toBeNull();
    expect(screen.queryByText('Achievements')).toBeNull();
    expect(screen.queryByText('No projects yet.')).toBeNull();
    expect(screen.queryByText('No education entries yet.')).toBeNull();
    expect(screen.queryByText('No achievements yet.')).toBeNull();
    expect(screen.getByRole('heading', { name: 'About Me' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Skills' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Links' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Activity' })).toBeInTheDocument();
  });

  it('falls back to the solid banner when there is no cover', async () => {
    profileFixture = { ...profileFixture, hasCover: false };
    renderProfilePage();
    await screen.findByText('Test Student');
    expect(document.querySelector('img.pf-cover-img')).toBeNull();
    expect(document.querySelector('div.pf-cover')).not.toBeNull();
  });
});
