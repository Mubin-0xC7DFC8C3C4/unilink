import { useRef } from 'react';

/** Six individual digit boxes (reference screenshot 1): automatic focus
 *  movement, backspace navigation, arrow-key movement and paste support
 *  for a complete 6-digit code. Controlled: `value` is the joined code. */
export function OtpBoxes({ value, onChange, invalid, disabled, autoFocus }: {
  value: string;
  onChange: (code: string) => void;
  invalid?: boolean;
  disabled?: boolean;
  autoFocus?: boolean;
}) {
  const refs = useRef<Array<HTMLInputElement | null>>([]);
  const digits = Array.from({ length: 6 }, (_, i) => value[i] ?? '');
  const setDigit = (index: number, digit: string) => {
    const next = digits.slice();
    next[index] = digit;
    onChange(next.join('').slice(0, 6));
  };
  const focus = (index: number) => { refs.current[Math.max(0, Math.min(5, index))]?.focus(); };
  return <div className={`otp-boxes${invalid ? ' invalid' : ''}`} role="group" aria-label="6-digit verification code">
    {digits.map((d, i) => (
      <input key={i} ref={(el) => { refs.current[i] = el; }} type="text" inputMode="numeric" autoComplete="one-time-code"
        maxLength={1} value={d} disabled={disabled} aria-label={`Digit ${i + 1} of 6`}
        autoFocus={autoFocus && i === 0}
        onChange={(e) => {
          const v = e.target.value.replace(/\D/g, '');
          if (v === '') { setDigit(i, ''); return; }
          setDigit(i, v.slice(-1));
          if (i < 5) focus(i + 1);
        }}
        onKeyDown={(e) => {
          if (e.key === 'Backspace' && digits[i] === '' && i > 0) { e.preventDefault(); setDigit(i - 1, ''); focus(i - 1); }
          if (e.key === 'ArrowLeft') { e.preventDefault(); focus(i - 1); }
          if (e.key === 'ArrowRight') { e.preventDefault(); focus(i + 1); }
        }}
        onPaste={(e) => {
          e.preventDefault();
          const pasted = (e.clipboardData.getData('text') || '').replace(/\D/g, '').slice(0, 6);
          if (pasted === '') return;
          onChange(pasted);
          focus(Math.min(pasted.length, 5));
        }} />
    ))}
  </div>;
}
