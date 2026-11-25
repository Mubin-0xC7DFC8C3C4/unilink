# UniLink — Zero-Cost Demo Deployment (Cloudflare Pages + Render + TiDB)

Total cost: **$0**. No credit card is needed for any step. Do not add a
payment method anywhere; if any screen asks for one, stop — you are in
the wrong flow.

Topology: Cloudflare Pages serves the React app and proxies `/api` to
the Spring Boot backend on Render Free; the backend stores data in
TiDB Cloud Starter (MySQL-compatible) and uploaded files **inside the
database** (`STORAGE_PROVIDER=db`, D-168), because Render's filesystem
is ephemeral. Render Free sleeps after ~15 idle minutes; the first
request then takes about a minute to wake. Open the site a few minutes
before presenting.

## Step 0 — GitHub (one-time)

The code must be on GitHub; both Render and Cloudflare deploy from it.

1. Create an empty repository on github.com (no README, no .gitignore).
2. Add the deploy key you were given (the `id_ed25519.pub` text) under
   **Settings → Deploy keys → Add deploy key**, ticking
   **Allow write access**. Then send the repository's SSH URL
   (`git@github.com:<you>/<repo>.git`) to your assistant, who pushes
   `master` and verifies the remote.
   *(Alternative: push from your own PC — but note the newest commits
   were made by your assistant and exist only in the assistant's copy
   until this push happens.)*

## Step 1 — TiDB Cloud Starter (database)

1. Sign up at https://tidbcloud.com (GitHub sign-in is fine).
2. Create a **Starter** cluster (free). Choose the region nearest you.
3. In **Billing / cluster settings, set the monthly spending limit to
   $0** and never add a payment method.
4. Open **Connect → Public**, create a database named `unilink` and a
   user with read/write + DDL rights on it (the cluster root user works
   for a demo). Note the host, port (**4000**), username and password.
5. Build the JDBC URL exactly like this (TLS is mandatory on TiDB Cloud):

   `jdbc:mysql://<host>:4000/unilink?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&sslMode=VERIFY_IDENTITY&enabledTLSProtocols=TLSv1.2,TLSv1.3`

   Flyway migrations V001–V013 were made TiDB-compatible (D-167: no
   triggers, no FULLTEXT, portable seed syntax) and run automatically
   on the backend's first boot.

## Step 2 — Render Free (backend)

1. Sign up at https://render.com with GitHub. **New → Web Service**,
   connect the UniLink repository.
2. Settings: **Root Directory** `backend` · **Language/Docker** Docker ·
   **Dockerfile Path** `./Dockerfile` · **Instance Type** Free.
   Health Check Path: `/api/health`.
3. Environment variables (prod profile fails fast if a required one is
   missing):

   | Key | Value |
   |---|---|
   | `SPRING_PROFILES_ACTIVE` | `prod` |
   | `DB_URL` | the TiDB JDBC URL from Step 1 |
   | `DB_USER` / `DB_PASSWORD` | TiDB app user (read/write) |
   | `DB_MIGRATION_USER` / `DB_MIGRATION_PASSWORD` | TiDB user with DDL rights (root user for a demo) |
   | `ENCRYPTION_KEY` | a long random string you invent and keep |
   | `INITIAL_ADMIN_EMAIL` / `INITIAL_ADMIN_PASSWORD` | your first platform-admin login (password ≥ 12 chars) |
   | `STORAGE_PROVIDER` | `db` |
   | `APP_BASE_URL` | the Cloudflare Pages URL (fill in after Step 3, then restart the service) |
   | `FRONTEND_BASE_URL` | same Pages URL |
   | `MAIL_PROVIDER` | `brevo` for real email delivery (`log` = no delivery; codes are never printed in logs either way). `smtp` (Gmail) does NOT work from Render Free — Render blocks outbound SMTP ports 25/465/587 on free services, so a Gmail SMTP send times out at TCP connect. See D-189. |
   | `BREVO_API_KEY` | Brevo API key (starts `xkeysib-`; Settings → SMTP & API → API Keys in Brevo). **Set only after the Brevo code is live** (see the ordering note below) |
   | `MAIL_FROM` | a Brevo-verified sender email (Brevo → Settings → Senders & IPs → Senders → Add a sender → click the confirmation link). Must be verified in Brevo or delivery is rejected. |
   | `SMTP_HOST` | `smtp.gmail.com` — only used if `MAIL_PROVIDER=smtp` (paid Render plan or local dev) |
   | `SMTP_PORT` | `587` |
   | `SMTP_USERNAME` | your Gmail address |
   | `SMTP_PASSWORD` | a Google App Password (Google Account → Security → 2-Step Verification → App passwords), **not** your Gmail password |
   | `SMTP_STARTTLS` | `true` |

   **Brevo setup (D-189 — the only email path that works on Render Free):**
   1. Create a free Brevo account at https://brevo.com (no card; 300 emails/day).
   2. Verify a sender: Brevo → Settings → Senders & IPs → Senders → Add a sender → enter your email → click the confirmation link Brevo sends to that inbox. The sender must show **Verified**; no custom domain or DNS is needed, and a verified sender can email any recipient.
   3. Create an API key: Brevo → Settings → SMTP & API → API Keys → Create a new API key (starts `xkeysib-`). Copy it once — it is never shown again.
   4. In Render → unilink-api → Environment, set `MAIL_PROVIDER=brevo`, `BREVO_API_KEY=<the key>`, `MAIL_FROM=<the verified sender email>`.
   **Ordering: set these only AFTER the deploy containing the Brevo adapter is live.** The previous release has no `brevo` provider, so setting `MAIL_PROVIDER=brevo` before the code is live leaves no `EmailProvider` bean and the app will not start. The old `SMTP_*` vars become unused and may be left or removed.

   With `MAIL_PROVIDER=brevo` the backend refuses to start unless
   `BREVO_API_KEY` is set and `MAIL_FROM` is a real (non-placeholder)
   address — a half-configured provider used to fail silently and no
   email was ever delivered. Changing any of these redeploys the service.

   The Dockerfile already sizes the JVM for Render's 512 MB
   (heap ≈ 65% RAM, capped metaspace, SerialGC).
4. Deploy. First boot runs all Flyway migrations against TiDB, creates
   the platform admin, and should answer at
   `https://<service>.onrender.com/api/health`. Note the service URL.

## Step 3 — Point the frontend at the backend

Edit `frontend/public/_redirects` (GitHub web editor is fine): replace
`REPLACE-WITH-RENDER-BACKEND-URL` with the Render origin from Step 2
(e.g. `https://unilink-api.onrender.com`, no trailing slash). Commit.
This makes the browser talk only to the Pages origin while `/api` is
proxied to Render — cookies and CSRF work exactly as designed, no CORS.

## Step 4 — Cloudflare Pages (frontend)

1. Sign up at https://pages.cloudflare.com (no card).
2. **Create application → Pages → Connect to Git**, pick the repository.
3. Build settings: **Root directory** `frontend` · **Build command**
   `npm run build` · **Build output directory** `dist` · environment
   variable `NODE_VERSION` = `20`.
4. Deploy. Your public URL is `https://<project>.pages.dev`.
5. Go back to Render and set `APP_BASE_URL` / `FRONTEND_BASE_URL` to
   this URL, then restart the backend service.

## Demo-day notes

- **Wake the backend first**: open `https://<project>.pages.dev` (or the
  health URL) ~3 minutes before the demonstration; the first load wakes
  Render (≈ 1 minute), afterwards everything is fast.
- **Email verification and password-reset codes**: with `MAIL_PROVIDER=brevo`
  (D-189) they arrive in the student's real inbox via the Brevo HTTPS API.
  With `MAIL_PROVIDER=smtp` (Gmail) they do NOT arrive on Render Free —
  outbound SMTP is blocked. With the `log` provider no email is sent at
  all — and since 2026-11-24 codes are never printed in the backend log
  either, so a demo without a configured provider needs the admin to
  approve accounts manually. Phone OTPs still have no free delivery path
  (they need a paid SMS gateway); the phone OTP falls back to email when SMS fails.
- **Admin demo**: sign in at `/admin/login` with the `INITIAL_ADMIN_*`
  credentials, complete the forced password change + TOTP setup, create
  a moderator/university data as needed, publish an event — it appears
  on the student Events page immediately.
- **Photos persist**: profile/cover uploads are stored in TiDB
  (`stored_files`), so they survive Render restarts and redeploys —
  verify by uploading, then using **Render → Manual Deploy / Restart**
  and reloading the profile.
- **GitHub Workspace** stays in its honest "not configured" state until
  a GitHub OAuth app is registered (`GITHUB_CLIENT_ID/SECRET`); that is
  optional for the demo.

## Acceptance checklist (public URLs)

- [ ] `GET https://<service>.onrender.com/api/health` → 200
- [ ] Pages URL loads the student app; register a student (OTP from
      Render logs), verify, log in
- [ ] Admin login + forced password change works
- [ ] Edit profile; upload profile photo and cover; restart the Render
      service; photos still display
- [ ] Post to the feed; react; comment; reply
- [ ] Admin publishes an event (and a draft stays invisible); the
      published event appears for the student; register interest
- [ ] All of the above with your PC off — everything runs on
      Cloudflare + Render + TiDB
