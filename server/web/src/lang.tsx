import { useState } from "react";
import { IconChevron, IconCheck } from "./Icons";

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

/** Small dropdown to pick the default quality every channel opens at. */
export function QualitySelect({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  return (
    <select className="input w-auto" value={value} onChange={(e) => onChange(e.target.value)} title="Default video quality for every channel">
      <option value="auto">Quality · Auto</option>
      <option value="high">Quality · High</option>
      <option value="mid">Quality · Medium</option>
      <option value="low">Quality · Low</option>
    </select>
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
          <div className="fixed inset-0 z-10" onClick={() => setOpen(false)} />
          <div className="absolute right-0 z-20 mt-1 w-80 max-h-96 overflow-auto card !p-2 shadow-lg">
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
