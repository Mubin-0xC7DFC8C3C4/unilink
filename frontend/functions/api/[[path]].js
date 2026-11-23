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

export async function onRequest(context) {
  const url = new URL(context.request.url);
  url.protocol = "https:";
  url.hostname = "unilink-api-243s.onrender.com";
  url.port = "";
  return fetch(url.toString(), context.request);
}
