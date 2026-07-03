import { Readable } from "node:stream";
import type { FastifyInstance } from "fastify";
import { requireAdmin } from "./auth";
import { getChannels } from "../jio/channels";
import { getNativeEpg } from "../jio/epg";
import { getPlaybackInfo, proxyUpstream, proxyLicense, rewriteManifest } from "../proxy/streamProxy";

/**
 * Web-player endpoints (guarded by the admin session — the browser player is an admin feature; the
 * TVs play directly and don't use these).
 */
export async function registerPlayRoutes(app: FastifyInstance): Promise<void> {
  app.get("/api/channels", { preHandler: requireAdmin }, async () => ({
    channels: await getChannels(),
  }));

  app.get<{ Params: { id: string } }>(
    "/api/epg/:id",
    { preHandler: requireAdmin },
    async (req) => ({ programs: await getNativeEpg(req.params.id) })
  );

  // Manifest URL + DRM flags the player feeds to Shaka.
  app.get<{ Params: { id: string } }>(
    "/api/play/:id",
    { preHandler: requireAdmin },
    async (req, reply) => {
      try {
        return await getPlaybackInfo(req.params.id);
      } catch (err) {
        return reply.code(502).send({ error: (err as Error).message });
      }
    }
  );

  // Generic upstream proxy (manifest + segments), token injected server-side.
  app.get<{ Querystring: { cid?: string; u?: string } }>(
    "/api/proxy",
    { preHandler: requireAdmin },
    async (req, reply) => {
      const { cid, u } = req.query;
      if (!cid || !u) return reply.code(400).send({ error: "cid and u are required" });
      try {
        const target = decodeURIComponent(u);
        const upstream = await proxyUpstream(cid, target, req.headers["range"] as string | undefined);
        const contentType = upstream.headers.get("content-type") ?? "";

        // Manifests are small: buffer + rewrite URLs so the browser resolves media via the proxy.
        const looksManifest =
          contentType.toLowerCase().includes("mpegurl") ||
          contentType.toLowerCase().includes("dash+xml") ||
          /\.(mpd|m3u8)(\?|$)/i.test(target);
        if (looksManifest) {
          const text = await upstream.text();
          const rewritten = rewriteManifest(text, contentType, target, cid);
          reply.code(upstream.status);
          if (contentType) reply.header("content-type", contentType);
          return reply.send(rewritten ?? text);
        }

        reply.code(upstream.status);
        for (const h of ["content-type", "content-length", "accept-ranges", "content-range"]) {
          const v = upstream.headers.get(h);
          if (v) reply.header(h, v);
        }
        if (!upstream.body) return reply.send();
        return reply.send(Readable.fromWeb(upstream.body as any));
      } catch (err) {
        return reply.code(502).send({ error: (err as Error).message });
      }
    }
  );

  // Widevine license proxy (raw challenge in, license bytes out).
  app.post<{ Params: { id: string } }>(
    "/api/play/:id/license",
    { preHandler: requireAdmin },
    async (req, reply) => {
      try {
        const challenge = req.body as Buffer;
        const license = await proxyLicense(req.params.id, challenge);
        reply.header("content-type", "application/octet-stream");
        return reply.send(license);
      } catch (err) {
        return reply.code(502).send({ error: (err as Error).message });
      }
    }
  );
}
