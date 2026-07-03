import { useEffect, useMemo, useState } from "react";
import { api, type Channel } from "./api";
import { Player } from "./Player";

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
  // Auth off → open dashboard (no login gate).
  if (!state.authEnabled) return <Shell onLogout={() => setAuthed(false)} />;
  if (!authed) return <Login onLogin={() => setAuthed(true)} />;
  return <Shell onLogout={() => setAuthed(false)} />;
}

function SetupWizard({ onDone }: { onDone: () => void }) {
  const [pw, setPw] = useState("");
  const [pw2, setPw2] = useState("");
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);
  const submit = async () => {
    setErr("");
    if (pw.length < 4) return setErr("Use at least 4 characters.");
    if (pw !== pw2) return setErr("Passwords don’t match.");
    setBusy(true);
    try { await api.setup({ password: pw }); onDone(); }
    catch (e: any) { setErr(e.message); } finally { setBusy(false); }
  };
  const skip = async () => {
    setErr(""); setBusy(true);
    try { await api.setup({ disableAuth: true }); onDone(); }
    catch (e: any) { setErr(e.message); } finally { setBusy(false); }
  };
  return (
    <div className="grid place-items-center h-full p-6">
      <div className="card p-7 w-full max-w-sm">
        <h1 className="text-2xl font-bold">Welcome to JTV <span className="text-primary">●</span></h1>
        <p className="text-muted text-sm mt-1 mb-5">First run — create an admin password. It’s saved on
          the server (no <code>.env</code> needed).</p>
        <label className="text-sm text-muted">New admin password</label>
        <input className="input mt-1.5" type="password" value={pw} onChange={(e) => setPw(e.target.value)} />
        <label className="text-sm text-muted mt-3 block">Confirm password</label>
        <input className="input mt-1.5" type="password" value={pw2} onChange={(e) => setPw2(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && submit()} />
        <button className="btn-primary w-full mt-4" disabled={busy} onClick={submit}>
          {busy ? "Creating…" : "Create & continue"}
        </button>
        <button className="btn-ghost w-full mt-2" disabled={busy} onClick={skip}>
          Continue without a password
        </button>
        <p className="text-muted text-[11px] mt-2">No password = open dashboard on your network. Fine
          for a home LAN; don’t expose the server to the internet without one.</p>
        {err && <div className="text-danger text-sm mt-3">{err}</div>}
      </div>
    </div>
  );
}

function Login({ onLogin }: { onLogin: () => void }) {
  const [pw, setPw] = useState("");
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);
  const submit = async () => {
    setErr(""); setBusy(true);
    try { await api.login(pw); onLogin(); }
    catch (e: any) { setErr(e.message); } finally { setBusy(false); }
  };
  return (
    <div className="grid place-items-center h-full p-6">
      <div className="card p-7 w-full max-w-sm">
        <h1 className="text-2xl font-bold">JTV Server <span className="text-primary">●</span></h1>
        <p className="text-muted text-sm mt-1 mb-5">Credential broker &amp; web player</p>
        <label className="text-sm text-muted">Admin password</label>
        <input className="input mt-1.5" type="password" value={pw}
          onChange={(e) => setPw(e.target.value)} onKeyDown={(e) => e.key === "Enter" && submit()} />
        <button className="btn-primary w-full mt-4" disabled={busy} onClick={submit}>
          {busy ? "Signing in…" : "Sign in"}
        </button>
        {err && <div className="text-danger text-sm mt-3">{err}</div>}
      </div>
    </div>
  );
}

function Shell({ onLogout }: { onLogout: () => void }) {
  const [tab, setTab] = useState<"channels" | "account">("channels");
  const logout = async () => { try { await api.logout(); } catch {} onLogout(); };
  return (
    <div className="h-full flex flex-col">
      <header className="flex items-center gap-4 px-6 py-3 border-b border-border bg-surface">
        <div className="font-bold text-lg">JTV <span className="text-primary">Server</span></div>
        <nav className="flex gap-1 ml-4">
          {(["channels", "account"] as const).map((t) => (
            <button key={t} onClick={() => setTab(t)}
              className={`px-3 py-1.5 rounded-lg text-sm capitalize ${tab === t ? "bg-surface2 text-primary" : "text-muted hover:text-text"}`}>
              {t}
            </button>
          ))}
        </nav>
        <div className="ml-auto"><button className="btn-ghost" onClick={logout}>Log out</button></div>
      </header>
      <main className="flex-1 overflow-auto">
        {tab === "channels" ? <Channels /> : <Account />}
      </main>
    </div>
  );
}

function Channels() {
  const [channels, setChannels] = useState<Channel[] | null>(null);
  const [err, setErr] = useState("");
  const [group, setGroup] = useState<string>("All");
  const [q, setQ] = useState("");
  const [playing, setPlaying] = useState<Channel | null>(null);
  const [favs, setFavs] = useState<Set<string>>(new Set());
  const FAV = "★ Favorites";

  useEffect(() => {
    api.channels().then((r) => setChannels(r.channels)).catch((e) => setErr(e.message));
    api.favorites().then((r) => setFavs(new Set(r.ids))).catch(() => {});
  }, []);

  const toggleFav = async (id: string) => {
    const next = new Set(favs);
    next.has(id) ? next.delete(id) : next.add(id); // optimistic
    setFavs(next);
    try { await api.toggleFavorite(id); } catch { setFavs(favs); }
  };

  const groups = useMemo(
    () => [FAV, "All", ...Array.from(new Set((channels ?? []).map((c) => c.group))).sort()],
    [channels]
  );
  const filtered = useMemo(() => (channels ?? [])
    .filter((c) => group === FAV ? favs.has(c.id) : group === "All" || c.group === group)
    .filter((c) => c.name.toLowerCase().includes(q.toLowerCase())), [channels, group, q, favs]);

  if (err) return <div className="p-8 text-danger">Couldn’t load channels: {err}</div>;
  if (!channels) return <div className="p-8 text-muted">Loading channels…</div>;

  return (
    <div className="flex h-full">
      <aside className="w-56 shrink-0 border-r border-border overflow-auto p-2">
        {groups.map((g) => (
          <button key={g} onClick={() => setGroup(g)}
            className={`block w-full text-left px-3 py-2 rounded-lg text-sm truncate ${group === g ? "bg-surface2 text-primary" : "text-muted hover:text-text"}`}>
            {g}
          </button>
        ))}
      </aside>
      <section className="flex-1 overflow-auto p-5">
        <input className="input mb-4 max-w-sm" placeholder="Search channels…" value={q} onChange={(e) => setQ(e.target.value)} />
        <div className="grid gap-3" style={{ gridTemplateColumns: "repeat(auto-fill, minmax(140px, 1fr))" }}>
          {filtered.map((c) => (
            <div key={c.id} className="relative">
              <button onClick={() => setPlaying(c)}
                className="card w-full p-3 flex flex-col items-center gap-2 hover:border-primary transition-colors focus:outline-none focus:ring-2 focus:ring-primary">
                <img src={c.logoUrl} alt="" className="h-14 w-14 rounded-full object-cover bg-surface2"
                  onError={(e) => ((e.target as HTMLImageElement).style.visibility = "hidden")} />
                <div className="text-xs text-center line-clamp-2">{c.name}</div>
                <span className="pill bg-live/15 text-live text-[10px]">LIVE</span>
              </button>
              <button onClick={() => toggleFav(c.id)} title="Toggle favorite"
                className={`absolute top-1.5 right-1.5 text-lg leading-none ${favs.has(c.id) ? "text-yellow-400" : "text-muted hover:text-text"}`}>
                {favs.has(c.id) ? "★" : "☆"}
              </button>
            </div>
          ))}
        </div>
        {filtered.length === 0 && <div className="text-muted mt-8">No channels match.</div>}
      </section>
      {playing && <Player channelId={playing.id} name={playing.name} onClose={() => setPlaying(null)} />}
    </div>
  );
}

function Account() {
  const [st, setSt] = useState<{ loggedIn: boolean; mobile: string; updatedAt: number } | null>(null);
  const [mobile, setMobile] = useState("");
  const [otp, setOtp] = useState("");
  const [step, setStep] = useState<1 | 2>(1);
  const [msg, setMsg] = useState<{ text: string; ok: boolean } | null>(null);
  const [token, setToken] = useState("");
  const [copied, setCopied] = useState(false);
  const [authEnabled, setAuthEnabled] = useState(false);
  const [newPw, setNewPw] = useState("");

  const refresh = () => api.adminStatus().then(setSt).catch(() => {});
  useEffect(() => {
    refresh();
    api.adminConfig().then((r) => setToken(r.serverToken)).catch(() => {});
    api.setupState().then((s) => setAuthEnabled(s.authEnabled)).catch(() => {});
  }, []);

  const run = async (fn: () => Promise<unknown>, okText: string) => {
    setMsg(null);
    try { await fn(); setMsg({ text: okText, ok: true }); refresh(); }
    catch (e: any) { setMsg({ text: e.message, ok: false }); }
  };

  return (
    <div className="p-6 max-w-lg">
      <div className="card p-5">
        <div className="flex items-center justify-between">
          <span className="text-muted">Jio account</span>
          <span className={`pill ${st?.loggedIn ? "bg-ok/15 text-ok" : "bg-danger/15 text-danger"}`}>
            {st?.loggedIn ? "Signed in" : "Not signed in"}
          </span>
        </div>
        <div className="flex justify-between py-2 border-t border-border mt-3 text-sm">
          <span className="text-muted">Mobile</span><span>{st?.mobile || "—"}</span>
        </div>
        <div className="flex justify-between py-2 border-t border-border text-sm">
          <span className="text-muted">Tokens updated</span>
          <span>{st?.updatedAt ? new Date(st.updatedAt).toLocaleString() : "—"}</span>
        </div>
      </div>

      <div className="card p-5 mt-4">
        <h3 className="font-semibold mb-3">Sign in to Jio</h3>
        {step === 1 ? (
          <>
            <input className="input" inputMode="numeric" placeholder="10-digit mobile number"
              value={mobile} onChange={(e) => setMobile(e.target.value)} />
            <button className="btn-primary w-full mt-3"
              onClick={() => run(async () => { await api.sendOtp(mobile); setStep(2); }, "OTP sent.")}>
              Send OTP
            </button>
          </>
        ) : (
          <>
            <input className="input" inputMode="numeric" placeholder="Enter OTP"
              value={otp} onChange={(e) => setOtp(e.target.value)} />
            <div className="flex gap-2 mt-3">
              <button className="btn-primary flex-1"
                onClick={() => run(async () => { await api.verifyOtp(mobile, otp); setStep(1); setOtp(""); }, "Signed in — all TVs will pick this up.")}>
                Verify &amp; save
              </button>
              <button className="btn-ghost" onClick={() => setStep(1)}>Back</button>
            </div>
          </>
        )}
      </div>

      <div className="flex gap-2 mt-4">
        <button className="btn-ghost" onClick={() => run(api.refresh, "Tokens refreshed.")}>Refresh tokens</button>
        <button className="btn-ghost" onClick={() => run(api.logoutJio, "Jio account signed out.")}>Sign out Jio</button>
      </div>
      {msg && <div className={`mt-3 text-sm ${msg.ok ? "text-ok" : "text-danger"}`}>{msg.text}</div>}

      <div className="card p-5 mt-4">
        <h3 className="font-semibold mb-1">TV access token</h3>
        <p className="text-muted text-xs mb-3">Paste this into each TV to connect it. Keep it private.</p>
        <div className="flex gap-2">
          <input className="input font-mono text-sm" readOnly value={token} onFocus={(e) => e.target.select()} />
          <button className="btn-ghost shrink-0" onClick={async () => {
            try { await navigator.clipboard.writeText(token); setCopied(true); setTimeout(() => setCopied(false), 1500); } catch {}
          }}>{copied ? "Copied ✓" : "Copy"}</button>
        </div>
      </div>

      <div className="card p-5 mt-4">
        <h3 className="font-semibold mb-1">Security</h3>
        {authEnabled ? (
          <>
            <p className="text-muted text-xs mb-3">Dashboard is password-protected.</p>
            <button className="btn-ghost" onClick={() =>
              run(async () => { await api.disableAuth(); setAuthEnabled(false); }, "Password removed — dashboard is now open.")}>
              Remove password
            </button>
          </>
        ) : (
          <>
            <p className="text-muted text-xs mb-3">No password — open on your network (fine for a home LAN).</p>
            <div className="flex gap-2">
              <input className="input" type="password" placeholder="New password" value={newPw} onChange={(e) => setNewPw(e.target.value)} />
              <button className="btn-ghost shrink-0" onClick={() =>
                run(async () => { await api.setPassword(newPw); setNewPw(""); setAuthEnabled(true); }, "Password set.")}>
                Set password
              </button>
            </div>
          </>
        )}
      </div>

      <p className="text-muted text-xs mt-6 leading-relaxed">
        On each TV: <b>Settings → Sign-in Method → Connect to JTV Proxy Server</b>, enter this server’s
        URL and the token above. TVs pull credentials from here and never log in again.
      </p>
    </div>
  );
}
