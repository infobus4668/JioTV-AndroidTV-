import path from "node:path";
import Fastify, { type FastifyInstance } from "fastify";
import cookie from "@fastify/cookie";
import fastifyStatic from "@fastify/static";
import { config } from "./config";
import { registerRoutes } from "./api/routes";
import { registerPlayRoutes } from "./api/play";
import { startRefreshScheduler } from "./refresh";
import { isAdminConfigured } from "./store/settings";
import { ensureCert } from "./https";

/** Builds a fully-wired Fastify instance (used for both the HTTP and HTTPS listeners). */
async function buildApp(extra?: Record<string, unknown>): Promise<FastifyInstance> {
  // `extra` may carry an `https` option; cast because that changes Fastify's inferred server type.
  const app = Fastify({ logger: { level: "info" }, bodyLimit: 8_388_608, ...(extra as any) }) as unknown as FastifyInstance;

  // Widevine license challenges arrive as raw binary.
  app.addContentTypeParser("application/octet-stream", { parseAs: "buffer" }, (_req, body, done) => done(null, body));

  await app.register(cookie);
  await registerRoutes(app);
  await registerPlayRoutes(app);

  const webRoot = path.join(__dirname, "..", "web", "dist");
  await app.register(fastifyStatic, { root: webRoot, prefix: "/" });

  // SPA fallback: any non-API route serves index.html so client routing works.
  app.setNotFoundHandler((req, reply) => {
    if (req.url.startsWith("/api")) return reply.code(404).send({ error: "Not found" });
    return reply.sendFile("index.html");
  });
  return app;
}

async function main() {
  if (!isAdminConfigured()) {
    console.log("ℹ  First run — open the web UI to create an admin password (no .env editing needed).");
  }

  startRefreshScheduler();

  // HTTP
  try {
    const http = await buildApp();
    await http.listen({ port: config.port, host: config.host });
    console.log(`JTV server (HTTP)  → http://${config.host}:${config.port}`);
  } catch (err) {
    console.error("Failed to start HTTP server:", err);
    process.exit(1);
  }

  // HTTPS (self-signed) — needed for browser Widevine/DRM off-localhost. Non-fatal if it can't bind.
  try {
    const { key, cert } = ensureCert();
    const https = await buildApp({ https: { key, cert } });
    await https.listen({ port: config.httpsPort, host: config.host });
    console.log(`JTV server (HTTPS) → https://${config.host}:${config.httpsPort}  (self-signed; accept the warning)`);
  } catch (err) {
    console.warn("HTTPS listener not started:", (err as Error).message);
  }
}

void main();
