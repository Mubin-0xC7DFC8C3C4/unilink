# Phase 1 — Frontend redesign audit: LinkedIn × GitHub hybrid (2026-11-18)

**Scope:** read-only inspection of `~/workspace/unilink` at HEAD `0a300c8`
(verified via `git log`; working tree clean). **No files were modified and no
code was written in this phase** — this report is the only new file
(`docs/phase-reports/09-frontend-redesign-audit.md`). No builds or tests were
run, except one live browser probe of the dev server (see §2). Four inventory
workers inspected the tree in parallel; counts below are what was actually
inspected.

**Stack (verified, `frontend/package.json`):** React `^18.3.1`,
react-router-dom `^6.27.0`, `@tanstack/react-query` `^5.59.0`,
react-hook-form `^7.53.0`, `@hookform/resolvers` `^3.9.0`, zod `^3.23.8`,
Vite `^5.4.9`, TypeScript `^5.6.3`. **No icon library, no CSS/UI library, no
Tailwind.** Note: the master prompt §10 says "TanStack Router" — that is
wrong; the app uses react-router-dom 6.

---

## 1. Headline finding — admin in-app navigation is broken in the browser (verified live)

This is the single most important result of the audit, and it was **not**
in the brief. Everything else below is inventory; this is a live defect.

**The defect.** `frontend/src/admin/main.tsx:21` sets
`BrowserRouter basename="/admin"`, and every in-app link/navigation in the
admin app uses an absolute `/admin/…` target anyway: the sidebar `NAV`
array (`AdminLayout.tsx:6`), `nav('/admin/totp')` (`AdminLogin.tsx:10`),
`nav('/admin/change-password')` / `nav('/admin/login')`
(`AdminLayout.tsx:11,14`, `Totp.tsx:16-17`, `AdminChangePassword.tsx:12`),
and the `AdminNeedLogin` link (`admin/main.tsx:19`). React Router v6
prepends the basename to absolute targets, so each resolves to a doubled
`/admin/admin/…` URL.

**Verified live (main agent, Playwright probe against the dev server,
2026-11-18 ~21:54 +06):**

- Visiting `/admin/verifications` while signed out renders
  `AdminNeedLogin`, and its "Admin log in" link has
  `href="/admin/admin/login"`.
- Clicking it navigates to `/admin/admin/login` and renders "Admin sign-in
  required" again — a dead loop. The login form is reachable only by
  typing `/admin/login` directly.
- The student app is unaffected (no basename): every header link on
  `/feed` probed correct (`/feed`, `/events`, `/teams`, `/messages`,
  `/search`, `/notifications`, `/login`, `/signup`).

**Consequences (mechanism proven for links; signed-in screens need a live
session to observe on Windows):**

- Every admin sidebar link double-prefixes, so a signed-in admin clicking
  one lands on the catch-all `*` route ("Page not found" inside
  `AdminLayout`) instead of the target page.
- The admin login flow uses the same `nav()` calls (`/admin/totp` after
  login, `/admin/change-password` after TOTP when a password change is
  required), so it resolves to the same broken URLs. The login page
  itself loads directly (Playwright smoke 2/2 checks only the heading).
- The mandatory password-change redirect (`AdminLayout.tsx:9-11`) uses
  `nav('/admin/change-password')`, so an admin who must change their
  password is redirected to the broken URL, not the form.
- The must-change exemption is additionally dead in code:
  `AdminLayout.tsx:11` exempts `loc.pathname !== '/change-password'`,
  but with the basename the location pathname is
  `/admin/change-password`, so the exemption never matches (contrast the
  student equivalent, which correctly checks `'/settings'`,
  `Layout.tsx:12`). As written it settles on the NotFound screen rather
  than looping (the replace-to-same-URL produces no further location
  change), but the exemption is still dead.
- Why this was never caught: the dev fallback serves `admin.html` for
  any `/admin/*` URL (including the doubled ones), so the failure is
  inside the router, not a server 404; and no admin workflow has ever
  been exercised end-to-end in a browser (Playwright smoke checks only
  the login heading; no live backend session existed in the sandbox).

**Fix (Phase 3/6, not this phase):** drop `basename="/admin"` **or** drop
the `/admin` prefix from every admin link/navigation — one of the two, not
both. Verify live with a signed-in admin session on Windows before
declaring it fixed. Do not "fix" the exemption or the links on assumption
without that check.

---

## 2. What the main agent verified personally

The coordinator's report below was spot-checked against the code and the
live dev server; these items were verified by the main agent directly:

- `git log` HEAD `0a300c8`, working tree clean; `frontend/src` = 58 files
  (51 `.tsx`).
- `package.json` versions, route inventories (`main.tsx:22-29`,
  `admin/main.tsx:21-24`), `tokens.css`, `Layout.tsx`, `ThemeContext.tsx`
  / `theme.ts` — all as reported in §3–§4.
- Admin basename double-prefix: **confirmed live** in the browser (§1).
- `style={{` occurrences: **147** (exact grep count).
  `<svg` occurrences in `src/`: **0**.
- `GET /notifications/unread-count`, skill endpoints
  (`POST|DELETE /profiles/me/skills`), and student `GET /announcements`:
  **no page calls any of them** (grep over `frontend/src`).
- Attachments: `Feed.tsx` has none; `PostDetail.tsx` has owner-only
  upload + list (`PostDetail.tsx:10,23,43-45`).
- Admin `QueryClient`: `retry:1`, no `staleTime`
  (`admin/main.tsx:16`); student: `retry:1, staleTime:30_000`
  (`main.tsx:18`).
- The live probe scripts lived in `/tmp` only and were deleted after use;
  no repo file was touched.

---

## 3. Architecture summary

### Entries — two separate SPAs, two routers

- Student: `frontend/index.html:4` → `src/main.tsx:22`
  (`BrowserRouter`, no basename). Admin: `frontend/admin.html:4` →
  `src/admin/main.tsx:21` (`BrowserRouter basename="/admin"`). Build emits
  both: `frontend/vite.config.ts:29`
  (`rollupOptions.input: {main: index.html, admin: admin.html}`). Both
  mount into `<div id="root">` (`index.html:4`, `admin.html:4`).
- Component-based `<Routes>` in both apps — no `createBrowserRouter`, no
  loaders/actions, no lazy loading, no `<Navigate>` redirect routes at
  all; all "redirects" are imperative `useNavigate` or full-page
  `window.location.assign` on 401.

### Router structure

- Student (`src/main.tsx:22-29`): one layout route `<Layout/>` wrapping
  all 20 routes + catch-all, so even 404 renders the header. Admin
  (`src/admin/main.tsx:22-23`): login/TOTP outside the guard/layout; all
  other routes nested under one pathless
  `<Route element={<AdminGuard><AdminLayout/></AdminGuard>}>`, so the
  sidebar renders on every guarded page including the catch-all.
- No nested sub-routes anywhere. Tabbed UIs (Feed levels, Events
  upcoming/past, MasterData tabs) are component state, not routes — not
  deep-linkable or bookmarkable.
- Student `QueryClient`: `retry:1, staleTime:30_000` (`src/main.tsx:18`);
  admin has a separate `QueryClient` (`retry:1`, no staleTime).

### Layouts

- Student `src/app/Layout.tsx:6`: header nav (Feed/Events/Teams/Messages/
  Search, Notifications, Profile `/profile/me`, Settings), theme toggle,
  logout `POST /auth/logout` → `/login` (`Layout.tsx:19-25`). No sidebars.
- Admin `src/admin/AdminLayout.tsx:7`: sidebar from hardcoded `NAV`
  array of 11 absolute `/admin/…` links (`AdminLayout.tsx:8,15`); logout
  `POST /admin/auth/logout` → `/admin/login` (`AdminLayout.tsx:14`).

### Data layer

- **No typed client layer.** `frontend/src/api/client.ts` contains no
  per-operation functions — only generic transport helpers (`api`,
  `apiGet`, `apiPost`, `apiPut`, `apiPatch`, `apiDelete`), all prefixing
  `/api`, sending `credentials: 'include'`, and attaching `X-XSRF-TOKEN`
  from the `XSRF-TOKEN` cookie (`client.ts:39,47-51`). Every endpoint is
  called inline at the call site via string-template URLs. `apiPatch` is
  exported but never called; `apiPut` is called only in `SettingsPage`.
- `frontend/src/api/types.ts`: `ApiErrorBody`/`ApiError` (`:1-3`),
  `PageResponse<T> {items, nextCursor?, hasMore?}` (`:4`), `Post` (`:5`),
  `EventItem` (`:6`), `UserProfile` (`:7`), `Room` (`:8`),
  `NotificationItem` (`:9`), `VerificationItem` (`:10`),
  `ProjectItem`/`EducationItem`/`AchievementItem` (`:24-26`),
  `NOTIFICATION_TYPES = ['VERIFICATION_DECISION','REPORT_AUTO_HIDE',
  'TEAM_JOIN_ACCEPTED']` (`:23`). It still carries dead legacy fields
  the backend never sends (`Post.reactionCount`, `Post.author`,
  `Post.content`, `EventItem.date`).
- TanStack Query: almost every `useQuery` overrides `retry:false`.
  Invalidation is by string query key only (`['feed']`,
  `['admin-verifications']`, `['teams']`); no cache normalisation, no
  `useInfiniteQuery` (feed cursor is manual state). Several mutations
  have **no invalidation at all** (PostDetail react/bookmark/report/
  appeal; EventDetail RSVP; all SettingsPage mutations; MessagesPage
  send/markRead relies on STOMP echo).

### Auth (`src/app/auth.tsx`)

- Server-side session only; no JWT/token in JS, no `localStorage` auth
  token. Cookie `UL_SESSION`, http-only, SameSite Lax, `secure: false`,
  2 h timeout, JDBC store
  (`backend/src/main/resources/application.yml:24,28,44-47`). Every API
  call sends it (`api/client.ts:39`).
- Session restore on every app load: `GET /api/csrf` (fire-and-forget,
  errors swallowed) then `GET /api/auth/me` or
  `GET /api/admin/auth/me` (`auth.tsx:20-21`). `auth.tsx:29` maps
  `verified: d.state==='VERIFIED'`; `auth.tsx:24` — admin `/me` with
  `mfaVerified===false` forces user to `null`. `ready` gates guards;
  while not ready, `RequireAuth` returns blank `null` (no spinner)
  (`auth.tsx:41`).
- CSRF: readable cookie `XSRF-TOKEN`, read by regex from
  `document.cookie`, sent as `X-XSRF-TOKEN` on **every** `api()` call
  including GETs (`api/client.ts:2,37`). Backend
  `CookieCsrfTokenRepository.withHttpOnlyFalse()`
  (`backend/.../config/SecurityConfig.java:63-64`); `GET /api/csrf`
  returns `{token, headerName}` but the frontend ignores the body and
  relies on the cookie. CSRF is exempt for `/api/auth/**`,
  `/api/admin/auth/**`, `/api/public/**`, `/api/appeals/public`,
  `/actuator/**`, `/ws/**` (`SecurityConfig.java:63-64`).

### Messaging (`src/api/stomp.ts`)

- Dependency-free STOMP 1.2 over native WebSocket, default path
  `/ws/websocket` (`stomp.ts:71-75`). No auth header/token in the
  CONNECT frame — only `accept-version: 1.2`, `host`,
  `heart-beat: 10000,10000` (`stomp.ts:130-132`); auth is the
  `UL_SESSION` cookie on the handshake. Only used by
  `MessagesPage.tsx:41,54,76`: subscribe `/topic/room.${publicId}`,
  send `/app/rooms.${publicId}.send` with `{body, idempotencyKey}`.
  Heartbeat 10 s, dead-after 25 s, reconnect backoff 1 s → 30 s max,
  subscriptions re-sent on reconnect (`stomp.ts:22-30,120-135`).
  `send()` returns false while disconnected and the caller falls back
  to REST (`MessagesPage.tsx:76-77`).

### Theme (`src/app/ThemeContext.tsx`, `src/design/theme.ts`, `src/design/tokens.css`)

- `ThemeProvider` wraps both apps (`src/main.tsx:22`,
  `src/admin/main.tsx:21`); `useTheme`/`toggle` in
  `ThemeContext.tsx:4-5`; `applyTheme` sets
  `document.documentElement[data-theme]` (`theme.ts:3`). `theme.ts`
  contains logic only, no tokens. Only global stylesheet is
  `design/tokens.css`, imported by `main.tsx:4` and `admin/main.tsx:4`.
  There is **no `index.css`**.

---

## 4. Page inventory — all 36 routes

**Counts inspected:** 21 student route entries (incl. index + catch-all),
15 admin route entries = 36 total. 20 student page components, 14 admin
page components — **every page component is routed; zero orphans, zero
routes pointing at a missing component.**

### Student app (`src/main.tsx:22-29`; 🔒 = guarded by `Guard`)

| Path | Component | Purpose / key features | State |
|---|---|---|---|
| `/` (index) | `features/auth/Landing.tsx:3` | Static landing; hardcoded `StatStrip` literals ("4 levels", "6 sectors", "Checked" — not fetched, `Landing.tsx:4`); 3-step verification explainer | COMPLETE (static by design) |
| `/signup` | `features/auth/SignUp.tsx:11` | 3-step form, zod; catalog dropdowns `GET /public/universities\|departments\|batches` (`:16-18`); multipart `POST /auth/register` with ID-card image + profile photo (`:21-30`); stores id in `sessionStorage` (`unilink:otpUserId`), → `/otp` | COMPLETE |
| `/otp` | `features/auth/OtpStep.tsx:9` | `POST /auth/verify-phone {userId, otp}` + resend with 60 s cooldown (`:14-15`); userId from sessionStorage only; absent → "sign up again" (`:16-17`); success → `/pending` | COMPLETE |
| `/verify-email` | `features/auth/EmailVerify.tsx:3` | Reads `?token=`, `GET /auth/verify-email?token=…` (`:4`); ok → link to `/pending`; no resend-email UI | COMPLETE |
| `/pending` 🔒-exempt | `features/auth/Pending.tsx:3` | `GET /auth/verification/status` (`:5`); shows state + rejection reason; if REJECTED, resubmit `POST /auth/verification/resubmit` (empty FormData, `:6`) | COMPLETE |
| `/login` | `features/auth/Login.tsx:5` | `POST /auth/login` (`:7`); 401 rendered as "Incorrect email or password" (`:17`); `mustChangePassword` → `/settings`, else `/feed` (`:7-8`) | COMPLETE |
| `/forgot-password` | `features/auth/ForgotPassword.tsx:8` | 3-step: `POST /auth/password-reset/request`, then `POST /auth/password-reset/confirm {email, otp, newPassword}` with client password rules (`:9-13`) | COMPLETE |
| `/appeals/public` | `features/auth/PublicAppeal.tsx:9` | Unguarded suspension appeal via emailed single-use `?token=`; `POST /appeals/public?token=…` with placeholder targetType/targetId the backend ignores (`:12-13`) | COMPLETE |
| `/feed` 🔒 | `features/feed/Feed.tsx:7` | Level tabs DEPARTMENT/UNIVERSITY/HUB/NATIONAL (`:25`), sector filter + follow/unfollow sector, keyset pagination, composer `POST /posts` (`:23,31-34`). Composer is title+body only — **no attachment upload here** (attachments only on PostDetail) | COMPLETE |
| `/posts/:id` 🔒 | `features/posts/PostDetail.tsx:5` | Post + comments (`GET /posts/:id`, `/posts/:id/comments`), @-mention picker, Like (`POST /reactions`), bookmark/unbookmark, report, owner edit (PATCH)/delete, owner-only attachment upload + list, owner-only appeal when HIDDEN (`:6-28,38-54`) | COMPLETE |
| `/events` 🔒 | `features/events/EventsPage.tsx:8` | Upcoming/past tabs (`GET /events/upcoming`, `/events?past=true`), create-event form `POST /events` (`:12-21`), link to `/calendar` | COMPLETE |
| `/events/:id` 🔒 | `features/events/EventDetail.tsx:3` | `GET /events/:id`; RSVP INTERESTED/GOING, team-request, owner edit (PATCH)/delete (`:6-16`) | COMPLETE |
| `/calendar` 🔒 | `features/events/CalendarPage.tsx:11` | Month picker → `GET /calendar?from=&to=` (`:6-10`); renders titles as **plain text, no links to `/events/:id`** (`:14`) | PARTIAL |
| `/teams` 🔒 | `features/teams/TeamsPage.tsx:16` | `GET /teams`; join request; creator-only per-team pending requests + accept/decline (`:6-15`). **No create-team UI** — creation only via EventDetail "Request a team" (`EventDetail.tsx:8`) | COMPLETE (as list/join/decide) |
| `/search` 🔒 | `features/search/SearchPage.tsx:7` | Keyword + type (people/posts/all) + sector → `GET /search?q=&type=&sector=`; results link to `/profile/:id`, `/posts/:id` (`:10-22`). Lead says "by keyword, sector or skill" but **no skill input/filter exists** | PARTIAL |
| `/profile/:id` 🔒 | `features/profile/ProfilePage.tsx:41` | `GET /profiles/me` or `/profiles/:id`; follow/unfollow; Verified badge; activity list; portfolio (projects/education/achievements) with owner add/edit/delete (`:7-40,42-55`). `/profile/me` is not a separate route — `id==='me'` is special-cased (`:41`) | COMPLETE |
| `/messages` 🔒 | `features/messages/MessagesPage.tsx:22` | Room list/search, dept + university room openers, temp-room create/invite/close, STOMP live chat with REST fallback, read ticks, attachments (`:32-115`). Room-admin inferred from `memberIds[0]` (heuristic — code comment admits no creatorId in RoomDto, `:113-118`); invite is by **numeric user id typed by hand** (`:124`) | PARTIAL |
| `/notifications` 🔒 | `features/notifications/NotificationsPage.tsx:7` | `GET /notifications`; mark-read / mark-all-read; per-type email+in-app preferences, one `POST /notifications/preferences` per type (`:10-24`) | COMPLETE |
| `/saved` 🔒 | `features/posts/SavedPage.tsx:2` | 2-line `ListPage` wrapper over `GET /bookmarks`; every row links to `/posts/${targetId}` — **event bookmarks would link to a post URL** (lead claims "Posts and events") | PARTIAL |
| `/settings` 🔒 | `features/settings/SettingsPage.tsx:3` | Change password `POST /auth/change-password`, photo/cover upload `PUT /profiles/me/photo\|cover`, privacy (phoneVisible), theme toggle, account delete `POST /me/delete` (`:4-20`). **Profile save sends only `{bio}`** (`:4`) — headline/links/skills shown on ProfilePage have **no edit UI anywhere** | PARTIAL |
| `*` | `NotFound` inline, `src/main.tsx:19,29` | "Page not found" inside Layout | EXISTS |

### Admin app (`src/admin/main.tsx:21-24`; basename `/admin`)

| Path | Component | Purpose / key features | State |
|---|---|---|---|
| `/admin` (index) | `admin/pages/Dashboard.tsx:3` | `GET /admin/dashboard`; 4 stats + new users today (`:3-6`) | COMPLETE |
| `/admin/login` | `admin/pages/AdminLogin.tsx:8` | `POST /admin/auth/login`, password ≥12; success → `/admin/totp` with `{stage, otpauthUrl}` in location state (`:10`) | COMPLETE |
| `/admin/totp` | `admin/pages/Totp.tsx:9` | Enrol (`POST /admin/auth/totp/enrol`, returns 10 recovery codes shown once) then verify (`POST /admin/auth/totp/verify`, recovery code accepted); success → `/admin` or `/admin/change-password` if mustChangePassword (`:14-17`). **otpauthUrl shown as raw text, no QR code rendered** (`:22-24`) | PARTIAL |
| `/admin/change-password` 🔒 | `admin/pages/AdminChangePassword.tsx:9` | `POST /admin/auth/change-password`; success signs out everywhere → `/admin/login` (`:11-12`) | COMPLETE |
| `/admin/verifications` 🔒 | `admin/pages/Verifications.tsx:3` | `GET /admin/verification-queue?status=`; review pane with OCR score/text, previous attempts, duplicate-phone flag, ID image via `<img src="/api/admin/verification/:id/id-image">` (`:31-38`); approve/reject (reason mandatory for reject), keyboard "A" to approve (`:8`) | COMPLETE |
| `/admin/users` 🔒 | `admin/pages/Users.tsx:3` | Search + `GET /admin/users?q=`; SUSPEND/BAN/REACTIVATE/REVOKE/FORCE_RESET with mandatory reason; SUSPEND/BAN buttons hidden unless `role==='PLATFORM_ADMIN'` (client-side only, `:7-8`); FORCE_RESET shows one-time temp password (`:9-13`) | COMPLETE |
| `/admin/moderation` 🔒 | `admin/pages/Moderation.tsx:6` | `GET /admin/moderation-queue`; decide `POST /admin/moderation/decide {targetType,targetId,action,reason}` — HIDE/REMOVE/RESTORE/EDIT_REQUEST only, no WARN (`:4-9`) | COMPLETE |
| `/admin/appeals` 🔒 | `admin/pages/Appeals.tsx:5` | `GET /admin/appeals`; decide `POST /admin/appeals/:id/decide {ACCEPT\|REJECT, reason}` — Uphold=REJECT, Overturn=ACCEPT (`:4-8`) | COMPLETE |
| `/admin/events` 🔒 | `admin/pages/AdminEvents.tsx:7` | `GET /admin/events`; PATCH REMOVE/HIDE/RESTORE, close registration, fix deadline/visibility (`:4-12`) | COMPLETE |
| `/admin/announcements` 🔒 | `admin/pages/Announcements.tsx:6` | `GET /admin/announcements`; create (scope GLOBAL/UNIVERSITY required), edit (PUT), delete=deactivate (`:4-16`) | COMPLETE |
| `/admin/master-data` 🔒 | `admin/pages/MasterData.tsx:9` | 5 tabs (universities, department-catalog, departments, batches, sectors) reading `GET /public/*`; create/deactivate/rename via `/admin/master-data/*`; department-catalog tab read-only by design (`:4-8,45-53`) | COMPLETE |
| `/admin/moderators` 🔒 | `admin/pages/Moderators.tsx:6` | `GET /admin/moderators`; create (email+university+password ≥12), assign university (PUT), deactivate, reset-credentials with one-time temp password (`:10-24`) | COMPLETE |
| `/admin/analytics` 🔒 | `admin/pages/Analytics.tsx:2` | `GET /admin/analytics`; 3 stats; **events-per-sector and 30-day user growth rendered as raw `JSON.stringify` dumps, not a table/chart** (`:9`) | PARTIAL |
| `/admin/audit` 🔒 | `admin/pages/Audit.tsx:10` | `GET /admin/audit-logs?limit=200` (client-side action filter, since backend takes only `?limit`); CSV export link `/api/admin/audit-logs/export?limit=200`, explicitly labelled unfiltered (`:4-15`) | COMPLETE |
| `/admin/*` | `NotFound` inline, `src/admin/main.tsx:18,23` | "Page not found" inside AdminLayout | EXISTS |

Non-page components (no route): `admin/AdminList.tsx:3` and
`features/shared.tsx:5` (generic list wrappers), `app/auth.tsx`,
`app/ThemeContext.tsx`, `design/primitives.tsx`, `api/client.ts`,
`api/stomp.ts`, `api/types.ts`.

---

## 5. Component inventory — reusable vs needs redesign

### `design/primitives.tsx` — 17 exports

| Export | Line | Reusable? | Notes |
|---|---|---|---|
| `Button` | :2 | YES — genuinely reusable; ~35 files | `variant?: 'outline' \| 'primary'` only; no size variant |
| `Field` | :3 | YES; all form pages | label + error wrapper |
| `Input` | :7 | YES, with caveat | `forwardRef` bare `<input>` with **no className**; styled only as `.field` descendant (`tokens.css:45`) — unstyled outside `Field` |
| `StatStrip` | :8 | YES | 3 pages: Landing, admin Dashboard, Analytics |
| `RuledList` | :9 | YES | 25 occurrences |
| `EmptyState` | :10 | YES | 21 occurrences |
| `Skeleton` | :11 | YES | 37 occurrences; two fixed bars, label "Loading" |
| `Pill` | :12 | YES, narrow | 2 pages: Feed, ProfilePage |
| `VerifiedBadge` | :13 | One-off | Hardcoded `✓ Verified` pill |
| `Modal` | :14 | YES in principle | **Only 1 production call site**: SettingsPage |
| `Toast` | :15 | **DEAD — zero production call sites** | Definition only |
| `ApiUnavailable` | :20 | YES | Renders "Not found"; 3 pages |
| `NotFoundState` | :22 | **DEAD — zero call sites** | Near-duplicate of `ApiUnavailable` |
| `EndpointUnavailable` | :27 | **DEAD in production** (only `src/test/error-handling.test.tsx`) | Third near-duplicate; file comment says "Currently no call site uses it" |
| `SessionExpiredNotice` | :30 | Page-specific (login pages) | Reads/clears `sessionStorage` key `unilink:session-expired` |
| `PageHeader` | :31 | YES — near-universal page header | eyebrow + `h1.display` + lead |
| `ErrorState` | :32 | YES | 41 occurrences |

### `features/shared.tsx` — 2 exports

- `useList<T>(key, path)` (`:4`) — thin `useQuery` wrapper
  (`retry: false`). No direct callers outside `shared.tsx`; used
  internally by `ListPage`.
- `ListPage<T>` (`:5`) — generic list-page shell (`PageHeader` +
  `Skeleton`/`ErrorState`/`EmptyState` + `render(items)`); normalises
  `data.items ?? data.content ?? data`. 5 production users: SavedPage,
  EventsPage, CalendarPage, TeamsPage, NotificationsPage.
  `admin/AdminList.tsx:7-12` is the admin equivalent.

**Reusable for the redesign:** `Button`, `Field`, `Input` (with class
caveat), `PageHeader`, `EmptyState`, `ErrorState`, `Skeleton`,
`RuledList`, `StatStrip`, `ListPage`, `Modal`, `Pill`.

**Needs redesign / removal:** `Toast` (dead), `NotFoundState` (dead),
`EndpointUnavailable` (dead) — consolidate the three near-identical
alert boxes into one.

**Missing primitives (pages hand-roll them):** Card (raw
`<div className="card-group">`, e.g. `Feed.tsx:33`,
`EventsPage.tsx:14`); Tabs (raw `<button className="btn btn-sm">` tab
lists, e.g. `Feed.tsx:29`, `EventsPage.tsx:13`); Select/Textarea/
Checkbox (raw elements, e.g. `Feed.tsx:28-31`,
`EventsPage.tsx:15-20`); Avatar; Dropdown; Tooltip; Pagination
(hand-rolled Previous/Next, `Feed.tsx:39`).

**Other inconsistencies:** `Button` has no size variant, yet a `.btn-sm`
class exists in CSS (`tokens.css:42`) — pages bypass the primitive with
raw `<button className="btn btn-sm">` (`Feed.tsx:30`,
`Layout.tsx:23-25`, `EventsPage.tsx:13`). **147 `style={{`
occurrences** across `src/` (verified by main-agent grep; e.g. 8 in
`Feed.tsx`, 7 in `EventsPage.tsx`); `EmptyState` `padding:'32px 0'`,
`PageHeader` `paddingTop:32`, `Skeleton` heights 24/16 are hardcoded
inline literals. Pages hardcode `style={{maxWidth:480}}` / `720` (e.g.
`Login.tsx:10`, `SettingsPage.tsx:21`, `AdminLogin.tsx:11`) — no
form-width token.

---

## 6. Design-system audit

### Actual tokens (`design/tokens.css:2-16`; `theme.ts` has logic only)

- **Colors, light** (`tokens.css:3-5`): `--background #fdfdfb`,
  `--surface #ffffff`, `--surface-muted #f2f1ed`, `--border #e4e2dc`,
  `--text-primary #14140f`, `--text-secondary #5b5951`,
  `--text-muted #5b5951`, `--primary #14140f`, `--on-primary #fdfdfb`,
  `--scrim #a8a8a5`.
- **Colors, dark** (`tokens.css:13-15`): `--background #0e0e0c`,
  `--surface #171715`, `--surface-muted #22221e`, `--border #262622`,
  `--text-primary #f5f4f0`, `--text-secondary #a9a69c`,
  `--text-muted #a9a69c`, `--primary #f5f4f0`, `--on-primary #0e0e0c`,
  `--scrim #a8a8a5` (unchanged).
- **Font family** (`tokens.css:6`): `system-ui, -apple-system, "Segoe UI",
  Roboto, "Noto Sans Bengali", "Noto Sans", sans-serif`. No font-weight
  tokens; weights hardcoded (`.display` 500, `.headline` 400, `.title`
  500, `.lead` 300, `tokens.css:26-29,47`). **Inter is NOT used.**
- **Type scale** (`tokens.css:7`): `--display 2.5rem` (40px),
  `--headline 1.5rem` (24px), `--title 1.125rem` (18px),
  `--body-lg 1.125rem` (18px), `--body-md 0.875rem` (14px),
  `--body-sm 0.75rem` (12px), `--label-caps 0.6875rem` (11px),
  `--stat 1.75rem` (28px). `body` defaults to `var(--body-md)` 14px
  (`tokens.css:19`).
- **Spacing** (`tokens.css:8`): 4/8/16/24/32/48px. **Radius**
  (`tokens.css:10`): 0/8/12/16/9999px. **Layout** (`tokens.css:9`):
  `--container-max 1000px`, `--header-height 72px`.
- **Shadows: MISSING** — no `--shadow*` token and zero `box-shadow`
  occurrences anywhere in `src/`. Cards are flat 1px borders.
- **Semantic colors: MISSING** — no success/error/warning/danger/info
  tokens of any kind.

### Target palette (§4) comparison

| Target | Status | Evidence |
|---|---|---|
| Primary Navy #172B45 | **MISSING** | No navy anywhere; `--primary` is near-black `#14140f` / off-white `#f5f4f0` (`tokens.css:5,15`) |
| Primary Blue #2878C8 | **MISSING** | No blue token; links inherit colour (`tokens.css:20` `a { color: inherit; }`) |
| Success Green #23834C | **MISSING** | No green token; no success state colour exists |
| Light Background #F3F5F8 | **CLOSE** | Actual `--background #fdfdfb` (`tokens.css:3`) — warm off-white, not cool grey-blue |
| White Surface #FFFFFF | **EXISTS** | `--surface #ffffff` light (`tokens.css:3`) |
| Dark Surface #202938 | **CLOSE** | Dark `--surface #171715` / `--surface-muted #22221e` (`tokens.css:13`) — warm near-black, not slate |
| Border #E2E8F0 | **CLOSE** | Actual `#e4e2dc` light / `#262622` dark — warm grey vs cool grey-blue |
| Secondary Text #64748B | **CLOSE** | Actual `#5b5951` light / `#a9a69c` dark — warm grey-brown vs slate |
| Warning #D99B32 | **MISSING** | No warning token; warning banners hardcode `#fff3cd`/`#5c3d00` |

The current palette is a warm monochrome (near-black/off-white/warm
greys, from the `pic.docx` transcription in DESIGN.md), not the navy/blue
target. Adopting §4 is a full re-tokening, not a tweak — and it conflicts
with the source-document tokens, so it needs explicit owner approval
(see §10).

### Typography vs target scale

Page titles: actual `.display` is **40px** (32px at ≤720px,
`tokens.css:26,73`) — **above** the 24–30px target. Section: `.title`
18px fits 17–20px but `.headline` 24px exceeds it. Body: `.body-md`
14px fits the low end of 14–16px; `.body-lg` is 18px. Labels:
`.label-caps` is **11px, below** the 12–13px target; `.body-sm` 12px
fits.

### Light/dark mode — EXISTS and is real

`[data-theme="dark"]` (`tokens.css:12-16`) overrides every colour
token. Toggle UI in three places: student header button
`Theme: {theme}` (`src/app/Layout.tsx:23`), admin rail button
(`src/admin/AdminLayout.tsx:15`), Settings "Switch to dark/light
theme" (`src/features/settings/SettingsPage.tsx:34`). **Persistence
EXISTS via localStorage, key `unilink-theme-hint`** — read in
`initialTheme()` (`theme.ts:2`), written in `applyTheme()`
(`theme.ts:3`); first visit falls back to `prefers-color-scheme`.
**Caveats:** `SettingsPage.tsx:34` tells users "Your choice is saved to
your account when signed in" — no server-side theme save exists (no
theme field/call in `src/api/` or `SettingsPage.tsx`). Neither
`index.html` nor `admin.html` contains any theme script (only
charset/viewport/title, `index.html:3`), so no pre-paint theme
application — a flash of the default theme on load is possible (not
verified live). Theme-ignoring components: the three hardcoded warning
banners (`#fff3cd` bg / `#5c3d00` text) render identical light-yellow in
dark mode: `src/app/auth.tsx:38`,
`src/features/settings/SettingsPage.tsx:22`,
`src/admin/pages/AdminChangePassword.tsx:14`.

### Responsive behaviour — EXISTS but minimal

**One breakpoint only.** `tokens.css:73` is the only layout media
query: `@media (max-width: 720px)` — `.split` 2-col → 1-col,
`.stat-strip` stacks, `.header nav { display: none; }`,
`.admin-rail` 220px → 160px, `.display` 40px → 32px. The only other
query is `prefers-reduced-motion` (`tokens.css:23`). No 768/1024/tablet
breakpoints exist. Viewport meta present (`index.html:3`,
`admin.html:3`).

**Missing:** mobile nav — **at ≤720px the student header nav is hidden
with no replacement** (no hamburger/menu component; no "menu" string in
`Layout.tsx`/`AdminLayout.tsx`); remaining header items are
Notifications/Profile/Settings/Sign out (`Layout.tsx:21-25`). Admin
rail never collapses, only narrows to 160px. No responsive table
handling (`.table` has no overflow wrapper, `tokens.css:65-66`).

### Icons — no icon library; text glyphs only

Zero `<svg` occurrences in `src/` (verified by main-agent grep); the
only "icon" hit in `package-lock.json` is transitive `iconv-lite`.
Icons are done with text characters and one emoji: `✓` checkmark
(`VerifiedBadge`, `primitives.tsx:13`; read-receipt ticks `✓`/`✓✓`,
`MessagesPage.tsx:19`); `→` arrow glyph in links/buttons
(`Landing.tsx:3`, `SignUp.tsx:48`, `EmailVerify.tsx:5`, `Feed.tsx:39`,
`EventsPage.tsx:23`, `ProfilePage.tsx:52`); `📎` paperclip emoji, the
only emoji in production `src/` (`MessagesPage.tsx:126`). Everything
else is plain text: "Notifications" link instead of a bell
(`Layout.tsx:21`), text "Close" instead of × (`primitives.tsx:14`),
text "Theme: light/dark" toggle (`Layout.tsx:23`).

### Token-rule violations

`tokens.css:1` says it is "the ONLY file that may contain raw hex
values", and `scripts/check-tokens.mjs` fails the build on any hex in
`.ts/.tsx/.css/.html` outside it. Three files violate it with identical
`#fff3cd`/`#5c3d00` warning banners: `src/app/auth.tsx:38`,
`src/features/settings/SettingsPage.tsx:22`,
`src/admin/pages/AdminChangePassword.tsx:14` (also theme-ignoring;
also the only `fontSize` in the codebase, `auth.tsx:38`).
`.field-error` is `var(--text-primary)` with an `"Error: "` text prefix
(`tokens.css:46-47`) — errors are indistinguishable by colour.
`.stat-strip` uses `--radius-none` (0) explicitly (`tokens.css:52`)
while every other surface uses 8–16px radius.

---

## 7. API integration inventory

### Frontend call inventory (reconstructed from call sites — `client.ts` has no per-operation functions)

**Auth/session:** `GET /csrf` (`auth.tsx:20`);
`GET /auth/me` (`auth.tsx:21`; also `['me']` queries in EventDetail,
PostDetail, MessagesPage, TeamsPage); `GET /admin/auth/me`
(`admin/main.tsx`); `POST /auth/register` multipart
(`SignUp.tsx:25`); `POST /auth/verify-phone`,
`POST /auth/otp/resend` (`OtpStep.tsx:12-13`);
`GET /auth/verify-email?token=` (`EmailVerify.tsx:4`);
`POST /auth/login` (`Login.tsx:7`); `POST /auth/logout`
(`Layout.tsx:17`); `POST /auth/password-reset/request|confirm`
(`ForgotPassword.tsx:9-10`); `POST /auth/change-password`
(`SettingsPage.tsx:11`); `GET /auth/verification/status`,
`POST /auth/verification/resubmit` (`Pending.tsx:4-5`);
`POST /me/delete {password}` (`SettingsPage.tsx:17`).

**Admin auth:** `POST /admin/auth/login` (`AdminLogin.tsx:10`);
`POST /admin/auth/totp/enrol|verify` (`Totp.tsx:13-14`);
`POST /admin/auth/change-password` (`AdminChangePassword.tsx:11`);
`POST /admin/auth/logout` (`AdminLayout.tsx:14`).

**Public catalog:** `GET /public/universities|departments|
department-catalog|batches|sectors` (SignUp, Announcements, MasterData,
Moderators, Feed, SearchPage).

**Profiles/portfolio/follow:** `GET /profiles/me|{id}`;
`PUT /profiles/me {bio}` and `{phoneVisible}` (two separate mutations,
`SettingsPage.tsx:4-5`); `PUT /profiles/me/photo|cover` multipart
(`:19-20`); `GET /profiles/me/activity`,
`GET /profiles/{id}/activity`;
`GET /profiles/{userId}/projects|education|achievements` (numeric id,
never literal `me`); full CRUD
`POST|PUT|DELETE /profiles/me/{projects|education|achievements}[/{id}]`
(`ProfilePage.tsx:20,22`); `POST|DELETE /profiles/me/sectors/{sectorId}`
(`Feed.tsx:20-21`); `POST|DELETE /profiles/{id}/follow`
(`ProfilePage.tsx:46-47`).

**Feed/posts:** `GET /feed?level&sector&cursor`,
`GET /community/context` (`Feed.tsx:14,17`);
`POST /posts {title, body, visibility, sectorIds}` (`Feed.tsx:19`);
`GET|PATCH|DELETE /posts/{id}`; `GET|POST /posts/{id}/comments {body,
mentionUserIds}`; `GET|POST /posts/{id}/attachments` (multipart);
`GET /posts/{id}/attachments/{attachmentId}` via raw
`<a href="/api/posts/…">`, not `api()` (`PostDetail.tsx:44`);
`POST /reactions?reaction={type} {targetType:'POST', targetId}`;
`POST /bookmarks {targetType:'POST', targetId}`,
`DELETE /bookmarks` (JSON body via raw `api()`, not `apiDelete`);
`POST /reports`, `POST /appeals` (`PostDetail.tsx:12-18`).

**Events/calendar:** `POST /events`; `GET /events/upcoming`,
`GET /events?past=true`; `GET|PATCH|DELETE /events/{id}`;
`POST /events/{id}/respond?response=`;
`POST /events/{id}/team-request {}`; `GET /calendar?from&to`.

**Teams:** `GET /teams`; `POST /teams/{teamId}/join {}`;
`GET /teams/{t.id}/requests`;
`POST /teams/requests/{rid}/decide {accept}`.

**Search:** `GET /search?q&type={all|posts|users}[&sector=]`
(`SearchPage.tsx:11`); mention search reuses `type=users` at ≥2 chars
(`PostDetail.tsx:25`).

**Messaging:** `GET /rooms`, `GET /rooms/search?q=`,
`GET /rooms/{publicId}/messages`,
`POST /rooms/{publicId}/messages {body, idempotencyKey}`,
`POST /rooms/{publicId}/attachments` (multipart),
`POST /rooms/{publicId}/read?messageId=`,
`POST /rooms/department|university|temp {name, memberIds:[]}`,
`POST /rooms/{publicId}/invite {userId}`,
`DELETE /rooms/{publicId}/members/{uid}`,
`POST /rooms/{publicId}/close`.

**Notifications:** `GET /notifications`;
`POST /notifications/{id}/read`, `POST /notifications/read-all`,
`POST /notifications/preferences {type, emailEnabled, inAppEnabled}`
× 3 types.

**Public appeals:** `POST /appeals/public?token= {targetType:'USER',
targetId:null, reason}` (`PublicAppeal.tsx:12`).

**Admin:** `GET /admin/dashboard`, `/admin/verification-queue[?status=]`,
`POST /admin/verification/{id}/approve|reject`,
`GET /admin/verification/{id}/id-image` (raw `<img src="/api/…">`, not
via `api()`), `GET /admin/users[?q=]`,
`POST /admin/users/{id}/{action} {reason}`,
`GET /admin/moderation-queue`, `POST /admin/moderation/decide`,
`GET /admin/appeals`, `POST /admin/appeals/{id}/decide`,
`GET /admin/events`, `PATCH /admin/events/{id}`,
`GET|POST|PUT|DELETE /admin/announcements[/{id}]`,
`GET /admin/analytics`, `GET /admin/audit-logs?limit=50`, master data
(`POST /admin/master-data/{universities|departments|batches|sectors}`,
`PUT /admin/master-data/{tab}/{id}`,
`POST /admin/master-data/{tab}/{id}/deactivate`), moderators
(`GET|POST /admin/moderators`, `PUT /admin/moderators/{id}`,
`POST /admin/moderators/{id}/deactivate|reset-credentials`).

**No page calls a wholly non-existent endpoint**, with two near-misses:
(a) `MasterData.tsx:40-41` templates deactivate/rename over `tab`, so
`.../department-catalog/{id}/deactivate` and
`PUT .../department-catalog/{id}` are constructible, but the backend
switch covers only `universities|departments|sectors|batches`
(default throws `INVALID_KIND`,
`backend/.../admin/AdminService.java:592-601`;
`AdminController.java:108-134` has no department-catalog PUT) —
currently unreachable because the list disables Rename/Deactivate for
that tab (`MasterData.tsx:45`); (b) `AdminEvents.tsx:9` sends
`{registrationClosed:true}` in `PATCH /admin/events/{id}` — the
endpoint exists (`AdminController.java:105`) but the
`EventModerateBody` fields were not verified in this read.

### Backend endpoints no page ever calls (verified by grep over `frontend/src`)

`GET /public/skills`; `POST|DELETE /profiles/me/skills`;
`POST /profiles/me/identity-change`; `GET /announcements` (student
list); `DELETE /reactions`; `DELETE /posts/{id}/attachments/{attachmentId}`;
`POST /teams`, `GET /teams/event/{eventPostId}`;
`POST /rooms/private/{userId}`,
`GET /rooms/{publicId}/messages/{messageId}/attachments[/{attachmentId}]`,
`POST|DELETE /blocks/{userId}`, `POST /rooms/{publicId}/mute`;
`GET /notifications/unread-count`;
`POST /admin/auth/email-otp/request|verify`;
`GET /admin/identity-changes`, `POST /admin/identity-changes/{id}/decide`,
`GET /admin/users/{id}`, `POST /admin/users/{id}/recommend-suspension`,
`GET /admin/suspension-recommendations`,
`POST /admin/suspension-recommendations/{id}/decide`,
`GET /admin/audit-logs/export`, `GET /admin/alerts`.

### Backend-support verdicts for master-prompt needs

| Need | Verdict | Evidence |
|---|---|---|
| Contribution / activity data (GitHub-style graph) | **PARTIAL** | `GET /profiles/me/activity`, `GET /profiles/{id}/activity` exist (`ProfileController.java:58,61`) but return only the latest **20** `PostDto`s, `ACTIVE` only, visibility-scoped (`ProfileService.java:176-190`, `PageRequest.of(0,20)`). No per-day/week counts, no streaks, no contribution entity/repository/DTO anywhere (grep for `contribution` finds nothing in backend). A graph needs a new aggregation endpoint |
| Project tasks / milestones | **MISSING** | `Project` has only `id, userId, title, description, url, createdAt` (`identity/domain/Project.java`); `ProjectDto` mirrors it (`profile/ProfileDtos.java:7`). No Task/Milestone entity, repository, service, or controller. `Team`/`TeamDto` likewise only `name, maxSize, status, createdAt` |
| Team invitations | **PARTIAL** | Join-request flow exists (`POST /teams/{teamId}/join`, `GET /teams/{teamId}/requests`, `POST /teams/requests/{requestId}/decide`, `team/api/TeamController.java:22,26,29`). **No team-invitation concept** — no invite endpoint/entity for teams. Only invite is a chat-room invite, `POST /rooms/{publicId}/invite` for temporary rooms |
| Alumni discovery | **MISSING** | No `alumni`/`graduate` string anywhere in backend or frontend. `GET /search` users branch is full-text `MATCH(full_name)` over `state='VERIFIED'` users only, `LIMIT 50` (`search/SearchService.java:33`); no graduation-year/batch/status filter; `type` accepts only `all\|posts\|users` |
| Event team formation / looking-for-teammates | **EXISTS (thin)** | `POST /events/{id}/team-request` creates the event's team, caller becomes creator/admin, name `Team for event {id}`, `maxSize` 4 (`content/api/PostController.java:112-120`). `GET /teams/event/{eventPostId}` exists (`TeamController.java:19`) but **no frontend page calls it**. No "looking for teammates" flag, search, or open-team listing |
| Project discussions | **MISSING** | No discussion/thread endpoint tied to projects. Only post comments (`PostController.java:68,71`); `CommentDto` has `postId` only |
| Saved items / bookmarks | **EXISTS** | `POST|DELETE /bookmarks`, `GET /bookmarks` (`PostController.java:80,83,86`); `BookmarkDto` (`ContentDtos.java:11`). Only `POST` targets used in UI; event bookmarking not wired |
| Following / followers | **PARTIAL** | Follow/unfollow exist (`ProfileController.java:129,133`); `ProfileDto` carries `followers`/`following` counts (`ProfileService.java:13-16`). **No endpoint lists followers or following** |
| Post sharing | **MISSING** | No share/repost endpoint, no Share entity/repository. Only "share" in frontend is temp-password copy (`Users.tsx:12`, `Moderators.tsx:17`) |
| Event registration / interested-going | **EXISTS (thin)** | `POST /events/{id}/respond?response=` accepts only `INTERESTED\|GOING`, upserts `EventResponse` (`PostController.java:121`; `PostService.java:147-157`). **No GET for respondents or counts**, and the frontend never displays or invalidates RSVP state (`EventDetail.tsx:7`) |
| User search | **EXISTS** | `GET /search?q&type=users` (`SearchController.java:9-15`; `SearchService.java:33`). Limits: verified users only, name-only full-text, max 50, no skill/department/university filter on users (sector filters posts only) |
| Notifications list | **EXISTS** | `GET /notifications` (default `limit=20`), `POST /{id}/read`, `POST /read-all`, `POST /preferences` (`NotificationController.java:10,16,19,23`). Gap: only 3 types are ever emitted — `VERIFICATION_DECISION`, `REPORT_AUTO_HIDE`, `TEAM_JOIN_ACCEPTED` (`NotificationEventListener.java:16,24,39,43`) — despite `NotificationsPage.tsx:19` promising "mentions, team updates and event reminders". No mention or event-reminder notifications exist |
| Profile skills / projects / achievements | **EXISTS (with UI gap)** | Projects/education/achievements full CRUD (`ProfileController.java:64-112`). Skills endpoints exist (`ProfileController.java:115,119`) and `ProfileDto.skills` is `List<String>`, but **no frontend page calls the skill endpoints** — ProfilePage only displays `skills`; SettingsPage only edits `bio`/`phoneVisible`, so `headline`/`links` (accepted by `UpdateProfile`, `ProfileController.java:15`) also have no UI |

### API contract risks

1. **No typed client layer** — all calls are string-template URLs at
   call sites; a rename breaks silently at runtime (only Phase-2 test
   mocks catch it). Redesign should introduce one typed operation per
   endpoint.
2. **Inconsistent response shapes.** Only `GET /feed` returns
   `PageResponse {items, nextCursor, hasMore}`
   (`common/PageResponse.java`; `FeedService.java:19-20`, limit clamped
   1–50). Every other list returns a bare `List<T>`; `GET /search`
   returns a `Map {posts, users}`. `ListPage`/`AdminList` defensively
   accept array *or* `{items}` *or* `{content}`
   (`features/shared.tsx:8`; `admin/AdminList.tsx:8`);
   `PostDetail.tsx:9` types comments as
   `Comment[] | {items: Comment[]}` — evidence the shape was unstable.
3. **Pagination mostly absent.** Only feed is cursor-paginated;
   notifications (`limit` default 20), room messages (`limit` 50),
   audit logs (`limit` 50) are capped but not paginated; everything
   else is an unbounded `List` (activity is the exception with
   hard-coded 20).
4. **DTOs, not entities** — with one echo exception:
   `POST /profiles/me/identity-change` echoes its own request record
   back (`ProfileController.java:136-141`) instead of returning a
   created resource/DTO.
5. **Naming is camelCase in JSON** (Jackson records: `eventDeadline`,
   `regLink`, `readFlag`, `commentCount`). One historical snake_case
   leak (`reg_link`) was fixed per the D-141 comment in
   `PostController.java:129-133`.
6. **Uniform error shape, uneven status codes.** Errors are
   `ApiError {timestamp, status, code, message, path, traceId,
   fieldErrors}` (`common/ApiError.java`;
   `GlobalExceptionHandler.java:24-26`), 422 for validation with
   `fieldErrors`; frontend maps 401→session-expired redirect (except
   auth/public paths), 403 `PASSWORD_CHANGE_REQUIRED`→password screen,
   404→"not found" (`client.ts:58-85`). But creates return default
   200 (no `@ResponseStatus(CREATED)` observed on any controller), and
   many writes return `Map.of("status","ok"/"followed"/"deleted")`
   instead of the resource — the frontend cannot reliably get the
   created id/status without a refetch.
7. **Method inconsistency.** Same resource, different verbs: posts
   `PATCH`, portfolio `PUT`, profile `PUT`, announcements `PUT`, admin
   events `PATCH`; bookmark delete carries a JSON body
   (`PostDetail.tsx:14`) while `apiDelete` cannot send one, so the call
   site bypasses the helper with raw `api()`.
8. **CSRF + session coupling (must preserve).** Cookie session
   (`SessionCreationPolicy.IF_REQUIRED`, `SecurityConfig.java:98,112`);
   CSRF via `CookieCsrfTokenRepository.withHttpOnlyFalse`, header
   `X-XSRF-TOKEN`, primed by `GET /api/csrf`
   (`SecurityConfig.java:61-66`; `AuthController.java:26`). Admin chain
   additionally requires `MFA_VERIFIED` authority
   (`SecurityConfig.java:99`); student idle timeout 2 h vs admin 30 min
   (`SecurityConfig.java:78-97`). A redesign that moves to bearer
   tokens or a different origin must re-do all of this; a same-origin
   cookie design can keep it.
9. **STOMP auth is session-bound and membership-checked**
   (`config/WebSocketConfig.java`: SockJS `/ws`, native
   `/ws/websocket`, broker `/topic`+`/queue`, `/app`, `/user`;
   SUBSCRIBE/SEND to `/topic/room.*` requires membership). Any
   messaging redesign must keep the room-membership check server-side.
10. **No API versioning** (`/api/…` throughout, no `/v1`);
    `OpenApiConfig.java` exists but no frontend code consumes generated
    types — the redesign is the moment to generate the client from
    OpenAPI or hand-write the typed layer.

---

## 8. Auth & roles audit

### Auth flows — all EXISTS

- **Login:** `Login.tsx:7-9` — `POST /api/auth/login
  {email,password}`; success sets user and navigates to `/settings` if
  `mustChangePassword`, else `/feed`. 401 rendered in place as
  "Incorrect email or password" (`Login.tsx:15`). No lockout/attempt-
  count UI; backend caps are server-side only.
- **Registration:** `SignUp.tsx:25-26` — 3-step form,
  `POST /api/auth/register` as multipart FormData; client password
  rule 8+ chars with upper/lower/digit (`SignUp.tsx:9`); catalogs
  loaded unauthenticated from `/public/*` (`:15-17`); returned `id`
  stored in `sessionStorage['unilink:otpUserId']`, navigate to `/otp`.
  **No session is created by registration** (hence `/pending` is
  unguarded — `main.tsx:19-21` comment). Missing-file check is a bare
  `alert()` (`SignUp.tsx:31`).
- **Phone OTP:** `OtpStep.tsx:12-13` —
  `POST /api/auth/verify-phone {userId, otp}`, userId from
  sessionStorage only; resend with 60 s client cooldown. Copy states
  5-minute expiry / max 5 attempts — enforced server-side, not counted
  client-side. No OTP input validation client-side.
- **Email verification:** `EmailVerify.tsx:4` — `?token=` →
  `GET /api/auth/verify-email?token=…` on mount. No resend-email UI on
  this page.
- **Pending:** `Pending.tsx:4-5` — unauthenticated
  `GET /api/auth/verification/status`; REJECTED → resubmit via empty
  FormData. Copy states "up to 3 times in 24 hours" — server-enforced.
- **Forgot password:** `ForgotPassword.tsx:9-10` — request → confirm
  `{email, otp, newPassword}` → success text + link to `/login`. Copy:
  code expires in 15 minutes.
- **Logout:** `Layout.tsx:17` — `POST /api/auth/logout`, then
  `setUser(null)` + navigate `/login` **even if the call fails**,
  surfacing failure as a persistent `role="alert"` banner. Admin
  identical (`AdminLayout.tsx:14`).
- **401 handling:** `api/client.ts:44` — any non-auth 401 triggers
  `redirectOnSessionExpired`: sets
  `sessionStorage['unilink:session-expired']='1'` and
  `window.location.assign('/login')` (or `/admin/login` under `/admin`)
  (`client.ts:26-32,37-44`). Exempt: paths starting `/auth/`,
  `/admin/auth/`, `/csrf`, `/public/` (`:13-16`) and auth pages
  themselves (`AUTH_PATHS`, `:10`). Login pages read-and-clear the flag
  once via `<SessionExpiredNotice/>` (`primitives.tsx:44`;
  `Login.tsx:11`, `AdminLogin.tsx:10`).

### Role-state matrix

| Role | Frontend behaviour | Block presentation |
|---|---|---|
| **Visitor (signed out)** | Public only: `/`, `/signup`, `/otp`, `/verify-email`, `/pending`, `/login`, `/forgot-password`, `/appeals/public` (`main.tsx:26-29`). Header shows Log in / Sign up (`Layout.tsx:22-23`) | Guarded routes render inline `<NeedLogin/>` ("Please log in… Log in" button, `main.tsx:23-24`). **Not a redirect** — URL stays on the requested page. While `ready===false`, blank |
| **Unverified student** (`verified===false`) | Can log in, sent to `/feed` (`Login.tsx:7-9`). Can open **all** guarded student routes — no verified check | Writes fail server-side (`policy.requireVerified`, `AccessPolicy.java:20-21`; e.g. `PostService.java:45,67`, `MessagingService.java:57,70,83`). Frontend shows composer/buttons as fully enabled and only surfaces the 403 afterwards as generic "You do not have permission to do that." (`client.ts:74`). Feed is silently scoped server-side to National-only for unverified users (`FeedService.java:22-30`, D-016); UI gives no indication. Only hints: Messages empty state "Your department and university rooms appear here once you are verified." (`MessagesPage.tsx:122`) and no `✓ Verified` badge (`ProfilePage.tsx:49`). **PARTIAL/MISSING: no upfront verified-gate or explanation on Feed/Events/Teams/Post composer** |
| **Verified student** | Full student app; `✓ Verified` badge on own profile (`ProfilePage.tsx:49`, `primitives.tsx:13`) | — |
| **University moderator** | **Not a student-app role at all.** Uses the separate admin app, restored via `GET /api/admin/auth/me` (`admin/main.tsx:20`). All 11 admin nav items shown to every admin (`AdminLayout.tsx:6`). Guard is only "signed-in admin with MFA complete" (`admin/main.tsx:18-19`) | Scoping entirely server-side: dashboard/verifications copy says "Moderators see their university only" (`Dashboard.tsx:4`, `Verifications.tsx:23-35` — out-of-scope ID image renders 404 as "may have been removed, or this request is outside your moderator scope"). **No frontend role check hides moderator-inappropriate pages**: Moderators page and Announcements GLOBAL scope are rendered to moderators with no `role` check (`Moderators.tsx:6-24` has no `useAuth`; `Announcements.tsx:17` offers `GLOBAL` to all). Enforcement, if any, is backend 403 → inline error |
| **Platform admin** | Same admin app/routes as moderator. Only frontend distinction: `Users.tsx:6-7` — `isPlatformAdmin = user?.role==='PLATFORM_ADMIN'`; SUSPEND/BAN actions **filtered out of the list** for non-platform-admins (silent hide, with lead text "Suspend and Ban are Platform Admin only", `Users.tsx:6-9`) | All other admin pages treat the two roles identically in the UI |
| **Suspended/banned student** | No distinct frontend state. Login 401/403 handling is generic (`Login.tsx:15`, `client.ts:70-74`); only appeal surface is unguarded public token page `/appeals/public` (token from URL, single-use, 7-day expiry per copy) | — |

**Guards check only session existence.** `RequireAuth` (`auth.tsx:41`)
checks `user != null`; `user.verified` is populated (`auth.tsx:29`)
but **never used in any guard**; there is **no role-based route guard**
in either app. Student and admin sessions are separate apps with
separate restore paths; a student session is not recognised by the
admin guard (it would 401 on `/api/admin/auth/me` and render
`AdminNeedLogin`).

**mustChangePassword forced redirect** is a layout effect, not a route
guard: student `Layout.tsx:12` (any page except `/settings` →
`/settings`, replace); admin `AdminLayout.tsx:9-11` (any page →
`/admin/change-password`). Also routed directly from login/TOTP
(`Login.tsx:7-8`, `Totp.tsx:16-17`). Backend enforcement:
`MustChangePasswordFilter` returns 403 `PASSWORD_CHANGE_REQUIRED`
except an allow-list (`MustChangePasswordFilter.java:7-26,37-45`);
frontend maps that code to the server message (`client.ts:71-74`).
See §1 for why the admin side of this redirect is broken in the
browser as built.

### Locked-feature handling

- **Hide (silent):** SUSPEND/BAN for moderators (`Users.tsx:6-7`);
  owner-only Edit/Delete/attachment-upload (`PostDetail.tsx:37,45`);
  event Edit/Delete only when `isOwner` (`EventDetail.tsx:18`);
  Follow/Unfollow only when `!isMe` (`ProfilePage.tsx:51`).
- **Hide with explanation (text nearby, not on the control):** Users
  page lead; Messages verification empty state; must-change banners.
- **Disable without explanation (dominant pattern):** buttons disabled
  on `isPending`, empty input, or missing prerequisite with no reason
  text — Post disabled if title/body empty (`Feed.tsx:35`); Create
  event disabled if fields empty (`EventsPage.tsx:22`); Create
  moderator disabled until email + university + 12-char password
  (`Moderators.tsx:24`); Master-data Add disabled until `canAdd`
  (`MasterData.tsx:43,53`); Moderation/Users action buttons disabled
  until a reason is typed (`Moderation.tsx:10`, `Users.tsx:10`).
  Closed rooms: Send/Invite/Remove disabled when `room.closed`
  (`MessagesPage.tsx:119-121`) — "closed" appears only in the room
  list label.
- **Disable with explanation (rare):** Settings/AdminChangePassword
  must-change banners; Verifications "Reject" requires a reason (label
  says "Reason (mandatory for rejection)", `Verifications.tsx:13,16`).
- **Heuristic (fragile) role inference:** room-admin Remove shown only
  when `isRoomAdmin`, inferred as `room.memberIds[0] === meId` for
  TEMP rooms because `RoomDto` carries no `creatorId`/roles
  (`MessagesPage.tsx:112-121`, comment: "If the backend ever sends a
  creatorId, prefer it").
- **Frontend owns only:** signed-in vs not, `mfaVerified`,
  `mustChangePassword` redirect, the single `PLATFORM_ADMIN` filter in
  Users, `isOwner`/`isMe`/`isRoomAdmin` gating, form-validity
  disabling. Everything else — verified-only writes, feed visibility
  scoping, moderator university scoping, GLOBAL-announcement
  restriction, moderator-vs-admin page access — is backend-enforced
  (403/404 surfaced generically via `errorMessage`,
  `client.ts:70-74`).

### Empty / loading / error states

- **Primitives exist** (`primitives.tsx`): `Skeleton` (two grey bars,
  `aria-busy`, default label "Loading", `:11`), `EmptyState` (`:9`),
  `ErrorState` (title "Something went wrong" + message + optional "Try
  again", `:45`), `ApiUnavailable`/`NotFoundState` (404 wording,
  `:20,25`), `SessionExpiredNotice` (`:44`). **No spinner component
  exists**; pending state is Skeleton or button text (`Logging in…`,
  `Creating account…`) + `disabled`.
- **Consistent pattern EXISTS:** `ListPage` (`shared.tsx:7-12`) and
  `AdminList` (`AdminList.tsx:7-12`) — `isPending ? <Skeleton/> :
  isError ? <ErrorState onRetry> : empty ? <EmptyState/> : render`.
  Used by Teams, Notifications, Saved, Calendar, Events list, most
  admin lists.
- **Direct ternary pattern EXISTS (same shape, hand-written):** Feed
  (`Feed.tsx:36-37`), PostDetail, EventDetail, Search
  (`SearchPage.tsx:20` — also a pre-submit `EmptyState`), Profile
  (`ProfilePage.tsx:49` — 404 → `ApiUnavailable`, other errors →
  `ErrorState`), Messages (rooms + messages separately), Pending,
  EmailVerify, Dashboard/Analytics/Audit/Verifications.
- **Inconsistent / MISSING:** Auth pages (Login, SignUp, OtpStep,
  ForgotPassword, AdminLogin, Totp, AdminChangePassword) have **no**
  Skeleton/EmptyState/ErrorState — only inline `field-error` + disabled
  submit. `RequireAuth` renders blank while restoring (`auth.tsx:41`).
  `EventsPage` create-event form is always rendered below the list
  even while the list loads/errors (`EventsPage.tsx:19-22`). Retry is
  inconsistent: query errors usually get `onRetry={refetch}`, but
  mutation errors never get retry (only error text). `ErrorState` vs
  inline `field-error` vs `ApiUnavailable` selection is ad-hoc per
  page. `Toast` exists but no audited page uses it; success feedback is
  ad-hoc `role="status"` paragraphs (e.g. `Pending.tsx:8`,
  `Users.tsx:11`).

### Messaging auth fragility

- `MessagesPage` sets only `client.onStatus`; **`onError` is never
  assigned** (`MessagesPage.tsx:42`; default no-op `stomp.ts:57`).
  WebSocket `onerror` and STOMP `ERROR` frames
  (`stomp.ts:132,171-173`) are invisible — no error banner, only the
  status line.
- No distinction between transient drop and auth failure: an
  expired/revoked session makes the handshake fail → `onclose` →
  infinite reconnect loop at up to 30 s intervals
  (`stomp.ts:145-149,190-195`), with sends silently falling back to
  REST, which then 401-redirects to `/login` (`client.ts:44`). No
  re-auth/refresh attempt inside the STOMP client.
- `ERROR` frames do not trigger reconnect or resubscribe (only
  `onError` is called, `stomp.ts:171-173`); a server-side subscription
  rejection (e.g. not a member) leaves the UI showing "Live" status
  while receiving nothing.

### Admin auth specifics

- **TOTP enrolment — EXISTS, PARTIAL.** `AdminLogin.tsx:7-9`:
  `POST /api/admin/auth/login {email,password}` (client rule: password
  ≥12 chars). Response `{stage, otpauthUrl?}` navigates to
  `/admin/totp` with stage in **router state only** (`:9`).
  `Totp.tsx:11-14`: stage defaults to `NEEDS_TOTP` if state is
  missing; `otpauthUrl` defaults to `''`. `NEEDS_ENROLMENT` shows the
  raw `otpauthUrl` as plain `<code>` text (`Totp.tsx:21`), then
  `POST /admin/auth/totp/enrol {code}` returns 10 recovery codes
  displayed once (`Totp.tsx:12,25`). **MISSING: no QR code is
  rendered.** Verify: `POST /admin/auth/totp/verify {code}` — a
  recovery code also works here (`Totp.tsx:14,27`); success sets the
  admin user and navigates to `/admin` or `/admin/change-password` if
  `mustChangePassword` (`:14-16`). Until verify succeeds,
  `AuthProvider` treats the admin as not signed in (`auth.tsx:24`).
  **Fragility:** stage/`otpauthUrl` live only in `location.state` — a
  reload or direct visit to `/admin/totp` loses enrolment context
  (`Totp.tsx:21-23`); enrol and verify forms share one `code` state.
- **MISSING UI:** backend also exposes
  `POST /api/admin/auth/email-otp/request|verify`
  (`AdminAuthController.java:80-90`) — **zero references in
  `frontend/src`**. No email-OTP fallback/recovery UI exists.
- **Temp-password handling — EXISTS, PARTIAL.** Creation/reset surfaces
  show the temp password **once** in plain text with "shown once —
  share it securely, it is not stored" + Dismiss: moderator reset
  (`Moderators.tsx:13,17`) and user `FORCE_RESET`
  (`Users.tsx:8,12`). No copy-to-clipboard, no expiry shown in the UI
  (backend: 24 h expiry per decision log; not displayed).
  `createModerator` (`Moderators.tsx:11,24`) takes a caller-chosen
  ≥12-char password with no force-change indicator in the form. Student
  temp-password login is handled only via the must-change redirect; no
  dedicated temp-password entry screen.

---

## 9. Gap analysis vs the master prompt vision

### §5 — Global application shell

| Requirement | Status | Evidence |
|---|---|---|
| UniLink logo + header | **PARTIAL** | Header nav exists (`Layout.tsx:19-25`) but is text links; no logo asset/component, no icon set |
| Community / Workspace mode switcher | **MISSING** | No mode concept anywhere in the frontend; no switcher, no mode state, no mode routes |
| Global search in header | **MISSING** | Search is a separate page (`/search`, `SearchPage.tsx:7`); header has only a "Search" nav link, no search box |
| Notifications in header | **PARTIAL** | "Notifications" is a text nav link (`Layout.tsx:21`); no bell icon, no unread-count badge — backend `GET /notifications/unread-count` exists but **no page calls it** |
| Messages in header | **PARTIAL** | "Messages" text nav link (`Layout.tsx:21`); no unread indicator, no presence/online status |
| User profile menu | **PARTIAL** | Header links Profile `/profile/me` + Settings as separate text links (`Layout.tsx:24-25`); no dropdown menu, no avatar |
| Preserve auth/identity across mode switch | **MISSING** (no modes) | Auth is per-app (`auth.tsx` + separate admin restore); a student session is not recognised by the admin app — modes would need a shared auth context the current two-SPA split does not have |

### §6 — Community mode

| Requirement | Status | Evidence |
|---|---|---|
| Three-column home (profile sidebar / feed / right rail) | **MISSING** | `Layout.tsx` has header nav only; `Feed.tsx` is a single-column list (level tabs, sector filter, composer, pagination). No profile sidebar, no right rail (no Upcoming Events / Suggested Students / Trending / Opportunities rail) |
| Create Post composer | **EXISTS** (partial) | `Feed.tsx:23,31-34` — title+body only; **no attachment upload in the composer** (attachments only on PostDetail) |
| Community feed with post types | **PARTIAL** | Text posts, announcements (`GET /announcements` exists backend but **no page calls it** — announcements never surface in the student UI), events as posts, project showcase only via profile portfolio, achievements only via profile portfolio. No event-post or achievement-post rendering in the feed |
| Reactions / comments / share / save | **PARTIAL** | Reactions (Like only in UI — `POST /reactions` supports types), comments with @-mentions, bookmark/save, report/appeal. **Share: MISSING** (no endpoint, no UI) |
| Four community levels | **EXISTS** | Level tabs DEPARTMENT/UNIVERSITY/HUB/NATIONAL (`Feed.tsx:25`); `GET /feed?level=…`, `GET /community/context` |
| Rich student profile | **PARTIAL** | Cover/photo upload exists (`SettingsPage.tsx:19-20`); name, verification badge, university/department/batch, about (bio only — headline/links have no edit UI), skills (display only — skill endpoints uncalled), interests (no field), projects/education/achievements CRUD, activity list (latest 20 posts only), external links (display only, no edit UI). GitHub/LinkedIn/portfolio links are just `url` fields on portfolio items — no dedicated profile-link section. Student ID display: not evidenced in the inventory |
| Network (discover/search/suggested/following/followers/alumni) | **MISSING as a page** | No `/network` route. Discovery is only via `/search` (verified users, name-only, 50 max). Follow/unfollow exists; **no followers/following list endpoints**; suggested connections do not exist; **alumni discovery MISSING entirely** |
| Events (listing, detail, RSVP, team formation) | **PARTIAL** | Listing (upcoming/past tabs) + detail + RSVP (INTERESTED/GOING) + team-request exist. Gaps: no poster rendering evidenced, no eligibility display, no respondents/counts (no GET), no "looking for teammates" listing, calendar items are plain text with no links (`CalendarPage.tsx:14`), past vs upcoming distinguished only by tabs |
| Messages (conversation sidebar, active chat, rooms) | **PARTIAL** | Room list + active chat + dept/university/temp rooms exist; private-room creation endpoint uncalled; invite by hand-typed numeric user id; no contextual panel; no online/connection status; room-admin heuristic; `onError` never assigned (see §8) |

### §7 — Workspace mode

| Requirement | Status | Evidence |
|---|---|---|
| Workspace mode / dashboard | **MISSING** | No workspace routes, no dashboard, no mode. The closest existing surface is `TeamsPage` (list + join/decide) and profile portfolio items |
| Repository-style project cards | **MISSING** | Projects exist only as profile portfolio rows (`ProjectItem {title, description, url}`, `types.ts:24-26`); no project cards, no visibility/status/last-updated/contributors/tech tags in the UI. No project detail page, no tabs (Overview/Team/Tasks/Activity/Discussions) |
| Teams / invitations / roles | **PARTIAL** | Team list + join-request decide exists (`TeamsPage.tsx:6-15`). **No team invitations, no roles, no member management UI** (only room-member remove in messages) |
| Tasks / milestones / progress | **MISSING** | No backend support and no UI (see §7) |
| Contribution calendar | **MISSING** | No backend aggregation and no UI; activity feed is latest-20-posts only. Any graph would need a new endpoint; the prompt forbids inventing counts |
| Project discovery / saved projects | **MISSING** | No explore-projects surface; saved items cover posts only (event bookmarks mis-link) |

### §8 — Admin portal

The admin portal **EXISTS as a separate app** with all 11 listed areas
routed: Dashboard, Verification queue, Moderation, Users, Events,
Announcements, Master data, Moderators, Analytics, Audit log, Settings
(as `/admin/change-password` + `SettingsPage` equivalent is student-side;
there is no separate admin settings page — settings in admin =
change-password only). Gaps: Analytics is **PARTIAL** (raw
`JSON.stringify` dumps, `Analytics.tsx:9`); TOTP QR **MISSING**; admin
email-OTP UI **MISSING**; moderator-vs-admin page scoping is server-side
only (no frontend role checks except SUSPEND/BAN); no admin settings
page beyond change-password. Authentication (TOTP, must-change) is
untouched by design and must stay that way. **Plus the §1 navigation
defect: as built, no admin in-app link reaches its target in the
browser.**

### §9 — Role-based UX

Visitor/unverified/verified states exist as routes + `RequireAuth`, but
**verified-gating is absent from the UI** (see §8); locked features are
mostly disabled silently or hidden silently, rarely explained;
empty/loading/error states exist via shared primitives but are
inconsistent across auth pages and mutation errors. Backend remains the
authority — correctly — but the frontend does not communicate that to
the user before a failed submit.

---

## 10. Risks — what would break if the shell/router is restructured

1. **Admin navigation is already broken (§1).** Any redesign touching
   admin links must fix the basename double-prefix first and verify it
   live with a signed-in admin; do not build the new shell on top of
   the current links.
2. **Dev-only admin fallback; production needs an equivalent.**
   `vite.config.ts:6-21` (`adminSpaFallback` plugin): in dev only, any
   `/admin/*` URL without a file extension is rewritten to
   `/admin.html` (comment, `vite.config.ts:4-5`: "Production (Nginx)
   needs the equivalent rewrite: /admin/* -> admin.html"). Without it,
   Vite's SPA fallback serves `index.html` and the student app shows
   "Page not found". The plugin matches only
   `path.startsWith('/admin/')` — **bare `/admin` (no trailing slash)
   is not rewritten** (`vite.config.ts:16`). The student app has no
   equivalent plugin; its deep links also depend on a server rewrite
   that does not exist in this repo (no Nginx config was found among
   inspected files).
3. **Two roots, one `#root`.** Both HTML files mount into
   `<div id="root">` with different scripts. Merging to one entry,
   renaming either file, or changing `vite.config.ts:29` build inputs
   breaks the other app; the admin app is only reachable via
   `admin.html`.
4. **Hardcoded absolute paths everywhere.** Every `Link`/`NavLink`/
   `navigate` target is a literal absolute path (student: `/feed`,
   `/profile/me`, etc., `Layout.tsx:19-25`; admin: see §1).
   Renaming any route requires updating all call sites — there is no
   route-name constant or central route table. `api/client.ts` also
   hardcodes the `/admin` prefix test for 401 redirects
   (`client.ts:24-37`).
5. **`/profile/me` is a param value, not a route.** The header links to
   `/profile/me` (`Layout.tsx:24`) and it only works because
   `ProfilePage` treats `id==='me'` specially (`ProfilePage.tsx:41`).
   Splitting `/profile/me` into its own route, or adding a real user
   id `me`, would collide.
6. **Token/OTP state lives outside the URL.** Sign-up → OTP passes the
   user id via `sessionStorage` (`unilink:otpUserId`, `SignUp.tsx:30`,
   `OtpStep.tsx:7-10`); admin login → TOTP passes `{stage, otpauthUrl}`
   via location state (`AdminLogin.tsx:10`, `Totp.tsx:10-11`).
   Directly loading `/otp` or refreshing `/admin/totp` loses that
   state (OTP shows "sign up again"; TOTP falls back to `NEEDS_TOTP`
   with no otpauthUrl, `Totp.tsx:12-13,24-25`). Any restructure
   changing navigation must preserve these handoffs.
7. **CSRF priming + session coupling.** Every authenticated non-auth
   write depends on the `XSRF-TOKEN` cookie being primed by
   `GET /api/csrf` on app load (`auth.tsx:20`). A redesign that
   changes app bootstrap, entry points, or origin handling must keep
   the prime-then-call order; moving to bearer tokens or a different
   origin requires re-doing `SecurityConfig.java:61-66,98,112`.
8. **STOMP wiring is session-bound and membership-checked server-side**
   (`WebSocketConfig.java:17-20,28-40`). Messaging redesign must keep
   room-membership checks server-side, preserve the REST fallback
   (`MessagesPage.tsx:76-77`), and fix (not drop) the missing `onError`
   handler.
9. **No typed client + inconsistent envelopes.** All calls are
   string-template URLs; only feed is cursor-paginated; creates return
   200 with `Map.of("status",…)` instead of the resource.
   Restructuring pages without first introducing the typed client risks
   silent runtime breakage that only Phase-2 test mocks would catch.
10. **MasterData department-catalog trap.** The deactivate/rename code
    templates over `tab`, so a redesign that enables those buttons for
    the department-catalog tab would construct endpoints the backend
    rejects with `INVALID_KIND` (`AdminService.java:592-601`).
11. **Live verification is environmentally blocked in the sandbox**
    (Java→MySQL interception, unchanged since 2026-11-17; Flyway never
    ran there). The signed-in admin navigation behaviour (§1) and all
    route behaviour must be verified live on the owner's Windows PC —
    the unsigned-user link behaviour was verified here on the dev
    server, but nothing requiring a live backend session was.

---

## 11. Proposed implementation plan (Phases 2–7) + owner decisions

### Owner decisions required FIRST (before Phase 3 code)

1. **Design tokens — adopt §4 palette or keep the source-document
   palette?** The current warm monochrome comes from `pic.docx` via
   DESIGN.md (a verified source document). The §4 navy/blue palette
   conflicts with it. This is a design-direction decision, not a code
   detail. Also decide: Inter (needs a font-loading decision — bundled
   vs CDN) vs keep system-ui stack.
2. **Missing backend endpoints — build, descope, or fake?** The prompt
   forbids fake production data, so each MISSING item needs a ruling:
   contribution aggregation endpoint; tasks/milestones (new entities +
   API); team invitations; followers/following list endpoints; alumni
   discovery (needs a data model decision — SRS §1.2 excludes
   faculty/staff/alumni accounts, which **conflicts** with the master
   prompt §2 naming teachers/alumni as audience — owner must
   resolve); project discussions; post sharing; RSVP respondent
   lists/counts; mention/event-reminder notification types; skill
   editing UI (endpoints already exist); headline/links editing UI
   (accepted by `UpdateProfile` already). Without rulings, Workspace
   Phase 5 has no data to render for tasks, contributions, and project
   discussions.
3. **Icon library.** None exists. Choose one family (e.g.
   lucide-react) — a dependency decision — or continue with text
   glyphs (not recommended for a LinkedIn/GitHub-grade UI).
4. **Mode-switcher IA.** Community and Workspace share one shell, but
   today there are two SPAs with separate auth restore. Decide: (a)
   keep two entries and add a cross-app switcher (needs shared session
   handling — sessions are per-app today), or (b) merge into one SPA
   with a mode state (bigger restructure; breaks the `admin.html`
   entry pattern and the dev fallback). Also decide the mode URL
   scheme (e.g. `/community/*` vs `/workspace/*`, or a mode
   query/state) — the current flat routes (`/feed`, `/teams`) need a
   home under the new IA.
5. **Admin stays a separate entry.** The prompt requires it (§8);
   confirm the admin app remains `admin.html` + `basename="/admin"`
   and is only restyled, with TOTP/must-change logic untouched. (Note:
   the basename question in §1 must be fixed regardless of this
   decision — the current combination is broken.)
6. **QR codes for TOTP enrolment.** Needs a QR library decision (new
   dependency) or a documented "type the URL" flow (current
   behaviour).
7. **Announcements in the student UI.** Backend `GET /announcements`
   exists but no page calls it — decide where announcements surface
   (feed? dedicated page?).

### Phase 2 — Design blueprint (approval gate, no code)

- Global shell: header (logo, mode switcher, header search box,
  notification bell with unread count via
  `GET /notifications/unread-count`, messages with unread state,
  profile menu), mode IA and URL scheme, Community IA (3-column home,
  level tabs kept as routes or state, profile, network, events,
  messages), Workspace IA (dashboard, projects, teams, tasks,
  contributions), Admin IA (existing 11 pages, restyled).
- Design tokens: full §4 palette mapped to light/dark (including
  semantic success/error/warning/danger, shadows, focus states),
  typography scale reconciled to 24–30/17–20/14–16/12–13, spacing/
  radius system, icon set.
- Component inventory: consolidate the three alert boxes; add Card,
  Tabs, Select, Textarea, Avatar, Dropdown, Pagination, Spinner,
  Badge, Tooltip; retire dead primitives; define empty/loading/error/
  success patterns per page type.
- Page-by-page wireframes for: Community home, profile, network,
  events list/detail, messages, Workspace dashboard, project
  list/detail, Teams, admin dashboard + verification queue.
- **Deliverable for approval:** blueprint document + token table +
  wireframes. Do not start Phase 3 until approved.

### Phase 3 — Design system implementation (dependencies: Phase 2 approval; token + icon decisions)

1. Fix the admin basename defect first (§1): drop the basename or the
   `/admin` prefixes, and verify live with a signed-in admin on
   Windows before building on it.
2. Re-token `tokens.css` (+ `theme.ts` if needed); fix the three
   hardcoded warning banners (token violation + dark-mode bug); add
   semantic colors, shadows, error colour; correct `.display` 40px →
   target range; add 768/1024 breakpoints and a mobile nav (header
   nav currently vanishes ≤720px with no replacement).
3. Build the missing primitives (Card, Tabs, Select, Textarea, Avatar,
   Dropdown, Pagination, Spinner, Badge); add size variants to
   `Button` (kill the raw `.btn-sm` bypass); consolidate alert boxes;
   fix `Input` to carry its own class.
4. Introduce the typed API client (one operation per endpoint)
   **before** touching pages — this is the safety net for the router
   restructure; generate from OpenAPI if the owner prefers, else
   hand-write against the §7 inventory. Keep `api()` transport, CSRF
   priming, and 401 handling byte-for-byte in behaviour.
5. Build the shared shell (header + mode switcher + profile menu) as
   a new component; keep `Layout.tsx` and `AdminLayout.tsx` working
   until the cutover.
6. Fix the small defects found in audit as part of the system work:
   theme "saved to your account" copy (no server save exists);
   `SettingsPage` profile save sending only `{bio}` (wire
   headline/links/skills to the existing endpoints); `SavedPage`
   event-bookmark mis-link; `CalendarPage` plain-text titles (link to
   `/events/:id`).

### Phase 4 — Community implementation (dependencies: Phase 3; backend rulings for share/notifications)

One page at a time, in this order: Home (3-column: profile sidebar,
feed, right rail) → Profile → Network (search-based discovery first;
followers/following lists and alumni only if backend rulings allow) →
Events (detail: respondents need backend ruling) → Messages (keep
STOMP wiring + REST fallback; add `onError` handling; replace
hand-typed numeric invite with a picker using
`GET /search?type=users` and `GET /rooms/search`) → Notifications
(wire unread-count badge) → Saved/Settings. Test existing
functionality after every major page (typecheck, build, Vitest,
Playwright smoke, plus the Windows live check for routes). Keep all
21 student routes working; add `/network` only when its data story is
decided; do not remove any route without a migration plan.

### Phase 5 — Workspace implementation (dependencies: Phase 3; backend rulings for tasks/contributions/discussions)

Dashboard (welcome, my projects, active projects, team tasks, pending
invitations via existing join-request endpoints, recent activity via
`GET /profiles/me/activity`, contribution overview — **empty state, not
invented data, until the aggregation endpoint exists**) → Projects
(repository-style cards from profile portfolio data as the interim
source; full project detail tabs only for data that exists) → Teams
(list/join/decide + invitations if backend approves) →
Tasks/Contributions (blocked on backend rulings — do not build UI over
missing endpoints). Reuse Phase 3 tokens; use Workspace-specific
layouts (tables, repo cards, tabs) — no feed patterns.

### Phase 6 — Admin implementation (dependencies: Phase 3; QR-library decision)

Restyle the 11 existing admin pages without changing security logic:
keep `AdminGuard`, TOTP flow, must-change filter behaviour, and all
endpoints identical. Fix Analytics `JSON.stringify` dumps with real
tables (no chart library until the owner chooses one — or plain
tables, which need no dependency). Add QR rendering for TOTP
enrolment if approved. Do not touch `SecurityConfig`,
`MustChangePasswordFilter`, or the admin auth controllers. Keep the
`/admin/*` dev fallback and the production Nginx rewrite requirement
in view; verify the basename question live before changing any admin
link.

### Phase 7 — QA (dependencies: Phases 4–6)

Route matrix (all 36 routes + new mode routes), login/registration/
email/phone verification, pending/rejected flows, permissions
(unverified writes must show an upfront explanation, not a post-submit
403 — this is a redesign requirement, not a backend change), Community
feeds (all 4 levels), messaging (STOMP live + REST fallback +
reconnect + expired-session behaviour), events, projects, responsive
layouts (720px + new breakpoints), light + dark themes (including the
warning banners), browser console, API errors (422 `fieldErrors`
rendering — currently no form renders `fieldErrors`). Regression
suite: backend 287/287 (unchanged), frontend typecheck + build +
Vitest 108/108 (must stay green; new tests for new primitives),
Playwright smoke 2/2 (extend for mode switcher + new routes). **Live
verification must happen on the owner's Windows PC** (sandbox
Java→MySQL remains blocked); the admin navigation fix in §1 must be
closed live before the redesign is declared done.

### Sequencing summary

Phase 2 (blueprint + owner decisions) → Phase 3 (tokens, primitives,
typed client, shell) → Phase 4 (Community, one page at a time) →
Phase 5 (Workspace, gated on backend rulings) → Phase 6 (Admin
restyle, security untouched) → Phase 7 (QA + Windows live
verification). The critical path is the owner decisions in §11
(tokens, missing-backend rulings, icon library, mode IA, QR); without
them, Phases 3 and 5 cannot start cleanly.

---

## 12. Limitations of this audit

- Read-only: no code was changed, no build/test run was executed.
- No live backend session exists anywhere in this audit (sandbox
  Java→MySQL remains blocked); everything about authenticated
  behaviour comes from code reading, except the unsigned-user admin
  link behaviour, which was verified live on the dev server (§1).
- The admin signed-in navigation symptom (sidebar clicks landing on
  "Page not found", must-change redirect landing on the broken URL)
  follows from the same proven mechanism but has not been observed
  with a live session — it must be confirmed on Windows.
- `AdminEvents.tsx:9` `{registrationClosed:true}` against
  `EventModerateBody` was not verified in this read.
- Design-reference screenshots (LinkedIn/GitHub/current UniLink) were
  not provided in this turn, so no reference comparison was done.
