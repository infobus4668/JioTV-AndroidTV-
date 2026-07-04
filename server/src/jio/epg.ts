import { jio } from "../config";

export interface EpgProgram {
  title: string;
  description: string;
  startMs: number;
  stopMs: number;
  // Catch-up identifiers (from the native Jio EPG) — needed to resolve a past show's VOD stream.
  srno?: string;
  showId?: string;
  showtime?: string;
  catchup?: boolean;
}

// Jio's getepg `offset` is a day index; offset 0 covers yesterday+today. Catch-up only exists for the
// past and Jio's EPG doesn't go back further, so offset 0 is all we need (and it's 1 request, not 4).
const OFFSETS = [0];
const TTL_MS = 30 * 60 * 1000;
const cache = new Map<string, { at: number; programs: EpgProgram[] }>();

async function fetchOffset(channelId: string, offset: number): Promise<EpgProgram[]> {
  try {
    const url = `https://jiotvapi.cdn.jio.com/apis/v1.3/getepg/get?offset=${offset}&channel_id=${encodeURIComponent(channelId)}&langId=6`;
    const res = await fetch(url, { headers: { "User-Agent": "Mozilla/5.0", appname: jio.APP_NAME } });
    if (!res.ok) return [];
    const json = (await res.json()) as any;
    return (json.epg ?? []).map((o: any) => ({
      title: o.showname ?? "",
      description: o.description ?? "",
      startMs: Number(o.startEpoch ?? 0),
      stopMs: Number(o.endEpoch ?? 0),
      srno: o.srno != null ? String(o.srno) : undefined,
      showId: o.showId ?? undefined,
      showtime: o.showtime ?? undefined,
      catchup: !!o.isCatchupAvailable,
    })).filter((p: EpgProgram) => p.title && p.startMs > 0 && p.stopMs > 0);
  } catch {
    return [];
  }
}

/** Native Jio EPG for one channel — merges a few day-offsets (yesterday…+3d), de-duped, cached 30 min. */
export async function getNativeEpg(channelId: string): Promise<EpgProgram[]> {
  const c = cache.get(channelId);
  if (c && Date.now() - c.at < TTL_MS) return c.programs;

  const parts = await Promise.all(OFFSETS.map((o) => fetchOffset(channelId, o)));
  const now = Date.now();
  const past = now - 28 * 3600_000;   // include yesterday (for catch-up)
  const future = now + 24 * 3600_000; // rest of today (for the live/next view)
  const seen = new Set<string>();
  const programs = parts
    .flat()
    .filter((p) => p.stopMs > past && p.startMs < future)
    .filter((p) => { const k = `${p.startMs}|${p.title}`; if (seen.has(k)) return false; seen.add(k); return true; })
    .sort((a, b) => a.startMs - b.startMs);

  cache.set(channelId, { at: Date.now(), programs });
  return programs;
}
