import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Landing from '../features/auth/Landing';

/** Landing copy (owner instruction 2026-11-24, round 3): minimal hero +
 *  3-card grid only. The verification-steps section and the Pending
 *  Review helper text were deleted, not rewritten. */
describe('Landing page copy (2026-11-24)', () => {
  it('shows the new hero and feature grid, and nothing below it', () => {
    render(<MemoryRouter><Landing /></MemoryRouter>);
    expect(screen.getByText('FOR UNIVERSITY STUDENTS IN BANGLADESH')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Where university students connect, collaborate, and build together.' })).toBeInTheDocument();
    expect(screen.getByText('Join a trusted academic network exclusive to verified students. Discuss coursework, discover campus events, and find teammates for your next project or hackathon.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Get started →' })).toHaveAttribute('href', '/signup');
    expect(screen.getByRole('link', { name: 'Log in' })).toHaveAttribute('href', '/login');
    for (const label of ['CAMPUS COMMUNITIES', 'TEAM & PROJECT DISCOVERY', 'AUTHENTIC NETWORK']) {
      expect(screen.getByText(label)).toBeInTheDocument();
    }
    expect(screen.getByText('Your Department & Beyond')).toBeInTheDocument();
    expect(screen.getByText('Build With Real Peers')).toBeInTheDocument();
    expect(screen.getByText('Verified Students Only')).toBeInTheDocument();
    expect(screen.getByText('Engage in focused discussions across your department, university, and nationwide student hubs.')).toBeInTheDocument();
    expect(screen.getByText('Find skilled teammates for hackathons, research, and software projects across campuses.')).toBeInTheDocument();
    expect(screen.getByText('Every profile is backed by university ID verification—zero spam, zero outsiders.')).toBeInTheDocument();
    expect(screen.queryByText('How it works')).toBeNull();
    expect(screen.queryByText('How verification works')).toBeNull();
    expect(screen.queryByText(/Pending Review/)).toBeNull();
    expect(screen.queryByText(/Create your account/)).toBeNull();
    expect(document.body.textContent).not.toMatch(/FR-[A-Z]+-\d+/);
  });
});
