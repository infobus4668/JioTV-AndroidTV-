import fs from "node:fs";
import path from "node:path";
import Database from "better-sqlite3";
import { config } from "../config";
import type { AuthData } from "../jio/types";

/**
 * Tiny SQLite-backed store. v1 holds a single shared Jio account (id = 1) plus its mobile number and
 * the last-refresh timestamp. Multi-account is a Phase 3 extension (add a profile id).
 */

fs.mkdirSync(config.dataDir, { recursive: true });
const db = new Database(path.join(config.dataDir, "jtv.sqlite"));
db.pragma("journal_mode = WAL");

db.exec(`
  CREATE TABLE IF NOT EXISTS credentials (
    id           INTEGER PRIMARY KEY CHECK (id = 1),
    mobile       TEXT,
    ssoToken     TEXT,
    authToken    TEXT,
    crmid        TEXT,
    uniqueId     TEXT,
    deviceId     TEXT,
    userId       TEXT,
    updated_at   INTEGER
  );
  CREATE TABLE IF NOT EXISTS favorites (
    channel_id TEXT PRIMARY KEY
  );
`);

export interface StoredCredentials extends AuthData {
  mobile: string;
  updatedAt: number;
}

const selectStmt = db.prepare("SELECT * FROM credentials WHERE id = 1");
const upsertStmt = db.prepare(`
  INSERT INTO credentials (id, mobile, ssoToken, authToken, crmid, uniqueId, deviceId, userId, updated_at)
  VALUES (1, @mobile, @ssoToken, @authToken, @crmid, @uniqueId, @deviceId, @userId, @updatedAt)
  ON CONFLICT(id) DO UPDATE SET
    mobile=excluded.mobile, ssoToken=excluded.ssoToken, authToken=excluded.authToken,
    crmid=excluded.crmid, uniqueId=excluded.uniqueId, deviceId=excluded.deviceId,
    userId=excluded.userId, updated_at=excluded.updated_at
`);
const clearStmt = db.prepare("DELETE FROM credentials WHERE id = 1");

export function getStoredCredentials(): StoredCredentials | null {
  const row = selectStmt.get() as any;
  if (!row || !row.ssoToken) return null;
  return {
    mobile: row.mobile ?? "",
    ssoToken: row.ssoToken ?? "",
    authToken: row.authToken ?? "",
    crmid: row.crmid ?? "",
    uniqueId: row.uniqueId ?? "",
    deviceId: row.deviceId ?? "",
    userId: row.userId ?? "",
    updatedAt: row.updated_at ?? 0,
  };
}

/** Persists credentials, stamping updatedAt with the caller-provided epoch millis. */
export function saveCredentials(auth: AuthData, mobile: string, nowMs: number): void {
  upsertStmt.run({ ...auth, mobile, updatedAt: nowMs });
}

/** Updates only the tokens (keeps mobile), stamping updatedAt. Used by the refresh scheduler. */
export function updateTokens(auth: AuthData, nowMs: number): void {
  const existing = getStoredCredentials();
  saveCredentials(auth, existing?.mobile ?? "", nowMs);
}

export function clearCredentials(): void {
  clearStmt.run();
}

// ── Favorites (shared across all TVs + the web player) ──
const favAll = db.prepare("SELECT channel_id FROM favorites");
const favHas = db.prepare("SELECT 1 FROM favorites WHERE channel_id = ?");
const favAdd = db.prepare("INSERT OR IGNORE INTO favorites (channel_id) VALUES (?)");
const favDel = db.prepare("DELETE FROM favorites WHERE channel_id = ?");

export function getFavorites(): string[] {
  return (favAll.all() as Array<{ channel_id: string }>).map((r) => r.channel_id);
}

/** Toggles a favorite and returns the new state (true = now favorited). */
export function toggleFavorite(channelId: string): boolean {
  if (favHas.get(channelId)) {
    favDel.run(channelId);
    return false;
  }
  favAdd.run(channelId);
  return true;
}

/** Replaces the whole favorites set (used by a TV pushing its local set up). */
export function setFavorites(ids: string[]): void {
  const tx = db.transaction((list: string[]) => {
    db.prepare("DELETE FROM favorites").run();
    for (const id of list) favAdd.run(id);
  });
  tx(ids);
}
