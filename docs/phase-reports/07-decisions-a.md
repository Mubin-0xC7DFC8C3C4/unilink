# Phase 3 decisions — Worker A (P0 Authentication & account security)

Date: 2026-11-17. Repo HEAD `e6ff354` at start; concurrent Phase 3 workers
share the tree (their decisions use other number ranges; this file holds
only Worker A's D-093–D-098). Do not edit
`docs/requirements/REQUIREMENTS_DECISION_LOG.md` directly — the coordinator
merges this file in.

### D-093 Admin password-change endpoint (ADOPTED)
- New endpoint `POST /api/admin/auth/change-password` (`AdminAuthController.java`,
  service `AdminAuthService.changePassword`): authenticated admin only, and
  MFA-verified only (the controller rejects a merely password-authenticated
  principal with 403). Body `{oldPassword, newPassword}` (reuses the student
  `ChangePasswordRequest`); new password at least 12 characters (D-031 rule),
  must differ from the current password (`SAME_PASSWORD`), wrong current
  password is `INVALID_CREDENTIALS`. Success clears the must-change flag and
  any temporary-password expiry, invalidates all sessions for the account
  (D-013), is audited as `ADMIN_PASSWORD_CHANGED`, and the caller must log in
  again. No such endpoint existed (audit I-16, Phase 2 closure note).
- The admin rule is length-only (≥12), matching `createModerator`; the
  student rule (≥8 with upper/lower/digit, FR-AUTH-03) is not imposed on
  admins, and no new composition rule was invented.

### D-094 Mandatory password change after an admin reset (ADOPTED)
- Flag `must_change_password` on `user_credentials` and `admin_accounts`
  (V010, D-097). Set by both issuers: moderator credential reset
  (`AdminService.resetModeratorCredentials`) and `FORCE_RESET`
  (`AdminService.changeUserState`). The initial password set by
  `createModerator` is NOT flagged — it is chosen by the Platform Admin at
  creation, not a generated temporary password; flagging it is a possible
  follow-up, not part of this change.
- Enforcement mechanism: a session attribute `MUST_CHANGE_PASSWORD`, set at
  login (student `AuthController.login`, admin `AdminAuthController.login`
  and after TOTP/email-OTP verification) and checked by
  `MustChangePasswordFilter` (a `OncePerRequestFilter` registered in both
  security chains). A flagged session gets 403 `PASSWORD_CHANGE_REQUIRED`
  on every API call except the escape hatches: login, change-password,
  logout, session restore (`/me`, so the frontend learns the flag after a
  reload), CSRF priming, the health probe, the admin TOTP/email-OTP steps,
  and password-reset request/confirm. WebSocket (`/ws`) is blocked.
- Why a session attribute, not a principal field: no serialised principal
  shape changes; the restriction cannot outlive the session that created
  it; changing the password invalidates the session, which clears it. The
  flag is also returned on login and on `/me` (student `UserDto`, admin
  `principalDto`) so the frontend can redirect.
- Why the admin change-password endpoint additionally requires MFA: the
  filter allowlists the TOTP steps for a flagged session, so a holder of a
  temporary password must still pass the second factor before turning it
  into a persistent password. A temporary password alone can never change
  itself into a persistent credential.
- The student change-password endpoint already existed and is reused
  unchanged, except that it (and password-reset confirm) now clears the
  flag and any temporary-password expiry.

### D-095 Temporary passwords expire after 24 hours (ADOPTED)
- `temp_password_expires_at` on `user_credentials` and `admin_accounts`
  (V010). Both issuers set it to issue time + 24 h
  (`AuthService.TEMP_PASSWORD_VALIDITY`, shared so every issuer uses the
  same window). Covers the moderator-reset temporary password and the
  `FORCE_RESET` temporary password.
- Enforcement at login (student `AuthService.login`, admin
  `AdminAuthService.login`): authentication runs first, so a wrong password
  still yields the generic `INVALID_CREDENTIALS`; a correct but expired
  temporary password yields 400 `TEMP_PASSWORD_EXPIRED` with the message
  "Your temporary password has expired. Ask an administrator to issue a new
  one." (admin wording: "Ask a Platform Admin to issue new credentials.").
  An expired login is neither a throttle success nor a wrong-password
  failure, so it moves neither the success reset nor the lockout count.
  The admin must re-issue; there is no self-service renewal.
- A password change (either change-password endpoint, or password-reset
  confirm) clears the expiry. An already-logged-in session whose temporary
  password expires mid-session is NOT cut off by expiry alone — the
  change-password call itself still accepts the expired temporary password
  as the old password, because changing away from it is the desired
  outcome; the must-change restriction (D-094) still applies to that
  session until the change happens.

### D-096 Recovery codes: salted Argon2id hashing, 90-day expiry, legacy codes invalidated (ADOPTED)
- Storage: `AdminRecoveryCode.code_hash` now holds an Argon2id hash
  produced by the existing `PasswordEncoder` (Argon2id, OWASP parameters,
  D-014) instead of unsalted SHA-256. The column widens from `CHAR(64)` to
  `VARCHAR(255)` in V010 (an Argon2id hash is ~97 chars).
- Verification (`AdminAuthService.verifyTotpOrRecovery`): a code counts
  only while unused and unexpired; single-use behaviour is unchanged
  (used + `used_at` on success). Verification tries `encoder.matches`
  against unused codes only.
- Expiry policy: codes expire 90 days after enrolment
  (`RECOVERY_CODE_VALIDITY`, `expires_at` set at enrolment) or on TOTP
  re-enrolment, which deletes all prior codes. Pre-V010 rows have no
  `expires_at`; they fall back to `created_at` + 90 days, and a row with
  neither timestamp is treated as expired.
- Pre-existing codes: **invalidated**. Their plaintext cannot be recovered
  from unsalted SHA-256 hashes to re-hash, so they cannot be migrated. A
  legacy row (64-char hex digest, no `$argon2` prefix) is skipped during
  verification and can never match — the admin is not silently left on the
  old scheme. Recovery path: the admin's TOTP still works (codes are only
  the second-factor fallback); if TOTP is also lost, a Platform Admin
  resets the credentials, which forces TOTP re-enrolment and issues fresh
  Argon2id codes. The `"$argon2"` prefix guard also keeps
  `encoder.matches` from throwing on a non-Argon2 hash.
- Cost note: verification hashes each candidate with Argon2id (up to 10
  unused codes, ~50–100 ms each worst case). Acceptable for a login-time
  second factor; no change made.

### D-097 V010 migration contents (ADOPTED)
- File `backend/src/main/resources/db/migration/V010__must_change_password_and_temp_expiry.sql`,
  additive only; V001–V009 untouched:
  `user_credentials` / `admin_accounts` gain `must_change_password
  TINYINT(1) NOT NULL DEFAULT 0` and `temp_password_expires_at DATETIME(3)
  NULL`; `admin_recovery_codes` gains `expires_at DATETIME(3) NULL` and
  `code_hash` is widened to `VARCHAR(255)`. Existing rows get flag 0 and
  null expiry/expires_at, i.e. no existing credential is force-expired or
  force-flagged by the migration itself.
- Live Flyway verification is impossible in this sandbox (Java→MySQL
  interception, unchanged); the migration is proven the same way V008 was
  — a static test (`MustChangePasswordMigrationTest`) asserting the
  entities map the columns, V002/V003 never created them, and V010 adds
  them. A genuine Flyway run against real MySQL is still required before
  launch.

### D-098 Frontend must-change redirect (ADOPTED)
- Student: login response `mustChangePassword` redirects to `/settings`
  (not `/feed`); `AuthProvider` restore carries the flag from `/me`, and
  `Layout` redirects to `/settings` from any other page when the flag is
  set (Settings itself is exempt, so there is no loop). `SettingsPage`
  gains the change-password form the existing student endpoint never had
  UI for, plus a must-change banner; success signs the user out to `/login`
  (the server invalidates the session).
- Admin: TOTP verify response `mustChangePassword` redirects to the new
  `/admin/change-password` page (new `AdminChangePassword.tsx`, inside the
  guarded admin section); `AdminLayout` redirects there on a restored
  flagged session. The page posts to the new endpoint; success signs the
  admin out to `/admin/login`.
- `errorMessage` surfaces the server's message for 403
  `PASSWORD_CHANGE_REQUIRED` instead of the generic "You do not have
  permission" text. A 403 from a flagged session is a redirect cue, not a
  permission failure; the 401 session-expired redirect is unchanged and
  does not trigger for the change-password calls themselves (they are
  `/auth/**` paths).
