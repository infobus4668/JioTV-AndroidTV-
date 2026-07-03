import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Builds the SPA into web/dist, which the Fastify server serves as static files.
// In dev, `npm run dev` proxies API calls to the Fastify server on :8080.
export default defineConfig({
  plugins: [react()],
  build: { outDir: "dist", emptyOutDir: true },
  server: {
    port: 5173,
    proxy: {
      "/api": "http://localhost:8080",
    },
  },
});
