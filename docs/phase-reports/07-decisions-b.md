# Phase 3 Worker B decisions — OTP & rate limiting (2026-11-17)

Worker B (P1 — OTP & rate limiting). Decisions D-099–D-103. These are
written here, not in `docs/requirements/REQUIREMENTS_DECISION_LOG.md`,
because parallel Phase 3 workers share that file; the coordinator merges
them. Status values follow the log's convention (ADOPTED / OPEN /
PROPOSED). No Flyway migration was created; no deployment config was
touched; event kind/type storage and the WARN moderation action (D-088)
were not implemented.

### D-099 OTP resend cap is per phone as well as per user and per IP (ADOPTED for v1.0)
- `POST /api/auth/otp/resend` now enforces three caps, all per hour:
  5 per user, **5 per phone** (new), 10 per IP — plus the existing 60 s
  cooldown per user. Caps unchanged from D-081 for user and IP.
- Rationale: D-077 keeps soft phone uniqueness, so several accounts may
  legitimately share one phone (family phone). Without a per-phone cap,
  each of those accounts could resend 5/hour, so one phone could receive
  an unbounded number of OTP SMS in an hour — an SMS-flooding / cost
  vector against the victim holding that phone, and a way to brute-force
  OTP guesses across accounts sharing the number. Audit I-17 requires
  "rate-limit OTP sends per phone/IP".
- Registration sends count towards the phone cap: `register()` seeds the
  `otp-resend-phone:<phone>` bucket (best-effort, return value ignored,
  exactly like the existing per-user seed). Registration itself is never
  blocked by the cap — a shared family phone must still be able to sign
  up; only repeat sends are throttled.
- The phone key is the stored canonical phone (`01[3-9]xxxxxxxx`, D-082),
  so the cap cannot be dodged by re-formatting the number. A null/blank
  phone (defensive; registration requires one) simply skips the phone
  key rather than failing the resend.

### D-100 X-Forwarded-For is honoured only from trusted proxies (ADOPTED for v1.0)
- New `com.unilink.security.IpResolver`, used by every call site that
  resolves a client IP: `AuthController` (resend, login, password-reset
  request, account deletion), `AdminAuthController` (login, TOTP
  enrol/verify, email-OTP verify) and `AdminController` (audit IP for
  all admin actions). No raw `getRemoteAddr()` and no direct
  `X-Forwarded-For` read remains outside `IpResolver`.
- Rule: the header is honoured only when the direct peer
  (`getRemoteAddr()`) is a configured trusted proxy. Default trusted set
  is `127.0.0.1, ::1` — the nginx front-end of D-1 terminates on
  127.0.0.1, so the default is correct for the planned deployment.
  Configurable via `app.security.trusted-proxies`
  (`TRUSTED_PROXIES` env override) in `application.yml`.
- The first header entry is the client; later entries are proxies. The
  values `unknown`, blanks and non-IP-literals are skipped; if no usable
  entry remains, the direct remote address is used. A hostname in the
  header is never resolved (no DNS lookup from attacker-controlled
  input). Bracketed IPv6 and an IPv4 `:port` suffix are accepted.
- Trusted entries are matched as parsed IP addresses, so `::1` and
  `0:0:0:0:0:0:0:1` are the same proxy. Only exact addresses are
  supported — no CIDR ranges yet; a deployment that needs a proxy range
  should extend `IpResolver`, not bypass it.
- This fixes two opposite bugs: `AdminController.ip()` trusted the header
  from *any* client (audit IPs could be forged; per-IP caps could be
  dodged by asserting a fresh header per request), while the auth
  controllers ignored the header entirely (behind a real proxy every
  client would share the proxy's IP key).

### D-101 Multi-key rate-limit acquires are atomic (ADOPTED for v1.0)
- `LoginThrottle` mutations now run under a single lock (previously each
  `ConcurrentHashMap.compute` was atomic per key, but a pair of keys was
  not). New `tryAcquireAll(Map<String,Integer> caps, window,
  lockDuration[, cooldownKey, cooldown])`: either every key is consumed
  or none is — except that the first key whose cap is exceeded is locked
  for `lockDuration`, mirroring single-key `tryAcquire`.
- Rationale: the old resend path did two sequential `tryAcquire` calls
  (user, then IP). A resend that passed the user cap but hit the IP cap
  consumed one unit of user quota without sending anything, and two
  concurrent resends could both pass a pre-check and consume quota the
  caps should have refused. The resend path now does one atomic acquire
  of the user + phone + IP keys, with the 60 s cooldown folded into the
  same critical section. The `isLocked` / `isCoolingDown` pre-checks are
  kept only to produce the specific "wait a minute" message; the atomic
  acquire re-validates everything.
- Single-key behaviour is unchanged: 5 failures → 15-minute lock, window
  expiry resets the count, success clears the bucket. All pre-existing
  `LoginThrottleTest` cases pass unmodified.

### D-102 Password-reset OTP requests are capped, silently (ADOPTED for v1.0)
- `AuthService.requestPasswordReset` now caps at 5/hour per email and
  10/hour per IP (same `tryAcquireAll` pattern, no new table). A capped
  request sends nothing and returns normally — the controller still
  answers the generic "If the email exists, a reset code was sent", so
  the cap is not an oracle for whether the email exists or how many
  resets it has had.
- Rationale: this was the only OTP send with no rate limit at all — an
  attacker could flood any address with reset emails indefinitely, and
  use the endpoint to probe liveness. NFR-SEC-05 requires OTP endpoints
  to be rate-limited. The verify side already had a 5-attempt cap per
  reset token (`OtpService.verifyPasswordResetOtp`); this closes the
  send side.
- The signature changed to `requestPasswordReset(String email, String
  ip)`; the only caller is `AuthController.resetRequest`, which now
  passes `IpResolver`-resolved IP. No other caller exists.

### D-103 OTP wrong-guess counters must survive the verify transaction's rollback (ADOPTED for v1.0)
- Found by Worker B while proving the brute-force requirement: the
  5-attempt caps on phone-OTP and password-reset-OTP verification could
  never engage. `OtpService.verifyPhoneOtp` / `verifyPasswordResetOtp`
  did `row.setAttempts(n+1); save(row)` and then threw `OTP_INVALID` —
  but the throw rolls back the enclosing `@Transactional` method
  (`AuthService.verifyPhone` / `confirmPasswordReset`), so the increment
  was rolled back with it. Every wrong guess saw `attempts = 0`, so
  guessing was unbounded within the code's lifetime (5 min phone OTP,
  15 min reset OTP).
- Fix: the increment is now an atomic `UPDATE … SET attempts =
  COALESCE(attempts, 0) + 1` executed by a `@Modifying` repository
  method in its own `REQUIRES_NEW` transaction
  (`PhoneOtpRepository.incrementAttempts`,
  `PasswordResetTokenRepository.incrementAttempts`), called from
  `OtpService` before throwing. The counter commits even when the
  caller's transaction rolls back, and concurrent wrong guesses cannot
  lose updates (single-statement increment).
- The same bug class exists in `AdminAuthService.verifyEmailOtp` (admin
  email-OTP verify: same setAttempts+save+throw inside `@Transactional`),
  but that file is owned by Worker A and was mid-rewrite when found, so
  it was left untouched and is flagged in `07-section-b.md` instead.
  Note the existing service-level test
  `AdminAuthSecurityTest.emailOtpAttemptCounterSurvivesFreshRequest`
  passes DESPITE the bug, because the test class is `@Transactional`:
  the test's own transaction never rolls back mid-test, so the counter
  appears to persist. It proves the counter logic, not the production
  behaviour. A MockMvc-level test is the honest proof; one is added for
  the phone and reset paths in `OtpRateLimitTest`.
- The TOTP path never had this bug: it records failures in the
  in-memory `LoginThrottle`, which no transaction can roll back.
