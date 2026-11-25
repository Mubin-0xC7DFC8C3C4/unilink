# UniLink Requirements Clarification and Decision Log

Source: `UniLink_SRS (1)(1).docx` (the SRS) and `pic.docx` (visual reference).
Status values: **PROPOSED** (needs owner approval before the implementation agent relies on it), **ADOPTED** (follows directly from the SRS or from a project owner instruction), **OPEN** (needs an owner answer; the default shown is used until answered).

Rules for the implementation agent:
1. The SRS is the baseline. Nothing in this log edits the SRS text; each entry says what the SRS says, what is wrong or missing, and what to build.
2. Where an entry is PROPOSED, implement the stated default, mark it in the traceability matrix as "assumption", and keep it easy to change.
3. Never resolve a new ambiguity silently. Add an entry (next free ID) and continue with the safest reading.

---

## A. Technology conflicts

### D-001 Laravel versus Spring Boot (ADOPTED)
- Phase 1 note, 2026-11-17: owner approved the Spring Boot direction in principle (see D-065). SRS verification still outstanding (D-064).
- SRS 1.4 specifies Java 17+ with Spring Boot (MVC, REST), Spring WebSockets, Spring `@Scheduled`/Async and Spring Security. SRS 2.4 (Operating Environment) lists "Framework: Laravel".
- Evidence for Spring Boot: it appears in 1.4, in the Definitions table (Spring Boot, STOMP, SockJS), in the Admin Portal row and in the background-jobs row. Laravel appears once.
- **Decision:** Spring Boot 3.x on Java 17+ (Maven). SRS 2.4 is treated as a documentation error. Recommend the owner correct SRS 2.4 to "Spring Boot".

### D-002 Frontend stack differs from SRS 1.4 (PROPOSED, owner instruction)
- Phase 1 note, 2026-11-17: owner approved the React + TypeScript + Vite direction in principle (see D-065). SRS verification still outstanding (D-064).
- SRS 1.4: HTML5, CSS3, JavaScript, Bootstrap, optional Alpine.js or Vue. The project brief instructs React, TypeScript and Vite.
- **Decision:** React + TypeScript + Vite as a single-page app per the brief, served as static files behind the same origin as the API. Bootstrap is **not** used, because the visual reference (`pic.docx`) is a custom token-based system that Bootstrap defaults would fight. SRS 1.4 should be amended; this is a deliberate deviation, not an accident.
- MVC (NFR-MNT-01) is satisfied as: Spring MVC controllers (Controller), service layer plus JPA entities (Model), React views (View).

### D-003 Redis and other infrastructure (ADOPTED)
- Not required by the SRS. **Decision:** no Redis, no message broker, no Elasticsearch in v1.0. Sessions use Spring Session JDBC, rate limits use an in-process token bucket, WebSocket uses the in-memory STOMP broker behind an abstraction. Introduce Redis or an external STOMP relay only when a second application instance is deployed (see D-051), and record that change here.

### D-004 Quarkus and Copilot SDK (ADOPTED)
- The brief mentions Quarkus and Copilot SDK guidance. Neither is an SRS requirement. **Decision:** not used.

### D-005 Architecture style (ADOPTED)
- Phase 1 note, 2026-11-17: owner approved the modular monolith direction in principle (see D-065). SRS verification still outstanding (D-064).
- NFR-SCL-01 asks that web, WebSocket and storage layers can scale horizontally. **Decision:** modular monolith (one Spring Boot deployable, one MySQL schema, packages by domain), stateless request handling apart from the session store in the database. SRS 1.4 describing Spring Boot as a "microservices" framework is only a definition and does not require microservices.

---

## B. Authentication, verification and sessions

### D-010 Sign-up order of operations (ADOPTED, SRS wins over the brief's example flow)
- The brief lists: registration, OTP, email, ID upload, OCR. The SRS puts the ID card image **in the sign-up form** (FR-AUTH-01), runs OCR on the uploaded image (FR-AUTH-06), and moves to Pending Review only after OTP and email are both verified (FR-AUTH-07).
- **Decision:** one sign-up request creates the account in `REGISTERED` with the ID image stored privately. OCR runs asynchronously after upload. After OTP and email verification the account moves to `PENDING_REVIEW` and enters the queue, with the OCR result if ready, or an "OCR pending/failed" note (NFR-FLT-03).

### D-011 OTP channel and what "verified" means (PROPOSED)
- FR-AUTH-04: OTP to "phone or email". FR-AUTH-07: "After OTP and email verification". Ambiguity: is the OTP verifying the phone, and is the email link separate?
- **Decision:** the sign-up OTP verifies **phone ownership** by SMS by default, with email OTP as the fallback channel when the SMS gateway fails (NFR-FLT-02). The email **link** (FR-AUTH-05) is always a separate step. Pending Review requires both flags (`phone_verified_at`, `email_verified_at`). OTP: 6 digits, 5 minute expiry, 5 attempts, stored hashed, single use, resend cooldown and per-phone/per-IP daily caps (NFR-SEC-05).
- OPEN: should one phone number be allowed on multiple accounts? SRS requires unique email and unique student ID per university only. **Default:** do not enforce phone uniqueness, rate-limit per phone, flag duplicates to reviewers.

### D-012 Resubmission limit wording (PROPOSED)
- FR-AUTH-08: "resubmit up to 3 times in 24 hours".
- **Decision:** at most 3 resubmissions in any rolling 24 hours per account; the 4th attempt is refused with the time when it becomes available again. Each attempt is a row in `verification_attempts` (the SRS ER has a single `verifications` row with an `attempts` integer; see D-062).

### D-013 Session design (ADOPTED)
- NFR-SEC-06 requires secure HTTP-only cookies and invalidation on logout and password change. FR-AUTH-12: end after 2 hours of inactivity.
- **Decision:** server-side sessions (Spring Session JDBC), cookie `UL_SESSION` (HttpOnly, Secure, SameSite=Lax), idle timeout 2 hours for students. No JWT in browsers. All of a user's sessions are deleted on password change, suspension, ban and verification revocation. CSRF protection stays ON, using the double-submit cookie pattern (`XSRF-TOKEN` readable by the SPA, sent as `X-XSRF-TOKEN`).
- Single origin deployment (SPA and `/api` and `/ws` behind one hostname) so CORS can be an empty allow-list.

### D-014 Password hashing (ADOPTED)
- FR-AUTH-03 says "a secured way"; NFR-SEC-01 says "hashed". **Decision:** Argon2id via Spring Security `DelegatingPasswordEncoder` (OWASP parameters: 19 MiB memory, 2 iterations, 1 lane), so hashes can be upgraded later. Password rule exactly as FR-AUTH-03: at least 8 characters with upper, lower and a digit. Admin accounts use a stricter rule (D-031).

### D-015 Account states (ADOPTED)
- The SRS names states informally (Pending, Verified, Rejected, suspended, banned). **Decision, enforced by the State pattern:** `REGISTERED -> PENDING_REVIEW -> VERIFIED | REJECTED`; `REJECTED -> PENDING_REVIEW` (resubmit); `VERIFIED -> SUSPENDED | BANNED | REVOKED(back to unverified)`; `SUSPENDED -> VERIFIED` (reactivate); `* -> DELETED` (account deletion, see D-052). Unconfirmed registrations older than 7 days are purged together with their ID images.

### D-016 Who can read what while unverified (PROPOSED)
- SRS 2.7: unverified students can "read public information"; Visitor sees landing, sign-up and login only. "Public information" is undefined.
- **Decision:** an unverified student can read **National Feed** items marked `NATIONAL` visibility, read-only, plus their own profile and verification screens. They cannot see Department, University or Hub content, cannot comment, react, post, message, join teams or bookmark. Every one of those is enforced on the server (FR-AUTH-13, TC-07).

### D-017 Identity change re-verification (PROPOSED)
- FR-PROF-09 and 2.5: changing university, department or student ID requires repeating verification. Taken literally the user would lose access mid-change.
- **Decision:** the change is submitted as an `identity_change_requests` record with a new ID image and OCR run. The user **stays Verified with the old values** until a reviewer approves; on approval values swap atomically and community membership is recomputed. Rejection leaves the account unchanged.

### D-018 Email domain check (PROPOSED)
- SRS 5.6 mitigation lists "email domain check" but no FR requires it, and many students use personal email addresses.
- **Decision:** store optional `email_domains` per university. A mismatch is a **hint shown to the reviewer**, never a block.

### D-019 Suspended users who need to appeal (PROPOSED)
- TC-28: a suspended user cannot log in. FR-ADM-08: users can appeal suspension. These conflict.
- **Decision:** the suspension email contains a single-use, expiring appeal link. Opening it shows an appeal form (reason text) without granting a session. Banned users get the same link once. Rate-limited and audited.

---

## C. Community, content and events

### D-020 Missing master entity for "same department across universities" (PROPOSED, important)
- FR-COM-04 and TC-10: the National Department Hub shows the same department from every university. The SRS ER has `DEPARTMENTS(id, name, university_id)` only, so "CSE at University A" and "CSE at University B" have no link.
- **Decision:** add `department_catalog` (canonical department/discipline, e.g. "Computer Science and Engineering") and give each `departments` row a `catalog_id`. The Hub is keyed on `catalog_id`. Platform Admin manages the catalog (extends FR-ADM-10).

### D-021 Visibility model for posts (PROPOSED)
- FR-EVT-02 says "visibility options" without listing them; FR-COM-03 says the University section shows "all posts of the same university across all departments".
- **Decision:** `visibility` enum `DEPARTMENT | UNIVERSITY | DEPARTMENT_HUB | NATIONAL`, chosen by the author, with scope columns copied from the author's verified profile at creation. A post is shown in: **Department** feed if `DEPARTMENT` and same university and same department; **University** feed if `UNIVERSITY`, plus `DEPARTMENT` posts of the same university (literal FR-COM-03); **Hub** feed if `DEPARTMENT_HUB` and same `catalog_id`; **National** feed if `NATIONAL`. Moving a student between scopes never rewrites old posts.

### D-022 Messaging levels (PROPOSED)
- FR-COM-06: messaging "available at every community level". FR-MSG-01: group chat for Department and University only.
- **Decision:** persistent group rooms exist for Department and University (FR-MSG-01). For Hub and National levels "messaging and collaboration" means private chat and team chats can be started from any profile or event at those levels. No Hub or National group rooms in v1.0 (moderation load, spam).

### D-023 Event "registration" versus out-of-scope registration (ADOPTED)
- Scope table excludes "event registration inside UniLink". ER has `event_responses`, and the UI text mentions "participation buttons"; FR-PROF-07 shows "events joined".
- **Decision:** `event_responses` records `INTERESTED | GOING` as a personal signal only. Registration happens on the external `reg_link`. FR-ADM-07 "close registration" means setting `registration_closed`, which hides the link and shows "Registration closed".

### D-024 Past-event handling and time zone (ADOPTED)
- FR-EVT-05: a job moves events to Past when the deadline passes. **Decision:** feed and sidebar queries ALSO filter `deadline > now()`, so correctness never depends on the job's tick; the job (every minute, retried per NFR-REL-03, idempotent) sets `archived_at` and closes teams. All timestamps stored UTC, displayed and entered in Asia/Dhaka. Editing a deadline to a past time is rejected (same as FR-EVT-09); extending an archived event to a future deadline reactivates it.

### D-025 @mentions (PROPOSED)
- FR-EVT-04 needs @name tagging but names are not unique and the SRS has no username.
- **Decision:** the composer offers autocomplete among users allowed to see that event; the stored mention references user id (`comment_mentions`), not text. Mentioned users get an in-app notification.

### D-026 Team model (PROPOSED)
- SRS ER has `teams` and `team_members`; FR-TEAM-02 needs join requests; FR-MSG-04 separates "temporary group chat for team formation" from team chat.
- **Decision:** add `team_join_requests`. Team size includes the creator. A user may belong to one team per event. Team states `OPEN -> FULL -> CLOSED` and `OPEN|FULL -> CLOSED_EVENT_PAST` (State pattern). A team chat room is created with the team; members are added on acceptance (FR-TEAM-03). Standalone temporary chats (FR-MSG-04) are separate rooms of type `TEMP_GROUP`.

### D-027 Skills must be searchable (PROPOSED)
- ER stores `skills` and `interest_tags` as JSON on `profiles`, but TC-17 searches students by skill and FR-SEC-02 filters by sector.
- **Decision:** normalise into `skills`, `user_skills`, `sector_tags`, `user_sector_follows`, `post_sector_tags`. JSON stays only for `links` (display only).

### D-028 Search technology (PROPOSED, to be proven by measurement)
- NFR-PER-02: results within 2 s for up to 100,000 records; NFR-LOC-01: Bangla text.
- **Decision:** MySQL InnoDB FULLTEXT (word tokens work for space-separated Bangla), `utf8mb4`, `innodb_ft_min_token_size=2`, filters by indexed columns, keyset pagination, behind a `SearchStrategy` interface. Load-test with 100,000 seeded rows including Bangla. If the target fails, add an external engine as a new strategy; do not add it earlier.

### D-029 Message search scope (PROPOSED)
- FR-MSG-03 and TC-16 search "a conversation or person by name". **Decision:** search conversation names and participants only; no full-text search of message bodies in v1.0.

### D-029a Upload limits (PROPOSED values; SRS says only "a specific limit")
- Profile photo 2 MB, cover 5 MB, ID card 5 MB, event poster 5 MB, post attachment 10 MB (PDF or image), chat file 10 MB. Allowed types by content sniffing, not extension: JPEG, PNG, WebP, PDF (not for ID or photos). Images re-encoded to strip EXIF. All limits are configuration.

### D-029b Contribution points (OPEN, default: not built)
- Scope table lists "optional contribution points" but no FR defines it. **Default:** only the verification badge is built; contribution points are marked "Not implemented, no requirement" in the traceability matrix.

### D-029c Notification catalogue (PROPOSED)
- FR-NOT-01 lets users choose which notification types also arrive by email but no types are defined. **Decision:** types: verification decision, report auto-hide, appeal decision, mention, team join request and decision, new message when offline (digest), event deadline reminder for bookmarked events, announcement, warning. Security emails (OTP, password reset, verification decision, suspension) are not optional.

---

## D. Reporting, moderation and administration

### D-030 Admin identity is separate from student identity (PROPOSED, deviates from SRS ER)
- SRS ER puts `role` on `USERS`. Admin accounts are created only by a Platform Admin (SRS 2.5) and are staff, not students.
- **Decision:** separate `admin_accounts` table (roles `PLATFORM_ADMIN`, `UNIVERSITY_MODERATOR`, moderator linked to one `university_id`), never linked to a student account. Students always have role `STUDENT`. This makes student-to-admin privilege escalation structurally impossible. A person who is both uses two accounts.

### D-031 Administrator two-factor method (PROPOSED)
- FR-ADM-01 says "two-factor (OTP)". **Decision:** TOTP (RFC 6238 authenticator app) enrolled at first login, with 10 single-use hashed recovery codes; email OTP as fallback. Admin password at least 12 characters. Admin idle timeout 30 minutes (assumption; SRS only says "session timeout" in 5.6). Optional IP allow-list per NFR-SEC-07 as configuration. Admin login, 2FA and failed attempts have their own throttling and lockout.

### D-032 Moderator scope rule (PROPOSED)
- FR-MOD-02/03 limit moderators to "the assigned university", but content has both an author university and a scope.
- **Decision:** a moderator may act on a user if `user.university_id` equals theirs, and on an item if its **author** belongs to their university or its `scope_university_id` equals theirs. Anything else is a 404 (not 403, to avoid leaking existence). The scope is applied in the repository query, not only in the controller.

### D-033 Reported messages and privacy (PROPOSED)
- FR-ADM-05 includes reported messages. **Decision:** moderators never get general access to conversations. A report on a message stores a snapshot of that message plus the 10 preceding messages at report time, and only that snapshot is reviewable.

### D-034 What auto-hide applies to (PROPOSED)
- FR-REP-02 says "item" hidden from feeds after 5 different verified reporters.
- **Decision:** auto-hide for posts, events and comments. Profiles and messages are **flagged high priority** but not auto-hidden (hiding a profile is effectively a suspension and invites brigading). Reporter rules: verified, not the owner, one active report per target. The threshold (5), the window and the 3-strikes-in-30-days rule (FR-REP-04) are rows in `system_settings`, editable only by Platform Admin and audited.

### D-035 Content moderation lifecycle (ADOPTED from FR-ADM-06)
- Post/event/comment states: `ACTIVE`, `HIDDEN_AUTO`, `HIDDEN_ADMIN`, `REMOVED`, `EDIT_REQUESTED`, `ARCHIVED` (events). "Edit-request" means the admin asks the owner to edit and cannot rewrite user content. Transitions are in the State pattern and each writes an audit row.

### D-036 Moderator "recommend suspension" (ADOPTED from FR-MOD-05)
- Implement as `suspension_recommendations(PENDING -> CONFIRMED | DECLINED)`. Only a Platform Admin can confirm and the suspension is applied in the same transaction as the confirmation.

### D-037 Audit log immutability (ADOPTED, technical design)
- FR-ADM-11 and NFR-REL-04: cannot be edited or deleted from the portal; retained at least 1 year; "tamper-resistant" in the Definitions.
- **Decision:** (1) no update or delete API exists; (2) the runtime database user has `INSERT, SELECT` only on `audit_logs` (Flyway runs under a different DDL user); (3) a trigger rejects `UPDATE` and `DELETE`; (4) each row stores `prev_hash` and `row_hash` so tampering is detectable, with a verification job; (5) the audit row is written in the **same transaction** as the action. No purge below 1 year. Failed admin login attempts for an existing admin account are logged (TC-23); failures for unknown emails go to the application security log only.

### D-038 Verification overdue alerts (ADOPTED from FR-ADM-14)
- A job every 15 minutes finds `PENDING_REVIEW` older than 48 hours and creates one alert per request (idempotent) for moderators of that university; if the university has no active moderator it goes to all Platform Admins. Delivered in-portal and by email.

### D-039 Announcements placement (PROPOSED)
- FR-ADM-09 "pinned to feeds". **Decision:** a global announcement is pinned at the top of every section for all verified students; a university announcement only for that university's students, with an optional expiry.

### D-039a Reviewer usability target (ADOPTED)
- NFR-USA-06 "fewer than 1 minute" per verification: the review screen shows ID image, entered values, OCR match and previous attempts side by side, with keyboard shortcuts for approve and reject. Measured in moderated usability sessions, not asserted.

---

## E. Visual design

### D-040 Gaps in the visual reference (OPEN)
- `pic.docx` provides neutrals only. It has **no** accent colour, no error, warning or success colours, no focus ring, no scrim token, no confirmed font family, and approximate (measured) radii and spacing. `DESIGN.md` does not invent them.
- Contrast: lint of the reference colours reports `text-muted` at 3.35:1 (light) and 3.85:1 (dark) on the page background, below 4.5:1.
- **Default until answered:** status is communicated with text, icons and shape; font stack `system-ui` plus a Bengali fallback; `text-muted` limited to non-essential text. **Owner action:** supply the font name and any accent and status colours, or approve the defaults.

### D-041 Light and dark themes (ADOPTED)
- Both themes are defined in `DESIGN.md` and selected with `prefers-color-scheme`, overridable by a user setting stored in the database (not only localStorage).

---

## F. Operations, privacy and non-functional

### D-050 Measurable meaning of performance targets (PROPOSED)
- NFR-PER-01 "pages load within 3 seconds for 95%": **measure** as p95 largest-contentful-paint at most 3 s on a throttled mid-range mobile profile, plus p95 server response at most 500 ms for read APIs.
- NFR-PER-04 "1,000 concurrent users": **test** as 1,000 virtual users with 5 to 15 s think time and, separately, 1,000 simultaneously open WebSocket sessions. Reference hardware is not specified by the SRS; the test report must state the hardware used. No target may be reported as met without a test run.

### D-051 Horizontal scaling and real-time (ADOPTED)
- NFR-SCL-01 requires the architecture to allow scaling. v1.0 runs one application instance with the in-memory broker behind a `RealtimeBroadcaster` interface; the database is the source of truth for messages. A second instance requires an external STOMP relay or pub/sub and a shared rate limiter, at which point Redis becomes justified.

### D-052 Account deletion versus retention (PROPOSED)
- NFR-PRV-02 (permanent deletion) conflicts with FR-MSG-05 (messages stored permanently) and NFR-REL-04 (audit log kept 1 year).
- **Decision:** deletion removes the profile, contact details, ID images, posts, comments, bookmarks and follows. Messages the user sent remain for the other participants with the sender shown as "Deleted user" and personal identifiers removed. Audit rows keep only the numeric id, which no longer resolves to a person. Backups age out within 14 days (NFR-REL-02). Legal review against applicable Bangladeshi data-protection rules is required before launch; this log does not claim legal compliance.

### D-053 ID card image retention (PROPOSED)
- SRS 5.6 says ID images are deleted after verification; NFR-SEC-04 says private storage for owner and admins; FR-ADM-03 shows "previous attempts".
- **Decision:** approved submissions: image deleted 7 days after approval (configurable), keeping OCR result, scores and decision. Rejected: image kept 30 days to support appeal then deleted. Images are served only through an authenticated, authorised endpoint that streams the object or issues a 60-second signed URL. URLs never appear in logs or list responses.

### D-054 Availability and backups (ADOPTED)
- NFR-AVL-01 (99% monthly) cannot be demonstrated by design alone: requires monitoring and a measured month. NFR-REL-02: daily automated database backups kept at least 14 days, plus object storage backup, plus a documented restore drill whose result is recorded.

### D-055 Browser support (ADOPTED)
- NFR-CMP-01: latest two versions of Chrome, Firefox, Edge and Safari. Playwright projects for chromium, firefox and webkit. Edge is covered via the chromium project plus a manual check.

### D-056 Accessibility target (ADOPTED)
- NFR-USA-04 says "where practical". **Decision:** target WCAG 2.1 AA for student flows and the verification review screen, with automated axe checks in CI and keyboard-only e2e paths.

---

## G. SRS defects to correct in the next SRS revision

### D-060 Requirement ID ordering
- The SRS lists `NFR-USA-06` before `NFR-USA-05` and `NFR-SEC-07` before `NFR-SEC-06`. IDs are kept exactly as written (they are the traceability keys); only the order differs. Some IDs in the document are split across formatting runs (for example `FR-EVT-0` then `7`), which is a typography issue of the .docx, not a different ID. The implementation uses the canonical forms `FR-EVT-07`, `FR-REP-02`, etc. Recommend the owner re-check the SRS for such split runs before sign-off.

### D-061 Test case table
- TC-23 to TC-31 appear before TC-20 to TC-22; 31 test cases in total, none missing. Test IDs TC-01 to TC-31 are kept as the acceptance spine and mapped in `SRS_TRACEABILITY_TEMPLATE.md`. Several are ambiguous: TC-10 ("Open CSE Department of existing universities") is the National Department Hub; TC-14 and TC-15 end with a stray semicolon and no stated further result.

### D-062 SRS ER diagram gaps
- The ER lacks: batches, department catalog, admin accounts, OTP and email tokens, verification attempts, identity change requests, reactions, notifications and preferences, sector follows, post tags, skills, blocks, mutes, chat room settings, team join requests, appeals, report counters and strikes, suspension recommendations, announcements read state, system settings, attachments. `ARCHITECTURE_OVERVIEW.md` section 5 gives the complete table list the implementation must create.
- `USERS.student_roll` should be unique together with `university_id` (FR-AUTH-02).

### D-063 Smaller wording issues
- FR-COM-01 ("places a verified student automatically in a spaces derived from the profile") and FR-COM-05 ("all visibility events") are garbled; interpreted via D-021. NFR-FLT-02 "alternative channel" interpreted via D-011. FR-MSG-07 and the 1.2 scope row "optional contribution points" are covered by D-029a and D-029b.

---

## H. Phase 1 additions — 2026-11-17 (owner approval: Phase 1 only)

Added in Phase 1. The originals in `~/workspace/user/files/` were not modified; this is the project copy at `docs/requirements/REQUIREMENTS_DECISION_LOG.md`. No entry below finalises a security-sensitive or business-critical default. See `docs/phase-reports/01.md` for the full Phase 1 report.

### D-064 Source documents not yet received (OPEN)
- Addendum, 2026-11-17: CLOSED by D-072. SRS and pic.docx were received and verified — see `docs/phase-reports/01-addendum.md`.
- The original `UniLink_SRS.docx` and `pic.docx` were not in the workspace as of 2026-11-17. Only the six Markdown files were provided.
- **Decision:** do not treat any requirement, design value or acceptance criterion as verified until the actual documents are received. The 116 requirements and 31 test cases were checked only for internal consistency against `SRS_TRACEABILITY_TEMPLATE.md` (116 unique IDs, TC-01 to TC-31 with no gaps) — that check does not verify wording, completeness or IDs against the SRS.
- **Action on receipt:** recheck every ID and wording, re-run the 116 / 31 count, verify design tokens against `pic.docx`, identify missing / conflicting / incorrectly interpreted requirements, and issue a Phase 1 addendum updating this log and `docs/traceability.md`.

### D-065 Architecture direction owner-approved 2026-11-17 (ADOPTED, direction only)
- Owner approved as the intended v1.0 technology direction: React + TypeScript + Vite; Java + Spring Boot 3.x + Maven; MySQL 8; Flyway database migrations; Spring Security; modular monolith architecture; separate Student App and Admin Portal; backend-enforced RBAC and university-level moderator isolation.
- Do not introduce Laravel, Quarkus, microservices, Redis, Kafka, or other infrastructure without a documented technical justification and owner approval.
- This approves direction only. It does not verify any SRS requirement (see D-064) and does not approve Phase 2.

### D-066 Local development environment (ADOPTED for dev only)
- For local development, proceed with: MySQL 8; private local file storage; local email testing using a mail catcher; Tesseract OCR adapter; provider interfaces for SMS, email, OCR, and storage.
- Do not pretend external services are operational without real credentials and successful integration tests. Keep production provider credentials and hosting decisions as external dependencies.
- Phase 1 toolchain check, 2026-11-17, in this VM: Node v24.22.1, npm 10.9.4, Python 3.12.3, git 2.43.0 present; Java, Maven, Docker / Docker Compose, MySQL and Tesseract NOT FOUND. Provisioning those is a Phase 2 entry task (see Phase 1 report §4.1 and §9.3).

### D-067 Phone uniqueness — security-sensitive, remains OPEN
- Supersedes the D-011 default ("do not enforce phone uniqueness") as a final decision. That default is NOT final.
- Phone verification is part of account identity (FR-AUTH-04 / FR-AUTH-07). If one phone can verify many accounts, a single SIM can create multiple verified identities, weakening the "verified student" guarantee and enabling duplicate / Sybil accounts and evasion of suspension. If uniqueness is enforced strictly, legitimate cases are blocked: shared family phones, students without a personal phone, SIM recycling in Bangladesh, and students who change numbers.
- **Options for the owner:** (a) enforce unique phone per account; (b) keep the D-011 default — do not enforce, rate-limit per phone, flag duplicates to reviewers; (c) unique per university; (d) soft uniqueness — allow, but require manual review when a phone is already used, and block a phone attached to a suspended / banned account.
- Also to be decided: what happens on number change, whether a phone attached to a deleted account can be reused, and whether OTP rate limits are per phone, per IP, or both (NFR-SEC-05). Decide before Phase 5.

### D-068 Admin TOTP 2FA and recovery — PROPOSED design, recovery OPEN
- D-031 (TOTP, 10 single-use hashed recovery codes, email OTP fallback, admin password at least 12 characters, 30-minute idle timeout, optional IP allow-list, separate throttling / lockout) remains PROPOSED, not final.
- **Secure-design requirements to be approved before Phase 5 / 9:** TOTP secrets encrypted at rest (`ENCRYPTION_KEY`), never in logs or responses; enrolment only after password authentication, with a confirmation code before activation; recovery codes hashed, single-use, regeneration invalidates all prior codes, use is audited and notified; email OTP fallback short-expiry, single-use, rate-limited, audited, and visible to a Platform Admin; lost-authenticator recovery only via Platform Admin after identity verification, never self-service; bootstrap of the first Platform Admin is a documented, one-time, environment-variable command with forced password rotation and TOTP enrolment at first login; no public admin registration endpoint.
- **Owner question:** may email OTP alone ever satisfy the second factor for a Platform Admin, or must a Platform Admin always use TOTP / recovery code?

### D-069 Auto-hide — PROPOSED, pending SRS verification, with required safeguards
- D-034 remains PROPOSED, not final. Threshold 5, window and 3-strikes-in-30-days must be checked against the SRS on receipt; if the SRS differs, the SRS wins.
- **Required safeguards:** 5 distinct verified reporters; reporter verified, not the owner, one active report per target; duplicate prevention via unique constraint on `reports(reporter_id, target_type, target_id)` where active, plus a concurrency guard so simultaneous 5th and 6th reports hide exactly once; rate limit on reports; auto-hide for posts, events and comments only — profiles and messages are flagged high priority, not auto-hidden; owner appeal path (FR-REP-03) with admin decision audited and owner notified; 3 auto-hides in 30 days flags the user for admin review (FR-REP-04); threshold, window and strike rule are rows in `system_settings`, editable only by Platform Admin and audited.
- Brigading risk noted: 5 coordinated reporters can hide legitimate content. The mitigation in the documents is appeal + admin review + audit trail. Any additional safeguard is an owner decision (see Phase 1 report §9, Q5).

### D-070 Design provisional + WCAG AA — OPEN (D-040 remains OPEN)
- Use `DESIGN.md` as a provisional reference until `pic.docx` is available. Do not invent design values that cannot be verified from `pic.docx`.
- All essential text, interactive elements, status indicators and focus states must meet WCAG 2.1 AA contrast (4.5:1 for normal text). `text-muted` at 3.35:1 (light) and 3.85:1 (dark) on background is below AA and must not be used for important information; use `text-secondary` there.
- Focus-visible styles must be visible in both themes. The reference defines no focus ring; Phase 1 does not invent one. Status must be carried by text, icons and shape, not by new colours, until the owner supplies accent / status colours.
- Font family remains UNCONFIRMED; interim stack is `system-ui` with Noto Sans Bengali fallback. Bangla input / display (NFR-LOC-01) requires the Bengali fallback regardless of the final font choice.

### D-071 Verification, visibility, moderator scope and deletion policies — pending SRS verification (OPEN)
- Addendum, 2026-11-17: checked against the SRS text and confirmed as readings of it, except for the items in D-075, which remain assumptions. See `docs/phase-reports/01-addendum.md`.
- The following must be checked against the original SRS before being treated as final, even where the log marks them ADOPTED: verification flow and states (D-010, D-011, D-012, D-015, D-017), unverified access (D-016), visibility model (D-021, with D-020), moderator scope (D-032, with D-030), account deletion (D-052) and ID image retention (D-053).
- Until the SRS check is done, these entries are working assumptions only. If the SRS contradicts a log entry, the SRS wins on what the product does; the log entry must then be corrected and the change recorded here.

---

## I. Phase 1 addendum — SRS / pic.docx verification, 2026-11-17

See `docs/phase-reports/01-addendum.md` for the full verification. D-072 to D-075 are reserved for this addendum; the build coordinator's new decisions must start at D-076.

### D-072 SRS verification completed 2026-11-17 (ADOPTED)
- SRS received (`UniLink_SRS__1.docx`, 211 paragraphs, 29 tables) and checked by script against `SRS_TRACEABILITY_TEMPLATE.md`: 116 / 116 requirement IDs match exactly, no missing / extra / mis-transcribed IDs, no wording mismatches in the template's abridged text; 31 / 31 test cases TC-01 to TC-31 match.
- D-064 is closed. The template is now the verified traceability baseline; the SRS is the authority where the template abridges — notably FR-AUTH-01: sign-up collects full name, phone, email, university, department, batch, address, student ID number, student ID card image AND profile photo.
- Technology conflicts confirmed: SRS §1.4 is Spring Boot / Java 17+; SRS §2.4 "Framework: Laravel" is a single contradictory line (D-001 confirmed). SRS §1.5 calls Spring Boot a "microservices" framework only as a definition (D-005 confirmed).

### D-073 pic.docx verification completed 2026-11-17 (ADOPTED)
- Both screenshots viewed directly (dark Academic Overview, 1842x860; light Account modal, 1399x857). `DESIGN.md` is a faithful transcription within its stated limits.
- D-040 remains OPEN: font family, accent, status colours, focus ring and scrim are absent from the reference; several values are "approx". The font is a clean grotesque sans-serif but the exact family cannot be read from an image.
- Do NOT copy from the screenshots: (1) the "UniConnect" product name / branding — the product is UniLink; (2) the "Browser-local storage · Auto-saved on this device" model in the Account modal — UniLink stores authoritative state server-side in MySQL with server sessions (D-013); localStorage is not a source of truth.

### D-074 Sector list is open-ended (ADOPTED)
- FR-SEC-01 says sectors "including Software Engineering, Data Science, AI/ML, Cyber Security, Web Development, Competitive Programming etc." Seed the six named sectors; the Platform Admin manages the full list via FR-ADM-10. This closes Phase 1 Q7 in part — no further sector list is needed from the owner before Phase 4.

### D-075 Ambiguities that survive SRS verification (OPEN)
- The SRS does not resolve: (a) phone uniqueness — the SRS requires unique email and unique student ID per university only (FR-AUTH-02); D-067 soft-uniqueness default stands as the build decision; (b) what "public information" means for unverified students (SRS §2.7) — D-016 read-only National feed stands as the PROPOSED interpretation; (c) FR-COM-01 / FR-COM-05 garbled wording — D-021 stands; (d) contribution points — "optional" in scope (§1.2), no FR — D-029b not-built stands; (e) ID-image deletion timing — SRS §5.6 says "deleting ID images after verification" without a period; D-053 (7 days after approval, 30 days after rejection) stands as the operational interpretation.
- None of these blocks the build under the Master Execution Prompt; each is recorded as an assumption in the traceability matrix.

---

## I. Build decisions — 2026-11-17 (Master Execution Prompt; coordinator)

### D-076 Sign-up fields per full SRS text (ADOPTED)
- FR-AUTH-01 in full SRS text collects full name, phone, email, university, department, batch, address, student ID number, student ID card image AND profile photo. The traceability template's abridged text truncates after "ba…". Build sign-up from the SRS list, not the truncated template text. Address is stored on the profile and hidden from other users by default (FR-PROF-08); profile photo is stored via StorageGateway like other images.

### D-077 Phone uniqueness — soft uniqueness as the build decision (ADOPTED for v1.0)
- Do NOT globally enforce unique phone. Rate-limit OTP sends per phone and per IP, flag duplicate-phone accounts to reviewers in the verification queue, and BLOCK a phone attached to a suspended or banned account from verifying a new account. Number change: a verified student may change phone only via a new OTP verification on the new number; the old number is released. Deleted account: its phone may be reused after the account deletion job completes. Rationale: SRS requires unique email + unique student ID per university only (FR-AUTH-02, D-075a); strict phone uniqueness would block legitimate shared-family-phone students in Bangladesh, while soft uniqueness preserves the reviewer check against Sybil accounts.

### D-078 Admin TOTP — email OTP fallback rule (ADOPTED for v1.0)
- TOTP RFC 6238, secret encrypted at rest, 10 hashed single-use recovery codes, email OTP fallback rate-limited, audited and visible to Platform Admin — but email OTP alone NEVER satisfies the second factor for a Platform Admin (TOTP or recovery code required). For a University Moderator, email OTP fallback may satisfy 2FA only when TOTP is not yet enrolled or after a Platform Admin-approved reset, and each such login is audited. Lost-authenticator recovery only via Platform Admin after identity verification, never self-service.

### D-079 Auto-hide threshold as a setting (ADOPTED for v1.0)
- Auto-hide threshold is 5 distinct verified reporters (D-034 / D-069), stored as a row in `system_settings` (`REPORT_THRESHOLD_DEFAULT`), editable only by Platform Admin and audited. Duplicate reports by the same reporter do not count; one active report per target; transition is exactly-once under optimistic locking. Appeal path and 3-auto-hides-in-30-days strike flag stand as in D-034.

### D-080 Account-deletion endpoint shape (ADOPTED for v1.0)
- SRS NFR-PRV-02 requires students to permanently delete their account data; D-052 defines anonymise-and-delete. Implemented as `POST /api/me/delete` with `{password}` (password re-authentication, CSRF-protected, student session required) in `AccountDeletionService.deleteOwnAccount`, because that is the path the Settings page already calls. The traceability table's earlier `DELETE /api/me` was a planning assumption, superseded by the frontend contract.
- Semantics: the users row is hard-deleted in one transaction; all user-owned rows follow via `ON DELETE CASCADE` (credentials, OTPs, tokens, verification requests, profile data, posts, comments, reactions, bookmarks, follows, reports, notifications, teams); sent messages stay with `sender_id` set NULL and render as "Deleted user"; private storage objects (ID card, profile photo, incl. the latest verification request's keys) are deleted; all sessions are invalidated; the audit row keeps only the numeric id (written via `AuditService`, hash chain intact). The state machine's `* -> DELETED` is the terminal outcome; no row remains to carry the state, so a second call returns 404. Live verification pending the database permission.

### D-081 OTP resend endpoint (ADOPTED for v1.0)
- `POST /api/auth/otp/resend {userId}` added in Phase 2 (audit I-5 offered "add a resend endpoint or remove the resend button"). Caps: 60-second cooldown per user, max 5 resends/hour per user, max 10/hour per IP, reusing the `LoginThrottle` bucket pattern. Resend is rejected with 400 if the phone is already verified and 404 for an unknown user. Rationale: the backend could honestly support it (`OtpService.sendPhoneOtp` already existed), so removing the button would have hidden a working capability.

### D-082 Phone format is strictly 01[3-9]xxxxxxxx (ADOPTED for v1.0)
- Client zod regex and backend `@Pattern("^01[3-9]\\d{8}$")` on `RegisterForm.phone` accept only the 11-digit Bangladeshi mobile form. The `+880…` form is rejected with a clear message. Rationale: one canonical form keeps OTP delivery, duplicate-phone flagging (D-077) and login-throttle keys consistent; the form message tells the user the expected format.

### D-083 Batch optional on sign-up (ADOPTED for v1.0)
- `batchId` is nullable in the registration DTO, so the sign-up form leaves batch optional while university and department are required. Matches the backend contract as built.

### D-084 Idle-timeout notice is warning-only (ADOPTED for v1.0)
- The student app warns after 105 minutes idle (server session ends at 2 h) and the admin app after 25 minutes (server ends idle admin sessions at 30 min). The banner does not extend the server session; any activity dismisses it and restarts the timer.

### D-085 Admin session restore counts only MFA-verified admins (ADOPTED for v1.0)
- `GET /api/admin/auth/me` restore treats only MFA-verified admins as signed in; an admin who has logged in but not completed TOTP is routed to the TOTP step, not the dashboard.

### D-086 Team join-request decide body is {accept: boolean} (ADOPTED for v1.0)
- `POST /api/teams/requests/{id}/decide` takes `{accept: boolean}`, not the `{action, reason}` the audit's I-8 assumed. The backend contract governs; the frontend was changed to match it. Recorded because it corrects the audit text.

### D-087 No public moderators endpoint (ADOPTED for v1.0)
- Moderators are listed via `GET /api/admin/moderators` only. The audit's I-8 suggested `GET /api/public/*` for moderator reads; exposing admin accounts publicly would be wrong, so no public endpoint was added.

### D-088 WARN action and event kind deferred (OPEN)
- No `WARN` moderation action exists server-side, so the button was removed rather than wired to a fabricated action. No event kind/type field exists server-side, so the event form drops the type select (kind lives in title/body). Both need a schema/contract decision before they can be built.

### D-089 GET /api/health is public (ADOPTED for v1.0)
- Returns only `{status: "UP", service: "unilink-backend"}` — a liveness probe with no user data, same as `/actuator/health`. Added to the student chain's permitAll list (it previously 401'd because it sits under `/api/**`).

### D-090 Unauthenticated student-auth calls return 401, not NPE/500 (ADOPTED for v1.0)
- `/api/auth/**` is permitAll, so unauthenticated requests reached `AuthController` with a null `Authentication` and NPE'd (500) in `changePassword`, `me`, `deleteMe`, `status` and `resubmit` — the same class as the admin-TOTP NPE fixed earlier in Phase 2. `AuthController.requireStudentPrincipal` now throws 401 `Unauthorized` on a null authentication; a wrong principal type still yields 403 via `AccessPolicy.requireStudent`. Proven by `AuthController401Test` (5 tests). Note: `POST /api/me/delete` is not under permitAll, so its unauthenticated 401 comes from the filter chain (with a CSRF token); the controller guard is defence in depth there.

### D-091 Spring-session cleanup ERROR in H2 tests is benign (ADOPTED)
- `JdbcIndexedSessionRepository.cleanUpExpiredSessions` logs `BadSqlGrammarException: DELETE FROM SPRING_SESSION … Table not found` when an H2 test context lives past a minute boundary. Cause: H2 test databases have no SPRING_SESSION tables (Flyway disabled in tests, `ddl-auto=create-drop`); production gets the tables from V007. No production impact and no code fix; MockMvc tests that create a session set `spring.session.jdbc.initialize-schema=always` in their own test properties.

### D-092 EducationEntry/Achievement year column quoted in the entity (ADOPTED for v1.0)
- `year` is a reserved word in H2, so unquoted DDL/DML fails under H2; the entities now use `@Column(name="\"year\"")`. MySQL renders it backticked to the same column, and no migration was rewritten. This is a test-environment accommodation only, recorded so a future reader does not "simplify" it away.
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

### D-104 EXIF stripping is dependency-free and covers every image upload path (ADOPTED)
- EXIF is stripped by a new dependency-free parser, `storage/ExifStripper`
  (JDK only, no new Maven dependency): JPEG drops APP1 segments whose payload
  starts with `Exif\0\0`; PNG drops `eXIf` chunks; WebP drops `EXIF` chunks,
  repairs the RIFF size and clears the VP8X EXIF-present flag. Pixel data is
  copied byte-for-byte, so OCR and rendering are unaffected.
- Applied on ALL server-side upload paths that accept images: sign-up ID card
  and profile photo (`AuthService.register`), resubmitted ID card
  (`AuthService.resubmit`), profile photo and cover
  (`ProfileService.uploadPhoto/uploadCover`), post attachments
  (`PostService.addPostAttachment`), chat attachments
  (`MessagingService.sendMessageWithAttachment`). PDFs pass through unchanged
  (no EXIF segment exists in PDF).
- Size is validated on the ORIGINAL bytes, then stripping runs, then the
  stripped bytes are stored; the stored `size_bytes` is the stripped length.
- A structurally malformed image is returned unchanged (fail-open): it already
  passed magic-byte sniffing, no decoder can read EXIF from an unparseable
  container, and rejecting it would change previously-accepted uploads.
- Residual: non-EXIF APP1 segments (e.g. XMP) and PNG `iTXt`/`zTXt` text chunks
  are kept — they can in principle carry location/creator text. Dropping all
  APP1 segments and all PNG text chunks is a follow-up, not done here, because
  D-029a specifies EXIF removal and XMP may carry copyright the owner wants.
- Already-stored images are NOT re-processed: only new uploads are stripped.
  ID images age out anyway under D-053 (7/30 days); a backfill for old profile
  photos/covers/attachments is a separate job if the owner wants it.
- The event-poster path in D-029a (5 MB) has no upload endpoint in the code, so
  there is nothing to strip there yet; when a poster upload is built it must
  route through `UploadValidator` + `ExifStripper` like the other paths.

### D-105 Per-post attachment cap is 5; per-message 1 by design; no per-room cumulative cap (ADOPTED)
- Max **5 attachments per post**, enforced server-side in
  `PostService.addPostAttachment` (count checked BEFORE storing, so a rejected
  6th file leaves no orphaned object); over the limit returns 400
  (`ATTACHMENT_LIMIT`). The number 5 matches the SRS's "a specific limit"
  (D-029a) and the report auto-hide threshold's order of magnitude; it is a
  constant (`UploadValidator.MAX_ATTACHMENTS_PER_POST`) so it is one edit to change.
- Per-message: exactly **1 attachment per message**, enforced structurally —
  there is no API that adds a second file to a message
  (`sendMessageWithAttachment` creates one message + one attachment). No
  separate numeric per-message cap is needed.
- Per-room/per-chat cumulative cap: **none**. A cumulative cap would break
  long-lived department/university rooms, which legitimately accumulate files
  over a term. Abuse is bounded by the per-file 10 MB cap, verified-membership
  checks on every send/download, and the per-message limit above. A per-user
  daily upload quota is noted as a follow-up, not built here.
- Known race: the count check and the store are not atomic, so two concurrent
  5th uploads could both pass and leave 6 attachments. Consequence is one
  extra file, not a security breach; fixing it would need a DB lock or
  unique-constraint trick disproportionate to the risk.

### D-106 Upload hardening: sniffing, file names, traversal, download headers (ADOPTED)
- Magic-byte sniffing governs; declared content type and file extension are
  never trusted. Tightened in this work: JPEG now requires a real marker code
  as the 4th byte (`FF D8 FF 00` is rejected); PNG requires the full 8-byte
  signature (a 4-byte `PNG` prefix is rejected); PDF is an attachment type,
  never an image. GIF/SVG/text remain rejected everywhere.
- `UploadValidator.sanitizeFileName` is applied to every client-supplied file
  name before storage and before it is echoed in a `Content-Disposition`
  header: directories are stripped (no traversal, no absolute path), quotes,
  backslashes and control characters are replaced (no header injection),
  leading dots are removed, length is capped at 200 chars, null/blank becomes
  `file`. The stored `file_name` is the sanitised name, not the raw upload name.
- `LocalStorageAdapter.size()` had no traversal guard (unlike
  store/open/exists/delete) and could leak an arbitrary file's size/existence
  via an absolute or escaping key; it now applies the same root-normalisation
  check and returns -1 outside the root. `store()` now also rejects a bad
  namespace and defaults a null file name to `file`.
- ID-card downloads (`GET /api/admin/verification/{id}/id-image`) used to
  always answer `image/jpeg` even for a PNG/WebP ID card; the content type is
  now sniffed from the stored bytes (same pattern `ProfileService.readImage`
  already used).
- `AuthService.register`/`resubmit` used to store ID cards and photos with a
  hardcoded `"image/jpeg"` content type regardless of the real type; the
  sniffed type is now stored.

### D-107 Attachment lifecycle: owner-only delete, soft-deleted posts stay sealed (ADOPTED)
- New endpoint `DELETE /api/posts/{id}/attachments/{attachmentId}` (owner
  only): the row and the stored object are both removed, so a deleted
  attachment leaves no orphaned file. A non-owner, or a wrong post id, gets
  404 (never a hint the attachment exists). There was previously no
  attachment-delete path at all; message attachments have none either, and
  need none — messages cannot be deleted, so their attachments have no
  lifecycle of their own.
- Profile photo/cover replacement is already owner-only by construction (the
  controller passes the session user's id; the key deleted is read from that
  user's own row) and already stores the new object BEFORE deleting the old
  one, so a failed store never loses the current photo. No change to the
  ordering was needed; the replacement now also strips EXIF (D-104) and
  sanitises the file name (D-106).
- `deletePost`/`deleteEvent` only soft-delete (status `REMOVED`); their
  attachment files are deliberately KEPT so a moderation RESTORE can bring
  the post back — but while the post is not `ACTIVE` its attachments are no
  longer listable or downloadable (previously they were: `getPost` does not
  check status, so a "deleted" post's files stayed reachable). This closes a
  real gap found in review.
- `AuthService.resubmit` does NOT delete the previous ID-card object when a
  new one is stored: the rejected attempt's `VerificationRequest` row still
  references it, and D-053 keeps rejected ID images for 30 days to support
  appeal. The `purgeIdImages` job deletes it when retention expires. This is
  intentional, not an orphan.

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

### D-116 Sole Platform Admin recovery — break-glass via deployment-owner DB/console action with dual control (ADOPTED for v1.0)
- Problem: the sole Platform Admin loses BOTH the authenticator (TOTP secret) and all 10 recovery codes. There is no in-code recovery: `AdminService.resetModeratorCredentials` (`POST /api/admin/moderators/{id}/reset-credentials`) requires an authenticated, MFA-verified Platform Admin caller, so the locked-out sole admin cannot invoke it and no peer exists to do so; `AdminAuthService.verifyEmailOtp` refuses a Platform Admin outright (D-078); `enrolTotp` throws `ALREADY_ENROLLED` while `totpEnrolled` is true. (Phase 2 verification report `06-phase2-verification.md`, Notes.)
- **Decision:** recover by a deployment-owner-controlled database/console action with dual control and full audit. The Executor performs, in one transaction, exactly the MFA part of what `resetModeratorCredentials` already does in code — delete the `admin_totp_secrets` row, delete the `admin_recovery_codes` rows, set `admin_accounts.totp_enrolled = 0`, delete the admin's `SPRING_SESSION` rows — then the admin re-enrols through the normal login flow (`POST /api/admin/auth/login` returns `NEEDS_ENROLMENT` + fresh `otpauthUrl`; `POST /api/admin/auth/totp/enrol` verifies a code and issues 10 new single-use recovery codes). No password is reset in the main path: the password is not lost, and resetting it from a console would require hand-manufacturing an Argon2id hash and distributing a temporary password — new risk for no benefit. Password-also-lost is a separate escalation (§9 of the runbook: offline Argon2id hash with D-014 parameters, verified before use, delivered once out of band).
- **Who can trigger it:** the locked-out admin (Requester) asks via a pre-registered channel; the Deployment Owner (Executor, holds DB/console access) executes; a named senior person independent of the Requester (Authoriser, e.g. head of IT / registrar's nominee) verifies the Requester's identity out of band (at least two of: callback on pre-registered phone, pre-registered email, photo ID on video/in person, line-manager/HR confirmation) and signs the approval. Requester ≠ Authoriser; no execution without that signed approval; an approval is valid 24 hours for one execution only. Role-holders are named at deploy time in the deployment's private operations record.
- **Dual control / separation of duties:** approval and execution are by different people, and the Executor may not execute on a verbal-only, self-issued, or expired approval. Where the Deployment Owner is the locked-out admin (common in a small deployment), the Executor step is still performed only after the Authoriser's approval; the control then rests on the approval plus the audit trail, and that limitation is recorded in the incident record. Dual control here is procedural, not technical — a person with sole database access can technically act alone; the controls make misuse attributable and visible, they cannot physically prevent it.
- **Audit (what is logged and where):** (1) an incident record outside the UniLink database (ticket reference, timestamps, all three names, identity evidence, approval, audit row id, enrolment completion time; retained ≥1 year, same bar as D-037); (2) an `audit_logs` row `ADMIN_BREAK_GLASS_RESET` inserted by hand, chained per `AuditService` (`prev_hash` = latest `row_hash`, `row_hash` = SHA-256 hex of `prev_hash|action|target_type|target_id|actor_admin_id|reason|created_at-string`), `actor_admin_id` = recovered admin id (FK requires an existing admin row or NULL), metadata JSON naming ticket/requester/approver/executor, `method: "db-console"`, `passwordReset: false`, Executor host/IP — never a secret; (3) the application's own rows on re-enrolment (`ADMIN_LOGIN_PASSWORD_OK`, `ADMIN_TOTP_ENROLLED`, `ADMIN_LOGIN_OK` with `mfa: "totp"`). Post-recovery the chain is verified (`AuditService.verifyChain`) and the audit CSV export filed with the incident record. The `audit_logs` append-only triggers (D-037) mean the hand-inserted row can never be used to rewrite history.
- **Time limits and expiry:** approval valid 24 hours, one execution; the reset must only be executed while the Requester is present to re-enrol immediately, because from commit until re-enrolment the account is in `NEEDS_ENROLMENT` (password-only enrolment window — anyone with the password could enrol a fresh authenticator). Enrolment target within 1 hour, hard limit 24 hours; beyond that it is an open security incident, not a pending task.
- **Re-enrolment:** the ordinary flow, no special path — login → `NEEDS_ENROLMENT` → scan fresh `otpauthUrl` on the new device → enrol with a verifying code → 10 new recovery codes displayed once, stored immediately in two separate places (password manager + paper, never on the same device as the authenticator) → verify with a fresh TOTP code. Old secret and codes are confirmed gone (counts 0) and old sessions confirmed invalidated.
- **Revocation / closure:** the path is a procedure, not a standing credential, so there is nothing to disable — closure is a checklist: enrolment confirmed, old MFA material confirmed gone, sessions confirmed dead, chain verified + CSV filed, second Platform Admin created and TOTP-enrolled, any shared DB/console credentials rotated, approval spent (a further incident needs fresh approval), incident record closed with all timestamps, row ids, and the chain-check result.
- **What must NEVER be done:** no shared backdoor password, hardcoded credential, unauthenticated reset endpoint, or flag/config that bypasses authentication — in code, config, or "temporarily"; never disable TOTP globally; never grant an email-OTP exception for a Platform Admin (D-078); never delete the `admin_accounts` row to re-trigger `AdminBootstrap` (it runs only when no admins exist, and the delete fails while audit rows reference the account, `fk_audit_actor ON DELETE RESTRICT`); never write a temporary password in a ticket, email, chat, or log; never execute without signed unexpired approval; never leave the account in `NEEDS_ENROLMENT` overnight without treating it as an incident; never skip the audit row or "repair" the chain by updating/deleting a row.
- **Rejected alternatives:** (b) pre-provisioned sealed recovery credential (envelope/vault) — a standing secret with silent failure modes (lost/never-created envelope = unrecoverable at the worst moment; stolen sealed codes never expire; cannot be drilled without consuming it; a spare admin account is an unmonitored second Platform Admin); (c) standing second Platform Admin — adopted as *prevention* (§10 policy: maintain ≥2 Platform Admins at all times; a second admin is created as step 6 of every recovery), but it is not a recovery procedure for the sole-admin case; (d) escrowed/split TOTP secret or encryption key — key-management machinery the team does not operate, standing-secret failure mode, and compromise of the encryption key would expose every admin's TOTP secret; (e) email-OTP exception — re-opens the exact hole D-078 closed; (f) delete + bootstrap — destructive, unaudited, blocked by the audit FK; (g) unauthenticated endpoint / backdoor / TOTP disable — vulnerabilities, not trade-offs.
- **Single-person deployment (no second trusted person):** true dual control is impossible; the minimum viable control is detection, delay, and audit — (1) a named person *outside* the deployment (registrar/HR/parent-organisation contact) confirms the Requester's identity out of band; (2) written notice of the intended action to a pre-registered monitoring mailbox the Executor does not solely control, plus a minimum 4-hour cooling-off wait before executing; (3) written self-authorisation filed before any database change; (4) full audit as above; (5) a second Platform Admin created immediately after re-enrolment so the deployment stops being single-person. If that is impossible, the organisation accepts single-person operation with detection-only controls in writing, reviewed annually.
- **Prevention:** maintain at least two active Platform Admins at all times; store recovery codes separately from the authenticator device; drill §4–§5 annually on a non-production copy (never execute Step 1 against production). Known gaps this does not fix: no admin password-change endpoint (a recovered admin cannot rotate their own password without a peer — Phase 2 Notes); recovery codes are unsalted SHA-256 and never expire (Phase 2 Notes).
- **Owner actions:** name the Executor and Authoriser (and deputies) per deployment with pre-registered contact details; confirm the single-person acceptance in writing where it applies. D-068's recovery question is answered for the sole-admin case by this entry; the rest of D-068 (TOTP design approval) is unchanged.
- **Status:** ADOPTED for v1.0 as the recovery procedure (documentation only — no code, endpoint, flag, or config change in this task). No live MySQL verification claimed.

### D-117 Admin email-OTP wrong-guess counter must survive the verify transaction's rollback (ADOPTED)
- Same D-103 bug class as the phone and password-reset OTPs, found by Worker B and left for the coordinator because `AdminAuthService` was Worker A's file mid-rewrite: `verifyEmailOtp` did `setAttempts(n+1)` + `save` inside a `@Transactional` method, then threw `OTP_INVALID`, so the increment rolled back and the 5-attempt cap could never engage in production. The existing `AdminAuthSecurityTest.emailOtpAttemptCounterSurvivesFreshRequest` passes despite the bug because the test class is `@Transactional` — the persistence context still shows the rolled-back increment within the test.
- Fix mirrors D-103 exactly: `AdminEmailOtpRepository.incrementAttempts` (`@Modifying`, `REQUIRES_NEW`, atomic `UPDATE … SET attempts = COALESCE(attempts,0)+1`), used by `verifyEmailOtp`. Proven by new non-transactional `AdminEmailOtpAttemptsTest` (3 tests): wrong guesses counted across separate transactions, 6th guess hits `OTP_ATTEMPTS_EXCEEDED`, correct code still verifies after wrong guesses.
- Not changed: `AuditService.write` stays same-transaction by design (D-037), so `ADMIN_LOGIN_FAILED` rows written just before a throwing verify still roll back with the caller's transaction. The security-critical state (throttle counters, OTP attempt counters) now survives; the audit loss for failed auth attempts is a documented limitation in the Phase 3 report, not silently fixed by changing audit semantics.

### D-118 Users page hides SUSPEND/BAN from moderators (ADOPTED)
- Worker D restricted SUSPEND/BAN to Platform Admin server-side (D-112), but `Users.tsx` still rendered both buttons to every admin; a moderator clicking one got a 403. The admin session already carries `role` (`SessionUser.role`, restored from `/api/admin/auth/me`), so the page now renders SUSPEND/BAN only when `role === 'PLATFORM_ADMIN'`. Server-side 403 remains the enforcement; this is presentation only. Lead text updated to say Suspend and Ban are Platform Admin only. Proven by `users-role-gating.test.tsx` (2 tests: moderator sees no SUSPEND/BAN; Platform Admin sees both).

### D-119 Bootstrap admin must rotate its password at first login (ADOPTED) — Issue B
- Problem (audit Worker 3, §4E): `AdminBootstrap` created the first Platform Admin from `INITIAL_ADMIN_EMAIL` / `INITIAL_ADMIN_PASSWORD` without setting `must_change_password`, so the env-issued password — the one most likely to have been handled outside the product — was a persistent credential with no forced rotation, even though the class Javadoc said "Rotate the password … at first login" and the must-change filter + admin change-password endpoint (D-093/D-094) already supported the flow. The fix was confirmed missing at audit time and is now applied: `AdminBootstrap.run` sets `a.setMustChangePassword(true)`.
- Testability: the bootstrap read credentials via `System.getenv`, which cannot be set from a test, so the flag could not be proven by a test. The credentials are now bound with `@Value("${INITIAL_ADMIN_EMAIL:}")` / `@Value("${INITIAL_ADMIN_PASSWORD:}")` constructor parameters. Spring resolves `${...}` from the environment, so in production the values still come from the same env vars and behaviour is unchanged; the blank/short-password guards and logging (email only, never the password) are untouched.
- Proven by new `AdminBootstrapTest` (5 tests, H2, real repository path): bootstrap creates a PLATFORM_ADMIN with `mustChangePassword=true`, Argon2id hash verifying against the supplied password, and `createdAt` set (Issue A sibling — the bootstrap path goes through the entity `@PrePersist`, so Worker 1's timestamp fix covers it); second run creates nothing; pre-existing admin skips bootstrap; short password refused; missing credentials skipped.

### D-120 New moderators must rotate their Platform-Admin-issued password (ADOPTED)
- Problem (audit Worker 3, §4E): `AdminService.createModerator` set an initial password chosen by the Platform Admin but did not set `mustChangePassword`, unlike the bootstrap admin (D-119) and `resetModeratorCredentials` (D-094). A moderator's first password was therefore persistent with no forced rotation. Fixed: `createModerator` now sets `mustChangePassword(true)`.
- Deliberately NOT set: a 24 h temporary-password expiry. Unlike a credential reset, a new moderator may legitimately first log in days after creation, and an expiry would lock them out before first use. Owner to decide if the expiry should apply here too.
- Proven by `MustChangePasswordTest.createModeratorSetsMustChangeFlagAndLoginSignalsIt`: the stored flag is true and `AdminAuthService.login` reports `mustChangePassword=true`.

### D-121 Suspended / expired-temp login attempts must leave a login_attempts row (ADOPTED)
- Problem (audit Worker 3, §4D): `AuthService.login` recorded a `login_attempts` row on success and on wrong-password failure, but the `SUSPENDED`/`BANNED` and `TEMP_PASSWORD_EXPIRED` rejections threw `AppException` out of the `try` and the catch rethrew without recording — exactly the attempts most worth reviewing for forensics (I-18: one row per login attempt) left no row. Fixed: the `AppException` catch now calls `recordLoginAttempt(email, ip, false)` before rethrowing. The write is best-effort (already swallowed internally), so a logging failure still cannot break login.
- Proven by `SecurityChainsTest.suspendedLoginAttemptIsRecorded` and `expiredTempPasswordLoginAttemptIsRecorded` (service-level, H2).

### D-122 Narrow the CSRF exemption for authenticated /api/auth/** mutations (PROPOSED — not implemented)- Observation (audit Worker 3, §4D): `SecurityConfig.csrf` ignores `/api/auth/**` wholesale, so the authenticated mutations `POST /api/auth/change-password` (and the password-reset confirm, which is unauthenticated by design) skip CSRF validation. The login/logout endpoints must stay exempt (no session yet), but change-password is an authenticated state-changing call. Mitigation in place: the `UL_SESSION` cookie is `SameSite=Lax` + `HttpOnly`, which blocks cross-site POST cookies from third-party origins in modern browsers, so the practical exposure is low — but defence-in-depth says authenticated mutations should require the token.
- NOT changed by this worker: narrowing the exemption requires the frontend to send `X-XSRF-TOKEN` on `POST /api/auth/change-password`, and `frontend/src/api/client.ts` currently treats every `/auth/` path as CSRF-exempt. That is a coordinated frontend + backend change and outside this worker's scope (no frontend changes). Proposal for the coordinator/owner: exempt only `POST /api/auth/login`, `POST /api/auth/logout`, `POST /api/auth/register`, `POST /api/auth/verify-phone`, `POST /api/auth/otp/resend`, `GET /api/auth/verify-email`, and the password-reset request/confirm, and update `client.ts` to send the token for the remaining `/auth/` calls. Same note applies to `/api/admin/auth/**`: the TOTP/email-OTP steps are part of the login flow and must stay exempt, but `POST /api/admin/auth/change-password` is MFA-gated and authenticated, so it is the same class of candidate.

### D-123 Suspended / banned students must get the Forbidden + appeal guidance, not "incorrect password" (ADOPTED)
- Problem (audit Worker 3, §4D): `Principals.UnilinkUserPrincipal.isAccountNonLocked()` returned false for `SUSPENDED`/`BANNED`, so the `DaoAuthenticationProvider` threw `LockedException` before `AuthService.login` ever ran — its explicit `SUSPENDED`/`BANNED` branch (`Forbidden("Account is suspended… Use the appeal link sent to your email (D-019)")`) was unreachable, and a suspended user logging in with the correct password was told "Email or password is incorrect" with no mention of the appeal path. Proven by the failing `SecurityChainsTest.suspendedLoginAttemptIsRecorded`, which expected `Forbidden` and got `BadRequest/INVALID_CREDENTIALS`.
- Fix: `isAccountNonLocked()` now returns only `active` (matching `AdminPrincipal`); the state check stays in `AuthService.login`, which is the single place that can produce the appeal guidance. Behaviour change, all in the intended direction: correct password + SUSPENDED/BANNED → 403 with the appeal message (and a `login_attempts` row via D-121); wrong password + SUSPENDED/BANNED → still `INVALID_CREDENTIALS` and still consumes login-throttle quota; verified accounts → unchanged. A suspended account still cannot obtain a session: the `Forbidden` is thrown before `AuthController.saveAuth` runs.
- Proven by `SecurityChainsTest.suspendedLoginAttemptIsRecorded` (now green) plus the unchanged `LoginThrottleTest` / `MustChangePasswordTest` suites.



### D-137 Spring MVC client-error signals map to 400/404/405/409, not 500 (ADOPTED) — audit Worker 4, §4F
- Problem: `GlobalExceptionHandler`'s catch-all `@ExceptionHandler(Exception.class)` swallowed Spring MVC's own client-error signals, so a malformed JSON body / missing body (`HttpMessageNotReadableException`), a missing required parameter (`MissingServletRequestParameterException`, e.g. `GET /api/public/departments` with no `universityId`), a wrong path-variable type (`MethodArgumentTypeMismatchException`, e.g. `GET /api/profiles/abc`), an unknown path (`NoResourceFoundException`), and a wrong method (`HttpRequestMethodNotSupportedException`) all answered 500 INTERNAL_ERROR. Each now has its own handler with the status Spring itself would have used (400 `INVALID_REQUEST`, 404 `NOT_FOUND`, 405 `METHOD_NOT_ALLOWED`), in the ApiError shape; `HttpMediaTypeNotSupportedException` maps to 415. `IllegalArgumentException` maps to 400 (covers null ids reaching `findById(null)` and negative `PageRequest` sizes, e.g. `POST /api/teams {}` and `GET /api/notifications?limit=-1`) and `DataIntegrityViolationException` to 409 `CONFLICT`; both log at warn and never echo internal detail. One subtlety: the repository proxy translates `findById(null)`'s `IllegalArgumentException` into `InvalidDataAccessApiUsageException`, so that subclass is mapped to 400 explicitly — other `DataAccessException`s stay 500, as a genuine database fault should. The generic handler stays last-resort only. `ConstraintViolationException` now also lists the violations as field errors, like the `@Valid` handler (previously dropped).
- Proven by new `ApiErrorHandlingTest` (25 tests, MockMvc + H2): every case above was reproduced failing on HEAD c0d6ab7 (500s, or 200 junk rows — see D-140) before the fix and passes after it. No live MySQL verification claimed.

### D-138 Security-filter 401/403 answers are ApiError JSON, not container HTML (ADOPTED) — audit Worker 4, §4F
- Problem: both security chains' entry point / access-denied handler used `res.sendError(401/403)`, producing the container's HTML error page (empty body under MockMvc), while every controller error is ApiError JSON — a client parsing errors had to special-case the security layer. The admin idle-timeout filter answered a bare 401 with an empty body, and `MustChangePasswordFilter` hand-built JSON with only status/code/message/path (no timestamp/traceId). New shared `common/ApiErrorWriter.write` (same field set as ApiError, values JSON-escaped) is used by both chains, the idle filter, and the must-change filter. Messages are fixed literals, never exception detail.
- Proven by `ApiErrorHandlingTest`: unauthenticated student/admin 401 and authenticated-student-on-admin 403 now assert `application/json` + `$.code` UNAUTHORIZED/FORBIDDEN. No frontend change needed: `client.ts` reads `status`/`code`/`message` only, and the 401 redirect suppression for `/auth/` paths is unchanged.

### D-139 Failed sign-in is 401, not 400 (ADOPTED) — audit Worker 4, §4F
- Problem: `AuthService.login` and `AdminAuthService.login` threw `BadRequest` (400) `INVALID_CREDENTIALS` for a wrong password / unknown email. A failed sign-in is an authentication failure (401), and both login pages already branch on it (`Login.tsx` and `AdminLogin.tsx`: `status===401 ? 'Incorrect email or password. Please try again.'`) — with 400 that branch was dead code. Changed both login paths to 401 with the same code (`INVALID_CREDENTIALS`) and message, so the enumeration behaviour (identical error for unknown email vs wrong password, OtpRateLimitTest) is unchanged. Deliberately NOT changed: the wrong-current-password check in `changePassword` (both services) stays 400 — that is an authenticated request-validation failure, not a sign-in attempt — and the password-reset-confirm wrong-code check stays 400.
- Proven by `ApiErrorHandlingTest.studentLoginWrongPasswordIs401` / `adminLoginWrongPasswordIs401` plus updated `AdminAuth401Test`, `OtpRateLimitTest` (throttle + enumeration), `SecurityChainsTest` (cross-chain logins) and `MustChangePasswordTest` (service-level status assertion). Frontend redirect behaviour is safe: `client.ts` suppresses the session-expired redirect for `/auth/` and `/admin/auth/` paths.

### D-140 Null targets / unvalidated values no longer persist junk rows or NPE (ADOPTED) — audit Worker 4, §4F
- Problem: (a) `ModerationService.resolveOwner` switched on a possibly-null `targetType` (NPE → 500 for `POST /api/reports` / `/api/appeals` with no target) and `AdminService.decideContent` switched on null `targetType`/`action` the same way. (b) `PostService.react/unreact/bookmark/unbookmark` passed null `targetType`/`targetId` straight through: `react`/`bookmark` persisted a junk row (nullable columns) with 200, the deletes silently no-op'd with 200. The `reaction` value was an unvalidated free string — any value persisted. (c) `ProfileService.addSkill/addProject/addEducation/addAchievement` persisted junk rows with 200 for a null/blank name/title/institution. Fixes: null guards returning 400 `INVALID_TARGET` (moderation/report/appeal, admin decide, reactions/bookmarks), reaction restricted to `LIKE` — the only value the product uses (frontend `PostDetail`, traceability FR-CNT-02 contract `POST /reactions?reaction=LIKE`; extend the set if reactions grow) — and blank guards (`SKILL_NAME_REQUIRED`, `TITLE_REQUIRED`, `INSTITUTION_REQUIRED`) on the four profile creates. Null ids elsewhere (e.g. `POST /api/teams {}` → `findById(null)`, negative `limit`) are covered by the D-137 `IllegalArgumentException` → 400 mapping rather than per-service guards.
- Proven by `ApiErrorHandlingTest` (null reaction target, invalid reaction, null skill/project/education/achievement, null report/decide targets — all 200/500 on HEAD, 400 after). No live MySQL verification claimed.

### D-141 Calendar bounds are validated and the response is a camelCase DTO (ADOPTED) — audit Worker 4, §4F
- Problem: `GET /api/calendar` passed `from`/`to` strings straight to the SQL comparison, so garbage dates were silently coerced and the endpoint answered 200 with wrong results; it also returned a raw `queryForList` map whose `reg_link` key was the one snake_case key in the student API. `from`/`to` are now parsed (date `YYYY-MM-DD`, as the frontend sends, or ISO local date-time; 400 `INVALID_REQUEST` otherwise) and the endpoint returns a `CalendarEventDto` record (`id`, `title`, `deadline`, `location`, `regLink`). Query semantics (deadline in `(from, to]`, in-scope events only) and the `to` bound meaning are unchanged; the frontend `CalendarPage` reads only `id`/`title`/`deadline`/`location`, so the rename is safe.
- Proven by `ApiErrorHandlingTest.badCalendarDatesAre400Not500` (200 pre-fix) and `validCalendarDatesAre200`. No live MySQL verification claimed.

### D-157 Entity defaults must match NOT NULL columns — Hibernate sends explicit NULL, DB defaults never fire (ADOPTED) — audit Worker 1, §4A/§4B
- Root cause (one mechanism, 11 confirmed failures): every `DATETIME(3)/TINYINT(1) NOT NULL ... DEFAULT ...` column in V001–V011 is safe only if the column is omitted from the INSERT, but Hibernate includes every mapped column and sends an explicit NULL for any field left null in Java (no `@DynamicInsert` / `@ColumnDefault` anywhere in the codebase). On the user's Windows MySQL each of these inserts therefore fails with `Column '<col>' cannot be null`, while the H2 test database (schema generated from the entities, columns nullable) accepts the NULL — which is why the suite was green while production inserts were broken. The template case is Issue A: `AdminAccount.onCreate()` set `createdAt` but not `updatedAt` and there was no `@PreUpdate`, so the bootstrap insert failed; the same shape recurs in 10 more places (evidence = file:line of the entity field + the creation site that leaves it null + the migration column).
- Confirmed Critical (insert fails on MySQL): (1) `AdminAccount.updatedAt` — `identity/domain/AdminAccount.java:20-21`, left null by `AdminBootstrap.run`, `AdminService.createModerator`, `DevSeeder.moderator` (V003 `updated_at NOT NULL`). (2) `AdminAccount.mustChangePassword` — same file/sites (V010 `must_change_password NOT NULL DEFAULT 0`). (3) `User.updatedAt` — `identity/domain/User.java:23`, `AuthService.register:78-83`, `DevSeeder.student` (V002). (4) `UserCredential.mustChangePassword` — `identity/domain/UserCredential.java:10`, `AuthService.register:84` (id + hash only), `DevSeeder` (V010). (5) `Profile.updatedAt` — `identity/domain/Profile.java:14`, `AuthService.register:85`, `DevSeeder` (V002). (6) `Post.updatedAt` — `identity/domain/Post.java:20`, `PostService.createPost:39-43`, `DevSeeder.post` (V004). (7) `University.updatedAt` — `identity/domain/University.java:12`, `AdminService.createUniversity:365-366` (V001). (8) `VerificationRequest.submittedAt` — `identity/domain/VerificationRequest.java:18`, neither `AuthService.register:86-89` nor resubmit `:284-287` nor `DevSeeder` sets it, and `onCreate` did not either (V002 `submitted_at NOT NULL DEFAULT CURRENT_TIMESTAMP(3)`). (9) `Skill.active` — `identity/domain/Skill.java:8`, `ProfileService.addSkill:82` builds a Skill with name only (V001 `active NOT NULL DEFAULT 1`). (10) `AdminAlert.readFlag` — `identity/domain/AdminAlert.java:13`, both writers omit it: `AdminService` overdue alerts `:439-441` and `ModerationService.checkStrikes:92-94` (V003 `read_flag NOT NULL DEFAULT 0`); the strike path additionally swallowed the failure in `catch (Exception ignored)`, so the alert was silently lost.
- Confirmed High (insert fails, narrower reach): (11) `Profile.phoneVisible` — `identity/domain/Profile.java:12`, `ProfileService.updateProfile:61` and `uploadCover:201` create a Profile with id only when no row exists (registration sets it, so the main path was safe; the insert-if-missing paths were not) (V002 `phone_visible NOT NULL DEFAULT 0`).
- Confirmed Medium (correctness, no insert failure): no entity had a `@PreUpdate` at all, so `updatedAt` never refreshed from Java on update (Hibernate writes the loaded stale value back explicitly, which also suppresses MySQL's `ON UPDATE CURRENT_TIMESTAMP`). The five `updated_at` entities now refresh it in `@PreUpdate`.
- Latent/defensive (no JPA writer leaves them null today; same-pattern guard only): `DepartmentCatalog.active`, `Department.active`, `Batch.active`, `SectorTag.active`, `AdminIpAllowlist.active` (all `NOT NULL DEFAULT 1`; no writer exists for `DepartmentCatalog`/`AdminIpAllowlist`, the other three writers always set it).
- Fix (entity-level only, existing `@PrePersist` pattern, no new pattern, no migration, no new dependency): each affected `onCreate` now fills its field when null — `updatedAt = createdAt` on insert, `mustChangePassword = false`, `phoneVisible = false`, `readFlag = false`, `active = true`, `submittedAt = createdAt` — and the five `updated_at` entities gain `@PreUpdate updatedAt = now`. Caller-supplied values are never overwritten (`if (x == null)` guards; `createdAt` is never touched by `@PreUpdate`). No V012 was needed: Java now sends non-null values, so the schema is unchanged and no backfill is required (these inserts could never have succeeded, so no bad rows exist from JPA). Explicit service sets win over the defaults (e.g. Worker 3's D-119/D-120 `setMustChangePassword(true)` is untouched by the `false`-when-null default).
- Checked and NOT issues (all 56 entities vs V001–V011): every other mapped field is set by every creation site (OTP/token attempt/used counters, `status`/`type`/`visibility`/`role` strings, `AuditLog.prevHash`/`rowHash`, `Notification.readFlag`, `EventDetail.registrationClosed`, `RoomMember.role`, etc.); unmapped timestamp columns (`user_credentials.updated_at`, `system_settings.updated_at`, `team_members.joined_at`) are safe because Hibernate omits them and the DB default applies; empty-`onCreate` entities (`UserSkill`, `UserSectorFollow`, `EventDetail`, `CommentMention`, `PostSectorTag`, `TeamMember`, `SystemSetting`, `UserCredential`) map no timestamp field and their tables carry no mapped NOT NULL column left null; PKs/generated IDs, FKs (raw Longs, no `@ManyToOne`), uniques, enums-as-String, `TINYINT(1)`↔Boolean, `DOUBLE`/`INT`/`BIGINT` types, `V010 code_hash VARCHAR(255)`, `V011 profiles.links TEXT` and `message_attachments.created_at` all match; `ddl-auto: none` with Flyway as schema source. No reference to a database named `uniconnect` anywhere; the only `UniConnect` hits are the historical doc notes in `docs/phase-reports/01-addendum.md` and `docs/requirements/REQUIREMENTS_DECISION_LOG.md` (D-073) stating the old branding must NOT be copied.
- Proven by new `integration/EntityTimestampDefaultsTest` (14 tests, H2): each test persists the entity exactly as the production service builds it and reloads it from the DB, asserting the field is non-null, plus update tests asserting `updatedAt` refreshes while a preset `createdAt` is never overwritten. Reproduced failing on HEAD c0d6ab7 first (13 failures + 1 error — every predicted failure), then 14/14 green after the fix. The tests assert Java-side values deliberately: H2's generated schema is nullable, so only the value Hibernate will send to MySQL can prove the fix. No live MySQL verification claimed (sandbox still blocks Java→MySQL).
- One pre-existing test had to be repaired for the fix, not for a behaviour change: `Phase3WorkerDTest.accountDeletionDeletesKeysFromAllVerificationRequests` built its "old" request without a `submittedAt` and then used `findTopByUserIdOrderBySubmittedAtDesc` expecting the factory request — which only worked while `submittedAt` stayed NULL (the bug). With `submittedAt` now real, the just-created request is the newest, so the test overwrote its own `key1` before deletion ran and failed. The test now sets `old.setSubmittedAt(now minus 1 day)` so the request really is the older one. Isolated full-suite re-run after the repair: 287 tests, 0 failures, 0 errors (snapshot copy, to avoid the other workers' live edits racing the shared `target/` directory — see the report).

### D-142 Strike (FR-REP-04) alerts must be addressed to a real admin, and the hide must count in the same transaction (ADOPTED)
- Problem (audit Worker 2, §4C): `ModerationService.checkStrikes` saved the `STRIKE_FLAG` `AdminAlert` with `adminId` null. `AdminService.adminAlerts` reads by `adminId`, so the alert was visible to nobody; and the `uk_alert_once` unique key (with a NULL `admin_id`) never deduped it in MySQL, so every strike check wrote another invisible row. Second, the strike count filtered on `Post.updatedAt`, which is maintained by MySQL `ON UPDATE` only — the persistence context still held null for the post just hidden in the same transaction, so the 3rd auto-hide counted 0 for itself and the alert fired one hide late (on the 4th).
- Fix: the alert is addressed like the overdue-verification alerts — the author's university moderators, falling back to Platform Admins when there are none — with `adminId` and `universityId` set and an `existsByTypeAndTargetTypeAndTargetIdAndAdminId` guard so it is one alert per admin (the unique key is the backstop, not the mechanism). `maybeAutoHide` now also stamps `post.setUpdatedAt(now)` on the entity when hiding, so the strike count sees the hide in-transaction on any database. `ModerationService` gains an `AdminAccountRepository` constructor dependency (Spring-autowired; no other construction sites).
- Proven by `RepositoryServiceAuditTest.strikeAlertIsAddressedToUniversityModerator` (H2): 3 posts × 5 distinct reporters → all `HIDDEN_AUTO`, the author's university moderator holds a `STRIKE_FLAG` alert for the author, and no alert row has a null `adminId`.
- Numbering note: D-122 was already taken in this log by Worker 3 (CSRF exemption proposal) when this worker started; these entries therefore use D-142–D-156 (D-137–D-141 were already in use in code by other workers). The coordinator should confirm the final numbering.

### D-143 Suspension-appeal links are single-use (ADOPTED)
- Problem (audit Worker 2, §4C): `ModerationService.appealByToken` looked the appeal up by token hash with no status check, so after a successful submission (`PENDING`) the same link could be re-submitted until its 7-day expiry, overwriting the appellant's reason.
- Fix: the token is accepted only while the appeal is `AWAITING_SUBMISSION`; otherwise `BadRequest TOKEN_USED`. Expiry is still enforced.
- Proven by `RepositoryServiceAuditTest.appealTokenIsSingleUse` (H2).

### D-144 Message reports snapshot the target plus the 10 preceding messages (ADOPTED)
- Problem (audit Worker 2, §4C): the D-033 comment in `ModerationService.report` promises "this message + 10 preceding", but only the reported message was snapshotted, so a moderator reviewing a message report had no conversation context.
- Fix: after snapshotting the target, the 10 newest preceding messages in the same room (`MessageRepository.findByRoomIdAndIdLessThanOrderByIdDesc`, `PageRequest.of(0, 10)`) are snapshotted too. No general conversation access is added — only the 10 preceding.
- Proven by `RepositoryServiceAuditTest.messageReportSnapshotsTargetPlusPreceding` (H2): 3-message room, report the 3rd → 3 snapshots.

### D-145 Team join decisions validate before mutating the request (ADOPTED)
- Problem (audit Worker 2, §4C): `TeamService.decideRequest` saved the request as `ACCEPTED` first and only then ran the `ALREADY_IN_TEAM` check (and the team-room lookup). A failing accept consumed the request — it could never be re-decided — without the user joining the team.
- Fix: on accept, the membership check and the room lookup run before the request is marked `ACCEPTED`; on reject the request is marked `REJECTED` and returned. The success path (member + room member + FULL check + event) is unchanged.
- Proven by `RepositoryServiceAuditTest.failedTeamAcceptLeavesRequestPending` (H2, non-transactional so the committed state is read back): accept throws `ALREADY_IN_TEAM`, the request is still `PENDING`, the user is not a member of the team.

### D-146 Private rooms are deduplicated per pair; markRead validates the message (ADOPTED)
- Problem (audit Worker 2, §4C): (a) `MessagingService.createPrivateRoom` created a fresh `PRIVATE` room on every call — `chat_rooms` has no unique key on the pair, so the same two students accumulated duplicate conversations. (b) `markRead` accepted any `Long` as `messageId`, so a caller could advance `last_read_message_id` past messages that do not exist or live in another room, corrupting read receipts.
- Fix: (a) before creating, scan the caller's rooms for a `PRIVATE` room already containing the other user and return it. (b) `markRead` loads the message and requires it to belong to the room (`NotFound` otherwise).
- Proven by `RepositoryServiceAuditTest.privateRoomIsReusedForSamePair` and `...markReadRejectsForeignAndMissingMessages` (H2). Note: the department/university `getOrCreate*Room` paths still have the same check-then-create shape, but they are single-owner system rooms with no pair key to dedupe on; concurrent double-creation there is left as a known race (see §4C report — proposal, not fixed).

### D-147 Mark-all-read marks all unread notifications (ADOPTED)
- Problem (audit Worker 2, §4C): `NotificationService.markAllRead` paged the first 50 unread rows and saved those, so a user with more than 50 unread notifications kept the rest unread after "mark all read".
- Fix: bulk `UPDATE Notification SET readFlag = true WHERE recipientId = :userId AND readFlag = false` (`NotificationRepository.markAllRead`, `@Modifying`, run inside the service's transaction).
- Proven by `RepositoryServiceAuditTest.markAllReadMarksBeyondFifty` (H2): 55 unread → 0 after one call.

### D-148 Email-verification links resolve by token hash, not a table scan (ADOPTED)
- Problem (audit Worker 2, §4C): `AuthService.verifyEmailByToken` called `emailTokenRepo.findAll()` and scanned every email-verification token on each link click.
- Fix: `EmailVerificationTokenRepository.findTopByTokenHashAndUsedFalseOrderByCreatedAtDesc`. Behaviour is unchanged: the row is the newest unused token for that hash, and `verifyEmail` below still enforces that it is the user's newest unused token (an older superseded link still fails with `TOKEN_INVALID`).
- Proven by `RepositoryServiceAuditTest.emailVerificationByTokenWorks` (H2): a fresh link verifies the email; an unknown token is `TOKEN_NOT_FOUND`.

### D-149 Duplicate master data is a 409, not a 500 (ADOPTED)
- Problem (audit Worker 2, §4C): `AdminService.createUniversity` / `createSector` / `createDepartment` did no duplicate or existence checks, while V001 has unique keys (`uk_universities_name`, `uk_sector_name`, `uk_dept_uni_catalog`) and FKs — and there is no `DataIntegrityViolationException` handler, so a duplicate or bogus reference surfaced as 500 `INTERNAL_ERROR` instead of a client error.
- Fix: `createUniversity` and `createSector` pre-check `findByName` → `Conflict` (`UNIVERSITY_EXISTS` / `SECTOR_EXISTS`); `createDepartment` requires an existing university and catalog (`NotFound`) and rejects a duplicate (university, catalog) pair (`Conflict DEPARTMENT_EXISTS`). The concurrent-race window (check-then-act) is unchanged — a true concurrent duplicate still hits the unique key as a 500; translating that needs a handler decision (see §4C report — proposal, not fixed).
- Proven by `RepositoryServiceAuditTest.duplicateMasterDataIsConflictNot500` (H2).

### D-150 Post sector ids are validated and deduplicated; sector follows require an existing sector (ADOPTED)
- Problem (audit Worker 2, §4C): `PostService.createPost` / `updatePost` wrote `PostSectorTag` rows for whatever ids were passed. `post_sector_tags` has an FK to `sector_tags` (RESTRICT) and a `(post_id, sector_tag_id)` unique key, so an unknown id or a repeated id failed the save as a 500. Same class in `ProfileService.followSector` (`user_sector_follows` FK to `sector_tags`).
- Fix: `PostService` (new `SectorTagRepository` constructor dependency) validates each distinct sector id exists (`NotFound`) before writing; `followSector` requires the sector to exist first.
- Proven by `RepositoryServiceAuditTest.postSectorIdsAreValidatedAndDeduped` and `...followUnknownSectorIsNotFound` (H2).

### D-151 One pending identity-change request per user (ADOPTED)
- Problem (audit Worker 2, §4C): `ProfileService.requestIdentityChange` created a row unconditionally, so a user could hold several `PENDING` requests for the same change and the admin decide path had two live requests.
- Fix: `IdentityChangeRequestRepository.existsByUserIdAndStatus` guard → second `PENDING` submit is `Conflict REQUEST_PENDING`. The decide path (D-017) is unchanged.
- Proven by `RepositoryServiceAuditTest.duplicatePendingIdentityChangeIsConflict` (H2).

### D-152 Feed comment counts use a COUNT query (ADOPTED)
- Problem (audit Worker 2, §4C): `FeedService.toDto` computed `commentCount` as `findByPostIdOrderByCreatedAtAsc(postId).size()` — a full comment load per post in every feed page (up to 50 posts → 50 full comment loads).
- Fix: `CommentRepository.countByPostId` and use it in `toDto`. The counted set is the same one `listComments` returns.
- Proven by `RepositoryServiceAuditTest.feedCommentCountCountsWithoutLoadingComments` (H2): 3 comments → `commentCount == 3`. (The per-post author/event/sector lookups in `toDto` remain N+1 in the small; batching them is a larger change and is left as a proposal in the §4C report.)

### D-153 Catalog batches are fetched with a scoped query (ADOPTED)
- Problem (audit Worker 2, §4C): `PublicCatalogService.batches(universityId, null)` loaded the whole `batches` table (`findAll`) and filtered in memory.
- Fix: `BatchRepository.findByUniversityIdAndActiveTrue`.
- Proven by `RepositoryServiceAuditTest.batchesForUniversityAreScoped` (H2).

### D-154 The event list is bounded to the 50 newest (ADOPTED)
- Problem (audit Worker 2, §4C): `PostService.listEvents` was an unbounded `findAll(spec, Sort)` — every matching event in the system, with no limit parameter on the controller (`GET /api/events`).
- Fix: `PageRequest.of(0, 50, Sort DESC createdAt)`. The API shape is unchanged (still a list); the bound is the same 50 the feed/search paths already use.
- Proven by `RepositoryServiceAuditTest.eventListIsBoundedToFifty` (H2): 55 upcoming events → 50 returned.

### D-155 Account deletion also removes the cover photo and post-attachment files/rows (ADOPTED)
- Problem (audit Worker 2, §4C): `AccountDeletionService` collected ID-card and profile-photo keys (plus all verification-request keys, I-18) but never the profile cover key, so the cover file orphaned in storage after deletion (NFR-PRV-02). Separately, the user's posts cascade-delete with the `users` row, but the `attachments` table has no FK to posts (polymorphic owner), so post-attachment rows and their files orphaned too.
- Fix: collect `Profile.coverKey`; sweep the user's posts' `POST` attachments (rows via new `AttachmentRepository.deleteByOwnerTypeAndOwnerId`, files via the collected keys) before the user row is deleted. Deliberately NOT swept: message attachments — sent messages stay (sender `SET NULL`) as conversation history, so their attachments stay readable to the other participants. `AccountDeletionService` gains `ProfileRepository`, `PostRepository`, `AttachmentRepository` constructor dependencies.
- Proven by `RepositoryServiceAuditTest.accountDeletionRemovesCoverAndPostAttachmentFiles` (H2): cover and post-attachment files gone, post-attachment rows gone, user row gone, message + its attachment file still present.

### D-156 The archive job only archives ACTIVE events (ADOPTED)
- Problem (audit Worker 2, §4C): `ScheduledJobs.archivePastEvents` set every past event's post to `ARCHIVED`, including `REMOVED` and `HIDDEN_ADMIN` ones — overwriting their moderation state, so a later `RESTORE` could not know what the post was.
- Fix: only `ACTIVE` posts become `ARCHIVED`. The `EventDetail` is still archived and teams still close for every past event.
- Proven by `RepositoryServiceAuditTest.archivePastEventsKeepsModerationState` (H2): past `ACTIVE` → `ARCHIVED`; past `REMOVED` stays `REMOVED`.

### D-158 Full-stack integration build: additive extension of the audited backend (ADOPTED) — owner master prompt 2026-11-22
- The brief assumed a frontend-only tree; this workspace already holds the complete audited backend. The build therefore extends the existing backend additively (new Flyway V012, new endpoints) instead of rebuilding it, and removes the frontend's browser-local data layer (`frontendStore.ts`, `activityLog.tsx`) wherever a real API now exists. No committed migration (V001–V011) was modified; nothing was committed to git.

### D-159 Session authentication retained over the brief's JWT mandate (DEVIATION — recorded for owner ruling)
- The brief (§3/§6) mandates JWT access tokens + rotating refresh cookies. The existing platform uses Spring Session JDBC with HttpOnly cookies + CSRF, audited in Phase 3; the frontend, the admin TOTP flow and 287 backend tests are built on it, and a swap cannot be runtime-verified in this sandbox (Java→MySQL is blocked here). The session architecture is retained; the deviation and a JWT migration path are reported to the owner rather than silently imposed.

### D-160 Argon2id retained over the brief's BCrypt-12 mandate (DEVIATION — recorded for owner ruling)
- Argon2id (the audited Phase 1/3 choice) is at least as strong as BCrypt strength 12 for password storage; existing hashes cannot be re-encoded without plaintext. Retained.

### D-161 SRS password rule retained over the brief's special-character rule (DEVIATION — per owner's standing SRS precedence)
- The brief §18 requires a special character; SRS FR-AUTH-03 requires ≥8 chars with upper, lower and digit only, and the owner has previously ruled that the SRS takes precedence. The SRS rule stays in force for student passwords (admin passwords remain ≥12 per D-068 defaults).

### D-162 Existing API contract names retained over the brief's suggested names (ADOPTED)
- `/api/auth/register`, `/api/feed`, `/api/reactions`, `/api/bookmarks`, `/api/profiles/me` etc. stay; the brief's `/api/auth/signup`, `/api/posts`, `/api/saved`, `/api/users/me` names were not adopted, because the approved frontend is contract-tested against the existing names (brief §2 makes the existing frontend the source of truth).

### D-163 Admin events are posts with an extended event_details row (ADOPTED)
- V012 extends `event_details` (organizer, schedule, venue, registration, lifecycle `event_status` DRAFT/PUBLISHED/COMPLETED/CANCELLED, creating admin) instead of creating a parallel events table. Draft = post status DRAFT; cancelled = post status CANCELLED + event_status CANCELLED; the student lists already filter to ACTIVE posts, so drafts/cancelled events can never leak to students. Capacity and registration deadlines are enforced in `respondToEvent`; `event_responses` gains UNIQUE (user_id, post_id).

### D-164 Five reaction types persist; one row per user per target (ADOPTED — supersedes the D-140 LIKE-only validation)
- `PostService.react` now accepts LIKE, CELEBRATE, INSIGHTFUL, SUPPORT, CURIOUS; re-reacting replaces the stored type. A REACTION notification fires on a newly created post reaction.

### D-165 Threaded comments via comments.parent_id (ADOPTED)
- V012 adds `parent_id` (self-FK, ON DELETE CASCADE). Replies validate that the parent exists and belongs to the same post; REPLY notifications go to the parent author, COMMENT notifications to the post author.

### D-166 GitHub integration is read-only OAuth with encrypted tokens (ADOPTED)
- Scope `read:user`; the code exchange is server-side; tokens are stored AES-256-GCM encrypted (`github_connections`). Repository reads use the public GitHub API (the connection token when present); private repositories are refused. The OAuth callback is permitAll because GitHub's redirect carries no session — the HMAC-signed `state` binds the exchange to the student. UniLink-native discussions persist in `project_discussions` and are never posted to GitHub. Without `GITHUB_*` configuration the API reports `configured: false` and the UI shows that state instead of simulating a connection.

### D-167 TiDB portability of the Flyway chain (ADOPTED) — owner free-deployment instruction 2026-11-23
- The owner chose TiDB Cloud Starter as the production database and authorised fixing V001. Per PingCAP's compatibility documentation, TiDB does not support triggers or FULLTEXT, while `utf8mb4_0900_ai_ci` IS supported (TiDB ≥ 7.4; Cloud Starter qualifies) and foreign keys are supported. Changes: (1) V001 — all seven seed upserts rewritten from `ON DUPLICATE KEY UPDATE x=VALUES(x)` to `INSERT IGNORE` (the multi-table departments form was also rejected by MySQL 8.0.46 with error 1064; first-write-wins seed semantics preserved). (2) V004 — the two FULLTEXT indexes removed; `SearchService` now uses escaped LIKE matching on every database (supersedes the D-028 FULLTEXT plan; search results lose relevance ranking, keep substring matching and identical shapes/limits). (3) V006 — the two audit append-only triggers removed; append-only remains enforced by construction (AuditService only inserts; AuditLogRepository exposes no update/delete), losing only the DB-level tamper guard. The full chain V001–V013 was executed statement-by-statement against MySQL 8.0.46 after these edits: 60 tables, seeds correct. **Consequence: V001/V004/V006 checksums changed — an existing database (the owner's Windows dev DB) must run Flyway `repair` once (or boot once with `spring.flyway.validate-on-migrate=false`) before migrating again.** TiDB execution itself is verified only when the owner's cluster is created (no account exists yet); the SQL-level incompatibilities documented by PingCAP are all addressed.

### D-168 Database-backed file storage for ephemeral hosts (ADOPTED)
- New `StorageGateway` implementation `DatabaseStorageAdapter`, active when `app.storage.provider=db` (the local adapter stays the default via `matchIfMissing`). Files persist in the new `stored_files` table (V013, portable DDL) with the same key shape as the local adapter, so profile/cover/ID/post images survive restarts on hosts with an ephemeral filesystem (Render Free). Exercised by `DatabaseStorageAdapterTest` (H2 slice): byte-exact round-trip, exists/size/delete, namespace validation.

### D-169 Free-tier deployment topology: Cloudflare Pages + Render Free + TiDB (ADOPTED for the faculty demo)
- The frontend stays same-origin from the browser's perspective: Cloudflare Pages serves the SPA and proxies `/api/*` and `/ws/*` to the Render backend via `frontend/public/_redirects`, so session cookies (Lax + Secure under the prod profile) and CSRF work unchanged and no CORS configuration is introduced. WebSocket/STOMP through the Pages proxy is not guaranteed; REST covers the demo acceptance flows and messaging is out of product scope. The backend binds `${PORT:8080}` and the Dockerfile sizes the JVM for 512 MB (MaxRAMPercentage=65, metaspace cap, SerialGC). Render Free sleeps when idle — accepted by the owner for the demo. Uploads use D-168 database storage; OTP/reset codes use the logging providers (read from Render logs) until real SMTP/SMS credentials are configured.

### D-170 AuditLog.metadata mapped as JSON, not LOB (ADOPTED — production 409 root cause, 2026-11-25)
- `AuditLog.metadata` was mapped `@Lob String` while the Flyway column is JSON (V006). Hibernate binds a LOB as a binary-charset parameter; MySQL/TiDB reject the audit INSERT with error 3144 ("Cannot create a JSON value from a string with CHARACTER SET 'binary'"), which made every account deletion fail with 409 (the audit write is the first write of the delete transaction). Proven by the Render log lines at 05:38/05:40/06:07 UTC on 2026-11-25. Fix: `@JdbcTypeCode(SqlTypes.JSON)` on the field (no migration — the column stays JSON). The H2 tests never caught this because Hibernate-generated DDL makes the column LONGTEXT there. Regression guard: `AuditLogJsonMappingTest` (annotation test + write/read test). The full delete sequence was also replayed in raw SQL against MySQL 8.0.46 with production-shaped data and succeeded, exonerating the schema and cascades.

### D-171 SMTP timeout property names are lowercase (ADOPTED — production hang root cause, 2026-11-25)
- The `spring.mail.properties.mail.smtp` timeouts in `application.yml` were spelled in camelCase (`connectionTimeout`, `writeTimeout`). JavaMail property names are lowercase, so those entries were silently ignored — the production log showed "timeout -1" and a registration hung 120 s+ against smtp.gmail.com:587 on 2026-11-25 (the 2026-11-23 hang the timeouts were meant to fix). Fix: `connectiontimeout` / `timeout` / `writetimeout` (all lowercase, 10000 ms). Regression guard: `MailTimeoutPropertiesTest` boots the real mail sender and asserts the exact lowercase keys. Note: this bounds the failure to ~10 s; it does not restore delivery — the TCP connection to smtp.gmail.com:587 itself times out from Render (see the remaining-issues note in the 2026-11-25 deletion-audit report).

### D-172 Tesseract OCR installed in the backend runtime image (ADOPTED, 2026-11-25)
- `OcrJobService` shells out to the `tesseract` binary for ID verification, but the runtime image (`eclipse-temurin:21-jre`) never installed it — every verification in production failed with "Cannot run program tesseract: No such file or directory" (production log 2026-11-25; ocrStatus FAILED for every application). Fix: `apt-get install -y --no-install-recommends tesseract-ocr` in the runtime stage of `backend/Dockerfile`, before `USER unilink`. No code change; the image grows by the tesseract package set.

### D-173 Registration journey is OCR → email OTP code → active; phone OTP leaves the journey (ADOPTED — owner brief 2026-11-25)
- The new registration journey is: register → automated ID-card OCR (Step A progress screen) → 6-digit email OTP code (Step B) → account active (Step C, "Go to Log in →"). The phone OTP step is no longer part of registration: `AuthService.register` no longer sends a phone OTP and no longer seeds the phone-resend cooldown. The phone OTP endpoints (`/auth/verify-phone`, `/auth/otp/resend`), the `/otp` page and `OtpService.sendPhoneOtp`/`verifyPhoneOtp` stay intact and usable (legacy flow, and the phone-verification regression tests keep passing via resend). `OtpResendTest.registerSeedsResendCooldown` is rewritten to the new behaviour (register sends no phone OTP; a resend after registration succeeds). Activation no longer requires admin approval for clean sign-ups (brief §3/§6); uncertain cases go to the admin Verification Queue as MANUAL_REVIEW.

### D-174 Email OTP is a 6-digit code for new registrations; the link flow is retained (ADOPTED — owner brief 2026-11-25)
- New registrations verify email with a 6-digit code, not the 24 h link: new `email_otps` table (V014) mirroring `phone_otps` (SHA-256 hash, 5 attempts with the atomic REQUIRES_NEW increment, single-use, 10-minute expiry — 10 minutes rather than the phone code's 5 because email delivery is slower than SMS). The code is sent automatically when OCR reaches AUTO_VERIFIED and never otherwise; resend is `POST /api/auth/email-otp/resend` with the same caps as the phone resend (60 s cooldown, 5/hour per user, 5/hour per email, 10/hour per IP, atomically acquired). The link flow (`sendEmailVerificationLink`, `/auth/verify-email`, `/auth/email-verification/resend`, `EmailVerify.tsx`) is retained unchanged for links already in flight and for its existing tests; registration no longer sends a link. Codes are never logged and never appear in API responses; the fake provider in tests records them in `TestProviders.EMAIL_OTPS`.

### D-175 Registration-scoped token for verification-status and email-OTP endpoints (ADOPTED — owner brief 2026-11-25 §7)
- Registration returns an opaque 64-hex registration token; only its SHA-256 hash is stored (`verification_requests.verification_token_hash`, V014, 24 h expiry). `GET /api/auth/registration/status`, `POST /api/auth/email-otp/resend` and `POST /api/auth/email-otp/verify` accept only that token — no userId, no email, no name in the request or the response — so the endpoints cannot be used to enumerate accounts. An unknown or expired token answers 404 with the same generic body as a missing resource. The token is stored in sessionStorage by the frontend (`unilink:verificationToken`) and is re-issued by resubmit. The session-based `GET /api/auth/verification/status` is unchanged for the logged-in Pending Review page.

### D-176 Activation uses the legal two-step state transition (ADOPTED — owner brief 2026-11-25 §6)
- `AccountState` allows REGISTERED → PENDING_REVIEW and PENDING_REVIEW → VERIFIED, but not REGISTERED → VERIFIED directly, so the state machine is not changed: a successful email OTP after AUTO_VERIFIED moves the account REGISTERED → PENDING_REVIEW → VERIFIED inside one transaction and publishes the same `StudentVerified` event the admin approval path publishes (in-app notice + decision email). A MANUAL_REVIEW account is already PENDING_REVIEW; its email OTP success marks the email verified and leaves the account PENDING_REVIEW for the admin decision. A VERIFICATION_FAILED account goes REGISTERED → PENDING_REVIEW → REJECTED (also two legal steps), which re-uses the existing REJECTED-only resubmit flow (3 per 24 h) as the safe retry path; resubmit re-runs OCR and issues a fresh registration token.

### D-177 Single student ID card image in the signup form (ADOPTED — owner brief 2026-11-25 §1; supersedes A1)
- The brief's 10-field list has one "Student ID Card Image". The two-field front/back UI from A1 (2026-11-20) is removed: the frontend never submitted the back side (SignUp only appended `idCard`, so the back upload was collected, validated and silently dropped — a real UX defect), and OCR reads the single image. The backend still accepts the optional `idCardBack` part for old clients and still stores it when sent. The profile-photo upload is removed from signup entirely (D-178).

### D-178 Profile photo is no longer collected at registration (ADOPTED — owner brief 2026-11-25 §1)
- `AuthService.register` no longer requires a profile photo (`PROFILE_PHOTO_REQUIRED` removed); `users.profile_photo_key` and `verification_requests.profile_photo_key` are already NULLABLE, so no migration is needed. Settings → Edit Profile keeps its existing photo upload unchanged, and sending a photo at registration still works (the part is still accepted and stored). Existing tests that send a photo keep passing.

### D-179 Batch is required in the signup form; the backend stays lenient (ADOPTED — owner brief 2026-11-25 §1/§2)
- The brief lists Batch/Session among the required registration fields and its validation message ("Please select your batch/session."), so the frontend zod schema requires `batchId`. The backend `RegisterForm.batchId` stays optional (null accepted) so old clients and API callers keep working.

### D-180 OCR matching and decision rules (ADOPTED — owner brief 2026-11-25 §3)
- New `IdCardMatcher` (pure logic, unit-tested): student ID is normalised per OCR line (Bengali digits → Latin, spaces/hyphens stripped) and must match exactly on one line — no partial or cross-line match. Name similarity is token-based Levenshtein (prefixes Md./Mohammad/Mst. ignored), threshold ≥ 0.75. University matches on a distinctive token (stopwords excluded, as in the existing scorer) or a controlled abbreviation map (DU, MU, BUET, RU, CU, JU, SUST, KUET, RUET, CUET, MIST, NSU, IUB, AIUB, BRAC, EWU, DIU, ULAB, UAP). Department matches on its acronym or expanded name from a controlled alias map (CSE/EEE/ECE/CE/ME/BBA/MBA/LLB and the seeded catalog names); a department is NEUTRAL when the card carries no department evidence and MISMATCH only when a different known department is clearly present. Address is supporting evidence only: a city/area token conflict (both sides name different places from a small gazetteer) downgrades to MANUAL_REVIEW, absence is neutral. Decisions: AUTO_VERIFIED requires ID matched + name ≥ 0.75 + university matched + department not MISMATCH + OCR confidence ≥ 50 when available; VERIFICATION_FAILED requires substantial OCR text (≥ 40 chars) and at least two hard failures among ID / name (< 0.50) / university (a different known university present); everything else is MANUAL_REVIEW. Failure reasons use the brief's field-specific messages. Composite `ocr_match_score` = 0.4·ID + 0.3·name + 0.2·university + 0.1·department (neutral department = 0.05).

### D-181 OCR preprocessing and confidence (ADOPTED — owner brief 2026-11-25 §3)
- `TesseractOcrAdapter` now preprocesses with pure-Java ImageIO (no new dependency — Maven is offline): grayscale, contrast stretch, 2× upscale for small images, PNG re-encode; unreadable formats (e.g. WebP, which ImageIO cannot decode) fall back to the original bytes, which Tesseract reads directly. OCR runs with TSV output so the mean word confidence is a real signal (blank/short text or mean confidence < 35 → MANUAL_REVIEW as "unclear image"). If the configured languages (`ben+eng`) fail, the adapter retries once with `eng` alone, so a missing Bengali data file degrades instead of failing every card. The Dockerfile installs `tesseract-ocr-ben` and `tesseract-ocr-eng` explicitly (the base `tesseract-ocr` package alone does not guarantee language data with `--no-install-recommends`). Deskewing is not implemented (no library available offline); `--psm 6` plus the preprocessing is the documented limit. OCR failure after the existing 3-attempt retry now ends in MANUAL_REVIEW, never in a stuck VERIFYING state.

### D-182 Password checklist shows the real backend rule (ADOPTED — owner brief 2026-11-25 §2C; confirms D-161)
- The backend rule (`AuthService.checkPasswordRule`, FR-AUTH-03) is ≥ 8 characters + one uppercase + one lowercase + one digit — no special character (D-161, SRS precedence). The signup checklist shows exactly those four rules with live red ✗ / green ✓ indicators, and the zod schema mirrors them. No special-character requirement is shown anywhere.

### D-183 MANUAL_REVIEW and VERIFICATION_FAILED routing (ADOPTED — owner brief 2026-11-25 §3/§4)
- MANUAL_REVIEW sets the request status to PENDING_REVIEW (so it appears in the existing admin Verification Queue) and the account to PENDING_REVIEW; the verification flow shows an "under manual review" screen with a link to Pending Review. VERIFICATION_FAILED sets the request to REJECTED with the field-specific reason and the account to REJECTED; the flow shows the reason and directs the student to log in and resubmit a clearer image from Pending Review. No OTP is ever sent for FAILED or MANUAL_REVIEW. The 30-second countdown on the progress screen is visual only; the frontend polls the real status every 2 s and recovers it after a refresh from the registration token.

### D-184 GitHub→Render auto-deploy root cause and fix (ADOPTED — owner instruction 2026-11-25)
- Root cause (proven 2026-11-25 ~14:50 +06): the repository `Mubin-0xC7DFC8C3C4/unilink` had no Render webhook and the Render GitHub App was not installed, so GitHub could not notify Render of pushes. Render's own configuration was correct throughout (autoDeploy=yes, trigger=commit, branch master, correct repo URL); no push ever auto-triggered a deploy — every deploy in history was manual or service_updated. The service's GitHub connection had been via a different credential (smrifat1411) without GitHub App access to this repo.
- Fix (2026-11-25 ~14:53 +06): the owner reconnected the service in the Render dashboard (Settings → Build → Source) using the Mubin-0xC7DFC8C3C4 credential and installed the Render GitHub App with access to all repositories. The dashboard shows Source `Mubin-0xC7DFC8C3C4/unilink`, branch master, Auto-Deploy: On Commit. The reconnection itself created no deploy; verification is by a test push (this entry).
- Correction: the repo owner is `Mubin-0xC7DFC8C3C4` (the shorter `Mubin-0xC7DFC8C4` spelling in earlier notes does not exist) and the repo is public, not private.
