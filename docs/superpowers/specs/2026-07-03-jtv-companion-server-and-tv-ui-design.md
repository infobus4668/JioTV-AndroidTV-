# JTV — Companion Server, App Dual-Setup, and Android TV UI Overhaul

**Date:** 2026-07-03
**Status:** Approved for phased build (Android first, then server)
**Repo layout:** monorepo — `android/` (TV app) + `server/` (companion) + `docs/`

---

## 1. Goals

1. **Log in once, everywhere** — a self-hostable server holds the Jio login, refreshes tokens centrally; every TV pulls current credentials instead of logging in separately.
2. **Watch in the browser** — the server proxies channels (incl. DRM/Widevine) with a polished web player and local playback.
3. **App dual-setup** — the TV app offers, at first boot and in Settings, **two setup methods**: Phone (OTP) or JTV Proxy Server.
4. **Proper Android TV UI** — bring every screen up to Google's TV design guidelines (overscan, focus, typography, tv-material components).

## 2. Non-goals (v1)
- Multiple Jio accounts / profiles (single shared account in v1; multi-account → Phase 3).
- App playing **through** the server proxy (app stays credential-sync; it decrypts DRM itself). Proxy playback is a browser feature.
- Catch-up/timeshift, favorites-sync across TVs (→ Phase 3).

---

## 3. Workstream A — Android TV UI / Design System (build first)

### 3.1 Problems found in the current app
- `Type.kt` only defines `bodyLarge`; all other styles fall back to **phone-sized** M3 defaults — too small for a 10-foot UI.
- No **overscan-safe margins** (only `safeDrawingPadding()`); content can sit at the screen edge on real TVs. Guideline: 48dp horizontal / 27dp vertical.
- Focus indicator is weak: `focusedScale = 1.0f` disables the focus "pop"; only a container-color/border change remains. Guideline: border + subtle scale + glow.
- **Mobile Material3 mixed into TV screens**: `OutlinedTextField` (Login), `CircularProgressIndicator` / `LinearProgressIndicator` (Main/Settings). Google warns against mixing.
- No explicit **initial focus / focus restoration** on several screens; the remote can land nowhere on entry.
- Text-entry on TV (EPG URL `BasicTextField`) is awkward.

### 3.2 Design-system foundation (tokens)
- **`theme/Type.kt`** — full TV type scale (display/headline/title/body/label), sized up for 10-foot legibility. Auto-improves every screen that uses `MaterialTheme.typography.*`.
- **`theme/Dimens.kt`** (new) — `TvDimens` spacing scale + overscan constants; `Modifier.tvOverscan()` (48dp/27dp) and `Modifier.tvScreen()` scaffold padding.
- **`theme/TvDefaults.kt`** (new) — shared `ClickableSurfaceDefaults` (border + scale 1.05 + glow) + a `tvFocusable`/`focusRestorer` helper, so focus looks consistent everywhere.
- Keep the existing dark palette (`Color.kt`); add a focus-glow usage.

### 3.3 Screen-by-screen work
- **Setup (new, `ui/setup/SetupScreen`)** — first-boot chooser: two large focusable TV cards ("Sign in with Jio number" / "Connect to JTV Proxy Server"), initial focus on the first.
- **ServerSetup (new, `ui/setup/ServerSetupScreen`)** — server URL + access-token entry (TV-friendly), "Connect" → pulls credentials.
- **Login** — replace mobile `OutlinedTextField` with a TV display field; ensure numpad has initial focus + clear focus visuals; overscan.
- **Main** — overscan margins; restore focus scale/glow on cards + sidebar; explicit initial focus + `focusRestorer` between sidebar and grid; TV-size typography (via tokens).
- **Settings** — overscan; TV components; larger focus targets; initial focus + scroll-into-view; keep custom toggles (they're focusable rows).
- **Player** — overscan for overlays/side panel; keep existing key handling; larger overlay typography.

### 3.4 Verification
Build (`assembleDebug`) after each screen. Manual focus walk-through noted as device-dependent (can't run on a real TV here).

---

## 4. Workstream B — Companion Server (`server/`, build second)

**Stack:** Fastify (TypeScript) API + React/Vite/Tailwind + shadcn/ui + Shaka Player; SQLite (`better-sqlite3`); single multi-stage Docker image; optional Caddy profile for HTTPS.

### 4.1 Modules
- `src/jio/*` — port of the app's proven logic (OTP `send`/`verify`, `geturl`, `refreshtoken`, `__hdnea__` extract/rewrite/expiry), header-accurate against the Kodi plugin + jiotv_go.
- `src/store/*` — SQLite: credentials (single account v1), config, server access token.
- `src/api/*` — Fastify routes (below).
- `src/proxy/*` — manifest rewrite, segment proxy (injects live `__hdnea__`), **Widevine license proxy**.
- `web/*` — React app: admin login, **status dashboard** (token expiry countdown + re-login), channel grid, Shaka player.

### 4.2 API (Phase 1 = broker; Phase 2 = proxy/player)
- `POST /api/login/otp/send` `{ mobile }` — admin setup.
- `POST /api/login/otp/verify` `{ mobile, otp }` → store `AuthData`.
- `GET /api/credentials` *(bearer `JTV_SERVER_TOKEN`)* → fresh `AuthData` for TVs. **This is what the app's Server-mode calls.**
- `GET /api/status` → token validity/expiry (for dashboard + health).
- *(P2)* `GET /api/channels`, `GET /api/play/:id/manifest.mpd|master.m3u8`, `GET /api/segment`, `POST /api/license/:id`.

### 4.3 Central token refresh
Scheduler refreshes SSO/auth tokens before expiry via `refreshtoken`; `/api/credentials` always returns valid tokens, so a TV that gets a 401 simply re-pulls — no per-TV re-login.

### 4.4 Security
`JTV_SERVER_TOKEN` gates machine endpoints; admin password gates the dashboard; Caddy profile for HTTPS (required for browser Widevine/EME; localhost exempt).

### 4.5 Docker
Multi-stage (build web → Fastify serves static + API); volume `/data` for SQLite; env for port/token; healthcheck; `docker-compose.yml` (+ optional `caddy` profile).

---

## 5. Workstream C — App "Server mode" (with A)
- `data/ServerClient.kt` — calls `{serverUrl}/api/credentials` with the access token; maps JSON → `AuthData`.
- `SettingsManager` — new keys: `setupMode` (`phone` | `server`), `serverUrl`, `serverToken`.
- `Navigation.kt` — first boot with no auth + no setup → `SetupScreen`; server path stores pulled `AuthData` via existing `saveAuthData`, then the normal player/DRM flow is unchanged. On 401, re-pull from server.
- Settings — "Account / Setup" section to view and switch/reconfigure both methods.

---

## 6. Build order (as requested)
1. **A — TV design-system foundation** (tokens: Type/Dimens/TvDefaults) → apply per screen.
2. **A + C — Setup/ServerSetup screens + ServerClient + SettingsManager + Navigation** (dual-setup).
3. **B Phase 1 — server broker + admin dashboard + Docker** (log in once).
4. **B Phase 2 — proxy + Widevine web player.**
5. **Phase 3 — EPG/catch-up/favorites-sync, multi-account.**

Each step ends with a green build (`assembleDebug` for the app; `docker build` for the server).

## 7. References
jiotv_go (feature/EPG/M3U reference), Kodi plugin `dineshintry/plugin.kodi.jiotv` (header source), TS-JioTV (catch-up), Shaka Player license-server-auth (`registerRequestFilter` for Widevine headers), Android TV design guidelines (overscan/focus/typography).
