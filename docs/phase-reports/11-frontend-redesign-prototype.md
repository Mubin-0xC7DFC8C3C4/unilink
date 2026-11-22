# Stage 3 — Visual Prototype (Frontend Redesign): Report

- **Date:** 2026-11-18 ~22:37 +06
- **Status:** COMPLETE, pending owner review and approval before Stage 4
- **Deliverable:** standalone artifact `unilink-visual-prototype` (4 screens, desktop/mobile + light/dark toggles)
- **Source of truth:** `docs/phase-reports/10-frontend-redesign-blueprint.md` (§§2–13)

## What was built

A standalone visual prototype outside the repo — no production frontend code, backend code,
database schema, API contract, or authentication logic was touched, and no package was installed.
It contains the four approved screens, each switchable between Desktop (≥1200px) and Mobile
(390px) and between Light and Dark, with a visible SAMPLE banner on every screen:

1. **Community Home** — 3-column layout (profile + nav + levels + sectors | composer + feed |
   events + suggested-students empty state + trending sectors), mode switcher, header search,
   bottom tab bar on mobile.
2. **Workspace Overview** — left sidebar nav, repo-style project cards, team activity, Tasks and
   Contribution overview as honest empty states, right shortcuts/discovery rail.
3. **Student Profile** — cover + overlapping avatar, Verified badge, About / Skills / Projects /
   Activity, Followers-Following lists as an honest empty state, right rail (Links, Education,
   Achievements, Similar students empty state).
4. **Admin Dashboard** — separate dark-rail entry, 14 rail items (Announcements retained),
   stat strip, verification queue sample rows, moderation + analytics cards.

All sample people, posts, projects and events are labelled `[SAMPLE]`; no alumni content exists
anywhere (SRS §1.2 wins, per the owner's 2026-11-18 ruling); every Stage 6 C-item (Share,
suggested students, followers/following lists, tasks, discussions, contribution graph/counts,
saved projects, global showcase feed, project visibility/status/tags/last-updated) appears only
as a disabled control or an honest empty state, with no invented numbers — the admin stat strip
shows "—" instead of figures.

## Design decisions implemented

- Tokens verbatim from blueprint §2–§4: navy `#172B45`, blue `#2878C8`, success `#23834C`,
  bg `#F3F5F8`, surface `#FFFFFF`, border `#E2E8F0`, dark `#161E2B / #202938 / #5B9BD5`;
  warning `#D99B32` as fill/accent only, warning text `#8A5A00`; secondary text on tinted
  surfaces uses the stronger `#54687E`.
- Typography: Inter + Noto Sans Bengali; page titles 24–28px/600, sections 17–20px, body
  14–16px, labels 12–13px. Spacing 4/8/12/16/24/32/48; radius 8/12/16; one subtle shadow tier.
- Breakpoints 768 / 1200; Community 1180px (264 | 1fr | 320), Workspace 1200px, Admin fluid
  1280px with a 240px rail.
- One student shell with a prominent `[Community | Workspace]` segmented switcher; Admin is a
  visually separate entry and is never merged into student nav.
- Community = three-column LinkedIn-principle layout; Workspace = GitHub-principle sidebar +
  repo cards + structured panels. No feed, composer, or Like row in Workspace.
- The full 36-route preservation map is shown in the prototype itself (8 public + 2
  mode-neutral + 10 flat→canonical aliases + 8 new canonical + 14 admin + catch-all +
  new `/admin/settings`).

## Verification performed by the main agent

Playwright against the exported artifact, 16 screenshots (4 screens × desktop/mobile ×
light/dark), all captured and read back:

- All 16 states render; **zero JavaScript errors** (the only console noise is the Google Fonts
  request failing in the sandbox — see note below).
- No horizontal overflow on 15 of 16 states (measured `scrollWidth == clientWidth`).
- Dark theme renders correctly on all four screens (dark surfaces, readable text, sample
  banners visible); dark swaps only colour variables, so geometry is theme-independent.
- Sample labelling, empty states, admin rail (14 items incl. Announcements), and the
  "—" stat strip all confirmed visually.

## Findings

### 1. Admin Dashboard mobile overflow — FOUND, FIXED IN THE PROTOTYPE, FIX CONFIRMED 2026-11-19
- **Found by my own capture:** at Mobile 390px the Admin screen measured 479px wide inside a
  374px frame — stat cards and the verification table's Action column were clipped and
  unreachable. Cause: the 5-column verification table's intrinsic width stretched the
  single-column admin grid.
- **Fix applied in the prototype only (2026-11-18 ~22:35 +06):** the table now sits in a
  `.tablewrap` horizontal-scroll container (`overflow-x:auto`, table `min-width:560px`) and
  `#s-admin main` / `.stat` carry `min-width:0`, with matching rules in the
  `@media(max-width:900px)` block so real phone viewports get the same behaviour.
- **Verification status: CONFIRMED 2026-11-19 ~15:19 +06.** Playwright at a true 390px viewport,
  light and dark: frame scrollWidth 374 = clientWidth (was 479 pre-fix), document scrollWidth
  390 = viewport width (no page-level horizontal scroll), all 4 stat cards inside the frame
  in a 2×2 grid, both Review buttons reachable by scrolling the table alone. Zero JS errors.
  Captures: `admin-mobile-fixed-light.png` and `admin-mobile-fixed-dark.png` in
  `~/workspace/unilink-prototype-shots/`. This supersedes the earlier "PROBABLE, not visually
  confirmed" verdict from inspection `5a2cba13` (2026-11-18 ~22:36 +06), whose captures never
  showed the Admin screen at 390px. The check ran against the live prototype file (v2,
  md5-matched); the hosted URL itself was not re-checked from a browser.
- **Table-scroll trade-off:** the verification table scrolls sideways inside its card, so
  swiping to reach the Review buttons slides the student-name column out of view while you
  do — containment, not page overflow.
- **Screenshots:** the two Admin mobile screenshots (`admin-mobile-light/dark`) were captured
  BEFORE the fix and still show the overflow; they are kept, labelled pre-fix. The post-fix
  captures above confirm the fix. The other 14 screenshots are of the current build and are
  unaffected (the fix touched mobile Admin CSS only).
- **Preserve while re-verifying:** keep `.tablewrap` and the `min-width:0` rules; keep the rail
  hidden and the statstrip at 2 columns on mobile; keep the honest "—" / SAMPLE states.

### 2. Icons are emoji/text glyphs, not Lucide SVGs (deviation to note)
The blueprint specifies Lucide React icons. The prototype uses emoji and text glyphs
(🔔 💬 ⌂ 👥 📅 🔖 etc.) because no package could be installed in Stage 3. Visually
acceptable for a prototype, but Stage 4 must replace them with real Lucide icons
(`lucide-react`, already verified on npm, not yet installed).

### 3. Inter webfont did not load in the sandbox (screenshot-only note)
The prototype loads Inter + Noto Sans Bengali from Google Fonts. In the sandbox, Chromium
could not reach Google Fonts (`ERR_TUNNEL_CONNECTION_FAILED`), so my screenshots render the
system-ui fallback stack. The hosted artifact will load the real fonts in the owner's browser.
Production will bundle the fonts via Fontsource (blueprint §3), not a CDN.

### 4. Minor nits (for Stage 4, not blockers)
- The notification bell shows a sample badge "3" — covered by the SAMPLE banner, but it is a
  sample value, not a real count.
- The prototype's route-map label reads "Admin (15 existing + catch-all = 16 entries)"; the
  audit counts 15 admin entries total *including* the catch-all, so the accurate wording is
  "14 named + catch-all = 15 existing, + new /admin/settings = 16". The listed routes
  themselves are correct.
- On the Profile desktop screen the Follow / Message / Edit buttons wrap into a vertical
  stack; readable, but Stage 4 should confirm the intended arrangement.

## Remaining design conflicts & blockers

- **Admin navigation defect (known, out of Stage 3 scope):** production admin double-prefixes
  routes (`/admin/admin/…`) due to basename + absolute links. The blueprint schedules the fix
  first in the implementation phase; the prototype only notes it.
- **Workspace depth is intentionally thin:** without Stage 6 backend items, project cards omit
  visibility/status/tags/updated-date, Showcase is follow/team-scoped only, and
  Tasks/Discussions/Contributions are empty states. Approving Stage 4 does not approve those
  backends.
- **Network page:** follow/unfollow exists, but Suggested and Followers/Following lists have no
  endpoints — the live page must ship with the honest states prototyped here until Stage 6.
- **Open blueprint §14 items (unchanged):** TOTP QR library (`qrcode.react` proposed) vs
  type-the-URL flow; Analytics charting (tables specified; any chart library is separate);
  announcement placement in the student UI; Interests field on profiles (Stage 6 or drop);
  alias retirement timing (confirm ≥1 release cycle). Plus the pre-existing D-122, D-120,
  D-088, D-067/D-068/D-069/D-075.
- **No blocker for Stage 4 frontend work** on Community Home, Profile, and the Admin shell —
  those are fully specified by existing endpoints.

## Screenshots

16 captured to `~/workspace/unilink-prototype-shots/` (4 screens × desktop 1440px / mobile
390px × light / dark). The 8 light screenshots and 2 dark examples are attached to the
delivery message. Admin mobile light/dark are **pre-fix** (see Finding 1); post-fix captures
`admin-mobile-fixed-light.png` and `admin-mobile-fixed-dark.png` were added 2026-11-19 and
confirm the fix.

## Confirmation — no production change

- No production frontend code, backend code, database schema, API contract, or
  authentication logic was modified. No packages were installed. No existing route was
  removed or renamed. Admin remains a separate entry.
- Evidence: `git status` in `~/workspace/unilink` shows HEAD unchanged at `0a300c8` with no
  tracked file modified; the only untracked files are the three phase reports
  (`09-frontend-redesign-audit.md`, `10-frontend-redesign-blueprint.md`, this file).
- The prototype lives entirely in the artifact `unilink-visual-prototype`, outside the repo.

## Next step

**Waiting for owner review and approval before Stage 4 (Frontend Implementation).**
