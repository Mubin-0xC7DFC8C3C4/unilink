import { ApiError, type ApiErrorBody } from './types';
function csrfToken(): string | undefined { const m = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/); return m ? decodeURIComponent(m[1]) : undefined; }
export function isNetworkError(e: unknown): boolean { return e instanceof TypeError || (e instanceof ApiError && e.status===0); }
export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers: Record<string,string> = { ...(init.headers as Record<string,string>||{}) };
  const isForm = init.body instanceof FormData;
  if (!isForm && init.body) headers['Content-Type']='application/json';
  const csrf = csrfToken(); if (csrf) headers['X-XSRF-TOKEN']=csrf;
  let res: Response;
  try { res = await fetch(`/api${path}`, { ...init, headers, credentials: 'include' }); }
  catch { throw new ApiError(0, { code:'NETWORK_ERROR', message:'Network error — the server could not be reached. Check your connection and try again.' }); }
  if (res.status===204) return undefined as T;
  const text = await res.text(); let body: ApiErrorBody & Record<string,unknown> = {};
  try { body = text ? JSON.parse(text) : {}; } catch { body = { message: text }; }
  if (!res.ok) throw new ApiError(res.status, body as ApiErrorBody, `Request failed (${res.status})`);
  return body as T;
}
export const apiGet = <T,>(p:string)=>api<T>(p);
export const apiPost = <T,>(p:string, data?: unknown)=>api<T>(p,{method:'POST', body: data instanceof FormData? data : data!==undefined? JSON.stringify(data): undefined});
export const apiPatch = <T,>(p:string, data?: unknown)=>api<T>(p,{method:'PATCH', body: JSON.stringify(data)});
export const apiPut = <T,>(p:string, data?: unknown)=>api<T>(p,{method:'PUT', body: data!==undefined? JSON.stringify(data): undefined});
export const apiDelete = <T,>(p:string)=>api<T>(p,{method:'DELETE'});
export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) { if(e.status===401) return 'You are not signed in. Please log in and try again.'; if(e.status===403) return 'You do not have permission to do that.'; if(e.status===404) return 'That item was not found. It may have been removed, or the backend endpoint is not available yet.'; if(e.status===409) return e.message||'This conflicts with existing data.'; if(e.status===422) return e.message||'The data was not accepted. Check the form and try again.'; if(e.status===429) return 'Too many requests. Please wait a moment and try again.'; if(e.status===0) return e.message; return e.message; }
  return 'Something went wrong. Please try again.';
}
