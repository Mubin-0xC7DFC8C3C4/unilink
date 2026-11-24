import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Landing from '../features/auth/Landing';

/** Landing copy (owner instruction 2026-11-24): clean, user-friendly
 *  product text. No internal requirement codes and no technical
 *  form-field lists anywhere on the page. */
describe('Landing page copy (2026-11-24)', () => {
  it('shows the new subtitle, stat cards, verification steps and footer note', () => {
    render(<MemoryRouter><Landing /></MemoryRouter>);
    expect(screen.getByText('A verified academic network for university students in Bangladesh. Join department discussions, discover campus events, and form project teams in a trusted, student-only space.')).toBeInTheDocument();
    for (const label of ['COMMUNITY TIERS', 'FOCUS SECTORS', 'VERIFIED STUDENTS']) {
      expect(screen.getByText(label)).toBeInTheDocument();
    }
    expect(screen.getByText('4')).toBeInTheDocument();
    expect(screen.getByText('6')).toBeInTheDocument();
    expect(screen.getByText('100%')).toBeInTheDocument();
    expect(screen.getByText('Department · University · Hub · National')).toBeInTheDocument();
    expect(screen.getByText('Tech, Research, Clubs, Events & more')).toBeInTheDocument();
    expect(screen.getByText('University ID & OTP authenticated')).toBeInTheDocument();
    expect(screen.getByText('How verification works')).toBeInTheDocument();
    expect(screen.getByText('1. Create your account')).toBeInTheDocument();
    expect(screen.getByText('2. Verify your identity')).toBeInTheDocument();
    expect(screen.getByText('3. Get approved')).toBeInTheDocument();
    expect(screen.getByText('Need to update your submission? You can easily re-upload your ID details from the Pending Review screen.')).toBeInTheDocument();
    expect(screen.queryByText(/FR-SEC/)).toBeNull();
    expect(document.body.textContent).not.toMatch(/FR-[A-Z]+-\d+/);
  });
});
