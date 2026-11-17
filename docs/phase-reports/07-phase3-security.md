# Phase 3 report — Security Hardening & Remaining Business Logic (2026-11-17)

## Verdict

**Phase 3 is complete as code + contract, verified by tests on H2 and
mocked-fetch frontend tests. Committed as `b29e5b6` (security hardening)
and `573d821` (data integrity + business-logic triage); this report and
its per-workstream evidence files are committed alongside them.** Phase 1
commits (`32ae119`, `1f59a50`) and Phase 2 commits (`080e763`, `8c0407c`,
`e6ff354`) are preserved as ancestors; HEAD at start was `e6ff354`,
working tree clean.

Five workstreams ran in parallel (workers A–E) and were integrated by the
coordinator, who also fixed two items the workers flagged (D-117, D-118),
renumbered a decision collision (see §Decisions), merged all decisions
into the log, and re-ran the full suites on the settled tree. Detailed
per-worker evidence lives in `07-section-a.md` … `07-section-e.md` and
`07-decisions-a.md` … `07-decisions-f.md`; this report summarises them
with file:line evidence and records only the coordinator's own verified
numbers in §Test results.

**Not verified, stated plainly:** no live MySQL verification — the sandbox
Java→MySQL interception is unchanged; Flyway has never run; V010/V011 are
proven only by static migration tests + H2 entity mapping; no workflow has
run against a live backend. A genuine Flyway run + boot + end-to-end pass
is still required before launch (audit I-19).

## P0 — Authentication and account security (Worker A + coordinator)

### 1. Admin password-change endpoint (D-093)
- New `POST /api/admin/auth/change-password` —
  `backend/src/main/java/com/unilink/admin/api/AdminAuthController.java:106`,
  service `AdminAuthService.changePassword`
  (`backend/src/main/java/com/unilink/admin/AdminAuthService.java:155`).
  Authenticated admin only **and MFA-verified only** (403 otherwise);
  current password + new password ≥12 chars (D-031); new must differ
  (`SAME_PASSWORD`); audited (`ADMIN_PASSWORD_CHANGED`); all sessions
  invalidated (D-013). No such endpoint existed (audit I-16).
- Frontend: new admin page `frontend/src/admin/pages/AdminChangePassword.tsx`
  (route `/admin/change-password` in `frontend/src/admin/main.tsx`).

### 2. Mandatory password change after an admin reset (D-094)
- Flag `must_change_password` on `user_credentials`
  (`identity/domain/UserCredential.java:10`) and `admin_accounts`
  (`identity/domain/AdminAccount.java:15`), added by V010. Set from both
  issuers: `AdminService.resetModeratorCredentials`
  (`AdminService.java:488-491`) and `FORCE_RESET` in `changeUserState`
  (`AdminService.java:193-198`). `createModerator`'s initial password is
  deliberately NOT flagged (recorded in D-094).
- Enforcement: session attribute `MUST_CHANGE_PASSWORD`, set at login and
  checked by new `MustChangePasswordFilter`
  (`security/MustChangePasswordFilter.java:21`, registered in both chains
  in `SecurityConfig.java:96,117`). A flagged session gets 403
  `PASSWORD_CHANGE_REQUIRED` on every API call except the documented
  escape hatches (login, change-password, logout, `/me`, CSRF, health,
  admin TOTP/email-OTP steps, password-reset request/confirm); `/ws` is
  blocked. The flag is returned on login and on `/me`. The existing
  student change-password endpoint is reused and now clears the flag (as
  does password-reset confirm).
- Frontend: student login redirects to `/settings` on the flag
  (`features/auth/Login.tsx`); `Layout` redirects from any page
  (`app/Layout.tsx`); `SettingsPage` gains the change-password form plus
  a must-change banner; admin TOTP verify redirects to
  `/admin/change-password` (`admin/pages/Totp.tsx`); `AdminLayout`
  redirects on a restored flagged session.

### 3. Temporary passwords expire after 24 h (D-095)
- Column `temp_password_expires_at` on both credential tables (V010).
  Both issuers set issue + 24 h via shared `AuthService.TEMP_PASSWORD_VALIDITY`
  (`identity/service/AuthService.java:41`). Login rejects a correct-but-
  expired temporary password with 400 `TEMP_PASSWORD_EXPIRED` and a clear
  message; a wrong password still yields generic `INVALID_CREDENTIALS`;
  expiry moves neither the throttle success reset nor the lockout count.
  The admin must re-issue.

### 4. Recovery codes: Argon2id + 90-day expiry, legacy codes invalidated (D-096)
- Enrolment (`AdminAuthService.java:140-141`) stores
  `encoder.encode(code)` (Argon2id via the existing `PasswordEncoder`)
  with `expires_at` = now + 90 days; `code_hash` widened `CHAR(64)` →
  `VARCHAR(255)` in V010. Verification skips expired and legacy
  non-`$argon2` rows; single-use unchanged. **Pre-existing codes are
  invalidated, not migrated** — unsalted SHA-256 hashes cannot be
  re-hashed without the plaintext. Recovery path: TOTP still works; if
  TOTP is also lost, a Platform Admin reset forces re-enrolment with
  fresh codes. Temporary passwords are still generated the pre-existing
  way (~48 bits, returned once, never logged); generation strength was
  not in scope and is unchanged.

### 5. Platform Admin recovery (D-116, Worker E — DESIGN ONLY, no code)
- Full runbook in `07-section-e.md` (643 lines). Recommendation: **(a)
  break-glass via a deployment-owner-controlled database/console action,
  with dual control and full audit** — the operator performs exactly the
  MFA half of what `resetModeratorCredentials` already does in code
  (delete TOTP secret + recovery codes, `totp_enrolled = 0`, delete the
  admin's sessions), then the admin re-enrols through the normal tested
  flow. No password reset in the main path (password still known).
  Controls: Requester ≠ Authoriser; signed approval valid 24 h for one
  execution; identity verified out of band (≥2 factors); execution only
  while the requester is present to re-enrol immediately (target ≤1 h,
  hard limit 24 h); threefold audit (incident record, hash-chained
  `audit_logs` row `ADMIN_BREAK_GLASS_RESET`, application login/enrol
  rows); closure includes creating a second Platform Admin.
- Rejected: (b) sealed envelope/vault credential (standing secret, silent
  failure modes, undrillable); escrowed/split TOTP secret or key;
  email-OTP exception (re-opens the D-078 hole); delete-row + re-trigger
  bootstrap (blocked by audit FK); any unauthenticated reset endpoint,
  backdoor password, or TOTP-disable flag. Single-person deployment:
  minimum viable control is detection, delay (4 h cooling-off + written
  pre-announcement to a monitoring mailbox), and audit, with residual
  risk accepted in writing and reviewed annually.

## P1 — OTP and rate limiting (Worker B + coordinator)

### Per-phone OTP resend cap (D-099, audit I-17)
- `AuthService.resendPhoneOtp` (`identity/service/AuthService.java:103-131`)
  now enforces 5/hour per user, **5/hour per phone (new)**, 10/hour per
  IP, plus the 60 s per-user cooldown. Registration seeds the per-phone
  bucket (never blocked). Unknown/already-verified users still get the
  identical generic 200 with no OTP sent and no quota consumed.

### Forwarded-IP handling via IpResolver (D-100)
- New `backend/src/main/java/com/unilink/security/IpResolver.java`:
  `X-Forwarded-For` honoured only when the direct peer is a configured
  trusted proxy (default `127.0.0.1, ::1`, via
  `app.security.trusted-proxies` / `TRUSTED_PROXIES`,
  `application.yml:81`). First header entry is the client;
  `unknown`/blank/non-IP values skipped; hostnames never resolved;
  bracketed IPv6 and IPv4 `:port` accepted. All rate-limit/audit call
  sites now use it; **no raw `getRemoteAddr()` or direct XFF read remains
  outside `IpResolver`** (verified by grep at integration). This also
  fixes a real hole: `AdminController.ip()` previously trusted XFF from
  any client, so audit IPs could be forged.

### Atomic rate-limit operations (D-101)
- `LoginThrottle.java`: all mutations run under one lock; new
  `tryAcquireAll` acquires user+phone+IP keys (+ cooldown) in one
  critical section, all-or-nothing. Closes the partial-consumption hole
  (user quota consumed when the IP cap hit) and the check-then-act race.
  Single-key semantics unchanged (5 failures → 15 min lock).

### Password-reset OTP send cap (D-102)
- `AuthService.requestPasswordReset` (`AuthService.java:179-192`): 5/hour
  per email + 10/hour per IP via `tryAcquireAll`; a capped request is
  dropped silently, controller still returns the generic 200.

### OTP wrong-guess counters actually engage now (D-103 — real bug found)
- Root cause: `OtpService.verifyPhoneOtp` / `verifyPasswordResetOtp`
  incremented the counter with `setAttempts(n+1)` + `save` and then threw
  `OTP_INVALID` — but the throw rolls back the enclosing `@Transactional`
  method, so every wrong guess saw `attempts = 0`: the 5-attempt cap
  could never engage and guessing was unbounded within the code lifetime.
  Fix: atomic `UPDATE … SET attempts = COALESCE(attempts, 0) + 1` via
  `@Modifying` + `REQUIRES_NEW` repository methods
  (`identity/repo/PhoneOtpRepository.java:13-23`,
  `identity/repo/PasswordResetTokenRepository.java:11-19`, used in
  `identity/service/OtpService.java:45-48` and `:89-92`).
- **Coordinator follow-up, D-117:** the identical bug existed in
  `AdminAuthService.verifyEmailOtp` (Worker B flagged it; Worker A's
  file was mid-rewrite). Fixed the same way:
  `AdminEmailOtpRepository.incrementAttempts`
  (`identity/repo/AdminEmailOtpRepository.java`) + use in
  `verifyEmailOtp`. Proven by new non-transactional
  `AdminEmailOtpAttemptsTest` (3 tests). The pre-existing
  `AdminAuthSecurityTest.emailOtpAttemptCounterSurvivesFreshRequest` had
  to change to run with `@Transactional(NOT_SUPPORTED)`: inside one
  shared test transaction the persistence context serves the stale
  managed row, so the carry-forward read saw attempts=0 — the false
  confidence B warned about. NOT_SUPPORTED gives every service call its
  own transaction, as in production. No production code was changed for
  the test; the test now models production.
- **Known limitation, not fixed:** `AuditService.write` stays
  same-transaction by design (D-037), so `ADMIN_LOGIN_FAILED` rows
  written just before a throwing verify still roll back with the
  caller's transaction. The security-critical state (throttle counters,
  OTP attempt counters) now survives; the audit loss for failed auth
  attempts is documented here, not silently fixed by changing audit
  semantics.

### Enumeration / brute-force verification
- Login throttle engages (5 wrong → 400, 6th → 429, correct-while-locked
  → 429); login errors byte-identical for unknown email vs wrong
  password; OTP resend anti-enumeration holds (pre-existing
  `OtpResendTest` green); phone-OTP brute force capped (6th guess, even
  the correct code afterwards, → `OTP_ATTEMPTS_EXCEEDED`); TOTP brute
  force locked at the HTTP layer (5 wrong → 400, 6th → 429).
- Bound across resends: ~30 guesses/hour per user/phone (5 resends × 5
  guesses + initial 5), gated by the resend caps — the pre-existing
  design, now enforced by caps that actually engage.

## P1 — File upload security (Worker C)

### EXIF stripping, dependency-free (D-104)
- New `backend/src/main/java/com/unilink/storage/ExifStripper.java` —
  pure JDK, **no new Maven dependency** (`pom.xml` untouched). JPEG
  drops APP1 segments starting `Exif\0\0`; PNG drops `eXIf` chunks;
  WebP drops `EXIF` chunks, repairs the RIFF size and clears the VP8X
  EXIF flag. Pixel data copied byte-for-byte. Wired into all five image
  upload paths (validate original bytes first, strip, store stripped
  bytes): `AuthService` register (ID card + photo) and resubmit (ID
  card), `ProfileService` photo/cover, `PostService` post attachments,
  `MessagingService` chat attachments. Tests use crafted GPS-bearing
  JPEG/PNG/WebP images and assert the GPS payload is gone from stored
  bytes on every path.

### Per-post attachment cap 5 (D-105)
- `UploadValidator.MAX_ATTACHMENTS_PER_POST = 5`; enforced in
  `PostService.addPostAttachment` (400 `ATTACHMENT_LIMIT`, checked
  BEFORE storing so a rejected 6th file leaves no orphan), counting via
  new `AttachmentRepository.countByOwnerTypeAndOwnerId`. Per-message is 1
  by API design; no per-room cumulative cap (would break long-lived
  department rooms).

### Validation / traversal / ownership hardening (D-106, D-107)
- `UploadValidator`: JPEG now needs a real marker as 4th byte, PNG the
  full 8-byte signature; `validateImage`/`validateAttachment` return the
  sniffed type (stored type is now the sniffed type, not a hardcoded
  `"image/jpeg"`); new `sanitizeFileName` applied to every stored file
  name and every `Content-Disposition` header (old code only stripped
  `"`, leaving CR/LF injection).
- `LocalStorageAdapter.size()` had no traversal guard (unlike
  store/open/exists/delete) and could leak an arbitrary file's size —
  now guarded; `store()` rejects bad namespaces and null file names.
- `AdminController` ID-image download no longer always answers
  `image/jpeg`; type is sniffed from stored bytes.
- **Real gap fixed:** soft-deleted posts' attachments stayed
  downloadable (`getPost` doesn't check status). `PostService`
  list/download now 404 unless the post is `ACTIVE` (files kept so
  moderation RESTORE still works).
- **New endpoint** `DELETE /api/posts/{id}/attachments/{attachmentId}`
  (`PostController.java:50`, `PostService.deletePostAttachment`):
  owner-only, deletes row + stored object; no attachment-delete path
  existed before.
- Verified sound, unchanged: photo/cover replacement
  (store-new-before-delete-old, own-row key only), message-attachment
  membership checks, admin ID-image scope, purge jobs, account-deletion
  key collection. Resubmit deliberately does NOT delete the old ID image
  (D-053 30-day retention; purge job handles it).
- Deferred: XMP/PNG text-chunk stripping (D-029a specifies EXIF only);
  backfill of already-stored images; event-poster upload (no endpoint
  exists); per-user daily upload quota; the count-check/store race (one
  extra file, not a breach).

## P2 — Data integrity + business-logic triage (Worker D)

### MessageAttachment.createdAt (D-108)
- Root cause: the entity had no `createdAt` field at all (and an empty
  `@PrePersist void onCreate(){}`), V005 created `message_attachments`
  without a `created_at` column, and `MessagingService` passed a literal
  `null` into the DTO. Fixed entity
  (`identity/domain/MessageAttachment.java` — column + `@PrePersist`
  that sets it when null, same pattern as sibling `Attachment`), DTO
  mapping, and additive migration V011 (adds `created_at DATETIME(3)
  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)`). Related entities with empty
  `onCreate(){}` were checked: none has a createdAt field/column or a
  DTO exposing one — consistent, not bugs.

### I-11 Verification-decision email (D-109)
- `NotificationEventListener.onVerified` / `onRejected` now also send
  the decision email via `EmailProvider.sendSimple`, in addition to the
  in-app notification. Security emails cannot be disabled (D-029c), so
  no preference check. Provider failure is caught and logged only —
  email failure never fails the decision.

### I-12 OCR university-name comparison (D-110)
- `TesseractOcrAdapter.score` adds one component per distinctive
  university token (stopwords and tokens < 3 chars skipped; no
  distinctive token → component skipped, score unchanged).
  `OcrProvider`/`OcrJobService` gained 5-arg forms; `AuthService`
  register/resubmit pass the university name.
- Regression found by the new tests and fixed in the same scorer: the
  student-ID check compared the raw ID against lowercased text, so an ID
  with any uppercase letter (essentially every real ID, e.g.
  `CSE-2021-047`) could never match — a perfect card scored at most
  0.66. Now lowercases the ID first. This line predates Phase 3
  (initial commit).

### I-13 Suspension-appeal token wired (D-111)
- `AdminService.issueSuspensionAppeal`: creates the D-019 token via
  `ModerationService.createSuspensionAppealToken`, emails
  `${baseUrl}/appeals/public?token=…`; exceptions caught and logged —
  the suspension stands, the link can be re-issued. Called from
  `changeUserState` after SUSPEND/BAN and from `decideRecommendation`
  when a recommendation is confirmed. Frontend: new public page
  `frontend/src/features/auth/PublicAppeal.tsx`, unguarded route
  `/appeals/public` (a suspended user has no session). (FORCE_RESET
  returning the temp password was already fixed in Phase 2 verification.)

### I-14 Moderator-scope remaining holes (D-112)
- `AdminService.changeUserState`: SUSPEND and BAN now require Platform
  Admin first (`policy.requirePlatformAdmin`); moderators use
  `recommendSuspension`. `dashboard.pendingAppeals` scoped for
  moderators (JOIN users, filter by university); `analytics` aggregates
  scoped (`eventsPerSector`, `verificationTurnaroundHours`,
  `activeUniversities`). (Appeals list/decide, comment moderation and
  GLOBAL announcements were already fixed in Phase 2 verification.)
- **Coordinator follow-up, D-118:** `Users.tsx` still rendered SUSPEND/BAN
  to every admin (moderators got a surfaced 403). The admin session
  already carries `role`, so the page now renders SUSPEND/BAN only when
  `role === 'PLATFORM_ADMIN'`; server-side 403 remains the enforcement.
  Proven by `users-role-gating.test.tsx` (2 tests). Two pre-existing
  tests that clicked SUSPEND without an auth context were updated to
  render as a Platform Admin (test-only change, assertions unchanged).

### I-15 Remaining guards (D-113)
- `requireVerified` added to secondary writes: posts unreact/unbookmark,
  messaging rooms/list/read/block/mute, profile deletes, search,
  notification prefs. `GET /api/posts/{id}/comments` and
  `GET /api/teams/event/{id}` now take `Authentication` and apply D-016
  visibility. `POST /api/appeals` ownership check (unknown → 404,
  non-owner → 403). `NotificationService.setPreference` rejects
  `email=false` for `VERIFICATION_DECISION` (400
  `SECURITY_EMAIL_REQUIRED`). Deliberately NOT guarded: `updateMe` /
  photo / cover (own-profile edits, not in audit §4.3); notification
  list/read left open to unverified students (needed for
  verification-decision delivery, D-109).

### I-16 Admin IP allow-list enforced (D-114)
- `AdminAuthService.login`: after password auth and the temp-password
  expiry check, before `throttle.recordSuccess`: if the allow-list has
  active rows and the resolved client IP matches none, audit
  `ADMIN_LOGIN_IP_BLOCKED` and throw 403. A blocked login does not reset
  the throttle. Opt-in: no active rows → all IPs allowed (previous
  behaviour). Helpers `isIpAllowed` / `ipMatchesCidr` (exact or IPv4
  CIDR 0–32; malformed input never matches). The IP is the
  `IpResolver`-resolved client IP.

### I-17 Duplicate-phone flagging + suspended-phone block (D-115, part 1)
- `AuthService.register`: 403 when any user sharing the phone is
  SUSPENDED or BANNED (D-077); other duplicates remain allowed.
  `VerificationRequestDto.duplicatePhone` added; the queue passes it
  from `AdminService.verificationQueue`; `Verifications.tsx` shows a
  "Duplicate phone" badge and a review-pane warning. (OTP per-phone/IP
  rate limiting is D-099/D-101, Worker B.)

### I-18 Smaller fixes (D-115, part 2)
- Resubmit cap counts only `RESUBMIT` outcomes in 24 h (D-012); decision
  rows no longer consume it. `previousAttempts` counts attempts on that
  request only. Identity-change decide path: `listIdentityChanges` +
  `decideIdentityChange` (reason mandatory; scope-checked; 409 if already
  decided; approve validates university active + department belongs +
  student ID free at the new university, then atomically swaps
  university/department/studentId and `idCardKey`); new endpoints `GET
  /api/admin/identity-changes` and `POST
  /api/admin/identity-changes/{id}/decide`. Account deletion collects
  `idCardKey` + `profilePhotoKey` from ALL of a user's verification
  requests. `purgeIdImages` nulls stale `User.idCardKey`. `LoginAttempt`
  is now written (one row per login attempt; write failure swallowed so
  the login outcome stands).
- M6 (D-108, in V011): FKs `fk_appeal_appellant` (SET NULL),
  `fk_appeal_decided_by` (RESTRICT), `fk_modev_actor` (RESTRICT),
  `fk_tm_event_post` (CASCADE). Deliberately no FK for polymorphic
  targets or moderation snapshots (D-033 evidence must survive message
  deletion).
- M8 (D-108, in V011): `profiles.links` JSON → TEXT (UI treats links as
  free display text; a plain URL is not valid JSON, so MySQL would
  reject the write at runtime). `audit_logs.metadata` stays JSON
  (`AuditService` always writes `ObjectMapper` JSON).

### I-22 Text-muted contrast (done — trivial and safe)
- `frontend/src/design/tokens.css`: `--text-muted` set equal to
  `--text-secondary` in both themes (light 3.35:1 → 6.89:1, dark 3.85:1 →
  7.93:1, both above WCAG AA 4.5:1). One line per theme; the token is used
  only by `.eyebrow` and `.muted-caption` (non-essential text).

## Decisions

New entries **D-093–D-118** merged into
`docs/requirements/REQUIREMENTS_DECISION_LOG.md` (26 entries). One
collision found at integration: Worker B and Worker C both claimed
D-103. The coordinator kept B's D-103 (OTP rollback bug) and shifted
C's D-103–D-106 → D-104–D-107, D's D-107–D-114 → D-108–D-115, and E's
D-115 → D-116, updating every code comment, test, decision file and
report section consistently; verified each of D-093–D-118 is claimed
exactly once. D-088 (event kind/type, WARN) stays OPEN and untouched.
Pre-existing gaps D-006–D-009, D-042–D-049, D-057–D-059 in the log are
from earlier phases, not this merge.

## Migrations

- **V010** `V010__must_change_password_and_temp_expiry.sql` (Worker A,
  D-093–D-097): additive only. Adds `must_change_password TINYINT(1)
  NOT NULL DEFAULT 0` and `temp_password_expires_at DATETIME(3) NULL`
  to `user_credentials` and `admin_accounts`; widens
  `admin_recovery_codes.code_hash` `CHAR(64)` → `VARCHAR(255)` and adds
  `expires_at DATETIME(3) NULL`. Existing rows get flag 0 / null expiry
  — no existing credential is force-expired or force-flagged by the
  migration itself.
- **V011** `V011__worker_d_data_integrity.sql` (Worker D, D-108): additive
  only (one deliberate type change: `profiles.links` JSON → TEXT, M8).
  Adds `message_attachments.created_at DATETIME(3) NOT NULL DEFAULT
  CURRENT_TIMESTAMP(3)`; adds the four M6 FKs above.
- **V001–V008 unchanged** (verified: `git diff e6ff354` on those files is
  empty; V009 likewise). No migration was rewritten. No new Maven
  dependency (`pom.xml` untouched).

## Security review per area

- **Admin auth:** change-password requires MFA (a temp password alone
  cannot become a persistent credential); must-change flag enforced by a
  session-attribute filter with explicit escape hatches; temp passwords
  expire in 24 h and fail closed; recovery codes are now salted Argon2id
  with 90-day expiry and legacy codes are dead rows. Remaining: no
  admin password-change *reset* flow beyond the existing moderator reset;
  `createModerator` initial password does not force a change (D-094);
  recovery-code verification costs up to 10 Argon2id verifications per
  login (accepted, D-096); `/api/admin/auth/**` remains CSRF-exempt as
  the whole admin-auth prefix already was (flagged, not changed).
- **OTP / rate limiting:** per-user, per-phone and per-IP caps all engage
  (the D-103/D-117 rollback bug is the headline fix — guessing was
  unbounded before); XFF trusted only from configured proxies; atomic
  multi-key acquires; enumeration oracles closed (uniform 200s,
  byte-identical login errors). Remaining: in-memory limiter per D-003
  (per-process, lost on restart; a second instance needs a shared
  limiter, D-051); CIDR trusted-proxy ranges not implemented (exact
  addresses only); OTP guess bound across resends ≈30/hour by design.
- **File uploads:** EXIF stripped dependency-free on every image path;
  magic-byte sniffing hardened; traversal guards on all storage
  operations including `size()`; header injection closed;
  soft-deleted-post download hole closed; owner-only attachment delete
  added. Remaining: XMP/PNG text chunks not stripped; no backfill of
  already-stored images; no per-user daily quota; count-check/store race
  (one extra file, not a breach). Also noted, not changed:
  `PostService.getPost` returns any post to any verified student
  regardless of feed scope (attachment downloads inherit that rule);
  the 413 message says "10 MB limit" even for a 2 MB photo.
- **Data integrity:** `MessageAttachment.createdAt` now persists and is
  returned; M6 FKs and M8 JSON→TEXT decided and applied. Remaining:
  `analytics()` uses MySQL-only `DATE()`/`DATE_SUB()`/`TIMESTAMPDIFF`
  and cannot run on H2 at all — a genuine run still needs real MySQL.
- **Audit:** same-transaction design (D-037) kept; consequence is that
  `ADMIN_LOGIN_FAILED` rows for failed TOTP/email-OTP attempts roll back
  with the throwing transaction (documented above, not silently fixed).

## Test results (coordinator's own runs on the settled tree)

- Backend `mvn -o test`: **214/214 green, BUILD SUCCESS** (93 at Phase 2
  close + 121 new/changed; log `docs/phase-reports/07-backend-tests.log`,
  gitignored). New test classes: `MustChangePasswordTest` 12,
  `MustChangePasswordFilterTest` 4, `MustChangePasswordMigrationTest` 3,
  `AdminEmailOtpAttemptsTest` 3, `IpResolverTest` 8, `LoginThrottleTest`
  12 (7 pre-existing + 5 new), `OtpRateLimitTest` 9, `ExifStripperTest`
  9, `UploadValidatorTest` 12, `LocalStorageAdapterTest` 5,
  `UploadSecurityTest` 15, `Phase3WorkerDTest` 21,
  `WorkerDControllerGuardsTest` 6, `OcrUniversityScoreTest` 5,
  `AdminIpCidrTest` 4, `MigrationV011Test` 6.
- Frontend: `npm run typecheck` clean, `npm run build` succeeds,
  Vitest **108/108** (94 Phase 2 + 14 new: `phase3-auth.test.tsx` 6,
  `worker-d.test.tsx` 6, `users-role-gating.test.tsx` 2).
- Playwright smoke: **2/2**. Vite dev server: landing and
  `/admin/login` both 200 (server was down after a runtime restart;
  restarted with `nohup npm run dev`, log `/tmp/vite-dev.log`).
- All previously passing tests kept green. Two regressions caused by the
  coordinator's D-118 change (pre-existing tests clicking SUSPEND without
  an auth context) were fixed by rendering those tests as a Platform
  Admin — test-only change, assertions unchanged — and the suite re-ran
  108/108. One pre-existing test (`emailOtpAttemptCounterSurvivesFreshRequest`)
  needed `@Transactional(NOT_SUPPORTED)` to model production transactions
  (see D-117); its assertions are unchanged.

## Deferred / not done (with reasons)

- I-19 live-MySQL integration tests — blocked by the sandbox
  Java→MySQL interception; V010/V011 and the V006 `DELIMITER $$`
  trigger block can only be proven by a genuine Flyway run.
- I-20/I-21 E2E/performance — out of scope.
- D-088 event kind/type storage and WARN moderation — deferred, untouched.
- Identity-change submit path still takes no ID image (D-017 mentions
  one); recommend-suspension UI never existed; `updateMe`/photo/cover
  left without `requireVerified` (own-profile edits, not in audit §4.3).
- `createModerator` initial password does not force a change (D-094).
- XMP/PNG text-chunk stripping, backfill of stored images, per-user
  daily upload quota, per-room cumulative attachment cap — see §P1.
- CIDR trusted-proxy ranges; cross-row OTP guess counter; shared
  limiter (D-051).

## Remaining limitations

- **No live MySQL verification** (standing limitation): sandbox
  Java→MySQL interception unchanged; Flyway never ran; `unilink_dev`
  has no tables; no workflow has run against a live backend; V010/V011
  proven only statically + on H2.
- `analytics()` cannot run on H2 (MySQL-only date functions) — its
  scoped aggregates are proven only by static predicate checks + live H2
  predicate checks.
- Failed-auth audit rows may be lost to transaction rollback (see §P1).
- Recovery-code verification cost (up to 10 Argon2id checks per login)
  accepted, not optimised.

## Incidents during Phase 3

- VM/service restarts wiped the toolchain (Java/Maven/MySQL/Tesseract)
  twice during the work; `~/.m2` and `~/workspace` survived; the
  platform replay restored Java 21.0.13 + Maven 3.8.7. MySQL was not
  restored in this session and is not needed (all backend tests are H2).
  `mvn clean` does not work offline (clean plugin not cached) — `rm -rf
  target` was used instead.
- Parallel workers sharing one tree caused transient non-compiling states
  and one concurrent-build race on shared `target/` (27 tests failed
  with `NoClassDefFoundError`, clean re-run passed 27/27). Workers also
  made three minimal repairs to each other's files to keep the tree
  compilable (duplicate `NotificationEventListener` methods,
  `IdentityChangeRequestDto.newDepartmentId` type, `isRecoveryCodeExpired`
  visibility) — all reviewed at integration and preserved.
- One worker's test file vanished once after successful writes (cause
  unknown, possibly a filesystem sync glitch around a restart); it was
  rewritten and verified on disk.

## Files changed for the commit (98 files)

Backend main (43 modified + 5 new): `admin/AdminAuthService`,
`admin/AdminDtos`, `admin/AdminService`,
`admin/api/AdminAuthController`, `admin/api/AdminController`,
`config/SecurityConfig`, `content/PostService`,
`content/api/PostController`, `identity/api/AuthController`,
`identity/domain/AdminAccount`, `identity/domain/AdminRecoveryCode`,
`identity/domain/MessageAttachment`,
`identity/domain/UserCredential`, `identity/dto/AuthDtos`,
`identity/repo/AdminEmailOtpRepository`,
`identity/repo/AttachmentRepository`,
`identity/repo/PasswordResetTokenRepository`,
`identity/repo/PhoneOtpRepository`, `identity/repo/UserRepository`,
`identity/repo/VerificationAttemptRepository`,
`identity/repo/VerificationRequestRepository`,
`identity/service/AccountDeletionService`,
`identity/service/AuthService`, `identity/service/OcrJobService`,
`identity/service/OtpService`, `integration/ocr/OcrProvider`,
`integration/ocr/TesseractOcrAdapter`, `jobs/ScheduledJobs`,
`messaging/MessagingService`,
`messaging/api/MessagingRestController`,
`moderation/ModerationService`,
`notification/NotificationEventListener`,
`notification/NotificationService`,
`notification/api/NotificationController`, `profile/ProfileService`,
`profile/api/ProfileController`, `search/api/SearchController`,
`security/LoginThrottle`, `storage/LocalStorageAdapter`,
`storage/UploadValidator`, `team/TeamService`,
`team/api/TeamController`, `application.yml`; new
`security/IpResolver`, `security/MustChangePasswordFilter`,
`storage/ExifStripper`, `V010`, `V011`.

Backend tests (5 modified + 13 new files): modified
`integration/AdminAuthSecurityTest`, `integration/Phase2ServiceTest`,
`security/LoginThrottleTest`, `storage/LocalStorageAdapterTest`,
`storage/UploadValidatorTest`; new `admin/AdminIpCidrTest`,
`identity/OtpRateLimitTest`,
`integration/AdminEmailOtpAttemptsTest`,
`integration/MigrationV011Test`,
`integration/MustChangePasswordMigrationTest`,
`integration/MustChangePasswordTest`,
`integration/Phase3WorkerDTest`,
`integration/UploadSecurityTest`,
`integration/ocr/OcrUniversityScoreTest`,
`messaging/api/WorkerDControllerGuardsTest`,
`security/IpResolverTest`, `security/MustChangePasswordFilterTest`,
`storage/ExifStripperTest`.

Docs (1 modified + 12 new): modified
`docs/requirements/REQUIREMENTS_DECISION_LOG.md` (D-093–D-118); new
`07-decisions-a.md` … `07-decisions-f.md`,
`07-section-a.md` … `07-section-e.md`, and this report
`07-phase3-security.md`.

Frontend (15 modified + 5 new): modified `admin/AdminLayout`,
`admin/main`, `admin/pages/Totp`, `admin/pages/Users`,
`admin/pages/Verifications`, `api/client`, `api/types`,
`app/Layout`, `app/auth`, `design/tokens.css`,
`features/auth/Login`, `features/settings/SettingsPage`,
`main`, `test/api-contracts.test`, `test/fix-worker.test`; new
`admin/pages/AdminChangePassword`, `features/auth/PublicAppeal`,
`test/phase3-auth.test`, `test/users-role-gating.test`,
`test/worker-d.test`.

No deployment config touched. No Phase 3 work beyond this report's
scope. Do not start deployment; do not start another phase.
