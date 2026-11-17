# Phase 3 report section — Worker A (P0 Authentication & account security)

Date: 2026-11-17. Repo HEAD `e6ff354` at start; working tree shared with
concurrent Phase 3 workers (their files are theirs; this section covers
only Worker A's changes). Decisions D-093–D-098 are in
`docs/phase-reports/07-decisions-a.md`. Nothing is committed by this worker.

## Completed

### 1. Admin password-change endpoint (D-093)
- `POST /api/admin/auth/change-password` — new route in
  `backend/src/main/java/com/unilink/admin/api/AdminAuthController.java:106`
  (service `AdminAuthService.changePassword`,
  `backend/src/main/java/com/unilink/admin/AdminAuthService.java:155`).
  Authenticated admin only, MFA-verified only (403 otherwise); current
  password + new password ≥12 chars (D-031); new must differ from current
  (`SAME_PASSWORD`); wrong current password is `INVALID_CREDENTIALS`.
  Success clears the must-change flag and temp expiry, invalidates all
  sessions (D-013), audits `ADMIN_PASSWORD_CHANGED`, and the caller logs
  in again. No such endpoint existed (audit I-16).
- Proof: `MustChangePasswordTest.adminChangePasswordSucceedsClearsFlagAndOldPasswordStopsWorking`
  (old password stops working, new password logs in, flag cleared) and
  `adminChangePasswordRejectsWrongCurrentAndWeakAndSame`.

### 2. Mandatory password change after an admin reset (D-094)
- Flag `must_change_password` on `user_credentials`
  (`backend/src/main/java/com/unilink/identity/domain/UserCredential.java:10`)
  and `admin_accounts`
  (`backend/src/main/java/com/unilink/identity/domain/AdminAccount.java:15`),
  added by V010 (see §Migration). Set from both issuers:
  `AdminService.resetModeratorCredentials`
  (`backend/src/main/java/com/unilink/admin/AdminService.java:488-491`)
  and `FORCE_RESET` in `AdminService.changeUserState`
  (`backend/src/main/java/com/unilink/admin/AdminService.java:193-198`).
  The stale "no must-change enforcement" comment on the reset method was
  corrected.
- Enforcement: session attribute `MUST_CHANGE_PASSWORD`, set at login
  (student `AuthController.java:46-47`, admin `AdminAuthController.java:60,75,92`)
  and checked by `MustChangePasswordFilter`
  (`backend/src/main/java/com/unilink/security/MustChangePasswordFilter.java:21`,
  registered in both chains in `SecurityConfig.java:96,117`). A flagged
  session gets 403 `PASSWORD_CHANGE_REQUIRED` on every API call except the
  documented escape hatches (login, change-password, logout, `/me`, CSRF
  priming, health, admin TOTP/email-OTP steps, password-reset
  request/confirm); `/ws` is blocked. The flag is returned on login and
  on `/me` (student `UserDto`, admin `principalDto`).
- The student change-password endpoint already existed and is reused; it
  and password-reset confirm now clear the flag and temp expiry
  (`AuthService.java:251` changePassword; confirmPasswordReset).
- Proof: `MustChangePasswordFilterTest` (4 tests: unflagged passes,
  flagged blocks feed/posts/admin dashboard/WebSocket, flagged allows
  change-password/logout/me and the auth steps) and
  `MustChangePasswordTest` reset/FORCE_RESET tests asserting the flag is
  set and login signals it.

### 3. Temporary-password expiry, 24 h (D-095)
- Column `temp_password_expires_at` on both credential tables (V010).
  Both issuers set issue time + 24 h via the shared constant
  `AuthService.TEMP_PASSWORD_VALIDITY`
  (`backend/src/main/java/com/unilink/identity/service/AuthService.java:41`).
- Enforcement at login: student `AuthService.login` and admin
  `AdminAuthService.login` reject a correct-but-expired temporary password
  with 400 `TEMP_PASSWORD_EXPIRED` and a clear message; a wrong password
  still yields generic `INVALID_CREDENTIALS`; expiry moves neither the
  throttle success reset nor the lockout count. The admin must re-issue.
- Proof: `MustChangePasswordTest.expiredModeratorTempPasswordRejectsLogin`
  and `expiredStudentTempPasswordRejectsLogin` (expiry set in the past,
  login rejected with `TEMP_PASSWORD_EXPIRED`); `plainAdminPasswordWithoutExpiryLogsInNormally`
  (null expiry is unaffected).

### 4. Recovery codes: Argon2id + 90-day expiry, legacy codes invalidated (D-096)
- Enrolment (`AdminAuthService.java:140-141`) stores
  `encoder.encode(code)` (Argon2id via the existing `PasswordEncoder`)
  with `expires_at` = now + 90 days (`RECOVERY_CODE_VALIDITY`,
  `AdminAuthService.java:23`); `code_hash` widens `CHAR(64)` →
  `VARCHAR(255)` in V010.
- Verification (`AdminAuthService.java:190-191,207`): unused codes only,
  expired codes skipped, legacy non-`$argon2` hashes skipped (never
  verified), `encoder.matches` for the rest; single-use unchanged
  (used + `used_at`). Expiry fallback for pre-V010 rows is
  `createdAt + 90 days`; a row with no timestamps counts as expired.
- Pre-existing codes: **invalidated, not migrated** — unsalted SHA-256
  hashes cannot be re-hashed without the plaintext. They remain as rows
  but can never verify. Recovery path: TOTP still works; if TOTP is also
  lost, a Platform Admin reset forces TOTP re-enrolment, which issues
  fresh Argon2id codes. This is recorded in D-096 and here, not silently
  left usable under the old scheme.
- Proof: `MustChangePasswordTest.enrolledRecoveryCodesAreArgon2HashedWithExpiryAndSingleUse`
  (hashes start with `$argon2`, expiry ~90 days, second use of the same
  code fails), `legacySha256RecoveryCodeIsInvalidated`,
  `expiredArgon2RecoveryCodeIsRejectedAndLiveOneAccepted`,
  `recoveryCodeExpiryFallsBackToCreatedAtPlus90Days`.

### Frontend (D-098)
- Student login redirects to `/settings` when `mustChangePassword` is set
  (`frontend/src/features/auth/Login.tsx`); `AuthProvider` restores the
  flag from `/me` (`frontend/src/app/auth.tsx`); `Layout` redirects to
  `/settings` from any other page (`frontend/src/app/Layout.tsx`).
  `SettingsPage` gains the change-password form (the student endpoint
  previously had no UI) plus a must-change banner
  (`frontend/src/features/settings/SettingsPage.tsx`); success signs out
  to `/login`.
- Admin TOTP verify redirects to the new `/admin/change-password` page
  (`frontend/src/admin/pages/Totp.tsx`); new page
  `frontend/src/admin/pages/AdminChangePassword.tsx` posts to the new
  endpoint; `AdminLayout` redirects there on a restored flagged session
  (`frontend/src/admin/AdminLayout.tsx`); route added in
  `frontend/src/admin/main.tsx`. `errorMessage` surfaces the server
  message for 403 `PASSWORD_CHANGE_REQUIRED`
  (`frontend/src/api/client.ts`).
- Proof: `frontend/src/test/phase3-auth.test.tsx` (6 tests: both login
  redirects, Settings form posts old+new to `/auth/change-password`,
  TOTP verify redirect, admin form posts to
  `/admin/auth/change-password`, error-message wording).

## Migration

- New file `backend/src/main/resources/db/migration/V010__must_change_password_and_temp_expiry.sql`,
  additive only; V001–V009 untouched. Adds `must_change_password
  TINYINT(1) NOT NULL DEFAULT 0` and `temp_password_expires_at DATETIME(3)
  NULL` to `user_credentials` and `admin_accounts`; widens
  `admin_recovery_codes.code_hash` to `VARCHAR(255)` and adds
  `expires_at DATETIME(3) NULL`. Existing rows get flag 0 / null expiry —
  no existing credential is force-expired or force-flagged by the
  migration itself.
- Proof: `MustChangePasswordMigrationTest` (3 tests: entity mappings,
  V002/V003 never created the columns, V010 adds them). A genuine Flyway
  run against real MySQL is still required (see §Not verified).

## Decisions recorded

D-093–D-098 in `docs/phase-reports/07-decisions-a.md` (endpoint, must-change
mechanism, 24 h temp expiry, recovery-code hardening + legacy invalidation,
V010 contents, frontend redirects). `createModerator`'s initial password is
deliberately NOT flagged (D-094).

## Test results

- Backend, targeted (real tree, 2026-11-17 ~17:30 +06, after the toolchain
  restore): `MustChangePasswordTest` **12/12**, `MustChangePasswordFilterTest`
  **4/4**, `MustChangePasswordMigrationTest` **3/3** — **19/19, BUILD SUCCESS**.
  One fix was needed on the way: the first run failed with
  `BadSqlGrammarException: Table "SPRING_SESSION" not found` in the six
  tests that exercise session invalidation (D-091) — fixed by adding
  `spring.session.jdbc.initialize-schema=always` to the test's properties,
  the same property the MockMvc tests already use.
- Backend, full suite (isolated copy of the tree at the same snapshot,
  `docs/phase-reports/07-backend-tests-a.log`): **210 run, 203 pass, 5
  failures, 2 errors — BUILD FAILURE, none of them mine.** The 7 are other
  workers' in-progress work: `OcrUniversityScoreTest` 5/5 fail (Worker B's
  OCR scoring expectations vs the current scorer), `Phase3WorkerDTest`
  analytics test errors on MySQL-only `DATE_SUB` SQL run against H2
  (Worker D), `ExifStripperTest` 1 error (`IndexOutOfBoundsException` in
  `ExifStripper.stripJpeg`, Worker E). All 19 of this worker's tests pass
  in that run, as do the Phase 2 regression classes (e.g.
  `AdminAuth401Test` 6/6, `AdminAuthSecurityTest` 6/6, `Phase2ServiceTest`
  20/20, `MigrationColumnsTest` 3/3).
- Frontend (real tree, 2026-11-17 ~17:45 +06): `npm run typecheck` clean,
  `npm run build` succeeds, Vitest **106/106** (94 Phase 2 + 6 new in
  `phase3-auth.test.tsx` + 6 from another worker's `worker-d.test.tsx`),
  across 8 files.

## Deferred / left undone

- Nothing in items 1–4 was left undone. Out of scope by instruction:
  event kind/type storage, the WARN moderation action (D-088), deployment
  and deployment config, rewriting any migration.
- `createModerator` initial password does not force a change (D-094
  records the choice; follow-up if the owner wants it flagged too).
- Recovery-code verification cost (up to 10 Argon2id verifications per
  login attempt) was accepted, not optimised (D-096).
- CSRF: `/api/admin/auth/**` (including the new change-password endpoint)
  remains CSRF-exempt, as the whole admin-auth prefix already was; the
  MFA requirement, not CSRF, is what protects the endpoint. No change
  made; flagged for the coordinator if CSRF policy for auth endpoints is
  revisited.

## Security notes

- Admin change-password requires MFA (D-093/D-094): a temporary password
  alone cannot become a persistent credential.
- Expired temporary passwords fail closed at login with a non-enumerating
  error for wrong passwords; the expired message is only returned after
  the password itself verified.
- Legacy SHA-256 recovery codes are dead rows, never verifiable — no
  fallback to the old scheme exists anywhere in the verify path.
- Temporary passwords are still generated the pre-existing way (12-char
  UUID substring + `A1`, ~48 bits, returned once, never logged/stored in
  plaintext); generation strength was not in scope and is unchanged.

## Not verified

- **No live MySQL verification.** The sandbox blocks Java→MySQL
  (unchanged); Flyway has never run; V010 is proven only by the static
  migration test + H2 entity mapping. A genuine boot against real MySQL
  (Flyway applying V010, login + reset + change-password end to end) is
  still required before launch.
- No live-browser check: frontend proof is Vitest with mocked fetch only.

## Notes (environment / concurrency)

- The shared tree was being edited concurrently by other Phase 3 workers
  while this work ran. Two transient breakages were observed and are NOT
  this worker's doing: (a) `AdminAuthController` referenced a new
  `IpResolver` class without importing it — one import line was added to
  restore compilation (the other worker's design, preserved, including
  their `ipResolver.resolve(req)` call in the new change-password
  endpoint); (b) a duplicate-method edit in `NotificationEventListener`
  broke Lombok processing for the whole compilation until its author
  fixed it; (c) a half-written `Phase3WorkerDTest.java` broke
  test-compilation for every runner until its author finished it.
- A VM replacement wiped the toolchain mid-task (Java/Maven gone,
  `/tmp` wiped); the toolchain was being restored by the system apt
  replay when this report was written — backend re-verification after
  the restore is recorded in §Test results.
- The full backend suite could not be run cleanly in the shared tree
  during the concurrent-edit window: a run in the live tree failed with
  I/O errors and missing classpath resources because another worker's
  suite starts with `rm -rf target` in the same tree. The clean full
  result above comes from an isolated copy (`~/workspace/.scratch-a`,
  removed after the run); the targeted 19/19 was also verified in the
  live tree itself.
