import path from "node:path";
import Fastify from "fastify";
import cookie from "@fastify/cookie";
import fastifyStatic from "@fastify/static";
import { config } from "./config";
import { registerRoutes } from "./api/routes";
import { registerPlayRoutes } from "./api/play";
import { startRefreshScheduler } from "./refresh";
import { isAdminConfigured } from "./store/settings";

async function main() {
  if (!isAdminConfigured()) {
    console.log("ℹ  First run — open the web UI to create an admin password (no .env editing needed).");
  }

  const app = Fastify({ logger: { level: "info" }, bodyLimit: 8_388_608 });

  // Widevine license challenges arrive as raw binary.
  app.addContentTypeParser(
    "application/octet-stream",
    { parseAs: "buffer" },
    (_req, body, done) => done(null, body)
  );

  await app.register(cookie);
  await registerRoutes(app);
  await registerPlayRoutes(app);

  // Serve the built React SPA (web/dist). Run `npm --prefix web run build` (Docker does this).
  const webRoot = path.join(__dirname, "..", "web", "dist");
  await app.register(fastifyStatic, { root: webRoot, prefix: "/" });

  // SPA fallback: any non-API route serves index.html so client rendering works.
  app.setNotFoundHandler((req, reply) => {
    if (req.url.startsWith("/api")) return reply.code(404).send({ error: "Not found" });
    return reply.sendFile("index.html");
  });

  startRefreshScheduler();

  try {
    await app.listen({ port: config.port, host: config.host });
    console.log(`JTV server listening on http://${config.host}:${config.port}`);
  } catch (err) {
    app.log.error(err);
    process.exit(1);
  }
}

void main();
