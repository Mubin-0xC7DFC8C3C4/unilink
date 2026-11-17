# UniLink Test Strategy

Read with `SRS_TRACEABILITY_TEMPLATE.md` (every test is tied to an SRS ID there) and `REQUIREMENTS_DECISION_LOG.md` (ambiguity defaults). The SRS defines 31 acceptance cases (TC-01 to TC-31); this strategy turns them into automated tests and adds what the SRS leaves out.

## 1. Rules that cannot be bent
1. **A test is "passed" only if it was executed** and the command, date and result are recorded in the traceability matrix. Never write "passes" from reading code.
2. **No mocks of the thing under test.** Mock external providers (SMS, SMTP, OCR, S3) with fakes; never mock the database, Spring Security or the authorisation policy in integration, security or e2e tests.
3. **Real database.** Integration tests run against MySQL 8 in Testcontainers with the real Flyway migrations. H2 is not used (dialect and FULLTEXT differences hide defects).
4. **Verify the database, not just the response.** Admin and moderation tests assert the row changed (and the audit row exists in the same transaction), per the brief.
5. **Every feature gets a negative test** (wrong role, wrong scope, wrong owner, invalid input, duplicate, provider failure).
6. **Performance targets are claims until measured** (D-050). A failing target is reported as failing.
7. Flaky tests are fixed or removed with a recorded reason, never retried until green.

## 2. Test pyramid and tools

| Level | Tools | Scope | Runs |
|---|---|---|---|
| Unit | JUnit 5, Mockito, AssertJ | Services, validators, state machines, pattern classes, policies, mappers | every commit |
| Slice | `@DataJpaTest` + Testcontainers, `@WebMvcTest` | Repositories and queries (scope filters, FULLTEXT, keyset paging), controller validation and error mapping | every commit |
| Integration | `@SpringBootTest`, MockMvc or REST Assured, Testcontainers MySQL, fake providers, `WebSocketStompClient` | End-to-end through security filter chains with a real session; transactions; WebSocket | every PR |
| Frontend unit and component | Vitest, React Testing Library, MSW (unit only) | Components, form validation, error and empty states, theme switching | every commit |
| End-to-end | Playwright (chromium, firefox, webkit) against a running stack with fake providers | Full workflows in a browser, both themes, 360 px and 1280 px viewports | every PR (smoke) and nightly (full) |
| Accessibility | axe-core via Playwright | Student and admin screens in light and dark | every PR |
| Security | JUnit security suite, OWASP ZAP baseline against the running stack, dependency scanning (OWASP Dependency-Check, `npm audit`) | Section 5 | every PR and nightly |
| Performance | k6 (HTTP and WebSocket), MySQL `EXPLAIN` checks | Section 6 | on demand and before release |
| Architecture | ArchUnit | Layering: controllers do not touch repositories or entities; entities not in DTOs | every commit |

Coverage gate: JaCoCo line coverage at least 80% on `service`, `security`, `moderation`, `identity` packages, with branch coverage reported. Coverage is a floor, not evidence of quality; the traceability matrix is the completeness measure.

## 3. Test data and environment
- Builders (`TestDataFactory`) for universities, departments (with catalog), batches, students in each state, admin accounts per role, posts per visibility. Two universities, two catalog departments each, and one extra university with the same catalog department to exercise the Hub (TC-09, TC-10).
- Fake providers: `FakeSmsProvider` and `FakeEmailProvider` record messages in memory so tests can read the OTP; `FakeOcrProvider` returns configurable text and score; `InMemoryStorageGateway`. Fakes exist only under the `test` and `e2e` profiles and the build fails if they are on the `prod` classpath.
- Controlled `Clock` bean so deadlines, OTP expiry, lockouts, 24 h and 30 day windows and the 48 h overdue rule are tested without sleeping.
- Each integration test class starts from a clean schema (Flyway clean in test profile only) or uses transactional rollback; e2e seeds through a test-only seed endpoint disabled outside the `e2e` profile.
- Seed generator for the performance dataset: 100,000 posts and 20,000 students including Bangla text.

## 4. Functional coverage by module (mapped to the SRS cases)

| Module | Must-have automated tests |
|---|---|
| Auth and verification (TC-01 to TC-07) | Happy path through OTP, email link, Pending Review, admin approval, badge. Duplicate email and duplicate (university, student id). Wrong OTP 5 times locks that OTP and a fresh OTP works; OTP expiry at 5 min by clock; resend cooldown. OCR mismatch gives low score and reviewer rejects with reason; resubmission allowed 3 times per rolling 24 h, 4th refused. Five failed logins lock 15 min and unlock by clock; failure messages identical for unknown email and wrong password. Password reset invalidates old password and all sessions. Unverified user blocked from post, comment, react, message, team join, bookmark (each endpoint). |
| Profile (TC-08, TC-17) | Project CRUD with links; owner-only edits (IDOR); phone and address hidden in other users' DTOs; identity change keeps old values until approval; skill and department search. |
| Community (TC-09, TC-10, TC-11) | Visibility matrix test: for each of the 4 visibility values and each of 4 viewer positions (same dept, same university other dept, same catalog other university, unrelated) assert exactly which feeds show the post (D-021). |
| Events (TC-11, TC-12, TC-13) | National hackathon appears in National feed and sidebar ordered by deadline; past deadline rejected on create and edit; job archives at deadline and the feed excludes it even before the job runs; deadline extension reactivates; teams close with the event. |
| Teams (TC-14) | Request, join, accept: member count, team room membership and notification in one transaction; rollback test when room insert fails; size limit closes team; one team per user per event; only creator decides (IDOR). |
| Messaging (TC-15, TC-16) | Persist then deliver within 1 s over real STOMP; message not delivered if the transaction rolls back; read status; block prevents delivery and new conversation; mute suppresses notifications; room search by name; unauthorised SUBSCRIBE to another room is rejected. |
| Reporting (TC-18, TC-19) | Four reports no hide, fifth distinct verified reporter hides, notifies owner and enqueues with high priority; duplicate reporter and owner reports not counted; concurrent fifth and sixth reports hide exactly once; appeal then admin restore returns visibility and writes audit; 3 hides in 30 days flags user. |
| Admin and moderator (TC-23 to TC-31) | Correct password and wrong TOTP refused and attempt logged; approve writes status, badge, notification and audit atomically; reject without reason blocked; moderator of University A cannot read, search, count or mutate University B data (queue, users, content, announcements, audit) and gets 404; student session on `/api/admin/**` gets 401 or 403 and the admin SPA bundle is never required for that decision; suspend revokes sessions and blocks login; master data additions appear in sign-up and filters; any attempt to update or delete an audit row through API, JPA or SQL as the runtime DB user fails; overdue alert after 48 h by clock, exactly once. |
| Notifications, bookmarks, calendar | Preference opt-out respected except security emails; bookmark idempotency; calendar shows only in-scope events. |

## 5. Security test suite (executed, not asserted)

Run as both student and admin roles.
- **AuthZ matrix test**: a parameterised test enumerates every mapped route from Spring's handler mapping and, for each role (visitor, unverified, verified, moderator A, moderator B, platform admin), asserts the expected status. Any new route without a matrix entry fails the build.
- **IDOR**: for every resource with an id in the URL, user B requests user A's resource (profile edit, project, bookmark, appeal, team request, message room, attachment, ID image) and gets 403 or 404 with no data.
- **Privilege escalation**: student calls admin endpoints with forged role headers, tampered cookies, and an admin-looking payload; mass-assignment tests send `role`, `status`, `verified` fields in profile updates and assert they are ignored.
- **Moderator isolation**: Section 4, plus ID image endpoint and report snapshots.
- **SQL injection**: payloads in every search and filter parameter and in sort fields (sort fields are allow-listed); assert no SQL error and no data change.
- **XSS**: script and event-handler payloads in post, comment, profile, project links, event fields, chat messages and filenames; assert they are stored and returned as inert text and that the CSP header is present; Playwright checks no script executes. `javascript:` and `data:` URLs rejected in link fields.
- **CSRF**: state-changing request without or with a wrong token fails; cookie flags (HttpOnly, Secure, SameSite) asserted.
- **Brute force and abuse**: login lockout, OTP attempt cap, OTP send rate by phone and IP, password reset rate, admin login throttling, report and message flood limits.
- **File upload**: wrong extension with different content, oversize, polyglot image/script, zip bomb style image, SVG, path traversal filename, double extension, EXIF removal verified, stored object not publicly reachable.
- **Private ID cards**: unauthenticated, other student, other-university moderator and expired signed URL all denied; URL not present in any list response, log line or error.
- **WebSocket**: CONNECT without session fails; SUBSCRIBE to another room, another user's queue or admin topics fails; SEND to a room after being removed or blocked fails; session close on logout and suspension.
- **Session**: invalidation on logout and password change (second browser session dies); fixation (new session id on login); idle timeouts 2 h student and 30 min admin by clock.
- **Secrets and logging**: a test scans captured log output of the integration suite for OTP values, passwords, tokens and storage URLs and fails if found.
- **Headers**: HSTS, CSP, X-Content-Type-Options, frame protection, Referrer-Policy asserted on responses.
- **Dependencies**: OWASP Dependency-Check and `npm audit` reports reviewed; critical findings block release.
- **ZAP baseline** against the running stack, results recorded; known false positives documented.

## 6. Performance and reliability tests

Targets and the way each is measured (D-050). Record hardware, dataset size, k6 script version and raw results with every run.
- **NFR-PER-01 / TC-21**: k6 scenario of 1,000 virtual users (5 to 15 s think time) browsing feeds, event pages, profiles, search. Pass = server p95 at most 500 ms for reads and no error rate above 1%; browser p95 LCP at most 3 s measured with Lighthouse CI on a throttled mobile profile.
- **NFR-PER-02**: 100,000 posts and 20,000 users seeded; search by keyword, by Bangla keyword, by sector and by skill; p95 at most 2 s; `EXPLAIN` saved for each query to show index use. If it fails, implement a new `SearchStrategy` and re-run; do not claim the target.
- **NFR-PER-03**: 1,000 simultaneous WebSocket sessions, steady message traffic; p95 send-to-receive latency at most 1 s.
- **NFR-PER-04**: the above two run together; record CPU, memory, DB connections, GC.
- **Query checks**: integration tests count SQL statements for feed, comments and message list endpoints to catch N+1.
- **Concurrency**: simultaneous accept of the last team seat, simultaneous fifth and sixth reports, duplicate message send retries (idempotency key), double approve of one verification.
- **Resilience**: with a Toxiproxy or fake failing provider: SMS down falls back to email OTP (NFR-FLT-02); OCR down still queues with a note (NFR-FLT-03); storage down returns a clear error and leaves no half-created account; job failure retries (NFR-REL-03). Kill the application mid-transaction and verify no partial data (NFR-REL-01).
- **Backup and restore**: restore the latest backup into a clean database and run the smoke suite; record time taken (NFR-REL-02). Availability (NFR-AVL-01) needs a month of monitoring data; before then the status is "Not tested".

## 7. End-to-end flows (Playwright)

1. Registration, OTP (read from fake provider), email link, Pending Review screen. 2. Admin 2FA login, review with ID image and OCR score, approve; student refreshes and sees Verified. 3. Profile editing with project links. 4. Level switching and visibility; unverified user sees only National read-only. 5. Event publish, sidebar order, comment with mention, bookmark, calendar. 6. Team: request, join, accept, team chat message. 7. Two browsers exchanging real-time private messages; block and mute. 8. Report from five accounts, auto-hide, owner appeal, admin restore. 9. Moderator isolation in the UI and by direct URL. 10. Master data change reflected in sign-up. 11. Both themes at 360 and 1280 px; keyboard-only run of sign-up and verification review. 12. Safari engine (webkit) and Firefox smoke for TC-22.

Rules: no `waitForTimeout` sleeps, use web-first assertions; traces and screenshots kept for failures; tests independent and re-runnable.

## 8. Acceptance and usability
- **UAT-USA-01**: five or more students who were not on the team sign up and reach a feed without help; record success and time.
- **UAT-USA-06**: three reviewers process ten verification requests each; record time per request; target under one minute (NFR-USA-06).
- Visual review of every screen against `DESIGN.md` in both themes; run `npx @google/design.md lint DESIGN.md` in CI.

## 9. Reporting
After each phase and at release, produce `docs/testing/RESULTS.md` containing: commands run, date, commit hash, counts of passed, failed, skipped tests, coverage, security tool output summaries, performance numbers with hardware, and the updated traceability matrix. Unexecuted items are listed as "Not tested". Defects found are listed with severity; release is blocked by any open critical or high defect.

## 10. Entry and exit criteria per phase
- **Entry**: requirements for the phase reviewed against the decision log; test plan rows exist in the matrix.
- **Exit**: all mapped tests written and executed; no failing tests; security review of the phase done; docs updated; remaining risks reported.
