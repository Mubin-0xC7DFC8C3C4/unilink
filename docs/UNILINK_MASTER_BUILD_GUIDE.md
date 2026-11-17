# UniLink Master Build Guide

Primary instruction manual for the implementation agent (Muse AI). This guide tells you **what to build, in what order, under which rules, and how to prove it works**. It does not contain the application.

---

## 0. Read these first, in this order

1. `REQUIREMENTS_DECISION_LOG.md`: every SRS conflict, gap and default. Where this guide and the log differ, the log wins. Entries marked PROPOSED are the defaults to build; OPEN items use the stated default until the owner answers.
2. `DESIGN.md`: the enforceable design system (tokens for light and dark, component rules). Run `npx @google/design.md lint DESIGN.md`; generate CSS variables from it (`npx @google/design.md export --format tailwind DESIGN.md` or a small script) and never type colours by hand.
3. `ARCHITECTURE_OVERVIEW.md`: modules, packages, tables, security architecture, patterns, workflows.
4. `TEST_STRATEGY.md`: how every requirement is proved.
5. `SRS_TRACEABILITY_TEMPLATE.md`: the living matrix. Copy it to `docs/traceability.md` and keep it current.
6. The SRS itself (`UniLink_SRS (1)(1).docx`) for exact requirement wording. It outranks this guide on **what** the product does; the decision log outranks it only where the SRS is inconsistent or silent.

Source priority: SRS requirements, then the decision log, then this guide, then `DESIGN.md` for visuals (visual reference: `pic.docx`).

## 1. Non-negotiable working rules

1. **Do not invent features** that contradict the SRS or the out-of-scope list: no native apps, video or voice calls, payments, ticketing or in-app event registration, job portal, recruiter, faculty, staff or alumni accounts, ads, ERP integration, biometric verification. Contribution points are not built (D-029b).
2. **Do not silently change requirements.** New ambiguity: add an entry to the decision log (next free ID), implement the safest reading, continue.
3. **Real backend for every feature.** Each screen talks to the real API and persists in MySQL. No mock data in shipped code, no `localStorage` as a source of truth, no placeholder buttons. If a feature cannot be finished, hide nothing behind a fake: mark it `Partially completed` or `Not implemented` in the matrix.
4. **External services need credentials.** Build real provider adapters (SMTP, SMS gateway, OCR, S3). Without credentials the adapter must fail clearly and the app must use the documented fallback; never pretend an integration works. Test fakes live only in `test` and `e2e` profiles.
5. **Never report a test as passed unless you ran it.** Record command, date, commit and result. Never claim production readiness or any performance target without measured evidence.
6. **Backend is the authority.** Every protected operation checks: authentication, role, account state, university scope, ownership. Hiding buttons is UX, not security.
7. **No secrets in the repository.** Environment variables only; commit `.env.example`.
8. **Work in the phases in section 12.** Do not skip phases or mark incomplete work complete. Inspect before you build: if code already exists, read it, keep what works, reuse suitable components.

## 2. Product scope (what "complete" means)

UniLink is a verified student network for Bangladesh universities (SRS 1.0 to 2.7). Version 1.0 implements every in-scope FR and NFR (116 requirements, listed with planned mapping in the matrix) and the 31 acceptance cases TC-01 to TC-31:

- Student sign-up with ID card, phone OTP, email link, OCR pre-check, admin or moderator approval, badge.
- Profiles with projects, education, achievements, skills, links, follow or connect, activity feed, privacy of phone and address.
- Four community levels: Department, University, National Department Hub, National Feed, with the visibility model of D-020 and D-021.
- Events and posts, comments with @mentions, reactions, bookmarks, calendar, notifications, sector tags, search.
- Teams for competitions with join requests, team chat.
- Real-time chat: department and university rooms, private chat, temporary groups, read status, block, mute, attachments.
- Community reporting, auto-hide, appeals, strikes.
- A separate **Admin Portal** and University Moderator role (section 6).

## 3. Technology (decided)

| Layer | Choice | Notes |
|---|---|---|
| Frontend | React 18+, TypeScript (strict), Vite | two entries: student `index.html`, admin `admin.html`; TanStack Query, React Router, React Hook Form + Zod, Vitest, Testing Library, Playwright; CSS variables from `DESIGN.md`, no Bootstrap (D-002) |
| Backend | Java 17+ (21 allowed), Spring Boot 3.x (latest stable 3.x at start), Maven | Web, Security, Data JPA, Validation, WebSocket (STOMP + SockJS fallback), Scheduling, Async, Actuator, Spring Session JDBC, Spring Retry, springdoc-openapi |
| Database | MySQL 8, Flyway | `utf8mb4`; migrations are the only way the schema changes |
| Storage | `StorageGateway` interface: S3-compatible adapter and private local-volume adapter | private by default |
| Email, SMS, OCR | `EmailProvider` (SMTP), `SmsProvider` (gateway adapter; pick a Bangladesh-capable provider with the owner), `OcrProvider` (e.g. Tesseract adapter with `ben+eng` language data, replaceable) | each behind an interface with a fake for tests |
| Real-time | Spring WebSocket + STOMP, in-memory broker behind `RealtimeBroadcaster` | D-051 |
| Web server | Nginx over HTTPS, single origin | D-013 |
| Not used | Redis, Kafka, RabbitMQ, Elasticsearch, microservices, Quarkus, Copilot SDK, Laravel | D-001, D-003, D-004, D-005. Add only with a decision-log entry and a measured reason |

Verify current stable versions when you start and pin them. Do not copy version numbers from memory into the build.

## 4. Roles and permission matrix

Account kinds: students (`users`) and staff (`admin_accounts`, D-030). Roles: Visitor, Unverified Student, Verified Student, University Moderator, Platform Admin.

| Capability | Visitor | Unverified | Verified | Moderator (own univ.) | Platform Admin |
|---|---|---|---|---|---|
| Landing, sign-up, login | Yes | n/a | n/a | n/a | n/a |
| Verification screens, resubmit, own profile edit | No | Yes | Yes | No | No |
| Read National feed (NATIONAL items) | No | Read only | Yes | No (admin UI only) | No |
| Read Department, University, Hub feeds | No | No | Yes, within scope | No | No |
| Post, comment, react, bookmark, report | No | No | Yes | No | No |
| Message, join or create teams | No | No | Yes | No | No |
| Admin Portal login (own login, 2FA) | No | No | No | Yes | Yes |
| Verification queue approve or reject | No | No | No | Own university | All |
| View or search users | No | No | No | Own university | All |
| Suspend, ban, reactivate, revoke, force reset | No | No | No | Recommend suspension only | Yes (and confirms recommendations) |
| Moderate content, appeals | No | No | No | Own university | All |
| Announcements | No | No | No | Own university | Global or any university |
| Master data, thresholds, moderators, analytics scope | No | No | No | No (read-only analytics for own university) | Yes |
| Audit log read | No | No | No | Own university actions | All |
| Audit log write or edit or delete | Never | Never | Never | Never | Never (append-only by the system) |

Enforcement: `AccessPolicy` is the single decision point; services call it; repositories apply university scope in the query; out-of-scope resources return 404; admin endpoints live under `/api/admin/**` on their own filter chain requiring `MFA_VERIFIED`. The authorisation matrix is tested automatically (TEST_STRATEGY section 5).

## 5. Student application: behaviour rules

- Default landing after login: Department tab; one-tap level switching (FR-COM-07).
- Verification flow and states exactly as D-010, D-011, D-012, D-015, D-017. The OTP, email and ID-card flows are rate-limited and audited as security events.
- Feeds use keyset pagination (cursor), sector filter and Specification-based visibility (D-021). Archive of past events is a separate tab.
- Time zone: store UTC, show Asia/Dhaka. Bangla text input and display must work end to end (UTF-8, `utf8mb4`, Bengali font fallback in `DESIGN.md`).
- Every screen needs loading, empty, error, confirmation and offline/failed-request states. Confirm destructive actions. Forms keep user input on error.
- Real-time chat: persist first, then broadcast after commit; offline users get notifications; reconnect with backoff and resync from the last message id.

## 6. Admin Portal (critical, separate, real)

**Separation.** Separate bundle (`admin.html` at `/admin`), separate router, separate layout (left rail, dense tables, split review pane), separate login page, separate API namespace `/api/admin/**`, separate security filter chain and principal type. It shares only the design tokens and generic primitives. It never loads student feature code.

**Accounts.** `admin_accounts` rows are created only by a Platform Admin (or an initial bootstrap command for the first Platform Admin that reads credentials from environment variables, runs once, and is documented). There is no public registration endpoint. Passwords at least 12 characters. 2FA is mandatory: TOTP enrollment at first login, recovery codes, email OTP fallback (D-031). Optional IP allow-list by configuration. Lockout and throttling are separate from student login. Idle timeout 30 minutes.

**Modules to implement (each with real backend and audit):**
1. Dashboard: counts of users, pending verifications, reports awaiting review, active events, new users per day (FR-ADM-02). Only real queries; moderators see their university only.
2. Verification queue and review (FR-ADM-03, FR-AUTH-06): list with filters, review pane showing entered data, ID image (authorised stream), OCR text and match score, previous attempts, university email-domain hint, approve, reject with mandatory reason. Keyboard shortcuts. Target under one minute per request (D-039a).
3. User management (FR-ADM-04): search, view, suspend (with optional end date), ban, reactivate, revoke verification, force password reset, each with a reason and session revocation; moderator can only recommend suspension (FR-MOD-05).
4. Moderation queue (FR-ADM-05/06): reported and auto-hidden posts, comments, messages (snapshot only, D-033), profiles; ordered by report count then age; hide, remove, restore, request edit, warn.
5. Appeals (FR-ADM-08), event moderation (FR-ADM-07), announcements (FR-ADM-09, D-039).
6. Master data (FR-ADM-10): universities (with email domains), department catalog, departments, batches, sector tags. Deactivate rather than delete referenced rows.
7. Moderator management (FR-ADM-12): create, edit, deactivate, assign to one university, reset credentials.
8. Analytics (FR-ADM-13): user growth, verification turnaround, events per sector, active universities; SQL aggregates, no fabricated numbers.
9. Overdue alerts (FR-ADM-14, D-038).
10. Audit log viewer (FR-ADM-11): read-only with filters and export; D-037 immutability.

**Audit.** Every admin mutation and every admin login outcome is written through `AuditService` in the same transaction as the change, with actor, action, target type and id, reason, metadata (no personal data beyond ids), IP (from the trusted proxy header configuration) and timestamp, chained by hash. Tests verify the database row and the audit row together.

## 7. Database engineering

1. Design first: the table list and constraints in `ARCHITECTURE_OVERVIEW.md` section 5 are the baseline (they extend the SRS ER, see D-062). Produce `docs/db/ERD.md` (Mermaid) and `docs/db/schema.md` (every table, columns, keys, indexes, deletion behaviour, retention).
2. Flyway migrations in `backend/src/main/resources/db/migration/`, versioned `V001__master_data.sql`, `V002__identity.sql`, and so on, one per domain, never edited after merge; fixes are new versions. Separate DB users: migration user (DDL) and runtime user (DML; `INSERT, SELECT` only on `audit_logs`). Add the immutability triggers in a migration.
3. Use transactions for: registration, verification decision, auto-hide transition, team accept, account deletion, announcements with notifications fan-out marker.
4. Use optimistic locking (`@Version`) on `verification_requests`, `teams`, `posts` moderation fields to prevent double decisions.
5. Retention (D-052, D-053, NFR-REL-04): purge unconfirmed registrations after 7 days with images; ID images per D-053; audit log kept at least 1 year; notifications older than 90 days archived by job (configurable).
6. Provide `docs/db/seed-dev.sql` or a dev-only seeder for master data (universities, catalog, departments, sectors listed in FR-SEC-01) and demo accounts under the `dev` profile only. Seed data never ships in the `prod` profile.

## 8. Backend engineering rules

- Package by feature (see architecture section 3). Controllers thin; services own business rules and transactions; repositories own queries; DTOs (Java records) at every API boundary; entities never returned.
- Constructor injection, `private final` fields, no field injection. SOLID, DRY, small methods, meaningful names.
- Validation with Jakarta Bean Validation plus domain validators. Reject, never silently coerce.
- Errors: `@RestControllerAdvice` returns one shape: `{ "timestamp", "status", "code", "message", "path", "traceId", "fieldErrors": [ { "field", "message" } ] }`. Messages are user-friendly and never reveal internals (NFR-USA-05). Error codes are stable strings used by the frontend.
- Logging: SLF4J parameterised, JSON in `prod`, request id in MDC. Never log passwords, OTPs, tokens, ID images or their URLs, or full message bodies.
- Configuration: `application.yml` plus `application-dev|test|prod.yml`; all secrets and endpoints from environment variables bound by `@ConfigurationProperties` with validation; fail fast on missing prod values.
- Jobs: `@Scheduled` for event archiving (every minute), overdue verification alerts (15 min), unconfirmed-registration purge (daily), ID image retention purge (daily), audit hash verification (daily), notification digest. Each job is idempotent, uses retry with backoff, logs outcome, and exposes last-run status to admins.
- Asynchronous work (OCR, email, SMS) uses a bounded executor with an outbox-style table or `@TransactionalEventListener(AFTER_COMMIT)` plus retry, so a provider outage cannot lose or duplicate work (use idempotency keys).
- Public API documentation with springdoc-openapi at `/v3/api-docs` and Swagger UI in `dev` only; generate the TypeScript client from it.
- Pagination: all collections paginated (`limit` capped at 50, cursor for feeds and messages).
- Search: `SearchStrategy` over MySQL FULLTEXT with allow-listed sort fields (D-028).

## 9. Security engineering (design and test every item)

Password hashing Argon2id (D-014); server sessions (D-013); CSRF on; CORS empty (same origin); security headers (HSTS, CSP `default-src 'self'` with no inline scripts, nosniff, Referrer-Policy, Permissions-Policy, frame denial); login throttling and lockout (FR-AUTH-10); OTP expiry, attempts, cooldown and caps (FR-AUTH-04, NFR-SEC-05); rate limits on OTP, login, reset, upload, message, report; generic auth errors; secure password recovery with session invalidation; admin 2FA (D-031); least-privilege roles; IDOR and escalation prevention via `AccessPolicy` and scoped repository queries; SQL injection prevention (JPA, parameter binding, allow-listed sort); XSS (text rendering, sanitiser for any rich text, CSP); upload validation (size, Tika content sniffing, re-encode, random keys, private storage); ID card private storage with authorised streaming; WebSocket authentication and per-destination authorisation; audit logging; data minimisation in DTOs; dependency and secret scanning in CI. The security test list is in `TEST_STRATEGY.md` section 5 and is part of "done".

## 10. Design system and frontend quality

- `DESIGN.md` is a contract. Build `frontend/src/design/tokens.css` from it (light on `:root`, dark on `[data-theme="dark"]`, initial theme from `prefers-color-scheme`, user choice saved to the account), and use tokens only; add a lint rule (`stylelint` custom property rule or a grep test) that fails on raw hex outside `tokens.css`.
- Follow the visual language of `pic.docx`: eyebrow label plus large heading, stat strip for real numbers, ruled lists instead of card piles, outlined pill buttons, bordered grouped settings cards, modal with left nav. Admin uses the same tokens with denser layout.
- Open gaps (D-040): font family, accent, status colours, focus ring are not in the reference. Do not invent them. Communicate status with text, icons, shape. Ask the owner via the decision log.
- **Avoid the generic look:** no gradients, glassmorphism, decorative icons, fake charts or statistics, repeated identical card grids, large radii everywhere, gratuitous animation (respect reduced motion), or buttons that do nothing. Every screen states its purpose, has one primary action and a clear hierarchy.
- Accessibility to WCAG 2.1 AA (D-056): semantic HTML, labelled controls, focus management in modals, visible focus, keyboard-only paths, `aria-live` for errors and real-time updates, 4.5:1 contrast except where DESIGN.md documents the known muted-text limitation.
- Responsive 360 to 1920 px (NFR-USA-03). Bangla text must not break layout.
- Frontend security: no `dangerouslySetInnerHTML` without the sanitiser; never trust client state for authorisation; handle 401, 403, 404, 409, 422, 429 and network errors consistently.

## 11. Design pattern implementation map (course requirement)

Implement exactly these where listed in `ARCHITECTURE_OVERVIEW.md` section 8, and document each in `docs/design-patterns.md` with: problem, why this pattern, location, participating classes (with a small class diagram), maintainability gain, and tests. Do not add patterns that have no problem to solve; Abstract Factory is intentionally excluded.

| Pattern | Concrete use | Test |
|---|---|---|
| Strategy | `OcrProvider`, `SmsProvider`, `SearchStrategy`, `DeliveryChannel`, `FeedSource` per level | UT-PAT-STRAT: each strategy honours the contract; selection by config |
| Adapter | `SmtpEmailAdapter`, `GatewaySmsAdapter`, `TesseractOcrAdapter`, `S3StorageAdapter`, `LocalStorageAdapter` | IT-PAT-ADAPT with fake servers and contract tests |
| Factory Method | `DeliveryChannelFactory`, provider resolution with fallback | UT-PAT-FACT: fallback when primary unavailable (NFR-FLT-02) |
| Observer | Spring domain events `StudentVerified`, `StudentRejected`, `PostAutoHidden`, `AppealDecided`, `TeamJoinAccepted`, `MessageSent` with `AFTER_COMMIT` listeners for notifications and audit | IT-PAT-OBS: listeners fire only after commit, not on rollback |
| State | `AccountState`, `PostModerationState`, `EventLifecycle`, `TeamState`, `AppealState` | UT-PAT-STATE: full transition table, illegal transitions throw |
| Chain of Responsibility | `ContentValidator` chain (size, type, links, flood), `ReportEligibilityHandler` chain | UT-PAT-COR: each handler alone and the ordered chain |
| Specification | `FeedSpecifications` for level, visibility, scope, sector, status, date | IT-PAT-SPEC: the visibility matrix test |
| Facade | `VerificationFacade`, `ModerationFacade`, `TeamFormationFacade` | IT-PAT-FACADE: one call, one transaction, rollback on failure |
| Builder | `ApiError`, `AuditEntry`, notification payload | UT-PAT-BUILD |
| Decorator | `StorageGateway` stack (validating, scanning, metrics) | UT-PAT-DECOR: only if three or more behaviours stack; otherwise omit and say so in the docs |
| Repository, Service Layer, DTO, DI, MVC | whole app | ArchUnit rules |

## 12. Development phases

For **every** phase follow the loop: (1) review requirements and decision log entries; (2) list tasks; (3) identify dependencies; (4) implement; (5) run the relevant tests; (6) fix failures; (7) review security and code quality; (8) update documentation and the traceability matrix; (9) report what is complete, what is not, and the remaining risks. Do not start a phase while the previous phase has failing tests.

**Phase 1: Inspection and clarification.** Inspect the repository (existing frontend, backend, configs). Read all six documents. Confirm the decision-log defaults, list OPEN items for the owner (font, accent and status colours, SMS and OCR providers, upload limits, deployment target, hardware for load tests). Output: `docs/phase-reports/01.md`, repository audit, risk list.

**Phase 2: Architecture and technology.** Create the multi-module layout: `backend/`, `frontend/`, `docs/`, `ops/` (Nginx, compose, backup scripts), `.github/workflows` or equivalent CI. Set up Maven, linting (Checkstyle or Spotless, ErrorProne optional), ArchUnit, Vite with TypeScript strict, ESLint, Prettier, Vitest, Playwright, JaCoCo, Testcontainers. Docker Compose for MySQL and a mail catcher in dev. Exit: empty app builds, CI green, health endpoint, OpenAPI served.

**Phase 3: Design system and frontend foundation.** Generate tokens from `DESIGN.md`, theme switch, primitives (Button, Pill, Field, Modal, StatStrip, RuledList, EmptyState, Skeleton, Toast), layouts for student and admin, API client with CSRF and error mapping, route guards (UX only), i18n scaffold, axe in CI. Visual review in both themes at 360 and 1280 px.

**Phase 4: Database.** Write ERD and schema docs first, then Flyway migrations V001 onward, the immutability triggers, the DB users and grants, dev seeders. Repository tests with Testcontainers for constraints (unique email, unique university plus student id, cascade and set-null behaviour), indexes via `EXPLAIN` on the main feed queries.

**Phase 5: Authentication, verification, authorisation.** Registration, OTP, email link, OCR job, Pending Review, student login, throttling and lockout, password reset, sessions, `AccessPolicy`, student verification state machine, notifications for decisions (minimal notification service now, completed in Phase 8). Admin login with TOTP and the minimal admin approve or reject path so verification can be completed end to end. Tests: TC-01 to TC-07, TC-23, TC-24, TC-25, TC-27 and the matching security tests.

**Phase 6: Profiles and community.** Profiles, projects, education, achievements, skills, links, follow, privacy, activity feed, community context, feeds per level with the visibility matrix, sector follows. Tests: TC-08, TC-09, TC-10, visibility matrix, IDOR tests.

**Phase 7: Events, content, search, teams.** Events, posts, comments with mentions, reactions, attachments, bookmarks, calendar, upcoming sidebar, archive job, team requests and join flow, search with FULLTEXT and the 100,000-row benchmark. Tests: TC-11 to TC-14, TC-17, TC-20 (SQLi and XSS parts), performance query checks.

**Phase 8: Messaging and notifications.** Rooms (department, university, private, team, temp), STOMP with authenticated sessions and destination authorisation, persistence then broadcast, read state, block, mute, attachments, notification centre, preferences, email digests, deadline reminders. Tests: TC-15, TC-16, WebSocket security and latency tests.

**Phase 9: Admin Portal and moderator workflows.** Every module in section 6, reporting and auto-hide, appeals, strikes, announcements, master data, moderator management, analytics, overdue alerts, audit viewer with hash verification. Tests: TC-18, TC-19, TC-26, TC-28 to TC-31, moderator isolation suite, audit immutability tests.

**Phase 10: Security hardening.** Run the whole security suite, ZAP baseline, dependency scans, header and cookie checks, secret scan, log scan, review of every endpoint against the AuthZ matrix, review of upload and ID image paths, account deletion and retention jobs (D-052, D-053). Fix and re-run.

**Phase 11: Comprehensive testing and performance.** Full regression, full Playwright suite on three engines (TC-22), accessibility run, k6 load tests per `TEST_STRATEGY.md` section 6 with the stated hardware, usability sessions, backup and restore drill. Report measured numbers; failing targets are reported as failing with a remediation plan.

**Phase 12: Deployment and documentation.** Production profile, Nginx config with TLS and headers, systemd or container deployment, `.env.example`, backup and restore scripts and runbook, monitoring and alert setup, final documentation, final traceability matrix, release notes, known issues. Verify the deployment instructions on a clean machine before marking them verified.

## 13. Repository layout and required documents

```
unilink/
  README.md                         what it is, how to run, test, deploy
  DESIGN.md                         design system (this contract)
  .env.example                      every variable, no real secrets
  backend/  pom.xml, src/main/java/com/unilink/..., src/main/resources/{application*.yml, db/migration}
            src/test/java/...  (unit, slice, integration, security, architecture)
  frontend/ package.json, vite.config.ts, index.html, admin.html, src/..., e2e/ (Playwright)
  ops/      nginx/, docker-compose.yml, backup/, runbooks/, k6/
  docs/     requirements/  (SRS copy, REQUIREMENTS_DECISION_LOG.md)
            architecture/  (ARCHITECTURE_OVERVIEW.md, diagrams)
            api/           (OpenAPI export, API guide)
            db/            (ERD.md, schema.md, retention.md)
            design-patterns.md
            testing/       (TEST_STRATEGY.md, RESULTS.md)
            traceability.md
            deployment.md
            phase-reports/
```

Required documents: README, DESIGN.md, OpenAPI documentation, database documentation with ER diagram, architecture diagram, design pattern documentation, testing documentation, deployment instructions, `.env.example`. They must describe what **exists**, not what is planned.

### Environment variables (names; values never committed)
`SPRING_PROFILES_ACTIVE`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, `DB_MIGRATION_USER`, `DB_MIGRATION_PASSWORD`, `APP_BASE_URL`, `SESSION_COOKIE_DOMAIN`, `STORAGE_PROVIDER` (`s3|local`), `S3_ENDPOINT`, `S3_BUCKET_PRIVATE`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `LOCAL_STORAGE_PATH`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `MAIL_FROM`, `SMS_PROVIDER`, `SMS_API_URL`, `SMS_API_KEY`, `SMS_SENDER_ID`, `OCR_PROVIDER`, `OCR_DATA_PATH`, `TOTP_ISSUER`, `ENCRYPTION_KEY` (for TOTP secrets at rest), `INITIAL_ADMIN_EMAIL`, `INITIAL_ADMIN_PASSWORD` (bootstrap only, rotate), `ADMIN_IP_ALLOWLIST`, `TRUSTED_PROXY_CIDRS`, `RATE_LIMIT_*`, `REPORT_THRESHOLD_DEFAULT`, `LOG_LEVEL`.

## 14. Definition of done and acceptance (section 17 of the brief, restated)

The system is complete only when all are true and evidenced:
- All approved in-scope requirements are implemented (matrix shows no `Not started`; anything `Not implemented` has a decision id).
- Every critical workflow works end to end in a browser: verification, event lifecycle, team formation, reporting and appeals, messaging, admin approval, moderator isolation.
- Student and Admin interfaces are separate; backend authorisation is enforced and tested for both roles.
- Persistence works in MySQL with migrations; nothing authoritative is in the browser.
- Light and dark themes both work; the UI follows `DESIGN.md`; the linter result is recorded.
- Required patterns are implemented, documented and tested.
- Automated tests pass and security tests were executed; build and type checks pass (`mvn verify`, `npm run typecheck`, `npm run lint`, `npm run build`, Playwright).
- Performance targets are reported with measurements, hardware and dataset (met or not met).
- No critical or high defects open; documentation matches the system; deployment instructions were run on a clean environment.
- Final deliverable: the traceability matrix (SRS ID to implementation location to test ID to status) using only the statuses Completed, Partially completed, Not implemented, Blocked by external dependency, Not tested.

Never claim production readiness without this evidence. If something is blocked (SMS credentials, OCR language data, hosting), say so, state what is needed, and keep the status honest.

## 15. Each phase report template

```
Phase N report: <date> <commit>
Completed: <requirement ids> (tests executed: <command and result>)
Partially completed: <ids and what is missing>
Not implemented / blocked: <ids, reason>
Defects found and fixed: <list>   Open defects: <list with severity>
Security review notes: <list>
Decision-log entries added: <ids>
Docs updated: <files>
Remaining risks and questions for the owner: <list>
```
