// UniLink — Cloudflare Pages Function: single-origin API proxy.
//
// The Pages project's _redirects proxy rules for /api/* are not applied by
// Cloudflare (verified live 2026-11-23: /api/health returned the SPA's
// index.html while the SPA fallback and the new bundle deployed correctly),
// so the proxy is implemented here instead. Pages Functions run before
// static assets and _redirects, and returning the upstream fetch response
// preserves its status and headers verbatim — including Set-Cookie, which
// keeps session + CSRF cookies bound to the Pages origin exactly as in the
// single-origin design (no CORS, no cross-site cookies).
//
// The backend serves the same /api/* paths, so only the origin changes.
//
// Wake-up retries (2026-11-23): the Render free tier spins the backend
// down after ~15 idle minutes. While it wakes (which can take a minute or
// more), Render's edge answers 502 Bad Gateway, and the first page load
// after an idle period failed outright — e.g. the signup catalogue
// (GET /api/public/universities) showed "Could not load universities".
// For idempotent GET/HEAD requests this function now retries a 502/503/
// 504 a few times with backoff, riding out the wake-up window. Writes are
// never retried (a re-sent POST could double-submit if the first attempt
// did reach the app).

const RETRYABLE_STATUSES = new Set([502, 503, 504]);
const RETRY_DELAYS_MS = [4000, 10000, 20000];

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

export async function onRequest(context) {
  const url = new URL(context.request.url);
  url.protocol = "https:";
  url.hostname = "unilink-api-243s.onrender.com";
  url.port = "";
  const target = url.toString();

  const method = context.request.method;
  const mayRetry = method === "GET" || method === "HEAD";

  let lastError;
  for (let attempt = 0; attempt <= RETRY_DELAYS_MS.length; attempt++) {
    if (attempt > 0) await sleep(RETRY_DELAYS_MS[attempt - 1]);
    try {
      const resp = await fetch(target, context.request);
      if (!mayRetry || !RETRYABLE_STATUSES.has(resp.status)) return resp;
      // Wake-up window: discard this response and try again after a
      // delay. The final attempt returns whatever the upstream gives.
      if (attempt === RETRY_DELAYS_MS.length) return resp;
      try { await resp.body?.cancel(); } catch { /* best effort */ }
    } catch (err) {
      lastError = err;
      if (!mayRetry || attempt === RETRY_DELAYS_MS.length) throw err;
    }
  }
  throw lastError;
}
