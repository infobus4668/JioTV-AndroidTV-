import { getStreamData, GeturlAuthError, type StreamData } from "../jio/stream";
import { extractHdneaToken, extractTokenExpiryEpochSec } from "../jio/hdnea";
import { refreshTokens } from "../jio/tokens";
import { getStoredCredentials, updateTokens } from "../store/db";

interface CachedStream {
  key: string;
  data: StreamData;
  hdnea: string;
  expiresAtMs: number;
}

const HDNEA_MARKER = "__hdnea__=";
const cache = new Map<string, CachedStream>();

/** A playback key is either "channelId" (live) or "channelId~beginEpochSec" (catch-up). */
function parseKey(key: string): { channelId: string; beginSec: number } {
  const i = key.indexOf("~");
  return i >= 0 ? { channelId: key.slice(0, i), beginSec: Number(key.slice(i + 1)) || 0 } : { channelId: key, beginSec: 0 };
}

async function resolve(key: string): Promise<CachedStream> {
  const { channelId, beginSec } = parseKey(key);
  const creds = getStoredCredentials();
  if (!creds) throw new Error("No active login on the server — sign in on the Account page first.");
  const opts = beginSec > 0 ? { streamType: "Seek" as const, beginEpochMs: beginSec * 1000 } : {};
  let data: StreamData;
  try {
    data = await getStreamData(channelId, creds, opts);
  } catch (e) {
    // A 401/403/419 usually means the SSO token went stale — refresh once and retry (like the app).
    if (e instanceof GeturlAuthError) {
      const refreshed = await refreshTokens(creds);
      updateTokens(refreshed, Date.now());
      data = await getStreamData(channelId, refreshed, opts);
    } else {
      throw e;
    }
  }
  const hdnea = extractHdneaToken(data.streamUrl);
  const expSec = extractTokenExpiryEpochSec(hdnea);
  const expiresAtMs = expSec > 0 ? expSec * 1000 : Date.now() + 90_000;
  const entry: CachedStream = { key, data, hdnea, expiresAtMs };
  cache.set(key, entry);
  return entry;
}

/** Returns cached stream data, re-resolving a fresh __hdnea__ token ~15s before it expires. */
export async function getStream(key: string): Promise<CachedStream> {
  const c = cache.get(key);
  if (c && Date.now() < c.expiresAtMs - 15_000) return c;
  return resolve(key);
}

/** Rewrites the `__hdnea__` query token in a Jio URL to the freshest one. */
function withFreshToken(url: string, hdnea: string): string {
  if (!hdnea) return url;
  const i = url.indexOf(HDNEA_MARKER);
  return i >= 0 ? url.slice(0, i + HDNEA_MARKER.length) + hdnea : url;
}

/**
 * Proxies one upstream request (manifest or segment) with a fresh token + the stream headers, so the
 * browser never talks to the Jio CDN directly (defeats CORS) and never sees an expired token.
 */
export async function proxyUpstream(
  channelId: string,
  targetUrl: string,
  rangeHeader?: string
): Promise<Response> {
  const c = await getStream(channelId);
  const url = withFreshToken(targetUrl, c.hdnea);
  const headers: Record<string, string> = { ...c.data.streamHeaders };
  if (c.hdnea) headers["Cookie"] = HDNEA_MARKER + c.hdnea;
  if (rangeHeader) headers["Range"] = rangeHeader;
  return fetch(url, { headers });
}

/** Forwards a Widevine license challenge to Jio's license server with the correct headers. */
export async function proxyLicense(channelId: string, challenge: Buffer): Promise<Buffer> {
  const c = await getStream(channelId);
  if (!c.data.licenseUrl) throw new Error("Channel has no DRM license URL");
  const res = await fetch(c.data.licenseUrl, {
    method: "POST",
    headers: c.data.licenseHeaders,
    body: new Uint8Array(challenge),
  });
  if (!res.ok) throw new Error(`License request failed (HTTP ${res.status})`);
  return Buffer.from(await res.arrayBuffer());
}

/**
 * Rewrites a manifest so the browser player resolves media correctly through the proxy:
 *  - **DASH (.mpd):** inject an absolute top-level `<BaseURL>` (the real manifest directory) when one
 *    isn't already present, so relative SegmentTemplate media resolves to absolute Jio URLs — which
 *    the Shaka request filter then routes back through `/api/proxy`.
 *  - **HLS (.m3u8):** rewrite every segment / key / variant URI to an absolute `/api/proxy?...` link,
 *    resolved against the playlist URL.
 * Returns the rewritten text, or null if this body isn't a manifest we should touch.
 */
export function rewriteManifest(
  body: string,
  contentType: string,
  requestedUrl: string,
  segPrefix: string
): string | null {
  const ct = contentType.toLowerCase();
  const isMpd = ct.includes("dash+xml") || body.trimStart().startsWith("<");
  const isHls = ct.includes("mpegurl") || body.trimStart().startsWith("#EXTM3U");
  const baseDir = requestedUrl.slice(0, requestedUrl.lastIndexOf("/") + 1);
  const proxyPrefix = segPrefix; // caller supplies the full prefix ending in "u="

  if (isMpd) {
    if (/<BaseURL>\s*https?:\/\//i.test(body)) return body; // already absolute
    return body.replace(/(<MPD\b[^>]*>)/i, `$1<BaseURL>${baseDir}</BaseURL>`);
  }

  if (isHls) {
    const abs = (u: string) => {
      try {
        return new URL(u, requestedUrl).toString();
      } catch {
        return u;
      }
    };
    const wrap = (u: string) => proxyPrefix + encodeURIComponent(abs(u));
    return body
      .split("\n")
      .map((line) => {
        const t = line.trim();
        if (t === "") return line;
        if (t.startsWith("#")) {
          // Rewrite URI="..." attributes (EXT-X-KEY / MAP / MEDIA / I-FRAME-STREAM-INF).
          return line.replace(/URI="([^"]+)"/g, (_m, u) => `URI="${wrap(u)}"`);
        }
        return wrap(t); // a segment or variant-playlist line
      })
      .join("\n");
  }

  return null;
}

/** The real manifest URL + DRM flags the web player needs to configure Shaka. `key` is the cid. */
export async function getPlaybackInfo(key: string) {
  const c = await getStream(key);
  // "PayWall" in the URL = not subscribed. (A plain "Fallback" is a real non-DRM stream — entitled.)
  const entitled = !/paywall/i.test(c.data.streamUrl);
  return {
    channelId: key,
    isMpd: c.data.isMpd,
    manifestUrl: c.data.streamUrl,
    hasDrm: c.data.isMpd && c.data.licenseUrl.length > 0,
    entitled,
  };
}
