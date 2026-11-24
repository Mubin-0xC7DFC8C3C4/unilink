import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Landing from '../features/auth/Landing';

/** Landing copy (owner instruction 2026-11-24, round 2): clean, modern
 *  product text. No internal requirement codes, no OTP timing details,
 *  and no technical form-field lists anywhere on the page. */
describe('Landing page copy (2026-11-24)', () => {
  it('shows the new hero, feature grid, How it works and helper text', () => {
    render(<MemoryRouter><Landing /></MemoryRouter>);
    expect(screen.getByText('VERIFIED STUDENT NETWORK · BANGLADESH')).toBeInTheDocument();
    expect(screen.getByText('Connect with verified university students across Bangladesh. Collaborate on projects, join campus communities, and build your academic network in a trusted, student-only space.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Get started →' })).toHaveAttribute('href', '/signup');
    expect(screen.getByRole('link', { name: 'Log in' })).toHaveAttribute('href', '/login');
    for (const label of ['COMMUNITIES', 'COLLABORATION', 'TRUST & SAFETY']) {
      expect(screen.getByText(label)).toBeInTheDocument();
    }
    expect(screen.getByText('4 Tiers')).toBeInTheDocument();
    expect(screen.getByText('Campus-Wide')).toBeInTheDocument();
    expect(screen.getByText('100% Verified')).toBeInTheDocument();
    expect(screen.getByText('Department · University · Hub · National')).toBeInTheDocument();
    expect(screen.getByText('Discussions, Events, Teams & Projects')).toBeInTheDocument();
    expect(screen.getByText('Authenticated via University ID & OTP')).toBeInTheDocument();
    expect(screen.getByText('How it works')).toBeInTheDocument();
    expect(screen.getByText('1. Create your account')).toBeInTheDocument();
    expect(screen.getByText('2. Verify your identity')).toBeInTheDocument();
    expect(screen.getByText('3. Join your campus')).toBeInTheDocument();
    expect(screen.getByText('Need to update your ID submission? You can re-upload your documents anytime from the Pending Review screen.')).toBeInTheDocument();
    expect(screen.queryByText(/FR-SEC/)).toBeNull();
    expect(document.body.textContent).not.toMatch(/FR-[A-Z]+-\d+/);
    expect(document.body.textContent).not.toMatch(/5 minutes, max 5 attempts/);
  });
});
