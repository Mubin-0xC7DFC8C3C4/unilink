# Worker D report section (Phase 3, P2 — data integrity + business-logic triage)

Worker D block: audit I-11–I-18 + P2 data integrity (A). Decisions D-108–D-115
are in `07-decisions-d.md`. Nothing below is live-MySQL verified: the sandbox
still blocks Java→MySQL, so all backend verification is H2 unit/integration
tests and the frontend is mocked-fetch Vitest only. No commits were made by
Worker D (coordinator commits).

## Completed

### A. MessageAttachment.createdAt — root cause found and fixed

- Root cause: the entity had no `createdAt` field at all (and an empty
  `@PrePersist void onCreate(){}`), V005 created `message_attachments`
  without a `created_at` column, and `MessagingService.listMessageAttachments`
  passed a literal `null` into the DTO.
- `backend/src/main/java/com/unilink/identity/domain/MessageAttachment.java` —
  added `@Column(name="created_at") private LocalDateTime createdAt;` and a
  `@PrePersist` that sets it when null (same pattern as sibling `Attachment`).
- `backend/src/main/java/com/unilink/messaging/MessagingService.java` —
  `listMessageAttachments` now maps `a.getCreatedAt()` instead of `null`.
- Migration `backend/src/main/resources/db/migration/V011__worker_d_data_integrity.sql`
  (additive only; V010 belongs to Worker A): adds
  `message_attachments.created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)`;
  converts `profiles.links` JSON → TEXT (M8 — the UI treats links as free
  display text, which MySQL JSON would reject at runtime; `audit_logs.metadata`
  stays JSON because `AuditService` always writes `ObjectMapper` JSON); adds
  the M6 foreign keys `fk_appeal_appellant` (appeals.appellant_id → users,
  SET NULL), `fk_appeal_decided_by` (appeals.decided_by → admin_accounts,
  RESTRICT), `fk_modev_actor` (moderation_events.actor_admin_id →
  admin_accounts, RESTRICT), `fk_tm_event_post` (team_members.event_post_id →
  posts, CASCADE). Deliberately no FK for polymorphic targets
  (`appeals.target_id`, `moderation_events.target_id`, `reports.target_id`)
  or for `moderation_snapshots.message_id/sender_id` (D-033 evidence must
  survive message deletion).
- Related entities with empty `onCreate(){}` (`SystemSetting`,
  `UserCredential`, `UserSkill`, `UserSectorFollow`, `EventDetail`,
  `CommentMention`, `PostSectorTag`, `TeamMember`) were checked: none has a
  createdAt field/column or a DTO exposing one — consistent, not bugs.
- Test: `Phase3WorkerDTest.messageAttachmentCreatedAtIsPersistedAndReturned`
  (send a message with a PNG attachment, list attachments, assert non-null
  `createdAt`) + static `MigrationV011Test` (entity maps `created_at`, V005
  never created the column, V011 adds it and the four FKs, V011 creates/drops
  no tables).

### I-11 Verification-decision email

- `backend/src/main/java/com/unilink/notification/NotificationEventListener.java` —
  constructor now takes `UserRepository` + `EmailProvider`; `onVerified` /
  `onRejected` still create the in-app notification, then send the decision
  email via `EmailProvider.sendSimple`. Security emails cannot be disabled
  (D-029c), so no preference check. Provider failure is caught and logged
  only — email failure never fails the decision.
- Test: `Phase3WorkerDTest.verificationDecisionSendsEmailAsWellAsInAppNotification`
  invokes the listener directly (it is `@TransactionalEventListener(AFTER_COMMIT)`,
  so it does not fire inside a rolling-back test transaction) and asserts the
  email lands in `TestProviders.SIMPLE_EMAILS` and the in-app row exists;
  `verificationApproveTransitionsState` proves the approve path end to end.

### I-12 OCR university-name comparison

- `backend/src/main/java/com/unilink/integration/ocr/OcrProvider.java` — default
  5-arg `extract(..., String universityName)` delegating to the 4-arg form
  (existing fakes keep working).
- `backend/src/main/java/com/unilink/integration/ocr/TesseractOcrAdapter.java` —
  `score(text, fullName, studentId, universityName)` adds one component per
  distinctive university token; stopwords (`university, of, the, and,
  institute, college, school, united, national`) and tokens < 3 chars are
  skipped; with no distinctive token left the component is skipped (score
  unchanged, not diluted). Old 3-arg `score` delegates with null university.
- `backend/src/main/java/com/unilink/identity/service/OcrJobService.java` —
  5-arg `runOcr` passing the university name through.
- `backend/src/main/java/com/unilink/identity/service/AuthService.java` —
  `register` passes `uni.getName()`; `resubmit` loads the university name and
  passes it.
- Regression found by the new tests and fixed in the same scorer: the
  student-ID check compared the raw ID against lowercased text, so an ID
  with any uppercase letter (i.e. essentially every real ID, e.g.
  `CSE-2021-047`) could never match — a perfect card scored at most 0.66.
  The check now lowercases the ID first. This line predates Worker D
  (initial commit d064f28); the fix is in the scorer this item owns.
- Test: `OcrUniversityScoreTest` (5 tests) — university token raises the
  score, a missing/wrong university lowers it, generic "University" earns
  nothing, null university keeps old behaviour.

### I-13 Suspension-appeal token wired (not removed)

- `backend/src/main/java/com/unilink/admin/AdminService.java` — new private
  `issueSuspensionAppeal(User, String reason)`: creates the D-019 token via
  `moderation.createSuspensionAppealToken`, emails
  `${baseUrl}/appeals/public?token=<token>` ("Your UniLink account was
  suspended"); any exception is caught and logged — the suspension stands,
  the link can be re-issued. Called from `changeUserState` after SUSPEND/BAN
  and from `decideRecommendation` when a recommendation is confirmed.
- `AdminService` constructor gained `ModerationService`, `EmailProvider`,
  `AppProperties`, `IdentityChangeRequestRepository` (all Spring-wired).
- Frontend: new public page `frontend/src/features/auth/PublicAppeal.tsx`,
  unguarded route `/appeals/public` in `frontend/src/main.tsx` (a suspended
  user has no session). Reads `?token=`, submits
  `{targetType:'USER', targetId:null, reason}` to
  `/api/appeals/public?token=…`; shows success / error states; submit is
  disabled until a reason is typed.
- Tests: backend `Phase3WorkerDTest.suspendIssuesAppealTokenAndEmailsLink`
  (token row exists with hash + expiry, email sent) and
  `moderatorCannotSuspendOrBan` (recommendation-confirm path also issues
  token + email); frontend `worker-d.test.tsx` (4 tests: no-token error with
  no POST, submit posts the exact URL/body and shows confirmation, disabled
  submit, invalid token shows the server error).

### I-14 Moderator-scope remaining holes

- `AdminService.changeUserState` — SUSPEND and BAN now call
  `policy.requirePlatformAdmin(admin)` first (D-036; moderators use
  `recommendSuspension`). REACTIVATE / REVOKE / FORCE_RESET untouched
  (FORCE_RESET is Worker A's).
- `AdminService.dashboard` — `pendingAppeals` scoped for moderators via
  `JOIN users u ON u.id=a.appellant_id … AND u.university_id=<uni>`.
- `AdminService.analytics` — `eventsPerSector` filters
  `p.scope_university_id=<uni>`; `verificationTurnaroundHours` joins `users`
  and filters by university; `activeUniversities` filters
  `university_id=<uni>`. `usersByState` / `usersPerDay30` were already scoped.
- Tests: `Phase3WorkerDTest.dashboardPendingAppealsIsScopedToModeratorUniversity`
  (MU moderator 1, DU moderator 1, platform 2) and, for analytics,
  `analyticsSqlScopesAggregatesToModeratorUniversity` (static: the exact
  scoped predicates are in the production SQL) plus
  `analyticsActiveUniversitiesPredicateScopesByUniversity` (live on H2: the
  predicate returns 1 for a moderator, 2 for the platform). The analytics
  method itself cannot run on H2 at all — it uses MySQL-only `DATE()`,
  `DATE_SUB()`, `TIMESTAMPDIFF` and `CURDATE` (pre-existing from Phase 2,
  not just the scoped aggregates) — so a genuine `analytics()` run still
  needs real MySQL (I-19).

### I-15 Remaining guards

- `PostService.unreact` / `unbookmark` — `policy.requireVerified(p)`.
- `PostService.listComments(Long, Principals.UnilinkUserPrincipal)` is the
  new signature: it calls `getPost` for the D-016 visibility rule. The old
  single-arg overload was removed; `PostController` comments endpoint now
  takes `Authentication`. One test caller updated:
  `Phase2ServiceTest.java:211`.
- `TeamService.listForEvent(Long, Principals.UnilinkUserPrincipal)` — loads
  the post, 404 if not an EVENT, unverified → NATIONAL only, verified →
  `isVisibleTo` matrix. `TeamController.forEvent` now takes `Authentication`.
- `MessagingService` — `requireVerified` on `myRooms`, `searchRooms`,
  `listMessages`, `markRead`, `listMessageAttachments`,
  `openMessageAttachment`; `block` / `unblock` / `mute` guarded in
  `MessagingRestController` (the service takes raw ids).
- `ProfileController` — `requireVerified` on `deleteProject`,
  `deleteEducation`, `deleteAchievement`, `removeSkill`, `followSector`,
  `unfollowSector`, `unfollow`. Deliberately NOT on `updateMe` / photo /
  cover (own-profile edits, not in the audit's §4.3 list).
- `SearchController.search` and `NotificationController.pref` —
  `requireVerified`.
- `NotificationService.setPreference` — rejects `email=false` for
  `VERIFICATION_DECISION` with 400 `SECURITY_EMAIL_REQUIRED`
  (`SECURITY_EMAIL_TYPES`, D-029c/FR-NOT-01).
- `ModerationService.appeal` — ownership check via `resolveOwner`: unknown
  target → 404, non-owner → 403. `appealByToken` untouched.
- Tests: backend `Phase3WorkerDTest.unverifiedPrincipalCannotUseSecondaryWrites`
  (unreact/unbookmark/myRooms/listMessages/listComments/listForEvent),
  `appealRequiresOwnership` (non-owner 403, unknown 404, owner OK),
  `securityEmailsCannotBeDisabled`; controller-level
  `WorkerDControllerGuardsTest` (6 tests, Mockito, no Spring) for the
  profile deletes, search, notification prefs and messaging block/unblock/mute.

### I-16 Admin IP allow-list enforced

- `AdminAuthService.login` — after password auth and the temp-password
  expiry check, before `throttle.recordSuccess`: if the allow-list has
  active rows and the resolved client IP matches none, writes audit
  `ADMIN_LOGIN_IP_BLOCKED` and throws 403. A blocked login does not reset
  the throttle. Constructor gained `AdminIpAllowlistRepository`
  (after Worker A's `SessionService` param). Opt-in: no active rows → all
  IPs allowed (previous behaviour).
- Helpers: package-private `isIpAllowed(String)` and static
  `ipMatchesCidr(String, String)` — exact match or IPv4 CIDR (prefix 0–32;
  malformed input never matches). The IP is the `IpResolver`-resolved
  client IP (Worker A, D-100).
- Tests: `AdminIpCidrTest` (4 tests: exact, CIDR in/out, /0 and /32
  boundaries, malformed) + `Phase3WorkerDTest.adminLoginBlockedFromUnlistedIp`
  (allow-list `10.0.0.0/8`: `127.0.0.1` → 403, `10.5.6.7` →
  `NEEDS_ENROLMENT`; empty list → login proceeds).

### I-17 Duplicate-phone flagging + suspended-phone block

- `UserRepository.findByPhone` added.
- `AuthService.register` — 403 `Forbidden` when any user sharing the phone
  has state SUSPENDED or BANNED (D-077); other duplicates remain allowed.
- `AdminDtos.VerificationRequestDto` gained `Boolean duplicatePhone`; the
  queue passes it from `AdminService.verificationQueue`.
- Frontend: `VerificationItem.duplicatePhone` in `frontend/src/api/types.ts`;
  `frontend/src/admin/pages/Verifications.tsx` shows a "Duplicate phone"
  badge in the queue row and a warning in the review pane.
- Tests: backend `Phase3WorkerDTest.verificationQueueFlagsDuplicatePhonesAndCountsAttemptsPerRequest`
  (shared phone → true for both, distinct → false) and
  `registrationBlockedWhenPhoneBelongsToSuspendedAccount`; frontend
  `worker-d.test.tsx` (2 tests: badge only on the flagged row, warning in
  the review pane).

### I-18 Smaller fixes

- `VerificationAttemptRepository` — added
  `countByUserIdAndOutcomeAndCreatedAtAfter` and
  `countByVerificationRequestId`.
- `AuthService.resubmit` — cap counts only `RESUBMIT` outcomes in 24 h
  (D-012); decision rows no longer consume it.
- `AdminService.verificationQueue` — `previousAttempts` counts attempts on
  that request only.
- Identity-change decide path: new `AdminDtos.IdentityChangeRequestDto` (+
  `from(r, u)`), `AdminService.listIdentityChanges(admin)`
  (moderator-scoped) and `decideIdentityChange(admin, requestId, accept,
  reason, ip)` (reason mandatory; scope-checked; 409 if already decided;
  approve validates university active + department belongs + student ID
  free at the new university, then atomically swaps university/department/
  studentId and `idCardKey` if present; both outcomes audit
  `IDENTITY_CHANGE_<STATUS>`). New endpoints in `AdminController`:
  `GET /api/admin/identity-changes`,
  `POST /api/admin/identity-changes/{id}/decide`.
- `VerificationRequestRepository.findByUserIdOrderBySubmittedAtDesc` added.
- `AccountDeletionService.deleteOwnAccount` — collects `idCardKey` +
  `profilePhotoKey` from ALL of the user's verification requests.
- `ScheduledJobs.purgeIdImages` — also nulls `User.idCardKey` when it
  points at the same deleted object.
- `LoginAttempt` is now written: `AuthService` gained
  `LoginAttemptRepository`; one row per login attempt (success flag);
  write failure is swallowed so the login outcome stands.
- Tests: backend `Phase3WorkerDTest.resubmitCapIgnoresDecisionRows`,
  `resubmitCapBlocksAfterThreeResubmissions`,
  `identityChangeDecideApprovesAndSwapsValues`,
  `identityChangeDecideRejectsWithReason`,
  `accountDeletionDeletesKeysFromAllVerificationRequests`,
  `purgeIdImagesNullsStaleUserIdCardKey`, `loginAttemptsAreRecorded`.

### I-22 Text-muted contrast (done — trivial and safe)

- `frontend/src/design/tokens.css` — `--text-muted` was `#8b8b8b` light
  (3.35:1) / `#726f67` dark (3.85:1), both below WCAG AA 4.5:1. Set equal to
  `--text-secondary` in both themes (`#5b5951` light, 6.89:1; `#a9a69c`
  dark, 7.93:1). One line per theme, zero layout impact; the token is used
  only by `.eyebrow` and `.muted-caption` (non-essential text).

## Test results

- Backend (H2, no live MySQL): first full run — **210 tests, 5 failures, 2
  errors**. All 5 failures were Worker D's `OcrUniversityScoreTest`
  (expectations written against the assumed scorer behaviour; the run
  exposed the pre-existing student-ID case bug — fixed in the scorer, see
  I-12). One error was Worker D's analytics test (H2 cannot run the
  MySQL-only `analytics()` SQL — replaced by a static predicate check + a
  live H2 predicate check). The other error is Worker A's
  `ExifStripperTest.jpegWithOnlyNonExifApp1IsUnchanged`
  (`IndexOutOfBoundsException` in Worker A's `ExifStripper`) — not Worker
  D's code, not fixed here; see Incidents.
- After the fixes, the affected classes re-run green:
  `Phase3WorkerDTest` **21/21**, `WorkerDControllerGuardsTest` **6/6**,
  `OcrUniversityScoreTest` **5/5**, `AdminIpCidrTest` **4/4**,
  `MigrationV011Test` **6/6** (BUILD SUCCESS). Final full-suite re-run
  after the fixes: **211 tests, 0 failures, 0 errors, 0 skipped — BUILD
  SUCCESS.** (211 vs 210: the analytics replacement added one test net.
  Worker A's `ExifStripperTest` also passes in this run — Worker A fixed
  it in the meantime.)
- Backend compile: `mvn test-compile` after `rm -rf target` — **BUILD
  SUCCESS** (188 main + 34 test sources). Note `mvn clean` does not work
  offline (maven-clean-plugin 3.3.2 not in the local cache).
- Frontend: typecheck clean, build OK, Vitest **106/106 green** (was 94/94
  at Phase 2 close; +6 `worker-d.test.tsx`, +6 Worker A `phase3-auth.test.tsx`).

## Deferred / not done (with reasons)

- I-19 (live-MySQL integration tests) — out of scope per task; the sandbox
  blocks Java→MySQL. V011 and the V006 `DELIMITER $$` trigger block can only
  be proven by a genuine Flyway run against real MySQL.
- I-20 / I-21 (E2E / performance) — out of scope per task.
- Event kind/type storage and the WARN moderation action (D-088) — deferred,
  not implemented.
- Admin password-change endpoint — Worker A's job (D-093).
- OTP per-phone/IP rate limiting — Worker B's job (Worker A also touched
  per-phone resend caps, D-099/D-101).
- `POST /api/appeals/public` removal alternative for I-13 — not chosen;
  wiring + email + public page chosen instead (D-111).
- `frontend/src/admin/pages/Users.tsx` still shows SUSPEND/BAN buttons to all
  admins (no role is available in admin app state; moderators now get a
  surfaced 403 via `errorMessage`). Recommend-suspension UI never existed
  (audit-noted). Follow-up, not new scope.
- Identity-change submit path (`POST /api/profiles/me/identity-change`)
  still takes no ID image, though D-017 mentions one. Noted, not expanded.
- `updateMe` / photo / cover left without `requireVerified` (own-profile
  edits, not in audit §4.3 list).
- Notification list/read left open to unverified students (needed for
  verification-decision delivery, D-109).

## Incidents / things the coordinator should know

- **Worker A `ExifStripperTest` failure (not Worker D's):** the first full
  run showed `ExifStripperTest.jpegWithOnlyNonExifApp1IsUnchanged` erroring
  with `IndexOutOfBoundsException: Range [42, 42 + 3) out of bounds for
  length 44` inside Worker A's `ExifStripper`. Worker D did not touch that
  code; Worker A fixed it in the meantime and it passes in the final
  full-suite re-run (211/211 green).
- **Concurrent-build race on the shared `target/` directory:** a selective
  re-run of Worker D's classes failed all 27 tests with
  `NoClassDefFoundError: RoomMemberRepository` /
  `ClassNotFoundException: Exceptions$NotFound` / Mockito "class
  redefinition failed" — the class files were absent mid-run and present
  again minutes later (a `ps` at the time showed another surefire booter
  running). A clean re-run immediately after passed 27/27. The race is an
  artefact of parallel workers sharing one `target/`; it is not a code
  problem, but the coordinator should be aware that full-suite numbers
  taken while another worker is building can be polluted this way.
- **Toolchain wiped by the service restart (~17:23 UTC, 2026-11-17):**
  Java/Maven/MySQL/Tesseract were gone again; `~/.m2` survived. The
  runtime's own OS-intent replay restored Java/Maven by ~17:30 UTC; MySQL
  was not restored in this session and is not needed (all backend tests are
  H2). `mvn clean` does not work offline (maven-clean-plugin 3.3.2 is not
  in the local cache) — `rm -rf target` was used instead.
- **Worker A test-compile blocker resolved by Worker A:** at task start
  `MustChangePasswordTest.java:244,247,249` failed to compile
  (`isRecoveryCodeExpired` not public). A full clean recompile at ~17:32 UTC
  succeeds (188 main + 34 test sources) — Worker A fixed it in the
  meantime; Worker D did not touch Worker A's files.
- **`Phase3WorkerDTest.java` vanished once:** the file was written
  (11,054 bytes) and appended twice with success, but was absent from disk
  minutes later; all other files written in the same session survived
  (cause unknown — possibly a filesystem sync glitch around the restart).
  It was rewritten in full with the same content plus the signature
  corrections, and its existence was verified immediately after.
- **Stale incremental compilation:** after the restart, `mvn test-compile`
  reported "Nothing to compile" while no test classes existed on disk; a
  manual `rm -rf target` + recompile fixed it. If a future run looks
  suspiciously fast, delete `target/` first.
