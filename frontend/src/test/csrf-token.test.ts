import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { apiPost, apiGet, resetCsrfTokenCache } from '../api/client';

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

// Production 2026-11-25: the backend XOR-masks the CSRF token, so the
// XSRF-TOKEN cookie holds the RAW token (rejected in the header) and the
// masked token from GET /api/csrf is what mutations must send. The
// client used to send the raw cookie value, so every UI mutation —
// including account deletion — failed with 403.
describe('CSRF token handling (api client)', () => {
  const RAW = '662e792f-11c0-4865-85a1-b6225118738f';
  const MASKED = 'Tmdd8QUILeaz0PgcFzzafqVHcdWUmQB-LUgPKqjCquW8td0YeFFvlDIxH4Ce4cl_JxHuRpNyXO2h-DFTT349GJ3zm92LhuV-';
  let calls: { url: string; init?: RequestInit }[];

  beforeEach(() => {
    resetCsrfTokenCache();
    calls = [];
    document.cookie = `XSRF-TOKEN=${RAW}`;
    vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
      calls.push({ url, init });
      if (url === '/api/csrf') return json({ token: MASKED, headerName: 'X-XSRF-TOKEN' });
      if (url === '/api/me/delete') {
        const sent = (init?.headers as Record<string, string> | undefined)?.['X-XSRF-TOKEN'];
        return sent === MASKED ? json({ deleted: true }) : json({ code: 'FORBIDDEN', message: 'Access denied' }, 403);
      }
      return json({});
    }));
  });
  afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); });

  it('sends the masked /api/csrf token on a mutation, never the raw cookie value', async () => {
    const res = await apiPost<{ deleted: boolean }>('/me/delete', { password: 'TestPass123' });
    expect(res.deleted).toBe(true);
    const del = calls.find((c) => c.url === '/api/me/delete');
    expect((del?.init?.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe(MASKED);
    expect((del?.init?.headers as Record<string, string>)['X-XSRF-TOKEN']).not.toBe(RAW);
    expect(calls.some((c) => c.url === '/api/csrf')).toBe(true);
  });

  it('caches the token: a second mutation does not refetch /api/csrf', async () => {
    await apiPost('/me/delete', { password: 'x' });
    await apiPost('/me/delete', { password: 'x' });
    expect(calls.filter((c) => c.url === '/api/csrf')).toHaveLength(1);
  });

  it('a GET never fetches /api/csrf', async () => {
    await apiGet('/public/universities');
    expect(calls.some((c) => c.url === '/api/csrf')).toBe(false);
  });

  it('on a 403 it refetches a fresh token and retries the mutation once', async () => {
    let first = true;
    vi.mocked(fetch).mockImplementation(async (input: Parameters<typeof fetch>[0], init?: RequestInit) => {
      const url = String(input);
      calls.push({ url, init });
      if (url === '/api/csrf') return json({ token: first ? MASKED : `${MASKED}2`, headerName: 'X-XSRF-TOKEN' });
      if (url === '/api/me/delete') {
        if (first) { first = false; return json({ code: 'FORBIDDEN', message: 'Access denied' }, 403); }
        return json({ deleted: true });
      }
      return json({});
    });
    const res = await apiPost<{ deleted: boolean }>('/me/delete', { password: 'TestPass123' });
    expect(res.deleted).toBe(true);
    expect(calls.filter((c) => c.url === '/api/csrf')).toHaveLength(2);
    expect(calls.filter((c) => c.url === '/api/me/delete')).toHaveLength(2);
  });

  it('when /api/csrf fails, the mutation still goes out without a token', async () => {
    vi.mocked(fetch).mockImplementation(async (input: Parameters<typeof fetch>[0]) => {
      const url = String(input);
      calls.push({ url });
      if (url === '/api/csrf') return json({ message: 'no token' }, 500);
      if (url === '/api/me/delete') return json({ deleted: true });
      return json({});
    });
    const res = await apiPost<{ deleted: boolean }>('/me/delete', { password: 'x' });
    expect(res.deleted).toBe(true);
  });
});
