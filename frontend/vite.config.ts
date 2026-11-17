import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
const root = new URL('.', import.meta.url).pathname;
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true }, '/ws': { target: 'http://localhost:8080', ws: true, changeOrigin: true } } },
  build: { rollupOptions: { input: { main: root + 'index.html', admin: root + 'admin.html' } } },
});
