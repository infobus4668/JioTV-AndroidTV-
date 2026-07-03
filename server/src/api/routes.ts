import type { FastifyInstance, FastifyReply } from "fastify";
import { sendOtp, verifyOtp } from "../jio/auth";
import { getStoredCredentials, saveCredentials, clearCredentials } from "../store/db";
import {
  isAdminConfigured, isAuthEnabled, verifyAdminPassword, setAdminPassword, disableAuth, ensureServerToken,
} from "../store/settings";
import { refreshNow } from "../refresh";
import { sessions, requireAdmin, requireServerToken } from "./auth";
import { randomBytes } from "node:crypto";

function startSession(reply: FastifyReply) {
  const sid = randomBytes(24).toString("hex");
  sessions.add(sid);
  reply.setCookie("admin_session", sid, { httpOnly: true, sameSite: "lax", path: "/", maxAge: 60 * 60 * 24 * 7 });
}

export async function registerRoutes(app: FastifyInstance): Promise<void> {
  // ── Public health (no secrets) ──
  app.get("/api/status", async () => {
    const c = getStoredCredentials();
    return { ok: true, hasCredentials: !!c };
  });

  // ── First-run setup (browser, no .env needed) ──
  // Tells the SPA whether to show the setup wizard, and whether login is required.
  app.get("/api/setup/state", async () => ({
    needsSetup: !isAdminConfigured(),
    authEnabled: isAuthEnabled(),
  }));

  // First run only. Either set a password, or choose "no password" (disableAuth) for an open LAN box.
  app.post("/api/setup", async (req, reply) => {
    if (isAdminConfigured()) return reply.code(403).send({ error: "Already set up" });
    const { password, disableAuth: noAuth } = (req.body ?? {}) as { password?: string; disableAuth?: boolean };
    if (noAuth) {
      disableAuth();
    } else {
      if (!password || password.length < 4) {
        return reply.code(400).send({ error: "Choose a password of at least 4 characters (or pick no password)" });
      }
      setAdminPassword(password);
    }
    const serverToken = ensureServerToken();
    startSession(reply);
    return { ok: true, serverToken };
  });

  // Toggle auth later from the dashboard.
  app.post("/api/admin/set-password", { preHandler: requireAdmin }, async (req, reply) => {
    const { password } = (req.body ?? {}) as { password?: string };
    if (!password || password.length < 4) {
      return reply.code(400).send({ error: "Choose a password of at least 4 characters" });
    }
    setAdminPassword(password);
    startSession(reply); // keep the current browser signed in under the new password
    return { ok: true };
  });

  app.post("/api/admin/disable-auth", { preHandler: requireAdmin }, async () => {
    disableAuth();
    return { ok: true };
  });

  // ── Admin auth ──
  app.post("/api/admin/login", async (req, reply) => {
    const { password } = (req.body ?? {}) as { password?: string };
    if (!isAdminConfigured()) return reply.code(409).send({ error: "Not set up yet" });
    if (!password || !verifyAdminPassword(password)) {
      return reply.code(401).send({ error: "Wrong password" });
    }
    startSession(reply);
    return { ok: true };
  });

  app.post("/api/admin/logout", async (req, reply) => {
    const sid = (req.cookies as Record<string, string | undefined>)?.admin_session;
    if (sid) sessions.delete(sid);
    reply.clearCookie("admin_session", { path: "/" });
    return { ok: true };
  });

  // ── Admin: account status + setup ──
  app.get("/api/admin/status", { preHandler: requireAdmin }, async () => {
    const c = getStoredCredentials();
    return {
      loggedIn: !!c,
      mobile: c?.mobile ?? "",
      updatedAt: c?.updatedAt ?? 0,
    };
  });

  // The TV access token to paste into each device (generated on setup, stored in config.json).
  app.get("/api/admin/config", { preHandler: requireAdmin }, async () => ({
    serverToken: ensureServerToken(),
  }));

  app.post("/api/login/otp/send", { preHandler: requireAdmin }, async (req, reply) => {
    const { mobile } = (req.body ?? {}) as { mobile?: string };
    if (!mobile || mobile.replace(/\D/g, "").length < 10) {
      return reply.code(400).send({ error: "Enter a valid 10-digit mobile number" });
    }
    try {
      await sendOtp(mobile);
      return { ok: true };
    } catch (err) {
      return reply.code(502).send({ error: (err as Error).message });
    }
  });

  app.post("/api/login/otp/verify", { preHandler: requireAdmin }, async (req, reply) => {
    const { mobile, otp } = (req.body ?? {}) as { mobile?: string; otp?: string };
    if (!mobile || !otp) return reply.code(400).send({ error: "Mobile and OTP are required" });
    try {
      const auth = await verifyOtp(mobile, otp);
      saveCredentials(auth, mobile, Date.now());
      return { ok: true };
    } catch (err) {
      return reply.code(502).send({ error: (err as Error).message });
    }
  });

  app.post("/api/admin/refresh", { preHandler: requireAdmin }, async (_req, reply) => {
    const ok = await refreshNow();
    if (!ok) return reply.code(400).send({ error: "Refresh failed or no credentials" });
    return { ok: true };
  });

  app.post("/api/admin/logout-jio", { preHandler: requireAdmin }, async () => {
    clearCredentials();
    return { ok: true };
  });

  // ── Machine endpoint: TVs pull the shared credentials here (bearer token) ──
  app.get("/api/credentials", { preHandler: requireServerToken }, async (_req, reply) => {
    const c = getStoredCredentials();
    if (!c) return reply.code(404).send({ error: "No active login on the server yet" });
    return {
      ssoToken: c.ssoToken,
      authToken: c.authToken,
      crmid: c.crmid,
      uniqueId: c.uniqueId,
      deviceId: c.deviceId,
      userId: c.userId,
    };
  });
}
