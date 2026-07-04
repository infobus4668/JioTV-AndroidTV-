import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, type Channel, type EpgProgram } from "./api";
import { useLangFilter, LanguageMenu } from "./lang";

const fmt = (ms: number) => new Date(ms).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

/** TV guide: per-channel Now / Next across a category. Click a row to watch. */
export function GuidePage() {
  const nav = useNavigate();
  const [channels, setChannels] = useState<Channel[] | null>(null);
  const [group, setGroup] = useState("");
  const [favs, setFavs] = useState<Set<string>>(new Set());
  const [langs, toggleLang, clearLangs] = useLangFilter();
  const [page, setPage] = useState(0);
  const FAV = "Favorites";
  const PAGE_SIZE = 15;

  useEffect(() => {
    api.channels().then((r) => setChannels(r.channels)).catch(() => setChannels([]));
    api.favorites().then((r) => setFavs(new Set(r.ids))).catch(() => {});
  }, []);

  const groups = useMemo(() => [FAV, "All", ...Array.from(new Set((channels ?? []).map((c) => c.group))).sort()], [channels]);
  const languages = useMemo(() => Array.from(new Set((channels ?? []).map((c) => c.language))).sort(), [channels]);
  useEffect(() => { if (!group) setGroup("All"); }, [group]);
  // Reset to the first page whenever the filters change.
  useEffect(() => { setPage(0); }, [group, langs]);

  const filtered = useMemo(
    () => (channels ?? [])
      .filter((c) => langs.size === 0 || langs.has(c.language))
      .filter((c) => group === FAV ? favs.has(c.id) : group === "All" ? true : c.group === group),
    [channels, group, favs, langs]
  );
  // Paginate so we only fetch EPG for the ~15 channels currently on screen.
  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const curPage = Math.min(page, pageCount - 1);
  const rows = filtered.slice(curPage * PAGE_SIZE, curPage * PAGE_SIZE + PAGE_SIZE);

  if (!channels) return <div className="empty-state">Loading guide…</div>;

  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-4">
        <h2>TV guide {filtered.length > 0 && <span className="text-subtle text-base font-normal">· {filtered.length} channels</span>}</h2>
        <LanguageMenu available={languages} langs={langs} onToggle={toggleLang} onClear={clearLangs} />
      </div>
      <div className="mb-4 overflow-x-auto no-scrollbar">
        <div className="tabs w-max">
          {groups.map((g) => (
            <button key={g} className={`tab whitespace-nowrap ${group === g ? "active" : ""}`} onClick={() => setGroup(g)}>{g}</button>
          ))}
        </div>
      </div>
      {rows.length === 0 ? (
        <div className="empty-state">{group === FAV ? "No favourites yet." : "No channels here."}</div>
      ) : (
        <>
          <div className="grid gap-2">
            {rows.map((c) => <GuideRow key={c.id} channel={c} onPlay={() => nav(`/watch/${c.id}`, { state: { name: c.name } })} />)}
          </div>
          {pageCount > 1 && (
            <div className="flex items-center justify-center gap-4 mt-5">
              <button className="btn-secondary btn-sm" disabled={curPage === 0} onClick={() => setPage(curPage - 1)}>← Previous</button>
              <span className="text-muted text-sm tabular-nums">Page {curPage + 1} of {pageCount}</span>
              <button className="btn-secondary btn-sm" disabled={curPage >= pageCount - 1} onClick={() => setPage(curPage + 1)}>Next →</button>
            </div>
          )}
        </>
      )}
    </div>
  );
}

function GuideRow({ channel, onPlay }: { channel: Channel; onPlay: () => void }) {
  const [now, setNow] = useState<EpgProgram | null>(null);
  const [next, setNext] = useState<EpgProgram | null>(null);
  useEffect(() => {
    let live = true;
    api.epg(channel.id).then((r) => {
      if (!live) return; const t = Date.now();
      setNow(r.programs.find((p) => p.startMs <= t && p.stopMs > t) ?? null);
      setNext(r.programs.find((p) => p.startMs > t) ?? null);
    }).catch(() => {});
    return () => { live = false; };
  }, [channel.id]);

  const t = Date.now();
  const prog = now ? Math.min(1, (t - now.startMs) / (now.stopMs - now.startMs)) : 0;

  return (
    <button onClick={onPlay} className="card card-hover w-full !p-3 flex items-center gap-3 text-left">
      <img src={channel.logoUrl} alt="" className="h-10 w-10 rounded-full bg-surface-2 object-cover shrink-0"
        onError={(e) => ((e.target as HTMLImageElement).style.visibility = "hidden")} />
      <div className="w-40 shrink-0 min-w-0">
        <div className="text-sm font-medium truncate">{channel.name}</div>
        <div className="text-subtle text-xs truncate">{channel.group}</div>
      </div>
      <div className="flex-1 min-w-0">
        {now ? (
          <>
            <div className="flex items-center gap-2">
              <span className="badge badge-accent shrink-0">NOW</span>
              <span className="text-sm truncate">{now.title}</span>
              <span className="text-subtle text-xs shrink-0 tabular-nums">{fmt(now.startMs)}–{fmt(now.stopMs)}</span>
            </div>
            <div className="progress mt-1.5"><div className="bar" style={{ width: `${prog * 100}%` }} /></div>
          </>
        ) : <span className="text-subtle text-sm">No programme info</span>}
      </div>
      <div className="w-44 shrink-0 min-w-0 hidden md:block">
        {next && (
          <>
            <div className="text-subtle text-xs tabular-nums">Next · {fmt(next.startMs)}</div>
            <div className="text-sm truncate">{next.title}</div>
          </>
        )}
      </div>
    </button>
  );
}
