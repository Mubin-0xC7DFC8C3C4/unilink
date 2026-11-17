import { defineConfig, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';
const root = new URL('.', import.meta.url).pathname;
/** Dev-only: nested admin routes (/admin/login, /admin/verifications, ...) must be
 *  served by admin.html, whose router uses basename "/admin". Without this, Vite's
 *  SPA fallback serves index.html for them and the student app shows "Page not found".
 *  Production (Nginx) needs the equivalent rewrite: /admin/* -> admin.html. */
const adminSpaFallback: Plugin = {
  name: 'admin-spa-fallback',
  configureServer(server) {
    // No @types/node in this project, so the request is typed structurally.
    server.middlewares.use((req: { url?: string }, _res: unknown, next: () => void) => {
      const raw = req.url ?? '';
      const path = raw.split('?')[0];
      if (path.startsWith('/admin/') && !/\.[a-z0-9]+$/i.test(path)) {
        req.url = '/admin.html' + raw.slice(path.length);
      }
      next();
    });
  },
};
export default defineConfig({
  plugins: [react(), adminSpaFallback],
  server: { port: 5173, proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true }, '/ws': { target: 'http://localhost:8080', ws: true, changeOrigin: true } } },
  build: { rollupOptions: { input: { main: root + 'index.html', admin: root + 'admin.html' } } },
});
