# UniLink — Verified Student Network for Bangladesh Universities

UniLink connects verified students across universities in Bangladesh:
department and national community feeds, rich profiles, teams, events
managed by administrators, and a developer workspace that showcases
students' real public GitHub repositories. Every student is verified
against their university ID card before they can post.

This README is the complete guide to running the whole system locally
on Windows 11. `README-WINDOWS.md` in this folder carries the extended
troubleshooting notes from the original setup package.

---

## 1. Project overview

- **Student app** — sign up with ID-card verification, community feed
  (Recent and Top), profiles, network, teams, events, saved posts,
  notifications, settings, and the GitHub developer workspace.
- **Admin portal** — a separate entry in the same frontend build:
  dashboard, verification queue, all students, moderation, events
  (create / draft / publish / cancel / edit / delete, registrations),
  identity changes, master data, admin users & roles, analytics, and
  the activity (audit) log.
- **Backend** — one modular-monolith Spring Boot service with a MySQL
  database. All state is server-side; the browser keeps no application
  data of its own.

## 2. Features

- Registration with phone OTP + email verification + admin ID-card
  review (front and back images), resubmission, and public appeals.
- Feed with five persisted reactions (Like, Celebrate, Insightful,
  Support, Curious), threaded comments (replies), bookmarks, reports
  with automatic hiding, and a Top ranking
  (`(affinity + engagement) / (hours + 2)^1.5`).
- Profiles with skills, links, education, projects and achievements; a
  unified activity stream (posts, comments, reactions); follow system
  with follower/following lists and department-first suggestions.
- Admin-managed events only — students can browse and register
  (Interested / Going, capacity and deadline enforced) but can never
  create or edit events. Published admin events appear on the student
  Events page immediately.
- Persistent notifications (follows, reactions, comments, replies,
  event publication/cancellation, registration confirmation,
  verification decisions) with per-type preferences.
- Developer Workspace: real GitHub OAuth (read-only, public
  repositories), an in-app repository browser (files, README, commits),
  and UniLink-native per-repository discussions with `@file` mentions.
- Role-based access: students, university moderators (scoped to their
  own university), and platform admins; admin sign-in requires TOTP
  MFA. Messaging and a Discover page are intentionally out of scope.

## 3. Technology stack

| Layer | Technology |
|---|---|
| Frontend | React 18, TypeScript (strict), Vite 5, TanStack Query, React Router 6 |
| Backend | Java 21, Spring Boot 3.3.5, Spring Security, Spring Session JDBC |
| Database | MySQL 8, Flyway migrations (the only schema mechanism) |
| Auth | Server sessions with HttpOnly cookies + CSRF; Argon2id password hashing; TOTP MFA for admins |
| Storage | Local private file storage behind a provider interface (dev) |
| Providers | SMS/email/OCR behind interfaces; logging providers in dev |

## 4. System architecture

One Spring Boot modular monolith (`backend/`) serves a JSON API under
`/api/**`; the React app (`frontend/`) is a single Vite build with two
entries — the student app (`index.html`) and the admin portal
(`admin.html`) — served by the Vite dev server in development and by a
static host behind the API origin in deployment. Packages follow the
domain: `identity`, `profile`, `community` (feed), `content` (posts,
comments, reactions, events), `teams`, `messaging` (legacy, out of
product scope), `moderation`, `admin`, `workspace` (GitHub), `common`,
`config`, `security`, `dev` (seed data, dev profile only).

## 5. Folder structure

```
unilink/
  backend/            Spring Boot service (Maven Wrapper included)
    src/main/java/com/unilink/…
    src/main/resources/db/migration/   Flyway V001…V012
    .env.example
  frontend/           React + Vite app (student + admin entries)
    src/  e2e/  .env.example
  database/           Notes on the migration system (see its README)
  docs/               Requirements, decision log, phase reports, api/
  scripts/            Windows PowerShell helpers (setup/start/stop/backup)
  ops/                Sandbox/CI helpers (not needed on Windows)
  README-WINDOWS.md   Extended Windows setup + troubleshooting
```

## 6. Prerequisites

- Windows 11, JDK 21 (Temurin or Oracle), MySQL 8.0 server, Node.js 20+
  (npm), Git. No global Maven is needed — the backend ships the Maven
  Wrapper (`backend/mvnw.cmd`).
- Optional: Tesseract OCR on PATH (without it, ID-card OCR scores 0.0
  and reviewers decide manually — everything else works).

## 7. Installation instructions (Windows)

1. Install MySQL 8 and create the database and users (step 8).
2. Copy `backend/.env.example` values into real environment variables
   (step 9) — Spring Boot does not read `.env` files by itself; set the
   variables in your terminal or via `scripts/setup.ps1`.
3. From `backend/`: `.\mvnw.cmd spring-boot:run` (first boot runs all
   Flyway migrations and creates the initial platform admin).
4. From `frontend/`: `npm install` then `npm run dev`.
5. Open the student app at http://localhost:5173 and the admin portal
   at http://localhost:5173/admin/login.

Or run `scripts/setup.ps1` once, then `scripts/start-dev.ps1`.

## 8. Database setup instructions

```sql
CREATE DATABASE unilink_dev CHARACTER SET utf8mb4;
CREATE USER 'unilink_app'@'localhost' IDENTIFIED BY '<app password>';
CREATE USER 'unilink_mig'@'localhost' IDENTIFIED BY '<migration password>';
GRANT ALL PRIVILEGES ON unilink_dev.* TO 'unilink_mig'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON unilink_dev.* TO 'unilink_app'@'localhost';
-- If trigger creation fails with error 1419 (binary logging on):
SET PERSIST log_bin_trust_function_creators = 1;
```

Flyway applies `V001…V012` automatically on first boot using the
migration user. Never edit an applied migration; add a new one.
`database/README.md` explains the layout. Backups:
`scripts/backup-database.ps1` (mysqldump into `backups/`).

## 9. Environment variables

Backend (see `backend/.env.example` for the full list):

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Application datasource |
| `DB_MIGRATION_USER`, `DB_MIGRATION_PASSWORD` | Flyway datasource |
| `ENCRYPTION_KEY` | AES-256-GCM key material (GitHub tokens, TOTP secrets) |
| `INITIAL_ADMIN_EMAIL`, `INITIAL_ADMIN_PASSWORD` | First platform admin (created once, only if no admin exists) |
| `APP_BASE_URL` | Public base URL for email links |
| `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `GITHUB_REDIRECT_URI` | GitHub OAuth app (optional; Workspace shows a "not configured" state without them) |
| `FRONTEND_BASE_URL` | Where the OAuth callback returns the browser (default `http://localhost:5173`) |
| `SPRING_PROFILES_ACTIVE` | Set to `dev` for seeded demo data |

Frontend: `frontend/.env.example` (`VITE_API_BASE`, default same-origin
`/api` via the Vite proxy). Root `.env.example` mirrors the backend set
for convenience.

## 10. How to run the backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run        # dev server on http://localhost:8080
.\mvnw.cmd clean package          # build the runnable jar
java -jar target\unilink-backend-*.jar
```

Health check: `http://localhost:8080/api/health`.

## 11. How to run the frontend

```powershell
cd frontend
npm install
npm run dev                       # http://localhost:5173 (admin: /admin/login)
npm run build                     # production build into dist/
```

## 12. Development credentials (dev profile ONLY)

With `SPRING_PROFILES_ACTIVE=dev`, the seeder creates demo data once
(idempotent — it does nothing when users already exist):

| Account | Email | Password |
|---|---|---|
| Platform admin | the `INITIAL_ADMIN_EMAIL` you set | the `INITIAL_ADMIN_PASSWORD` you set (TOTP enrolment on first sign-in) |
| MU moderator | `mod.mu@unilink.dev` | `Moderator123!` |
| DU moderator | `mod.du@unilink.dev` | `Moderator123!` |
| Verified student | `rahim@student.mu.edu.bd` | `Student123` |
| Verified student | `nusrat@student.mu.edu.bd` | `Student123` |
| Verified student | `tanvir@student.du.edu.bd` | `Student123` |
| Unverified student | `arif@student.mu.edu.bd` | `Student123` |

These exist only under the `dev` profile on a local machine. Never use
them anywhere else. In dev, email/SMS are logging providers — OTPs and
verification links are printed in the backend console.

## 13. GitHub OAuth setup

1. GitHub → Settings → Developer settings → OAuth Apps → New OAuth App.
2. Homepage URL `http://localhost:5173`; Authorization callback URL
   `http://localhost:8080/api/workspace/github/callback`.
3. Set `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` (and
   `GITHUB_REDIRECT_URI` if you changed the callback) and restart the
   backend.
4. In Workspace, choose **Authorize GitHub**; approve on GitHub; you
   return to `/workspace?github=connected` with your public
   repositories listed. The requested scope is `read:user` only;
   repository reads use the public API, private repositories are
   refused, and the stored token is encrypted at rest. Disconnect any
   time from Workspace (this deletes the stored token).

## 14. Admin event management guide

Sign in at `/admin/login` (TOTP required) → **Events → Create Event**.
Fill the form (audience/visibility, schedule, venue or meeting link,
registration settings, capacity) and choose **Publish Event** or
**Save as Draft**. Drafts are visible only in the admin portal;
publishing makes the event appear on the student Events page
immediately. From **All Events** you can publish a draft, cancel a
published event (registered students are notified), edit deadline and
visibility, hide/remove (moderation), delete drafts and cancelled
events, close registration, and view the registrations list. Students
have no event-creation controls anywhere, and the student event-create
endpoint refuses non-admin callers.

## 15. API documentation

Interactive OpenAPI (Swagger UI): `http://localhost:8080/swagger-ui.html`
— raw spec at `http://localhost:8080/v3/api-docs`. `docs/api/README.md`
summarises the endpoint groups and the integration contracts added in
the full-stack build (admin events, reactions, threaded comments, feed
sorting, network lists, workspace).

## 16. Testing instructions

```powershell
cd backend;  .\mvnw.cmd test                 # JUnit suite (H2, MySQL mode)
cd frontend; npm run typecheck               # TypeScript strict
             npm run build                   # production build
             npx vitest run                  # component/contract tests
             npx playwright test e2e/smoke.spec.ts
```

## 17. Troubleshooting

- **Backend fails fast with "could not resolve placeholder"** — a
  required environment variable (DB password, `ENCRYPTION_KEY`) is not
  set in this terminal.
- **Flyway error 1419 creating triggers** — run the
  `log_bin_trust_function_creators` statement from step 8 (local dev
  only).
- **OTP / verification email not arriving in dev** — expected: the dev
  providers log codes and links to the backend console.
- **Admin sign-in loops to TOTP** — enrol with any RFC 6238 authenticator
  app using the secret shown on first sign-in; recovery codes are shown
  once.
- **Workspace says GitHub is not configured** — set the three
  `GITHUB_*` variables (step 13) and restart the backend.
- **Port already in use** — stop the earlier backend/frontend first
  (`scripts/stop-dev.ps1`).

## 18. Future improvements

- Production deployment profile (TLS, Nginx, hosted MySQL, real
  SMS/email providers, object storage) — the provider interfaces are
  ready; see `docs/phase-reports/` for the deployment checklist.
- Cursor pagination for the Top feed ranking and for follower lists
  beyond the current page sizes.
- GitHub repository topics/languages enrichment in the workspace
  viewer.

---

Local development only. Dev credentials, logging providers and the
seeded demo data must never be used on a shared or production system.
