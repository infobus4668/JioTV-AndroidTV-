// Minimal Lucide-style icon set (stroke, currentColor) — replaces emoji chrome.
type P = { className?: string; size?: number };
const base = (size = 16) => ({
  width: size, height: size, viewBox: "0 0 24 24", fill: "none",
  stroke: "currentColor", strokeWidth: 2, strokeLinecap: "round" as const, strokeLinejoin: "round" as const,
});

export const IconTv = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><rect x="2" y="7" width="20" height="15" rx="2" /><path d="m17 2-5 5-5-5" /></svg>
);
export const IconUser = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><circle cx="12" cy="8" r="4" /><path d="M4 21a8 8 0 0 1 16 0" /></svg>
);
export const IconLogOut = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" /><path d="m16 17 5-5-5-5" /><path d="M21 12H9" /></svg>
);
export const IconSun = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><circle cx="12" cy="12" r="4" /><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" /></svg>
);
export const IconMoon = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z" /></svg>
);
export const IconStar = ({ className, size, filled }: P & { filled?: boolean }) => (
  <svg {...base(size)} className={className} fill={filled ? "currentColor" : "none"}>
    <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14l-5-4.87 6.91-1.01L12 2z" />
  </svg>
);
export const IconX = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M18 6 6 18M6 6l12 12" /></svg>
);
export const IconCopy = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><rect x="9" y="9" width="13" height="13" rx="2" /><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" /></svg>
);
export const IconPlus = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M12 5v14M5 12h14" /></svg>
);
export const IconTrash = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" /></svg>
);
export const IconRefresh = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M21 12a9 9 0 1 1-3-6.7L21 8" /><path d="M21 3v5h-5" /></svg>
);
export const IconSearch = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><circle cx="11" cy="11" r="7" /><path d="m21 21-4.3-4.3" /></svg>
);
export const IconCheck = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="M20 6 9 17l-5-5" /></svg>
);
export const IconGuide = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><rect x="3" y="4" width="18" height="18" rx="2" /><path d="M3 10h18M8 4v6M16 4v6" /></svg>
);
export const IconChevron = ({ className, size }: P) => (
  <svg {...base(size)} className={className}><path d="m6 9 6 6 6-6" /></svg>
);

/* category icons */
const News = (p: P) => <svg {...base(p.size)} className={p.className}><path d="M4 4h13v16H6a2 2 0 0 1-2-2V4z" /><path d="M17 8h3v10a2 2 0 0 1-2 2M8 8h5M8 12h5M8 16h5" /></svg>;
const Film = (p: P) => <svg {...base(p.size)} className={p.className}><rect x="3" y="4" width="18" height="16" rx="2" /><path d="M7 4v16M17 4v16M3 9h4M3 15h4M17 9h4M17 15h4" /></svg>;
const Music = (p: P) => <svg {...base(p.size)} className={p.className}><path d="M9 18V5l12-2v13" /><circle cx="6" cy="18" r="3" /><circle cx="18" cy="16" r="3" /></svg>;
const Sports = (p: P) => <svg {...base(p.size)} className={p.className}><circle cx="12" cy="12" r="9" /><path d="M12 3v18M3 12h18M6 6l12 12M18 6 6 18" /></svg>;
const Kids = (p: P) => <svg {...base(p.size)} className={p.className}><circle cx="12" cy="10" r="7" /><path d="M9 9h.01M15 9h.01M9 13a4 4 0 0 0 6 0M12 17v4" /></svg>;
const Sparkle = (p: P) => <svg {...base(p.size)} className={p.className}><path d="M12 3l2 5 5 2-5 2-2 5-2-5-5-2 5-2 2-5z" /></svg>;
const Book = (p: P) => <svg {...base(p.size)} className={p.className}><path d="M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2z" /><path d="M4 19a2 2 0 0 0 2 2h13" /></svg>;
const Bag = (p: P) => <svg {...base(p.size)} className={p.className}><path d="M6 7h12l1 13H5L6 7z" /><path d="M9 7a3 3 0 0 1 6 0" /></svg>;
const Business = (p: P) => <svg {...base(p.size)} className={p.className}><rect x="3" y="7" width="18" height="13" rx="2" /><path d="M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" /></svg>;
const Heart = (p: P) => <svg {...base(p.size)} className={p.className}><path d="M20.8 5.6a5 5 0 0 0-7.1 0L12 7.3l-1.7-1.7a5 5 0 1 0-7.1 7.1L12 21l8.8-8.3a5 5 0 0 0 0-7.1z" /></svg>;
const Globe = (p: P) => <svg {...base(p.size)} className={p.className}><circle cx="12" cy="12" r="9" /><path d="M3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18" /></svg>;

/** Returns an icon component for a category name (keyword match). */
export function categoryIcon(name: string): (p: P) => JSX.Element {
  const n = name.toLowerCase();
  if (n.includes("news") || n.includes("business")) return n.includes("business") ? Business : News;
  if (n.includes("sport")) return Sports;
  if (n.includes("movie") || n.includes("film") || n.includes("cinema")) return Film;
  if (n.includes("music")) return Music;
  if (n.includes("kid") || n.includes("child")) return Kids;
  if (n.includes("entertain")) return Sparkle;
  if (n.includes("educat") || n.includes("knowledge") || n.includes("info")) return Book;
  if (n.includes("shop")) return Bag;
  if (n.includes("devot") || n.includes("spiritual") || n.includes("lifestyle")) return Heart;
  return Globe;
}
