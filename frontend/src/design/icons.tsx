/**
 * Inline line icons for the app shell and Community Home (Phase 3
 * reference design). The project has no icon library; these are small
 * hand-drawn stroke icons in the warm monochrome system — they inherit
 * currentColor and carry no colour of their own.
 */
interface IconProps { size?: number }
function base(size: number) {
  return {
    width: size, height: size, viewBox: '0 0 24 24', fill: 'none',
    stroke: 'currentColor', strokeWidth: 1.8, strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const, 'aria-hidden': true, focusable: false,
  };
}
export function IconMenu({ size = 22 }: IconProps) {
  return <svg {...base(size)}><path d="M4 7h16M4 12h16M4 17h16" /></svg>;
}
export function IconGrid({ size = 20 }: IconProps) {
  return <svg {...base(size)}><rect x="4" y="4" width="7" height="7" rx="1.5" /><rect x="13" y="4" width="7" height="7" rx="1.5" /><rect x="4" y="13" width="7" height="7" rx="1.5" /><rect x="13" y="13" width="7" height="7" rx="1.5" /></svg>;
}
export function IconBell({ size = 20 }: IconProps) {
  return <svg {...base(size)}><path d="M6 9.5a6 6 0 0 1 12 0c0 4 1.5 5.5 1.5 5.5h-15S6 13.5 6 9.5" /><path d="M10 19a2.2 2.2 0 0 0 4 0" /></svg>;
}
export function IconGear({ size = 20 }: IconProps) {
  // A solid cog silhouette (filled 8-tooth gear outline with a punched
  // hub hole) — the standard settings glyph. Earlier stroke versions
  // (rayed circle, then a thin ring with teeth) kept reading as a
  // sun/theme icon at header size; a filled silhouette does not.
  return <svg {...base(size)} fill="currentColor" stroke="none"><path fillRule="evenodd" d="M8.87 4.97 L9.66 1.87 L14.34 1.87 L15.13 4.97 L14.76 4.81 L17.51 3.18 L20.82 6.49 L19.19 9.24 L19.03 8.87 L22.13 9.66 L22.13 14.34 L19.03 15.13 L19.19 14.76 L20.82 17.51 L17.51 20.82 L14.76 19.19 L15.13 19.03 L14.34 22.13 L9.66 22.13 L8.87 19.03 L9.24 19.19 L6.49 20.82 L3.18 17.51 L4.81 14.76 L4.97 15.13 L1.87 14.34 L1.87 9.66 L4.97 8.87 L4.81 9.24 L3.18 6.49 L6.49 3.18 L9.24 4.81 Z M8.9 12.0 a3.1 3.1 0 1 0 6.2 0 a3.1 3.1 0 1 0 -6.2 0 Z" /></svg>;
}
export function IconSearch({ size = 16 }: IconProps) {
  return <svg {...base(size)}><circle cx="11" cy="11" r="6.5" /><path d="M16 16l4.5 4.5" /></svg>;
}
export function IconHome({ size = 19 }: IconProps) {
  return <svg {...base(size)}><path d="M4 11l8-7 8 7" /><path d="M6.5 9.5V20h11V9.5" /></svg>;
}
export function IconUsers({ size = 19 }: IconProps) {
  return <svg {...base(size)}><circle cx="9" cy="8.5" r="3.5" /><path d="M3.5 20c.6-3.2 2.8-5 5.5-5s4.9 1.8 5.5 5" /><path d="M16 5.6a3.5 3.5 0 0 1 0 5.8M18.5 15.4c1.1.8 1.8 2 2.1 3.6" /></svg>;
}
export function IconCompass({ size = 19 }: IconProps) {
  return <svg {...base(size)}><circle cx="12" cy="12" r="8.5" /><path d="M15.5 8.5l-2 5-5 2 2-5z" /></svg>;
}
export function IconCalendar({ size = 19 }: IconProps) {
  return <svg {...base(size)}><rect x="4" y="5.5" width="16" height="15" rx="2" /><path d="M4 10h16M8.5 3.5v4M15.5 3.5v4" /></svg>;
}
export function IconBookmark({ size = 19 }: IconProps) {
  return <svg {...base(size)}><path d="M7 4h10v16l-5-4-5-4z" /></svg>;
}
export function IconLike({ size = 17 }: IconProps) {
  return <svg {...base(size)}><path d="M7 11v9H4.5v-9H7zM7 11l4.2-7.2c1.6 0 2.6 1.1 2.4 2.7L13 10h6.2c.9 0 1.5.8 1.3 1.7l-.8 5.4c-.2 1-1 1.9-2.1 1.9H7" /></svg>;
}
export function IconComment({ size = 17 }: IconProps) {
  return <svg {...base(size)}><path d="M4 5.5A2.5 2.5 0 0 1 6.5 3h11A2.5 2.5 0 0 1 20 5.5v8a2.5 2.5 0 0 1-2.5 2.5H12l-4.5 4v-4H6.5A2.5 2.5 0 0 1 4 13.5z" /></svg>;
}
export function IconFlag({ size = 17 }: IconProps) {
  return <svg {...base(size)}><path d="M5.5 21V4" /><path d="M5.5 4.5c4-2.2 6.5 2 12 .2v8.8c-5.5 1.8-8-2.4-12-.2" /></svg>;
}
export function IconDots({ size = 18 }: IconProps) {
  return <svg {...base(size)}><circle cx="5" cy="12" r="1.4" fill="currentColor" stroke="none" /><circle cx="12" cy="12" r="1.4" fill="currentColor" stroke="none" /><circle cx="19" cy="12" r="1.4" fill="currentColor" stroke="none" /></svg>;
}
export function IconX({ size = 16 }: IconProps) {
  return <svg {...base(size)}><path d="M5 5l14 14M19 5L5 19" /></svg>;
}
export function IconImage({ size = 18 }: IconProps) {
  return <svg {...base(size)}><rect x="3.5" y="5" width="17" height="14" rx="2" /><circle cx="9" cy="10" r="1.6" /><path d="M4 17.5l5-4.5 3.5 3 3-2.5 4.5 4" /></svg>;
}
export function IconFile({ size = 18 }: IconProps) {
  return <svg {...base(size)}><path d="M6 3.5h8L19 8v12.5H6z" /><path d="M13.5 3.5V8H19M9 13l-1.8 2.5L9 18M15 13l1.8 2.5L15 18" /></svg>;
}
export function IconTag({ size = 18 }: IconProps) {
  return <svg {...base(size)}><path d="M3.5 12V4.5H12l8.5 8.5-7.5 7.5z" /><circle cx="8.5" cy="9.5" r="1.5" /></svg>;
}
export function IconShare({ size = 17 }: IconProps) {
  return <svg {...base(size)}><path d="M13.5 5.5L20 12l-6.5 6.5v-4C7 14.5 4.5 16.5 3.5 19c0-6 3-10 10-11z" /></svg>;
}
export function IconFolder({ size = 18 }: IconProps) {
  return <svg {...base(size)}><path d="M3.5 6.5A1.5 1.5 0 0 1 5 5h4.5l2 2.5h7A1.5 1.5 0 0 1 20 9v8.5a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 17.5z" /></svg>;
}

export function IconGitHub({ size = 20 }: IconProps) {
  // The GitHub Octocat mark (simple-icons path), filled in currentColor.
  return <svg {...base(size)} fill="currentColor" stroke="none"><path d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12" /></svg>;
}
