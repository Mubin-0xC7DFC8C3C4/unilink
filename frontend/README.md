# UniLink Frontend

React 18 + TypeScript (strict) + Vite. Two entries: `index.html` (student) and `admin.html` (admin, served at /admin).

## Run

```bash
npm install
npm run dev      # Vite on :5173, proxies /api and /ws to http://localhost:8080
npm run build    # tsc + vite build (both entries)
npm run typecheck
npm run lint
npm test         # Vitest
npm run test:tokens  # fails on raw hex outside src/design/tokens.css
```

## Design

Tokens only from `src/design/tokens.css` (generated from DESIGN.md). Light on `:root`, dark on `[data-theme="dark"]`. Theme hint only is stored in localStorage before login; all authoritative state is server-side. `text-muted` is used only for non-essential captions (WCAG AA, see DESIGN.md).

## Backend

Same-origin `/api` (dev proxy to :8080). CSRF: reads `XSRF-TOKEN` cookie, sends `X-XSRF-TOKEN`. Errors use the backend `ApiError` shape. If an endpoint is not implemented yet, screens show an honest "Not available yet" state — no mock data is shipped as real.
