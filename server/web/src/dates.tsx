export interface DayOpt { key: number; label: string; date: Date }

/** True if `ms` falls on the same local calendar day as `date`. */
export function sameDay(ms: number, date: Date): boolean {
  const a = new Date(ms);
  return a.getFullYear() === date.getFullYear() && a.getMonth() === date.getMonth() && a.getDate() === date.getDate();
}

/**
 * The days actually present in a channel's programmes, today or earlier (catch-up is past-only), newest
 * first. Data-driven so we never show an empty/future tab — Jio's EPG is a rolling today-forward window,
 * so in practice this is just "Today" (and "Yesterday" when it's still in the window).
 */
export function daysFromPrograms(programs: { startMs: number }[]): DayOpt[] {
  const midnight = new Date(); midnight.setHours(0, 0, 0, 0);
  const today = midnight.getTime();
  const seen = new Map<number, DayOpt>();
  for (const p of programs) {
    const d = new Date(p.startMs); d.setHours(0, 0, 0, 0);
    if (d.getTime() > today) continue; // never offer future days
    const key = Math.round((d.getTime() - today) / 86_400_000); // 0 = today, -1 = yesterday …
    if (!seen.has(key)) {
      const label = key === 0 ? "Today" : key === -1 ? "Yesterday"
        : d.toLocaleDateString([], { weekday: "short", day: "numeric", month: "short" });
      seen.set(key, { key, label, date: d });
    }
  }
  return [...seen.values()].sort((a, b) => b.key - a.key);
}

/** A horizontal day-picker styled as tabs. Renders nothing when there's only one day (or none). */
export function DateTabs({ days, value, onChange }: { days: DayOpt[]; value: number; onChange: (d: number) => void }) {
  if (days.length <= 1) return null;
  return (
    <div className="tabs w-max">
      {days.map((d) => (
        <button key={d.key} className={`tab whitespace-nowrap ${value === d.key ? "active" : ""}`} onClick={() => onChange(d.key)}>
          {d.label}
        </button>
      ))}
    </div>
  );
}
