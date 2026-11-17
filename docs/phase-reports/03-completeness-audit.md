# Phase report 03 — Implementation-completeness audit

Date: 2026-11-17 ~19:40 +06. Author: audit coordinator (main agent's subagent).
Repo: `~/workspace/unilink`, HEAD `0000224` ("Playwright smoke tests green: fix nested admin routes in dev"), working tree clean.
Status of this document: audit only. **No source code was modified.** The only file written is this report.

## 1. Method

Four parallel read-only inventories, then coordinator reconciliation (including
independent re-reads of the highest-severity claims):

1. **Backend REST API inventory** — all 103 `@*Mapping` endpoints in
   `backend/src/main/java/com/unilink/`, controller → service → repository,
   auth rule per endpoint, planned-but-absent endpoints, dead code.
2. **Database inventory** — all 7 Flyway migrations V001–V007, all 55 `@Entity`
   classes, column-by-column diff entity ↔ SQL, seeds, Flyway/JPA config.
3. **Frontend inventory** — every route in both apps, every
   `apiGet/apiPost/apiPatch/apiPut/apiDelete` call (~50 distinct paths),
   matched against the real backend paths; placeholders and stubs.
4. **Auth / security / verification inventory** — registration, OTP, email
   link, login/throttle, sessions, CSRF, RBAC, admin TOTP, OCR + verification
   flow, account deletion, rate limiting; checked against the SRS original
   (`~/workspace/user/files/UniLink_SRS__1.docx`) and the decision log.

The requirement list is the 116 IDs verified in the Phase 1 addendum
(`docs/phase-reports/01-addendum.md`): 84 FR + 32 NFR, plus the 31 TCs
(used as cross-checks, not classified separately). Each requirement is
classified exactly once, using the strict rules below. Every classification
cites evidence (file paths with line numbers). Where two inventories
disagreed, the coordinator re-read the code and the reconciliation is noted.

### What "verified" means here

- **Fully implemented and verified** — the whole requirement is implemented
  AND verified by a passing test that exercises the actual behaviour, or by a
  live run. A UI page, a placeholder, a successful build, or a passing unit
  test of a *part* of the requirement does not qualify.
- **Implemented but not integration-tested** — code exists end-to-end
  (endpoint + service + persistence + frontend call where applicable, no
  known gaps), but no integration or end-to-end test exists or has run.
- **Partially implemented** — some layers exist but a layer, field or
  endpoint is missing, or the frontend and backend disagree.
- **Not implemented** — no code for it, or only a stub/placeholder, or a
  deliberate scope decision not to build it (decision id cited).
- **Blocked by environment** — code is complete and the environment is the
  *sole* blocker. Used only where that is literally true.

### Environment state at audit time

- **No endpoint or screen has ever run against a live backend.** Java→MySQL
  TCP is blocked by the sandbox: the owner-approved probe ran 2026-11-17
  18:43 +06 and failed with `Communications link failure / EOF, read 0
  bytes`, while MySQL itself is healthy on 127.0.0.1:3306. Flyway has never
  run; `unilink_dev.flyway_schema_history` does not exist.
- **In this audit session the backend toolchain was absent.** `mvn` and
  `java` are not installed in this environment (`mvn: No such file or
  directory`, exit 127; filesystem search found no Java/Maven binary; no
  `mysql`/`mysqld` binary and no `mysql.service`). The `~/.m2/repository`
  cache and `backend/target/unilink-backend-1.0.0-SNAPSHOT.jar` from an
  earlier build are still present. Consequence: **the backend test suite
  could not be re-run for this audit.** The 13/13 green result is the parent
  session's last verified run (2026-11-17 16:39 +06), not an audit-session
  re-verification. The backend test tree contains only 4 test classes
  (`ArchitectureTest`, `AccountStateTest`, `AccessPolicyTest`,
  `TotpServiceTest`) plus 2 test helpers — all pure unit tests, no
  `@SpringBootTest`, no integration tests.
- **Frontend suites were re-verified in this session:** `npx tsc --noEmit`
  exit 0; `npm run build` exit 0; Vitest 16 passed (16), 2 test files.
  Playwright smoke tests 2/2 pass (landing + admin login, frontend only,
  2026-11-17 18:48 +06).

## 2. Top-line numbers (116 requirements)

| Classification | Count | Share |
|---|---|---|
| Fully implemented and verified | **1** | 1% |
| Implemented but not integration-tested | **29** | 25% |
| Partially implemented | **71** | 61% |
| Not implemented | **15** | 13% |
| Blocked by environment | **0** | 0% |
| **Total** | **116** | |

The single fully-verified requirement is NFR-MNT-01 (MVC layering enforced
by the passing ArchUnit test, Git version control, documentation) — and even
that rests on the parent session's test run, since this session could not
re-run Maven (see §1).

**Blocked by environment is 0, deliberately.** No requirement is code-complete
with the environment as its *sole* blocker: every feature that cannot be
verified live also lacks integration tests (none exist), and every
environment-dependent feature (production SMS/SMTP/TLS) also has a code gap
or an unwired UI. The environment block (Java→MySQL interception) is real
and is what prevents *live* verification of the 29 "implemented but not
integration-tested" items — but the missing integration tests are
implementation work in their own right, so those items are classified there
rather than as environmentally blocked.

## 3. Findings that change the picture

These are not ordinary gaps. Each one is verified from code by the
coordinator (not only by a subagent).

1. **The backend most likely cannot boot at all (M7, HIGH).** Four Spring
   Data derived-query methods reference properties that do not exist:
   `UserCredentialRepository.findByUserId`, `ProfileRepository.findByUserId`,
   `EventDetailRepository.findByPostId`, `AdminTotpSecretRepository.findByAdminId`
   (`…/identity/repo/*.java:10`). The four entities name their id field `id`
   (`UserCredential.java:6`, `Profile.java:6`, `EventDetail.java:6`,
   `AdminTotpSecret.java:6` — `@Id @Column(name="user_id"|"post_id"|"admin_id")
   private Long id`). Spring Data derives queries from Java property names,
   so these four methods have no matching property and are expected to throw
   `PropertyReferenceException` when the Spring context starts. Not executed
   in this audit (no live backend is possible), but the code facts are
   unambiguous and the 13 green unit tests cannot catch it — none of them
   boots a Spring context. **Fix before anything else: rename the id fields
   (e.g. `Profile.userId`) or change the four methods to `findById(…)`.**
   Until this is fixed, the Java→MySQL question is moot — the app would fail
   before touching the database.
2. **Two entity/SQL mismatches would break live writes/reads (M1, M2,
   HIGH).** `VerificationRequest.createdAt` (`identity/domain/
   VerificationRequest.java:21`, set by `@PrePersist`) has no `created_at`
   column in `verification_requests` (V002). `NotificationPreference.createdAt`
   (`identity/domain/NotificationPreference.java:11`) has no `created_at`
   column in `notification_preferences` (V006). With `ddl-auto: none`,
   Hibernate never validates the schema, so both surface only at runtime —
   the verification queue path and the notification-preference path would
   fail on first use. Fix with a V008 migration (add the two columns) or by
   removing the two entity fields.
3. **The login lockout can never engage (FR-AUTH-10, NFR-SEC-05, HIGH).**
   `LoginThrottle.recordFailure` (`security/LoginThrottle.java:15-19`)
   resets the failure count to 1 on every failure while unlocked (when
   `lockedUntil` is null the condition is always true), so the count never
   reaches 5 and `lockedUntil` is never set. The `isLocked` check in
   `AuthService.login` is dead code. The same broken throttle backs admin
   login. TC-05 ("five failed login attempts → locked 15 minutes") cannot
   pass as written. There is also no rate-limit filter at all: no OTP,
   password-reset, registration, resubmit or report endpoint is throttled.
4. **The admin portal cannot log in as written.** All four admin-auth
   frontend calls omit `/auth`: `/admin/login`, `/admin/totp/enrol`,
   `/admin/totp/verify`, `/admin/logout` vs the real
   `/api/admin/auth/login`, `/api/admin/auth/totp/enrol`,
   `/api/admin/auth/totp/verify`, `/api/admin/auth/logout`
   (`frontend/src/admin/pages/AdminLogin.tsx:7`, `Totp.tsx:4-5`,
   `AdminLayout.tsx:6`). The same pattern hits the OTP screen
   (`/auth/otp/send`, `/auth/otp/verify` do not exist; real endpoint is
   `POST /api/auth/verify-phone`) and all three forgot-password steps.
   Overall **~30 of ~50 distinct frontend API paths mismatch the backend**
   (~20 match, ~5 partial). The largest clusters: admin auth, OTP/password
   reset, events, teams, profile, search, moderation, appeals, audit, master
   data, moderators.
5. **Two dead features with live endpoints.** `POST /api/appeals/public`
   can never succeed: `ModerationService.createSuspensionAppealToken` has
   zero callers, so no suspension-appeal token is ever issued (D-019 flow
   is dead). `FORCE_RESET` in `AdminService.changeUserState` generates a
   temporary password, hashes it, and discards it — it is never returned,
   logged or emailed, so the reset credential is unrecoverable by anyone.
6. **Moderator isolation has known holes.** No platform-only restriction on
   SUSPEND/BAN (any admin role may do any user action); the appeals list and
   decide endpoints have no university-scope check at all; the moderation
   decide COMMENT branch does not scope-check (POST/EVENT does); a moderator
   can publish a GLOBAL announcement; three analytics aggregates and the
   dashboard `pendingAppeals` count are global and visible to any moderator.
7. **No integration tests exist.** The backend suite is 13 pure unit tests
   (architecture, state machine, access policy, TOTP). No `IT-*`, no E2E
   beyond the 2 frontend smoke tests, no security tests, no load tests.
   Every TC-01–TC-31 acceptance case is unexecuted.
8. **CSRF reconciliation (inventories disagreed; coordinator checked).**
   The exemption list is `/api/auth/**`, `/api/admin/auth/**`,
   `/api/public/**`, `/api/appeals/public`, `/actuator/**`, `/ws/**`
   (`SecurityConfig.java:55-58`). `POST /api/me/delete` is **protected**
   (it is not under `/api/auth/**`); `POST /api/auth/change-password` **is
   exempt** (it is under `/api/auth/**`). The change-password exemption is
   low-risk in practice (the old password is required) but it is a
   deliberate-looking hole worth a decision, not an accident to assume away.
   The frontend also never calls `GET /api/csrf` to prime the token cookie,
   so CSRF-protected calls depend on an earlier response having set it.

## 4. Per-area summaries

Counts below are requirement classifications, except §4.3 and §4.10 which
are endpoint/path counts (the owner asked for those areas as inventories).

### 4.1 Student features (64 FRs: AUTH, PROF, COM, MSG, EVT, CNT, SRC, SEC, TEAM, CAL/NOT/BMK, REP)

| Classification | Count |
|---|---|
| Fully implemented and verified | 0 |
| Implemented but not integration-tested | 16 |
| Partially implemented | 40 |
| Not implemented | 8 |
| Blocked by environment | 0 |

What works in code: registration (backend), email verification link,
verification state transitions, feed levels (DEPARTMENT/UNIVERSITY/HUB/
NATIONAL) with the default DEPARTMENT tab, posts/comments/reactions/
bookmarks, search (server-side), teams join/decide, rooms/messages/STOMP,
reports with auto-hide, calendar query. What does not: the sign-up form
cannot bind to the backend (free-text university/department/batch, wrong
file field name); the OTP screen and all three forgot-password steps call
non-existent endpoints; no photo/cover upload, no activity feed, no
post/message attachments, no temporary group chats, no event detail/upcoming/
edit/delete endpoints; search returns a Map the frontend treats as an array
(always empty); the feed sector filter sends a name where the backend
expects an id; read receipts do not exist (`MessageDto` has no `read` field,
so chat ticks always render ✓).

### 4.2 Admin features (20: FR-ADM-01–14, FR-MOD-01–06)

| Classification | Count |
|---|---|
| Fully implemented and verified | 0 |
| Implemented but not integration-tested | 3 |
| Partially implemented | 16 |
| Not implemented | 1 |
| Blocked by environment | 0 |

Implemented but not integration-tested: dashboard (ADM-02), verification
queue + approve/reject + ID image (ADM-03), moderator actions written to the
audit log (MOD-06). Not implemented: event moderation (ADM-07 — no endpoint
at all). Everything else is partial, dominated by frontend/backend path
mismatches (admin login/TOTP, moderation queue, appeals decide, audit log
path, master-data reads, moderators list) and the moderator-scope holes in
§3.6. The verification queue is the most complete admin workflow in the
system; the master-data and moderator-management screens are read-only
shells whose copy promises actions that have no buttons or API calls.

### 4.3 Backend REST APIs (103 mappings)

- **103 REST mappings exist** (14 auth + 6 public + 13 profile + 2 feed +
  12 post + 5 team + 11 messaging + 1 search + 3 report + 5 notification +
  7 admin-auth + 24 admin), all resolving to implemented service methods —
  no `UnsupportedOperationException`, no TODO/FIXME stub in any service.
- **~25 planned endpoints are absent** (see §6, items I-7): photo/cover
  upload, activity feed, community context, temporary rooms, attachments,
  room member management, event detail/upcoming/edit/delete, event
  team-request, post edit/delete, event moderation, master-data
  reads/updates/deletes, announcements read/update/delete, moderators
  list/edit/reset-credentials, sector unfollow, project/education/
  achievement list/edit.
- **~20 further endpoints exist under a different path/method than the
  traceability matrix assumed** (the matrix is a planning document; the code
  paths are the real contract — e.g. `/api/auth/verify-email`,
  `/api/auth/password-reset/request|confirm`,
  `/api/admin/verification-queue`, `/api/admin/moderation/decide`).
- **Auth-check gaps:** `GET /api/posts/{id}/comments` and
  `GET /api/teams/event/{eventPostId}` take no `Authentication` parameter at
  all; many secondary writes lack `requireVerified` while their sibling
  writes have it (reactions/bookmarks DELETE, project/skill delete, unfollow,
  sector follow, messaging read/block/mute/list, search, notifications);
  `POST /api/appeals` has no ownership check; `GET /api/health` requires
  authentication (almost certainly unintended — `/actuator/health` is
  `permitAll`); unauthenticated calls to admin TOTP/email-OTP endpoints
  would throw NPE (500) instead of returning 401.
- **No secrets leak** in responses post-refactor (no controller returns an
  entity; password hashes, storage keys and appeal token hashes are omitted
  from DTOs). Intentional sensitive exposures: TOTP `otpauthUrl` on first
  login, plaintext recovery codes shown once at enrolment, the raw ID-card
  JPEG stream (scope-checked, `no-store`).

### 4.4 Database entities, relationships, Flyway migrations

- **7 migrations, 57 tables** (55 entity-mapped + 2 Spring Session), **55
  `@Entity` classes, 67 FK constraints in SQL.** Conventions are consistent
  (InnoDB, utf8mb4, `DATETIME(3)`, `BIGINT UNSIGNED` ids).
- **Zero JPA relationship annotations in the whole codebase.** Every FK is a
  bare `Long`; every status is a plain `String`; the `AccountState` enum is
  converted via `.name()` in services. All 67 FKs are SQL-only, and all
  unique keys/indexes are SQL-only (`ddl-auto: none`, so Hibernate never
  validates the schema).
- **Defects:** M1 and M2 (HIGH — entity fields with no SQL column, §3.2);
  M7 (HIGH — four derived queries reference non-existent properties, §3.1);
  M6 (MEDIUM — many FK-like columns have no SQL FK; `appeals`,
  `moderation_events` and `admin_alerts` have zero FKs at all); M3–M5
  (LOW — SQL columns with no entity field, harmless); M8 (LOW — `Profile.
  links` and `AuditLog.metadata` are SQL `JSON` but entity `String @Lob`).
- **Seeds:** V001 seeds 6 sectors, 7 system-settings keys (auto-hide
  threshold 5, strike window 30d / count 3, overdue 48h, resubmit max 3,
  ID-image retention 7/30 days), 2 universities (MU, DU), 2 catalog
  departments (CSE, EEE), 4 departments, 8 batches. No skills are seeded.
  No admin is seeded by any migration; `AdminBootstrap` creates the first
  Platform Admin from env vars only when none exists (no hardcoded
  credentials). `DevSeeder` (dev profile only) creates 2 moderators and 4
  students with **hardcoded passwords** (`Moderator123!`, `Student123`) —
  dev-only by profile, but committed in source and must be rotated before
  any non-local use.
- **Flyway has never run.** The V006 audit triggers use `DELIMITER $$`
  (mysql-CLI syntax); whether Flyway's parser accepts them is unverified.

### 4.5 Authentication, authorisation, OTP, account verification (17: AUTH-01–13, SEC-05/06/07, PRV-02)

| Classification | Count |
|---|---|
| Fully implemented and verified | 0 |
| Implemented but not integration-tested | 7 |
| Partially implemented | 10 |
| Not implemented | 0 |
| Blocked by environment | 0 |

Implemented but not integration-tested: password rule + Argon2id
(AUTH-03), email verification link (AUTH-05), Pending Review transition
(AUTH-07), resubmit (AUTH-08), logout + 2h idle (AUTH-12), account
deletion (PRV-02), secure HTTP-only sessions (SEC-06). Partial: sign-up
form mismatch (AUTH-01), no phone-format validation (AUTH-02), OTP without
resend/cooldown/caps and with an unwired frontend (AUTH-04), OCR score
without university-name comparison (AUTH-06), decision email never sent
(AUTH-09), login lockout broken (AUTH-10), password-reset frontend unwired
(AUTH-11), `requireVerified` missing on several writes (AUTH-13), no
rate-limit filter and broken throttle (SEC-05), IP allow-list dead code and
no admin password-rotation endpoint (SEC-07). RBAC itself (two filter
chains, `AccessPolicy`, moderator scoping) is implemented in code with unit
tests (5/5 `AccessPolicyTest`), but the admin-auth NPE hole and the
unverified-write gaps keep the area partial overall. Providers: dev SMS and
dev email are log-only stubs by design (D-066); production SMS is a real
generic-gateway adapter and production email a real SMTP adapter — neither
has credentials in-repo and neither has been exercised live.

### 4.6 Posts, comments, follows, bookmarks, reports, moderation (13: CNT-01–03, PROF-06, BMK-01, REP-01–04, ADM-05/06/08, MOD-03)

| Classification | Count |
|---|---|
| Fully implemented and verified | 0 |
| Implemented but not integration-tested | 2 |
| Partially implemented | 10 |
| Not implemented | 1 |
| Blocked by environment | 0 |

Implemented but not integration-tested: auto-hide at 5 distinct verified
reporters (REP-02) and the 3-in-30-days strike flag (REP-04) — both exist
in `ModerationService.report`. Not implemented: post attachments (CNT-03).
Everything else is partial: post creation shape mismatch, no post
edit/delete, follow called at the wrong path, bookmark removal never called
from the UI, reports and appeals have no UI at all, moderation/appeals
admin screens call the wrong paths with the wrong action values and no
reason, and the moderation decide COMMENT branch is not scope-checked.

### 4.7 Messaging and WebSocket (9: MSG-01–08, PER-03)

| Classification | Count |
|---|---|
| Fully implemented and verified | 0 |
| Implemented but not integration-tested | 0 |
| Partially implemented | 5 |
| Not implemented | 4 |
| Blocked by environment | 0 |

The STOMP contract matches exactly on paper (endpoint `/ws`, app prefix
`/app`, send `/app/rooms.{publicId}.send`, broadcast
`/topic/room.{publicId}`, membership interception on subscribe/send) and the
frontend client has 5 unit tests — but no handshake, broadcast or echo has
ever run against a live server. Partial: department/university group rooms
(MSG-01), private chat (MSG-02), message search (MSG-03 — endpoint exists,
no UI), real-time delivery (MSG-05 — no read status), block/mute (MSG-06 —
backend only). Not implemented: temporary group chats (MSG-04), attachments
(MSG-07), member removal / closing chats (MSG-08), sub-second delivery
measurement (PER-03).

### 4.8 Events, teams, announcements (15: EVT-01–09, TEAM-01–04, ADM-09, CAL-01)

| Classification | Count |
|---|---|
| Fully implemented and verified | 0 |
| Implemented but not integration-tested | 3 |
| Partially implemented | 10 |
| Not implemented | 2 |
| Blocked by environment | 0 |

Implemented but not integration-tested: the archive job (EVT-05), past-
deadline rejection (EVT-09), auto-close of full teams (TEAM-04). Not
implemented: upcoming-events list/sidebar (EVT-07 — the page itself admits
it is missing) and event edit/delete (EVT-08). Partial: event creation shape
mismatch, no event-detail endpoint, comments with hardcoded @mention
suggestions, no event team-request endpoint, team join/decide called at the
wrong paths, announcements create-only with GLOBAL scope ungated, calendar
called with `?month=` where the backend requires `?from=&to=`.

### 4.9 File uploads and ID-card verification (5: PROF-01, AUTH-06, CNT-03, MSG-07, SEC-04)

| Classification | Count |
|---|---|
| Fully implemented and verified | 0 |
| Implemented but not integration-tested | 1 |
| Partially implemented | 1 |
| Not implemented | 3 |
| Blocked by environment | 0 |

Implemented but not integration-tested: private ID storage with authorised
streaming (SEC-04). Partial: OCR (AUTH-06 — real Tesseract adapter, score
omits the university name). Not implemented: profile photo/cover upload
(PROF-01 — no endpoint, no UI), post attachments (CNT-03), message
attachments (MSG-07). Upload validation itself exists server-side
(`UploadValidator`: magic-byte sniffing, 2 MB photo / 5 MB ID card /
10 MB post/chat attachment) and storage is a local private-files adapter
with a path-traversal guard; production storage is an unconfigured
provider interface.

### 4.10 Frontend ↔ backend integration

- **~50 distinct frontend API paths: ~20 match, ~5 partial (path/method
  right, body/params/shape wrong), ~30 mismatch (no backend route).**
  Full table in the frontend inventory; the worst clusters are §3.4.
- **Session handling is half-built.** `credentials: 'include'` sends the
  `UL_SESSION` cookie; CSRF token is sent when the cookie exists. But
  `AuthProvider` is plain in-memory state: nothing calls `GET /api/auth/me`
  on load, so a page reload drops the client-side user (nav shows Log
  in/Sign up) even though the server session survives. `RequireAuth` is
  defined and used nowhere — **no route in either app is guarded**.
- **Sign-up never validates against the catalog.** University/department/
  batch are free-text inputs; `GET /api/public/*` is never called, and the
  sector list is hardcoded in `frontend/src/api/types.ts:11-12`.
- **404s are misreported to the user.** Missing/mismatched endpoints render
  as `<ApiUnavailable endpoint=…>` ("the backend endpoint is not available
  yet"), which conflates a genuinely missing resource with a wrong path —
  relevant because many of these 404s are the mismatches above.
- **What does match cleanly:** register/login/logout/me, email verify,
  verification status/resubmit, verification queue + approve/reject + ID
  image, feed/posts/comments/bookmarks, rooms/messages/read, notifications
  list, profile PUT, account delete, admin dashboard/analytics/appeals-list/
  announcement-create, and the whole STOMP contract (statically).

## 5. Full per-requirement table

Class labels: **FULL** = fully implemented and verified; **IMPL-UNTESTED** =
implemented but not integration-tested; **PARTIAL** = partially implemented;
**NOT-IMPL** = not implemented; **BLOCKED-ENV** = blocked by environment.
Evidence is file:line in `~/workspace/unilink` unless stated otherwise.

### 5.1 Functional requirements

| ID | Requirement (short) | Class | Evidence |
|---|---|---|---|
| FR-AUTH-01 | Sign-up collects 10 fields incl. address, ID image, profile photo | PARTIAL | Backend complete: `AuthDtos.java:10-17`, `AuthService.register:43-80`. Frontend cannot bind: `SignUp.tsx:4,10` sends free-text university/department/batch and file field `idCardImage`; backend expects `universityId/departmentId/batchId` (Long) and field `idCard`. |
| FR-AUTH-02 | Input validation; unique email + student ID per university | PARTIAL | Backend: Bean Validation + `existsByEmail`/`existsByUniversityIdAndStudentId` → 409 (`AuthService.java:55-57`). No phone-format validation anywhere (backend `@NotBlank` only; frontend `min(11)`). |
| FR-AUTH-03 | Password ≥8 chars, upper + lower + digit; stored securely | IMPL-UNTESTED | Rule in `AuthService.checkPasswordRule:34-37`; Argon2id `Argon2PasswordEncoder(16,32,1,19*1024,2)` (`SecurityConfig.java:32-35`). No test exercises the rule. |
| FR-AUTH-04 | OTP to phone/email, 5 min expiry, 5 attempts | PARTIAL | OTP semantics in `OtpService.java:28-58` (6-digit, SHA-256, 5 min, 5 attempts, single-use, SMS→email fallback). No resend endpoint/cooldown/per-phone caps; frontend `OtpStep.tsx:3-4` calls non-existent `/auth/otp/send|verify`; dev SMS is log-only (`LoggingSmsProvider.java:8-10`). |
| FR-AUTH-05 | Email verification link, marks email verified | IMPL-UNTESTED | `OtpService.java:59-76` (64-hex token, SHA-256, single-use, 24 h expiry); frontend `EmailVerify.tsx:4` MATCH. Never run live. |
| FR-AUTH-06 | OCR on ID card, compare student ID + university name | PARTIAL | Real Tesseract adapter (`TesseractOcrAdapter.java`, `OcrJobService.java`, `@Retryable` ×3). Score compares name + student ID only — no university-name comparison. Failure leaves request queued (`ocrStatus=FAILED`). |
| FR-AUTH-07 | OTP+email verified → Pending Review, appears in admin queue | IMPL-UNTESTED | `AuthService.maybeMoveToPendingReview:134-143` (both verifications required); queue `AdminService.verificationQueue:54-76`; frontend `Pending.tsx:4` MATCH. Never run live. |
| FR-AUTH-08 | Rejection reason shown; resubmit ≤3× in 24 h | IMPL-UNTESTED | `AuthService.resubmit:168-196` (REJECTED-only, rolling 24 h cap 3, 429 beyond; reason via `VerificationStatusDto.rejectionReason`, `AuthDtos.java:40-41`); frontend resubmit MATCH. Note: the 24 h count includes ALL attempt rows, not only resubmissions. |
| FR-AUTH-09 | Notify student by email + in-app of approval/rejection | PARTIAL | In-app only: `NotificationEventListener.java:10-17` creates `VERIFICATION_DECISION` notifications. `NotificationService.notify:12-22` writes only a DB row and never calls `EmailProvider`; no decision email exists anywhere. |
| FR-AUTH-10 | Login with email/password; lock 15 min after 5 failures | PARTIAL | Login works (`AuthService.login:114-128`, generic error, no enumeration). **Lock never engages**: `LoginThrottle.recordFailure` (`security/LoginThrottle.java:15-19`) resets the count to 1 on every failure. TC-05 cannot pass. |
| FR-AUTH-11 | Forgot password: OTP request, verify, reset | PARTIAL | Backend `password-reset/request` + `/confirm` exist (`AuthController.java:45,48`). Frontend calls three non-existent paths (`ForgotPassword.tsx:4-6`: `/password/forgot`, `/password/verify-otp`, `/password/reset`). |
| FR-AUTH-12 | Logout; sessions end after 2 h inactivity | IMPL-UNTESTED | Logout invalidates session (`AuthController.java:43-47`); `spring.session.timeout: 2h` (`application.yml:28`). No idle-timeout dialog in the UI. Never run live. |
| FR-AUTH-13 | Unverified accounts restricted to limited access | PARTIAL | `requireVerified` on core writes (`PostService`, `TeamService`, `MessagingService`, `ModerationService`) and National-only feed (`FeedService.java:18-27`). Missing on several writes: reactions/bookmarks DELETE, project/skill delete, unfollow, sector follow, messaging read/block/mute/list, search, notifications. |
| FR-PROF-01 | Upload/change profile picture and cover photo | NOT-IMPL | No `PUT /api/me/profile/photo|cover` endpoint; no upload UI in `ProfilePage.tsx`. |
| FR-PROF-02 | Show university/department/batch; edit profile | PARTIAL | `PUT /api/profiles/me` supports bio/headline/links/phoneVisible (`ProfileController.java:15`); identity fields read-only (correct). Frontend edits bio only — no headline/links inputs. |
| FR-PROF-03 | Add/edit/delete projects | PARTIAL | POST + DELETE exist (`ProfileController.java:23,27`); no edit, no list endpoint; frontend never calls either. |
| FR-PROF-04 | Add education entries and achievements | PARTIAL | POST only (`ProfileController.java:30,34`); no edit/delete/list; frontend never calls either. |
| FR-PROF-05 | External links (GitHub, LinkedIn, …) | PARTIAL | Links field on `PUT /api/profiles/me` with `javascript:` rejection (`ProfileService.java:44`); no links editor in the UI. |
| FR-PROF-06 | Follow/Connect on profiles, follower counts | PARTIAL | `POST/DELETE /api/profiles/{id}/follow` exist (`ProfileController.java:48,52`). Frontend calls `POST /users/{id}/follow`, which does not exist. |
| FR-PROF-07 | Activity feed on profile | NOT-IMPL | No `GET /api/users/{id}/activity` endpoint; frontend shows "Activity is not available yet" (`ProfilePage.tsx:5`). |
| FR-PROF-08 | Phone/address hidden by default; owner may show phone | IMPL-UNTESTED | Phone hidden unless owner or `phoneVisible` (`ProfileService.java:29-32`); address never in another user's DTO; toggle via `PUT /api/profiles/me`; frontend `SettingsPage.tsx:4-5` MATCH. Never run live. |
| FR-PROF-09 | Identity change requires re-verification | PARTIAL | `POST /api/profiles/me/identity-change` creates a PENDING request (`ProfileService.java:82`); frontend never calls it; no admin approve path for identity changes exists. |
| FR-COM-01 | Verified student auto-placed in profile-derived spaces | PARTIAL | No `GET /api/community/context`; no auto-placement on verification; rooms are created on demand (`MessagingService.getOrCreateDepartmentRoom/UniversityRoom`). |
| FR-COM-02 | Department section: same university + department only | IMPL-UNTESTED | `GET /api/feed?level=DEPARTMENT` + `FeedSpecifications`; frontend `Feed.tsx:7` MATCH on level. Never run live. |
| FR-COM-03 | University section: same university, all departments | IMPL-UNTESTED | `GET /api/feed?level=UNIVERSITY` + specifications; frontend MATCH on level. Never run live. |
| FR-COM-04 | National Department Hub: same department, all universities | IMPL-UNTESTED | `GET /api/feed?level=HUB` on `scope_catalog_id`; frontend MATCH on level. Never run live. |
| FR-COM-05 | National feed: all visibility events/posts/discussions | IMPL-UNTESTED | `GET /api/feed?level=NATIONAL`; unverified read-only (`FeedService.java:18-27`, D-016). Never run live. |
| FR-COM-06 | Messaging/collaboration at every community level | PARTIAL | Room endpoints exist (`MessagingRestController.java:18-24`); frontend never calls any room-creation endpoint; room list works (`GET /api/rooms` MATCH). |
| FR-COM-07 | Department feed by default after login; one-click level switch | IMPL-UNTESTED | Frontend defaults to DEPARTMENT with tab switcher (`Feed.tsx:5,12`); login navigates to `/feed`. No backend context call is needed for this behaviour. Never run live. |
| FR-MSG-01 | Group chat per Department and University | PARTIAL | `POST /api/rooms/department|university` exist (verified, `MessagingRestController.java:18,21`); rooms are created on demand, not automatically; frontend never calls them. |
| FR-MSG-02 | Private one-to-one chat between verified students | PARTIAL | `POST /api/rooms/private/{userId}` + STOMP send exist; frontend never calls the create endpoint (existing rooms work via `GET /api/rooms`). |
| FR-MSG-03 | Search inside messages by conversation/person name | PARTIAL | `GET /api/rooms/search?q=` exists (`MessagingRestController.java:15`, names only per D-029); no search UI in `MessagesPage.tsx`. |
| FR-MSG-04 | Temporary group chat for team formation + invites | NOT-IMPL | No `POST /api/rooms/temp`, no invite endpoint; no UI. |
| FR-MSG-05 | Real-time delivery, permanent storage, time + read status | PARTIAL | STOMP send/subscribe + persistent storage + timestamps exist; **no read status**: `MessageDto` has no `read` field (`MessagingService.java:15`), so chat ticks always render ✓ despite `markRead` existing server-side. Never live-tested. |
| FR-MSG-06 | Block a user, mute a conversation | PARTIAL | `POST/DELETE /api/blocks/{userId}`, `POST /api/rooms/{publicId}/mute` exist (`MessagingRestController.java:36-42`); frontend never calls them. |
| FR-MSG-07 | Send images/files (limit) in chat | NOT-IMPL | No `POST /api/rooms/{id}/attachments`; no attach UI in `MessagesPage.tsx`. |
| FR-MSG-08 | Group admin removes members, closes temporary chats | NOT-IMPL | No `DELETE /api/rooms/{id}/members/{uid}`, no close endpoint; no UI. |
| FR-EVT-01 | Verified student creates event post (Seminar/Workshop/…) | PARTIAL | `POST /api/events` exists (`PostController.java:43`, type enum, past-deadline rejection). Frontend sends `type` and omits `body`; backend `CreateEvent` is `{visibility, title, body, deadline, regLink, location, sectorIds}`. |
| FR-EVT-02 | Event visibility options | PARTIAL | Visibility supported server-side in `CreateEvent`; reachable only through the same broken create call as EVT-01. |
| FR-EVT-03 | Event detail page (description, poster, link, deadline, …) | PARTIAL | No `GET /api/events/{id}`; `GET /api/posts/{id}` returns the event fields. Frontend `EventDetail.tsx:4` calls the missing path. |
| FR-EVT-04 | Comments on every event; @mention other students | PARTIAL | Comments exist (`PostController.java:24,27`); mentions stored if the user exists. Frontend @mention suggestions are hardcoded `['@teammate','@moderator']` (`PostDetail.tsx:11`) — no user lookup. |
| FR-EVT-05 | Job moves past-deadline events to Past Events | IMPL-UNTESTED | `ScheduledJobs.archivePastEvents` (every 60 s, archives + closes teams). Never run live; the Past Events list itself cannot load (no `GET /api/events`). |
| FR-EVT-06 | Competition/hackathon "Looking for Teammates" | PARTIAL | No `POST /api/events/{id}/team-request`; teams are created directly via `POST /api/teams` with `eventPostId`. Frontend `EventDetail.tsx:6` calls the missing endpoint. |
| FR-EVT-07 | Sidebar lists Upcoming Events by nearest deadline | NOT-IMPL | No `GET /api/events/upcoming`; no sidebar — `EventsPage.tsx:7` lead text admits it is missing. |
| FR-EVT-08 | Event owner edits/deletes own event | NOT-IMPL | No `PATCH/DELETE /api/events/{id}`; no post edit/delete endpoint at all. |
| FR-EVT-09 | Reject event with past deadline at creation | IMPL-UNTESTED | Rejected in `PostService.createEvent` (`PostService.java:87`). No test exercises it. |
| FR-CNT-01 | Study / project-idea / discussion posts | PARTIAL | `POST /api/posts` exists; frontend sends `level` (not `visibility`) and `sector` string (not `sectorIds`) (`Feed.tsx:9`). |
| FR-CNT-02 | Comments, reactions, edit/delete own posts | PARTIAL | Comments + reactions exist; **post edit/delete do not exist**. |
| FR-CNT-03 | Attach images/PDFs to a post | NOT-IMPL | No `POST /api/posts/{id}/attachments`; no attachment picker in the composer. |
| FR-SRC-01 | Search bar to find things | PARTIAL | `GET /api/search` exists (scope-filtered, `SearchController.java:9`) but returns `Map{posts, users}`; frontend treats it as an array, so the list is always empty (`SearchPage.tsx:4`). |
| FR-SRC-02 | Search/filter by topic/sector | PARTIAL | `sector` param is ignored server-side; frontend sends a sector name. |
| FR-SEC-01 | Sectors incl. the six named ones | PARTIAL | Six sectors seeded (V001) and `GET /api/public/sectors` exists; frontend never calls it and hardcodes `SECTORS` (`api/types.ts:11-12`). |
| FR-SEC-02 | Follow sectors; filter every feed by sector | PARTIAL | Follow endpoint exists (`POST /api/profiles/me/sectors/{sectorId}`) with no unfollow; frontend never calls it; feed sector filter sends a name where the backend expects a Long id. |
| FR-TEAM-01 | Looking for Teammates creates team request for event | PARTIAL | Team creation via `POST /api/teams` (`TeamController.java:12`); no event team-request endpoint; frontend calls the missing path. |
| FR-TEAM-02 | Join request; creator accepts/declines | PARTIAL | `POST /api/teams/{teamId}/join` + `POST /api/teams/requests/{requestId}/decide` exist. Frontend accept calls `POST /api/teams/{id}/requests/{rid}/accept`, which does not exist. |
| FR-TEAM-03 | On acceptance, member added to team + team chat | PARTIAL | Server-side accept adds `TeamMember` + team-chat `RoomMember` in one transaction and closes at `maxSize` (`TeamService.java:60-66`); frontend accept path is wrong (see TEAM-02). |
| FR-TEAM-04 | Team closes when full or event becomes Past Event | IMPL-UNTESTED | Close-on-full in `decideRequest`; `archivePastEvents` closes teams via `TeamService.closeTeamsForEvent`. Never run live. |
| FR-CAL-01 | Calendar view of events by deadline | PARTIAL | `GET /api/calendar?from=&to=` exists (`PostController.java:51`); frontend sends `?month=` (`CalendarPage.tsx:4`). |
| FR-NOT-01 | Choose which notification types also arrive by email | PARTIAL | `POST /api/notifications/preferences` exists; frontend calls `PATCH /me/notification-preferences` (wrong method + path); **no guard that security emails cannot be disabled**; UI offers only two fixed presets, no per-type toggles. |
| FR-BMK-01 | Save/remove bookmarks; Saved page | PARTIAL | `POST/DELETE/GET /api/bookmarks` exist; frontend save MATCH (`PostDetail.tsx:9`) and Saved list MATCH, but `DELETE /api/bookmarks` is never called from the UI. |
| FR-REP-01 | Report option with reason (spam/fake/abuse/other) | PARTIAL | `POST /api/reports` exists (`ReportController.java:12`); frontend never calls it — no Report dialog in the UI. |
| FR-REP-02 | 5 distinct verified reporters → auto-hide | IMPL-UNTESTED | Implemented in `ModerationService.report` (threshold from `report.auto_hide_threshold`, default 5, `ModerationService.java:24,60-77`). Never run live. |
| FR-REP-03 | Owner appeals hidden item; admin decides | PARTIAL | `POST /api/appeals` + admin `POST /api/admin/appeals/{id}/decide` exist; no appeal UI in the student app; admin frontend calls the wrong path/action values. |
| FR-REP-04 | 3 auto-hides in 30 days → flagged for admin review | IMPL-UNTESTED | Strike check in `ModerationService.report:89-97` (`STRIKE_FLAG` admin alert). Never run live. |
| FR-ADM-01 | Admin Portal login: email + password + mandatory 2FA (OTP) | PARTIAL | Backend login + TOTP enrol/verify + recovery codes + email-OTP fallback exist (`AdminAuthService.java`). Frontend calls `/admin/login`, `/admin/totp/enrol|verify` — all missing `/auth`. Throttle broken (see AUTH-10); unauthenticated TOTP calls would NPE, not 401. |
| FR-ADM-02 | Dashboard with real counts | IMPL-UNTESTED | `GET /api/admin/dashboard` + UI MATCH (`Dashboard.tsx:3`). Note: `pendingAppeals` count is global/unscoped (`AdminService.java:47`). Never run live. |
| FR-ADM-03 | Verification queue with details, ID image, OCR score, decisions | IMPL-UNTESTED | Queue + approve/reject + ID-image stream all MATCH the frontend (`AdminController.java:32,33,36,40`); reason mandatory on reject (`VerificationFacade.java:41-42`); moderator-scoped. Never run live. |
| FR-ADM-04 | Search/view users; suspend, ban, reactivate, revoke, force-reset | PARTIAL | Endpoints exist (`AdminController.java:48-50`); frontend sends lowercase actions (`suspend`, `ban`, …) but the backend expects UPPERCASE → `INVALID_ACTION`; **no platform-only restriction on SUSPEND/BAN** (moderators may suspend/ban directly, contra D-036). |
| FR-ADM-05 | Moderation queue of reported/auto-hidden content | PARTIAL | `GET /api/admin/moderation-queue` exists; frontend calls `GET /admin/moderation`, which does not exist. |
| FR-ADM-06 | Hide/remove/restore/request-edit/warn; send warning | PARTIAL | `POST /api/admin/moderation/decide` exists (HIDE/REMOVE/RESTORE/EDIT_REQUEST — **no WARN action**); frontend calls a non-existent path with the wrong body and no reason; COMMENT branch is not scope-checked. |
| FR-ADM-07 | Moderate events (remove fake, close registration, fix deadline/visibility) | NOT-IMPL | No `PATCH /api/admin/events/{id}`; `AdminEvents.tsx:2` is a read-only table calling a non-existent GET. |
| FR-ADM-08 | Appeals queue; accept/reject each appeal with reply | PARTIAL | List + decide exist (`AdminController.java:67,70`); frontend calls the wrong path with `uphold/overturn` (backend expects ACCEPT/REJECT) and no reason; **no university-scope check** on list or decide. |
| FR-ADM-09 | Publish announcements (all universities or one) | PARTIAL | Create exists and frontend create MATCH (`AdminController.java:73`); no GET list; **GLOBAL scope is not platform-gated** — a moderator can publish a GLOBAL announcement. |
| FR-ADM-10 | Manage master data (universities, departments, batches, sectors) | PARTIAL | Only 3 POST creates exist (`AdminController.java:76-82`); no GET/PUT/DELETE, no batches endpoint; frontend reads via non-existent `GET /admin/{tab}` and never calls the creates. |
| FR-ADM-11 | Audit log (who/what/target/time/IP), append-only | PARTIAL | `GET /api/admin/audit-logs` exists; frontend calls `GET /api/admin/audit` (missing) and links a non-existent `/audit/export`; audit triggers use `DELIMITER $$` (Flyway acceptance unverified); hash chain + `verifyAuditChain` job exist. |
| FR-ADM-12 | Create/edit/deactivate/assign moderators; reset admin credentials | PARTIAL | Create + deactivate exist (`AdminController.java:85,88`); no GET, no edit, no reset-credentials; frontend never calls either and shows a read-only table. |
| FR-ADM-13 | Analytics (growth, turnaround, events/sector, active universities) | PARTIAL | `GET /api/admin/analytics` + UI MATCH; three aggregates (`eventsPerSector`, `verificationTurnaroundHours`, `activeUniversities`) are global and visible to any moderator (`AdminService.java:281-284`). |
| FR-ADM-14 | Alert admins when a verification waits >48 h | PARTIAL | `overdueVerificationAlerts` job exists (every 15 min, idempotent 48 h alerts, `ScheduledJobs.java:31`); no UI surfacing — frontend never calls `GET /api/admin/alerts`. |
| FR-MOD-01 | Moderator logs in with 2FA | PARTIAL | Same flow as ADM-01 (same defects and frontend mismatch). |
| FR-MOD-02 | Moderator sees only own university's queue/users/content | PARTIAL | Scoping implemented in most queries (`AdminService.verificationQueue`, `loadUser`, `verificationIdCardKey`, `VerificationFacade`); gaps: appeals unscoped, analytics globals, dashboard `pendingAppeals` global, moderation COMMENT branch, GLOBAL announcements. |
| FR-MOD-03 | Moderator approves/rejects and moderates own university | PARTIAL | Approve/reject + moderation exist with scope checks, subject to the MOD-02 gaps. |
| FR-MOD-04 | Moderator publishes announcements for own university only | PARTIAL | UNIVERSITY scope is checked for non-platform admins; GLOBAL scope is not gated (see ADM-09). |
| FR-MOD-05 | Moderator recommends suspension; Platform Admin confirms | PARTIAL | `POST /api/admin/users/{id}/recommend-suspension` + `POST /api/admin/suspension-recommendations/{id}/decide` exist; no UI calls either. |
| FR-MOD-06 | All moderator actions written to audit log | IMPL-UNTESTED | Audit writes exist in the action paths (`AuditService` calls in `VerificationFacade`, `AdminService`); same-transaction claim not verified live. |

### 5.2 Non-functional requirements

| ID | Requirement (short) | Class | Evidence |
|---|---|---|---|
| NFR-AVL-01 | 99% monthly uptime, 24/7 | PARTIAL | `/actuator/health` is `permitAll` (`SecurityConfig.java:100`); no monitoring, no uptime reporting, no restart policy evidenced. |
| NFR-CMP-01 | Latest two versions of Chrome, Firefox, Edge, Safari | PARTIAL | Playwright smoke tests pass on Chromium only (2/2, 2026-11-17). Firefox, Edge, Safari and mobile untested. |
| NFR-FLT-01 | Validate all input; clear message; invalid never saved | IMPL-UNTESTED | Bean Validation on DTOs + `GlobalExceptionHandler` (`common/GlobalExceptionHandler.java:7`). No integration test proves invalid data is never persisted. |
| NFR-FLT-02 | Email gateway failure → alternative channel (SMS) | PARTIAL | OTP send falls back SMS→email on `ProviderException` (`OtpService.java:28-44`). The reverse path (email verification link failing → SMS alternative) does not exist. |
| NFR-FLT-03 | OCR failure/inconclusive → still queued with a note | IMPL-UNTESTED | OCR failure returns `Result("", 0.0, "FAILED")` and never throws; request stays queued with `ocrStatus=FAILED` (`TesseractOcrAdapter.java`, `OcrJobService.java`). Never run live. |
| NFR-LOC-01 | English UI; Bangla text input (UTF-8) | IMPL-UNTESTED | utf8mb4 everywhere in migrations; UI is English. No test with Bangla input exists. |
| NFR-MNT-01 | MVC, version-controlled in Git, documented | FULL | Layering enforced by `ArchitectureTest` 3/3 (last verified run 2026-11-17 16:39 +06, 13/13 green); Git history exists (HEAD `0000224`); docs tree (`docs/`, decision log, phase reports). Caveat: Maven could not be re-run in this audit session (toolchain absent) — see §1. |
| NFR-PER-01 | Pages load <3 s for 95% of requests | NOT-IMPL | No measurement, no load test. |
| NFR-PER-02 | Search <2 s up to 100,000 records | NOT-IMPL | FULLTEXT indexes exist (`ft_posts`, `ft_users_name`); no seeded 100k-row test, no measurement. |
| NFR-PER-03 | Chat delivered <1 s under normal load | NOT-IMPL | No measurement; no live STOMP run at all. |
| NFR-PER-04 | ≥1,000 concurrent users | NOT-IMPL | No load test (D-050 plan only). |
| NFR-PRV-01 | Personal data only for stated purposes, per applicable law | NOT-IMPL | No privacy policy page found in the frontend or backend; no data-minimisation review evidenced. |
| NFR-PRV-02 | Students can permanently delete account data | IMPL-UNTESTED | `POST /api/me/delete` + `AccountDeletionService.deleteOwnAccount` (password re-auth, CASCADEs, storage objects deleted, sessions invalidated, audit keeps numeric id only). Minor caveat: only the latest verification request's storage keys are collected. Never run live. |
| NFR-PRV-03 | Contact details hidden by default | IMPL-UNTESTED | Same evidence as FR-PROF-08. Never run live. |
| NFR-REL-01 | Data integrity: transactions + FKs; no loss on crash | PARTIAL | `@Transactional` boundaries exist; 67 FK constraints in SQL. Undermined by M1/M2/M7 schema-mapping defects (§3.1–3.2) and no integrity/crash test. |
| NFR-REL-02 | Daily DB backups kept ≥14 days | NOT-IMPL | No backup script or cron found anywhere in the repo. (Deployment work, §7.) |
| NFR-REL-03 | Scheduled jobs retry on failure | PARTIAL | `@Retryable` (×3) exists only on the OCR job (`OcrJobService.java:14`). The five scheduled jobs have no retry. |
| NFR-REL-04 | Audit log retained ≥1 year, backed up with DB | PARTIAL | No code purges audit logs; `verifyAuditChain` job exists (`ScheduledJobs.java:60`). Backup itself is NFR-REL-02 (not implemented). |
| NFR-SCL-01 | Horizontal scaling of web/WebSocket/storage layers | PARTIAL | Stateless-ish app tier + JDBC sessions; but the STOMP broker is in-memory (D-003) and D-051 is a plan, not a deployment. |
| NFR-SEC-01 | HTTPS everywhere; passwords hashed | PARTIAL | Argon2id hashing done. HTTPS is not configured: no Nginx config exists in the repo, no TLS certificate, no HSTS. |
| NFR-SEC-02 | Protect against SQL injection, XSS, CSRF | IMPL-UNTESTED | Parameterised JPA; CSRF cookie repository + frontend token header; React auto-escapes output. No security test has run (TC-20 unexecuted). |
| NFR-SEC-03 | Server-side role + verification checks on every page/API | IMPL-UNTESTED | Two filter chains + `AccessPolicy` + `AccessPolicyTest` 5/5 (unit). Gaps noted in §4.3 (unverified writes, admin-auth NPE hole) keep this from FULL. No live/IT proof. |
| NFR-SEC-04 | ID images in private storage; owner + authorised admins only | IMPL-UNTESTED | Local private-files adapter with path-traversal guard; authorised stream `GET /api/admin/verification/{id}/id-image` (scope-checked, `no-store`). Never run live. |
| NFR-SEC-05 | OTP + login endpoints rate-limited | PARTIAL | A throttle class exists but is broken (count resets — see AUTH-10) and covers login only; OTP endpoints have no rate limiting at all. |
| NFR-SEC-06 | Secure HTTP-only cookies; invalidated on logout/password change | IMPL-UNTESTED | `UL_SESSION`, HttpOnly, SameSite=Lax, Secure in prod (`application-prod.yml`); invalidation on logout and password change (`SessionService.java:13-18`). Never run live. |
| NFR-SEC-07 | Admin 2FA, strong passwords, least privilege, optional IP allow-list | PARTIAL | TOTP + recovery codes + ≥12-char admin passwords + 30-min idle + audit logging implemented. **IP allow-list is dead code** (`AdminIpAllowlist` + repository exist, never consulted); no admin password-change/reset endpoint. |
| NFR-USA-01 | New student can sign up + view feed without instructions | PARTIAL | The flows exist in the UI; no usability test with new students has been held (UAT-USA-01 unexecuted). |
| NFR-USA-02 | Clear nav labels and icons | PARTIAL | Labels exist; no design review against DESIGN.md has been recorded. |
| NFR-USA-03 | Responsive 360–1920 px | PARTIAL | Responsive tokens/layouts exist; no responsive test run. |
| NFR-USA-04 | Contrast + keyboard navigation (WCAG 2.1 AA target) | PARTIAL | Design tokens exist; **known failure**: `text-muted` contrast is 3.35:1 (light) / 3.85:1 (dark) vs the 4.5:1 AA bar; no axe/CI check exists. |
| NFR-USA-05 | Understandable error messages explaining the fix | IMPL-UNTESTED | Error-mapping layer exists on both sides (`ApiError` codes + frontend `errorMessage()`); no test exercises it. |
| NFR-USA-06 | Admin processes a verification in <1 minute | PARTIAL | The review pane exists (queue + ID image + OCR score + approve/reject); no timed usability session has been held. |

## 6. Remaining IMPLEMENTATION work (code still to write or fix)

Ordered by dependency — items 1–3 are boot-blockers and come first. Each item
names the requirement(s) it unblocks.

### Boot-blockers (the app cannot start or run correctly without these)

- **I-1. Fix M7 derived queries (FR-AUTH-01/07, FR-ADM-03, FR-PROF-02/08,
  FR-ADM-01, and everything using `Profile`/`UserCredential`/`EventDetail`/
  `AdminTotpSecret`).** Rename the shared-PK id fields (e.g.
  `Profile.userId`, `EventDetail.postId`) or change the four repository
  methods to `findById(…)`. Expected to fail Spring context startup as
  written.
- **I-2. Fix M1/M2 (FR-AUTH-07, FR-ADM-03, FR-NOT-01).** Add a V008
  migration with `created_at DATETIME(3)` on `verification_requests` and
  `notification_preferences`, or remove the two `createdAt` entity fields.
  The two sides must agree; `submitted_at` already covers the verification
  timestamp.
- **I-3. Fix the login throttle (FR-AUTH-10, NFR-SEC-05, FR-ADM-01).**
  Rewrite `LoginThrottle.recordFailure` so the failure count accumulates
  within the window and locks at 5 for 15 minutes; add a unit test that
  reproduces the current bug (5 consecutive failures → locked). Then add
  real rate limiting to OTP send/verify, password-reset and resubmit
  endpoints (a filter or per-endpoint throttle, not just login).

### Frontend ↔ backend contract (the ~30 mismatches)

- **I-4. Admin auth paths (FR-ADM-01, FR-MOD-01).** Prefix all four calls
  with `/auth` (`/admin/auth/login`, `/admin/auth/totp/enrol|verify`,
  `/admin/auth/logout`).
- **I-5. OTP + password-reset flows (FR-AUTH-04, FR-AUTH-11).** Point the
  OTP screen at `POST /api/auth/verify-phone {userId, otp}`; add a resend
  endpoint with cooldown and per-phone/per-IP caps (or remove the resend
  button); point forgot-password at
  `/api/auth/password-reset/request|confirm`.
- **I-6. Sign-up form (FR-AUTH-01, FR-AUTH-02, FR-SEC-01).** Replace
  free-text university/department/batch with catalog dropdowns driven by
  `GET /api/public/universities|departments|batches`; send `universityId`/
  `departmentId`/`batchId` (Long) and the file field `idCard`; add
  client-side phone-format validation (backend must also validate the
  format — currently `@NotBlank` only).
- **I-7. Missing backend endpoints (one build task, grouped by area).**
  Photo/cover upload (PROF-01); activity feed (PROF-07); community context
  (COM-01); event detail + upcoming + edit/delete (EVT-03/07/08);
  event team-request (EVT-06/TEAM-01 — or formally adopt `POST /api/teams`
  as the path and fix the frontend); post edit/delete (CNT-02); post and
  message attachments (CNT-03, MSG-07); temporary rooms + invite +
  member removal + close (MSG-04/08); sector unfollow (SEC-02);
  project/education/achievement list + edit + delete (PROF-03/04);
  event moderation (ADM-07); announcements read/update/delete (ADM-09);
  master-data reads/updates/deletes + batches (ADM-10); moderators
  list/edit/reset-credentials (ADM-12); audit-log alias/export if wanted
  (ADM-11). Each needs the matching frontend call.
- **I-8. Frontend shape mismatches (no backend change needed).** Feed
  sector must send an id, not a name (SEC-02); post create must send
  `visibility` + `sectorIds`, not `level` + `sector` (CNT-01); event create
  must send `body` and drop `type` (EVT-01); search must handle the
  `Map{posts, users}` response (SRC-01); calendar must send `from`/`to`,
  not `month` (CAL-01); team accept must call
  `POST /api/teams/requests/{id}/decide {action, reason}` (TEAM-02);
  profile/follow must use `/api/profiles/…` (PROF-06); notification
  preferences must call `POST /api/notifications/preferences` (NOT-01);
  moderation must call `POST /api/admin/moderation/decide` with
  `{targetType, targetId, action, reason}` (ADM-06); appeals must call
  `POST /api/admin/appeals/{id}/decide {action: ACCEPT/REJECT, reason}`
  (ADM-08); user actions must send UPPERCASE values (ADM-04); audit must
  call `GET /api/admin/audit-logs` (ADM-11); master-data and moderators
  reads must use `GET /api/public/*` (ADM-10/12); moderation queue must
  call `GET /api/admin/moderation-queue` (ADM-05).
- **I-9. Read receipts (FR-MSG-05).** Add a `read` field to `MessageDto`
  (from `room_members.last_read_message_id`) so chat ticks can render ✓✓;
  the server-side `markRead` already exists.
- **I-10. Session restore + route guards (NFR-SEC-03, FR-AUTH-12).** Call
  `GET /api/auth/me` on app load to restore the session; actually use
  `RequireAuth` (currently defined and unused) on protected routes; add an
  idle-timeout notice. Also call `GET /api/csrf` on load to prime the CSRF
  cookie instead of relying on an earlier response.

### Behavioural gaps (backend logic)

- **I-11. Verification-decision email (FR-AUTH-09).** Make
  `NotificationService` (or the decision listener) send the email via
  `EmailProvider` on approve/reject; in-app only exists today.
- **I-12. OCR university-name comparison (FR-AUTH-06).** Extend the
  scorer to compare the university name, as the SRS requires.
- **I-13. Dead features (FR-ADM-04, FR-REP-03, D-019).** Either wire
  `createSuspensionAppealToken` into suspend/ban (and email the token) or
  delete the dead `POST /api/appeals/public` path; fix `FORCE_RESET` to
  return the temporary password securely to the Platform Admin (or remove
  the action).
- **I-14. Moderator-scope holes (FR-MOD-02, FR-ADM-04/06/08/09/13).**
  Scope the appeals list/decide to the moderator's university; scope-check
  the moderation COMMENT branch; gate GLOBAL announcements to Platform
  Admin; restrict SUSPEND/BAN to Platform Admin (moderators recommend);
  scope the three global analytics aggregates and the dashboard
  `pendingAppeals` count.
- **I-15. Missing guards (NFR-SEC-03, FR-NOT-01, FR-REP-03).** Add
  `requireVerified` to the secondary writes that lack it (see §4.3);
  require `Authentication` on `GET /api/posts/{id}/comments` and
  `GET /api/teams/event/{eventPostId}`; add the ownership check to
  `POST /api/appeals`; enforce "security emails cannot be disabled" in
  notification preferences; return 401 (not NPE/500) from unauthenticated
  admin-auth endpoints; decide whether `GET /api/health` should be public.
- **I-16. Admin IP allow-list (NFR-SEC-07).** Either enforce
  `AdminIpAllowlist` at admin login or delete the entity/repository and
  record the decision. Also add an admin password-change/reset endpoint
  (none exists).
- **I-17. D-077 operative parts (FR-AUTH-02).** Flag duplicate-phone
  accounts to reviewers in the verification queue; block a phone attached
  to a suspended/banned account; rate-limit OTP sends per phone/IP.
- **I-18. Smaller correctness fixes.** Resubmit counting should count
  resubmissions, not all attempts (FR-AUTH-08); `previousAttempts` should
  count per-request attempts, not all attempts for the user (FR-ADM-03);
  add an admin decide path for identity-change requests (FR-PROF-09);
  collect storage keys from *all* of a user's verification requests on
  account deletion, not only the latest (NFR-PRV-02); null the stale
  `User.idCardKey` in the purge job (D-053); decide M6 (add the missing
  FKs or record a deliberate decision not to, especially `appeals`,
  `moderation_events`, `team_members.event_post_id`); decide M8 (JSON vs
  `String @Lob` for `Profile.links` / `AuditLog.metadata`); verify the
  V006 `DELIMITER $$` trigger block runs under Flyway (it can only be
  proven by running Flyway); make `LoginAttempt` written or remove the
  dead table.

### Tests (none exist beyond unit tests)

- **I-19. Integration tests.** Write and run `IT-*` tests (Spring Boot
  Test against real MySQL `unilink_test`): registration → OTP → email →
  Pending Review → approve → Verified; login lockout (TC-05); resubmit
  cap; feed scoping per level; report → auto-hide at 5; appeal → restore;
  moderator scope 404s (TC-26); student forbidden from `/admin` (TC-27).
  These need a working Java→MySQL environment first.
- **I-20. E2E tests.** Automate TC-01–TC-31 in Playwright (only the 2
  smoke tests exist today). Include the security cases (TC-20 injection/
  XSS, TC-23 wrong admin OTP) and the cross-browser matrix (TC-22).
- **I-21. Performance + accessibility tests.** k6 load tests for
  NFR-PER-01–04 (3 s pages, 2 s search at 100k rows, 1 s chat, 1,000
  concurrent users); axe checks for NFR-USA-04; a timed usability session
  for NFR-USA-06; a usability session with 5+ new students for NFR-USA-01.
- **I-22. Fix the known contrast failure (NFR-USA-04).** `text-muted`
  (3.35:1 light / 3.85:1 dark) must not be used for essential information.

## 7. DEPLOYMENT work (separate from implementation)

None of this is code in the repo today; none of it unblocks the
implementation items above, and none of it should be confused with them.

- **D-1. Nginx.** Add `ops/nginx/unilink.conf` (draft prepared; owner
  decision pending on compose vs systemd). Must include the `/admin/*` →
  `admin.html` rewrite, `/api/` + `/ws/` proxying to 127.0.0.1:8080, and
  immutable `/assets/` caching. `vite preview` has the same gap as dev
  had; the built output (`dist/index.html` + `dist/admin.html`) needs the
  rewrite at whatever serves it.
- **D-2. TLS + domain.** Certificate, HTTPS listener, HSTS, real
  `server_name` (NFR-SEC-01). No domain or certificate exists yet.
- **D-3. Production providers.** SMS gateway credentials (FR-AUTH-04 prod
  path); production SMTP (FR-AUTH-05/09); production storage (the local
  private-files adapter is dev-only); hosting + deployment shape decision.
- **D-4. Database operations.** Daily backups kept ≥14 days + restore
  drill (NFR-REL-02, NFR-REL-04); production DB users/passwords and
  `ENCRYPTION_KEY` supplied via environment (prod yml already fails fast
  without them); rotate/remove the hardcoded `DevSeeder` passwords before
  any non-local use.
- **D-5. Observability.** Monitoring + uptime reporting (NFR-AVL-01);
  log aggregation; audit-log retention enforcement (≥1 year).
- **D-6. Environment block (owner action).** Resolve the Java→MySQL
  sandbox interception (Muse settings → Permissions). Do not retry the
  probe without a new diagnostic reason (owner instruction 2026-11-17).
  Until this clears, no live verification of anything in §5 is possible —
  but items I-1–I-18 do not need it (they are code fixes verifiable by
  compilation, unit tests and static checks), and I-19–I-21 do.
- **D-7. Pre-launch reviews.** Bangladeshi data-protection legal review
  and a privacy policy page (NFR-PRV-01); production SMS/email provider
  contracts; load-test hardware (NFR-PER-04).

## 8. Bottom line

- **1 of 116 requirements is fully implemented and verified.** 29 are
  implemented in code but have never been integration-tested; 71 are
  partial; 15 are not implemented at all.
- **The backend is the most complete layer and the least verified one:**
  103 endpoints exist with real service logic, but three defects
  (M7 boot failure, M1/M2 schema mismatches, broken login throttle) mean
  it has most likely never successfully started, and no integration test
  exists.
- **The frontend is the most visibly complete layer and the most
  disconnected one:** every page exists and renders, the smoke tests pass,
  but ~30 of ~50 API paths do not match the backend — including admin
  login, OTP and password reset, i.e. the flows a user hits first.
- **The honest next step is code, not deployment:** fix I-1–I-3, then the
  contract mismatches (I-4–I-10), then write the integration tests (I-19).
  Deployment work (§7) is real but separate, and none of it makes an
  unverified feature verified.
