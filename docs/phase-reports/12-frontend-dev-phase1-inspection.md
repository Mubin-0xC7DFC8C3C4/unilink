# UniLink — Frontend Development Phase 1: Inspection Report

Date: 2026-11-20. Read-only inspection of `~/workspace/unilink` at HEAD `0a300c8`
(working tree clean; nothing modified). Requested by the owner's Frontend
Development Phase 1 instruction (2026-11-20 16:09 +06). No code was changed.

## 1. Existing frontend architecture

- React 18.3.1, TypeScript 5.6.3 (strict), Vite 5.4.9, react-router-dom 6.27.0,
  @tanstack/react-query 5.59.0, react-hook-form 7.53.0, @hookform/resolvers 3.9.0,
  zod 3.23.8. Tests: Vitest 2.1.5, Playwright 1.48.0.
  State beyond React Query: React context only (`AuthProvider`, `ThemeProvider`).
- Two entry points from `vite.config.ts`: student app (`index.html` →
  `src/main.tsx`) and admin app (`admin.html` → `src/admin/main.tsx`). Dev server
  on 5173 proxies `/api` and `/ws` to `localhost:8080`; a dev-only
  `adminSpaFallback` plugin rewrites `/admin/*` to `admin.html` (production needs
  the equivalent Nginx rewrite; no Nginx config exists in the repo).
- Design tokens live in `src/design/tokens.css` — the warm ink/paper palette from
  DESIGN.md / pic.docx (light `#fdfdfb` / ink `#14140f`; dark `#0e0e0c`), light and
  dark themes, preference persisted (`unilink-theme-hint`). This is the same
  palette the approved showcase uses, so the redesign does not require a palette
  change in code.
- No icon library exists; there are zero `<svg>` elements in `src/` — icons are
  text glyphs. No component/UI library.

## 2. Existing routes

Student app (`src/main.tsx`, single `Layout` wrapper; guards check only that a
session user exists — no role or verified-status route guard):

- Public: `/` (Landing), `/signup`, `/otp`, `/verify-email`, `/pending`,
  `/login`, `/forgot-password`, `/appeals/public`.
- Guarded: `/feed`, `/posts/:id`, `/events`, `/events/:id`, `/calendar`,
  `/teams`, `/search`, `/profile/:id`, `/messages`, `/notifications`, `/saved`,
  `/settings`. Catch-all → NotFound.
- `/pending` is deliberately unguarded (registration creates no session).

Admin app (`src/admin/main.tsx`, `BrowserRouter basename="/admin"`):

- Public: `/admin/login`, `/admin/totp`.
- Guarded: `/admin` (Dashboard), `/admin/change-password`, `/admin/verifications`,
  `/admin/users`, `/admin/moderation`, `/admin/appeals`, `/admin/events`,
  `/admin/announcements`, `/admin/master-data`, `/admin/moderators`,
  `/admin/analytics`, `/admin/audit`. Catch-all → NotFound.
- Known defect (verified live in the redesign audit): `basename="/admin"` plus
  absolute `/admin/…` link targets double-prefix to `/admin/admin/…`; admin
  in-app navigation is broken as built. Any admin implementation phase must fix
  this first.

## 3. Existing authentication implementation

Student sign-up (`SignUp.tsx`, a 3-step wizard on one page):

- Fields: `fullName`, `phone` (BD mobile pattern), `email`, `universityId`,
  `departmentId`, `batchId` (optional), `address`, `studentId` (no format
  pattern), `password` (8+ with upper/lower/digit). **No Confirm Password field.**
- Uploads: exactly **one** ID-card image (form key `idCard`) and **one** profile
  photo (`profilePhoto`), enforced client-side with `alert()`s. No preview,
  replace/remove UI, format list, or size guidance.
- University/department/batch selectors are API-driven plain `<select>`s from
  `GET /public/universities`, `GET /public/departments?universityId=`,
  `GET /public/batches?…` — not searchable.

Student verification flow (implemented order):

1. `POST /auth/register` (multipart) → user id in sessionStorage → `/otp`.
2. **Phone OTP first, before any review**: `POST /auth/verify-phone`
   (5-minute expiry, max 5 attempts); resend via `POST /auth/otp/resend` with a
   60-second client cooldown → `/pending`.
3. Email verification runs in parallel as a token link (`GET /auth/verify-email?token=`).
4. `/pending` shows `GET /auth/verification/status` (state + rejection reason;
   resubmit up to 3 times in 24 h). After admin/moderator approval the student
   simply logs in. **No Sign Up Success page exists.**

Login (`Login.tsx`): email + password only. No "remember me" anywhere; **no
show/hide password toggle on any auth form**. `POST /auth/login` → success goes
to `/feed` (or `/settings` when `mustChangePassword`); 401 shows an inline
error; 403 surfaces the server message (suspended/banned appeal guidance).

Forgot password (`ForgotPassword.tsx`): one page, email OTP. Step 1
`POST /auth/password-reset/request {email}`; step 2 is **one combined form**
holding the OTP and the new password together, submitted once to
`POST /auth/password-reset/confirm {email, otp, newPassword}` (the backend
checks the OTP only at confirm time; codes live 15 minutes). **No separate OTP
step, no Retype field, no resend control.** Step 3 is a success message linking
to `/login`.

Change password (signed-in students): a section inside Settings only —
`POST /auth/change-password {oldPassword, newPassword}`; success invalidates
all sessions and returns to `/login`. `Layout` force-redirects
`mustChangePassword` sessions to `/settings`.

Admin auth (`src/admin/pages/`): `AdminLogin` (email + password, 12+ chars) →
`POST /admin/auth/login` → `Totp` page (first-time enrolment shows the raw
`otpauthUrl` as text — **no QR code**; `POST /admin/auth/totp/enrol` returns 10
recovery codes shown once; otherwise `POST /admin/auth/totp/verify`) →
Dashboard, or `/admin/change-password` when `mustChangePassword`
(`POST /admin/auth/change-password`, MFA-verified only). The backend also
exposes `POST /admin/auth/email-otp/request|verify`, which the frontend never
calls (no UI). **The Admin Login page exists in the product** — it was missing
from the visual designs, not from the code.

## 4. Existing reusable components

- `src/design/primitives.tsx` (17 exports): Button, Field, Input, PageHeader,
  StatStrip, RuledList, Pill, VerifiedBadge, Modal, Toast, EmptyState,
  ErrorState, Skeleton, SessionExpiredNotice, plus three near-identical alert
  boxes (ApiUnavailable, NotFoundState, EndpointUnavailable).
- `src/features/shared.tsx`: `useList`, `ListPage`.
- Shells/providers: student `Layout`, `AdminLayout`, `AdminList`,
  `AuthProvider`/`RequireAuth`, `ThemeProvider`.
- Missing primitives the new design needs: Card, Tabs, Select (styled),
  Textarea, Checkbox, Avatar, Dropdown, Pagination, Spinner, a working toast
  system, and an icon set. Forms currently use raw HTML controls.

## 5. Existing API integration

- `src/api/client.ts`: fetch wrapper; every call prefixed `/api`,
  `credentials: 'include'`; CSRF token read from the `XSRF-TOKEN` cookie and
  sent as `X-XSRF-TOKEN` on every call; CSRF primed by `GET /api/csrf` on app
  load. Session cookie `UL_SESSION` (http-only, SameSite Lax, Spring Session
  JDBC); student idle 2 h, admin 30 min. On 401 (outside auth paths) the client
  flags `unilink:session-expired` and hard-navigates to the matching login page.
- No typed per-endpoint client: endpoints are string literals at call sites;
  DTO types live in `src/api/types.ts`.
- Auth endpoints actually called — student: `POST /auth/register`,
  `POST /auth/verify-phone`, `POST /auth/otp/resend`, `GET /auth/verify-email`,
  `POST /auth/login`, `POST /auth/logout`, `GET /auth/me`,
  `POST /auth/password-reset/request`, `POST /auth/password-reset/confirm`,
  `POST /auth/change-password`, `GET /auth/verification/status`,
  `POST /auth/verification/resubmit`, `POST /appeals/public`,
  `POST /me/delete`, plus the `/public/*` catalogues. Admin:
  `POST /admin/auth/login`, `POST /admin/auth/totp/enrol|verify`,
  `POST /admin/auth/change-password`, `POST /admin/auth/logout`,
  `GET /admin/auth/me`.
- Backend contract (`AuthController` + `AuthDtos.RegisterForm`): register is
  multipart `@ModelAttribute` returning 201, with **exactly one**
  `MultipartFile idCard` and one `profilePhoto`; `phone` must match
  `^01[3-9]\d{8}$`; there is **no** separate password-reset OTP-verify endpoint
  and **no** post-approval OTP issuance in the sign-up flow.

## 6. Missing pages (against the owner's latest page list)

Student:

- Sign Up Success page (does not exist).
- Dedicated Profile Edit page (does not exist — editing is split between
  ProfilePage's inline portfolio editing and Settings, which edits **bio only**;
  headline, links and skills have no edit UI anywhere).
- My Network page (does not exist; followers/following are counts only).
- Workspace: every screen (GitHub authorization, repository list/browser,
  contributors, UniLink comments) — no route, component, or GitHub reference
  exists in the code.
- Settings sections: Account Preferences, Notification Preferences, Connected
  Accounts, GitHub Connection (Settings today = change password, bio,
  photo/cover, phone-visibility privacy, theme, delete account).
- Auth UX pieces: Confirm Password, show/hide toggles, front/back ID uploads
  with preview/replace/remove, searchable catalogue selectors, a stepped
  forgot-password flow with resend countdown.

Admin:

- User Details page (Users is a single list with inline actions).
- Reports as its own page (report handling lives inside Moderation).
- Security page; Settings page (only change-password exists).
- Admin Login exists (see §3) — it needs restyling, not creation.

## 7. Conflicts between the old design/implementation and the latest instructions

1. **ID card front + back (backend contract).** The brief requires two uploads;
   both the form and `RegisterForm` accept exactly one `idCard` file. This
   cannot be implemented frontend-only. Options: (a) owner approves a small
   backend change to accept front/back fields; (b) Phase 2 builds the two-slot
   UI but submits within the current contract (not honestly possible — one file
   would be dropped); (c) defer front/back. **Decision needed before Phase 2.**
2. **Sign-up verification order (backend flow).** The brief's order —
   register → pending review → approval → email OTP → Sign Up Successful — is
   the reverse of the implemented order (register → phone OTP → email link →
   pending review → approval) and includes an OTP step the backend does not
   have. Frontend-only Phase 2 can restyle every page, and can add an
   "approved" success state on the Pending page driven by the existing status
   endpoint, but adopting the brief's order requires a separately approved
   backend change. **Decision needed.**
3. **Forgot password (frontend-only — no conflict with the backend).** The
   brief's four steps (email → OTP page with resend countdown → New + Retype
   page → success) can be built on the existing two endpoints, because the
   backend checks the OTP only when the new password is submitted. Resend =
   calling the request endpoint again (subject to its rate limits).
4. **Confirm Password / show-hide toggles:** absent today; frontend-only fixes.
5. **Remember me:** not supported by the server-side session design; the brief
   makes it conditional ("if supported") — omit and say why.
6. **Searchable university/department selectors:** data is already API-driven
   from the public catalogues; making the selectors searchable is frontend-only.
   Whether the catalogues themselves cover all Bangladeshi universities is a
   backend data question, not a frontend one.
7. **Messaging:** fully present in code (route `/messages`, header nav, STOMP
   WebSocket, REST rooms API) while the latest product scope removes it. Per
   the owner's own rule (do not delete working features without approval) and
   the phased plan, Phase 2 does not touch it; removing the nav entry belongs
   to the Community phase, and removing the feature itself needs its own
   explicit decision.
8. **Admin basename defect** (§2): must be fixed before any admin UI work.
9. **Design corrections (blue marks) — readings.** Certain from the text:
   remove the `community_home_sidebar_collapsed` deliverable; remove the
   `repository_other_disabled` screen; repository navigation becomes a
   **My Projects** parent with repositories as child items, the open one
   highlighted, plus a **Contributors** section (sample-labelled); Student
   Profile gains **Sign Out** at the bottom. High-confidence readings of the
   marks: Workspace sidebar loses Teams / Project Showcase / Saved Projects;
   the unauthorized screen loses the "What you will see after authorization"
   card; Community Home loses the Community levels card, the Followed sectors
   card, and the Trending sectors card; Student Profile loses the "Profile
   settings" button and the "Students in your department" card; the authorized
   Workspace sidebar loses the item under "Connected as …". **Ambiguous —
   confirmation requested, nothing deleted on a guess:** (i) the
   Community/Workspace switcher pills in the header are marked on nearly every
   screen — remove the switcher and rely on the Workspace shortcut icon?
   (ii) the hamburger is circled/marked on several screens — with the collapsed
   screen removed, is desktop sidebar collapse itself removed (mobile drawer
   kept)? (iii) the Notifications item in the Community sidebar is struck
   through — remove from the sidebar only (header bell remains)? (iv) the long
   mark across the Student Profile tabs row — does it target the tabs or the
   "CSE Student · University of Dhaka · Dhaka, Bangladesh" line? (v) a mark at
   the top-right of Repository Open where Close Repository sits — the text
   explicitly requires Close Repository, so the recommendation is to keep it
   and treat the mark as stray; (vi) a mark in the empty area below the comment
   list on Repository Comments — possibly indicating the composer belongs
   there. The Admin Dashboard screenshot carries no marks and is treated as
   approved unchanged.

## 8. Safe implementation plan for Phase 2 — Authentication

Preconditions (owner decisions): conflict 1 (ID front/back), conflict 2
(sign-up flow order), and the ambiguous blue marks in §7.9. On confirmation,
first apply the design corrections to the showcase prototype and re-issue the
affected screenshots, then:

1. **Student Login** — rebuild on the existing `POST /auth/login` contract and
   the approved design: logo, email, password with show/hide, inline validation,
   401/403 error states as today, success → Community Home (`/feed` for now;
   the mode-based home arrives with the Community phase). No remember-me
   (unsupported). No fabricated success states.
2. **Student Sign Up** — rebuild the wizard on the existing register contract:
   add Confirm Password + match validation; searchable university/department
   selectors over the existing `/public/*` catalogues (labelled as the live
   catalogue, never a hardcoded list); upload UI with preview / replace /
   remove, accepted formats and size guidance, within the contract decided
   under conflict 1; helper text that the name and ID number must match the ID
   card; keep phone, batch, address and profile photo (the backend requires
   them — the brief's shorter field list cannot drop them without a backend
   change).
3. **Sign Up OTP** — restyle `/otp` (phone OTP, existing endpoints, existing
   60-second resend cooldown shown as a countdown).
4. **Verification Pending** — restyle `/pending` on the existing status
   endpoint; add the **Sign Up Successful** state when the status becomes
   APPROVED, with a "Go to Student Login" button (frontend-only). The brief's
   post-approval email-OTP step is included only if conflict 2 is decided for
   a backend change.
5. **Forgot Password** — split the single page into the brief's steps without
   backend changes: email page → OTP page (resend with countdown) → Change
   Password page with exactly New Password + Retype New Password, show/hide,
   mismatch validation → success state → back to Login. One `request` call at
   step 1 (and per resend), one `confirm` call at the end carrying the OTP and
   the new password, exactly as the backend expects.
6. **Admin Login** — restyle the existing login → TOTP → change-password flow;
   no behavioural changes, no public admin sign-up, TOTP and RBAC untouched.
7. **Verification for every page:** `npm run typecheck`, `npm run build`,
   Vitest, Playwright smoke, plus a manual walkthrough of each flow. Live
   end-to-end runs against MySQL remain blocked in this sandbox (documented
   Java→MySQL interception), so live verification stays a Windows step and will
   be reported as such — nothing will be claimed as live-verified that was not.

Explicit non-goals for Phase 2: no backend changes, no database changes, no
route removals, no auth/security behaviour changes, no messaging changes, no
new packages without approval.
