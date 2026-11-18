# UniLink — Windows 11 Local Setup

Run the complete UniLink application on a personal Windows 11 PC with local
MySQL 8. This is a **local development setup only**. It is not a production
deployment: there is no TLS, no Nginx, no hosted storage, no real SMS or
email provider, and the dev defaults below must never be used on a shared
machine.

Package contents verified at build time:

- Content commits end at `a054465` (Phase 3 report). The packaging
  commit on top adds only the Maven Wrapper files and this README —
  see §12 for the full commit list.
- Branch: `master`, working tree clean
- Backend: Java 21 / Spring Boot 3.3.5 / Maven, `backend/` (includes the
  Maven Wrapper, `mvnw.cmd`, added for this package — no application code
  was changed)
- Frontend: React 18 / TypeScript / Vite, `frontend/` (includes
  `package-lock.json`)
- Flyway migrations `V001`–`V011` in
  `backend/src/main/resources/db/migration/`
- Configuration: `backend/src/main/resources/application.yml` (+
  `application-dev.yml`, `application-prod.yml`),
  `.env.example`, `frontend/.env.example`
- `ops/` helper scripts are **sandbox-only** (see §11) and are not needed
  on Windows

## 1. What is verified, and what is not

Verified while preparing this package:

- All source, migrations, configuration and docs listed above are present
  in the repository.
- The Maven Wrapper files are genuine Apache Maven Wrapper 3.3.2
  (`mvnw`, `mvnw.cmd`, `maven-wrapper.jar` — the jar's SHA-1 matches the
  checksum published on Maven Central) and point at Maven 3.9.9.
- The application code contains no Linux-only paths: `application.yml`
  uses `localhost` URLs and the relative storage path `./storage-dev`,
  and the Vite dev server proxies `/api` and `/ws` to
  `http://localhost:8080`.
- The backend's email and SMS providers in the `dev`/`default` profiles
  are **logging providers**: they write OTPs and verification links to
  the backend console instead of sending them. No SMTP server is needed
  for local runs (see §7).

**Not verified — read before you start:**

- **Flyway has never run.** No migration `V001`–`V011` has ever been
  executed by Flyway against a real MySQL database, in this project, on
  any machine. The first backend boot on your PC is the first real
  Flyway run. Watch the startup log as described in §6.
- **`V006` is the highest-risk migration.** It creates two audit-log
  immutability triggers using a `DELIMITER $$` block. Flyway 10.10.0
  ships delimiter handling for MySQL scripts (checked by inspecting the
  bundled `flyway-core-10.10.0.jar`), but this exact script has never
  been executed by Flyway. If startup fails at `V006`, see §10.
- **No user workflow has ever run against a live MySQL backend.**
  Registration, OTP, login, admin login + TOTP, verification queue,
  messaging and every other flow are proven only by unit tests (H2) and
  mocked-fetch frontend tests. The first end-to-end run happens on your
  PC; treat it as a verification exercise, not a formality.
- `V004` uses MySQL-only `FULLTEXT` indexes and `V001`–`V005` use
  `ENGINE=InnoDB` / `utf8mb4_0900_ai_ci` — all standard MySQL 8, no
  action needed, but they will not run on any other database.
- The analytics queries use MySQL-only date functions and cannot run
  on H2; they are only exercised against real MySQL.

## 2. Required software

| Software | Version | Notes |
|---|---|---|
| Windows 11 | 21H2 or newer | 64-bit |
| Java (JDK) | 21 | Eclipse Temurin 21 (Adoptium) recommended; any JDK 21 works |
| MySQL Server | 8.0.x | MySQL Installer for Windows; Server only is enough |
| Node.js | 20 LTS or 22 LTS | Ships with npm; Vite 5 requires Node 18+, Playwright 1.48 requires 18+ |
| Git for Windows | any recent | Needed only to inspect history; the ZIP already contains the code |
| PowerShell | 5.1 or 7 | All commands below are PowerShell |

Optional: MySQL Workbench (bundled with the MySQL Installer),
[Tesseract 5](https://github.com/UB-Mannheim/tesseract/wiki) (only if you
want real ID-card OCR — without it, OCR records a failed result with
score 0.0 and a human reviewer decides; registration itself still
works), [Mailpit](https://mailpit.ax/) (only if you want a real local
inbox instead of the console log).

You need internet access for the first run: the Maven Wrapper downloads
Maven 3.9.9 and the backend downloads its dependencies; `npm install`
downloads the frontend dependencies.

Extract the ZIP to a short path without spaces, for example
`C:\unilink`. (Long paths and spaces in the folder name cause
intermittent failures in Node and Maven tooling.)

## 3. Install and verify Java 21

1. Install Eclipse Temurin 21 from
   <https://adoptium.net/temurin/releases/?version=21>. During setup,
   tick **"Set JAVA_HOME variable"** and **"Add to PATH"**.
2. Open a **new** PowerShell window and verify:

```powershell
java -version
```

Expected output: `openjdk version "21.0.x"`. If `java` is not found,
close and reopen PowerShell, or set `JAVA_HOME` manually to the JDK
folder (for example `C:\Program Files\Eclipse Adoptium\jdk-21.0.x-hotspot`)
and add `%JAVA_HOME%\bin` to `Path`.

## 4. Install MySQL 8 and create the database

1. Run the MySQL Installer, choose **Server Only** (add Workbench if
   you want a GUI), and install MySQL Server 8.0.x.
2. During configuration: set a **root password** and remember it,
   keep port **3306**, keep the default authentication
   (`caching_sha2_password` — the MySQL 8 connector used by the backend
   supports it), and run MySQL as a Windows service (start at boot).
3. Open PowerShell and connect as root (you will be prompted for the
   root password you set):

```powershell
mysql -u root -p
```

4. Create the database and the two application users. Replace
   `CHOOSE_A_STRONG_PASSWORD` and `CHOOSE_ANOTHER_STRONG_PASSWORD` with
   your own values — **do not reuse the dev defaults shipped in
   `.env.example` / `application.yml`** (see §8). Both users need a
   `localhost` and a `127.0.0.1` entry, because the JDBC URL uses
   `localhost` and MySQL treats the two hosts as different accounts:

```sql
CREATE DATABASE IF NOT EXISTS unilink_dev
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'unilink_app'@'localhost'
  IDENTIFIED BY 'CHOOSE_A_STRONG_PASSWORD';
CREATE USER IF NOT EXISTS 'unilink_app'@'127.0.0.1'
  IDENTIFIED BY 'CHOOSE_A_STRONG_PASSWORD';
CREATE USER IF NOT EXISTS 'unilink_mig'@'localhost'
  IDENTIFIED BY 'CHOOSE_ANOTHER_STRONG_PASSWORD';

GRANT ALL PRIVILEGES ON unilink_dev.* TO 'unilink_app'@'localhost';
GRANT ALL PRIVILEGES ON unilink_dev.* TO 'unilink_app'@'127.0.0.1';
GRANT ALL PRIVILEGES ON unilink_dev.* TO 'unilink_mig'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

`unilink_app` is the runtime user (the backend's day-to-day
connection). `unilink_mig` is the migration user (Flyway). The `ALL
PRIVILEGES` grant is deliberately broad for a local dev machine; it is
not a production grant.

5. Verify the users can connect:

```powershell
mysql -u unilink_app -p -e "SELECT 1;"
mysql -u unilink_mig -p -e "SELECT 1;"
```

## 5. Backend environment configuration

**Important:** Spring Boot does **not** read a `.env` file. Copying
`.env.example` to `.env` alone does nothing. Set real Windows
environment variables in each PowerShell window before starting the
backend (or set them persistently with `setx` — see below). The
variable names are documented in `.env.example`; the ones you must set
for a local run are:

```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:DB_PASSWORD = "CHOOSE_A_STRONG_PASSWORD"          # must match §4
$env:DB_MIGRATION_PASSWORD = "CHOOSE_ANOTHER_STRONG_PASSWORD"  # must match §4
$env:ENCRYPTION_KEY = "a-long-random-value-at-least-32-chars"
$env:INITIAL_ADMIN_EMAIL = "admin@example.com"          # first Platform Admin
$env:INITIAL_ADMIN_PASSWORD = "a-12-or-more-char-password"
```

Notes:

- If you do **not** set `DB_PASSWORD` / `DB_MIGRATION_PASSWORD`, the
  backend falls back to the dev defaults compiled into
  `application.yml`. Those defaults only work if your MySQL users were
  created with exactly those passwords — do not do that on any machine
  other than your own, and prefer setting the variables above.
- `ENCRYPTION_KEY` encrypts admin TOTP secrets at rest. The shipped
  default (`dev-only-encryption-key-change-me`) is public; set your own.
  Changing it later invalidates already-enrolled authenticators.
- `INITIAL_ADMIN_EMAIL` / `INITIAL_ADMIN_PASSWORD` create the first
  Platform Admin at startup, **only if no admin accounts exist yet**.
  The password must be at least 12 characters. If you forget to set
  them, the backend logs a warning at startup and skips the bootstrap;
  set them and restart the backend. There is no other way to create the
  first admin.
- To persist variables across PowerShell sessions (optional), use
  `setx DB_PASSWORD "your-value"` in a normal (non-admin) prompt, then
  open a new PowerShell window. `setx` does not affect the current
  window.
- The remaining variables in `.env.example` (SMTP, SMS, S3, OCR) are
  not needed for local runs: email/SMS default to logging providers
  and storage defaults to the local `./storage-dev` folder.

## 6. Flyway migrations and backend startup

Flyway runs automatically when the backend starts
(`spring.flyway.baseline-on-migrate: true`, migrations in
`classpath:db/migration`). There is no separate migration command to
run first. On the first boot it applies `V001`–`V011` in order and
creates the `flyway_schema_history` table.

Start the backend (first run downloads Maven 3.9.9 and all
dependencies — allow a few minutes and do not interrupt it):

```powershell
cd C:\unilink\backend
.\mvnw.cmd spring-boot:run
```

Watch the console. A successful first boot shows Flyway applying the
migrations, then:

```text
Successfully applied 11 migrations to schema `unilink_dev`
...
Started UnilinkBackendApplication ... Tomcat started on port 8080
```

Then verify the backend answers:

```powershell
curl.exe http://localhost:8080/api/health
```

Expected: a JSON response with `"status": "UP"`. The student app is
served by the Vite dev server (§7), not by the backend — the backend
only serves `/api`, `/ws` and `/api/health`.

Useful backend commands (all from `C:\unilink\backend`):

```powershell
.\mvnw.cmd test          # full backend test suite (uses H2, needs no MySQL)
.\mvnw.cmd spring-boot:run
```

Stop the backend with `Ctrl+C` in its PowerShell window.

**Reading OTPs and email links.** In the `dev` profile the email and
SMS providers only log. Registration OTPs, email verification links
and password-reset codes appear in the backend console as `DEV-ONLY
email to ...` lines — copy the code or link from there. Nothing is
actually sent.

## 7. Frontend install and startup

In a **second** PowerShell window (the backend keeps running in the
first):

```powershell
cd C:\unilink\frontend
npm install
npm run dev
```

`npm install` downloads all dependencies (first run only). `npm run
dev` starts Vite on port 5173 and proxies `/api` and `/ws` to the
backend on `http://localhost:8080` (see `frontend/vite.config.ts`).
No frontend environment variables are needed for local runs
(`frontend/.env.example` documents the optional `VITE_API_BASE`;
leave it empty for same-origin `/api`).

Useful frontend commands (all from `C:\unilink\frontend`):

```powershell
npm run typecheck   # TypeScript check
npm run build       # production build (output: frontend/dist)
npm test            # Vitest suite
npm run e2e         # Playwright smoke tests (needs the dev server running)
```

## 8. Exact localhost URLs

| What | URL |
|---|---|
| Student app | <http://localhost:5173/> |
| Admin login | <http://localhost:5173/admin/login> |
| Backend API | <http://localhost:8080/api> |
| Backend health | <http://localhost:8080/api/health> |
| API docs (dev profile) | <http://localhost:8080/swagger-ui.html> |

Keep the backend window and the frontend window running at the same
time. The student app talks to the backend through the Vite proxy, so
both must be up.

## 9. First-run walkthrough (first live verification)

This flow has never run against a live MySQL backend before — treat
each step as a check, and keep the backend console open so you can see
the `DEV-ONLY` email/SMS lines.

1. Open <http://localhost:5173/> and register a student account
   (university, department and batch come from dropdowns; you need a
   Bangladeshi mobile number in `01[3-9]xxxxxxxx` format and an ID card
   image).
2. Copy the phone OTP from the backend console and verify it. The
   account lands on the pending screen.
3. Sign in as the admin you bootstrapped in §5 at
   <http://localhost:5173/admin/login>. The first login enrols an
   authenticator: scan the QR code with an authenticator app
   (Microsoft Authenticator, Google Authenticator, or similar), enter
   the 6-digit code, and **save the recovery codes** shown on screen.
   If your authenticator clock is off by more than a minute, TOTP
   codes will be rejected — enable automatic time on the device.
4. In the admin portal, open the verification queue, review the ID
   image, and approve the student. The student can then sign in at
   <http://localhost:5173/>.

## 10. Common errors and troubleshooting

| Symptom | Cause / fix |
|---|---|
| `java` is not recognized | JDK 21 is not on `PATH`. Reopen PowerShell after install, or set `JAVA_HOME` and add `%JAVA_HOME%\bin` to `Path`. `java -version` must print 21. |
| `mvnw.cmd` fails to download Maven | No internet, or a proxy/firewall blocks `repo.maven.apache.org`. The wrapper downloads Maven 3.9.9 on first use. If you already have Maven 3.9+ installed, use `mvn spring-boot:run` instead. |
| `Communications link failure` / `Connection refused` at backend startup | MySQL is not running. Start the MySQL80 Windows service (`services.msc` → MySQL80 → Start), and check port 3306 with `Test-NetConnection localhost -Port 3306`. |
| `Access denied for user 'unilink_app'@'localhost'` | The password in `$env:DB_PASSWORD` does not match the MySQL user, or the user was created for only one host. Re-run the §4 SQL and make sure both `localhost` and `127.0.0.1` entries exist with the same password. |
| `Access denied for user 'unilink_mig'@'localhost'` | Same as above, for `$env:DB_MIGRATION_PASSWORD`. |
| `Unknown database 'unilink_dev'` | Create it with the §4 SQL. (The JDBC URL also has `createDatabaseIfNotExist=true`, so this should not normally happen.) |
| Flyway fails at `V006` | The `DELIMITER $$` trigger block. This is the one migration never executed by Flyway (see §1). First, copy the exact error from the console. The fallback is to create the two triggers manually in MySQL Workbench or the `mysql` CLI, then repair Flyway's history with `.\mvnw.cmd flyway:repair` (it clears the failed entry so the next boot retries). Do **not** edit `V006` in place — a changed checksum will fail validation on every other machine. The trigger SQL, without the `DELIMITER` lines, is: `CREATE TRIGGER trg_audit_no_update BEFORE UPDATE ON audit_logs FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_logs is append-only'; END;` and the same for `trg_audit_no_delete` with `BEFORE DELETE`. |
| `FlywayException: Validate failed: ... checksum mismatch` | A migration file was modified after it ran. Restore the file from the ZIP and do not edit applied migrations; new changes go in a new `V012+` migration. |
| `Table 'unilink_dev.flyway_schema_history' doesn't exist` after a failed boot | A previous boot failed part-way. Check which migration failed in the log, fix the cause, and restart — Flyway resumes from the failure point. |
| `Address already in use: 8080` (or 5173) | Another backend/frontend is already running, or another program uses the port. Stop the other process, or find it with `Get-Process -Id (Get-NetTCPConnection -LocalPort 8080).OwningProcess`. |
| Frontend shows `Page not found` on `/admin/login` | The Vite dev server is not running, or you opened the page before it started. Start `npm run dev` and wait for `Local: http://localhost:5173/`. |
| Frontend API calls fail with 401/403 | The backend is not running, or the session expired. Check the backend window is up, then sign in again. |
| `tesseract ... No such file` / OCR warnings | Tesseract is not installed. Benign for local runs: OCR records a failed result (score 0.0) and verification falls back to manual review. Install Tesseract 5 and add it to `PATH` if you want real OCR. |
| No OTP / email arrives | Nothing is sent in the `dev` profile by design. Read the code or link from the backend console (`DEV-ONLY email to ...`). |
| `npm install` fails | Node version too old, or no internet. `node -v` must print v20 or v22. Delete `frontend\node_modules` and `frontend\package-lock.json` only as a last resort and reinstall. |
| Tests fail on Windows | Backend tests use H2 and should pass without MySQL (`.\mvnw.cmd test`). Frontend tests use mocked fetch (`npm test`). If a test fails, keep the exact failure output — do not delete or edit the test to make it pass. |

## 11. What the `ops/` scripts are (and are not)

- `ops/recreate-dev-db.py` — recreates the dev databases after a VM
  wipe **in the Linux sandbox this project was built in**. It assumes
  MySQL root with no password and reads password defaults from the
  Spring config. **Do not run it on Windows.** Use the §4 SQL instead.
- `ops/fetch-deps.py` — fetches Maven dependencies with `curl`
  because Java processes in the sandbox cannot reach the internet.
  Not needed on Windows, where Maven downloads normally.
- `ops/docker-compose.yml` — a Docker Compose setup for reference.
  Not needed for this local setup; if you use Docker Desktop later,
  it is a starting point, not a tested configuration.

## 12. Commits in this package

- `a054465` — Phase 3: report and per-workstream evidence (D-093–D-118)
- `573d821` — Phase 3: data integrity + business-logic triage
- `b29e5b6` — Phase 3: security hardening
- `e6ff354` — Phase 2 closure
- `8c0407c` — Phase 2 verification fixes
- `080e763` — Phase 2: API contracts and auth workflows
- `1f59a50` / `32ae119` — Phase 1: boot blockers and login throttle
- Plus the Maven Wrapper files and this README (committed as the
  packaging commit on top of the above)

## 13. Security notes for this local setup

- Change every password and the encryption key from the shipped dev
  defaults. The defaults in `application.yml` and `.env.example` are
  public (they are committed in the repository) — they exist so the
  app boots in a throwaway dev environment, not as credentials to use.
- `.env` is git-ignored. Never commit it, never paste its contents
  into chat, and never reuse these passwords anywhere else.
- The backend binds to `localhost` only in this setup and the dev
  profile serves API docs at `/swagger-ui.html`. Do not expose port
  8080 or 5173 beyond your own PC.
- Uploads are stored under `backend/storage-dev/` (created on first
  upload) and are never served directly by the backend.
- This setup is for local development and verification. Production
  needs TLS, a reverse proxy, real SMTP/SMS/storage providers, secret
  management, and the deployment work that has not been started yet.
