import '@testing-library/jest-dom';
import { beforeEach } from 'vitest';
import { resetCsrfTokenCache } from '../api/client';
// The CSRF token cache is module state: clear it between tests so a
// token fetched under one test's fetch mock never leaks into another.
beforeEach(() => { resetCsrfTokenCache(); });
Object.defineProperty(window, 'matchMedia', { writable: true, value: (q: string) => ({ matches: false, media: q, onchange: null, addListener: () => {}, removeListener: () => {}, addEventListener: () => {}, removeEventListener: () => {}, dispatchEvent: () => false }) });
