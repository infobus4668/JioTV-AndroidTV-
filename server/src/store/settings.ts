import fs from "node:fs";
import path from "node:path";
import { randomBytes, scryptSync, timingSafeEqual } from "node:crypto";
import { config } from "../config";

/**
 * File-backed server config so login/setup is done in the browser — no .env editing required
 * (same philosophy as jiotv_go's credentials file). Persists the admin password hash + the TV access
 * token to `<DATA_DIR>/config.json`. Env vars (ADMIN_PASSWORD / JTV_SERVER_TOKEN) still work as
 * overrides for advanced/headless setups and take precedence over the file.
 */

interface FileConfig {
  adminPasswordHash?: string;
  serverToken?: string;
  // When true, the user explicitly chose "no password" — the dashboard and TV endpoints are open.
  authDisabled?: boolean;
}

const file = path.join(config.dataDir, "config.json");
let cache: FileConfig | null = null;

function load(): FileConfig {
  if (cache) return cache;
  try {
    cache = JSON.parse(fs.readFileSync(file, "utf8")) as FileConfig;
  } catch {
    cache = {};
  }
  return cache;
}

function save(c: FileConfig): void {
  cache = c;
  fs.mkdirSync(config.dataDir, { recursive: true });
  fs.writeFileSync(file, JSON.stringify(c, null, 2));
}

function hashPassword(pw: string): string {
  const salt = randomBytes(16);
  const hash = scryptSync(pw, salt, 64);
  return `scrypt$${salt.toString("hex")}$${hash.toString("hex")}`;
}

function verifyHash(pw: string, stored: string): boolean {
  const [alg, saltHex, hashHex] = stored.split("$");
  if (alg !== "scrypt" || !saltHex || !hashHex) return false;
  const expected = Buffer.from(hashHex, "hex");
  const actual = scryptSync(pw, Buffer.from(saltHex, "hex"), expected.length);
  return actual.length === expected.length && timingSafeEqual(actual, expected);
}

function eqConst(a: string, b: string): boolean {
  const ab = Buffer.from(a);
  const bb = Buffer.from(b);
  return ab.length === bb.length && timingSafeEqual(ab, bb);
}

/** True once setup is done — a password exists (env/file) OR the user chose "no password". */
export function isAdminConfigured(): boolean {
  const f = load();
  return !!(config.adminPassword || f.adminPasswordHash || f.authDisabled);
}

/**
 * Whether auth is actually enforced. An env ADMIN_PASSWORD always forces it on; otherwise it's on
 * only when a file password exists and the user hasn't chosen "no password".
 */
export function isAuthEnabled(): boolean {
  if (config.adminPassword) return true;
  const f = load();
  if (f.authDisabled) return false;
  return !!f.adminPasswordHash;
}

export function verifyAdminPassword(pw: string): boolean {
  if (config.adminPassword) return eqConst(pw, config.adminPassword);
  const h = load().adminPasswordHash;
  return h ? verifyHash(pw, h) : false;
}

/** Sets a password (turns auth ON). */
export function setAdminPassword(pw: string): void {
  const c = load();
  c.adminPasswordHash = hashPassword(pw);
  c.authDisabled = false;
  save(c);
}

/** Turns auth OFF — open dashboard + open TV endpoint (home-LAN convenience). */
export function disableAuth(): void {
  const c = load();
  c.authDisabled = true;
  delete c.adminPasswordHash;
  save(c);
}

/** The effective TV access token (env override, else the file value, else ""). */
export function getServerToken(): string {
  return config.serverToken || load().serverToken || "";
}

/** Returns the token, generating + persisting one on first use (unless an env override is set). */
export function ensureServerToken(): string {
  if (config.serverToken) return config.serverToken;
  const c = load();
  if (!c.serverToken) {
    c.serverToken = randomBytes(24).toString("hex");
    save(c);
  }
  return c.serverToken;
}
