/**
 * The profile's `links` field is a single free-text string on the
 * backend (UpdateProfile.links). The Settings → Edit Profile tab
 * edits it as three labelled URLs; they are stored as labelled lines
 * ("GitHub: https://…") and parsed back here for both the editor and
 * the profile page's Links card. Unlabelled legacy text is preserved
 * as `other` lines and still shown.
 */
export interface ParsedLinks { github: string; portfolio: string; linkedin: string; other: string[] }

export function parseLinks(raw?: string | null): ParsedLinks {
  const out: ParsedLinks = { github: '', portfolio: '', linkedin: '', other: [] };
  for (const line of (raw || '').split(/[\n,]+/).map((s) => s.trim()).filter(Boolean)) {
    const m = line.match(/^(github|portfolio|linkedin)\s*:\s*(.+)$/i);
    if (m) {
      const key = m[1].toLowerCase() as 'github' | 'portfolio' | 'linkedin';
      out[key] = m[2].trim();
    } else if (/^https?:\/\/github\.com/i.test(line) && !out.github) out.github = line;
    else if (/^https?:\/\/([^/]+\.)?linkedin\.com/i.test(line) && !out.linkedin) out.linkedin = line;
    else out.other.push(line);
  }
  return out;
}

export function composeLinks(l: { github: string; portfolio: string; linkedin: string; other?: string[] }): string {
  // D-202: unlabelled legacy lines round-trip verbatim — the old version
  // emitted only the three labelled lines, silently dropping them on
  // every save. Lines that would parse back as a labelled URL are kept
  // out of `other` by parseLinks, so this cannot duplicate a field.
  return [
    l.github.trim() && `GitHub: ${l.github.trim()}`,
    l.portfolio.trim() && `Portfolio: ${l.portfolio.trim()}`,
    l.linkedin.trim() && `LinkedIn: ${l.linkedin.trim()}`,
    ...(l.other || []).map((o) => o.trim()).filter(Boolean),
  ].filter(Boolean).join('\n');
}
