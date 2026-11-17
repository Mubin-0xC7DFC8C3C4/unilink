# Phase 1 report — Backend boot blockers (2026-11-17)

Scope: audit items I-1 (M7), I-2 (M1/M2), I-3 (login throttle), plus a
third boot-blocker the new context test found (ambiguous
AuthenticationManager). No frontend or deployment code was touched.

## What was wrong, and the fix

### M7 — four Spring Data derived queries referenced properties that do not exist

- **Problem.** `UserCredentialRepository.findByUserId`,
  `ProfileRepository.findByUserId`, `EventDetailRepository.findByPostId` and
  `AdminTotpSecretRepository.findByAdminId` derive a query from a property
  (`userId` / `postId` / `adminId`) that none of the four entities has. All
  four entities name their id field `id` and map it to the FK column
  (`user_id` / `post_id` / `admin_id`): the PK *is* the foreign key.
- **Root cause.** The repository methods were written as if the FK were a
  separate field; Spring Data validates derived queries when the repository
  beans are created and throws `PropertyReferenceException`.
- **Fix.** Deleted the four derived methods and changed the 12 call sites to
  the inherited `findById(...)` (SecurityConfig, AuthService x2,
  AccountDeletionService, AdminService, ProfileService x2, FeedService,
  PostService, AdminAuthService x3). No entity or migration changed.
- **Proof.** Negative control: re-adding `findByUserId` to ProfileRepository
  makes RepositoryQueryTest fail with exactly
  `PropertyReferenceException: No property 'userId' found for type 'Profile'`;
  reverted byte-identical afterwards.
- **Files.** The four repository interfaces + the eight call-site files above.

### M1/M2 — entity fields with no column in the migrations

- **Problem.** `VerificationRequest.createdAt` and
  `NotificationPreference.createdAt` map to `created_at`, but V002
  (`verification_requests`) and V006 (`notification_preferences`) never
  create the column. With `ddl-auto: none` this fails only at runtime, on
  first read/write of those rows.
- **Root cause.** The two entity fields were added without a matching
  migration column.
- **Fix.** New additive migration
  `V008__missing_created_at_columns.sql`: `ALTER TABLE ... ADD COLUMN
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)` on both
  tables. No existing migration was rewritten; nothing is dropped or altered.
- **Files.** `backend/src/main/resources/db/migration/V008__missing_created_at_columns.sql`.

### Ambiguous AuthenticationManager (found by the new context test)

- **Problem.** `SecurityConfig` declares two `AuthenticationManager` beans
  (`studentAuthManager`, `adminAuthManager`), neither `@Primary`. Spring
  Security's `HttpSecurityConfiguration` autowires one `AuthenticationManager`
  by type (as the parent manager for the filter chains) and the context fails
  with "Found 2 beans for type AuthenticationManager, but none marked as
  primary". The backend could not have started even with a working database.
- **Root cause.** Two same-type beans, no primary; the two service consumers
  use `@Qualifier`, so only the framework's own injection point broke.
- **Fix.** Marked `studentAuthManager` `@Primary`. Verified from the
  Spring Security 6.3.4 bytecode that the autowired manager is used only as
  the *parent* manager; each chain still builds its own ProviderManager from
  its explicitly configured provider, so admin logins still use the admin
  provider.
- **Files.** `backend/src/main/java/com/unilink/config/SecurityConfig.java`.

### I-3 — login throttle never locked

- **Problem.** `LoginThrottle.recordFailure` compared "now" against
  "now minus 15 minutes", which is always true, so the failure count reset
  to 1 on every failure while unlocked and the 5-failure / 15-minute lock
  could never engage.
- **Root cause.** The window check was written against the wrong instant; the
  `Bucket` record also carried no last-failure timestamp, so no correct
  window check was possible.
- **Fix.** `Bucket` now carries `lastFailure`; failures accumulate within a
  15-minute window, lock at 5 for 15 minutes, a lock is not extended by
  further failures, and a success clears the bucket.
- **Files.** `backend/src/main/java/com/unilink/security/LoginThrottle.java`
  (separate commit).

## New tests (all in `backend/src/test`)

- `ApplicationContextTest` — boots the whole context against in-memory H2
  (MySQL mode), Flyway disabled, `ddl-auto=create-drop`. Catches repository
  validation and dependency-injection errors. Found the ambiguous
  AuthenticationManager above.
- `RepositoryQueryTest` — `@DataJpaTest` on H2; exercises the four shared-PK
  lookups plus a derived query. Any invalid derived query in any repository
  fails this slice at startup.
- `MigrationColumnsTest` — asserts the entities map `createdAt` to
  `created_at`, V002/V006 do not define the column, and V008 adds it.
- `LoginThrottleTest` — 5 consecutive failures lock; further failures keep
  the lock; success clears it; keys are independent.
- H2 added as a test-scope dependency (`backend/pom.xml`); the jar was
  fetched with `ops/fetch-deps.py` (2.2.224).

Two things the tests deliberately do NOT do: Flyway is disabled in the
context test because the production migrations use MySQL-only DDL that H2
cannot parse (`year` column, `FULLTEXT KEY`) — running them against H2 fails
for the wrong reason. And no test here proves MySQL connectivity: Java
processes still cannot reach MySQL in this sandbox.

## Test results

- Backend `mvn -o test`: **22/22 green, BUILD SUCCESS** (13 pre-existing +
  9 new: LoginThrottle 4, MigrationColumns 3, RepositoryQuery 1,
  ApplicationContext 1). Command and full log:
  `docs/phase-reports/phase1-tests.log`.
- Frontend (unchanged, re-verified): `npm run typecheck` clean,
  `npm run build` succeeds, Vitest **16/16**.

## Genuine startup attempt (MySQL, dev profile)

- Command: `mvn -o spring-boot:run -Dspring-boot.run.profiles=dev`
  (log: `docs/phase-reports/phase1-boot.log`).
- **Result: the backend still does NOT connect to MySQL.** Startup fails in
  `flywayInitializer` with `Communications link failure — the last packet
  sent successfully to the server was 0 milliseconds ago. The driver has not
  received any packets from the server.` (SQL State 08S01). Same interception
  as the approved probe of 18:43 +06. MySQL itself is healthy (listening on
  127.0.0.1:3306, CLI connects, `unilink_dev`/`unilink_test` recreated after
  the VM replacement).
- Consequence: Flyway has still never run; `unilink_dev` has no tables;
  `/actuator/health` was never reached. The repository-query and
  AuthenticationManager fixes are proven only by the H2 context tests, not by
  a live MySQL boot.

## Environment note

The VM was replaced again during this phase (~19:58 +06): Java, Maven, MySQL
and Tesseract were gone. Restored with
`apt-get install openjdk-21-jdk maven mysql-server tesseract-ocr` — Java
21.0.13, Maven 3.8.7, MySQL 8.0.46, Tesseract 5.3.4. MySQL was restarted and
the dev/test databases and users recreated with `ops/recreate-dev-db.py`.

## Remaining blockers

1. **Sandbox Java→MySQL interception (environment, unchanged).** Until it
   lifts, no live boot, no Flyway run, no integration test against MySQL is
   possible. Everything in this phase is proven against H2, not MySQL.
2. **Flyway migration chain never executed on real MySQL.** V001–V008 are
   unproven on the real database (H2 chokes on `year` / `FULLTEXT KEY`, so
   even the H2 run only got through V001–V003). First live boot must be
   watched for migration errors.
3. Phase 2 work (frontend↔backend contract mismatches I-4–I-10, etc.) is
   untouched, per instruction.
