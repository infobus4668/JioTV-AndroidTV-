import { timingSafeEqual } from "node:crypto";
import type { FastifyReply, FastifyRequest } from "fastify";
import { envServerToken, isAuthEnabled } from "../store/settings";
import { hasCode } from "../store/db";

/** In-memory admin sessions (reset on restart — fine for a single self-hosted instance). */
export const sessions = new Set<string>();

export function safeEqual(a: string, b: string): boolean {
  const ab = Buffer.from(a);
  const bb = Buffer.from(b);
  if (ab.length !== bb.length) return false;
  return timingSafeEqual(ab, bb);
}

/** Guards the admin dashboard + web-player endpoints (browser session cookie). Open when auth is off. */
export function requireAdmin(req: FastifyRequest, reply: FastifyReply, done: () => void) {
  if (!isAuthEnabled()) return done();
  const sid = (req.cookies as Record<string, string | undefined>)?.admin_session;
  if (!sid || !sessions.has(sid)) {
    reply.code(401).send({ error: "Not authenticated" });
    return;
  }
  done();
}

/** Guards the machine endpoint the TVs use to pull credentials. Open when auth is off; otherwise a
 *  valid access code (any of the named codes) or the optional env token is required. */
export function requireServerToken(req: FastifyRequest, reply: FastifyReply, done: () => void) {
  if (!isAuthEnabled()) return done();
  const header = req.headers["authorization"] ?? "";
  const token = (header.startsWith("Bearer ") ? header.slice(7) : "").trim();
  const env = envServerToken();
  const ok = (env && safeEqual(token, env)) || hasCode(token) || hasCode(token.toUpperCase());
  if (!ok) {
    reply.code(401).send({ error: "Invalid access code" });
    return;
  }
  done();
}
