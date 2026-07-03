import { timingSafeEqual } from "node:crypto";
import type { FastifyReply, FastifyRequest } from "fastify";
import { config } from "../config";

/** In-memory admin sessions (reset on restart — fine for a single self-hosted instance). */
export const sessions = new Set<string>();

export function safeEqual(a: string, b: string): boolean {
  const ab = Buffer.from(a);
  const bb = Buffer.from(b);
  if (ab.length !== bb.length) return false;
  return timingSafeEqual(ab, bb);
}

/** Guards the admin dashboard + web-player endpoints (browser session cookie). */
export function requireAdmin(req: FastifyRequest, reply: FastifyReply, done: () => void) {
  const sid = (req.cookies as Record<string, string | undefined>)?.admin_session;
  if (!sid || !sessions.has(sid)) {
    reply.code(401).send({ error: "Not authenticated" });
    return;
  }
  done();
}

/** Guards the machine endpoint the TVs use to pull credentials (bearer token). */
export function requireServerToken(req: FastifyRequest, reply: FastifyReply, done: () => void) {
  const header = req.headers["authorization"] ?? "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : "";
  if (!config.serverToken || !safeEqual(token, config.serverToken)) {
    reply.code(401).send({ error: "Invalid access token" });
    return;
  }
  done();
}
