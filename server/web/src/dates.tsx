import { useMemo } from "react";

/** The selectable EPG days — yesterday … +3 days, matching the server's native-EPG window. */
export function dayList(): { key: number; label: string; date: Date }[] {
  const today = new Date();
  return [-1, 0, 1, 2, 3].map((d) => {
    const date = new Date(today);
    date.setDate(today.getDate() + d);
    const label =
      d === 0 ? "Today" : d === -1 ? "Yesterday" : d === 1 ? "Tomorrow"
      : date.toLocaleDateString([], { weekday: "short", day: "numeric", month: "short" });
    return { key: d, label, date };
  });
}

/** A reference "now" for a day offset: today → the real now; other days → same clock time on that day. */
export function refTimeFor(dayOffset: number): number {
  if (dayOffset === 0) return Date.now();
  const d = new Date();
  d.setDate(d.getDate() + dayOffset);
  return d.getTime();
}

/** True if `ms` falls on the same local calendar day as `date`. */
export function sameDay(ms: number, date: Date): boolean {
  const a = new Date(ms);
  return a.getFullYear() === date.getFullYear() && a.getMonth() === date.getMonth() && a.getDate() === date.getDate();
}

/** A horizontal day-picker styled as tabs (shared by the Guide and the player's programme guide). */
export function DateTabs({ value, onChange, className = "" }: { value: number; onChange: (d: number) => void; className?: string }) {
  const days = useMemo(dayList, []);
  return (
    <div className={`tabs w-max ${className}`}>
      {days.map((d) => (
        <button key={d.key} className={`tab whitespace-nowrap ${value === d.key ? "active" : ""}`} onClick={() => onChange(d.key)}>
          {d.label}
        </button>
      ))}
    </div>
  );
}
