# Frontend Report — UniLink Student App + Admin Portal

**Date:** 2026-11-17
**Scope:** `~/workspace/unilink/frontend/` only. No backend, ops or Phase 1 docs touched (except this report).
**Sources read:** `docs/design/DESIGN.md`, `docs/UNILINK_MASTER_BUILD_GUIDE.md` §3/§10, `docs/architecture/ARCHITECTURE_OVERVIEW.md` §4, `docs/phase-reports/01-addendum.md`. Product name UniLink everywhere; UniConnect branding and the browser-local-storage model from `pic.docx` were NOT copied.

## What was built

- **Stack:** Vite 5 + React 18 + TypeScript strict, React Router 6, TanStack Query 5, React Hook Form + Zod, Vitest + Testing Library, Playwright scaffolded (`e2e/smoke.spec.ts`, `playwright.config.ts`).
- **Two entries (verified in build output):** `index.html` (student, `src/main.tsx`) and `admin.html` (admin, `src/admin/main.tsx`, `BrowserRouter basename="/admin"`). `vite.config.ts` has both inputs; dev proxy `/api` + `/ws` → `http://localhost:8080`.
- **Design tokens:** `src/design/tokens.css` generated from DESIGN.md — light on `:root`, dark on `[data-theme="dark"]`, exact token values, typography/spacing/radii as specified. Theme from `prefers-color-scheme`, toggle in both apps; only a theme *hint* is kept in localStorage before login. `text-muted` used only for non-essential captions (eyebrow/footer); essential info, errors and status use `text-secondary`/`text-primary`. No gradients, shadows, glassmorphism, decorative icons, fake charts or invented colours. `npm run test:tokens` (script `scripts/check-tokens.mjs`) passes — no raw hex outside `tokens.css` (also verified by grep).
- **Primitives (`src/design/`):** Button (pill / pill-primary), Field/Input, Modal, StatStrip (real numbers only), RuledList, EmptyState, Skeleton, Toast, Pill/VerifiedBadge (outlined pill + ✓ + word "Verified"), plus `ApiUnavailable`, `ErrorState`, `PageHeader` (eyebrow + one heading + one primary action).
- **API client (`src/api/`):** fetch-based typed client, `credentials: 'include'`, CSRF read `XSRF-TOKEN` cookie → send `X-XSRF-TOKEN`, error mapping for 401/403/404/409/422/429 + network errors using backend `ApiError` shape `{timestamp,status,code,message,path,traceId,fieldErrors[]}`.
- **Student pages (all call the real API; every button performs its operation or is honestly disabled):** Landing, SignUp (3-step, FR-AUTH-01 full field list incl. ADDRESS, student ID, ID-card image, PROFILE PHOTO; password 8+ upper/lower/digit via Zod), OTP step, email-verification page, Pending Review / Rejected+resubmit, Login, Forgot Password (forgot → verify-otp → reset), Feed (level switcher Department default / University / Hub / National + sector filter + keyset pagination with Next/Previous cursors + post composer), Post detail (comments with @mention autocomplete via datalist, reactions, bookmarks), Events list (Upcoming / Past archive tabs + create: Seminar/Workshop/Competition/Hackathon, visibility selector, deadline) + Event detail (INTERESTED/GOING, Looking for Teammates toggle, team request, external reg_link/prizes/eligibility display), Calendar (month picker), Teams (join request, accept requests), Search (people/posts, keyword/sector/type), Profiles (own + others, projects/education/achievements/skills/links, follow, activity feed), Messages (room list by type, chat window with read ticks ✓/✓✓, send), Notifications centre + preferences, Saved page, Settings (profile edit, privacy toggles, theme, delete account with password re-auth in a modal).
- **Admin pages (`src/admin/`, separate bundle, left rail, dense tables, split review pane):** Admin login (12-char minimum) + TOTP enrolment/verify, Dashboard (real counts via StatStrip), Verification queue (filters, review pane: entered data, OCR text+score, previous attempts, email-domain hint, approve/reject with mandatory reason, keyboard shortcut A to approve), Users (search, suspend/ban/reactivate/revoke/force-reset with reason), Moderation queue (hide/remove/restore/request-edit/warn), Appeals (uphold/overturn), Events moderation, Announcements (list + publish), Master data (universities / department-catalog / departments / batches / sectors tabs), Moderators, Analytics (real aggregates only), Audit log viewer (read-only, filters, export link).

## Commands run + real results (2026-11-17, this VM, Node 24.22.1 / npm 10.9.4)

| Command | Result |
|---|---|
| `npm install` | PASS — 300 packages added in 29s |
| `npm run typecheck` (`tsc -b --noEmit`) | PASS (clean, after fixing 7 initial errors) |
| `npm run lint` (`eslint src`) | PASS (clean, after fixing 4 unused-import errors) |
| `npm test` (Vitest) | PASS — 1 file, **11/11 tests** (primitives, sign-up validation incl. weak/strong password, theme switching, error mapping 401/403/404/429) |
| `npm run test:tokens` | PASS — "no raw hex outside src/design/tokens.css" |
| `npm run build` | PASS — 135 modules, `dist/index.html` + `dist/admin.html` both emitted (main 39.67 kB, admin 20.59 kB, shared 305 kB) |
| `npm run dev` smoke check | PASS — `GET /` 200, `GET /admin.html` 200; `GET /api/feed` via proxy returned 500 because **the backend was not reachable** (see Blocked) |
| `curl http://localhost:8080/actuator/health` (before build) | Backend not reachable — no backend code existed in `~/workspace/unilink/backend/` at that time (parallel agent) |

## Completed
Scaffold, both entries, tokens + theme in both themes, primitives, typed API client + CSRF + error mapping, all student and admin screens listed above wired to the documented contract, loading/empty/error states on every list screen, Vitest suite, token grep test, `typecheck`/`lint`/`build` green.

## Partially completed
- **Live end-to-end verification:** no screen has been exercised against a live backend (backend was down / not yet built when tested). All API calls follow the documented contract, and screens that receive 404 show an honest "Not available yet" state instead of fake data — but no 200 response has been observed for any endpoint.
- **Messages / realtime:** room list, send and read-marking are wired over REST; no live STOMP client is implemented yet (contract lists STOMP `/ws`; the page notes it). Reconnect/backoff/resync are therefore not implemented.
- **Verification review pane:** ID image is not rendered — the contract gives no authorised-stream URL for it; the pane says so honestly and shows OCR text/score when the backend returns them.
- **Playwright:** smoke spec scaffolded only; not run (no live browser in this subagent environment).

## Not implemented
- STOMP/WebSocket live client (see above).
- Account deletion relies on `POST /me/delete`, and verification status on `GET /verification/status` — neither path is spelled out in the task's contract; they are clearly-marked assumptions, not invented successes (they surface the honest unavailable/error state if the backend returns 404).
- No mock data is shipped as real anywhere; no `localStorage` source of truth (theme hint only).

## Blocked and why
- **Backend unavailable during testing:** `~/workspace/unilink/backend/` was empty and `http://localhost:8080` did not respond at test time (parallel backend agent). Consequence: no endpoint could be verified live; every screen's live behaviour is unproven until the backend is up. Re-run `npm run dev` + Playwright (`npm run e2e`) against the running backend to close this.
- **Design gaps (D-040, pre-existing):** font family, accent/status/focus colours remain unconfirmed in DESIGN.md — none were invented; status is carried by text/icons/shape as instructed.

## Files created (47 under `src/`, plus config)
`package.json`, `vite.config.ts`, `vitest.config.ts`, `tsconfig.json`, `index.html`, `admin.html`, `.env.example`, `.gitignore`, `eslint.config.mjs`, `playwright.config.ts`, `README.md`, `scripts/check-tokens.mjs`, `e2e/smoke.spec.ts`, `src/design/{tokens.css,theme.ts,primitives.tsx}`, `src/api/{client.ts,types.ts}`, `src/app/{Layout,ThemeContext,auth}.tsx`, `src/main.tsx`, `src/admin/{main.tsx,AdminLayout.tsx,AdminList.tsx}` + 12 admin pages, `src/features/{auth (7), feed, posts (2), events (3), teams, search, profile, messages, notifications, settings, shared}.tsx`, `src/test/{setup.ts,primitives.test.tsx}`. `node_modules/` (161 MB) is git-ignored, not committed.

## Run it
```bash
cd ~/workspace/unilink/frontend
npm install
npm run dev      # :5173, proxies /api and /ws to http://localhost:8080
npm run build    # tsc + vite build, both entries
npm run typecheck && npm run lint && npm test && npm run test:tokens
```
