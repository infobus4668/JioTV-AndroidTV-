# JTV Server (companion)

Self-hostable **credential broker + streaming proxy + web player** for the JTV Android TV app.

- **Log in once, everywhere** — stores your Jio login and refreshes tokens centrally; every TV pulls current credentials instead of logging in separately.
- **Watch in the browser** *(Phase 2)* — proxies channels (incl. DRM/Widevine via a license proxy) with a web UI and local playback.
- **Self-host** — single Docker image (Node/TypeScript API + web UI), one `docker compose up`.

## Status

- ✅ **Phase 1:** credential broker + admin dashboard, SQLite store, central token-refresh scheduler.
  The Android app's **Settings → Sign-in Method → Connect to JTV Proxy Server** consumes
  `GET /api/credentials`. (Smoke-tested.)
- ✅ **Phase 2:** channel list + stream proxy (live `__hdnea__` injection + manifest rewrite) +
  **Widevine license proxy** + a **React/Vite/Tailwind + Shaka** web player. Typecheck/build/serve
  verified; the channel list fetches live from Jio. *Live DRM playback needs a real Jio login + HTTPS
  in a browser (on-device test).*
- 🟡 **Phase 3 (in progress):** EPG now/next (`GET /api/epg/:id`, live-verified). Next: catch-up,
  favorites-sync across TVs, multi-account profiles.

## Web player (Phase 2)

`web/` is a Vite/React/TypeScript/Tailwind SPA (built to `web/dist`, served by Fastify). It plays
channels via [Shaka Player](https://github.com/shaka-project/shaka-player): a request filter routes
every manifest/segment through `/api/proxy` (server injects the fresh token), and Widevine license
requests go to `/api/play/:id/license`. Browser Widevine requires HTTPS — use the Caddy profile.

## Quick start (Docker)

```bash
cd server
cp .env.example .env        # then edit the two secrets:
#   JTV_SERVER_TOKEN  — long random string the TVs use   (openssl rand -hex 24)
#   ADMIN_PASSWORD    — password for the web dashboard
docker compose up -d --build
```

Open `http://<host>:8080`, sign in with `ADMIN_PASSWORD`, then **Send OTP → Verify** with your Jio
number. On each TV: **Settings → Sign-in Method → Connect to JTV Proxy Server**, enter the server URL
and `JTV_SERVER_TOKEN`.

> Browser Widevine playback (Phase 2) needs HTTPS. An optional Caddy profile is included:
> edit `Caddyfile` with your domain, then `docker compose --profile https up -d`.

## Local dev (Node 22+)

```bash
cd server
npm install
JTV_SERVER_TOKEN=dev ADMIN_PASSWORD=dev npm run dev   # tsx watch on :8080
npm run typecheck   # tsc --noEmit
npm run build && npm start
```

## API

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET  | `/api/status` | none | Health + whether a login exists |
| POST | `/api/admin/login` | password | Start a dashboard session (cookie) |
| POST | `/api/admin/logout` | cookie | End the dashboard session |
| GET  | `/api/admin/status` | cookie | Login state, mobile, last-refresh |
| POST | `/api/login/otp/send` | cookie | Send OTP to a Jio number |
| POST | `/api/login/otp/verify` | cookie | Verify OTP, store credentials |
| POST | `/api/admin/refresh` | cookie | Force a token refresh now |
| POST | `/api/admin/logout-jio` | cookie | Clear the stored Jio account |
| GET  | `/api/credentials` | **bearer** `JTV_SERVER_TOKEN` | **TVs pull the shared AuthData here** |

## Layout

```
server/
├─ src/
│  ├─ config.ts            env + Jio constants
│  ├─ jio/                 auth.ts · tokens.ts · hdnea.ts · types.ts  (ported from JioApiClient.kt)
│  ├─ store/db.ts          SQLite credential store (better-sqlite3)
│  ├─ refresh.ts           central token-refresh scheduler
│  ├─ api/routes.ts        Fastify routes + admin/bearer auth
│  └─ server.ts            bootstrap (serves web/ + API)
├─ web/                    Phase 1 dashboard (static). Phase 2: React/Vite/Tailwind + Shaka player.
├─ Dockerfile · docker-compose.yml · Caddyfile
```
