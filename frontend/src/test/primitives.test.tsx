import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useRef, useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { Button, EmptyState, StatStrip, VerifiedBadge, Modal, Input } from '../design/primitives';
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
describe('Input forwards refs to react-hook-form', () => {
  function RegisteredForm({ onSubmit }: { onSubmit: (v: { email: string }) => void }) {
    const { register, handleSubmit } = useForm<{ email: string }>();
    return <form onSubmit={handleSubmit(onSubmit)}><Input aria-label="Email" {...register('email')} /><button type="submit">Submit</button></form>;
  }
  it('a registered Input submits its typed value', async () => {
    const user = userEvent.setup();
    let submitted: { email: string } | null = null;
    render(<RegisteredForm onSubmit={(v) => { submitted = v; }} />);
    await user.type(screen.getByLabelText('Email'), 'a@b.edu');
    await user.click(screen.getByRole('button', { name: 'Submit' }));
    expect(submitted).toEqual({ email: 'a@b.edu' });
  });
  it('a callback ref reaches the underlying input element', () => {
    let node: HTMLInputElement | null = null;
    render(<Input aria-label="Ref target" ref={(el) => { node = el; }} />);
    expect(node).toBeInstanceOf(HTMLInputElement);
    expect(node!.getAttribute('aria-label')).toBe('Ref target');
  });
  it('forwards a RefObject to the input', () => {
    function Probe() {
      const ref = useRef<HTMLInputElement>(null);
      const [attached, setAttached] = useState(false);
      useEffect(() => { setAttached(ref.current instanceof HTMLInputElement); }, []);
      return <><Input aria-label="Ref object" ref={ref} /><span>{attached ? 'attached' : 'detached'}</span></>;
    }
    render(<Probe />);
    expect(screen.getByText('attached')).toBeInTheDocument();
  });
});
describe('sign-up validation', () => {
  // D-193/D-194: the schema now takes a catalog department id OR a typed
  // department label, and a free-text batch string — the old numeric
  // batchId shape is gone.
  const valid = {fullName:'A B',phone:'01700000000',email:'a@b.edu',universityId:1,departmentId:10,batch:'44',address:'Dhaka',studentId:'1',password:'Strong1pass',confirmPassword:'Strong1pass'};
  it('rejects weak passwords', () => {
    const r = signUpSchema.safeParse({...valid,password:'weak'});
    expect(r.success).toBe(false);
  });
  it('accepts a strong password', () => {
    const r = signUpSchema.safeParse(valid);
    expect(r.success).toBe(true);
  });
  it('accepts a typed department label instead of a catalog id', () => {
    const r = signUpSchema.safeParse({...valid,departmentId:undefined,departmentLabel:'Robotics and Mechatronics'});
    expect(r.success).toBe(true);
  });
  it('accepts the free-text batch formats', () => {
    for (const batch of ['44','45','2022-23','Fall 2024','Spring 2025','2023','59th Batch']){
      expect(signUpSchema.safeParse({...valid,batch}).success).toBe(true);
    }
  });
  it('rejects an invalid free-text batch', () => {
    expect(signUpSchema.safeParse({...valid,batch:'Fall 2024!'}).success).toBe(false);
    expect(signUpSchema.safeParse({...valid,batch:'a'.repeat(33)}).success).toBe(false);
  });
  it('rejects bad email', () => {
    const r = signUpSchema.safeParse({...valid,email:'not-email'});
    expect(r.success).toBe(false);
  });
  it('rejects a non-Bangladeshi phone number', () => {
    const r = signUpSchema.safeParse({...valid,phone:'01234567890'});
    expect(r.success).toBe(false);
  });
});
describe('theme', () => {
  it('toggles light to dark and back', () => { expect(toggleTheme('light')).toBe('dark'); expect(toggleTheme('dark')).toBe('light'); });
  it('initial theme is light or dark', () => { expect(['light','dark']).toContain(initialTheme()); });
});
describe('error mapping', () => {
  it('maps 401/403/404/429', () => {
    // 401 now says the session expired (error-handling contract): a 401 on
    // a guarded call redirects to login with that message; a 401 on the
    // login call itself is rendered as wrong credentials by the login page.
    expect(errorMessage(new ApiError(401,{message:'x'}))).toMatch(/session has expired/i);
    expect(errorMessage(new ApiError(403,{message:'x'}))).toMatch(/permission/i);
    expect(errorMessage(new ApiError(404,{message:'x'}))).toMatch(/not found/i);
    expect(errorMessage(new ApiError(429,{message:'x'}))).toMatch(/Too many requests/i);
  });
});
