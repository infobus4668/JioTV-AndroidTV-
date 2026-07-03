import { refreshTokens } from "./jio/tokens";
import { getStoredCredentials, updateTokens } from "./store/db";

/**
 * Central token-refresh scheduler. Jio's SSO/auth tokens are long-lived but do expire; refreshing
 * them periodically means every TV that pulls `/api/credentials` always gets a valid token, so no TV
 * ever has to re-login — you only re-login on the server when the account itself is signed out.
 *
 * The short-lived per-stream `__hdnea__` token is refreshed per-playback by the Phase 2 proxy, not here.
 */
const REFRESH_INTERVAL_MS = 6 * 60 * 60 * 1000; // every 6 hours

let timer: NodeJS.Timeout | null = null;

export async function refreshNow(): Promise<boolean> {
  const stored = getStoredCredentials();
  if (!stored) return false;
  try {
    const updated = await refreshTokens(stored);
    updateTokens(updated, Date.now());
    console.log("[refresh] tokens refreshed");
    return true;
  } catch (err) {
    console.warn("[refresh] failed:", (err as Error).message);
    return false;
  }
}

export function startRefreshScheduler(): void {
  if (timer) return;
  // Kick once shortly after boot, then on the interval.
  setTimeout(() => void refreshNow(), 30_000);
  timer = setInterval(() => void refreshNow(), REFRESH_INTERVAL_MS);
}

export function stopRefreshScheduler(): void {
  if (timer) clearInterval(timer);
  timer = null;
}
