import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

/**
 * Vite configuration.
 *
 * The proxy section rewrites requests from /api/* to the Spring Boot API
 * running at http://localhost:8081. This avoids CORS issues during development
 * — the browser sees all requests going to the same origin (the Vite dev server).
 *
 * Example: fetch('/api/logs/clusters/summary')
 *   → proxied to → http://localhost:8081/logs/clusters/summary
 */
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
})
