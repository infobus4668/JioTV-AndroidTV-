# JTV v1.2 — stability & security must-fixes

Built on v1.1 (`versionCode 2`, `versionName 1.2`, same signing key — installs
directly over v1.1 with `adb install -r`, login and settings kept). Only fixes that
qualify as correctness/security are in; no UI polish, no feature work. Deferred
(midnight EPG, Sony Yay start-point, atomic server config, native XMLTV guide) stays
deferred — see bottom.

## Must-fix 1: token refresh single-flight + honest 403 (app)
- `JioApiClient.refreshToken` now runs under a `Mutex`, re-reads credentials inside the
  lock, and remembers a Jio-rejected session (`SessionExpiredException`) instead of
  spamming the token service on every channel.
- `getStreamUrl` 403 no longer triggers a refresh: a 403 is per-channel
  (delisted/licensing, e.g. Zee list entries Jio still returns but blocks) and fails fast
  with `ChannelBlockedException`. 401/419 still refresh exactly once.
- Player (`TvPlayerScreen`) treats 403-blocked live channels as "Jio isn't providing this
  channel right now. Press CH+/CH- for the next one." instead of the wrong
  "Login expired", and surfaces it immediately without 5 pointless retries. Replays keep
  their existing fail-fast path. Why it qualifies: parallel refreshes could burn the
  fragile Jio refresh token ("refresh token not found") and force a full OTP re-login.

## Must-fix 2: Voice Boost Off no longer stops playback (app)
- `DialogueAudioProcessor.queueInput` ignores Media3's empty buffer (`!hasRemaining()`
  return), snapshots the volatile level once per buffer, resets filter history when
  returning from Off, and processes whole stereo frames only. Why it qualifies: with
  Voice Boost Off, every channel change/reload fed the empty shared buffer into
  `replaceOutputBuffer(0)` → `IllegalArgumentException` → "Playback stopped".
  Workaround before this fix was leaving Voice Boost on Medium (the default).

## Must-fix 3: proxy only fetches Jio hosts (server)
- `proxyUpstream` rejects any `u=` URL whose host isn't `jio.com`/`*.jio.com` before
  attaching credential headers. Why it qualifies: `/api/proxy` and `/seg` accept a
  client-supplied URL; on an open-LAN server anyone could point it at an evil host and
  harvest `ssoToken` / `__hdnea__` cookie. Internal `masterAlive` probes (server-resolved
  URLs only) are unchanged.

## Install
```bash
adb connect <YOUR_TV_IP>:5555
adb install -r JTV-v1.2.apk
```
No uninstall needed when coming from v1.1 (same key, higher `versionCode`). Coming from
a differently-signed build, uninstall once: `adb uninstall com.fenyx.jtv`.

## Deferred (does not qualify for this release)
- Midnight EPG next-day fetch, Sony Yay recorded-schedule start-point, app-side manifest
  probe for live Zee fallback, server atomic `config.json` + `.bak`, native `/epg.xml.gz`
  builder, standby/autostart. Each is real but rare/narrow (specific channels, hours, or
  power-cut windows) — next pass, not this one.

---
# JTV v1.1 — device-adaptive Live TV

A device-adaptive Live TV client built on [F-e-n-y-x/JioTV-AndroidTV-](https://github.com/F-e-n-y-x/JioTV-AndroidTV-)
— all credit for the original app belongs to its author. Not affiliated with JioTV / Reliance Jio.
Signed with this project's own release key: if you installed a build signed with a different key,
uninstall it once first.

This is the **single maintained release** of this repository — `versionCode 1`, `versionName 1.1`.
Everything below describes what THIS build contains.

## Highlights
- **Plays DRM channels** (Star, Sony, Zee, Colors…) with the stream token refreshed in the background.
- **Instant startup** — the channel list loads from cache, then revalidates quietly in the background.
- **Adaptive UX per device** — phones/tablets get full touch UX; Android TV/Fire TV gets pure D-pad;
  PC emulators and air-mouse boxes get hover-to-focus + click everywhere.
- **One login for all devices** via the companion server, or direct phone-number + OTP login.

## Manage channels from Settings
- **Settings → Channels → Hide / Unhide Channels** — the single hidden-channels manager. Lists every
  channel in your selected language scope with a one-press Hide/Unhide toggle per row, **All / Hidden /
  Visible** filter chips with live counts, and **Show all** to unhide everything. The Hidden view
  resolves hidden channels across all languages, so anything ever hidden stays findable. Hidden
  channels disappear from Home, search, EPG and zap lists until unhidden. Works with remote (D-pad +
  page jumps), mouse (hover/click/wheel) and touch (48dp rows).

## On-screen player keys — fully customizable
- Settings → **On-Screen Player Keys** (touch/mouse devices):
  - Toggle each key on/off: Channels ☰, Categories 🗂, Programmes 📅, Number #, Aspect ⛶, Rotate ⟳,
    PiP ⧉, Pause ⏸, Stream info 📊, Settings ⚙.
  - **Dock Position**: bottom center / left / right.
  - **Split Dock Into Two Groups**: navigation keys anchor bottom-left, playback keys bottom-right —
    each group wraps inside its own half so they never collide.
  - Edge ▲▼ zap pill (Next / Previous) on the right edge, toggleable.

## Input & UX fixes in this line
- **Tap anywhere outside a player panel closes it** — a close-scrim sits under the channel/category/
  settings panels and over the video + volume strip, so closing works on portrait phones (previously
  the panel + volume strip covered the whole screen) and on emulators.
- The **tune-in splash no longer overlaps the dock** (the dock hides while the channel card shows).
- **OK now activates focused rows** in the player's settings panel and programme sheet (previously
  browse-only on remote); error-screen arrows move focus; dedicated ⏩/⏪ remote keys seek ±30 s in a
  replay or open the programme sheet on live; CH± no longer zaps underneath open panels.
- **EPG time-grid is D-pad alive on entry** (focus + last-played restore), EPG rows mode restores its
  position, and short programmes no longer wrap their time label into gibberish.
- **Refresh actually refreshes** — the top-bar button force-reloads the channel list from the network
  (bypassing the 24h cache) with a proper in-button spinner. Pull-to-refresh was removed.
- **OTP resend** with a 30 s countdown; focused login buttons stay visibly focused while loading.
- **Server setup on phones** keeps its buttons above the keyboard (IME padding + scroll); TV-only
  auto-focus of fields; connect spinner.
- Double-press **Back** exits the app from Home (no more instant exits); the A–Z rail fits every
  letter on phones; text overflow fixed across both orientations (banner, sheets, replay bar, cards,
  dialogs); "Press OK" copy adapts to mouse devices.

## Core features (carried in this build)
- **Full EPG**: rows and a 5-hour time-grid with a red now-marker, catch-up replay from past
  programmes (▶ badge), and ±24 h time-shift.
- **Channel language filter** (multi-select with counts) applied everywhere; A–Z or channel-number
  sort; category icons; favorites.
- **Player**: audio-track/subtitle picker, language-feed switcher, Voice Boost dialogue enhancer +
  auto volume, sleep timer, aspect cycler, PiP, zap-preview strip, A–Z jump rail, stream-stats overlay.
- **Companion server** (optional, in `server/`): one shared login for every device, a responsive web
  player, and M3U/EPG for external players.

## Install
```bash
adb connect <YOUR_TV_IP>:5555
adb uninstall com.fenyx.jtv
adb install JTV-v1.1.apk
```
This is a **fresh v1.1** (versionCode 1): any previously installed JTV build must be uninstalled once
before installing — the lower build number blocks direct updates, even though the signing key is the
same.

## Security & tooling
- Jio tokens are excluded from Android cloud/device backups; `Log.v/d` are stripped from release builds.
- The signing keystore stays out of the repo tree (loaded via the `JTV_SIGNING_PROPS` env var).
