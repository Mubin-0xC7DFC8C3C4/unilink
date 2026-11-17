# Phase report 02 — Playwright smoke tests (frontend only)

Date: 2026-11-17 ~18:48 +06. Author: main agent (no subagent).

## Scope

Frontend-only smoke tests. The backend was NOT running (Java→MySQL is still
blocked; see MEMORY.md) and nothing in this report claims backend integration
works. The two tests check that the two app entries render in a real browser.

## Commands and results

Chromium install (approved by the owner at ~18:46 +06; first attempt at
~17:48 +06 had failed with a download error and a timed-out approval):

```
cd ~/workspace/unilink/frontend && npx playwright install chromium
# Chrome for Testing 159.0.8154.6 (playwright chromium v1255) -> ~/.cache/ms-playwright/chromium-1255
# + FFmpeg v1013 + Chrome Headless Shell v1255. INSTALL EXIT:0
```

Dev server (started before the install finished; reused by Playwright):

```
cd ~/workspace/unilink/frontend && npm run dev
# VITE v5.4.21 ready in 557 ms, http://localhost:5173/
# curl http://localhost:5173/ -> HTTP 200
```

Smoke tests (`frontend/e2e/smoke.spec.ts`, config `frontend/playwright.config.ts`):

```
npx playwright test --reporter=list,html
# 2 passed (2.2s): landing loads (572ms), admin login loads (409ms)
# Re-run after the config fix: 2 passed (2.6s)
```

Screenshots (all taken against the dev server, 2026-11-17 18:48 +06):

- `frontend/e2e/screenshots/landing.png` — student landing page.
- `frontend/e2e/screenshots/admin-login.png` — "Admin log in" with Email /
  Password fields and Log in button (verified by reading the image).
- `frontend/e2e/screenshots/admin-dashboard.png` — admin shell (Dashboard
  heading) with an honest "Something went wrong — Request failed (500)"
  alert, because the dashboard fetches real counts from the backend and no
  backend is running. That error state is correct behaviour, not a bug.

HTML report: `frontend/playwright-report/index.html` (generated, not committed).

## Problem found and fixed (debugging format)

**Problem.** First run: 1 passed, 1 failed. `admin login loads` timed out
waiting for a heading matching /Admin/i at `/admin`. The page had rendered
(the nav, "Admin Portal" paragraph and "Dashboard" h1 were present) but
there was no heading containing "Admin". After pointing the test at
`/admin/login` it failed again: the dev server served the *student* app
(`index.html`) for `/admin/login`, which showed "Page not found".

**Root cause.** Vite's dev-server SPA fallback serves `index.html` for
unknown paths. The multi-page build declares `admin.html` as a second
entry, and Vite serves it for `/admin` (extensionless `.html` resolution),
but `/admin/login` (and every other nested admin route) fell through to the
student app. So no nested admin route was reachable in local development.

**Files changed.**
- `frontend/vite.config.ts`: new dev-only `adminSpaFallback` plugin
  (`configureServer` middleware) rewriting `/admin/*` requests without a
  file extension to `/admin.html`, preserving the query string. Verified:
  `curl /admin/login` and `/admin/verifications` now serve
  `/src/admin/main.tsx`. Typing note: the project has no `@types/node` and
  tsconfig restricts `types` to `vitest/globals`, so `http.IncomingMessage`
  has no `url` in this project's type world; the middleware request is
  typed structurally (`{ url?: string }`) instead of adding a dependency.
- `frontend/e2e/smoke.spec.ts`: the admin test now goes to `/admin/login`
  and expects the "Admin log in" heading (the test's name), instead of
  going to `/admin` and expecting any heading containing "Admin".

**Tests executed / results.**
- `npx playwright test --reporter=list,html`: 2 passed (2.2s), re-run 2 passed (2.6s).
- `npx tsc --noEmit`: exit 0 (after fixing the middleware typing; the first
  version failed with TS2339 on `req.url`).
- `npm run build`: exit 0.
- `npm test -- --run` (Vitest): 16 passed (16).
- Backend `mvn test` was not re-run for this change (no backend files touched).

## Remaining issues / limitations

- **Frontend only.** Nothing here verifies the backend, the API, or any
  data flow. Registration, login, verification, messaging and account
  deletion are still untested against a live backend (blocked on the
  Java→MySQL interception).
- **Production serving needs the same rewrite.** There is no Nginx config in
  the repo yet; when one is written it must rewrite `/admin/*` to
  `admin.html`, or nested admin routes will 404 / fall through in production
  exactly as they did in dev. `vite preview` has the same gap.
- The admin dashboard's "Request failed (500)" is the dev proxy (`/api` ->
  `localhost:8080`) with no backend behind it; it will clear once the
  backend runs.
- `test-results/` and `playwright-report/` are generated artefacts, not committed.
