import { useState } from "react";
import { IconChevron, IconCheck, IconStar, categoryIcon } from "./Icons";

/** Category picker as a dropdown — the mobile replacement for the desktop sidebar / scroll strip.
 *  Keeps the whole category list one tap away without eating a full row of horizontal space. */
export function CategoryMenu({ groups, value, onChange, favKey = "Favorites" }: {
  groups: string[]; value: string; onChange: (g: string) => void; favKey?: string;
}) {
  const [open, setOpen] = useState(false);
  return (
    <div className="relative">
      <button className="btn-secondary max-w-[150px]" onClick={() => setOpen((o) => !o)} title="Category">
        <span className="truncate">{value}</span> <IconChevron size={14} className="shrink-0" />
      </button>
      {open && (
        <>
          <div className="fixed inset-0 z-10" onClick={() => setOpen(false)} />
          <div className="absolute right-0 z-20 mt-1 w-56 max-w-[calc(100vw-2rem)] max-h-[60vh] overflow-auto card !p-1 shadow-lg">
            {groups.map((g) => {
              const Ico = g === favKey ? null : categoryIcon(g);
              const active = value === g;
              return (
                <button key={g} onClick={() => { onChange(g); setOpen(false); }}
                  className="flex items-center gap-2.5 w-full text-left px-3 py-2 rounded-md text-sm hover:bg-surface-hover">
                  {g === favKey ? <IconStar size={15} filled={active} className="shrink-0" /> : Ico && <Ico size={15} className="shrink-0 opacity-80" />}
                  <span className={`truncate ${active ? "text-accent" : ""}`}>{g}</span>
                  {active && <IconCheck size={14} className="text-accent ml-auto shrink-0" />}
                </button>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}

/** Global, persisted language filter (empty set = show all). Shared by Channels + Guide. */
export function useLangFilter(): [Set<string>, (l: string) => void, () => void] {
  const [langs, setLangs] = useState<Set<string>>(() => {
    try { return new Set(JSON.parse(localStorage.getItem("langs") || "[]")); } catch { return new Set(); }
  });
  const persist = (s: Set<string>) => { setLangs(new Set(s)); localStorage.setItem("langs", JSON.stringify([...s])); };
  return [langs, (l) => { const s = new Set(langs); s.has(l) ? s.delete(l) : s.add(l); persist(s); }, () => persist(new Set())];
}

/** Global, persisted default video quality ("auto" | "high" | "mid" | "low"). Read by the player. */
export function usePrefQuality(): [string, (q: string) => void] {
  const [q, setQ] = useState<string>(() => { try { return localStorage.getItem("prefQuality") || "auto"; } catch { return "auto"; } });
  return [q, (v) => { setQ(v); try { localStorage.setItem("prefQuality", v); } catch {} }];
}

const QUALITY_OPTS: [string, string][] = [["auto", "Auto"], ["high", "High"], ["mid", "Medium"], ["low", "Low"]];

/** Styled dropdown (matches LanguageMenu) to pick the default quality every channel opens at. */
export function QualitySelect({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const [open, setOpen] = useState(false);
  const label = QUALITY_OPTS.find((o) => o[0] === value)?.[1] ?? "Auto";
  return (
    <div className="relative">
      <button className="btn-secondary" onClick={() => setOpen((o) => !o)} title="Default video quality for every channel">
        Quality · {label} <IconChevron size={14} />
      </button>
      {open && (
        <>
          <div className="fixed inset-0 z-10" onClick={() => setOpen(false)} />
          <div className="absolute right-0 z-20 mt-1 w-40 card !p-1 shadow-lg">
            {QUALITY_OPTS.map(([v, l]) => (
              <button key={v} onClick={() => { onChange(v); setOpen(false); }}
                className="flex items-center justify-between w-full text-left px-3 py-1.5 rounded-md text-sm hover:bg-surface-hover">
                <span className={value === v ? "text-accent" : ""}>{l}</span>
                {value === v && <IconCheck size={14} className="text-accent" />}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

export function LanguageMenu({ available, langs, onToggle, onClear }: {
  available: string[]; langs: Set<string>; onToggle: (l: string) => void; onClear: () => void;
}) {
  const [open, setOpen] = useState(false);
  return (
    <div className="relative">
      <button className="btn-secondary" onClick={() => setOpen((o) => !o)}>
        Languages{langs.size ? ` · ${langs.size}` : ""} <IconChevron size={14} />
      </button>
      {open && (
        <>
          <div className="fixed inset-0 z-10 bg-black/50 md:bg-transparent" onClick={() => setOpen(false)} />
          {/* Mobile-first: base is a full-width bottom sheet (never clips); md+ becomes the anchored dropdown. */}
          <div className="z-20 overflow-auto card !p-2 shadow-lg
            fixed inset-x-0 bottom-0 w-full max-h-[70vh] rounded-t-2xl rounded-b-none pb-[calc(1.5rem+env(safe-area-inset-bottom,0px))]
            md:absolute md:inset-x-auto md:right-0 md:bottom-auto md:mt-1 md:w-80 md:max-w-[calc(100vw-2rem)] md:max-h-96 md:rounded-lg md:pb-2">
            <div className="flex justify-between items-center px-2 py-1">
              <span className="text-subtle text-xs">Show languages</span>
              {langs.size > 0 && <button className="text-accent text-xs" onClick={onClear}>Clear</button>}
            </div>
            <div className="grid grid-cols-2 gap-x-1">
              {available.map((l) => (
                <button key={l} onClick={() => onToggle(l)} className="flex items-center gap-2 min-w-0 text-left px-2 py-1.5 rounded-md text-sm hover:bg-surface-hover">
                  <span className={`w-4 h-4 shrink-0 rounded grid place-items-center border ${langs.has(l) ? "bg-accent border-accent" : "border-border-strong"}`}>{langs.has(l) && <IconCheck size={12} className="text-white" />}</span>
                  <span className="truncate">{l}</span>
                </button>
              ))}
            </div>
          </div>
        </>
      )}
    </div>
  );
}
