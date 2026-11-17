# Phase 2 verification report (2026-11-17)

Independent final verification of commit `080e763` (parent `1f59a50`).
Phase 1 commits `32ae119` and `1f59a50` are untouched. No Phase 3 work.
Nothing in this round is committed yet — the fixes below are uncommitted
on top of `080e763` for the parent to review and commit.

## Verdict

**`080e763` stands with fixes.** The contract work is real and the test
suites pass, but the review found 3 blockers and 13 should-fix items in
what the commit introduced or newly exposed. All of them are fixed in the
uncommitted tree (listed below), each with a test. After the fixes:
backend **93/93**, frontend Vitest **94/94**, Playwright smoke **2/2**.

## Findings and fixes

Severity: blocker / should-fix / note. All fixed unless marked otherwise.

### Blockers (all fixed)

- **B1 — event upcoming/past filter (hardening, downgraded).**
  `PostService.eventTime` built an uncorrelated subquery. The fix worker
  proved the failure does NOT reproduce: `EventDetail.id` is the post id,
  so `r.id IN (SELECT e.id …)` already agreed via the shared-PK invariant.
  The explicit correlation (`e.id = r.id`) is in place anyway, with a
  comment saying so, plus a two-event test. `PostService.java:156-172`.
- **F1 — activity feed bypassed visibility scoping.**
  `GET /api/profiles/{id}/activity` returned any user's ACTIVE posts to any
  authenticated student, including unverified and cross-university callers.
  Fixed: `ProfileService.activity` now takes the viewer principal and
  filters by the same list-scope rule as the feed (unverified → NATIONAL
  only). `ProfileService.java:166-181`, `ProfileController.java:58-63`.
- **F1 — any moderator could publish a GLOBAL announcement.**
  `AdminService.createAnnouncement` only scope-checked when
  `scope=="UNIVERSITY"`. Fixed: scope validated against {GLOBAL,
  UNIVERSITY} (else 400, which also fixes the latent `Map.of` NPE on null
  scope); GLOBAL requires Platform Admin. `AdminService.java:249-252`.

### Should-fix (all fixed)

- Comment moderation had no scope check → `decideContent` COMMENT branch
  now resolves comment → post → `requireModeratorScopeOr404`.
  `AdminService.java` (decideContent).
- Appeals had no scope check → `pendingAppeals` and `decideAppeal` are
  scoped to the moderator's university (out-of-scope → not listed / 404).
- No attempt limit on TOTP enrol/verify → `LoginThrottle` on
  `admin-totp:<adminId>`, 5 failures → lock, 429. Email-OTP request limit
  was dead code (lock checked, never set) → now uses `tryAcquire` and a
  fresh request carries the previous attempt count forward.
  `AdminAuthService.java:54,82,114-115`.
- Null TOTP/email-OTP code → NPE 500 → explicit null/blank checks → 400
  `CODE_REQUIRED`.
- Re-enrolment while already enrolled → 400 `ALREADY_ENROLLED` (recovery
  is via the Platform Admin reset).
- OTP resend was a user-enumeration oracle (distinct 404 / 400 /
  200 / 429) → uniform generic 200 for unknown and already-verified
  users; no OTP sent, no quota consumed. Registration now seeds the 60 s
  resend cooldown. `AuthService.java:74,93-108`.
- `FORCE_RESET` set a password nobody knew (Phase 2 made it reachable by
  fixing the spelling) → `changeUserState` returns the temp password once;
  `Users.tsx` displays it once, mirroring `Moderators.tsx`.
- Moderator credential reset comment overclaimed "must change on next
  login" (no enforcement exists; no admin password-change endpoint) →
  comment corrected; the stale `AdminTotpSecret` row is now deleted on
  reset.
- `updateModerator`: no email-format check, no self-deactivation guard →
  both added (plus self-email-change block).
- `createBatch` surfaced DB errors as 500 → existence (404) and duplicate
  (409) pre-checks; blank names → 400 on the master-data PUTs.
- Audit CSV export: negative `limit` → 400; formula-leading cells defused
  (`'` prefix), `\r` stripped.
- Multipart limits were never configured → Spring Boot's 1 MB/file default
  made the 2/5/10 MB validator limits unreachable. Added
  `max-file-size: 10MB`, `max-request-size: 11MB` to `application.yml` and
  a `MaxUploadSizeExceededException` → 413 `FILE_TOO_LARGE` handler.
- `GET /api/profiles/{id}` cast an admin principal → 500 → now uses
  `policy.requireStudent` (403).
- Team creation ignored event visibility → `TeamService.createTeam` now
  applies the own-scope matrix (NATIONAL / UNIVERSITY / DEPARTMENT /
  DEPARTMENT_HUB), rejects non-ACTIVE posts, closed registration and
  archived/past events. Note: `PostService.getPost` itself returns any post
  to a VERIFIED caller, so the literal reuse would not have blocked
  cross-university teams; the scope matrix from `FeedSpecifications` was
  used instead.
- Cover upload enforced 2 MB, D-029a says 5 MB → new `COVER_MAX = 5 MB`.
- Account deletion left the client apparently signed in → `SettingsPage`
  now clears auth state and navigates to `/login` on success.
- WebP magic was bare `RIFF` → now requires `WEBP` at offset 8.
- `LocalStorageAdapter.delete` had no traversal check and swallowed
  errors → same normalisation check as `store`/`open`, warning logged.
- The 5 remaining SHAPE items → PostDto gains `commentCount` + `sectors`,
  CommentDto gains `authorName`, BookmarkDto gains `title` (all additive,
  server-side); Dashboard/Analytics read the real backend field names.
- Frontend: TeamsPage fetches pending requests only for owned teams;
  MessagesPage Remove only for the room admin, never the creator;
  MasterData Add disabled until required fields are chosen; AdminEvents
  gains a Hide button; Audit export labelled unfiltered with `?limit=200`;
  notification prefs report per-type status; @-mentions wired
  (`mentionUserIds` sent); MasterData first-render no longer fetches
  departments/batches with an empty university id (query key includes the
  path + `enabled` gate).

### Notes (not fixed, recorded)

- `MessageAttachment.createdAt` is always null (entity has no timestamp
  column) — cosmetic; needs a migration or a DTO change, deferred.
- Duplicated visibility allow-lists and sniff/validate/store sequences —
  refactor candidate, no behaviour bug.
- OTP resend IP key uses `getRemoteAddr()` (no `X-Forwarded-For` handling);
  the two `tryAcquire` calls are not atomic; recovery codes are unsalted
  SHA-256 of 8 digits and never expire; no must-change-password enforcement
  for reset credentials (needs a new endpoint + migration — Phase 3 scope);
  sole Platform Admin who loses authenticator + recovery codes has no
  in-code recovery path; EXIF stripping (D-029a) has no implementation;
  photo/cover download is any-authenticated-student (may be intended);
  `MessagesPage` admin inference uses `memberIds[0]` = creator as a
  heuristic (RoomDto carries no creator/role field — switch to a backend
  `creatorId` if one is ever sent); `year` quoting relies on Hibernate
  rendering the quoted identifier with MySQL backticks (H2-proven only).

## API contract counts (recomputed from scratch, then fixed)

| | At `080e763` | Final (after verification fixes) |
|---|---|---|
| Frontend operations | 129 (127 REST + 2 STOMP) | 129 |
| MATCH | 124 | **129** |
| SHAPE | 5 | **0** |
| MISMATCH | **0** | **0** |
| Backend routes | 158 (157 REST + 1 STOMP) | 158 |
| Backend routes with no frontend caller | 30 | 30 |

No frontend call targets a nonexistent endpoint. The 30 orphans are mostly
legitimate admin/secondary endpoints (listed in `phase2-inventory.md`).
The pre-commit inventory's 36/17/23 counts were a freeze snapshot of the
pre-commit tree, not of `080e763` — the addendum in
`docs/phase-reports/phase2-inventory.md` records both recomputations.

## Security review, per focus area

- **Admin TOTP / recovery codes:** secret AES-256-GCM at rest, code
  verified before activation, single-use hashed codes, re-enrolment
  invalidates old codes — all verified. Fixed this round: attempt limiting
  (was unlimited guessing), re-enrolment guard, stale secret cleanup on
  reset. Remaining: unsalted/non-expiring codes; sole-Platform-Admin
  lockout trap.
- **OTP resend rate limits:** 60 s cooldown, 5/hour/user, 10/hour/IP are
  enforced per key with 429 (verified in code). Fixed: uniform response
  (was an enumeration oracle), cooldown seeded at registration. Limiter is
  in-memory only (per D-003, no Redis) — lost on restart.
- **Moderator credential reset:** Platform Admin only, temp password
  returned once, Argon2-hashed, old password + sessions + TOTP invalidated
  immediately, audited — verified. Temp password is 12 hex chars (~48
  bits) with no forced change — noted, not fixed (needs a change-password
  endpoint).
- **File uploads/downloads:** ownership/scope/type checks are sound;
  magic-byte sniffing server-side; path-traversal guard on store/open
  (delete fixed this round). Fixed: multipart config (1 MB default was
  silently capping everything), cover limit, WebP sniff. Remaining: no
  EXIF stripping; no per-post attachment count cap.
- **Temp chat rooms:** create/invite/remove/close gates verified; closing
  rejects new messages on REST and STOMP; removed members cannot read
  history (membership row deleted). Pass.
- **Account deletion / sessions:** Phase 2 did not touch the service;
  Phase 1 behaviour intact (password re-auth, storage deletion, audit,
  session invalidation). Client sign-out on success fixed this round.
- **Photo/cover replacement:** old objects deleted, owner-only, size
  limits server-side — verified. Cover limit corrected to 5 MB.
- **Announcements / moderation:** GLOBAL creation was a privilege
  escalation — fixed. Comment/appeal scope holes — fixed. `WARN` fails
  closed with 400 (D-088). Decisions are audited.

## Migration review

V009 (`ALTER TABLE chat_rooms ADD COLUMN closed_at DATETIME(3) NULL`) is
additive only, matches the `ChatRoom.closedAt` entity field, and cannot fail
on a database where V001–V008 applied. `git diff 1f59a50..080e763 --
backend/src/main/resources/db/migration/` shows only V009 added; V001–V008
untouched. No Phase 2 backend change assumes a schema element no migration
creates (the `year` quoting needs no migration — the columns exist in V002).

## Test results (my own runs, settled tree)

- Backend `mvn -o test`: **93/93 green, BUILD SUCCESS** (59 at `080e763` +
  34 new: AdminAuthSecurityTest 6, AdminSecurityFixesTest 12,
  GlobalExceptionHandlerTest 1, LocalStorageAdapterTest 2,
  UploadValidatorTest 4, ProfileControllerTest 2, Phase2ServiceTest +5,
  LoginThrottleTest +3, OtpResendTest +1). Mid-task reports of
  non-compiling test files were transient concurrent-edit states; the
  settled tree compiles and passes in full, including the previously
  reported `OtpResendTest.registerSeedsResendCooldown`.
- Frontend: typecheck clean, build succeeds, Vitest **94/94** (75 + 17
  fix-worker + 2 new FORCE_RESET display tests). Playwright smoke **2/2**.
- Dev server: landing and `/admin/login` return 200.

## Remaining known bugs and integration blockers

1. **Live MySQL verification is still blocked** (sandbox Java→MySQL
   interception, unchanged). Flyway has never run; `unilink_dev` has no
   tables. Everything above is proven against H2 / mocked fetch only.
2. No live-backend integration tests (audit I-19); no E2E beyond the 2
   smoke tests.
3. Event kind/type storage + `WARN` moderation action deferred (D-088).
4. Admin password-change endpoint does not exist (needed before any
   must-change enforcement); sole-Platform-Admin recovery is out-of-band
   only.
5. `year` column quoting is H2-proven, not live-MySQL-proven.
6. `/pending` stays unguarded by design (registration creates no session).
