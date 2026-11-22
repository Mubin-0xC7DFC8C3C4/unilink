const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/** "2026-12-05T09:00:00" → "Dec 5, 2026 · 9:00 AM". Returns undefined
 *  for missing or unparseable values so callers can omit the segment. */
export function formatEventDateTime(iso?: string | null): string | undefined {
  if (!iso) return undefined;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return undefined;
  let h = d.getHours();
  const ampm = h >= 12 ? 'PM' : 'AM';
  h = h % 12 || 12;
  const m = String(d.getMinutes()).padStart(2, '0');
  return `${MONTHS[d.getMonth()]} ${d.getDate()}, ${d.getFullYear()} · ${h}:${m} ${ampm}`;
}

/** The user-facing badge label for an event/post visibility value. */
export function eventVisibilityLabel(visibility?: string | null): string {
  switch (visibility) {
    case 'UNIVERSITY': return 'University';
    case 'NATIONAL': return 'National';
    case 'DEPARTMENT_HUB': return 'Department Hub';
    default: return 'Department';
  }
}
