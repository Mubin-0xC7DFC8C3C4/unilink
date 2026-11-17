export type Theme = 'light'|'dark';
export function initialTheme(): Theme { if (typeof window==='undefined') return 'light'; const saved = localStorage.getItem('unilink-theme-hint'); if (saved==='light'||saved==='dark') return saved; return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark':'light'; }
export function applyTheme(t: Theme){ document.documentElement.setAttribute('data-theme', t); try{ localStorage.setItem('unilink-theme-hint', t);}catch{} }
export function toggleTheme(t: Theme): Theme { return t==='light'?'dark':'light'; }
