# Backend audit report — comprehensive consistency audit, bug fixing & verification (2026-11-18)

## Verdict

**36 confirmed issues fixed, all with regression tests. Backend 287/287 green,
`clean package` equivalent BUILD SUCCESS, frontend checks all green.
Committed as `e06c63d` (entities + timestamps), `558c4b4` (repository/service),
`cc8622a` (bootstrap, auth, API errors) and this report.**
Phase 1–3 commits are preserved as ancestors; HEAD at start was `c0d6ab7`,
working tree clean. No migration rewritten, no new dependency, no frontend
code changed, no database touched.

**Not runtime-verified, stated plainly:** no fix in this audit has run
against a live MySQL backend. The sandbox still blocks Java→MySQL
(interception unchanged since 2026-11-17), so verification is H2 unit
tests + static migration/entity comparison + the offline Maven build.
The Windows runtime check (boot + `http://localhost:8080/api/health`)
must be performed by the owner — exact commands are in §10. Nothing
below claims a live boot.

## 1. Scope — files inspected

- **189 backend main Java files** (all of `backend/src/main/java`):
  56 JPA entities, all repositories, all services, all controllers,
  `SecurityConfig`, filters, jobs, OCR/email/SMS adapters, storage.
- **40 backend test files** (all of `backend/src/test/java`).
- **11 Flyway migrations** `V001`–`V011` (compared field-by-field
  against the 56 entities).
- Configuration: `application.yml`, `application-dev.yml`,
  `application-prod.yml`, `pom.xml`, `.env.example`, `ops/` scripts.
- **Not re-litigated:** Phase 1–3 decisions and fixes were taken as
  given and re-verified only where the audit touched them (OTP
  `REQUIRES_NEW` counting, moderator scoping, DTO-only reads).

Four workers audited in parallel (§4A/§4B entities+timestamps, §4C
repository/service, §4D/§4E auth+bootstrap, §4F API/error handling);
the coordinator integrated, resolved collisions, and re-ran every suite
on the settled tree.

## 2. Root cause behind the Critical issues (one mechanism, 11 failures)

Every `NOT NULL ... DEFAULT ...` column in V001–V011 is safe only if the
column is omitted from the INSERT — but Hibernate includes every mapped
column and sends an explicit NULL for any field left null in Java (no
`@DynamicInsert` / `@ColumnDefault` anywhere). So on the owner's
Windows MySQL each of these inserts fails with
`Column '<col>' cannot be null`, while H2 (schema generated from the
entities, columns nullable) accepts the NULL — which is why the suite
was green while production inserts were broken. No entity had any
`@PreUpdate` at all. Fix (D-157): entity-level defaults in the existing
`@PrePersist` pattern (`updatedAt = createdAt` on insert, booleans
`false`, `active = true`, `submittedAt = createdAt`) plus `@PreUpdate
updatedAt = now` on the five `updated_at` entities. Caller-supplied
values are never overwritten (`if (x == null)` guards; `createdAt`
never touched by `@PreUpdate`). **No V012 was needed:** Java now sends
non-null values, the schema is unchanged, and no backfill is required
— these inserts could never have succeeded, so no bad rows exist.

## 3. Confirmed issues and fixes

Severity: Critical = prevents startup / data integrity; High = breaks
an important workflow; Medium = incorrect behaviour; Low = minor.
Status: **FIXED AND VERIFIED** = fixed + regression test green on H2
and full suite green; **FIXED BUT NOT RUNTIME VERIFIED** = the same
fixes against live MySQL (all of them — see the verdict box). Nothing
in this audit is merely "fixed" without a test.

### §4A/§4B — Entity–database + timestamps (Worker 1, D-157)

| # | Sev | Issue (evidence) | Fix (file:line) |
|---|---|---|---|
| A | Critical | `AdminAccount.updatedAt` null on insert — `identity/domain/AdminAccount.java:20`, left null by `AdminBootstrap`, `AdminService.createModerator`, `DevSeeder.moderator` (V003 `updated_at NOT NULL`). Bootstrap failed on Windows MySQL with `Column 'updated_at' cannot be null`. | `AdminAccount.java:21-27` — `onCreate` sets `updatedAt = createdAt` when null; `@PreUpdate` refreshes it |
| 1 | Critical | `AdminAccount.mustChangePassword` null on insert (V010 `NOT NULL DEFAULT 0`), same sites | Same `onCreate`: defaults to `false` when null; explicit service sets (D-119/D-120 `true`) win |
| 2 | Critical | `User.updatedAt` null — `identity/domain/User.java:23`, `AuthService.register:78-83` (V002) | `User.java` `onCreate`/`@PreUpdate` |
| 3 | Critical | `UserCredential.mustChangePassword` null — `identity/domain/UserCredential.java:10`, `AuthService.register:84` sets id + hash only (V010) | `UserCredential.java` `onCreate` defaults `false` |
| 4 | Critical | `Profile.updatedAt` null — `identity/domain/Profile.java:14`, `AuthService.register:85` (V002) | `Profile.java` `onCreate`/`@PreUpdate` |
| 5 | Critical | `Post.updatedAt` null — `identity/domain/Post.java:20`, `PostService.createPost:39-43` (V004) | `Post.java` `onCreate`/`@PreUpdate` |
| 6 | Critical | `University.updatedAt` null — `identity/domain/University.java:12`, `AdminService.createUniversity:365-366` (V001) | `University.java` `onCreate`/`@PreUpdate` |
| 7 | Critical | `VerificationRequest.submittedAt` null — `identity/domain/VerificationRequest.java:18`; neither register, resubmit nor `DevSeeder` sets it (V002 `NOT NULL DEFAULT CURRENT_TIMESTAMP(3)`) | `VerificationRequest.java` `onCreate`: `submittedAt = createdAt` |
| 8 | Critical | `Skill.active` null — `identity/domain/Skill.java:8`, `ProfileService.addSkill:82` builds a Skill with name only (V001 `NOT NULL DEFAULT 1`) | `Skill.java` `onCreate` defaults `true` |
| 9 | Critical | `AdminAlert.readFlag` null — `identity/domain/AdminAlert.java:13`; both writers omit it (`AdminService` overdue alerts, `ModerationService.checkStrikes`); the strike path additionally swallowed the failure in `catch (Exception ignored)`, so the alert was silently lost (V003 `NOT NULL DEFAULT 0`) | `AdminAlert.java` `onCreate` defaults `false` |
| 10 | High | `Profile.phoneVisible` null — `identity/domain/Profile.java:12`; `ProfileService.updateProfile:61` and `uploadCover:201` create a Profile with id only when no row exists (V002) | `Profile.java` `onCreate` defaults `false` |
| 11 | Medium | No `@PreUpdate` anywhere — `updatedAt` never refreshed from Java on update (Hibernate writes the loaded stale value back, which also suppresses MySQL's `ON UPDATE CURRENT_TIMESTAMP`) | `@PreUpdate updatedAt = now` on the five `updated_at` entities |
| — | — | Latent/defensive, same-pattern guard only (no writer leaves them null today): `DepartmentCatalog.active`, `Department.active`, `Batch.active`, `SectorTag.active`, `AdminIpAllowlist.active` | Same `onCreate` default, no behaviour change |

Checked and NOT issues (all 56 entities vs V001–V011): every other
mapped field is set by every creation site; unmapped timestamp columns
(`user_credentials.updated_at`, `system_settings.updated_at`,
`team_members.joined_at`) are safe because Hibernate omits them and the
DB default applies; PKs/IDs, FKs (raw Longs), uniques, String enums,
`TINYINT(1)`↔Boolean, numerics, V010 `code_hash VARCHAR(255)`, V011
`profiles.links TEXT` and `message_attachments.created_at` all match.

### §4C — Repository / service layer (Worker 2, D-142–D-156)

| # | Sev | Issue (evidence) | Fix (file:line) |
|---|---|---|---|
| D-142 | High | `ModerationService.checkStrikes`: STRIKE_FLAG `AdminAlert` saved with `adminId` **null** — `AdminService.adminAlerts` reads by `adminId`, so the alert reached nobody; `uk_alert_once` (NULL `admin_id`) never deduped it in MySQL. Second defect: the count filtered on `Post.updatedAt`, which MySQL maintains via `ON UPDATE` only — the persistence context still held null for the post just hidden, so the alert fired one hide late (4th, not 3rd). | Alerts addressed to the author's university moderators, fallback Platform Admins, with an exists-guard; `maybeAutoHide` now stamps `post.setUpdatedAt(now)` on the entity. `ModerationService` gains `AdminAccountRepository` |
| D-143 | High | `ModerationService.appealByToken`: no status check — a submitted (`PENDING`) appeal could be re-submitted with the same token until 7-day expiry, overwriting the reason. Single-use link violated. | Require status `AWAITING_SUBMISSION`, else `BadRequest TOKEN_USED` |
| D-144 | Medium | `ModerationService.report` (MESSAGE): D-033 comment promises "message + 10 preceding" snapshots; only the reported message was snapshotted | Snapshot target + 10 newest preceding in the same room (new `MessageRepository.findByRoomIdAndIdLessThanOrderByIdDesc`) |
| D-145 | High | `TeamService.decideRequest`: request saved `ACCEPTED` **before** the `ALREADY_IN_TEAM` check / room lookup threw, consuming the request without the user joining | All failing checks run before the request is marked `ACCEPTED` |
| D-146 | Medium | `MessagingService.createPrivateRoom`: no duplicate check — every call created a fresh PRIVATE room (no unique key on the pair in V005). `markRead` accepted any `Long`, corrupting read receipts with nonexistent / foreign-room ids | Return the existing 1:1 room; `markRead` loads the message and requires it to belong to the room |
| D-147 | Medium | `NotificationService.markAllRead`: paged only the first 50 unread rows — "mark all read" left the rest unread | Bulk `@Modifying` update of all unread rows for the recipient |
| D-148 | Medium | `AuthService.verifyEmailByToken`: `emailTokenRepo.findAll()` scanned the whole token table per link click | New `findTopByTokenHashAndUsedFalseOrderByCreatedAtDesc`; behaviour unchanged (older superseded link still fails) |
| D-149 | Medium | `AdminService.createUniversity/createSector/createDepartment`: no duplicate/existence checks, while V001 has `uk_universities_name`, `uk_sector_name`, `uk_dept_uni_catalog` + FKs, and there is no `DataIntegrityViolationException` handler — duplicates/bogus ids surfaced as 500 | Pre-checks → `Conflict` (`UNIVERSITY_EXISTS`/`SECTOR_EXISTS`/`DEPARTMENT_EXISTS`) or `NotFound` |
| D-150 | Medium | `PostService.createPost/updatePost`: unvalidated sector ids → FK (`RESTRICT`) / unique-key violations → 500. Same class in `ProfileService.followSector` | Validate each distinct sector id exists (`NotFound`), dedupe; `followSector` requires the sector to exist |
| D-151 | Medium | `ProfileService.requestIdentityChange`: duplicate `PENDING` requests allowed per user | `existsByUserIdAndStatus` guard → `Conflict REQUEST_PENDING` |
| D-152 | Medium | `FeedService.toDto`: `commentCount` loaded **all** comments per post (up to 50 full loads per feed page) | New `CommentRepository.countByPostId` |
| D-153 | Medium | `PublicCatalogService.batches(uni, null)`: `batches.findAll()` + in-memory filter | New `findByUniversityIdAndActiveTrue` |
| D-154 | Medium | `PostService.listEvents`: unbounded `findAll(spec, Sort)` — every matching event | Bounded to 50 newest (`PageRequest.of(0, 50, …)`); API shape unchanged |
| D-155 | Medium | `AccountDeletionService`: profile cover key never collected (orphaned file, NFR-PRV-02); post-attachment rows/files orphaned because `attachments` has no FK to posts (polymorphic owner) while posts cascade-delete | Sweep cover key + user's `POST` attachments (rows + files). Message attachments deliberately **not** swept — sent messages stay (sender `SET NULL`) as conversation history |
| D-156 | Low/Med | `ScheduledJobs.archivePastEvents`: every past event's post → `ARCHIVED`, overwriting `REMOVED`/`HIDDEN_ADMIN` moderation state | Only `ACTIVE` → `ARCHIVED`; event detail still archived, teams still close |

Verified clean in §4C (no fix needed): no unguarded `Optional.get()`
in the service layer; `Post`/`Team`/`User`/`VerificationRequest`
carry `@Version`; OTP `REQUIRES_NEW` attempt counting (D-103/D-117)
holds in all three repositories; M7-class derived-query bugs are gone.

### §4D/§4E — Authentication / authorization + bootstrap (Worker 3, D-119–D-123)

| # | Sev | Issue (evidence) | Fix (file:line) |
|---|---|---|---|
| B | High | **Issue B — fix was missing.** `AdminBootstrap` did NOT call `a.setMustChangePassword(true)` — verified by reading the file at audit start. The bootstrapped Platform Admin's env-issued password was a persistent credential with no forced rotation, despite the class Javadoc saying "Rotate the password … at first login". | `admin/AdminBootstrap.java:36` — `a.setMustChangePassword(true);`. The must-change filter already allowlists `POST /api/admin/auth/change-password`, so the first-login flow works. To make the flag provable by a test, credentials moved from `System.getenv` to `@Value("${INITIAL_ADMIN_EMAIL:}")` / `@Value("${INITIAL_ADMIN_PASSWORD:}")` constructor params (`:18-21`); Spring resolves `${...}` from the environment, so production behaviour is unchanged. D-119 |
| D-120 | Medium | `createModerator` set a Platform-Admin-issued initial password with no forced rotation — `AdminService.java:384-386` (pre-fix) had no `setMustChangePassword`, unlike bootstrap (D-119) and `resetModeratorCredentials` | `AdminService.java:412` — `a.setMustChangePassword(true);`. Deliberately no 24 h temp expiry here (a new moderator may first log in days later; owner to decide) |
| D-121 | Low | `SUSPENDED`/`BANNED` and `TEMP_PASSWORD_EXPIRED` login rejections left no `login_attempts` row: `AuthService.login`'s `catch (Exceptions.AppException e){ throw e; }` rethrew without recording, contradicting I-18 ("one row per login attempt") | `AuthService.java:213` — the `AppException` catch now calls `recordLoginAttempt(email, ip, false)` before rethrowing (best-effort, cannot break login) |
| D-123 | Medium | Suspended/banned students never got the designed Forbidden + appeal guidance. `Principals.java:22` (pre-fix) `isAccountNonLocked()` returned false for `SUSPENDED`/`BANNED`, so `DaoAuthenticationProvider` threw `LockedException` before `AuthService.login` ran — its `SUSPENDED`/`BANNED` branch was **unreachable**. A suspended user with the correct password got "Email or password is incorrect". Proven by the initially failing `SecurityChainsTest.suspendedLoginAttemptIsRecorded` (expected `Forbidden`, got `BadRequest/INVALID_CREDENTIALS`) | `security/Principals.java:31` — `isAccountNonLocked()` now returns only `active` (matching `AdminPrincipal`); the state check stays in `AuthService.login`. Wrong password + suspended still yields `INVALID_CREDENTIALS` and consumes throttle quota; no session is created |

Verified clean in §4D/§4E (no fix needed): the two
`UserDetailsService` beans are unambiguous — named beans, each
`DaoAuthenticationProvider` takes its own via `@Qualifier`, each chain
sets its own provider explicitly, `studentAuthManager` is `@Primary`,
no other file autowires a `UserDetailsService` by type (proven by
`SecurityChainsTest`: both logins work, cross-domain logins rejected
401); Spring Session JDBC; admin 30-min idle filter; session fixation
`migrateSession`; password changes/suspensions invalidate all sessions;
Argon2id for passwords and recovery codes; OTP workflows, TOTP, RBAC
(`requirePlatformAdmin`, `requireModeratorScopeOr404`) all as designed;
bootstrap guards (duplicate prevention via `count() > 0`, ≥12-char
password, missing-env skip with warning); **no credential logging** —
grep over `admin/`, `identity/`, `security/` for password/token/OTP/
secret/hash/credential is clean (bootstrap logs the email only).

### §4F — API / error handling (Worker 4, D-137–D-141)

| # | Sev | Issue (evidence) | Fix (file:line) |
|---|---|---|---|
| D-137 | High | Catch-all handler turned Spring MVC client errors into 500s — `common/GlobalExceptionHandler.java:40` (`@ExceptionHandler(Exception.class)`) swallowed Spring's own signals. Reproduced on HEAD: malformed JSON body → 500; missing body → 500; `GET /api/public/departments` (missing required `universityId`) → 500; `GET /api/profiles/abc` (type mismatch) → 500; unknown path → 500; wrong method → 500; `POST /api/teams {}` (null `eventPostId` → `findById(null)`) → 500; `GET /api/notifications?limit=-1` (negative `PageRequest`) → 500 | Dedicated handlers in `GlobalExceptionHandler.java`: `HttpMessageNotReadableException` → 400 `INVALID_REQUEST` (Jackson detail never echoed); missing parameter/part → 400 with field error; `MethodArgumentTypeMismatchException` → 400; `NoResourceFoundException` → 404; `HttpRequestMethodNotSupportedException` → 405; `HttpMediaTypeNotSupportedException` → 415; `IllegalArgumentException` → 400; `InvalidDataAccessApiUsageException` → 400; `DataIntegrityViolationException` → 409 `CONFLICT` (detail never echoed); `ConstraintViolationException` now lists field errors. Generic handler stays last-resort only |
| D-138 | Medium | Security-filter 401/403 were container HTML, not ApiError JSON — `config/SecurityConfig.java:94` (admin chain) and `:113` (student chain) used `res.sendError(401/403)`; `AdminIdleFilter` answered a bare 401; `MustChangePasswordFilter` hand-built JSON missing `timestamp`/`traceId` | New `common/ApiErrorWriter.java` + entry-point/access-denied handlers in both chains, `AdminIdleFilter`, `MustChangePasswordFilter` — security 401/403 are now ApiError JSON (timestamp/status/code/message/path/traceId) |
| D-139 | Medium | Failed sign-in returned 400, not 401 — `AuthService.login` and `AdminAuthService.login` threw `BadRequest INVALID_CREDENTIALS`. Both login pages already branch on 401 (`Login.tsx:17`, `AdminLogin.tsx`) — that branch was dead code | Both logins now throw 401 with unchanged code `INVALID_CREDENTIALS` and message (enumeration behaviour unchanged). Wrong-current-password in `changePassword` and wrong-code in password-reset confirm deliberately stay 400 |
| D-140 | High | **Junk rows persisted with 200** (silent data corruption): `POST /api/reactions {}` → 200 with a `Reaction` row with null target/type; `POST /api/reactions?reaction=BOGUS` → 200 with an arbitrary string persisted (reaction value entirely unvalidated; only `LIKE` is used anywhere); `POST /api/profiles/me/skills {}` → 200 with a `Skill` row with null name; same pattern in `ProfileService.addProject/addEducation/addAchievement` (null/blank title/institution → 200). **Null targets NPE'd to 500:** `ModerationService.resolveOwner` switched on a possibly-null `targetType` (`POST /api/reports` with no target → NPE 500); `AdminService.decideContent` switched on null `targetType`/`action` | `ModerationService.resolveOwner` (null type/id → 400 `INVALID_TARGET`); `AdminService.decideContent` (null type/id/action → 400); `PostService.react/unreact/bookmark/unbookmark` (null target → 400 `INVALID_TARGET`; reaction restricted to `LIKE`); `ProfileService.addSkill/addProject/addEducation/addAchievement` (blank → 400 `SKILL_NAME_REQUIRED`/`TITLE_REQUIRED`/`INSTITUTION_REQUIRED`) |
| D-141 | Medium | Calendar accepted garbage dates with 200 and wrong results, plus a snake_case key — `content/api/PostController.java:125` passed `from`/`to` strings straight to SQL (H2/MySQL coerce them) and returned a raw `queryForList` map with `reg_link`, the only snake_case key in the student API | `PostController.calendar`: `from`/`to` parsed (date `YYYY-MM-DD` as the frontend sends, or ISO date-time; else 400 `INVALID_REQUEST`); returns new `CalendarEventDto(id, title, deadline, location, regLink)`; query semantics (`(from, to]`, in-scope only) unchanged; frontend `CalendarPage` reads only id/title/deadline/location, so the rename is safe |

Checked and NOT issues in §4F: no controller returns an entity;
`UserActionResult`/`ResetCredentialsResult` return a temporary password
once by design (D-094/D-095); the `other` handler never leaks
messages/stack traces; missing resources are 404 throughout;
`AccessPolicy` 403/404 distinctions are correct;
`MethodArgumentNotValidException` → 422 is the established project
convention (frontend `client.ts` handles it) — left as is.

## 4. `uniconnect` database — re-confirmed

Zero references to a database named `uniconnect` in code, resources,
`ops/`, `.env.example`, or configuration. Database URLs are
`unilink_dev` (`application.yml:5,10`, `ops/docker-compose.yml:21`)
and `unilink_test` (test profile). The only "UniConnect" hits in the
repo are historical doc notes (`docs/phase-reports/01-addendum.md`,
`docs/phase-reports/frontend.md`, decision log D-073) stating the old
branding from the `pic.docx` screenshots must NOT be copied — not a
database reference. Nothing in this audit touches any database other
than the code paths that use `unilink_dev`/`unilink_test`.

## 5. Files modified (52 + this report)

Backend main (42 files): `admin/AdminAuthService`,
`admin/AdminBootstrap`, `admin/AdminService`,
`common/ApiErrorWriter` (new), `common/GlobalExceptionHandler`,
`community/FeedService`, `config/SecurityConfig`,
`content/PostService`, `content/api/PostController`,
`identity/domain/` (14 entities: `AdminAccount`, `AdminAlert`,
`AdminIpAllowlist`, `Batch`, `Department`, `DepartmentCatalog`,
`Post`, `Profile`, `SectorTag`, `Skill`, `University`, `User`,
`UserCredential`, `VerificationRequest`),
`identity/repo/` (8 repos: `AttachmentRepository`,
`BatchRepository`, `CommentRepository`,
`EmailVerificationTokenRepository`,
`IdentityChangeRequestRepository`, `MessageRepository`,
`NotificationRepository`, `PostRepository`),
`identity/service/AccountDeletionService`, `identity/service/AuthService`,
`identity/service/PublicCatalogService`, `jobs/ScheduledJobs`,
`messaging/MessagingService`, `moderation/ModerationService`,
`notification/NotificationService`, `profile/ProfileService`,
`security/MustChangePasswordFilter`, `security/Principals`,
`team/TeamService`.

Backend tests (9 files): new `common/ApiErrorHandlingTest` (25
tests), `integration/AdminBootstrapTest` (5), `integration/EntityTimestampDefaultsTest` (14),
`integration/RepositoryServiceAuditTest` (17),
`integration/SecurityChainsTest` (11); modified
`integration/MustChangePasswordTest` (+1 test),
`integration/Phase3WorkerDTest` (1 test repaired for the submittedAt
fix — see §8), `identity/AdminAuth401Test`, `identity/OtpRateLimitTest`
(D-139 assertion updates only).

Docs: `docs/requirements/REQUIREMENTS_DECISION_LOG.md`
(D-119–D-123, D-137–D-141, D-142–D-156, D-157), this report
(`docs/phase-reports/08-backend-audit.md`).

**Not changed:** any Flyway migration (V001–V011 byte-identical),
`pom.xml` (no new dependency), any frontend file, any ops script.

## 6. Database changes / new migrations

**None.** No V012 was needed: every schema-level mismatch was fixed
on the Java side (entity defaults), and the schema itself was already
correct. No table dropped, no record deleted, no constraint weakened.
Flyway history on the owner's Windows machine (V001–V011 successful)
is unaffected — the next boot validates against the same checksums.

## 7. Tests executed and results (coordinator's own runs, settled tree)

Environment: this sandbox (Java 21.0.13, Maven 3.8.7 on PATH — the
workers restored the toolchain after VM wipes; the repo's `./mvnw`
cannot download Maven here because Java ignores the egress proxy).
Offline Maven from `backend/`:
`env -u https_proxy -u HTTPS_PROXY -u http_proxy -u HTTP_PROXY
MAVEN_OPTS="-Xmx512m" mvn -o -s /home/hatch/.m2/settings.xml …`

| Run | Result |
|---|---|
| Flagged test `Phase3WorkerDTest#accountDeletionDeletesKeysFromAllVerificationRequests` (fresh `target/`) | **1/1 green** |
| Full backend suite `mvn -o test` | **287/287 green, BUILD SUCCESS** (214 at Phase 3 close + 73 new/changed: 72 new tests in 5 new files + 1 added to `MustChangePasswordTest`) |
| Full build `mvn -o package` (after `rm -rf target`; see §8) | **BUILD SUCCESS, 287/287 tests green**, jar `target/unilink-backend-1.0.0-SNAPSHOT.jar` (72,790,819 bytes) |
| Frontend typecheck (`npm run typecheck`) | Clean |
| Frontend production build (`npm run build`) | Succeeds (2.50 s) |
| Frontend Vitest (`npx vitest run`) | **108/108 green** |
| Playwright smoke (`npx playwright test e2e/smoke.spec.ts`) | **2/2 green** |
| Vite dev server | Landing and `/admin/login` both **200** (server had been killed by a runtime restart; restarted with `nohup npm run dev`, log `/tmp/vite-dev.log`) |

All previously passing tests stayed green. Every new test was written
failing-first against HEAD `c0d6ab7` where the bug was behavioural
(Worker 1: 13 failures + 1 error pre-fix → 14/14; Worker 4: 20/20
failed pre-fix → 25/25; Worker 3: D-123 test failed as designed
pre-fix).

## 8. Integration notes (coordinator)

- **D-142 collision resolved.** The decision number was claimed twice
  (Worker 1 entity-defaults entry vs Worker 2 strike-alerts entry).
  Worker 1's entry was renumbered to **D-157** (log heading + all 14
  entity comments + both test comments); Worker 2 keeps D-142–D-156.
  Verified: each of D-119–D-123, D-137–D-141, D-142–D-157 now appears
  exactly once as a log heading. **Numbering gap D-124–D-136:** no log
  entries and no code references use these numbers (workers skipped
  ahead independently); the gap is cosmetic and was left as-is rather
  than churning 20 entries.
- **Phase3WorkerDTest incident resolved.** Worker 4 reported
  `accountDeletionDeletesKeysFromAllVerificationRequests` failing in
  the working tree (passing on pristine HEAD) and suspected Worker 2's
  D-155 deletion sweep. Root cause was Worker 1's submittedAt fix, not
  D-155: the test built its "old" request without `submittedAt` and
  relied on `findTopBy…SubmittedAtDesc` returning the factory request,
  which only worked while `submittedAt` stayed NULL (the bug). Worker 1
  repaired the test (`old.setSubmittedAt(now minus 1 day)`; test-only
  change, no behaviour change). The coordinator re-ran the test on the
  settled tree: **1/1 green**, and the full suite is 287/287.
- **Concurrent-build races (not code defects).** The four workers
  shared one checkout and one `target/`; several runs failed with
  `NoClassDefFoundError` / missing `.class` files mid-run. Every such
  failure disappeared on a clean re-run with a fresh `target/`. The
  coordinator's full-suite and package runs used a fresh `target/`.
- **`mvn clean` unavailable offline.** `maven-clean-plugin:3.3.2` is
  not in the local Maven repo, so `mvn -o clean package` fails in this
  sandbox with a plugin-resolution error (not a code failure). The
  coordinator ran `rm -rf target` + `mvn -o package` instead — the
  same effect. On the owner's Windows PC (online Maven) `.\mvnw.cmd
  clean package` works as specified.
- **No secrets in the diff** (scanned added lines for credential
  assignments — clean). No credentials are logged anywhere in
  bootstrap or auth flows (re-verified by grep).

## 9. Runtime startup result — BLOCKED BY ENVIRONMENT (not attempted here)

No boot was attempted in this sandbox: there is no new diagnostic
reason to re-probe the Java→MySQL path, and the standing instruction
is not to retry it without one. Established evidence (unchanged):
the sandbox intercepts Java processes' TCP to MySQL (approved probe
2026-11-17 18:43 +06 and genuine startup attempt 20:11 +06 both failed
with `Communications link failure` / `EOFException`, while MySQL is
healthy over TCP from non-Java processes). Flyway has never run in
this sandbox. **Runtime verification must be performed by the owner
on Windows — exact commands in §10.**

## 10. Manual actions required from the project owner (Windows)

No database action is needed: there is no new migration, and V001–V011
are already applied on the owner's machine. The work is code-only —
pull/re-extract the new commits, rebuild, restart, and verify:

1. Get the new code (after the parent commits it) and rebuild:
   ```powershell
   cd C:\unilink\backend
   .\mvnw.cmd clean package
   ```
   Expect `BUILD SUCCESS` and `Tests run: 287, Failures: 0, Errors: 0`.
   (`clean` works on Windows because Maven is online there; the
   sandbox failure in §8 does not apply.)
2. Restart the backend with the same PowerShell env vars as before
   (`SPRING_PROFILES_ACTIVE=dev`, `DB_PASSWORD`,
   `DB_MIGRATION_PASSWORD`, `ENCRYPTION_KEY`, `INITIAL_ADMIN_EMAIL`,
   `INITIAL_ADMIN_PASSWORD`):
   ```powershell
   cd C:\unilink\backend
   .\mvnw.cmd spring-boot:run
   ```
   Watch for `Started UnilinkBackendApplication` and no Flyway
   validation error. Flyway must report "already applied" for
   V001–V011 (no new migrations). If it reports a checksum mismatch,
   a migration file was modified — restore it from the package and do
   not edit applied migrations.
3. Verify health:
   ```powershell
   curl.exe http://localhost:8080/api/health
   ```
   Expected: JSON with `"status": "UP"`.
4. Verify Issue A + Issue B are fixed — first admin login:
   sign in at `http://localhost:5173/admin/login` with
   `INITIAL_ADMIN_EMAIL` / `INITIAL_ADMIN_PASSWORD`. Expect the
   authenticator enrolment flow, then a **mandatory password-change
   prompt** (the must-change flag is now set at bootstrap). Change
   the password; the new password must work for the next login.
   If the bootstrap had already failed before this fix (no admin row
   exists), it runs on this boot and creates the admin. If an admin
   row already exists from an earlier partial run, the bootstrap
   skips it (`count() > 0`) and the flag is NOT retroactively set —
   in that case change the admin password once via the admin portal
   (`/admin/change-password`) as a manual rotation.
5. Verify the D-123 fix: create a test student, suspend it from the
   admin portal, then try to sign in as that student — expect a
   clear "account suspended" message with appeal guidance, not
   "Email or password is incorrect".
6. Verify the D-137/D-139 fixes: a wrong-password sign-in now
   returns **401** (not 400); a malformed request body returns
   **400** (not 500). The login pages already handle 401.
7. Spot-check registration end-to-end (the D-157 entity fixes):
   register a student, verify the phone OTP from the backend console
   log, and confirm the account reaches the pending screen with no
   `Column '...' cannot be null` error in the backend log.

## 11. Remaining known issues

### NOT FIXED — proposals (no code changed; need an owner decision)

- **D-122 (PROPOSED):** `SecurityConfig.csrf` exempts all of
  `/api/auth/**`, so the authenticated `POST /api/auth/change-password`
  skips CSRF validation. Mitigated by `SameSite=Lax` + `HttpOnly`
  `UL_SESSION` cookie. Fixing it needs a coordinated frontend change
  (`frontend/src/api/client.ts` treats every `/auth/` path as exempt)
  plus the backend exemption narrowed to unauthenticated endpoints
  only — outside this audit's no-frontend-changes scope. Same
  candidate: `POST /api/admin/auth/change-password` (MFA-gated).
- **Unbounded `findAll()` loads that need API pagination** (confirmed,
  not fixed — silently truncating would change behaviour; paging needs
  controller/contract changes): `AdminService.listUsers`,
  `verificationQueue`, `auditLogs`, `listAnnouncements`,
  `listEventsForModeration`, `listModerators`, `listIdentityChanges`
  (also N+1 `findById` per row), `ScheduledJobs.purgeIdImages`,
  `AuditService.verifyChain`. Also `PostService.listComments` /
  `listBookmarks` (scoped to one post/user).
- **Check-then-act races → 500:** `AuthService.register` (email /
  university+studentId) and `AdminService.createModerator` (admin
  email) rely on pre-checks; a true concurrent duplicate still hits
  the unique key as a 500 because there is no
  `DataIntegrityViolationException` handler for the race (D-137's 409
  handler now covers the exception type, but the race path was not
  regression-tested — it cannot be reproduced on H2's create-drop
  schema). Translating it is a cross-cutting handler decision.
- **`getOrCreateDepartment/UniversityRoom` race:** same check-then-
  create shape as the fixed private-room dedup; no pair key exists to
  dedupe on; concurrent double-creation possible, low impact.
- **Low API inconsistencies (recorded, not fixed):** create endpoints
  return 200, not 201 (only `register` is 201); `submitIdentityChange`
  echoes the request record (no id/status); inconsistent
  `Map` action-result shapes; `AdminAuthController.principalDto`
  returns `universityId: ""` for null; `GET /api/csrf` returns `""`
  token when absent; `AdminService.analytics` returns raw
  `queryForList` maps with snake_case keys; `StompController.send`
  NPEs on null auth/payload (STOMP error-frame path, outside REST
  scope). `createModerator` 24 h temp expiry intentionally not set
  (D-120 note; owner to decide).

### BLOCKED BY ENVIRONMENT

- Live MySQL / Flyway execution / boot + health check in this sandbox
  (see §9). All 36 fixes are therefore **FIXED AND VERIFIED on H2 +
  build, FIXED BUT NOT RUNTIME VERIFIED against live MySQL**.
- `mvn clean` offline in this sandbox (missing clean plugin; §8).

### Notes (no action)

- `AuditService.write` stays same-transaction by design (D-037), so
  failed-login audit rows for failed TOTP/email-OTP attempts roll back
  with the throwing transaction.
- OTP guess bound across resends ≈30/hour by design; in-memory limiter
  per D-003; `/api/admin/auth/**` remains CSRF-exempt as before
  (flagged in D-122, not changed); D-088 (event kind/type, WARN)
  still deferred.
- Decision-log numbering gap D-124–D-136 (unused numbers, no dangling
  references) — cosmetic, left as-is.
