import { useEffect, useMemo, useRef, useState, useCallback } from "react";
import { useParams, useLocation, useNavigate } from "react-router-dom";
import shaka from "shaka-player/dist/shaka-player.compiled";
import { api, type EpgProgram } from "./api";

/* ── watch page (player + details + catch-up) ──────────────────────────── */
export function WatchPage() {
  const { id = "" } = useParams();
  const location = useLocation() as { state?: { name?: string } };
  const nav = useNavigate();
  const name = location.state?.name ?? `Channel ${id}`;

  const [programs, setPrograms] = useState<EpgProgram[]>([]);
  const [cid, setCid] = useState(id);          // live = id; catch-up = `${id}~${beginSec}`
  const [catchTitle, setCatchTitle] = useState<string | null>(null);

  useEffect(() => { setCid(id); setCatchTitle(null); api.epg(id).then((r) => setPrograms(r.programs)).catch(() => setPrograms([])); }, [id]);

  const now = Date.now();
  const current = useMemo(() => programs.find((p) => p.startMs <= now && p.stopMs > now), [programs, now]);
  const past = useMemo(() => programs.filter((p) => p.stopMs <= now).slice(-8).reverse(), [programs, now]);
  const upcoming = useMemo(() => programs.filter((p) => p.startMs > now).slice(0, 8), [programs, now]);

  const playCatchup = (p: EpgProgram) => { setCid(`${id}~${Math.floor(p.startMs / 1000)}`); setCatchTitle(p.title); };
  const goLive = () => { setCid(id); setCatchTitle(null); };

  return (
    <div className="p-6 max-w-6xl mx-auto">
      <button className="btn-ghost btn-sm mb-4" onClick={() => nav("/channels")}>← Channels</button>
      <div className="grid lg:grid-cols-[1fr_340px] gap-6 items-start">
        <div className="min-w-0">
          <PlayerBox cid={cid} title={catchTitle ? `${name} · ${catchTitle}` : name} />
          <div className="card mt-4">
            <div className="flex items-center justify-between gap-3">
              <div className="min-w-0">
                <h3 className="truncate">{name}</h3>
                {catchTitle ? (
                  <p className="text-muted text-sm mt-1"><span className="badge badge-accent mr-1">CATCH-UP</span>{catchTitle}</p>
                ) : current ? (
                  <p className="text-muted text-sm mt-1"><span className="badge badge-error mr-1">LIVE</span>{current.title} · {fmt(current.startMs)}–{fmt(current.stopMs)}</p>
                ) : (
                  <p className="text-muted text-sm mt-1"><span className="badge badge-error mr-1">LIVE</span>No programme info</p>
                )}
                {current?.description && !catchTitle && <p className="text-subtle text-sm mt-2">{current.description}</p>}
              </div>
              {catchTitle && <button className="btn-secondary btn-sm shrink-0" onClick={goLive}>Go live</button>}
            </div>
          </div>
        </div>

        <aside className="grid gap-4">
          {past.length > 0 && (
            <div className="card !p-4">
              <h3 className="mb-2 text-base">Previous shows</h3>
              <div className="space-y-1">
                {past.map((p) => (
                  <button key={p.startMs} onClick={() => playCatchup(p)}
                    className="w-full text-left px-2 py-2 rounded-md hover:bg-surface-hover transition-colors">
                    <div className="text-sm truncate">{p.title}</div>
                    <div className="text-subtle text-xs">{fmt(p.startMs)} – {fmt(p.stopMs)}</div>
                  </button>
                ))}
              </div>
            </div>
          )}
          {upcoming.length > 0 && (
            <div className="card !p-4">
              <h3 className="mb-2 text-base">Up next</h3>
              <div className="space-y-1">
                {upcoming.map((p) => (
                  <div key={p.startMs} className="px-2 py-2">
                    <div className="text-sm truncate">{p.title}</div>
                    <div className="text-subtle text-xs">{fmt(p.startMs)} – {fmt(p.stopMs)}</div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </aside>
      </div>
    </div>
  );
}

function fmt(ms: number) { return new Date(ms).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }); }
function langName(code: string): string {
  try { return new Intl.DisplayNames([navigator.language], { type: "language" }).of(code) ?? code; } catch { return code; }
}

/* ── embedded 16:9 player with custom controls ─────────────────────────── */
interface QualityOpt { label: string; height: number | "auto"; }

function PlayerBox({ cid, title }: { cid: string; title: string }) {
  const boxRef = useRef<HTMLDivElement>(null);
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
  const [audioLangs, setAudioLangs] = useState<string[]>([]);
  const [curLang, setCurLang] = useState("");
  const [qualities, setQualities] = useState<QualityOpt[]>([]);
  const [curQuality, setCurQuality] = useState<number | "auto">("auto");

  useEffect(() => {
    let cancelled = false;
    setError(null); setLoading(true);
    async function start() {
      const video = videoRef.current; if (!video) return;
      shaka.polyfill.installAll();
      if (!shaka.Player.isBrowserSupported()) { setError("This browser can’t play the stream (no MSE/EME)."); setLoading(false); return; }
      try {
        const info = await api.playInfo(cid);
        if (cancelled) return;
        const player: any = new shaka.Player();
        playerRef.current = player;
        await player.attach(video);
        if (info.hasDrm) player.configure({ drm: { servers: { "com.widevine.alpha": `/api/play/${encodeURIComponent(cid)}/license` } } });
        player.getNetworkingEngine().registerRequestFilter((type: any, request: any) => {
          if (type === shaka.net.NetworkingEngine.RequestType.LICENSE) return;
          request.uris = request.uris.map((u: string) =>
            u.startsWith("/api/proxy") || !/^https?:\/\//i.test(u) ? u : `/api/proxy?cid=${encodeURIComponent(cid)}&u=${encodeURIComponent(u)}`);
        });
        player.addEventListener("error", (e: any) => setError(drmMessage(e?.detail)));
        await player.load(info.manifestUrl);
        if (cancelled) return;
        setLoading(false);
        try {
          setAudioLangs(player.getAudioLanguages());
          setCurLang(player.getVariantTracks().find((t: any) => t.active)?.language ?? "");
          const heights = Array.from(new Set(player.getVariantTracks().map((t: any) => t.height).filter(Boolean))).sort((a: any, b: any) => b - a) as number[];
          setQualities([{ label: "Auto", height: "auto" }, ...heights.map((h) => ({ label: `${h}p`, height: h }))]);
        } catch {}
      } catch (e: any) { if (!cancelled) { setError(e?.message ?? "Failed to start playback"); setLoading(false); } }
    }
    start();
    return () => { cancelled = true; playerRef.current?.destroy(); playerRef.current = null; };
  }, [cid]);

  useEffect(() => {
    const v = videoRef.current; if (!v) return;
    const onPlay = () => setPaused(false), onPause = () => setPaused(true);
    const onVol = () => { setMuted(v.muted); setVolume(v.volume); };
    v.addEventListener("play", onPlay); v.addEventListener("pause", onPause); v.addEventListener("volumechange", onVol);
    return () => { v.removeEventListener("play", onPlay); v.removeEventListener("pause", onPause); v.removeEventListener("volumechange", onVol); };
  }, []);

  const nudge = useCallback(() => {
    setControls(true); window.clearTimeout(hideTimer.current);
    hideTimer.current = window.setTimeout(() => { setControls(false); setMenu(false); }, 3000);
  }, []);
  useEffect(() => () => window.clearTimeout(hideTimer.current), []);

  const togglePlay = () => { const v = videoRef.current; if (!v) return; v.paused ? v.play() : v.pause(); nudge(); };
  const onVolInput = (val: number) => { const v = videoRef.current; if (!v) return; v.volume = val; v.muted = val === 0; };
  const toggleFull = () => { const el = boxRef.current; if (!el) return; document.fullscreenElement ? document.exitFullscreen() : el.requestFullscreen?.(); };
  const pickLang = (l: string) => { playerRef.current?.selectAudioLanguage(l); setCurLang(l); };
  const pickQuality = (q: QualityOpt) => {
    const p = playerRef.current; if (!p) return;
    if (q.height === "auto") p.configure({ abr: { enabled: true } });
    else { p.configure({ abr: { enabled: false } }); const t = p.getVariantTracks().find((v: any) => v.height === q.height); if (t) p.selectVariantTrack(t, true); }
    setCurQuality(q.height); setMenu(false);
  };

  return (
    <div ref={boxRef} className="player-box relative w-full aspect-video bg-black rounded-lg overflow-hidden"
      onMouseMove={nudge} onMouseLeave={() => setControls(false)} style={{ cursor: controls ? "default" : "none" }}>
      <video ref={videoRef} className="absolute inset-0 h-full w-full" autoPlay playsInline onClick={togglePlay} />

      {/* title (top) */}
      <div className={`absolute inset-x-0 top-0 p-3 bg-gradient-to-b from-black/70 to-transparent text-white text-sm font-medium truncate transition-opacity ${controls ? "opacity-100" : "opacity-0"}`}>{title}</div>

      {/* center state */}
      <div className="absolute inset-0 grid place-items-center pointer-events-none">
        {loading && !error && <div className="h-9 w-9 rounded-full border-2 border-white/30 border-t-white animate-spin" />}
        {error && (
          <div className="card bg-surface max-w-sm mx-3 text-center pointer-events-auto">
            <div className="text-error font-semibold text-sm">{error}</div>
            <p className="text-muted text-xs mt-2">
              {location.protocol !== "https:" && location.hostname !== "localhost"
                ? "Open the server’s https:// URL (Account → HTTPS) for DRM channels."
                : "Some Jio channels use hardware DRM (L1) that desktop browsers can’t decrypt — those play on the TV app. Non-DRM channels work here."}
            </p>
          </div>
        )}
      </div>

      {/* controls (bottom) */}
      <div className={`absolute inset-x-0 bottom-0 flex items-center gap-2 p-3 bg-gradient-to-t from-black/80 to-transparent transition-opacity ${controls ? "opacity-100" : "opacity-0 pointer-events-none"}`}>
        <button className="text-white w-8 h-8 grid place-items-center" onClick={togglePlay} title={paused ? "Play" : "Pause"}>
          {paused ? <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z" /></svg>
                  : <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M6 5h4v14H6zM14 5h4v14h-4z" /></svg>}
        </button>
        <button className="text-white w-8 h-8 grid place-items-center" onClick={() => { const v = videoRef.current; if (v) v.muted = !v.muted; }} title="Mute">
          {muted || volume === 0 ? <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3L19 9.5 17.5 8 15 10.5 12.5 8 11 9.5 13.5 12 11 14.5 12.5 16 15 13.5 17.5 16 19 14.5z" /></svg>
                               : <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M3 9v6h4l5 5V4L7 9H3zm13 3a4 4 0 0 0-2-3.5v7A4 4 0 0 0 16 12z" /></svg>}
        </button>
        <input type="range" min={0} max={1} step={0.05} value={muted ? 0 : volume} onChange={(e) => onVolInput(Number(e.target.value))} className="w-20 accent-white" title="Volume" />
        <div className="ml-auto flex items-center gap-1">
          {(audioLangs.length > 1 || qualities.length > 1) && (
            <div className="relative">
              <button className="text-white w-8 h-8 grid place-items-center" onClick={() => setMenu((m) => !m)} title="Audio & quality">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.9.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.9l-.1-.1A2 2 0 1 1 6.9 4l.1.1a1.7 1.7 0 0 0 1.9.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.9-.3l.1-.1A2 2 0 1 1 20.9 6l-.1.1a1.7 1.7 0 0 0-.3 1.9V8a1.7 1.7 0 0 0 1.5 1H22a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z" /></svg>
              </button>
              {menu && (
                <div className="absolute bottom-10 right-0 w-48 card !p-2 bg-surface shadow-lg">
                  {audioLangs.length > 1 && (<div className="mb-2"><div className="text-subtle text-xs px-2 py-1">Audio</div>
                    {audioLangs.map((l) => <button key={l} onClick={() => pickLang(l)} className={`block w-full text-left px-2 py-1.5 rounded-md text-sm ${curLang === l ? "text-accent" : "hover:bg-surface-hover"}`}>{langName(l)}</button>)}</div>)}
                  {qualities.length > 1 && (<div><div className="text-subtle text-xs px-2 py-1">Quality</div>
                    {qualities.map((q) => <button key={String(q.height)} onClick={() => pickQuality(q)} className={`block w-full text-left px-2 py-1.5 rounded-md text-sm ${curQuality === q.height ? "text-accent" : "hover:bg-surface-hover"}`}>{q.label}</button>)}</div>)}
                </div>
              )}
            </div>
          )}
          <button className="text-white w-8 h-8 grid place-items-center" onClick={toggleFull} title="Fullscreen">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M8 3H5a2 2 0 0 0-2 2v3M16 3h3a2 2 0 0 1 2 2v3M8 21H5a2 2 0 0 1-2-2v-3M16 21h3a2 2 0 0 0 2-2v-3" /></svg>
          </button>
        </div>
      </div>
    </div>
  );
}

function drmMessage(detail: any): string {
  const code = detail?.code;
  // 6006/6007 = license request/response errors; 6001/6012 = key/CDM issues.
  if (code && [6001, 6006, 6007, 6008, 6012].includes(code)) return "DRM error — this channel likely needs hardware DRM (L1), which desktop browsers don’t support. It plays on the TV app.";
  return `Playback error ${code ?? ""}`.trim();
}
