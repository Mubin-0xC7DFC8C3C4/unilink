import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '../app/auth';
import { ThemeProvider } from '../app/ThemeContext';
import SignUp from '../features/auth/SignUp';

/** Phase 2.2 (owner instruction 2026-11-20): searchable catalogue
 *  selectors, two separate ID upload fields on the one-file contract,
 *  confirm password, and upload validation — all on the existing
 *  /auth/register API, unchanged. */

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const fetchMock = vi.fn();
const calls: { url: string; method: string }[] = [];
function qc() { return new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } }); }
function renderPage() {
  return render(<QueryClientProvider client={qc()}><ThemeProvider><AuthProvider><MemoryRouter initialEntries={['/signup']}><Routes><Route path="/signup" element={<SignUp />} /><Route path="/otp" element={<div>OTP PAGE</div>} /></Routes></MemoryRouter></AuthProvider></ThemeProvider></QueryClientProvider>);
}

beforeEach(() => {
  sessionStorage.clear(); calls.length = 0;
  fetchMock.mockImplementation(async (input: unknown, init?: { method?: string }) => {
    const url = String(input); const method = init?.method || 'GET';
    calls.push({ url, method });
    if (url === '/api/csrf') return json({});
    if (url === '/api/auth/me') return json({ message: 'unauthenticated' }, 401);
    if (url === '/api/public/universities') return json([{ id: 1, name: 'Metropolitan University' }, { id: 2, name: 'University of Dhaka' }]);
    if (url.startsWith('/api/public/departments')) return json([{ id: 10, universityId: 1, name: 'CSE' }]);
    if (url.startsWith('/api/public/batches')) return json([{ id: 100, universityId: 1, departmentId: 10, label: '45' }]);
    if (url === '/api/auth/register' && method === 'POST') return json({ id: 42 }, 201);
    return json({ message: 'no mock route for ' + url }, 404);
  });
  vi.stubGlobal('fetch', fetchMock);
});
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); });

async function toStep3(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Full name'), 'Test User');
  await user.type(screen.getByLabelText('Phone'), '01712345678');
  await user.type(screen.getByLabelText('Personal email'), 'test@example.com');
  await user.click(screen.getByRole('button', { name: /Continue/ }));
  await user.click(screen.getByLabelText('University'));
  await user.click(await screen.findByRole('option', { name: 'Metropolitan University' }));
  await user.click(screen.getByLabelText('Department'));
  await user.click(await screen.findByRole('option', { name: 'CSE' }));
  await user.type(screen.getByLabelText('Address'), '123 Test Road, Dhaka');
  await user.click(screen.getByRole('button', { name: /Continue/ }));
  await user.type(screen.getByLabelText('Student ID number'), 'CSE-001');
}

describe('SignUp page (Phase 2.2)', () => {
  it('filters the university list as the user types', async () => {
    const user = userEvent.setup(); renderPage();
    await user.type(screen.getByLabelText('Full name'), 'Test User');
    await user.type(screen.getByLabelText('Phone'), '01712345678');
    await user.type(screen.getByLabelText('Personal email'), 'test@example.com');
    await user.click(screen.getByRole('button', { name: /Continue/ }));
    await user.click(screen.getByLabelText('University'));
    await screen.findByRole('option', { name: 'Metropolitan University' });
    await user.type(screen.getByLabelText('University'), 'Dhaka');
    expect(screen.getByRole('option', { name: 'University of Dhaka' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'Metropolitan University' })).toBeNull();
  });

  it('shows the mismatch message only after typing in the confirmation field', async () => {
    const user = userEvent.setup(); renderPage();
    await toStep3(user);
    // Arriving at step 3, and typing only the password, must not surface
    // the mismatch message (owner polish, 2026-11-20).
    await user.type(screen.getByLabelText('Password', { exact: true }), 'Password1');
    expect(screen.queryByText('Passwords do not match')).toBeNull();
    // Once the confirmation is typed and differs, the message appears.
    await user.type(screen.getByLabelText('Confirm password'), 'Password2');
    await screen.findByText('Passwords do not match');
  });

  it('blocks submission when the passwords do not match', async () => {
    const user = userEvent.setup(); renderPage();
    await toStep3(user);
    await user.type(screen.getByLabelText('Password', { exact: true }), 'Password1');
    await user.type(screen.getByLabelText('Confirm password'), 'Password2');
    await user.upload(screen.getByLabelText('Student ID card — Front side'), new File(['f'], 'front.jpg', { type: 'image/jpeg' }));
    await user.upload(screen.getByLabelText('Student ID card — Back side'), new File(['b'], 'back.jpg', { type: 'image/jpeg' }));
    await user.upload(screen.getByLabelText('Profile photo'), new File(['p'], 'photo.jpg', { type: 'image/jpeg' }));
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    await screen.findByText('Passwords do not match');
    expect(calls.some((c) => c.url === '/api/auth/register')).toBe(false);
  });

  it('requires the back side of the ID card before submitting', async () => {
    const user = userEvent.setup(); renderPage();
    await toStep3(user);
    await user.type(screen.getByLabelText('Password', { exact: true }), 'Password1');
    await user.type(screen.getByLabelText('Confirm password'), 'Password1');
    await user.upload(screen.getByLabelText('Student ID card — Front side'), new File(['f'], 'front.jpg', { type: 'image/jpeg' }));
    await user.upload(screen.getByLabelText('Profile photo'), new File(['p'], 'photo.jpg', { type: 'image/jpeg' }));
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    await screen.findByText('Please attach the back side of your ID card.');
    expect(calls.some((c) => c.url === '/api/auth/register')).toBe(false);
  });

  it('rejects a non-image file on the front upload', async () => {
    const user = userEvent.setup(); renderPage();
    await toStep3(user);
    const input = screen.getByLabelText('Student ID card — Front side');
    fireEvent.change(input, { target: { files: [new File(['x'], 'notes.txt', { type: 'text/plain' })] } });
    await screen.findByText('Use a JPG, PNG or WebP image.');
  });
});
