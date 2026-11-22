import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '../app/auth';
import { ThemeProvider } from '../app/ThemeContext';
import VerificationExperience from '../features/auth/verification/VerificationExperience';
import { createMockVerificationAdapter } from '../features/auth/verification/verificationAdapter';

/** Phase 2.3 (corrected to the owner's exact specification, 2026-11-20):
 *  progress screen with a 30-second countdown → 6-box email code screen →
 *  success screen, on the isolated prototype adapter. These tests pin the
 *  exact supplied content and the box behaviour (auto-advance, paste,
 *  error treatment). */

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
const fetchMock = vi.fn();
function qc() { return new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } }); }
function renderPage(adapter = createMockVerificationAdapter({ readyDelayMs: 0, delayMs: 0 }), progressSeconds = 30) {
  return render(<QueryClientProvider client={qc()}><ThemeProvider><AuthProvider><MemoryRouter initialEntries={['/verification-preview']}><Routes><Route path="/verification-preview" element={<VerificationExperience adapter={adapter} progressSeconds={progressSeconds} />} /></Routes></MemoryRouter></AuthProvider></ThemeProvider></QueryClientProvider>);
}

beforeEach(() => {
  sessionStorage.clear();
  fetchMock.mockImplementation(async (input: unknown) => {
    const url = String(input);
    if (url === '/api/csrf') return json({});
    if (url === '/api/auth/me') return json({ message: 'unauthenticated' }, 401);
    return json({ message: 'no mock route for ' + url }, 404);
  });
  vi.stubGlobal('fetch', fetchMock);
});
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); });

const boxes = () => screen.getAllByRole('textbox');

describe('VerificationExperience (Phase 2.3, owner specification)', () => {
  it('progress screen shows the exact heading, subtext and 00:30 timer', async () => {
    renderPage(createMockVerificationAdapter({ readyDelayMs: 60_000, delayMs: 0 }));
    expect(await screen.findByText('Verification in progress...')).toBeInTheDocument();
    expect(screen.getByText('Please wait while we verify your student credentials. Do not close or refresh this window.')).toBeInTheDocument();
    expect(screen.getByText('Time remaining: 00:30')).toBeInTheDocument();
  });

  it('replaces the timer with the longer-wait message at 00:00, never negative', async () => {
    renderPage(createMockVerificationAdapter({ readyDelayMs: 60_000, delayMs: 0 }), 2);
    expect(await screen.findByText('Time remaining: 00:02')).toBeInTheDocument();
    expect(await screen.findByText('Taking a bit longer than usual... Almost done!', {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.queryByText(/Time remaining: -/)).toBeNull();
  });

  it('code screen: masked email, six boxes, Verify disabled until complete, resend counting down', async () => {
    renderPage();
    expect(await screen.findByText('Enter Verification Code')).toBeInTheDocument();
    expect(screen.getByText(/We’ve sent a 6-digit verification code to your personal email \(a•••@gmail\.com\)\./)).toBeInTheDocument();
    expect(boxes()).toHaveLength(6);
    expect(screen.getByRole('button', { name: 'Verify Account' })).toBeDisabled();
    expect(screen.getByRole('button', { name: /Resend Code \(00:\d{2}\)/ })).toBeDisabled();
  });

  it('auto-advances while typing; a wrong code gives the exact error, clears the boxes and refocuses the first', async () => {
    const user = userEvent.setup(); renderPage();
    await screen.findByText('Enter Verification Code');
    const bs = boxes();
    for (let i = 0; i < 6; i++) await user.type(bs[i], '0');
    expect(screen.getByRole('button', { name: 'Verify Account' })).toBeEnabled();
    await user.click(screen.getByRole('button', { name: 'Verify Account' }));
    expect(await screen.findByText('Invalid verification code. Please double-check and try again.')).toBeInTheDocument();
    for (const b of boxes()) expect((b as HTMLInputElement).value).toBe('');
    await waitFor(() => expect(document.activeElement).toBe(boxes()[0]));
  });

  it('accepts a pasted 6-digit code and reaches the success screen', async () => {
    const user = userEvent.setup(); renderPage();
    await screen.findByText('Enter Verification Code');
    fireEvent.paste(boxes()[0], { clipboardData: { getData: () => '123456' } });
    expect(boxes().map((b) => (b as HTMLInputElement).value).join('')).toBe('123456');
    await user.click(screen.getByRole('button', { name: 'Verify Account' }));
    expect(await screen.findByText('Verification Successful!')).toBeInTheDocument();
    expect(screen.getByText('Your student account has been verified. Redirecting to your dashboard...')).toBeInTheDocument();
  });
});
