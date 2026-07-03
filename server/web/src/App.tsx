import { useEffect, useMemo, useState } from "react";
import { Routes, Route, Navigate, NavLink, Outlet, useNavigate } from "react-router-dom";
import { api, type AccessCode, type Channel } from "./api";
import { WatchPage } from "./Player";
import { GuidePage } from "./Guide";
import {
  IconTv, IconUser, IconLogOut, IconSun, IconMoon, IconStar, IconSearch,
  IconCopy, IconPlus, IconTrash, IconRefresh, IconCheck, IconGuide, categoryIcon,
} from "./Icons";
import { useLangFilter, LanguageMenu } from "./lang";

/* ── theme ─────────────────────────────────────────────────────────────── */
function useTheme() {
  const [theme, setTheme] = useState<"dark" | "light">(
    (document.documentElement.getAttribute("data-theme") as "dark" | "light") || "dark"
  );
  const toggle = () => {
    const next = theme === "dark" ? "light" : "dark";
    document.documentElement.setAttribute("data-theme", next);
    try { localStorage.setItem("theme", next); } catch {}
    setTheme(next);
  };
  return { theme, toggle };
}

/* ── root ──────────────────────────────────────────────────────────────── */
export default function App() {
  const [authed, setAuthed] = useState<boolean | null>(null);
  const [state, setState] = useState<{ needsSetup: boolean; authEnabled: boolean } | null>(null);

  useEffect(() => {
    api.setupState().then(setState).catch(() => setState({ needsSetup: false, authEnabled: false }));
    api.adminStatus().then(() => setAuthed(true)).catch(() => setAuthed(false));
  }, []);

  if (authed === null || state === null)
    return <div className="grid place-items-center h-full text-muted">Loading…</div>;
  if (state.needsSetup && !authed)
    return <SetupWizard onDone={() => { setState({ needsSetup: false, authEnabled: true }); setAuthed(true); }} />;
  if (state.authEnabled && !authed) return <Login onLogin={() => setAuthed(true)} />;

  return (
    <Routes>
      <Route element={<Layout onLogout={() => setAuthed(false)} />}>
        <Route index element={<Navigate to="/channels" replace />} />
        <Route path="channels" element={<Channels />} />
        <Route path="guide" element={<GuidePage />} />
        <Route path="watch/:id" element={<WatchPage />} />
        <Route path="account" element={<Account />} />
        <Route path="*" element={<Navigate to="/channels" replace />} />
      </Route>
    </Routes>
  );
}

/* ── auth screens ──────────────────────────────────────────────────────── */
function Centered({ children }: { children: React.ReactNode }) {
  return <div className="grid place-items-center h-full p-6"><div className="card w-full max-w-sm">{children}</div></div>;
}

function SetupWizard({ onDone }: { onDone: () => void }) {
  const [pw, setPw] = useState(""); const [pw2, setPw2] = useState("");
  const [err, setErr] = useState(""); const [busy, setBusy] = useState(false);
  const go = async (fn: () => Promise<unknown>) => { setErr(""); setBusy(true); try { await fn(); onDone(); } catch (e: any) { setErr(e.message); } finally { setBusy(false); } };
  const create = () => pw.length < 4 ? setErr("Use at least 4 characters.") : pw !== pw2 ? setErr("Passwords don’t match.") : go(() => api.setup({ password: pw }));
  return (
    <Centered>
      <h1>Welcome to JTV</h1>
      <p className="text-muted text-sm mt-1 mb-5">First run — set an admin password (saved on the server, no <code>.env</code>).</p>
      <label className="text-sm text-muted">Admin password</label>
      <input className="input mt-1.5" type="password" value={pw} onChange={(e) => setPw(e.target.value)} />
      <label className="text-sm text-muted mt-3 block">Confirm password</label>
      <input className="input mt-1.5" type="password" value={pw2} onChange={(e) => setPw2(e.target.value)} onKeyDown={(e) => e.key === "Enter" && create()} />
      <button className="btn-primary w-full mt-4" disabled={busy} onClick={create}>Create &amp; continue</button>
      <button className="btn-secondary w-full mt-2" disabled={busy} onClick={() => go(() => api.setup({ disableAuth: true }))}>Continue without a password</button>
      <p className="text-subtle text-xs mt-2">No password = open dashboard on your network. Fine for a home LAN; don’t expose it to the internet without one.</p>
      {err && <p className="text-error text-sm mt-3">{err}</p>}
    </Centered>
  );
}

function Login({ onLogin }: { onLogin: () => void }) {
  const [pw, setPw] = useState(""); const [err, setErr] = useState(""); const [busy, setBusy] = useState(false);
  const submit = async () => { setErr(""); setBusy(true); try { await api.login(pw); onLogin(); } catch (e: any) { setErr(e.message); } finally { setBusy(false); } };
  return (
    <Centered>
      <h1>JTV Server</h1>
      <p className="text-muted text-sm mt-1 mb-5">Sign in to the dashboard.</p>
      <label className="text-sm text-muted">Admin password</label>
      <input className="input mt-1.5" type="password" value={pw} onChange={(e) => setPw(e.target.value)} onKeyDown={(e) => e.key === "Enter" && submit()} />
      <button className="btn-primary w-full mt-4" disabled={busy} onClick={submit}>{busy ? "Signing in…" : "Sign in"}</button>
      {err && <p className="text-error text-sm mt-3">{err}</p>}
    </Centered>
  );
}

/* ── layout (header + nav) ─────────────────────────────────────────────── */
function Layout({ onLogout }: { onLogout: () => void }) {
  const { theme, toggle } = useTheme();
  const logout = async () => { try { await api.logout(); } catch {} onLogout(); };
  const link = ({ isActive }: { isActive: boolean }) => `tab ${isActive ? "active" : ""}`;
  return (
    <div className="h-full flex flex-col">
      <header className="flex items-center gap-4 px-5 h-14 border-b border-border shrink-0">
        <div className="flex items-center gap-2">
          <span className="grid place-items-center w-7 h-7 rounded-md bg-accent text-white"><IconTv size={16} /></span>
          <span className="font-semibold tracking-tight">JTV<span className="text-muted font-normal"> Server</span></span>
        </div>
        <nav className="tabs ml-2">
          <NavLink to="/channels" className={link}><IconTv size={15} /> Channels</NavLink>
          <NavLink to="/guide" className={link}><IconGuide size={15} /> Guide</NavLink>
          <NavLink to="/account" className={link}><IconUser size={15} /> Account</NavLink>
        </nav>
        <div className="ml-auto flex items-center gap-2">
          <button className="icon-btn" title="Toggle theme" onClick={toggle}>{theme === "dark" ? <IconSun /> : <IconMoon />}</button>
          <button className="icon-btn" title="Log out" onClick={logout}><IconLogOut /></button>
        </div>
      </header>
      <main className="flex-1 min-h-0 overflow-y-auto"><Outlet /></main>
    </div>
  );
}

/* ── channels ──────────────────────────────────────────────────────────── */
function Channels() {
  const nav = useNavigate();
  const [channels, setChannels] = useState<Channel[] | null>(null);
  const [err, setErr] = useState(""); const [group, setGroup] = useState("All");
  const [q, setQ] = useState(""); const [favs, setFavs] = useState<Set<string>>(new Set());
  const [langs, toggleLang, clearLangs] = useLangFilter();
  const FAV = "Favorites";

  useEffect(() => {
    api.channels().then((r) => setChannels(r.channels)).catch((e) => setErr(e.message));
    api.favorites().then((r) => { const s = new Set(r.ids); setFavs(s); if (s.size) setGroup(FAV); }).catch(() => {});
  }, []);

  const toggleFav = async (id: string) => {
    const next = new Set(favs); next.has(id) ? next.delete(id) : next.add(id); setFavs(next);
    try { await api.toggleFavorite(id); } catch { setFavs(favs); }
  };
  const groups = useMemo(() => [FAV, "All", ...Array.from(new Set((channels ?? []).map((c) => c.group))).sort()], [channels]);
  const languages = useMemo(() => Array.from(new Set((channels ?? []).map((c) => c.language))).sort(), [channels]);
  const filtered = useMemo(() => (channels ?? [])
    .filter((c) => langs.size === 0 || langs.has(c.language))
    .filter((c) => group === FAV ? favs.has(c.id) : group === "All" || c.group === group)
    .filter((c) => c.name.toLowerCase().includes(q.toLowerCase())), [channels, group, q, favs, langs]);

  if (err) return <div className="empty-state">Couldn’t load channels: {err}</div>;
  if (!channels) return <div className="empty-state">Loading channels…</div>;

  return (
    <div className="flex h-full min-h-0">
      <aside className="w-52 shrink-0 border-r border-border overflow-y-auto py-3 px-2">
        {groups.map((g) => {
          const Ico = g === FAV ? null : categoryIcon(g);
          const active = group === g;
          return (
            <button key={g} onClick={() => setGroup(g)}
              className={`flex items-center gap-2.5 w-full text-left px-3 py-2 rounded-md text-sm truncate transition-colors ${active ? "bg-surface-2 text-fg" : "text-muted hover:text-fg hover:bg-surface-hover"}`}>
              {g === FAV ? <IconStar size={15} filled={active} className="shrink-0" /> : Ico && <Ico size={15} className="shrink-0 opacity-80" />}
              <span className="truncate">{g}</span>
            </button>
          );
        })}
      </aside>
      <section className="flex-1 min-w-0 overflow-y-auto">
        <div className="sticky top-0 z-10 bg-bg/95 backdrop-blur px-6 py-4 border-b border-border flex items-center gap-3">
          <div className="relative flex-1 max-w-sm">
            <IconSearch className="absolute left-3 top-1/2 -translate-y-1/2 text-subtle" size={16} />
            <input className="input pl-9" placeholder="Search channels…" value={q} onChange={(e) => setQ(e.target.value)} />
          </div>
          <LanguageMenu available={languages} langs={langs} onToggle={toggleLang} onClear={clearLangs} />
        </div>
        {filtered.length === 0 ? (
          <div className="empty-state">{group === FAV ? "No favourites yet — hover a channel and tap the star." : "No channels match."}</div>
        ) : (
          <div className="grid gap-4 p-6" style={{ gridTemplateColumns: "repeat(auto-fill, minmax(150px, 1fr))" }}>
            {filtered.map((c) => (
              <div key={c.id} className="relative group">
                <button onClick={() => nav(`/watch/${c.id}`, { state: { name: c.name } })}
                  className="card card-hover w-full !p-4 flex flex-col items-center gap-2 aspect-[4/5] justify-center">
                  <img src={c.logoUrl} alt="" className="h-16 w-16 rounded-full object-cover bg-surface-2"
                    onError={(e) => ((e.target as HTMLImageElement).style.visibility = "hidden")} />
                  <div className="text-sm text-center leading-snug line-clamp-2">{c.name}</div>
                  <div className="text-subtle text-xs text-center line-clamp-1">{c.group}</div>
                </button>
                <button onClick={() => toggleFav(c.id)} title="Favourite"
                  className={`absolute top-2 right-2 ${favs.has(c.id) ? "text-accent" : "text-subtle opacity-0 group-hover:opacity-100"} transition-opacity`}>
                  <IconStar size={18} filled={favs.has(c.id)} />
                </button>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

/* ── account ───────────────────────────────────────────────────────────── */
function Account() {
  const [st, setSt] = useState<{ loggedIn: boolean; mobile: string; updatedAt: number } | null>(null);
  const [mobile, setMobile] = useState(""); const [otp, setOtp] = useState("");
  const [step, setStep] = useState<1 | 2>(1);
  const [msg, setMsg] = useState<{ text: string; ok: boolean } | null>(null);
  const [authEnabled, setAuthEnabled] = useState(false); const [newPw, setNewPw] = useState("");

  const refresh = () => api.adminStatus().then(setSt).catch(() => {});
  useEffect(() => { refresh(); api.setupState().then((s) => setAuthEnabled(s.authEnabled)).catch(() => {}); }, []);
  const run = async (fn: () => Promise<unknown>, ok: string) => {
    setMsg(null); try { await fn(); setMsg({ text: ok, ok: true }); refresh(); } catch (e: any) { setMsg({ text: e.message, ok: false }); }
  };

  return (
    <div className="px-6 py-8">
      <div className="mx-auto w-full max-w-5xl">
        <h2 className="mb-5">Account &amp; settings</h2>
        {/* Multi-column masonry-ish grid: cards flow across columns; wide cards span both. */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-5 items-start">
          <div className="card">
            <div className="flex items-center justify-between">
              <h3>Jio account</h3>
              <span className={`badge ${st?.loggedIn ? "badge-success" : "badge-error"}`}>{st?.loggedIn ? "Signed in" : "Not signed in"}</span>
            </div>
            <div className="grid grid-cols-2 gap-y-2 mt-4 text-sm">
              <span className="text-muted">Mobile</span><span className="text-right">{st?.mobile || "—"}</span>
              <span className="text-muted">Tokens updated</span><span className="text-right">{st?.updatedAt ? new Date(st.updatedAt).toLocaleString() : "—"}</span>
            </div>
            <div className="flex gap-2 mt-5">
              <button className="btn-secondary" onClick={() => run(api.refresh, "Tokens refreshed.")}><IconRefresh /> Refresh</button>
              <button className="btn-ghost" onClick={() => run(api.logoutJio, "Jio account signed out.")}>Sign out Jio</button>
            </div>
          </div>

          <div className="card">
            <h3>Sign in to Jio</h3>
            {step === 1 ? (
              <div className="flex gap-2 mt-3">
                <input className="input" inputMode="numeric" placeholder="10-digit mobile number" value={mobile} onChange={(e) => setMobile(e.target.value)} />
                <button className="btn-primary shrink-0" onClick={() => run(async () => { await api.sendOtp(mobile); setStep(2); }, "OTP sent.")}>Send OTP</button>
              </div>
            ) : (
              <div className="flex gap-2 mt-3">
                <input className="input" inputMode="numeric" placeholder="Enter OTP" value={otp} onChange={(e) => setOtp(e.target.value)} />
                <button className="btn-primary shrink-0" onClick={() => run(async () => { await api.verifyOtp(mobile, otp); setStep(1); setOtp(""); }, "Signed in — all TVs will pick this up.")}>Verify</button>
                <button className="btn-ghost shrink-0" onClick={() => setStep(1)}>Back</button>
              </div>
            )}
            {msg && <p className={`text-sm mt-3 ${msg.ok ? "text-success" : "text-error"}`}>{msg.text}</p>}
          </div>

          <div className="lg:col-span-2"><CodesCard /></div>
          <HttpsCard />
          <EpgCard />

          <div className="card">
            <h3>Security</h3>
            {authEnabled ? (
              <div className="mt-2">
                <p className="text-muted text-sm mb-3">Dashboard is password-protected.</p>
                <button className="btn-secondary" onClick={() => run(async () => { await api.disableAuth(); setAuthEnabled(false); }, "Password removed — dashboard is open.")}>Remove password</button>
              </div>
            ) : (
              <div className="mt-2">
                <p className="text-muted text-sm mb-3">No password — open on your network (fine for a home LAN).</p>
                <div className="flex gap-2">
                  <input className="input" type="password" placeholder="New password" value={newPw} onChange={(e) => setNewPw(e.target.value)} />
                  <button className="btn-secondary shrink-0" onClick={() => run(async () => { await api.setPassword(newPw); setNewPw(""); setAuthEnabled(true); }, "Password set.")}>Set password</button>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

/* ── TV access codes ───────────────────────────────────────────────────── */
function CodesCard() {
  const [codes, setCodes] = useState<AccessCode[]>([]);
  const [name, setName] = useState(""); const [manual, setManual] = useState("");
  const [err, setErr] = useState(""); const [copied, setCopied] = useState<string | null>(null);

  const load = () => api.codes().then((r) => setCodes(r.codes)).catch(() => {});
  useEffect(() => { load(); }, []);
  const add = async () => {
    setErr("");
    try { await api.addCode({ name: name || "TV", code: manual.trim() || undefined }); setName(""); setManual(""); load(); }
    catch (e: any) { setErr(e.message); }
  };
  const copy = async (code: string) => { try { await navigator.clipboard.writeText(code); setCopied(code); setTimeout(() => setCopied(null), 1500); } catch {} };

  return (
    <div className="card">
      <h3>TV access codes</h3>
      <p className="text-muted text-sm mt-1">Short codes to connect each TV. Give one per device so you can revoke it later.</p>
      {codes.length > 0 && (
        <table className="table mt-4">
          <thead><tr><th>Name</th><th>Code</th><th></th></tr></thead>
          <tbody>
            {codes.map((c) => (
              <tr key={c.code}>
                <td>{c.name}</td>
                <td><span className="font-mono tracking-widest">{c.code}</span></td>
                <td className="text-right whitespace-nowrap">
                  <button className="icon-btn !w-8 !h-8 !border-0" title="Copy" onClick={() => copy(c.code)}>{copied === c.code ? <IconCheck className="text-success" /> : <IconCopy />}</button>
                  <button className="icon-btn !w-8 !h-8 !border-0 hover:!text-error" title="Delete" onClick={async () => { await api.deleteCode(c.code); load(); }}><IconTrash /></button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <div className="flex flex-wrap items-end gap-2 mt-4">
        <div className="grow min-w-[160px]"><label className="text-sm text-muted">Device name</label><input className="input mt-1" placeholder="e.g. Living Room TV" value={name} onChange={(e) => setName(e.target.value)} /></div>
        <div className="w-40"><label className="text-sm text-muted">Code (optional)</label><input className="input mt-1 font-mono" placeholder="auto-generate" value={manual} onChange={(e) => setManual(e.target.value)} /></div>
        <button className="btn-primary" onClick={add}><IconPlus /> Add</button>
      </div>
      {err && <p className="text-error text-sm mt-2">{err}</p>}
    </div>
  );
}

/* ── EPG source ────────────────────────────────────────────────────────── */
function EpgCard() {
  const [cfg, setCfg] = useState<{ mode: "native" | "xmltv"; url: string; status: string; lastSync: number; channels: number } | null>(null);
  const [url, setUrl] = useState("");
  const [msg, setMsg] = useState("");
  const load = () => api.epgConfig().then((c) => { setCfg(c); setUrl(c.url); }).catch(() => {});
  useEffect(() => { load(); }, []);
  const save = async (mode: "native" | "xmltv") => {
    setMsg(""); try { await api.setEpgConfig(mode, url); await load(); setMsg("Saved."); } catch (e: any) { setMsg(e.message); }
  };
  const refresh = async () => {
    setMsg("Downloading…"); try { const r = await api.refreshEpg(); setMsg(`Loaded ${r.channels} channels.`); load(); } catch (e: any) { setMsg(e.message); }
  };
  if (!cfg) return null;
  return (
    <div className="card">
      <h3>EPG source</h3>
      <p className="text-muted text-sm mt-1">Native = Jio’s per-channel guide. XMLTV = a downloaded guide file (.xml or .xml.gz).</p>
      <div className="tabs mt-3">
        <button className={`tab ${cfg.mode === "native" ? "active" : ""}`} onClick={() => save("native")}>Native</button>
        <button className={`tab ${cfg.mode === "xmltv" ? "active" : ""}`} onClick={() => save("xmltv")}>XMLTV</button>
      </div>
      <label className="text-sm text-muted mt-3 block">XMLTV URL</label>
      <div className="flex gap-2 mt-1">
        <input className="input font-mono text-sm" value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://…/epg.xml.gz" />
        <button className="btn-secondary shrink-0" onClick={() => save(cfg.mode)}>Save</button>
      </div>
      {cfg.mode === "xmltv" && (
        <div className="flex items-center gap-3 mt-3 flex-wrap">
          <button className="btn-secondary" onClick={refresh}>Refresh now</button>
          <span className="text-subtle text-xs">
            {cfg.status}{cfg.channels ? ` · ${cfg.channels} channels` : ""}{cfg.lastSync ? ` · ${new Date(cfg.lastSync).toLocaleString()}` : ""}
          </span>
        </div>
      )}
      {msg && <p className="text-muted text-sm mt-2">{msg}</p>}
    </div>
  );
}

/* ── HTTPS ─────────────────────────────────────────────────────────────── */
function HttpsCard() {
  const [info, setInfo] = useState<{ httpsPort: number; hasCert: boolean } | null>(null);
  const [msg, setMsg] = useState("");
  useEffect(() => { api.https().then(setInfo).catch(() => {}); }, []);
  const url = info ? `https://${location.hostname}:${info.httpsPort}` : "";
  return (
    <div className="card">
      <h3>HTTPS (for DRM channels)</h3>
      <p className="text-muted text-sm mt-1">
        Encrypted DRM channels need a secure context in the browser. Open the HTTPS URL and accept the
        one-time “not private” warning (self-signed certificate).
      </p>
      {info && (
        <div className="flex items-center gap-2 mt-3">
          <input className="input font-mono text-sm" readOnly value={url} onFocus={(e) => e.target.select()} />
          <a className="btn-secondary shrink-0" href={url} target="_blank" rel="noreferrer">Open</a>
        </div>
      )}
      <button className="btn-ghost mt-3" onClick={() => api.regenerateHttps().then((r) => setMsg(r.note)).catch((e) => setMsg(e.message))}>Regenerate certificate</button>
      {msg && <p className="text-muted text-sm mt-2">{msg}</p>}
    </div>
  );
}
