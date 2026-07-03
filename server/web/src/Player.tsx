import { useEffect, useRef, useState, useCallback } from "react";
import { useParams, useLocation, useNavigate } from "react-router-dom";
import shaka from "shaka-player/dist/shaka-player.compiled";
import { api, type EpgProgram } from "./api";
import { IconX } from "./Icons";

/** Route wrapper: reads the channel id + (optional) name and renders the custom player full-screen. */
export function WatchPage() {
  const { id = "" } = useParams();
  const location = useLocation() as { state?: { name?: string } };
  const nav = useNavigate();
  return <Player channelId={id} name={location.state?.name ?? `Channel ${id}`} onClose={() => nav(-1)} />;
}

interface QualityOpt { label: string; height: number | "auto"; }

function Player({ channelId, name, onClose }: { channelId: string; name: string; onClose: () => void }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const playerRef = useRef<any>(null);
  const hideTimer = useRef<number | undefined>(undefined);

  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [paused, setPaused] = useState(false);
  const [muted, setMuted] = useState(false);
  const [volume, setVolume] = useState(1);
  const [controls, setControls] = useState(true);
  const [menu, setMenu] = useState(false);
  const [now, setNow] = useState<EpgProgram | null>(null);
  const [next, setNext] = useState<EpgProgram | null>(null);
  const [audioLangs, setAudioLangs] = useState<string[]>([]);
  const [curLang, setCurLang] = useState("");
  const [qualities, setQualities] = useState<QualityOpt[]>([]);
  const [curQuality, setCurQuality] = useState<number | "auto">("auto");

  // EPG now/next
  useEffect(() => {
    let live = true;
    api.epg(channelId).then((r) => {
      if (!live) return; const t = Date.now();
      setNow(r.programs.find((p) => p.startMs <= t && p.stopMs > t) ?? null);
      setNext(r.programs.find((p) => p.startMs > t) ?? null);
    }).catch(() => {});
    return () => { live = false; };
  }, [channelId]);

  // Shaka setup
  useEffect(() => {
    let cancelled = false;
    async function start() {
      const video = videoRef.current; if (!video) return;
      shaka.polyfill.installAll();
      if (!shaka.Player.isBrowserSupported()) { setError("This browser can’t play the stream (no MSE/EME)."); setLoading(false); return; }
      try {
        const info = await api.playInfo(channelId);
        if (cancelled) return;
        const player: any = new shaka.Player();
        playerRef.current = player;
        await player.attach(video);
        if (info.hasDrm) player.configure({ drm: { servers: { "com.widevine.alpha": `/api/play/${encodeURIComponent(channelId)}/license` } } });
        player.getNetworkingEngine().registerRequestFilter((type: any, request: any) => {
          if (type === shaka.net.NetworkingEngine.RequestType.LICENSE) return;
          request.uris = request.uris.map((u: string) =>
            u.startsWith("/api/proxy") || !/^https?:\/\//i.test(u) ? u : `/api/proxy?cid=${encodeURIComponent(channelId)}&u=${encodeURIComponent(u)}`);
        });
        player.addEventListener("error", (e: any) => setError(`Playback error ${e?.detail?.code ?? ""}`.trim()));
        await player.load(info.manifestUrl);
        if (cancelled) return;
        setLoading(false);
        // Populate audio + quality options.
        try {
          setAudioLangs(player.getAudioLanguages());
          setCurLang(player.getVariantTracks().find((t: any) => t.active)?.language ?? "");
          const heights = Array.from(new Set(player.getVariantTracks().map((t: any) => t.height).filter(Boolean))).sort((a: any, b: any) => b - a) as number[];
          setQualities([{ label: "Auto", height: "auto" }, ...heights.map((h) => ({ label: `${h}p`, height: h }))]);
        } catch {}
      } catch (e: any) {
        if (!cancelled) { setError(e?.message ?? "Failed to start playback"); setLoading(false); }
      }
    }
    start();
    return () => { cancelled = true; playerRef.current?.destroy(); };
  }, [channelId]);

  // Sync play/pause + volume state from the element.
  useEffect(() => {
    const v = videoRef.current; if (!v) return;
    const onPlay = () => setPaused(false), onPause = () => setPaused(true);
    const onVol = () => { setMuted(v.muted); setVolume(v.volume); };
    v.addEventListener("play", onPlay); v.addEventListener("pause", onPause); v.addEventListener("volumechange", onVol);
    return () => { v.removeEventListener("play", onPlay); v.removeEventListener("pause", onPause); v.removeEventListener("volumechange", onVol); };
  }, []);

  const nudge = useCallback(() => {
    setControls(true);
    window.clearTimeout(hideTimer.current);
    hideTimer.current = window.setTimeout(() => { setControls(false); setMenu(false); }, 3500);
  }, []);
  useEffect(() => { nudge(); return () => window.clearTimeout(hideTimer.current); }, [nudge]);

  const togglePlay = () => { const v = videoRef.current; if (!v) return; v.paused ? v.play() : v.pause(); nudge(); };
  const toggleMute = () => { const v = videoRef.current; if (!v) return; v.muted = !v.muted; };
  const onVolInput = (val: number) => { const v = videoRef.current; if (!v) return; v.volume = val; v.muted = val === 0; };
  const toggleFull = () => {
    const el = containerRef.current; if (!el) return;
    if (document.fullscreenElement) document.exitFullscreen(); else el.requestFullscreen?.();
  };
  const pickLang = (lang: string) => { playerRef.current?.selectAudioLanguage(lang); setCurLang(lang); };
  const pickQuality = (q: QualityOpt) => {
    const p = playerRef.current; if (!p) return;
    if (q.height === "auto") { p.configure({ abr: { enabled: true } }); }
    else {
      p.configure({ abr: { enabled: false } });
      const t = p.getVariantTracks().find((v: any) => v.height === q.height);
      if (t) p.selectVariantTrack(t, true);
    }
    setCurQuality(q.height); setMenu(false);
  };

  return (
    <div ref={containerRef} className="fixed inset-0 z-50 bg-black flex flex-col select-none"
      onMouseMove={nudge} onClick={nudge} style={{ cursor: controls ? "default" : "none" }}>
      <video ref={videoRef} className="absolute inset-0 h-full w-full bg-black" autoPlay playsInline
        onClick={(e) => { e.stopPropagation(); togglePlay(); }} />

      {/* top bar */}
      <div className={`relative flex items-start justify-between p-4 bg-gradient-to-b from-black/80 to-transparent transition-opacity ${controls ? "opacity-100" : "opacity-0"}`}>
        <div className="min-w-0">
          <div className="text-white font-semibold text-lg truncate">{name}</div>
          {now && <div className="text-white/70 text-sm truncate"><span className="text-error font-semibold">● LIVE</span> · {now.title}{next && <span className="text-white/40"> · Next: {next.title}</span>}</div>}
        </div>
        <button className="icon-btn !border-white/20 !text-white shrink-0" title="Close" onClick={(e) => { e.stopPropagation(); onClose(); }}><IconX /></button>
      </div>

      {/* center state */}
      <div className="flex-1 grid place-items-center pointer-events-none">
        {loading && !error && <div className="h-10 w-10 rounded-full border-2 border-white/30 border-t-white animate-spin" />}
        {error && (
          <div className="card bg-surface max-w-md text-center pointer-events-auto">
            <div className="text-error font-semibold">{error}</div>
            <p className="text-muted text-sm mt-2">
              {location.protocol !== "https:" && location.hostname !== "localhost"
                ? "DRM channels need HTTPS — open the server’s https:// URL (Account → HTTPS)."
                : "If this is a DRM channel, try again — the token may have refreshed."}
            </p>
          </div>
        )}
      </div>

      {/* bottom control bar */}
      <div className={`relative flex items-center gap-3 p-4 bg-gradient-to-t from-black/80 to-transparent transition-opacity ${controls ? "opacity-100" : "opacity-0 pointer-events-none"}`}
        onClick={(e) => e.stopPropagation()}>
        <button className="text-white w-9 h-9 grid place-items-center" onClick={togglePlay} title={paused ? "Play" : "Pause"}>
          {paused
            ? <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z" /></svg>
            : <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor"><path d="M6 5h4v14H6zM14 5h4v14h-4z" /></svg>}
        </button>
        <button className="text-white w-9 h-9 grid place-items-center" onClick={toggleMute} title="Mute">
          {muted || volume === 0
            ? <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3L19 9.5 17.5 8 15 10.5 12.5 8 11 9.5 13.5 12 11 14.5 12.5 16 15 13.5 17.5 16 19 14.5z" /></svg>
            : <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M3 9v6h4l5 5V4L7 9H3zm13 3a4 4 0 0 0-2-3.5v7A4 4 0 0 0 16 12z" /></svg>}
        </button>
        <input type="range" min={0} max={1} step={0.05} value={muted ? 0 : volume} onChange={(e) => onVolInput(Number(e.target.value))}
          className="w-24 accent-white" title="Volume" />
        <span className="badge badge-error ml-1">LIVE</span>
        <div className="ml-auto flex items-center gap-2">
          {(audioLangs.length > 1 || qualities.length > 1) && (
            <div className="relative">
              <button className="icon-btn !border-white/20 !text-white" title="Audio & quality" onClick={() => setMenu((m) => !m)}>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.9.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.9l-.1-.1A2 2 0 1 1 6.9 4l.1.1a1.7 1.7 0 0 0 1.9.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.9-.3l.1-.1A2 2 0 1 1 20.9 6l-.1.1a1.7 1.7 0 0 0-.3 1.9V8a1.7 1.7 0 0 0 1.5 1H22a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z" /></svg>
              </button>
              {menu && (
                <div className="absolute bottom-11 right-0 w-52 card !p-2 bg-surface shadow-lg">
                  {audioLangs.length > 1 && (
                    <div className="mb-2">
                      <div className="text-subtle text-xs px-2 py-1">Audio</div>
                      {audioLangs.map((l) => (
                        <button key={l} onClick={() => pickLang(l)} className={`block w-full text-left px-2 py-1.5 rounded-md text-sm ${curLang === l ? "text-accent" : "hover:bg-surface-hover"}`}>{langName(l)}</button>
                      ))}
                    </div>
                  )}
                  {qualities.length > 1 && (
                    <div>
                      <div className="text-subtle text-xs px-2 py-1">Quality</div>
                      {qualities.map((q) => (
                        <button key={String(q.height)} onClick={() => pickQuality(q)} className={`block w-full text-left px-2 py-1.5 rounded-md text-sm ${curQuality === q.height ? "text-accent" : "hover:bg-surface-hover"}`}>{q.label}</button>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>
          )}
          <button className="icon-btn !border-white/20 !text-white" title="Fullscreen" onClick={toggleFull}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M8 3H5a2 2 0 0 0-2 2v3M16 3h3a2 2 0 0 1 2 2v3M8 21H5a2 2 0 0 1-2-2v-3M16 21h3a2 2 0 0 0 2-2v-3" /></svg>
          </button>
        </div>
      </div>
    </div>
  );
}

function langName(code: string): string {
  try { return new Intl.DisplayNames([navigator.language], { type: "language" }).of(code) ?? code; }
  catch { return code; }
}
