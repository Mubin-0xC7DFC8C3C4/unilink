# Phase 3 Worker B report — OTP & rate limiting (2026-11-17)

Worker B (P1 — OTP & rate limiting). Scope: `AuthService.resendPhoneOtp`,
`IpResolver` (new), `LoginThrottle` internals, plus one genuine
brute-force bug found while proving the requirement
(`OtpService` attempt counters, D-103). Decisions D-099–D-103 are
in `07-decisions-b.md`. No commit made (coordinator reviews and commits).
No Flyway migration created; no deployment config touched; event kind/type
storage and the WARN moderation action (D-088) not implemented.

## Completed

### 1. Per-phone OTP resend cap (D-099, audit I-17)
- `backend/src/main/java/com/unilink/identity/service/AuthService.java:103-131`
  (`resendPhoneOtp`): new `otp-resend-phone:<phone>` key, 5/hour, alongside
  the existing 5/hour per user and 10/hour per IP, plus the 60 s per-user
  cooldown. Unknown users and already-verified phones still get the same
  generic 200 with no OTP sent and no quota consumed (anti-enumeration
  behaviour unchanged).
- `AuthService.java:77-83` (`register`): the registration OTP send now
  also seeds the per-phone bucket (best-effort, like the existing per-user
  seed). Registration is never blocked by the cap (D-077 soft uniqueness).
- Test: `OtpRateLimitTest.resendPerPhoneCapBlocksSixthAccountSharingPhone`
  — 6 accounts sharing one phone, one resend each: first 5 return 200,
  6th returns 429, and the 6th sends no OTP (OTP value unchanged).

### 2. Forwarded-IP handling via IpResolver (D-100)
- New `backend/src/main/java/com/unilink/security/IpResolver.java`
  (whole file): `X-Forwarded-For` honoured only when the direct peer is a
  configured trusted proxy; default `127.0.0.1, ::1`, configurable via
  `app.security.trusted-proxies` (`TRUSTED_PROXIES` env). First header
  entry is the client; `unknown`/blank/non-IP values skipped; hostnames
  never resolved; bracketed IPv6 and IPv4 `:port` accepted.
- Config: `backend/src/main/resources/application.yml:81`
  (`trusted-proxies: ${TRUSTED_PROXIES:127.0.0.1,::1}`).
- All rate-limit/audit call sites now use it; no raw `getRemoteAddr()` or
  direct `X-Forwarded-For` read remains outside `IpResolver`:
  - `AuthController.java` — resend, login, password-reset request,
    account deletion (constructor now takes `IpResolver`).
  - `AdminAuthController.java` — login, TOTP enrol/verify, email-OTP
    verify (constructor now takes `IpResolver`).
  - `AdminController.java:21-25` — `ip()` now delegates to `IpResolver`;
    the old code trusted `X-Forwarded-For` from any client, so audit IPs
    could be forged.
- Tests: `IpResolverTest` (8 tests) — trusted/untrusted proxy, IPv6
  loopback both spellings, `unknown`/invalid fallback, bracketed IPv6 +
  IPv4 port, configurable list, hostname rejection. Plus two HTTP-level
  tests in `OtpRateLimitTest`: distinct forwarded IPs from a trusted proxy
  all succeed (11 resends); varying only a spoofed header from an
  untrusted peer still hits the shared 10/hour IP cap on the 11th resend.

### 3. Atomic rate-limit operations (D-101)
- `backend/src/main/java/com/unilink/security/LoginThrottle.java` — all
  mutations (`recordFailure`, `recordSuccess`, `tryAcquire`,
  `resetForTests`) now run under one lock; new `tryAcquireAll` acquires
  every key in one critical section (all-or-nothing, except the first
  over-cap key is locked, mirroring single-key `tryAcquire`); the resend
  cooldown is folded into the same atomic call. Single-key semantics are
  unchanged (5 failures → 15 min lock, window reset, success clears).
- `AuthService.resendPhoneOtp` now does one `tryAcquireAll` for the
  user + phone + IP keys instead of two sequential `tryAcquire` calls
  (the old pair could consume user quota without sending, and two
  concurrent resends could both slip past a pre-check).
- Tests added to `LoginThrottleTest`: `tryAcquireAllConsumesAllKeysOrNone`
  (failed call leaks zero quota — proven by counting the other key to its
  cap exactly), `tryAcquireAllCooldownConsumesNothing`, and three
  concurrency tests (20 parallel threads, latch-started):
  `concurrentFailuresStillLockAfterFive`,
  `concurrentTryAcquireNeverExceedsCap` (exactly 5 of 20 allowed),
  `concurrentTryAcquireAllIsAtomic` (exactly 5 of 20 allowed, both keys
  exactly at cap afterwards).

### 4. Password-reset OTP send cap (D-102)
- `AuthService.java:179-192` (`requestPasswordReset(String email, String
  ip)`): 5/hour per email + 10/hour per IP via `tryAcquireAll`; a capped
  request is dropped silently, controller still returns the generic 200.
  Only caller `AuthController.resetRequest` updated to pass the resolved
  IP. The verify side already capped at 5 attempts per token.
- Test: `OtpRateLimitTest.passwordResetRequestsAreCappedSilently` — 6
  requests all return the same generic 200; the 6th sends no OTP (stored
  OTP value unchanged); unknown email returns the same 200 with nothing
  sent.

### 5. Enumeration / brute-force verification
- Login throttle engages: `OtpRateLimitTest.loginLocksAfterFiveFailures`
  — 5 wrong passwords → 400 `INVALID_CREDENTIALS`; 6th → 429; correct
  password while locked → 429.
- No login enumeration: `OtpRateLimitTest.loginErrorIsIdenticalForUnknownEmail`
  — unknown-email and wrong-password responses are byte-identical.
- OTP resend anti-enumeration still holds: pre-existing
  `OtpResendTest.resendUnknownUserReturnsGeneric200WithoutSending` and
  `resendAlreadyVerifiedPhoneReturnsGeneric200WithoutSending` pass
  unchanged (no OTP sent for either).
- Phone-OTP brute force: `OtpRateLimitTest.wrongOtpGuessesAreCapped` — 5
  wrong guesses → 400 `OTP_INVALID`; 6th and even the correct code
  afterwards → 400 `OTP_ATTEMPTS_EXCEEDED`.
- TOTP brute force: `OtpRateLimitTest.totpVerifyLocksAfterFiveFailuresAtHttpLayer`
  — full HTTP flow (admin login → enrol → login → 5 wrong codes → 400
  `INVALID_TOTP`, 6th → 429). Service-level proof already existed in
  `AdminAuthSecurityTest.totpVerifyLocksAfterFiveFailures` /
  `totpEnrolLocksAfterFiveFailures` / `emailOtpRequestLocksAfterFiveRequests`;
  this test proves the throttle is wired through the controller/session
  flow. Those pre-existing tests pass unchanged.

### 6. OTP wrong-guess counters actually engage now (D-103 — real bug found)
- Root cause: `OtpService.verifyPhoneOtp` / `verifyPasswordResetOtp`
  incremented the attempt counter with `setAttempts(n+1)` + `save` and
  then threw `OTP_INVALID` — but the throw rolls back the enclosing
  `@Transactional` service method, so the increment was rolled back too.
  Every wrong guess saw `attempts = 0`: the 5-attempt cap could never
  engage and guessing was unbounded within the code lifetime (5 min
  phone OTP, 15 min reset OTP). No pre-existing test covered it (the
  only `OTP_ATTEMPTS_EXCEEDED` test in the tree was the admin email-OTP
  one, which passes only because its test class is `@Transactional` —
  see "Deferred" below).
- Fix: atomic `UPDATE … SET attempts = COALESCE(attempts, 0) + 1` via
  `@Modifying` + `REQUIRES_NEW` repository methods, called before
  throwing:
  - `backend/src/main/java/com/unilink/identity/repo/PhoneOtpRepository.java:13-23`
    (`incrementAttempts`)
  - `backend/src/main/java/com/unilink/identity/repo/PasswordResetTokenRepository.java:11-19`
    (`incrementAttempts`)
  - `backend/src/main/java/com/unilink/identity/service/OtpService.java:45-48`
    (phone) and `:89-92` (password reset)
- Tests: `OtpRateLimitTest.wrongOtpGuessesAreCapped` (failed before the
  fix with `OTP_INVALID` on the 6th guess — the test is what found the
  bug) and new `OtpRateLimitTest.wrongPasswordResetGuessesAreCapped`
  (same shape for the reset-code path).
- NOT fixed, flagged: the identical bug in
  `AdminAuthService.verifyEmailOtp` (admin email-OTP verify). That file
  belongs to Worker A and was mid-rewrite (non-compiling) when found, so
  per the ownership rule it was left untouched. Same fix pattern
  applies: `AdminEmailOtpRepository` needs an atomic `REQUIRES_NEW`
  increment, or the verify must stop relying on a counter written in a
  transaction that the failure itself rolls back. Also note
  `AdminAuthSecurityTest.emailOtpAttemptCounterSurvivesFreshRequest`
  gives false confidence here: it passes because the test class is
  `@Transactional` (its own transaction never rolls back mid-test), not
  because the counter survives a production rollback.

## Test results

- **Full backend suite (worktree, HEAD `e6ff354` + Worker B changes
  only): 115/115 green, BUILD SUCCESS** — baseline 93 + 22 new.
  Per class: `OtpRateLimitTest` 9/9, `IpResolverTest` 8/8,
  `LoginThrottleTest` 12/12 (7 pre-existing + 5 new),
  `OtpResendTest` 5/5, `AdminAuthSecurityTest` 6/6,
  `AdminSecurityFixesTest` 12/12, `Phase2ServiceTest` 20/20,
  `ArchitectureTest` 3/3, `ApplicationContextTest` 1/1, all others green
  (full per-class list in the run output). Command: from `backend/`:
  `env -u https_proxy -u HTTPS_PROXY -u http_proxy -u HTTP_PROXY
  MAVEN_OPTS="-Xmx512m" mvn -o -s /home/hatch/.m2/settings.xml test`.
- New tests: `IpResolverTest` 8, `LoginThrottleTest` +5 (3 concurrency +
  2 `tryAcquireAll`), `OtpRateLimitTest` 9. Total new: 22.
- Pre-existing tests in the touched areas (`OtpResendTest`,
  `AdminAuthSecurityTest`, `LoginThrottleTest` original 7) were not
  weakened; the only `LoginThrottleTest` change is additive methods.
- First full-suite run (before the D-103 fix): 114 run, 3 failures, all
  in new `OtpRateLimitTest` — 1 real bug (D-103, now fixed) and 2 test
  bugs (whole-body comparison tripped by per-request timestamp/traceId;
  `MvcResult.getSession()` returns the pre-filter request, so the TOTP
  test now carries the `UL_SESSION` cookie like a real client). Both
  test bugs are fixed in the test code, not by weakening assertions.
  After the fixes the subset run was 40/40 green, then the full suite
  115/115 green.

## Deferred / not done (with reasons)

- **No live MySQL verification.** The sandbox still blocks Java→MySQL;
  all proof is H2 unit/MockMvc tests. Flyway never ran; no workflow has
  run against a live backend. (Same standing limitation as Phase 2.)
- **Admin email-OTP verify cap (same D-103 bug class) left untouched.**
  `AdminAuthService.verifyEmailOtp` has the identical rollback problem
  (setAttempts+save, then throw inside `@Transactional`), so in
  production its 5-attempt cap never engages either. Left alone because
  Worker A owns `AdminAuthService` and was mid-rewrite on that exact
  file. Fix pattern is documented under D-103; needs a MockMvc-level
  test to prove it (the existing service-level test passes only because
  its class is `@Transactional`).
- **Failed-TOTP audit rows likely roll back too (flagged, not fixed).**
  `AdminAuthService.verifyTotpOrRecovery` / `enrolTotp` are
  `@Transactional` and call `audit.write` (REQUIRED, joins) before
  throwing on a wrong code — the `ADMIN_LOGIN_FAILED` audit row is
  rolled back with the transaction. Same file/ownership reason as above;
  worth a look by whoever owns D-037, since failed admin-login logging
  is a stated requirement.
- **Per-IP cap for password reset relies on the resolved IP only.**
  Nothing further needed; recorded here so the coordinator sees the
  password-reset path now keys on both email and IP.
- **CIDR trusted-proxy ranges not implemented** (D-100): exact addresses
  only. The planned nginx deployment terminates on 127.0.0.1, so the
  default covers v1.0; a multi-proxy deployment should extend
  `IpResolver` rather than bypass it.
- **OTP guess bound across resends.** Each resend mints a fresh OTP row
  with a fresh 5-guess allowance, so the per-hour bound on phone-OTP
  guessing is 5 resends × 5 guesses (+ the initial 5) ≈ 30 guesses/hour
  per user/phone, gated by the resend caps. A cross-row guess counter
  would tighten this but needs an `OtpService` change outside the
  resend/throttle scope; the current bound is the pre-existing design
  and is now enforced by caps that actually engage.
- **In-memory limiter (D-003).** Buckets are still per-process and lost
  on restart; unchanged by design. A second application instance would
  need a shared limiter (see D-051).
- **Login `ip` parameter still unused in `AuthService.login`.** The
  controller now passes the resolved IP, but the service keys the login
  throttle on email only and writes no audit row there. Left untouched:
  login-flow internals belong to Worker A.
- **Admin IP allow-list (`AdminIpAllowlist`) still dead code** (audit
  I-16). Out of scope for this worker; not implemented, not deleted.

## Security notes

- `AdminController` previously trusted `X-Forwarded-For` unconditionally
  for audit IPs — any client could forge the IP in the audit log. Now
  gated by `IpResolver` (D-100).
- The OTP resend path no longer has a partial-consumption hole
  (user quota consumed on an IP-cap failure) and no check-then-act race
  between the cooldown/lock pre-checks and the consume (D-101).
- No response distinguishes unknown users, already-verified phones, or
  capped password-reset requests from a successful send; all new caps
  fail closed with the pre-existing generic messages.
- Recovery codes remain unsalted SHA-256 and never expire (Phase 2
  note); untouched by this worker.

## Verification environment

- The shared working tree could not be used for the full-suite run at
  verification time: parallel Phase 3 workers (A, D, …) had
  in-progress, non-compiling edits in it (136 errors in
  `AdminAuthService.java` — Worker A's must-change refactor,
  `AdminDtos`/`AuditService`/`AdminBootstrap` knock-ons, and duplicate
  methods in `NotificationEventListener`). Zero compile errors are in
  Worker B files.
- Verification was therefore done in an isolated worktree
  (`/tmp/unilink-b`, HEAD `e6ff354` + Worker B changes only, re-applied
  from the same edits). The full backend suite was run there with the
  prescribed command: **115/115 green, BUILD SUCCESS**. Shared-tree
  files were not modified by the worktree run. The worktree was removed
  after the run; all Worker B changes live in the shared tree.
- Worker B files in the shared tree were verified intact after the
  other workers' edits (`IpResolver` references present in all three
  controllers; `tryAcquireAll` / `otp-resend-phone` present in
  `AuthService`/`LoginThrottle`). The coordinator should re-run the full
  suite on the merged tree before committing, since other workers'
  in-flight edits interleave with Worker B's in `AuthService`,
  `AuthController`, `AdminAuthController` and `AdminController` (notably
  Worker A's D-093/D-094 must-change work and Worker D's changes
  content-type work in those same files).

## Files changed (Worker B only)

- `backend/src/main/java/com/unilink/security/IpResolver.java` (new)
- `backend/src/main/java/com/unilink/security/LoginThrottle.java`
  (synchronized mutations + `tryAcquireAll`)
- `backend/src/main/java/com/unilink/identity/service/OtpService.java`
  (D-103 atomic attempt increments)
- `backend/src/main/java/com/unilink/identity/repo/PhoneOtpRepository.java`
  (D-103 `incrementAttempts`)
- `backend/src/main/java/com/unilink/identity/repo/PasswordResetTokenRepository.java`
  (D-103 `incrementAttempts`)
- `backend/src/main/java/com/unilink/identity/service/AuthService.java`
  (per-phone resend cap, phone-bucket seed at registration,
  password-reset caps)
- `backend/src/main/java/com/unilink/identity/api/AuthController.java`
  (`IpResolver` injection; resolved IP for resend/login/reset/delete)
- `backend/src/main/java/com/unilink/admin/api/AdminAuthController.java`
  (`IpResolver` injection; resolved IP for login/TOTP/email-OTP)
- `backend/src/main/java/com/unilink/admin/api/AdminController.java`
  (`ip()` delegates to `IpResolver`)
- `backend/src/main/resources/application.yml` (`trusted-proxies`)
- `backend/src/test/java/com/unilink/security/IpResolverTest.java` (new, 8)
- `backend/src/test/java/com/unilink/security/LoginThrottleTest.java`
  (+5: 2 atomicity, 3 concurrency)
- `backend/src/test/java/com/unilink/identity/OtpRateLimitTest.java`
  (new, 9)
- `docs/phase-reports/07-decisions-b.md` (D-099–D-103)
- `docs/phase-reports/07-section-b.md` (this file)
