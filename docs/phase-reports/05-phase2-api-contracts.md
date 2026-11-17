# Phase 2 report — Frontend↔backend API contracts and integration (2026-11-17)

Scope: audit items I-4–I-10, plus the error-handling gaps in the inventory
(§6) and one further 401/NPE bug found during verification. No deployment
config touched. Phase 1 commits 32ae119 and 1f59a50 are preserved.

## Inventory (the baseline)

`docs/phase-reports/phase2-inventory.md` (694 lines) is the full per-call
diff: 76 frontend operations (74 REST + 2 STOMP) against 124 backend
routes. At freeze time: **36 MATCH / 17 SHAPE / 23 MISMATCH**, 73 backend
routes with no frontend caller. The STOMP contract matches statically.
The inventory also corrects two audit claims: the counts were 74/23/14/37
at first pass (not ~50/20/5/30), and team-accept takes `{accept: boolean}`,
not `{action, reason}`.

## Auth workflows (I-4, I-5, I-6, I-10)

- **I-4 admin auth.** All four calls now use `/auth`: `POST
  /admin/auth/login` (returns `{stage, otpauthUrl}`, not `totpRequired`),
  `POST /admin/auth/totp/enrol {code}` (returns `{status, recoveryCodes}`,
  displayed once), `POST /admin/auth/totp/verify {code}`, `POST
  /admin/auth/logout`. Enrolment flow matches the backend:
  NEEDS_ENROLMENT shows the otpauthUrl + enrol form, then verify;
  NEEDS_TOTP shows verify only.
- **I-5 OTP + password reset.** OTP screen calls `POST
  /auth/verify-phone {userId, otp}`; `userId` comes from the registration
  response via `sessionStorage`. New backend endpoint `POST
  /api/auth/otp/resend {userId}` (D-081: 60 s cooldown, 5/hour per user,
  10/hour per IP; 400 if already verified, 404 unknown user). Forgot
  password uses `POST /auth/password-reset/request {email}` and `POST
  /auth/password-reset/confirm {email, otp, newPassword}` (both already
  existed). Bonus fix: email verify called `/auth/email/verify`; the real
  route is `/auth/verify-email`.
- **I-6 sign-up.** Free-text university/department/batch replaced by
  catalog dropdowns (`GET /api/public/universities|departments|batches`);
  submits numeric `universityId/departmentId/batchId` and file field
  `idCard`. Phone validated both sides as strictly `01[3-9]xxxxxxxx`
  (D-082; `+880` rejected). Batch is optional (D-083).
- **I-10 session restore + guards.** `AuthProvider` primes CSRF (`GET
  /api/csrf`) and restores the session (`GET /api/auth/me`) on load, with a
  `ready` flag so `RequireAuth` never flashes the signed-out fallback.
  `RequireAuth` now guards all protected student routes and the whole
  admin section; admin restore uses `GET /api/admin/auth/me` and counts
  only MFA-verified admins as signed in (D-085). `/pending` stays
  unguarded (registration creates no session). Idle-timeout warning banner
  after 105 min (student) / 25 min (admin), warning-only (D-084).
- **Admin-auth 401.** `/api/admin/auth/**` is permitAll, so unauthenticated
  requests reached the controller with a null `Authentication` and NPE'd
  (500). `requireAdminPrincipal()` now throws 401; proven by
  `AdminAuth401Test` (6 tests).
- **Student-auth 401 (found during reconciliation).** The same NPE class
  in `AuthController`: `changePassword`, `me`, `deleteMe`, `status`,
  `resubmit` dereferenced a null `Authentication` (500). Fixed with
  `requireStudentPrincipal()` (401 on null; wrong principal type still
  403 via `AccessPolicy`); proven by `AuthController401Test` (5 tests).
  Note: `POST /api/me/delete` is not under permitAll, so its
  unauthenticated 401 comes from the filter chain (with a CSRF token); the
  controller guard is defence in depth there.
- **`Input` forwardRef.** `frontend/src/design/primitives.tsx` `Input` did
  not forward refs, so react-hook-form's `register()` ref was dropped and
  every RHF form using it submitted empty values. Fixed with `forwardRef`
  (same props/rendering); the three RHF forms are back on `Input`. The
  test genuinely guards it: with the old `Input` restored, the RHF test
  fails.

## Pages, shapes, endpoints (I-7, I-8, I-9)

- **I-8 (22 fixes, frontend only unless noted):** feed sector sends an id
  (from `GET /public/sectors`); post create sends `visibility` +
  `sectorIds` (HUB maps to `DEPARTMENT_HUB`); event create sends `body`,
  drops `type`; search handles `Map{posts, users}` with separate
  People/Posts sections and `type=users|posts|all`; calendar sends
  `from`/`to`; team accept sends `{accept: boolean}` (D-086 — the audit's
  `{action, reason}` was wrong); profile/follow use `/api/profiles/…`;
  notification preferences POST one call per real type
  (`VERIFICATION_DECISION`, `REPORT_AUTO_HIDE`, `TEAM_JOIN_ACCEPTED`);
  reactions `POST /reactions?reaction=LIKE`; bookmark remove is `DELETE
  /api/bookmarks` with body; moderation `POST
  /admin/moderation/decide {targetType, targetId, action, reason}` (actions
  HIDE/REMOVE/RESTORE/EDIT_REQUEST, reason required; no WARN action exists
  — D-088); appeals `POST /admin/appeals/{id}/decide {action:
  ACCEPT|REJECT, reason}`; user actions UPPERCASE; audit `GET
  /admin/audit-logs?limit=200` (action filter client-side) + export
  `/api/admin/audit-logs/export`; master-data reads from `/api/public/*`;
  moderators stay on `GET /api/admin/moderators` (D-087 — no public
  endpoint; exposing admin accounts would be wrong); announcements create
  sends `scope` (required — a null scope NPE'd server-side after the row
  was saved) + edit PUT + delete DELETE; admin events support Remove /
  Restore / Close registration / deadline-visibility fix via PATCH;
  `@mention` suggestions are live search results, not hardcoded.
- **I-7 (new backend endpoints):** `PATCH/DELETE /api/posts/{id}`
  (owner-only, soft delete); `GET /api/events/{id}`,
  `/api/events/upcoming`, `/api/events?past=`, `PATCH/DELETE
  /api/events/{id}`; `POST /api/events/{id}/team-request`;
  post/message attachments (POST/GET + download, ≤10 MB);
  `GET /api/teams`; `PUT /api/profiles/me/photo|cover` (≤2 MB, old object
  deleted), `GET /api/profiles/{id}/photo|cover`, `GET
  /api/profiles/me|{id}/activity` (latest 20 ACTIVE posts), portfolio
  list/edit/delete (projects/education/achievements), `DELETE
  /api/profiles/me/sectors/{sectorId}`; search `?sector=<id>` now filters
  server-side; `GET /api/community/context`; temp rooms (`POST
  /api/rooms/temp`, invite, member removal, close — new additive migration
  `V009__chat_room_closed_at.sql`, `closed_at` nullable; closed rooms
  reject new messages, history stays readable); `GET/PUT
  /api/admin/moderators`, `POST /api/admin/moderators/{id}/reset-credentials`
  (returns the temporary password once, never stored/logged in plaintext,
  also resets `totpEnrolled=false` and invalidates sessions); master-data
  PUT/POST/deactivate; `GET/PUT/DELETE /api/admin/announcements/{id}`;
  `GET /api/admin/events`, `PATCH /api/admin/events/{id}`; `GET
  /api/admin/audit-logs/export` (CSV). `ProfileDto` gained
  `universityName, departmentName, batchLabel`. Supporting change:
  `EducationEntry.year` / `Achievement.year` use
  `@Column(name="\"year\"")` — H2 reserves `year`; MySQL renders it
  backticked to the same column; no migration rewritten (D-092).
- **Deferred (with reason):** event kind/type storage (no backend field;
  form drops the type select — D-088); `WARN` moderation action (no
  backend support — D-088). Search sector filtering on H2 is untestable
  (MySQL FULLTEXT) — covered by the frontend contract test + static review.
- **I-9 read receipts.** `MessageDto.read` is computed from
  `room_members.last_read_message_id` (true only when every other member's
  last-read id >= the message id; false with no other members). Chat ticks
  show ✓✓ only when the server says `read` is true; a missing `read`
  renders ✓.

## Error handling

- 401 from a guarded call sets `sessionStorage['unilink:session-expired']`
  and redirects to `/login` (student) or `/admin/login` (admin), with a
  "Your session has expired. Please log in again." notice on the login
  page. No redirect for auth calls, session restore, CSRF priming, public
  calls, or when already on an auth page — it cannot loop.
- 400/409/422 show the server's specific message (or its first
  `fieldErrors` entry); 403 forbidden; 404 is now a genuine "That item was
  not found. It may have been removed." — the "endpoint is not available
  yet" wording is reserved for genuinely unimplemented endpoints in a new
  `EndpointUnavailable` component that currently has **no call sites**.
  429 rate-limit; 500+ generic server error with retry, never fabricated
  data. List pages render `ErrorState` + retry instead of `ApiUnavailable`;
  single-resource pages keep `ApiUnavailable` (genuine not-found) with
  retry.
- Swallowed errors fixed: login/admin-login no-op `onError` removed (401
  on login itself renders "Incorrect email or password"); logout
  `catch{}` replaced with a surfaced error banner while still clearing
  the local session; `markRead` failure surfaces; room-search errors
  surface.
- State handling: the feed composer clears fields only `onSuccess` and
  shows "Posted." only after success; account-deletion copy corrected to
  "Account deleted. You have been signed out." (synchronous hard delete,
  no async processing).
- **GET /api/health is public** (D-089): liveness probe, no user data.
  Proven by `HealthEndpointTest` (2 tests).
- **Spring-session ERROR verdict:** the every-minute cleanup cron logs
  `BadSqlGrammarException` in H2 tests because the test databases have no
  SPRING_SESSION tables (Flyway disabled in tests). Benign test-environment
  noise (D-091); production gets the tables from V007. MockMvc tests that
  create a session set `spring.session.jdbc.initialize-schema=always` in
  their own test properties.

## Tests (verified by the coordinator running the full suites)

- Backend `mvn -o test`: **59/59 green, BUILD SUCCESS** — 22 pre-Phase-2
  + 37 new (AdminAuth401Test 6, AuthController401Test 5, HealthEndpointTest
  2, OtpResendTest 4, PhoneValidationTest 3, Phase2ServiceTest 14,
  LoginThrottleTest +3). Log: `docs/phase-reports/phase2-tests.log`.
- Frontend: `npm run typecheck` clean, `npm run build` succeeds, Vitest
  **75/75 across 5 files** (16 pre-existing + 59 new: auth-flows 11,
  api-contracts 17, error-handling 27, primitives +3 Input-ref tests, stomp 5).
- Playwright smoke re-run after the entry-point changes: **2/2**.
- Dev server kept running throughout; landing and `/admin/login` return
  200 at the end of the phase.
- Two pre-existing tests encoded the wrong contract and were updated with
  an explicit note (primitives sign-up schema now parses numeric catalog
  ids + bad-phone case; the 401 assertion now expects "session expired"
  wording). No other existing test was weakened or deleted.

## Decisions recorded (D-081–D-092)

In `docs/requirements/REQUIREMENTS_DECISION_LOG.md`: D-081 OTP resend
caps; D-082 strict phone format; D-083 batch optional; D-084 idle notice
warning-only; D-085 MFA-only admin restore; D-086 team decide
`{accept}`; D-087 no public moderators endpoint; D-088 WARN + event kind
deferred; D-089 `/api/health` public; D-090 student-auth 401 guard;
D-091 spring-session ERROR benign; D-092 `year` column quoting.

## Remaining incomplete API integrations

- Event kind/type storage and the `WARN` moderation action (D-088) — need
  a schema/contract decision.
- 73 backend routes have no frontend caller (inventory §3). Most are
  legitimate (admin/secondary endpoints, or routes the new pages now call
  via different paths); the inventory lists them, but no further wiring
  was in Phase 2 scope.
- `/pending` stays unguarded by design (registration creates no session);
  its status check runs unauthenticated — a pre-existing flow gap, not a
  contract mismatch.
- No integration tests against a live backend exist (I-19 in the audit);
  all proof here is H2 + mocked-fetch.

## Features still requiring live MySQL verification

Everything, in the strict sense: no workflow has run against a live
backend. Java→MySQL interception is unchanged; Flyway has still never
run; `unilink_dev` has no tables; no screen has been exercised against a
live backend. In particular, the following cannot be considered verified
until a genuine boot succeeds: registration → OTP → email → approval,
login/session restore, admin login + TOTP, password reset, STOMP live
messaging, ID-image streaming, the verification queue, account deletion,
and every new I-7 endpoint. What Phase 2 proves instead: the frontend
calls the routes the backend actually serves, with the payloads the
backend actually expects, and the contracts are pinned by 37 backend and
59 frontend tests.

## Closure — final state (2026-11-17, verified on the committed tree)

This section supersedes the snapshot numbers above, which describe the
inventory freeze time. The verification round (`06-phase2-verification.md`)
recomputed the contract from scratch and fixed 3 blockers + 13 should-fix
items found in `080e763`.

- **Commits.** `080e763` Phase 2: fix frontend-backend API contracts and
  complete auth workflows (83 files). `8c0407c` Phase 2 verification fixes:
  close scope holes and harden new endpoints (42 files). Both preserve
  Phase 1 commits `32ae119` and `1f59a50`. Working tree clean at closure.
- **Final API contract counts (recomputed from scratch, same counting rule:
  distinct method + path-template pairs).** 129 frontend operations
  (127 REST + 2 STOMP): **129 MATCH / 0 SHAPE / 0 MISMATCH**. 158 backend
  routes (157 REST + 1 STOMP). 30 backend routes have no frontend caller
  (legitimate admin/secondary endpoints, listed in the inventory §B).
  No frontend request targets a nonexistent endpoint.
- **Final test results (re-run on the committed tree at closure).**
  Backend `mvn -o test`: **93/93 green, BUILD SUCCESS**. Frontend:
  `npm run typecheck` clean, `npm run build` succeeds, Vitest **94/94**.
  Playwright smoke: **2/2**. Vite dev server: landing and `/admin/login`
  both return 200.
- **Migration.** V009 is additive only (one nullable `closed_at` column on
  `chat_rooms`); V001–V008 are unchanged. Verified by diff: the migration
  directory shows only V009 added since Phase 1.
- **Error states.** Loading, empty, unauthorized (401 → login with a
  session-expired notice), forbidden (403), not-found (404, genuine),
  rate-limit (429) and server-error (500, with retry) states are handled;
  no fabricated backend responses remain.
- **Deferred, kept deferred.** Event kind/type storage and the `WARN`
  moderation action (D-088). No admin password-change endpoint yet.
  Recovery codes are unsalted SHA-256 and never expire; a sole Platform
  Admin locking themselves out has no in-code recovery; no EXIF stripping
  on uploads; `MessagesPage` infers the room admin from `memberIds[0]`
  (heuristic); `MessageAttachment.createdAt` is always null (cosmetic).
- **Limitations (what Phase 2 does NOT prove).** No workflow has run
  against a live backend. Java→MySQL interception is unchanged; Flyway
  has still never run; `unilink_dev` has no tables; no screen has been
  exercised against a live backend; no live-backend integration tests
  exist (audit I-19). What Phase 2 proves instead: the frontend calls the
  routes the backend actually serves, with the payloads the backend
  actually expects, and the contracts are pinned by tests.
