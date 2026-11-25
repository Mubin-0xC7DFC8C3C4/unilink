import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '../app/auth';
import { ThemeProvider } from '../app/ThemeContext';
import SignUp from '../features/auth/SignUp';

/** SignUp (owner brief 2026-11-25 §1/§2, D-190/D-193/D-194): ten fields,
 *  TWO student ID card images (front + back), no profile photo at
 *  registration (that stays in Settings → Edit Profile), "University
 *  Address", free-text batch/session, department as a dropdown or a typed
 *  label, per-field validation with the brief's exact messages, and the
 *  4-rule password checklist from the real backend rules. */

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const fetchMock = vi.fn();
const calls: { url: string; method: string; body?: unknown }[] = [];
function qc() { return new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } }); }
function renderPage() {
  return render(<QueryClientProvider client={qc()}><ThemeProvider><AuthProvider><MemoryRouter initialEntries={['/signup']}><Routes><Route path="/signup" element={<SignUp />} /><Route path="/verification" element={<div>VERIFICATION PAGE</div>} /></Routes></MemoryRouter></AuthProvider></ThemeProvider></QueryClientProvider>);
}

beforeEach(() => {
  sessionStorage.clear(); calls.length = 0;
  fetchMock.mockImplementation(async (input: unknown, init?: { method?: string; body?: unknown }) => {
    const url = String(input); const method = init?.method || 'GET';
    calls.push({ url, method, body: init?.body });
    if (url === '/api/csrf') return json({});
    if (url === '/api/auth/me') return json({ message: 'unauthenticated' }, 401);
    if (url === '/api/public/universities') return json([{ id: 1, name: 'Metropolitan University' }, { id: 2, name: 'University of Dhaka' }]);
    if (url.startsWith('/api/public/departments')) return json([{ id: 10, universityId: 1, name: 'CSE' }]);
    if (url === '/api/auth/register' && method === 'POST') return json({ id: 42, verificationToken: 'tok-42' }, 201);
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
  await user.type(screen.getByLabelText('Batch / Session'), '45');
  await user.type(screen.getByLabelText('Your address'), '123 Test Road, Dhaka');
  await user.click(screen.getByRole('button', { name: /Continue/ }));
  await user.type(screen.getByLabelText('Student ID number'), 'CSE-001');
}

describe('SignUp page (2026-11-25 upgrade)', () => {
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

  it('validates step 1 on blur with the brief messages, and shows no errors on untouched fields', async () => {
    const user = userEvent.setup(); renderPage();
    expect(screen.queryByText('Full name is required.')).toBeNull();
    expect(screen.queryByText('Phone number is required.')).toBeNull();
    expect(screen.queryByText('Please enter your personal email address.')).toBeNull();
    await user.click(screen.getByLabelText('Full name'));
    await user.click(screen.getByLabelText('Phone'));
    await screen.findByText('Full name is required.');
    await user.type(screen.getByLabelText('Phone'), '01234567890');
    await user.click(screen.getByLabelText('Personal email'));
    await screen.findByText('Enter a valid Bangladeshi mobile number, e.g. 01712345678');
    await user.type(screen.getByLabelText('Personal email'), 'not-an-email');
    await user.click(screen.getByLabelText('Full name'));
    await screen.findByText('Enter a valid email address');
  });

  it('shows the password checklist live from the real backend rules', async () => {
    const user = userEvent.setup(); renderPage();
    await toStep3(user);
    await user.type(screen.getByLabelText('Password', { exact: true }), 'pass');
    const items = screen.getAllByRole('listitem');
    expect(items.some((li) => li.textContent === 'At least 8 characters' && li.classList.contains('unmet'))).toBe(true);
    expect(items.some((li) => li.textContent === 'A lowercase letter' && li.classList.contains('met'))).toBe(true);
    await user.type(screen.getByLabelText('Password', { exact: true }), 'word1A');
    expect(screen.getAllByRole('listitem').every((li) => li.classList.contains('met'))).toBe(true);
  });

  it('has two ID card uploads (front + back) and no profile photo at registration', async () => {
    const user = userEvent.setup(); renderPage();
    await toStep3(user);
    expect(screen.queryByLabelText(/Profile photo/)).toBeNull();
    expect(screen.getByLabelText('Student ID card — front')).toBeInTheDocument();
    expect(screen.getByLabelText('Student ID card — back')).toBeInTheDocument();
  });

  it('blocks submission when the passwords do not match', async () => {
    const user = userEvent.setup(); renderPage();
    await toStep3(user);
    await user.type(screen.getByLabelText('Password', { exact: true }), 'Password1');
    await user.type(screen.getByLabelText('Confirm password'), 'Password2');
    await user.upload(screen.getByLabelText('Student ID card — front'), new File(['f'], 'id.jpg', { type: 'image/jpeg' }));
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    await screen.findByText('Passwords do not match');
    expect(calls.some((c) => c.url === '/api/auth/register')).toBe(false);
  });

  it('requires the front ID card image before submitting', async () => {
    const user = userEvent.setup(); renderPage();
    await toStep3(user);
    await user.type(screen.getByLabelText('Password', { exact: true }), 'Password1');
    await user.type(screen.getByLabelText('Confirm password'), 'Password1');
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    await screen.findByText('Please upload the front of your student ID card.');
    expect(calls.some((c) => c.url === '/api/auth/register')).toBe(false);
  });

  it('requires the back ID card image before submitting', async () => {
    const user = userEvent.setup(); renderPage();
    await toStep3(user);
    await user.type(screen.getByLabelText('Password', { exact: true }), 'Password1');
    await user.type(screen.getByLabelText('Confirm password'), 'Password1');
    await user.upload(screen.getByLabelText('Student ID card — front'), new File(['f'], 'id.jpg', { type: 'image/jpeg' }));
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    await screen.findByText('Please upload the back of your student ID card.');
    expect(calls.some((c) => c.url === '/api/auth/register')).toBe(false);
  });

  it('rejects an invalid free-text batch before submitting', async () => {
    const user = userEvent.setup(); renderPage();
    await user.type(screen.getByLabelText('Full name'), 'Test User');
    await user.type(screen.getByLabelText('Phone'), '01712345678');
    await user.type(screen.getByLabelText('Personal email'), 'test@example.com');
    await user.click(screen.getByRole('button', { name: /Continue/ }));
    await user.click(screen.getByLabelText('University'));
    await user.click(await screen.findByRole('option', { name: 'Metropolitan University' }));
    await user.click(screen.getByLabelText('Department'));
    await user.click(await screen.findByRole('option', { name: 'CSE' }));
    await user.type(screen.getByLabelText('Batch / Session'), 'Fall 2024!');
    await user.click(screen.getByRole('button', { name: /Continue/ }));
    await screen.findByText('Batch/session may only contain letters, digits, spaces, hyphens, slashes, dots or parentheses');
    expect(calls.some((c) => c.url === '/api/auth/register')).toBe(false);
  });

  it('offers a typed department when the department is not listed', async () => {
    const user = userEvent.setup(); renderPage();
    await user.type(screen.getByLabelText('Full name'), 'Test User');
    await user.type(screen.getByLabelText('Phone'), '01712345678');
    await user.type(screen.getByLabelText('Personal email'), 'test@example.com');
    await user.click(screen.getByRole('button', { name: /Continue/ }));
    await user.click(screen.getByLabelText('University'));
    await user.click(await screen.findByRole('option', { name: 'Metropolitan University' }));
    await user.click(screen.getByRole('button', { name: /type it instead/ }));
    await user.type(screen.getByLabelText('Department'), 'Robotics and Mechatronics');
    await user.type(screen.getByLabelText('Batch / Session'), 'Fall 2024');
    await user.type(screen.getByLabelText('Your address'), '123 Test Road, Dhaka');
    await user.click(screen.getByRole('button', { name: /Continue/ }));
    await user.type(screen.getByLabelText('Student ID number'), 'CSE-001');
    await user.type(screen.getByLabelText('Password', { exact: true }), 'Password1');
    await user.type(screen.getByLabelText('Confirm password'), 'Password1');
    await user.upload(screen.getByLabelText('Student ID card — front'), new File(['f'], 'id.jpg', { type: 'image/jpeg' }));
    await user.upload(screen.getByLabelText('Student ID card — back'), new File(['b'], 'id-back.jpg', { type: 'image/jpeg' }));
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    await screen.findByText('VERIFICATION PAGE');
    const reg = calls.find((c) => c.url === '/api/auth/register');
    const fd = reg?.body as FormData;
    expect(fd.get('departmentId')).toBeNull();
    expect(fd.get('departmentLabel')).toBe('Robotics and Mechatronics');
    expect(fd.get('batch')).toBe('Fall 2024');
  });

  it('rejects a non-image file on the ID card upload', async () => {
    const user = userEvent.setup(); renderPage();
    await toStep3(user);
    const input = screen.getByLabelText('Student ID card — front');
    fireEvent.change(input, { target: { files: [new File(['x'], 'notes.txt', { type: 'text/plain' })] } });
    await screen.findByText('Use a JPG, PNG or WebP image.');
  });

  it('submits both ID card images and the free-text batch, then navigates to the verification flow', async () => {
    const user = userEvent.setup(); renderPage();
    await toStep3(user);
    await user.type(screen.getByLabelText('Password', { exact: true }), 'Password1');
    await user.type(screen.getByLabelText('Confirm password'), 'Password1');
    await user.upload(screen.getByLabelText('Student ID card — front'), new File(['f'], 'id.jpg', { type: 'image/jpeg' }));
    await user.upload(screen.getByLabelText('Student ID card — back'), new File(['b'], 'id-back.jpg', { type: 'image/jpeg' }));
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    await screen.findByText('VERIFICATION PAGE');
    const reg = calls.find((c) => c.url === '/api/auth/register');
    const fd = reg?.body as FormData;
    expect((fd.get('idCard') as File).name).toBe('id.jpg');
    expect((fd.get('idCardBack') as File).name).toBe('id-back.jpg');
    expect(fd.get('batch')).toBe('45');
    expect(fd.get('batchId')).toBeNull();
    expect(fd.get('departmentId')).toBe('10');
    expect(fd.get('profilePhoto')).toBeNull();
    expect(sessionStorage.getItem('unilink:verificationToken')).toBe('tok-42');
  });

  // ---- D-198: department catalogue search (shortName + aliases) ----
  // The default mock above has no shortName/aliases, so these tests
  // override fetch with catalogue rows that carry them.
  function mockCatalogs(depts: unknown, catalog: unknown) {
    fetchMock.mockImplementation(async (input: unknown, init?: { method?: string; body?: unknown }) => {
      const url = String(input); const method = init?.method || 'GET';
      calls.push({ url, method, body: init?.body });
      if (url === '/api/csrf') return json({});
      if (url === '/api/auth/me') return json({ message: 'unauthenticated' }, 401);
      if (url === '/api/public/universities') return json([{ id: 1, name: 'Metropolitan University', shortName: 'MU' }, { id: 2, name: 'University of Dhaka', shortName: 'DU' }]);
      if (url.startsWith('/api/public/departments')) return json(depts);
      if (url === '/api/public/department-catalog') return json(catalog);
      if (url === '/api/auth/register' && method === 'POST') return json({ id: 42, verificationToken: 'tok-42' }, 201);
      return json({ message: 'no mock route for ' + url }, 404);
    });
  }
  async function toStep2(user: ReturnType<typeof userEvent.setup>) {
    await user.type(screen.getByLabelText('Full name'), 'Test User');
    await user.type(screen.getByLabelText('Phone'), '01712345678');
    await user.type(screen.getByLabelText('Personal email'), 'test@example.com');
    await user.click(screen.getByRole('button', { name: /Continue/ }));
    await user.click(screen.getByLabelText('University'));
    await user.click(await screen.findByRole('option', { name: 'Metropolitan University' }));
  }
  const MU_DEPTS = [
    { id: 10, universityId: 1, catalogId: 1, name: 'Computer Science and Engineering', shortName: 'CSE', aliases: ['Computer Science'] },
    { id: 11, universityId: 1, catalogId: 2, name: 'Electrical and Electronic Engineering', shortName: 'EEE', aliases: ['Electrical Engineering'] },
  ];
  const CATALOG = [
    { id: 1, name: 'Software Engineering', shortName: 'SWE', aliases: ['Software Engg'] },
    { id: 2, name: 'Civil Engineering', shortName: 'Civil', aliases: [] },
  ];

  it('typing "MU" finds Metropolitan University', async () => {
    mockCatalogs(MU_DEPTS, CATALOG);
    const user = userEvent.setup(); renderPage();
    await user.type(screen.getByLabelText('Full name'), 'Test User');
    await user.type(screen.getByLabelText('Phone'), '01712345678');
    await user.type(screen.getByLabelText('Personal email'), 'test@example.com');
    await user.click(screen.getByRole('button', { name: /Continue/ }));
    await user.click(screen.getByLabelText('University'));
    await screen.findByRole('option', { name: 'Metropolitan University' });
    await user.type(screen.getByLabelText('University'), 'MU');
    expect(screen.getByRole('option', { name: 'Metropolitan University' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'University of Dhaka' })).toBeNull();
  });

  it('typing "CSE" finds "Computer Science and Engineering"', async () => {
    mockCatalogs(MU_DEPTS, CATALOG);
    const user = userEvent.setup(); renderPage();
    await toStep2(user);
    await user.click(screen.getByLabelText('Department'));
    await screen.findByRole('option', { name: 'Computer Science and Engineering' });
    await user.type(screen.getByLabelText('Department'), 'CSE');
    expect(screen.getByRole('option', { name: 'Computer Science and Engineering' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'Electrical and Electronic Engineering' })).toBeNull();
  });

  it('typing an alias finds the department', async () => {
    mockCatalogs(MU_DEPTS, CATALOG);
    const user = userEvent.setup(); renderPage();
    await toStep2(user);
    await user.click(screen.getByLabelText('Department'));
    await screen.findByRole('option', { name: 'Electrical and Electronic Engineering' });
    await user.type(screen.getByLabelText('Department'), 'Electrical Engineering');
    expect(screen.getByRole('option', { name: 'Electrical and Electronic Engineering' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'Computer Science and Engineering' })).toBeNull();
  });

  it('an empty per-university list still offers the typed fallback', async () => {
    mockCatalogs([], CATALOG);
    const user = userEvent.setup(); renderPage();
    await toStep2(user);
    await screen.findByText('No verified departments are listed for this university yet — you can type your department instead.');
    await user.click(screen.getByRole('button', { name: /type it instead/ }));
    await user.type(screen.getByLabelText('Department'), 'Robotics and Mechatronics');
    await user.type(screen.getByLabelText('Batch / Session'), 'Fall 2024');
    await user.type(screen.getByLabelText('Your address'), '123 Test Road, Dhaka');
    await user.click(screen.getByRole('button', { name: /Continue/ }));
    await screen.findByLabelText('Student ID number');
  });

  it('"search all departments" types the picked catalog name as the label', async () => {
    mockCatalogs([], CATALOG);
    const user = userEvent.setup(); renderPage();
    await toStep2(user);
    await user.click(screen.getByRole('button', { name: 'Search all departments' }));
    await user.click(screen.getByPlaceholderText('Search all departments'));
    await user.type(screen.getByPlaceholderText('Search all departments'), 'SWE');
    await user.click(await screen.findByRole('option', { name: 'Software Engineering' }));
    expect(screen.getByLabelText('Department')).toHaveValue('Software Engineering');
  });
});
