import { defineConfig } from 'vitest/config';

// Dev: the game server runs on :8080 (./mvnw ... java -jar game-server/target/*-exec.jar).
// Proxying /ws keeps the browser on one origin, so no CORS or origin rules are needed in development.
export default defineConfig({
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/ws': { target: 'ws://localhost:8080', ws: true },
      '/api': { target: 'http://localhost:8080' },
    },
  },
  test: {
    environment: 'node',
  },
});
