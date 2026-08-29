import { randomInt } from "node:crypto";

// Unambiguous alphabet (no 0/O/1/I) — easy to read + type on a TV remote.
const ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

export const CODE_MIN = 4;
export const CODE_MAX = 12;

/** Generates a random code of the given length (clamped to 4–12). */
export function generateCode(length: number): string {
  const n = Math.max(CODE_MIN, Math.min(CODE_MAX, Math.floor(length) || 6));
  let out = "";
  for (let i = 0; i < n; i++) out += ALPHABET[randomInt(ALPHABET.length)];
  return out;
}

/** Normalizes a user-typed code and validates it (4–12 alphanumeric). Returns null if invalid. */
export function normalizeCode(raw: string): string | null {
  const c = raw.trim().toUpperCase();
  if (c.length < CODE_MIN || c.length > CODE_MAX) return null;
  if (!/^[A-Z0-9]+$/.test(c)) return null;
  return c;
}
