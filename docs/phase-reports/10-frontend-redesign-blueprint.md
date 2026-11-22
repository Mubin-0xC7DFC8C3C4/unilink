# Stage 2 — Complete Design Blueprint: UniLink Frontend Redesign

**Status:** Stage 2 deliverable — **document only. No code was written and no
existing file was modified.** This file
(`docs/phase-reports/10-frontend-redesign-blueprint.md`) is the only new
file. Stage 3 (visual prototype) starts only after owner approval of this
blueprint.

**Ground truth:** Phase 1 audit
(`docs/phase-reports/09-frontend-redesign-audit.md`, HEAD `0a300c8`),
`frontend/src/design/tokens.css`, `theme.ts`, `app/Layout.tsx`, `main.tsx`,
`admin/main.tsx`, `docs/design/DESIGN.md`, `docs/traceability.md` (SRS
traceability matrix, 116 requirements, TC-01–TC-31), and the six reference
screenshots (`~/workspace/user/media_library/image/`: `github-1.png`,
`github-2.png`, `github-3.png`, `link-1.png`, `link-2.png`, `link-3.png`).

**Classification used throughout:** **A** = existing SRS requirement ·
**B** = frontend design improvement · **C** = proposed new feature
requiring approval · **D** = future enhancement. Nothing is silently
expanded.

**Main-agent verification notes (2026-11-18 ~22:20 +06):** all 21 contrast
ratios in §2 were independently recomputed with the WCAG
relative-luminance formula — **0 mismatches**. All four proposed packages
were verified on the npm registry and are compatible with React 18:
`lucide-react` 1.75.0 (peer React ^16.5.1||^17||^18||^19),
`@fontsource-variable/inter` 5.3.0, `@fontsource/noto-sans-bengali`
5.3.0, `qrcode.react` 4.2.0 (peer React ^16.8||^17||^18||^19). Two
corrections were made to the coordinator's draft and are marked
**[CORRECTED]**: (1) the admin rail keeps **Announcements** as a 14th
item — the draft's 13-item list dropped the existing
`/admin/announcements` page, which would delete a working feature;
(2) the route-total counts in §7 were recomputed (the draft listed 10
items under "9 new canonical routes").

---

## 1. Brand direction and reference-principle analysis

**Brand direction.** UniLink is the professional home of Bangladesh's
university students: one verified identity, two ways of working. The
brand is navy and blue on cool white and slate — academic without being
institutional, technical without being a code tool, warm in tone but
rigorous in structure. It must read as a serious product a student would
put on a CV, not as a campus noticeboard and not as a clone of LinkedIn
or GitHub.

**Brand attributes:** (1) Professional — every surface could sit beside
a job application. (2) Academic — university, department, batch and
verification are first-class identity, not metadata. (3) Trustworthy —
verification status is always visible and never faked; empty states are
honest. (4) Technology-oriented — the Workspace mode speaks in
repositories, teams and stacks, but only about things that really exist.
(5) Minimal but rich — density comes from information, not decoration.
(6) Consistent — one shell, one design system, two clearly different
modes. (7) Bangladeshi and international — Bengali-capable type, local
university context, global product standards.

### What to take from each screenshot — and what NOT to take

**link-1 — LinkedIn home feed. TAKE:** the three-column hierarchy
(identity/navigation left, feed centre, contextual right rail); the
profile-summary card with avatar, name, headline and counts; the
composer as a single affordance row (Photo / Video / Event / Write
article → in UniLink: Text / Project idea / Discussion / Event); feed
cards that lead with author identity + headline + timestamp, then
content, then a reaction-count line, then a uniform Like / Comment /
Share / Send action row; the right rail's news-list pattern (headline +
age + readers). **NOT:** the blue brand colour, "Jobs" and "For
Business" (no job portal — SRS §1.2), Premium upsells and ads, "1st"
connection-degree labels (no connection-degree model in UniLink), or
the "Sort by: Top" opaque ranking (feed order is level + recency, per
FR-COM-02–05).

**link-2 — LinkedIn profile. TAKE:** cover image with overlapping
circular avatar; name + verification affordance on one line; headline,
location, connection count; a primary action row (Open to / Add profile
section / Resources → in UniLink: Follow / Message / Edit profile);
sectioned content below the header; right-rail contextual cards
("Profile language", "Public profile & URL", "Who your viewers also
viewed" → in UniLink: "Who else in your department", "Similar
students"). **NOT:** recruiter-oriented "Open to work / hiring" cards,
the "Add verification badge" upsell, vanity public URLs, or any
hiring/recruiter surface.

**link-3 — LinkedIn messaging. TAKE:** the two-pane layout
(conversation list left, active thread right); list rows with avatar,
name, one-line preview, timestamp and a selected state; date dividers
inside the thread ("Today"); a composer with attachment controls and a
Send button; the search field above the list. **NOT:** stickers/meme
imagery, the "Conversation Thread" meta explainer text, or any
voice/video call affordance (out of scope for MVP per the master prompt
§6).

**github-1 — GitHub dashboard. TAKE:** the left "Top Repositories" list
with a filter input and a "New" action; the centre "Home" with
task-oriented onboarding cards ("Start a new repository", "Introduce
yourself with a profile README" — in UniLink: "Create your first
project", "Complete your profile"); the right rail "Latest changes"
timeline and "Explore repositories" discovery list; dense, small-type
list rows with owner/name pairs. **NOT:** any code content, the Copilot
upsell, or the "Start writing code" framing (UniLink's onboarding is
"Start a project / Join a team").

**github-2 — GitHub repository page. TAKE:** the `owner / name`
breadcrumb with a visibility pill (Public); the action row of small
outlined buttons (Pin / Unwatch / Fork / Star → in UniLink: Save /
Follow / Share-link); the tab bar with an active underline (Code /
Issues / Pull requests / Actions / Projects / Wiki / Security → in
UniLink: Overview / Team / Tasks / Activity / Discussions); the
branch/file toolbar pattern (in UniLink: a filter + "New" toolbar above
the project list); the "About" sidebar with icon-led stat lines
(Readme / stars / watching / forks → in UniLink: description, links,
team size, visibility, created date). **NOT:** any git feature — no Code
tab, file tree, commits, branches, pull requests, Actions, Wiki, or
Security tab as functional features. The tab *pattern* is borrowed; the
tab *contents* are only what the backend supports (see §9).

**github-3 — GitHub contribution graph. TAKE:** only the visual grammar
— a dense weekly grid with month/day labels and a "Less / More" legend,
plus the transparency pattern of a "Learn how we count contributions"
link. **NOT:** the graph itself as a functional feature, the "246
contributions in the last year" count, or the activity-overview radar
percentages. Per the ruling, UniLink renders an honest empty state for
contributions until a backend aggregation endpoint exists (audit §7: no
contribution entity/repository/DTO anywhere;
`GET /profiles/{id}/activity` returns only the latest 20 posts). A
contribution grid in the Stage 3 prototype is shown only as a labelled
future-direction annotation, never as a rendered component with sample
counts.

---

## 2. Colour palette — light + dark token tables

Adopted and refined from the master prompt §4 tokens. All contrast
ratios below were computed with the WCAG relative-luminance formula
(this blueprint, not copied from a reference) and **independently
re-verified by the main agent — 0 mismatches across all 21 pairs**.
Two honest corrections to the §4 tokens: **Warning #D99B32 is 2.42:1 on
white — unusable as text** (fill/icon accent only; warning text is a
darker amber), and **#64748B secondary text is 4.76:1 on white but
4.36:1 on the #F3F5F8 page background** — it is therefore restricted to
white surfaces, with a stronger token for tinted surfaces. Danger red
is a new token (B — it fills the DESIGN.md D-040 gap; the reference
had no error colour).

### Light theme

| Token | Hex | Use | Contrast (verified) |
|---|---|---|---|
| `--background` | `#F3F5F8` | Page background | — |
| `--surface` | `#FFFFFF` | Cards, inputs, menus | — |
| `--surface-muted` | `#E8EEF4` | Selected nav row, hover fill, subtle wells | — |
| `--surface-accent` | `#EAF2FA` | Blue tint: selected tabs, info backgrounds | — |
| `--border` | `#E2E8F0` | 1px hairlines, card outlines | — |
| `--border-strong` | `#CBD5E1` | Input borders, dividers needing emphasis | — |
| `--text-primary` | `#172B45` | Headings, primary text | 14.31:1 on white; 13.10:1 on `#F3F5F8` |
| `--text-secondary` | `#64748B` | Secondary text — **white surfaces only** | 4.76:1 on white |
| `--text-secondary-strong` | `#54687E` | Secondary text on tinted surfaces (`#F3F5F8`, `#EAF2FA`) | 5.26:1 on `#F3F5F8`; 5.74:1 on white |
| `--text-muted` | `#64748B` | Captions, timestamps, placeholders (non-essential only) | 4.76:1 on white |
| `--primary-navy` | `#172B45` | Brand: logo tile, header accents, primary headings | — |
| `--primary-blue` | `#2878C8` | Links, primary buttons, focus, active states | 4.55:1 on white; white on it 4.55:1 |
| `--primary-blue-hover` | `#1D6FB8` | Button hover, link text on tinted surfaces | 5.23:1 on white; 4.79:1 on `#F3F5F8` |
| `--accent-bg` | `#EAF2FA` | Blue tint background | — |
| `--success` | `#23834C` | Success text, icons, filled success buttons | 4.75:1 on white; white on it 4.75:1 |
| `--success-bg` | `#E7F4EC` | Success tint background | text on it: `#23834C`, re-check in Phase 3 |
| `--warning` | `#D99B32` | Warning fill / icon accent **only — never text** | 2.42:1 on white (fails; do not use as text) |
| `--warning-text` | `#8A5A00` | Warning text on white or `#FFF7E6` | 5.93:1 on white; 5.56:1 on `#FFF7E6` |
| `--warning-bg` | `#FFF7E6` | Warning tint background | — |
| `--danger` | `#B42318` | Error text, icons, destructive buttons (new, B) | 6.57:1 on white; white on it 6.57:1 |
| `--danger-bg` | `#FEF2F2` | Error tint background | — |
| `--info` | `#2878C8` | Info text/icons (same as primary blue) | 4.55:1 on white |
| `--info-bg` | `#EAF2FA` | Info tint background | — |
| `--focus-ring` | `#2878C8` | 2px outline + 2px offset on `:focus-visible` | — |
| `--scrim` | `rgba(23,43,69,0.55)` | Modal/drawer scrim (replaces `#a8a8a5`) | — |

### Dark theme

| Token | Hex | Use | Contrast (verified) |
|---|---|---|---|
| `--background` | `#161E2B` | Page background (deeper than surface so cards read as raised) | — |
| `--surface` | `#202938` | Cards, inputs, menus (§4 Dark Surface) | — |
| `--surface-muted` | `#2A3648` | Selected nav row, hover fill | — |
| `--surface-accent` | `#22344A` | Blue tint in dark | — |
| `--border` | `#334155` | 1px hairlines | — |
| `--border-strong` | `#475569` | Input borders | — |
| `--text-primary` | `#F5F7FA` | Headings, primary text | 13.63:1 on `#202938` |
| `--text-secondary` | `#CBD5E1` | Secondary text | 9.85:1 on `#202938` |
| `--text-muted` | `#94A3B8` | Captions, timestamps (now passes AA — fixes the old 3.85:1 dark muted) | 5.70:1 on `#202938` |
| `--primary-navy` | `#172B45` | Brand tile / header accent (unchanged hue) | — |
| `--primary-blue` | `#5B9BD5` | Links and blue text in dark | 4.94:1 on `#202938`; 5.65:1 on `#161E2B` |
| `--primary-blue-solid` | `#2E6DA8` | Filled primary buttons in dark | white on it 5.43:1 |
| `--success` | `#7EC98F` | Success text/icons in dark | 7.41:1 on `#202938` |
| `--success-solid` | `#23834C` | Filled success buttons (unchanged) | white on it 4.75:1 |
| `--warning` | `#E8A33D` | Warning text/icons in dark | 6.78:1 on `#202938` |
| `--warning-bg` | `#3A2E12` | Warning tint in dark | — |
| `--danger` | `#F28B82` | Error text/icons in dark | 6.12:1 on `#202938` |
| `--danger-solid` | `#B42318` | Destructive buttons (unchanged) | white on it 6.57:1 |
| `--info` | `#5B9BD5` | Info text/icons in dark | 4.94:1 on `#202938` |
| `--focus-ring` | `#5B9BD5` | Focus ring in dark | — |
| `--scrim` | `rgba(2,6,17,0.65)` | Modal/drawer scrim in dark | — |

### What changes from `tokens.css` and what stays

**Changes (B):** every colour token (warm ink/paper `#fdfdfb`/`#0e0e0c`/
`#14140f` → cool navy/slate above); primary action from ink `#14140f`
to navy `#172B45` + blue `#2878C8`; new semantic tokens (success/
warning/danger/info, focus ring, scrim) that DESIGN.md explicitly left
open (D-040); `.display` 40px → 28px (§3); `--label-caps` 11px → 12px
(§3); `--container-max` 1000px → per-layout widths (§4); shadows
added (§4); the three hardcoded `#fff3cd`/`#5c3d00` warning banners in
`auth.tsx:38`, `SettingsPage.tsx:22`, `AdminChangePassword.tsx:14`
become `--warning-bg`/`--warning-text` (fixes both the token-rule
violation and the dark-mode bug — audit §4). **Stays:** the token
mechanism itself (CSS custom properties, `:root` +
`[data-theme="dark"]`, `localStorage` key `unilink-theme-hint`,
`prefers-color-scheme` first-visit fallback — audit §4); the spacing
scale (4/8/16/24/32/48); the radius scale (extended, not replaced);
the eyebrow → display → lead typographic pattern from DESIGN.md; depth
from hairlines and surface tone, with shadows only subtle (DESIGN.md
"no drop shadows" is relaxed to one subtle tier — B — because the
references use soft card shadows, and flat-only cards read as
unfinished next to LinkedIn/GitHub density); the rule that `tokens.css`
is the only file containing raw hex, enforced by
`scripts/check-tokens.mjs`.

---

## 3. Typography

**Font stacks (B; proposed new dependencies for Phase 3:
`@fontsource-variable/inter` and `@fontsource/noto-sans-bengali`,
bundled — no CDN, so the app still works offline):**
- Latin/UI: `"Inter Variable", Inter, system-ui, -apple-system, "Segoe UI", Roboto, sans-serif`
- Bengali: `"Noto Sans Bengali", "Inter Variable", system-ui, sans-serif` (NFR-LOC-01 requires Bengali-capable display; Inter has no Bengali glyphs, so the Bengali face must be first in the stack for Bengali text and present as a fallback everywhere)
- Monospace (code/IDs/OTP only): `ui-monospace, "SF Mono", "Cascadia Mono", Consolas, monospace`

**Scale — reconciled to the master prompt targets (24–30 / 17–20 /
14–16 / 12–13):**

| Token | Size / Weight / Line-height | Use | Change from current |
|---|---|---|---|
| `--display` | 28px / 600 / 1.2, −0.01em | Page titles (target 24–30px) | was 40px/500 — the single biggest over-scale fix |
| `--headline` | 20px / 600 / 1.3 | Section titles (target 17–20px) | was 24px/400 |
| `--title` | 18px / 600 / 1.35 | Card titles, subsection heads | was 18px/500 |
| `--subtitle` | 16px / 600 / 1.4 | Small section heads (new) | new |
| `--body-lg` | 16px / 400 / 1.55 | Lead text, post body | was 18px/300 |
| `--body-md` | 15px / 400 / 1.55 | Default body (target 14–16px) | was 14px |
| `--body-sm` | 14px / 400 / 1.5 | Dense lists, meta lines | was 12px |
| `--label` | 12px / 500 / 1.45 | Labels, captions, timestamps (target 12–13px) | was 12px (`--body-sm`) |
| `--label-caps` | 12px / 600 / 1.4, uppercase, +0.08em | Eyebrows, stat captions | was 11px/400 — now meets the 12–13px target |
| `--stat` | 28px / 600 / 1.1 | Stat-strip numbers | was 28px/500 |

Weights in use: 400 (body), 500 (labels, buttons), 600 (headings,
emphasis), 700 (rare — stat highlights, unread counts). No 300 body
weight (the old `.lead` 300 was too light for Bengali glyphs). Line
length: body copy capped at ~68ch. Numbers that are data (counts,
dates) use tabular figures via `font-variant-numeric: tabular-nums` on
stat and table cells (B).

---

## 4. Design tokens (non-colour)

**Spacing (stays, extended by one step):** 4 / 8 / 12 / 16 / 24 / 32 /
48 / 64. The 12px step is added (B) for card-internal padding and
list-row gaps; the DESIGN.md discipline (no off-scale values) is kept.

**Radius (stays, extended):** `--radius-xs: 4px` (badges, chips — new),
`--radius-sm: 8px` (inputs, nav rows), `--radius-md: 12px` (cards),
`--radius-lg: 16px` (modals, large cards), `--radius-xl: 24px`
(profile header card — new), `--radius-full: 9999px` (pills, avatars).
Stat strip stays square (`--radius-none: 0`) per DESIGN.md.

**Shadows (new, B — one subtle tier each):**
- `--shadow-sm: 0 1px 2px rgba(23,43,69,0.06)` (cards at rest)
- `--shadow-md: 0 4px 12px rgba(23,43,69,0.10)` (dropdowns, hover lift)
- `--shadow-lg: 0 12px 32px rgba(23,43,69,0.16)` (modals, drawers)
- Dark equivalents use `rgba(2,6,17,0.35/0.45/0.55)`. Cards also keep
  a 1px `--border` — shadow is never the only separator.

**Breakpoints:** mobile `360–767px`, tablet `768–1199px`, desktop
`≥1200px` (per ruling). Media queries at `768px` and `1200px`
(replacing the single 720px breakpoint in `tokens.css:73`).

**Z-index scale:** content `0` · sticky header `50` · mobile bottom
nav `55` · drawer `60` · dropdown/menu `70` · modal `80` · toast `90` ·
skip-link `100`.

**Container widths:**
- Community home: max `1180px`, 3 columns `264px | minmax(0,1fr) |
  320px`, gap `24px`. Left rail sticky under the header; right rail
  sticky.
- Community single-column pages (post detail, event detail): max
  `760px` centred.
- Workspace: max `1200px`; overview uses a 12-column grid (main `8fr`,
  side `4fr`); project detail max `1080px` with a `280px` About
  sidebar (github-2 pattern).
- Admin: fluid max `1280px`, left rail `240px` + content; verification
  review pane splits `1fr | 420px` (ID image beside data, per
  DESIGN.md).
- Auth pages: max `480px` form column (replaces the hardcoded
  `style={{maxWidth:480}}` literals — audit §3).

---

## 5. Application architecture

**One student SPA, two modes, one shell; admin stays a separate entry.**

- **Student app:** a single entry (`index.html` → `src/main.tsx`, one
  `BrowserRouter`, no basename) and one `AuthProvider` / one
  `QueryClient` / one `ThemeProvider`. Community and Workspace are
  **modes of the same app**, not apps: the mode is derived from the
  route (`/community/*` vs `/workspace/*`), and the shell (header,
  mode switcher, search, notifications, messages, profile menu) is
  shared. One session, one identity, one notification system, one
  search — per the master prompt §2.
- **Admin app:** unchanged as a separate entry (`admin.html` →
  `src/admin/main.tsx`, `basename="/admin"`), restyled only, security
  logic untouched. Its navigation defect (audit §1) is fixed in Phase
  3 before any admin link is built on (see §12).

**RECOMMENDED URL scheme: path-prefixed modes — `/community/*` and
`/workspace/*`.**

Canonical routes (see §7 for the full map):
- Community home: `/community/home?level=DEPARTMENT` (level in the
  query string so it is shareable and deep-linkable — fixing the audit
  finding that level tabs are component state, not routes; default
  `DEPARTMENT` per FR-COM-07, already the current default).
- Workspace overview: `/workspace/overview`.
- Mode-neutral: `/`, `/login`, `/signup`, `/otp`, `/verify-email`,
  `/pending`, `/forgot-password`, `/appeals/public`, `/settings`,
  `/notifications`.

**Migration plan for the current flat routes (no deep link breaks, no
route removed):** every current route becomes a permanent client-side
alias that `<Navigate replace>`s to its canonical path, kept for at
least the whole redesign release cycle: `/feed` → `/community/home`;
`/posts/:id` → `/community/posts/:id`; `/events` →
`/community/events`; `/events/:id` → `/community/events/:id`;
`/calendar` → `/community/calendar`; `/teams` → `/workspace/teams`;
`/search` → `/community/discover`; `/profile/:id` →
`/community/profile/:id`; `/messages` → `/community/messages`;
`/saved` → `/community/saved`. `/settings` and `/notifications` are
already mode-neutral and do not move. The alias table lives in one
place (`src/app/routes.ts`, new — B) so no call site hardcodes a path
again (audit §10: "hardcoded absolute paths everywhere"). After one
release cycle the aliases may be removed only with an owner-approved
migration note; they are never removed silently.

**Mode switching — preserves auth and context:** the switcher is a
segmented control in the global header, `[Community | Workspace]`,
always visible when signed in. Switching never touches auth (same
session, same `AuthProvider`). Context mapping: Community → Workspace
lands on `/workspace/overview` (there is no per-page analogue for most
Community pages); Workspace → Community lands on `/community/home`
unless the user came from a page with a direct analogue (Teams ⇄ Teams
is the same page under `/workspace/teams`; profile has no Workspace
analogue). Query state that is mode-local is preserved where meaningful
(`?level=` stays inside Community; search text is carried into
`/community/discover?q=` when switching from the header search). The
switcher shows the current mode with a filled pill + label, not an icon
alone, and has `aria-pressed` / `aria-current` states.

**Alternatives rejected:**
- **(a) Two entries with a cross-app switcher** — rejected: student and
  admin sessions are separate apps with separate restore paths today
  (audit §8); a cross-app switcher would need shared session handling
  across entries and would reintroduce the two-SPA split the redesign
  exists to remove.
- **(b) Mode as a query parameter (`?mode=workspace`)** — rejected: it
  is not deep-linkable per section, it pollutes every link and
  bookmark, and it makes the mode invisible in the URL bar.
- **(c) Mode as client-only state (no URL)** — rejected for the same
  reasons as (b), plus refresh would lose the mode.

---

## 6. Navigation architecture

**Global header (one component, `AppHeader`, new — B; replaces
`Layout.tsx` header).** Sticky, `72px`, surface background, bottom
hairline, max-width matches the active mode's container. Contents,
left to right:
1. **Logo** — navy tile with a white "U" mark (new, B — no logo asset
   exists today) + "UniLink" wordmark; links to the current mode's
   landing page.
2. **Mode switcher** — segmented `[Community | Workspace]` control,
   prominent, immediately right of the logo (§5).
3. **Header search** — input with a Lucide `Search` icon and `/`
   keyboard hint (github-2 pattern); typing navigates to
   `/community/discover?q=…` (existing `GET /search?q=&type=` — audit
   §7); `Enter` submits, `Esc` clears. (B; today search is a bare nav
   link to a separate page.)
4. **Mode navigation** — icon + label items per mode (see below),
   centred on desktop.
5. **Notification bell** (Lucide `Bell`) with an unread-count badge fed
   by `GET /notifications/unread-count` — an endpoint that exists but
   **no page calls today** (audit §7); badge shows the real count,
   hidden at zero, capped display "9+". Click → `/notifications`. (B)
6. **Messages** (Lucide `MessageSquare`) with an unread indicator —
   real count only when a backend unread count for rooms exists; until
   then, a dot shown only when the room list reports unread messages
   (no invented counts). Click → `/community/messages`. (B)
7. **Profile menu** — avatar (new `Avatar` primitive, photo or
   initials) opening a dropdown: My Profile
   (`/community/profile/me`), Settings (`/settings`), theme toggle
   (Light/Dark, persisted — existing behaviour), Sign out
   (`POST /auth/logout`, existing). Replaces the current bare "Profile
   / Settings / Sign out" text links. (B)

**Community nav (ruling):** Home, My Network, Discover, Events,
Messages, Notifications, Saved, My Profile. Desktop: icon + label in
the header centre (link-1 pattern). The four community levels are
**not** header items — they are a segmented control at the top of
Community Home (`Department / University / National Dept. Hub /
National`, `?level=`), with the current level's context line ("Showing
students and posts from Computer Science, University of Dhaka" — from
`GET /community/context`, existing) beneath it (A: FR-COM-02–05,
FR-COM-07).

**Workspace nav (ruling):** Overview, My Projects, Teams, Project
Showcase, Saved Projects. Desktop: a left sidebar (github-1 pattern)
under the header plus the same items as header tabs on wide screens —
the blueprint specifies the **left sidebar** as canonical for Workspace
(it is what makes Workspace feel different from Community's top nav),
with the header carrying only logo / switcher / search / bell /
messages / profile.

**Admin nav (ruling, unchanged IA): [CORRECTED — 14 items, not 13.]**
Left rail, in the ruled order: Dashboard, Verification Queue, Content
Moderation, Appeals, User Management, Event Management, **Announcements**,
University Management, Department Management, Sector Management,
Moderator Management, Analytics, Audit Logs, Settings. The current rail
has 11 items (audit §4); **University / Department / Sector Management
are the three tabs of the existing Master Data page** (`MasterData.tsx`,
5 tabs) — the blueprint splits them into three rail items that all
render the Master Data page with the corresponding tab selected (B —
IA change, no new backend). Admin Settings is the new 13th item: it
consolidates the existing change-password form + theme + session info
(B — no new backend; see §10). **Announcements is the 14th item: it
is an existing working page (`/admin/announcements`,
`Announcements.tsx`) that the master prompt's §9 list did not name;
dropping it would delete a working feature, so it is retained
unchanged.**

**Mobile (360–767px):** header collapses to logo + mode switcher +
search icon + avatar. Primary navigation becomes a **bottom tab bar**
(5 items, icon + label, touch target ≥48px): Community — Home,
Network, Events, Messages, Profile; Workspace — Overview, Projects,
Teams, Showcase, Saved. A "More" tab opens a **drawer** (new `Drawer`
primitive) containing the remaining items (Discover, Notifications,
Saved, Settings, theme toggle, sign out). No important route is hidden
without a path to it (audit §4: today the header nav simply vanishes
≤720px). Tablet (768–1199px): Community home drops the right rail (its
cards move below the feed); Workspace sidebar collapses to icons;
admin rail narrows to icons with labels on tap.

---

## 7. Complete route map

Guard key: **P** = public · **S** = signed-in student (`RequireAuth`) ·
**V** = verified-student write-gated in UI (backend enforces; frontend
must explain upfront — audit §8) · **A** = signed-in admin with MFA
(`AdminGuard`). Data source cites audit §7 endpoints; **MISSING** means
no backend support exists today.

### Student — public (unchanged)

| Route | Purpose | Guard | Data source / class |
|---|---|---|---|
| `/` | Landing | P | Static (A) |
| `/login` | Student login | P | `POST /auth/login` (A) |
| `/signup` | Registration (3-step, ID + photo upload) | P | `POST /auth/register`, `GET /public/*` (A) |
| `/otp` | Phone OTP | P | `POST /auth/verify-phone`, `POST /auth/otp/resend` (A) |
| `/verify-email` | Email verification via token | P | `GET /auth/verify-email?token=` (A) |
| `/pending` | Verification status / resubmit (unguarded by design) | P | `GET /auth/verification/status`, `POST /auth/verification/resubmit` (A) |
| `/forgot-password` | Password reset request + confirm | P | `POST /auth/password-reset/request\|confirm` (A) |
| `/appeals/public` | Unguarded suspension appeal via token | P | `POST /appeals/public?token=` (A) |

### Student — mode-neutral authenticated (unchanged)

| Route | Purpose | Guard | Data source / class |
|---|---|---|---|
| `/settings` | Profile edit, photo/cover, privacy, password, theme, delete account | S | `PUT /profiles/me`, `PUT /profiles/me/photo\|cover`, `POST /auth/change-password`, `POST /me/delete` (A; headline/links/skills edit is B — endpoints already accept them, audit §7) |
| `/notifications` | Notification list + per-type preferences | S | `GET /notifications`, `POST /notifications/{id}/read`, `/read-all`, `/preferences` (A) |

### Student — Community (canonical)

| Route | Purpose | Guard | Data source / class |
|---|---|---|---|
| `/community/home?level=` | 3-column home; default `DEPARTMENT` | S (V for writes) | `GET /feed?level&sector&cursor`, `GET /community/context` (A) |
| `/community/posts/:id` | Post detail + comments + attachments | S (V for writes) | `GET\|PATCH\|DELETE /posts/{id}`, comments, `POST /reactions`, bookmarks, `POST /reports`, `POST /appeals` (A); **share: MISSING (C)** |
| `/community/events` | Events list, upcoming/past tabs, create | S (V to create) | `GET /events/upcoming`, `GET /events?past=true`, `POST /events` (A) |
| `/community/events/:id` | Event detail, RSVP, team request, comments | S (V for RSVP/team) | `GET\|PATCH\|DELETE /events/{id}`, `POST /events/{id}/respond`, `POST /events/{id}/team-request` (A); **respondent list/counts: MISSING (C)** |
| `/community/calendar` | Month calendar | S | `GET /calendar?from&to` (A; link titles to event detail — B fix, audit §9) |
| `/community/network` | **NEW:** connections, followers/following, suggested, discovery | S | Follow/unfollow `POST\|DELETE /profiles/{id}/follow` (A); **followers/following list endpoints: MISSING (C)**; **suggested students: MISSING (C)**; discovery via `GET /search?type=users` (A, name-only, verified only) |
| `/community/discover` | **NEW home of Search:** people / posts / sectors, skill filter | S | `GET /search?q&type=&sector=` (A); **skill search: MISSING (C)** — `GET /public/skills` exists but is uncalled |
| `/community/messages` | 2-pane messaging | S (V) | `GET /rooms`, messages, STOMP `/topic/room.*` (A); `POST /rooms/private/{userId}` exists but uncalled (B — wire it); **block/mute: endpoints exist, UI MISSING (B)** |
| `/community/profile/:id` | Student profile (incl. `/me`) | S | `GET /profiles/me\|{id}`, `GET /profiles/{id}/activity`, portfolio CRUD, follow (A); skills display only — skill endpoints uncalled (B) |
| `/community/saved` | Saved posts (+ events once wired) | S | `GET /bookmarks` (A; event-bookmark mis-link fix — B, audit §9) |

### Student — Workspace (all new routes; data per §9)

| Route | Purpose | Guard | Data source / class |
|---|---|---|---|
| `/workspace/overview` | Developer dashboard | S | `GET /profiles/me/projects`, `GET /teams`, `GET /profiles/me/activity` (A); contribution overview **empty state (D until backend endpoint — C if owner approves it)** |
| `/workspace/projects` | My + participating projects, repo-style cards | S | `GET /profiles/me/projects` (A); **visibility/status/tech tags/last-updated: MISSING (C)** — `ProjectDto` is only `id, userId, title, description, url, createdAt` |
| `/workspace/projects/:id` | Project detail, tabs Overview/Team/Tasks/Activity/Discussions | S | Overview+Team+Activity from existing endpoints (A); **Tasks: MISSING (C)**; **Discussions: MISSING (C)** — both render honest empty states |
| `/workspace/teams` | Team list + join/decide | S | `GET /teams`, join + decide (A — current `/teams` page, redesigned) |
| `/workspace/teams/:id` | Team detail, members, formation status | S | `GET /teams/{id}/requests`, team data (A, partial); **team invitations: MISSING (C)** |
| `/workspace/showcase` | Project discovery | S | Own + teammates' public projects via profile endpoints (A, thin); **global project discovery feed: MISSING (C)** |
| `/workspace/saved` | Saved projects | S | **MISSING (C)** — bookmarks only target posts today; needs a project bookmark target or a saved-projects endpoint |

### Student — aliases (all redirect `replace` to canonical; kept ≥1 release cycle)

`/feed` → `/community/home` · `/posts/:id` → `/community/posts/:id` · `/events` → `/community/events` · `/events/:id` → `/community/events/:id` · `/calendar` → `/community/calendar` · `/teams` → `/workspace/teams` · `/search` → `/community/discover` · `/profile/:id` → `/community/profile/:id` · `/messages` → `/community/messages` · `/saved` → `/community/saved`.

### Admin (unchanged routes; restyle only; security logic untouched)

`/admin` (Dashboard) · `/admin/login` · `/admin/totp` ·
`/admin/change-password` · `/admin/verifications` · `/admin/users` ·
`/admin/moderation` · `/admin/appeals` · `/admin/events` ·
`/admin/announcements` · `/admin/master-data` (rendered also as
University / Department / Sector Management rail items, §6) ·
`/admin/moderators` · `/admin/analytics` · `/admin/audit` ·
`/admin/settings` (**NEW page, B** — consolidates change-password +
theme + session info; no new backend) · `/admin/*` catch-all. Guard
**A** throughout (plus TOTP/must-change flow as today).

**Route total [CORRECTED]:** all **36 existing route entries preserved**
(21 student + 15 admin, per audit §4). **8 truly new canonical
routes:** `/community/network`, `/workspace/overview`,
`/workspace/projects`, `/workspace/projects/:id`,
`/workspace/teams/:id`, `/workspace/showcase`, `/workspace/saved`,
`/admin/settings`. **10 renamed/moved routes with aliases** (listed
above). Admin total becomes 16 entries (15 existing + `/admin/settings`).

---

## 8. Community page designs

Shared per-page contract: every page defines **loading** (skeleton),
**empty** (honest, with the next action), **error** (with retry),
**permission-denied** (explained upfront, not after submit), and
**disabled** (with the reason visible) states. No dead buttons. All
pages use the §12 primitives; no page invents its own card, tab, or
button style.

### 8.1 Community Home (`/community/home?level=`) — A + B

**Layout (desktop ≥1200px):** 3 columns `264px | 1fr | 320px` (link-1
pattern, UniLink identity).
- **Left rail:** Profile summary card (avatar, name, Verified badge,
  university · department · batch, follower/following counts from
  `ProfileDto` — A; "View profile" link); mode nav (Home / My Network
  / Discover / Events / Messages / Notifications / Saved / My Profile);
  **Community levels** segmented list (Department / University /
  National Dept. Hub / National) with the current level's scope line
  from `GET /community/context` (A, FR-COM-02–05); Followed sectors
  with follow/unfollow (existing
  `POST|DELETE /profiles/me/sectors/{sectorId}`).
- **Centre:** Welcome line ("Good evening, Ayesha — 3 events in your
  department this week" — only with real data; no invented counts);
  level header + scope line; **composer** (avatar + "Start a post…"
  opening an expanded composer: title, body, visibility selector,
  sector tags; **attachments in the composer are B** — the endpoint
  exists on PostDetail only today, so the composer wires the same
  `POST /posts/{id}/attachments` after creation); level tabs are the
  `?level=` segmented control; **feed** of post cards (§8.2) with
  cursor pagination ("Load more", existing keyset pagination); sector
  filter chips.
- **Right rail:** Upcoming Events card (nearest deadline first,
  `GET /events/upcoming` — FR-EVT-07); Suggested Students card (until
  the backend suggests anyone: an honest empty state "Suggestions
  aren't available yet" + a link to Discover — **no fabricated
  suggestions**); Trending Sectors (from followed sectors + sector
  list, real counts only); Opportunities card (open
  competitions/hackathons from `GET /events/upcoming` filtered by type
  — real data only).
- **States:** loading = skeleton feed (3 post skeletons); empty =
  per-level honest empty state ("No posts in your department yet — be
  the first to share" with composer CTA, for verified users;
  unverified users see the read-only explanation, §8.6); error =
  `ErrorState` + retry; unverified = banner explaining read-only
  National scope (FR-COM-05 / D-016) **before** the composer, which is
  disabled with the reason shown.
- **Tablet:** right rail drops below the feed; left rail collapses to a
  horizontal level strip. **Mobile:** single column; levels become a
  horizontal scrollable segmented control; left-rail cards move into
  the "More" drawer.

### 8.2 Post cards (shared by Home, Profile activity, Saved, Discover)

Author row (avatar, name, Verified badge, university · department,
timestamp, visibility pill — A); content (title, body, sector tags as
`Badge`s); attachments where present (existing
`GET /posts/{id}/attachments`); reaction-count line + action row **Like
/ Comment / Share / Save / Report** (link-1 pattern). Like is the only
reaction rendered today (`POST /reactions` supports types — other
reaction types are C). **Share is C** (no endpoint, audit §7) — the
action is present in the design only as a disabled "Share — coming
soon" affordance *if* the owner approves C; otherwise it is omitted
entirely. Never a decorative dead button. Save uses
`POST|DELETE /bookmarks` (A). Report uses `POST /reports` (A); hidden
posts show the existing appeal affordance (`POST /appeals`).

### 8.3 Student Profile (`/community/profile/:id`) — A + B

**Layout:** cover image (upload via existing
`PUT /profiles/me/cover`) with overlapping avatar (link-2 pattern);
header card: name + Verified badge, headline (B — edit UI for
`headline`, accepted by `UpdateProfile` but with no UI today, audit
§7), university · department · batch, bio, follower/following counts,
Follow / Message / Edit-profile actions (Follow uses existing
endpoints; Message opens `POST /rooms/private/{userId}` — B, endpoint
exists but uncalled). **Sections (ruled cards):** About; Skills
(display today; edit UI wired to the existing skill endpoints — B);
Interests (no field exists — **C**, needs a backend field; omitted
until approved); Projects / Education / Achievements (existing CRUD,
repo-card styling in Workspace mode, list styling here); External links
incl. GitHub / LinkedIn / portfolio (edit UI for `links` — B, accepted
by `UpdateProfile`); Activity (latest 20 posts via
`GET /profiles/{id}/activity` — A, with the honest note that it shows
recent posts, not a contribution history). Student ID number is
**never** displayed publicly (privacy, D-016/FR-PROF). **States:** own
profile vs other profile (edit affordances only when `isMe`); 404 →
`ApiUnavailable`; unverified viewer sees the profile but no
Message/Follow write affordances without explanation.

### 8.4 Network (`/community/network`) — A + B + C

**Layout:** tabbed page (Tabs primitive): **Suggested** / **Following**
/ **Followers** / **Discover**. Suggested: honest empty state until the
backend suggests anyone (C — no endpoint; no fabricated people).
Following/Followers: lists via the **C** list endpoints (until then,
the tabs show the counts from `ProfileDto` with an honest "lists
aren't available yet" state — no fake rows). Discover: search +
university/department/batch filters over `GET /search?type=users` (A —
name-only, verified only, 50 max; filters are C). Person cards:
avatar, name, Verified badge, university · department · batch, Follow
button (A). Alumni discovery is **out of scope** (SRS §1.2,
authoritative — see §14).

### 8.5 Events — list, detail, calendar — A + B + C

- **List (`/community/events`):** filter bar (type: Seminar / Workshop
  / Competition / Hackathon / all — FR-EVT-01 enum; visibility
  filter); tabs Upcoming / Past (FR-EVT-05 archive job); event cards:
  poster (where an attachment exists), title, organiser, type pill,
  university, deadline with a "closes in Xd" indicator, date,
  visibility, registration status (Going/Interested/none). Create-event
  form (verified only, FR-EVT-01/02/09 — past deadlines rejected,
  existing validation).
- **Detail (`/community/events/:id`):** header (poster, title,
  organiser, date, deadline, visibility); description; eligibility;
  registration link; RSVP **Interested / Going**
  (`POST /events/{id}/respond` — A) with the current state shown
  (today the frontend never displays RSVP state — B fix); **respondent
  list/counts: C** (no GET endpoint); comments with @-mentions
  (`POST /posts/{id}/comments` — FR-EVT-04, A); **Looking for
  Teammates** toggle + team request (`POST /events/{id}/team-request`
  — FR-EVT-06/FR-TEAM-01, A) with the created team linked to
  `/workspace/teams/:id`; owner edit/delete (FR-EVT-08, A). Past
  events are visually distinct (muted card, "Past event" badge,
  registration closed).
- **Calendar (`/community/calendar`):** month grid from
  `GET /calendar?from&to`; every event title **links to its detail
  page** (B fix — today they are plain text, audit §4); deadline dots
  on days with deadlines.

### 8.6 Messaging (`/community/messages`) — A + B

**Layout:** 2-pane (link-3 pattern): left conversation list (search
via `GET /rooms/search?q=` — FR-MSG-03; rows with avatar/group icon,
name, last-message preview, timestamp, unread dot, selected state),
right active thread (date dividers, message bubbles with timestamps
and read ticks — FR-MSG-05, existing; composer with attachment button
— FR-MSG-07, existing `POST /rooms/{id}/attachments`). Room types:
Department room, University room (FR-MSG-01, auto-joined), Private
chat (FR-MSG-02, wired via the uncalled `POST /rooms/private/{userId}`
— B), Team room (created on team-join acceptance, FR-TEAM-03),
Temporary room (FR-MSG-04, create/invite/close — existing).
**Fixes folded in (B):** `onError` handler assigned (audit §8 — today
STOMP errors are invisible); invite by picker from
`GET /search?type=users` instead of a hand-typed numeric id (audit
§4); block/mute UI wired to the existing endpoints (FR-MSG-06);
room-admin actions use the existing member/remove/close endpoints
(FR-MSG-08) with the `memberIds[0]` heuristic replaced by a backend
`creatorId` only if the owner approves that field (C — until then the
heuristic stays, labelled in code). **STOMP wiring, REST fallback, and
membership checks are preserved exactly** (audit §7/§10). **States:**
unverified users see the existing empty-state copy ("Your department
and university rooms appear here once you are verified") **plus** an
upfront explanation banner; expired sessions stop the reconnect loop
with a re-login prompt instead of silent REST-fallback 401s (B).
**Mobile:** list and thread become separate screens with a back
affordance; composer stays pinned above the keyboard.

### 8.7 Notifications (`/notifications`) — A + B

List grouped by day; unread items highlighted with a dot; mark-read /
mark-all-read (existing); per-type email + in-app preferences (existing
3 types). **Honesty fix (B):** the page copy today promises "mentions,
team updates and event reminders" but only 3 notification types are
ever emitted (audit §7) — the copy is corrected to list exactly the
types the backend emits, and mention/event-reminder types are **C**.
Unread badge in the header is fed by the real
`GET /notifications/unread-count` (B).

### 8.8 Saved (`/community/saved`) — A + B

List of bookmarked posts (existing `GET /bookmarks`); each row links
to the correct target — **the event-bookmark mis-link is fixed (B):**
event bookmarks link to `/community/events/:id`, post bookmarks to
`/community/posts/:id` (audit §4/§9). Empty state with a link to
Discover.

### 8.9 Discover / Search (`/community/discover`) — A + B + C

One search page (replaces `/search`): query input, type tabs (All /
People / Posts), sector filter (existing
`GET /search?q&type=&sector=`), and a **skill filter (C)** —
`GET /public/skills` exists but is uncalled, and the users branch of
search does not filter by skill. Result cards link to profiles and
posts. The page lead no longer claims skill search until C is approved.

### 8.10 Settings (`/settings`) — A + B

Sections (existing, restyled): Profile (bio **plus headline/links/
skills edit — B**, all accepted by `UpdateProfile` today), Photo &
cover (existing uploads), Privacy (phoneVisible — existing; note the
audit's open question of whether `ProfileDto` exposes it is
re-confirmed in Phase 3), Password (existing), Theme (existing toggle
— **copy fix (B):** remove "saved to your account", since no
server-side theme save exists — audit §4), Account deletion (existing
`POST /me/delete` with confirmation dialog, per NFR-PRV-02). No new
backend.

---

## 9. Workspace page designs — must NOT look like a social feed

Workspace uses tables, repo cards, tabs and structured panels
(github-1/2 patterns). No feed, no composer, no Like/Comment action
rows anywhere in Workspace.

### 9.1 Workspace Overview (`/workspace/overview`) — A + B + D

**Layout:** 12-column grid — main column (8fr) + side column (4fr).
**Main:** Welcome line (name, university, mode context); **My
Projects** (up to 5 repo cards, link to `/workspace/projects`);
**Projects I participate in** (teams the student belongs to —
`GET /teams` + linked projects; thin today, labelled honestly); **Team
activity** (pending join requests to decide — existing
`GET /teams/{id}/requests`; recent acceptances); **Recently added
projects** (by `createdAt` — labelled "added", **not** "updated",
because `Project` has no `updatedAt` field; "last updated" is C).
**Side:** **Contribution overview** — honest empty state (see
§1/github-3): "Contribution activity isn't available yet. UniLink
doesn't track per-day activity — your recent posts and projects are
listed below." with a link to Activity (D until a backend aggregation
endpoint exists; the endpoint itself is C). **Project discovery**
(link to Showcase); **Shortcuts** (New project, Find a team, View
profile). **States:** a student with no projects sees onboarding cards
("Create your first project", "Complete your profile" — github-1
pattern) with real CTAs, not decorative placeholders.

### 9.2 My Projects (`/workspace/projects`) — A + B + C

**Repo-style cards** (github-2 "About" sidebar pattern, as cards):
`owner / name` title line, description, link row (GitHub repository
link and demo link — both from the project's `url` field today;
separate demo-link field is C), footer meta: created date, team size
where a team is linked. **Fields the backend does not return are
omitted, not invented:** visibility, status, technology tags and "last
updated" do not appear on the card; they are listed as **C** backend
items in §14 (a `technologies` field, `visibility`, `status`,
`updatedAt` on `Project`). Filters: text filter + "Mine /
Participating" tabs (existing data). Sort: newest first (by
`createdAt`). "New project" opens the existing portfolio create form
(restyled as a dialog). Empty state: onboarding card, not a blank grid.

### 9.3 Project Detail (`/workspace/projects/:id`) — A + B + C

**Header (github-2 pattern):** `owner / project-name` breadcrumb +
visibility pill (only when the backend provides it — C; otherwise
omitted); action row: Save, Follow owner, Copy link. **Tab bar (active
underline):** Overview / Team / Tasks / Activity / Discussions.
- **Overview (A):** description, links (repository + demo), created
  date, owner card, team summary.
- **Team (A, partial):** members from the linked team (`GET /teams`,
  join-request decide); roles are **C** (no role model — members are
  listed without invented roles); invitations are **C** (no invite
  endpoint — the tab shows the join-request flow that exists).
- **Tasks (C):** honest empty state — "Tasks aren't available yet.
  Project tasks need backend support (see Stage 6 plan)." No fake task
  list, no fabricated progress bars. If the owner approves the backend
  work, this tab becomes the task board; until then it is an empty
  state, not a dead tab (the tab is present so the IA is stable, with
  the state explained).
- **Activity (A):** the owner's recent activity via
  `GET /profiles/{id}/activity` (latest 20 posts, visibility-scoped) —
  labelled as recent posts, not commits.
- **Discussions (C):** honest empty state — "Discussions aren't
  available yet. Project discussions need backend support." (No
  discussion endpoint tied to projects exists; post comments are not
  repurposed as project discussions.)

### 9.4 Teams (`/workspace/teams`, `/workspace/teams/:id`) — A + B + C

**List:** team cards (name, linked event where applicable, member
count vs `maxSize`, status — `teams.status` exists) with join-request
CTA (existing `POST /teams/{teamId}/join` — FR-TEAM-02) and
creator-only pending-request decide (existing). **No create-team UI
exists today** (creation only via event team-request — audit §4); the
redesign surfaces that path explicitly ("Teams are created from an
event's Looking for Teammates action" — A, FR-TEAM-01) and a standalone
create-team form is **C**. **Detail:** members, competition/hackathon
context (linked event), formation status (open/full/closed —
FR-TEAM-04: auto-close on full or event archival, with a closed
banner), team chat link (room created on acceptance — FR-TEAM-03),
collaboration panel linking to the team's room and project. Team
invitations are **C** (no invite endpoint).

### 9.5 Project Showcase (`/workspace/showcase`) — A + C

Discovery surface for public projects: search + sector filter over the
projects reachable through profile endpoints (thin today — labelled
honestly as "projects from students you follow and your teams"). A
global discovery feed is **C** (no public project-listing endpoint
exists). Cards are the same repo-style cards as §9.2. No fabricated
"trending" rankings.

### 9.6 Saved Projects (`/workspace/saved`) — C

**Entirely C:** bookmarks today only target posts. Until a project
bookmark target (or a saved-projects endpoint) is approved, the page
renders an honest empty state explaining that saving projects isn't
available yet, with a link to Showcase. No fake saved items.

---

## 10. Admin page designs (14 sections — restyle only, security untouched)

**[CORRECTED — 14 sections: the draft listed 13 and dropped
Announcements; see §6.]** Admin keeps its separate entry, left rail,
`AdminGuard`, TOTP enrolment/verify flow, must-change-password filter,
and all endpoints identical. **No change to `SecurityConfig`,
`MustChangePasswordFilter`, or the admin auth controllers.** The §1
(audit) basename defect is fixed in Phase 3 first (§12). All pages use
the §12 primitives and the same tokens; tables use the shared `Table` +
`Pagination` components (replacing hand-rolled lists); the rail
collapses to icons at tablet widths.

| Section | Redesign (B unless noted) | Data (existing unless noted) |
|---|---|---|
| Dashboard | Stat strip (real numbers only, from `GET /admin/dashboard`); "new users today"; moderator-scope note kept ("Moderators see their university only") | `GET /admin/dashboard` (A) |
| Verification Queue | Split review pane (ID image beside entered data + OCR score/text, previous attempts, duplicate-phone flag — existing); approve/reject with mandatory reason; keyboard "A" to approve kept; out-of-scope ID image renders the existing 404 explanation | `GET /admin/verification-queue`, approve/reject, `/id-image` (A) |
| Content Moderation | Queue table with target, reporter count, auto-hide state; decide HIDE/REMOVE/RESTORE/EDIT_REQUEST (existing actions); **WARN is not added** (D-088 still deferred — D) | `GET /admin/moderation-queue`, `POST /admin/moderation/decide` (A) |
| Appeals | Decide table (Uphold = REJECT, Overturn = ACCEPT, with reason); link to the underlying content/user | `GET /admin/appeals`, `POST /admin/appeals/{id}/decide` (A) |
| User Management | Search + user table; SUSPEND/BAN/REACTIVATE/REVOKE/FORCE_RESET with mandatory reason; SUSPEND/BAN remain Platform-Admin-only in the UI (existing client filter) **and** a visible explanation replaces the silent hide for moderators (B — audit §8: today the actions just disappear) | `GET /admin/users`, `POST /admin/users/{id}/{action}` (A) |
| Event Management | Events table with PATCH REMOVE/HIDE/RESTORE, close registration, fix deadline/visibility (existing) | `GET /admin/events`, `PATCH /admin/events/{id}` (A) |
| Announcements | Create/edit/deactivate with scope GLOBAL/UNIVERSITY (existing); **GLOBAL scope is hidden for moderators with an explanation** (B — today it is offered to all and fails server-side, audit §8) | `GET\|POST\|PUT\|DELETE /admin/announcements` (A) |
| University / Department / Sector Management | Three rail items rendering the existing Master Data page with the matching tab selected (B — IA split, §6); create/deactivate/rename as today; department-catalog tab stays read-only (the `INVALID_KIND` trap, audit §7/§10, is preserved — the buttons stay disabled with the reason shown) | `GET /public/*`, `/admin/master-data/*` (A) |
| Moderator Management | Table + create (email + university + ≥12-char password), assign university, deactivate, reset-credentials with the one-time temp password shown once (existing) | `GET\|POST /admin/moderators`, etc. (A) |
| Analytics | **Real tables, not JSON dumps (B):** events-per-sector table and 30-day user-growth table from `GET /admin/analytics` (today rendered as raw `JSON.stringify` — audit §4). No chart library is introduced; tables need no new dependency. Any charting is **C** (owner chooses a library or stays with tables) | `GET /admin/analytics` (A) |
| Audit Logs | Table with server-side `?limit=` + client-side action filter (existing); CSV export link kept, still labelled unfiltered | `GET /admin/audit-logs`, `/admin/audit-logs/export` (A) |
| Settings (NEW page) | Consolidates the existing change-password form + theme toggle + session info (B). No new backend. | `POST /admin/auth/change-password` (A) |
| TOTP enrolment | Enrolment screen restyled; **QR code rendering is C** (needs a QR library decision — proposed `qrcode.react`, Phase 3 dependency note; until approved, the current "type the URL" flow stays, restyled, with the raw `otpauthUrl` in a copyable field). Recovery codes still shown once. | `POST /admin/auth/totp/enrol\|verify` (A) |

---

## 11. Responsive behaviour per breakpoint

**Shell (all modes):** Desktop ≥1200px — full header (logo, switcher,
search input, nav, bell, messages, avatar menu). Tablet 768–1199px —
header keeps logo/switcher/search/bell/avatar; mode nav moves into the
left rail (Community) or collapses to icons (Workspace sidebar, admin
rail). Mobile 360–767px — header shows logo, switcher, search icon,
avatar only; navigation is the bottom tab bar + "More" drawer (§6);
the mode switcher stays in the header (it is the primary control and
is never hidden). No horizontal overflow at any width: tables get
horizontal scroll containers, long URLs wrap, the composer and message
input stay within the viewport.

**Community Home:** Desktop — 3 columns (§4). Tablet — left rail
collapses to a horizontal level strip above the feed; right rail moves
below the feed as stacked cards. Mobile — single column: level
segmented control (horizontally scrollable), composer, feed; left-rail
profile/saved links move into the drawer; right-rail cards appear
after the first 5 feed items, collapsed behind a "More from UniLink"
disclosure.

**Workspace Overview:** Desktop — 12-column grid (8fr/4fr). Tablet —
side column stacks below main. Mobile — single column; project cards
become full-width rows; the contribution empty state stays visible (it
is not hidden on small screens — hiding it would mislead).

**Profile:** Desktop — cover + header card + 2-column body (main
sections left, contextual rail right). Tablet — rail stacks below.
Mobile — avatar/cover scale down, action buttons become full-width
rows, sections stack in the order About → Projects → Skills →
Education → Achievements → Activity.

**Messages:** Desktop — 2-pane (320px list + thread). Tablet — 300px
list + thread. Mobile — list and thread are separate screens with a
back button; the thread's composer is pinned above the keyboard;
attachments open a bottom sheet.

**Admin:** Desktop — 240px rail + fluid content (max 1280px). Tablet —
rail collapses to 64px icons with tooltips. Mobile — rail becomes a
drawer opened from the header; tables scroll horizontally inside
cards; the verification review pane stacks (ID image above data).

---

## 12. Reusable component architecture

### Keep vs retire vs add

**Keep (audit §5 — all restyled to the new tokens, no API change):**
`Button` (add `size: sm | md | lg` — kills the raw `.btn-sm` bypass,
audit §3), `Field`, `Input` (fix: carry its own class instead of
relying on `.field` descendant styling), `PageHeader`, `EmptyState`,
`ErrorState`, `Skeleton`, `RuledList`, `StatStrip`, `ListPage`,
`Modal`, `Pill`.

**Retire (into one component):** `Toast` (dead), `NotFoundState`
(dead), `EndpointUnavailable` (dead) → a single `InlineAlert`
(info/warning/error/success, with icon + text) for in-page alerts. The
**Toast system** below is a separate, new, working system — not the
dead stub.

**Add (new primitives, all in `src/design/`):** `Card`, `Tabs`,
`Select`, `Textarea`, `Avatar` (photo or initials, sizes 24/32/40/64/
96), `Dropdown`/`Menu`, `Pagination` (cursor-aware, for the feed;
page-aware for admin tables), `Spinner`, `Badge` (visibility, type,
status, sector), `Tooltip`, `Drawer` (mobile nav + filters), `Dialog`
(confirmations — replaces `window.confirm`/`alert()` uses, e.g.
`SignUp.tsx:31`), `Toast` system (success/error toasts with a queue,
replacing ad-hoc `role="status"` paragraphs), `ModeSwitcher`
(segmented control), `HeaderSearch`, `NotificationBell` (badge +
dropdown preview), `StatCard`, `Table` (with sticky header + empty/
error states), `LevelTabs` (the 4-level segmented control), `PostCard`,
`ProjectCard` (repo-style), `PersonCard`, `EventCard`, `EmptyState`
variants (already exists — add the "not available yet" honest variant
used by Tasks/Discussions/Contributions).

**Icon standard:** Lucide React (`lucide-react`, proposed new
dependency — do not install in this stage). One family, 20px default
in navigation, 16px in buttons, 14px in meta lines; icons are always
accompanied by a text label in navigation (never icon-only except the
bell/messages/search, which get `aria-label`s). The `✓`/`→`/`📎` text
glyphs are retired (audit §4).

### Phase 3 prerequisites, in order

1. **Fix the admin basename defect first** (audit §1): drop
   `basename="/admin"` **or** drop the `/admin` prefix from every
   admin link/navigation; fix the dead must-change exemption
   (`AdminLayout.tsx:11` — compare against `/admin/change-password`);
   verify live with a signed-in admin on Windows before building any
   admin work on it.
2. **Typed API client** — one typed operation per endpoint from the
   audit §7 inventory, generated from `OpenApiConfig` if the owner
   prefers, else hand-written; keep `api()` transport, CSRF priming
   (`GET /api/csrf`), and 401 handling byte-for-byte in behaviour.
   This is the safety net for the router restructure (audit §10).
3. **Re-token `tokens.css`** per §2 (and update `DESIGN.md` + decision
   log D-040 — the palette change is owner-approved by the master
   prompt ruling, but the file must record it).
4. **Install the approved new dependencies** (`lucide-react`,
   `@fontsource-variable/inter`, `@fontsource/noto-sans-bengali`;
   `qrcode.react` only if the owner approves the TOTP QR item in §14).
5. **Build the primitives** (§12) and the new `AppHeader` + mode
   switcher; keep `Layout.tsx`/`AdminLayout.tsx` working until
   cutover.
6. **Small audit fixes folded into Phase 3** (B): theme "saved to your
   account" copy; `SettingsPage` profile save sending only `{bio}`
   (wire headline/links/skills); `SavedPage` event-bookmark link;
   `CalendarPage` plain-text titles; unverified-state upfront
   explanations (audit §8); `MessagesPage` `onError` handler;
   numeric-id invite replaced by a picker; Analytics JSON dumps →
   tables; Announcements GLOBAL scope hidden for moderators with
   explanation.

---

## 13. Stage 3 prototype plan — the 4 pages

Static visual prototypes (no backend calls, no real data). Every
prototype page carries a visible **"SAMPLE — prototype data, not real
UniLink activity"** banner, and every sample person/project is clearly
fictional. Desktop (1440px) and mobile (390px) directions for each.

1. **Community Home (desktop + mobile):** 3-column desktop per §8.1
   with sample content: sample student "Ayesha Rahman — CSE,
   University of Dhaka, Batch 2022" (labelled SAMPLE); left profile
   card + level control on Department; composer; 3 sample post cards
   (study post, project idea, discussion) with sample authors from two
   universities; right rail with 2 sample upcoming events, 2 sample
   suggested students (labelled as sample people, not real
   suggestions), trending sectors. Mobile: single column with
   scrollable level control, bottom tab bar + More drawer. Shows:
   layout, density, card system, level control, empty-state pattern
   (one level tab shown with the honest empty state).
2. **Workspace Overview (desktop + mobile):** 12-column grid per §9.1:
   welcome, 3 sample project cards (e.g. "Campus Bus Tracker", "Hall
   Meal Planner", "CSE Notes Hub" — all SAMPLE), team activity panel
   with 2 sample pending join requests, side column with the **honest
   contribution empty state** (the grid is NOT rendered; the
   empty-state card is the deliverable) + discovery + shortcuts.
   Mobile: stacked, bottom tab bar (Overview/Projects/Teams/Showcase/
   Saved).
3. **Student Profile (desktop + mobile):** cover + overlapping avatar,
   name + Verified badge, headline, university/department/batch, bio;
   sections About / Skills / Projects (2 sample repo-style cards) /
   Education / Achievements / Activity (3 sample posts); right rail
   with "Students in your department" (sample). Mobile: stacked
   sections, full-width action buttons.
4. **Admin Dashboard (desktop + mobile):** left rail with the 14 items,
   stat strip with sample numbers (labelled SAMPLE — these are
   prototype figures, not platform statistics), recent verifications
   table (sample rows), moderation queue summary; mobile: rail as
   drawer, tables scroll horizontally. Explicitly does **not** show:
   any TOTP or auth flow (security screens are not prototyped), or real
   user data.

Prototype tooling: built in the repo as static pages under the new
design system (Phase 3's tokens + primitives), or as a standalone
artifact — the parent agent decides the vehicle at Stage 3 start.
Either way, no prototype page is wired to a real endpoint.

---

## 14. Open decisions still needing the owner

These are the items the master prompt did **not** rule on. Nothing
below is decided in this blueprint.

1. **Alumni conflict (needs a confirmation, not a design choice):**
   SRS §1.2 (authoritative) excludes alumni accounts, and the master
   prompt §14 repeats that rule ("Do not introduce faculty, staff, or
   alumni accounts"); the master prompt §2 names alumni as audience
   and the audit flagged "Alumni discovery" as missing.
   Recommendation: keep SRS authoritative — no alumni accounts, no
   alumni discovery; "senior students / graduating batch" discovery
   stays within student accounts. Owner to confirm.
2. **Backend C-items (approve / descope / defer to Stage 6):** post
   sharing; followers/following list endpoints; suggested students;
   skill search; RSVP respondent lists/counts; mention + event-reminder
   notification types; project fields (`visibility`, `status`,
   `technologies`, `updatedAt`); tasks/milestones entities; project
   discussions; team invitations; team roles; standalone team
   creation; global project discovery; saved projects; contribution
   aggregation endpoint; `creatorId` on `RoomDto`. Each needs an
   approve/descope/defer ruling before Phase 5 can be scoped — without
   them, Workspace renders honest empty states (§9).
3. **QR library for TOTP enrolment** (`qrcode.react` proposed) vs
   keeping the "type the URL" flow.
4. **Charting for Analytics** — tables are specified (§10); any chart
   library is a separate decision.
5. **Announcement placement in the student UI** — `GET /announcements`
   exists but no page calls it (audit §7): feed slot, dedicated page,
   or right-rail card?
6. **Interests field on profiles** — no backend field exists (C);
   include in the Stage 6 backend list or drop from the profile
   design?
7. **Pre-existing owner decisions still open from earlier phases**
   (not re-opened here, recorded for completeness): D-122 CSRF
   narrowing (PROPOSED, needs a coordinated frontend change);
   `createModerator` 24-hour temp-password expiry (D-120); D-088 event
   kind/type + WARN action (deferred); D-067/D-068/D-069/D-075 (phone
   uniqueness, TOTP recovery, auto-hide, verification/visibility scope
   — OPEN/PROPOSED).
8. **Alias retirement timing** (§5): confirm the aliases are kept for
   at least one release cycle, and the owner accepts the
   redirect-based migration.

**Phase 3 prerequisites in order:** owner rulings on §14 items 1–6 (at
least the palette-adjacent ones are already ruled; the C-items gate
Phase 5, not Phase 3) → fix the admin basename defect → typed API
client → re-token → install approved dependencies → build primitives +
shell → fold in the §12 small fixes. **Do not start Phase 3
implementation until the owner approves this blueprint.**

---

## 15. Limitations of this blueprint

- Document only: no code was written, no dependency installed, no
  prototype built.
- All contrast ratios were computed from the hex values in §2 and
  independently re-verified (0 mismatches); they must still be
  re-checked in the browser in Phase 3, including on tinted surfaces
  (`--success-bg`, `--surface-accent`) where noted.
- Proposed package versions are as listed on the npm registry on
  2026-11-18 (`lucide-react` 1.75.0, `@fontsource-variable/inter`
  5.3.0, `@fontsource/noto-sans-bengali` 5.3.0, `qrcode.react` 4.2.0);
  the lockfile will pin whatever is current at install time.
- No live backend session was involved; every endpoint cited comes
  from the Phase 1 audit's read-only inventory.
- Design-reference screenshots were studied for principles only; no
  LinkedIn or GitHub logo, brand colour, exact layout, or proprietary
  asset is used in this blueprint.
