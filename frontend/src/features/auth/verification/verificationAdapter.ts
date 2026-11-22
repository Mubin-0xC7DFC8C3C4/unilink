/** DEVELOPMENT-ONLY prototype adapter — Phase 2.3 (owner decision A2,
 *  2026-11-20; UI corrected to the owner's exact specification the same
 *  day).
 *
 *  The journey these screens show (verification progress → email OTP →
 *  success) is NOT supported by the existing backend: the real flow is
 *  phone OTP (/auth/verify-phone) plus a parallel email LINK, then a real
 *  pending-review status (/auth/verification/status). There is no
 *  email-OTP endpoint and no verification-progress response.
 *
 *  This adapter isolates everything the prototype needs behind one
 *  interface so a future real adapter can replace the mock without
 *  touching the screens. The mock NEVER calls the API: no email is sent
 *  and no code is really verified. The screens are not linked from the
 *  live sign-up flow. */

export interface SubmittedRegistration {
  fullName: string;
  studentId: string;
  university: string;
  department: string;
  email: string;
}

export type VerificationErrorCode = 'INVALID_OTP' | 'OTP_EXPIRED';

export class VerificationError extends Error {
  code: VerificationErrorCode;
  constructor(code: VerificationErrorCode) { super(code); this.code = code; }
}

export interface VerificationAdapter {
  /** The submission the journey refers to (sample data in the mock). */
  getSubmittedRegistration(): SubmittedRegistration;
  /** Resolves when the verification step is ready (mock: a short wait
   *  standing in for the future server response). */
  whenVerificationReady(): Promise<void>;
  /** "Send" the email OTP (mock: issues the demo code locally). */
  sendEmailOtp(): Promise<void>;
  /** Check a code; rejects with VerificationError on invalid/expired. */
  verifyEmailOtp(code: string): Promise<void>;
}

/** The demo code for the prototype. It is deliberately NOT shown on the
 *  screens (the corrected design carries no extra text); it is disclosed
 *  in the delivery report so the owner can exercise the success state. */
export const DEMO_OTP_CODE = '123456';

const SAMPLE_REGISTRATION: SubmittedRegistration = {
  fullName: 'Ayesha Rahman',
  studentId: '241-115-129',
  university: 'Metropolitan University',
  department: 'Computer Science and Engineering',
  email: 'ayesha@gmail.com',
};

export function createMockVerificationAdapter(options?: { readyDelayMs?: number; delayMs?: number; otpValidityMs?: number }): VerificationAdapter {
  const readyDelayMs = options?.readyDelayMs ?? 5_000;
  const delayMs = options?.delayMs ?? 600;
  const otpValidityMs = options?.otpValidityMs ?? 600_000;
  let sentAt: number | null = null;
  const wait = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));
  return {
    getSubmittedRegistration: () => SAMPLE_REGISTRATION,
    whenVerificationReady: async () => { await wait(readyDelayMs); },
    sendEmailOtp: async () => { await wait(delayMs); sentAt = Date.now(); },
    verifyEmailOtp: async (code: string) => {
      await wait(delayMs);
      if (sentAt === null || Date.now() > sentAt + otpValidityMs) throw new VerificationError('OTP_EXPIRED');
      if (code !== DEMO_OTP_CODE) throw new VerificationError('INVALID_OTP');
    },
  };
}

/** The single shared instance the screens use. Swapping this for a real
 *  adapter is the only integration change a future backend phase needs. */
export const mockVerificationAdapter = createMockVerificationAdapter();
