/**
 * Helpers for Jio's short-lived Akamai `__hdnea__` token (used by the Phase 2 stream proxy).
 * Ported from JioApiClient.extractHdneaToken / extractTokenExpiryEpochSec.
 */

const MARKER = "__hdnea__=";

/** Returns everything after `__hdnea__=` in a stream URL, or "" if absent. */
export function extractHdneaToken(url: string): string {
  const i = url.indexOf(MARKER);
  return i >= 0 ? url.slice(i + MARKER.length) : "";
}

/** Parses the `exp=` epoch-seconds out of an `__hdnea__` token; 0 if not found. */
export function extractTokenExpiryEpochSec(token: string): number {
  const m = /exp=(\d+)/.exec(token);
  return m ? Number(m[1]) : 0;
}
