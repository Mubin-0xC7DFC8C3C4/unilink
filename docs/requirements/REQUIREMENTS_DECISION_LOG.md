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
