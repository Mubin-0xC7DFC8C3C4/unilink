# UniLink release gate

Owner's rule, set 2026-11-24 21:37 +06:

> From now on, do not consider a deployment complete unless these critical
> user journeys pass: signup reference data loading, registration, email OTP
> delivery and verification, phone OTP verification, login and session
> persistence, pending review, admin verification workflow. If any critical
> workflow fails, investigate and fix it before declaring the release
> complete.

A deployment is not complete when the build goes live. It is complete when
every journey below passes against the live deployment. A journey that has
never been exercised in production is NOT VERIFIED, not a pass.

## Status after the 2026-11-25 16:21 +06 deploy (`98d1de6`)

Overall: **NOT GREEN — 4 of 7 pass, 3 outstanding.**

| # | Journey | Status | Evidence |
|---|---------|--------|----------|
| 1 | Signup reference data loading | **PASS** | Catalogue endpoints 200 with real TiDB data (universities, departments, batches), live smoke test 2026-11-25 |
| 2 | Registration | **PASS** | Registration without a profile photo returns 201 (×3 probe accounts, all deleted again), live smoke test 2026-11-25 |
| 3 | Email OTP delivery and verification | **NOT VERIFIED** | SMTP from Render times out (`MailConnectException`, smtp.gmail.com:587). No OTP was attempted in the 2026-11-25 smoke tests by design — the code is only sent after AUTO_VERIFIED, which is itself unproven (see below). Must not be claimed working until a real email reaches an inbox. |
| 4 | Phone OTP verification | **NOT VERIFIED** | The verify-phone session defect was fixed in code (`5e77b96`, backend tests pass), but live end-to-end proof still needs a fresh code read from the Render logs by the owner (`~/workspace/audit-2026-11-23/verify_phone_session_prod.sh`). SMS is log-only in production. |
| 5 | Login and session persistence | **PASS** | Login 200 + `/auth/me` 200 across sessions, live smoke test 2026-11-25; logout invalidates the session (Phase A, 2026-11-23). |
| 6 | Pending review | **PASS** | Registrations reach PENDING_REVIEW and the token-based status endpoint returns the live OCR outcome (MANUAL_REVIEW observed 2026-11-25). |
| 7 | Admin verification workflow | **NOT TESTED** | Requires the owner's bootstrap admin sign-in; never verified by Muse. Admin approval is the only path that moves a MANUAL_REVIEW account to VERIFIED today. |

## Related open items

- A genuine AUTO_VERIFIED run is still unproven in production: it needs a
  real ID card whose text matches the registration. The only live OCR run
  so far (2026-11-25) correctly returned MANUAL_REVIEW for an unreadable
  generated test card.
- Email OTP delivery depends on the AUTO_VERIFIED path above, and on
  resolving the Render → smtp.gmail.com:587 timeout (network-level; cause
  not proven). Options not yet decided: confirm whether Render's free tier
  restricts outbound SMTP, or move delivery to an HTTPS-based transactional
  email provider.
- Auto-deploy itself is proven end to end (push → auto-trigger → build →
  live → SHA match, `98d1de6`, 2026-11-25) and is watched by the
  `unilink-auto-deploy-monitor` cron (every 15 min, silent when healthy).
