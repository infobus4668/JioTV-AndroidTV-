import { useEffect, useRef, useState } from "react";
import shaka from "shaka-player/dist/shaka-player.compiled";
import { api, type EpgProgram } from "./api";

/**
 * Shaka Player wired to the JTV proxy:
 *  - a request filter rewrites every manifest/segment URL to `/api/proxy?cid=…&u=…` so the browser
 *    never talks to the Jio CDN directly (defeats CORS) and the server injects a fresh `__hdnea__`;
 *  - for DRM channels, the Widevine license server points at `/api/play/:id/license`, which forwards
 *    the EME challenge to Jio with the correct headers.
 */
export function Player({ channelId, name, onClose }: { channelId: string; name: string; onClose: () => void }) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [now, setNow] = useState<EpgProgram | null>(null);
  const [next, setNext] = useState<EpgProgram | null>(null);

  useEffect(() => {
    let live = true;
    api.epg(channelId).then((r) => {
      if (!live) return;
      const t = Date.now();
      setNow(r.programs.find((p) => p.startMs <= t && p.stopMs > t) ?? null);
      setNext(r.programs.find((p) => p.startMs > t) ?? null);
    }).catch(() => {});
    return () => { live = false; };
  }, [channelId]);

  useEffect(() => {
    let player: shaka.Player | null = null;
    let cancelled = false;

    async function start() {
      const video = videoRef.current;
      if (!video) return;
      shaka.polyfill.installAll();
      if (!shaka.Player.isBrowserSupported()) {
        setError("This browser can't play the stream (no MSE/EME support).");
        setLoading(false);
        return;
      }
      try {
        const info = await api.playInfo(channelId);
        if (cancelled) return;

        player = new shaka.Player();
        await player.attach(video);

        if (info.hasDrm) {
          player.configure({
            drm: { servers: { "com.widevine.alpha": `/api/play/${encodeURIComponent(channelId)}/license` } },
          });
        }

        player.getNetworkingEngine()!.registerRequestFilter((type, request) => {
          if (type === shaka.net.NetworkingEngine.RequestType.LICENSE) return;
          request.uris = request.uris.map((u) =>
            u.startsWith("/api/proxy") || !/^https?:\/\//i.test(u)
              ? u
              : `/api/proxy?cid=${encodeURIComponent(channelId)}&u=${encodeURIComponent(u)}`
          );
        });

        player.addEventListener("error", (e: any) => {
          setError(`Playback error ${e?.detail?.code ?? ""}`.trim());
        });

        await player.load(info.manifestUrl);
        if (!cancelled) setLoading(false);
      } catch (e: any) {
        if (!cancelled) {
          setError(e?.message ?? "Failed to start playback");
          setLoading(false);
        }
      }
    }

    start();
    return () => {
      cancelled = true;
      player?.destroy();
    };
  }, [channelId]);

  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-black">
      <div className="flex items-center justify-between px-5 py-3 bg-gradient-to-b from-black/80 to-transparent">
        <div className="min-w-0">
          <div className="font-semibold text-lg truncate">{name}</div>
          {now && (
            <div className="text-sm text-muted truncate">
              <span className="text-live font-semibold">● NOW</span> {now.title}
              {next && <span className="ml-3 opacity-70">Next: {next.title}</span>}
            </div>
          )}
        </div>
        <button className="btn-ghost shrink-0 ml-3" onClick={onClose}>✕ Close</button>
      </div>
      <div className="relative flex-1">
        <video ref={videoRef} className="absolute inset-0 h-full w-full bg-black" autoPlay controls />
        {loading && !error && (
          <div className="absolute inset-0 grid place-items-center text-muted">Loading…</div>
        )}
        {error && (
          <div className="absolute inset-0 grid place-items-center">
            <div className="card p-6 text-center max-w-md">
              <div className="text-2xl mb-2">⚠</div>
              <div className="text-danger font-semibold">{error}</div>
              <div className="text-muted text-sm mt-2">
                DRM channels need HTTPS (a secure context) for Widevine in the browser.
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
