# Worker D decisions (Phase 3, P2 — data integrity + business-logic triage)

Entries D-108–D-115. These are Worker D's block; they are NOT in
`docs/requirements/REQUIREMENTS_DECISION_LOG.md` (concurrent workers) — the
coordinator merges them. Worker A owns D-093–D-106, Worker B owns the OTP
rate-limiting block; Worker E owns `07-decisions-e.md`.

### D-108 MessageAttachment.createdAt + V011 data-integrity fixes (M6, M8)

- **Status:** ADOPTED
- `MessageAttachment.createdAt` was always null: the entity had no `createdAt`
  field at all (and an empty `@PrePersist onCreate(){}`), V005 created
  `message_attachments` without a `created_at` column, and
  `MessagingService.listMessageAttachments` passed a literal `null` into the
  DTO. Fix: entity field + `@PrePersist` (sets only if null, same pattern as
  sibling `Attachment`), DTO maps `a.getCreatedAt()`, and V011 adds the column
  (`DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)`).
- V011 also adds the M6 foreign keys that were missing: `appeals.appellant_id`
  → users (SET NULL), `appeals.decided_by` → admin_accounts (RESTRICT),
  `moderation_events.actor_admin_id` → admin_accounts (RESTRICT),
  `team_members.event_post_id` → posts (CASCADE). Deliberately NO FK for
  polymorphic targets (`appeals.target_id`, `moderation_events.target_id`,
  `reports.target_id`) or for `moderation_snapshots.message_id/sender_id` —
  a snapshot is D-033 evidence and must survive message deletion.
- M8 split: `profiles.links` becomes TEXT (the entity is `String @Lob` and the
  UI treats links as free display text; a plain URL is not valid JSON, so the
  MySQL JSON column would reject the write at runtime). `audit_logs.metadata`
  stays JSON: `AuditService` always writes `ObjectMapper` JSON, valid by
  construction.
- V011 is additive only (no CREATE/DROP TABLE, no migration rewritten).
  Live-verification note: V011's chain can only be proven by a genuine Flyway
  run against real MySQL; H2 cannot run the production migrations
  (MySQL-only DDL). Covered statically by `MigrationV011Test`.

### D-109 Verification-decision email + security-email rule (I-11, I-15)

- **Status:** ADOPTED
- On approve/reject the system now sends an email as well as the in-app
  notification (`NotificationEventListener` → `EmailProvider.sendSimple`,
  subjects "Your UniLink verification was approved" /
  "…was rejected"). The email body carries the rejection reason on reject.
  A provider failure is caught and logged only — email failure must never
  fail the verification decision.
- Security emails cannot be disabled (FR-NOT-01, D-029c):
  `NotificationService.setPreference` rejects `email=false` for
  `VERIFICATION_DECISION` with 400 `SECURITY_EMAIL_REQUIRED`. OTP and
  password-reset emails bypass this service, so they were never
  disableable. The set is `NotificationService.SECURITY_EMAIL_TYPES`
  (currently one member); adding a type is a one-line change.
- Notification list / read / unread-count stay open to any authenticated
  student: the VERIFICATION_DECISION in-app notification is how an
  unverified user learns the decision, so gating the list would break the
  delivery path the email now supplements.

### D-110 OCR university-name comparison (I-12, FR-AUTH-06)

- **Status:** ADOPTED
- The OCR scorer now compares the university name as the SRS requires:
  `TesseractOcrAdapter.score(text, fullName, studentId, universityName)`
  adds one component per distinctive token of the university name.
  Stopwords (`university, of, the, and, institute, college, school, united,
  national`) and tokens shorter than 3 chars are skipped, so a card that
  merely says "University" earns nothing; if no distinctive token remains
  the component is skipped entirely (score unchanged, not diluted).
- Regression found by the new tests and fixed in the same scorer: the
  student-ID check compared the raw ID against lowercased text, so an ID
  with any uppercase letter could never match (a perfect card scored at
  most 0.66). The check now lowercases the ID first. That line predates
  Worker D (initial commit d064f28).
- `OcrProvider.extract` gained a default 5-arg overload delegating to the
  4-arg form, so existing fakes keep working; `OcrJobService.runOcr` gained
  a 5-arg form; `AuthService.register` and `resubmit` pass the university
  name. The old 3-arg `score` delegates with a null university (unchanged
  behaviour).

### D-111 Suspension-appeal token wired + public appeal page (I-13, D-019)

- **Status:** ADOPTED
- Chosen over removing `POST /api/appeals/public`: the endpoint is now
  reachable. `AdminService.changeUserState` issues a suspension-appeal token
  (`ModerationService.createSuspensionAppealToken`, D-019, 7-day single-use)
  and emails the link `${baseUrl}/appeals/public?token=<token>` after
  SUSPEND and BAN; `decideRecommendation` does the same when a moderator's
  recommendation is confirmed. Any failure to issue/email is caught and
  logged — the suspension stands and the link can be re-issued.
- New public student-app page `PublicAppeal.tsx` at the unguarded route
  `/appeals/public` (a suspended user has no session): reads `?token=`,
  submits `{targetType:'USER', targetId:null, reason}` — the token flow
  ignores targetType/targetId and finds the appeal by token hash only.
  The old "endpoint can never succeed" state is gone; the token row is
  created by the suspend/ban path, so the page is only reachable via the
  emailed link.

### D-112 Moderator-scope remaining holes (I-14, D-036, D-032)

- **Status:** ADOPTED
- SUSPEND and BAN in `AdminService.changeUserState` now require Platform
  Admin (`policy.requirePlatformAdmin`); moderators must use the
  recommendation flow (`recommendSuspension` → Platform Admin confirms,
  D-036). REACTIVATE / REVOKE / FORCE_RESET are unchanged (FORCE_RESET is
  Worker A's).
- Dashboard `pendingAppeals` is now scoped for moderators
  (`JOIN users u ON u.id=a.appellant_id … AND u.university_id=<uni>`);
  platform keeps the global count.
- Analytics aggregates scoped for moderators: `eventsPerSector` filters
  `p.scope_university_id=<uni>`, `verificationTurnaroundHours` joins `users`
  and filters by university, `activeUniversities` filters
  `university_id=<uni>` (returns 1/0 for their university).
  `usersByState` / `usersPerDay30` were already scoped.
- Appeals list/decide, comment moderation and GLOBAL announcements were
  already fixed in Phase 2 verification and were not redone.

### D-113 Secondary-write guards (I-15, D-016)

- **Status:** ADOPTED
- `requireVerified` added to the secondary writes the audit listed:
  `PostService.unreact`/`unbookmark`; `MessagingService.myRooms`,
  `searchRooms`, `listMessages`, `markRead`, `listMessageAttachments`,
  `openMessageAttachment` (and `block`/`unblock`/`mute` at the controller,
  because the service takes raw ids); `ProfileController` deleteProject /
  deleteEducation / deleteAchievement / removeSkill / followSector /
  unfollowSector / unfollow; `SearchController.search`;
  `NotificationController.pref`.
- `GET /api/posts/{id}/comments` and `GET /api/teams/event/{id}` now take
  `Authentication` and apply the D-016 visibility rule (verified: any;
  unverified: NATIONAL only, else 404) — the same rule as `getPost` and
  `createTeam`.
- `ModerationService.appeal` now checks ownership via the existing
  `resolveOwner`: unknown target → 404 `NotFound("Appeal target")`,
  non-owner → 403 `Forbidden("Only the owner can appeal this item")`.
  `appealByToken` (the suspended-user path) is untouched.
- Deliberately NOT gated: `updateMe` / photo / cover (own-profile edits,
  not in the audit's §4.3 list) and notification list/read (see D-109).

### D-114 Admin IP allow-list enforced at login (I-16)

- **Status:** ADOPTED
- Chosen over deleting the entity: `AdminAuthService.login` now enforces
  `AdminIpAllowlist`. After password auth succeeds (and after the
  temp-password expiry check), if the allow-list has active rows and the
  resolved client IP matches none of them, the login is refused with 403
  `Forbidden("This IP address is not allowed to sign in to the Admin Portal")`
  and an `ADMIN_LOGIN_IP_BLOCKED` audit row is written. A blocked login does
  NOT reset the throttle.
- The list is opt-in: with no active rows every IP is allowed (previous
  behaviour preserved). Matching is exact IP or IPv4 CIDR
  (`ipMatchesCidr`, prefix 0–32; malformed input never matches). The IP
  used is the `IpResolver`-resolved client IP (Worker A, D-100).
- The admin password-change endpoint is Worker A's job (D-093), not this
  decision.

### D-115 I-17 + I-18 bundle (duplicate phones, resubmit counting, identity changes, deletion/purge, LoginAttempt)

- **Status:** ADOPTED
- Duplicate phones (D-077): registration is blocked with 403 when the phone
  belongs to a SUSPENDED or BANNED account; other duplicates remain allowed
  (soft uniqueness). The verification queue flags them:
  `VerificationRequestDto.duplicatePhone` is true when another user shares
  the applicant's phone, shown in the admin queue as a "Duplicate phone"
  badge and a review-pane warning.
- Resubmit cap (D-012): `AuthService.resubmit` now counts only `RESUBMIT`
  outcomes in the last 24 h (`countByUserIdAndOutcomeAndCreatedAtAfter`);
  APPROVED/REJECTED decision rows no longer consume the 3-per-24h cap.
- `previousAttempts` is now per-request
  (`countByVerificationRequestId`), not per-user.
- Identity-change decide path (FR-PROF-09, D-017): new admin endpoints
  `GET /api/admin/identity-changes` and
  `POST /api/admin/identity-changes/{id}/decide`, moderator-scoped by the
  applicant's university; a reason is mandatory; approve validates the new
  university is active, the department belongs to it and the student ID is
  free at the new university, then atomically swaps
  university/department/studentId (and `idCardKey` when a new one is
  present). Remaining gap (not expanded): the student submit path still
  takes no ID image, though D-017 mentions one.
- Account deletion now collects storage keys from ALL of the user's
  verification requests, not only the latest; the purge job additionally
  nulls `User.idCardKey` when it points at the purged object (registration
  stores one key on both rows).
- `LoginAttempt` is now written (one row per login attempt, success flag;
  write failure is swallowed so the login outcome stands) instead of
  deleting the dead table — the table is the audit trail for brute-force
  review.
- V006 `DELIMITER $$` trigger block: live-verification-only. It can only be
  proven by running Flyway against real MySQL; not attempted.
