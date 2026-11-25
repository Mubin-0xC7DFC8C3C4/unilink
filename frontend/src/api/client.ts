import { ApiError, type ApiErrorBody } from './types';
/** CSRF token for mutations (production 2026-11-25): the backend's
 *  handler XOR-masks the token (Spring Security 6 default with
 *  CookieCsrfTokenRepository), so the XSRF-TOKEN cookie holds the RAW
 *  token — which the server rejects in the header. The masked token is
 *  what GET /api/csrf returns, and that is the value mutations must
 *  send. Sending the raw cookie value made every UI mutation (profile
 *  edits, posts, settings, account deletion) fail with 403. The masked
 *  value unmasks to the same raw token, so it stays valid while the
 *  session does; it is refreshed when a mutation is rejected. */
let csrfTokenCache: string | undefined;
export function resetCsrfTokenCache(): void { csrfTokenCache = undefined; }
async function fetchCsrfToken(): Promise<string | undefined> {
  if (csrfTokenCache) return csrfTokenCache;
  try {
    const res = await fetch('/api/csrf', { credentials: 'include' });
    if (!res.ok) return undefined;
    const body = (await res.json()) as { token?: unknown };
    if (typeof body.token === 'string' && body.token) { csrfTokenCache = body.token; return body.token; }
  } catch { /* no token: the mutation still goes out and the server decides */ }
  return undefined;
}
export function isNetworkError(e: unknown): boolean { return e instanceof TypeError || (e instanceof ApiError && e.status===0); }
export const SESSION_EXPIRED_KEY = 'unilink:session-expired';
export const SESSION_EXPIRED_MESSAGE = 'Your session has expired. Please log in again.';
/** Auth pages: a 401 here is a failed sign-in attempt (or an anonymous
 *  visitor), never an expired session — redirecting would loop or bounce
 *  a signed-out user away from the page they need. */
const AUTH_PATHS = ['/login', '/signup', '/otp', '/forgot-password', '/verify-email', '/pending', '/admin/login', '/admin/totp'];
/** API paths whose 401 is expected, not an expired session: the sign-in /
 *  TOTP calls themselves, the session-restore calls (AuthProvider), and
 *  CSRF priming. */
function isAuthApiPath(path: string): boolean {
  return path.startsWith('/auth/') || path.startsWith('/admin/auth/') || path === '/csrf' || path.startsWith('/public/');
}
/** Pure decision: where (if anywhere) a 401 from `apiPath` should send the
 *  user. Returns null when no redirect applies, so the caller can render
 *  the error in place instead. Exported for tests. */
export function sessionExpiredRedirectPath(apiPath: string, pathname: string): string | null {
  if (isAuthApiPath(apiPath)) return null;
  if (AUTH_PATHS.includes(pathname)) return null;
  return pathname.startsWith('/admin') ? '/admin/login' : '/login';
}
/** Records the expired-session flag (read once by the login pages) and
 *  navigates to the login page. No-op when no redirect applies. */
export function redirectOnSessionExpired(apiPath: string): void {
  if (typeof window === 'undefined') return;
  const dest = sessionExpiredRedirectPath(apiPath, window.location.pathname);
  if (!dest) return;
  try { window.sessionStorage.setItem(SESSION_EXPIRED_KEY, '1'); } catch { /* storage unavailable: the redirect still happens */ }
  try { window.location.assign(dest); } catch { /* jsdom / non-browser: flag is set, navigation is best-effort */ }
}
export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers: Record<string,string> = { ...(init.headers as Record<string,string>||{}) };
  const isForm = init.body instanceof FormData;
  if (!isForm && init.body) headers['Content-Type']='application/json';
  const method = (init.method ?? 'GET').toUpperCase();
  // /api/auth/**, /api/public/** and /api/csrf are CSRF-exempt, so a 403
  // there is never a stale token — no token fetch, no retry.
  const needsCsrf = method !== 'GET' && method !== 'HEAD' && !isAuthApiPath(path);
  const run = async (): Promise<Response> => {
    const h = { ...headers };
    if (needsCsrf) { const csrf = await fetchCsrfToken(); if (csrf) h['X-XSRF-TOKEN'] = csrf; }
    try { return await fetch(`/api${path}`, { ...init, headers: h, credentials: 'include' }); }
    catch { throw new ApiError(0, { code:'NETWORK_ERROR', message:'Network error — the server could not be reached. Check your connection and try again.' }); }
  };
  let res = await run();
  if (res.status === 403 && needsCsrf) {
    // The session may have rotated (e.g. after login) since the token
    // was cached: fetch a fresh token once and retry the mutation. A 403
    // means the server rejected the request before running it, so the
    // retry cannot double-apply a side effect.
    resetCsrfTokenCache();
    res = await run();
  }
  if (res.status===204) return undefined as T;
  const text = await res.text(); let body: ApiErrorBody & Record<string,unknown> = {};
  try { body = text ? JSON.parse(text) : {}; } catch {
    // A 2xx with a non-JSON body is not data: the request never reached
    // the API (e.g. a static host's SPA fallback answered with
    // index.html). Returning it as data made callers crash on the shape
    // ((data||[]).map is not a function — the whole app unmounted on the
    // signup page). Reject instead, so query/mutation error states show.
    if (res.ok) throw new ApiError(res.status, { code:'INVALID_RESPONSE', message:'The server returned an unreadable response. Please try again.' });
    body = { message: text };
  }
  if (!res.ok) { if (res.status===401) redirectOnSessionExpired(path); throw new ApiError(res.status, body as ApiErrorBody, `Request failed (${res.status})`); }
  return body as T;
}
export const apiGet = <T,>(p:string)=>api<T>(p);
export const apiPost = <T,>(p:string, data?: unknown)=>api<T>(p,{method:'POST', body: data instanceof FormData? data : data!==undefined? JSON.stringify(data): undefined});
export const apiPatch = <T,>(p:string, data?: unknown)=>api<T>(p,{method:'PATCH', body: JSON.stringify(data)});
export const apiPut = <T,>(p:string, data?: unknown)=>api<T>(p,{method:'PUT', body: data!==undefined? JSON.stringify(data): undefined});
export const apiDelete = <T,>(p:string)=>api<T>(p,{method:'DELETE'});
/** First field-validation message, when the server sent field errors but
 *  only a generic top-level message (GlobalExceptionHandler returns
 *  "Validation failed" with fieldErrors on 422). */
function firstFieldError(e: ApiError): string | undefined {
  const f = e.fieldErrors?.[0];
  return f ? `${f.field}: ${f.message}` : undefined;
}
/** The server's specific message, unless it is the generic "Validation
 *  failed" (422) or the client-side fallback ("Request failed (…)", which
 *  ApiError uses when the body carried no message) — then the first field
 *  error is the specific message. */
function serverMessage(e: ApiError): string | undefined {
  const hasServerMessage = !e.code.startsWith('HTTP_') && e.message && e.message !== 'Validation failed';
  if (hasServerMessage) return e.message;
  return firstFieldError(e);
}
export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.status===401) return SESSION_EXPIRED_MESSAGE;
    // D-094: a restricted session answers 403 PASSWORD_CHANGE_REQUIRED —
    // that is not a generic permission failure, so surface the server's
    // message (it names the fix: change the password).
    if (e.status===403) return e.code==='PASSWORD_CHANGE_REQUIRED' ? (e.message || 'You must change your password before continuing.') : 'You do not have permission to do that.';
    // A missing resource is a genuine not-found — never "the backend
    // endpoint is not available yet". That wording is reserved for the
    // EndpointUnavailable component, used only where an endpoint is
    // actually unimplemented (almost none after Phase 2).
    if (e.status===404) return 'That item was not found. It may have been removed.';
    if (e.status===429) return 'Too many requests. Please wait a moment and try again.';
    if (e.status===400 || e.status===409 || e.status===422) return serverMessage(e) || 'The data was not accepted. Check the form and try again.';
    if (e.status===0) return e.message;
    if (e.status>=500) return 'Something went wrong on the server. Please try again.';
    return e.message || 'Something went wrong. Please try again.';
  }
  return 'Something went wrong. Please try again.';
}
