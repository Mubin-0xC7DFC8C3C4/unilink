import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Button, EmptyState, StatStrip, VerifiedBadge, Modal } from '../design/primitives';
import { signUpSchema } from '../features/auth/SignUp';
import { toggleTheme, initialTheme } from '../design/theme';
import { ApiError } from '../api/types';
import { errorMessage } from '../api/client';

describe('primitives', () => {
  it('renders a primary button and handles click', async () => {
    const user = userEvent.setup(); let n=0;
    render(<Button variant="primary" onClick={()=>n++}>Create account</Button>);
    await user.click(screen.getByRole('button',{name:'Create account'})); expect(n).toBe(1);
  });
  it('renders verified badge with word Verified', () => { render(<VerifiedBadge/>); expect(screen.getByLabelText('Verified')).toHaveTextContent('Verified'); });
  it('renders stat strip only with given numbers', () => { render(<StatStrip stats={[{label:'Users',value:'42',note:'real'}]}/>); expect(screen.getByText('42')).toBeInTheDocument(); expect(screen.getByText('Users')).toBeInTheDocument(); });
  it('renders empty state', () => { render(<EmptyState title="No posts yet" body="Nothing here."/>); expect(screen.getByText('No posts yet')).toBeInTheDocument(); });
  it('modal has dialog role', () => { render(<Modal title="Delete account" onClose={()=>{}}>Body</Modal>); expect(screen.getByRole('dialog')).toBeInTheDocument(); });
});
describe('sign-up validation', () => {
  it('rejects weak passwords', () => {
    const r = signUpSchema.safeParse({fullName:'A B',phone:'01700000000',email:'a@b.edu',university:'DU',department:'CSE',batch:'45',address:'Dhaka',studentId:'1',password:'weak'});
    expect(r.success).toBe(false);
  });
  it('accepts a strong password', () => {
    const r = signUpSchema.safeParse({fullName:'A B',phone:'01700000000',email:'a@b.edu',university:'DU',department:'CSE',batch:'45',address:'Dhaka',studentId:'1',password:'Strong1pass'});
    expect(r.success).toBe(true);
  });
  it('rejects bad email', () => {
    const r = signUpSchema.safeParse({fullName:'A B',phone:'01700000000',email:'not-email',university:'DU',department:'CSE',batch:'45',address:'Dhaka',studentId:'1',password:'Strong1pass'});
    expect(r.success).toBe(false);
  });
});
describe('theme', () => {
  it('toggles light to dark and back', () => { expect(toggleTheme('light')).toBe('dark'); expect(toggleTheme('dark')).toBe('light'); });
  it('initial theme is light or dark', () => { expect(['light','dark']).toContain(initialTheme()); });
});
describe('error mapping', () => {
  it('maps 401/403/404/429', () => {
    expect(errorMessage(new ApiError(401,{message:'x'}))).toMatch(/not signed in/i);
    expect(errorMessage(new ApiError(403,{message:'x'}))).toMatch(/permission/i);
    expect(errorMessage(new ApiError(404,{message:'x'}))).toMatch(/not found/i);
    expect(errorMessage(new ApiError(429,{message:'x'}))).toMatch(/Too many requests/i);
  });
});
