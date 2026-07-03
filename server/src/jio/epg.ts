import { jio } from "../config";

export interface EpgProgram {
  title: string;
  description: string;
  startMs: number;
  stopMs: number;
}

/** Native Jio EPG for one channel (mirrors EpgRepository.getNativeEpgForChannel). */
export async function getNativeEpg(channelId: string): Promise<EpgProgram[]> {
  const url = `https://jiotvapi.cdn.jio.com/apis/v1.3/getepg/get?offset=0&channel_id=${encodeURIComponent(channelId)}&langId=6`;
  try {
    const res = await fetch(url, { headers: { "User-Agent": "Mozilla/5.0", appname: jio.APP_NAME } });
    if (!res.ok) return [];
    const json = (await res.json()) as any;
    const epg: any[] = json.epg ?? [];
    return epg
      .map((p) => ({
        title: p.showname ?? "",
        description: p.description ?? "",
        startMs: Number(p.startEpoch ?? 0),
        stopMs: Number(p.endEpoch ?? 0),
      }))
      .filter((p) => p.title && p.startMs > 0 && p.stopMs > 0)
      .sort((a, b) => a.startMs - b.startMs);
  } catch {
    return [];
  }
}
