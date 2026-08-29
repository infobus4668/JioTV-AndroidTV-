# JTV v1.5.3-mod — device-adaptive Live TV (single release)

A device-adaptive fork of [F-e-n-y-x/JioTV-AndroidTV-](https://github.com/F-e-n-y-x/JioTV-AndroidTV-)
`v1.5.2` — all credit for the original app belongs to the original author. Not affiliated with JioTV/
Reliance Jio. Signed with the mod's own release key: uninstall any build installed with a different
key first.

This is the **single maintained release** of this repository (all previous releases and tags were
removed; everything is folded into this one release).

## Touch player overhaul (new in v1.5.3)
- **Tap outside any panel to close it** — the channel list, categories, player settings, programme
  sheet, stats, numpad and zap strip all close with a tap outside, peeling in the same order as Back.
  The banner + control dock can no longer get stuck on screen behind panels (the 5-second auto-hide
  re-arms), and the translucent panels no longer show the banner/progress line as ghost text.
- **Volume by right-edge swipe** — the on-screen volume icon and slider panel are gone. Swipe up/down
  along the right ~30% of the screen (portrait AND landscape) to change volume; a small percentage
  readout fades in while you swipe. Taps in the strip still toggle the controls.
- **Configurable control dock** — Settings → **Player Touch Dock** lets you enable/disable each
  on-screen button (Channels ☰, Programmes 📅, Number #, Aspect ⛶, Rotate 🔄, PiP ⧉, Pause ⏸, Stats 📊,
  Settings ⚙) plus the edge ▲▼ zap keys. Defaults keep only the essentials.
- **"Jump to LIVE" is a real button** — while paused: clicking exits a catch-up replay and reloads the
  live feed, or snaps live playback back to the edge after a long stall. (Previously it was decorative
  text — phones had no ⏭ key to act on.)
- **Orientation follows the device** — the player opens portrait, rotates with the system auto-rotate
  setting, and the 🔄 dock button toggles portrait ↔ landscape explicitly for auto-rotate-off users.
  The old forced-landscape lock is gone.
- **Immersive fix for MIUI/HyperOS** — some OEMs ignored the launch-time system-bar hide and re-showed
  the status bar over the player (clipping the channel banner). The hidden state is re-asserted on
  every window focus.
- **EPG & visual polish** — "NOW" header key no longer wraps into "NO/W"; half-hour timeline ticks on
  phones (one lonely hour label before); wider guide channel column (no mid-word name breaks); D-pad
  focus borders on guide cells and programme blocks; solid 2dp focus borders on settings rows; tuning
  card no longer collides with the control dock; visible zap-button pill and progress-bar track;
  category chips always show their channel counts.

## Device-adaptive UI/UX
- Detects **touch vs non-touch hardware** at launch and loads the right UX for each:
  - **Phones / tablets**: every control is tappable — channel cards, chips, settings rows, dialogs; a
    configurable on-screen key cluster in the player, ▲▼ zap buttons, a tap numpad, tap-to-toggle
    overlay and long-press pause.
  - **Android TV / boxes**: pure D-pad experience as before; no touch layers loaded. Mouse users get
    hover-to-focus.
- Login adapts too: real text fields with the IME on touch devices, the D-pad numpad on TV.

## Full EPG time-grid + catch-up + time-shift
- Settings → EPG Mode → **Grid**: channels down the side, a shared 5-hour scrolling timeline, live-now
  highlighting and a red "now" marker per row.
- **Catch-up from the guide**: past replayable programmes show a ▶ badge and launch replays directly.
- **Time-shift**: ◀ / NOW / ▶ header keys move the whole window up to 24 h into the past.

## Channel language filter & fixed mapping
- Settings → Channels → **Channel Languages**: pick one or more languages (with per-language counts);
  every channel list — home grid, EPG view and the player's channel-switching sidebar — loads only those
  languages. Persisted across restarts.
- Fixes the upstream language mapping (wrong from id 5 onward); the correct dictionary is parsed at
  runtime, applied everywhere including the web player and M3U generator (`server/src/jio/channels.ts`).

## More
- **A–Z sort** (favorites still first), **category icons**, **recently-watched** rail, **UpdateChecker**.
- **Fast D-pad browsing** — hold-acceleration plus CH± / PgUp·PgDn page jumps in long lists.
- **Player extras** — catch-up bar (⏪ ⏸ ⏩ + scrubber), programme sheet, stream-stats overlay, sleep
  timer, aspect-ratio cycler, language-feed switcher, audio-track/subtitle picker, zap-preview strip and
  an A–Z letter rail in the sidebar.

## Companion server web player — responsive
- The browser player is fully **responsive for phones & tablets in both portrait and landscape**:
  safe-area insets, an orientation-aware watch page (side-by-side in landscape), touch-friendly
  controls, an always-tappable favourite star, a **3-column channel grid on portrait phones**, and
  device-tuned breakpoints.

## Security & tooling
- Android **backup-exclusion rules** wired so Jio tokens never leave the device in cloud/device backups.
- The release **signing keystore is kept out of the repo tree** (loaded via the `JTV_SIGNING_PROPS`
  environment variable); `.gitignore` covers runtime data and TLS certs. The current key is documented
  in the README's signing section.
- **GitHub Actions CI**: Android unit tests + debug build, server typecheck, web typecheck + build, and a
  secret scan that fails if a signing/secret file is ever tracked.

*Upstream changelog (v1.5.2 and earlier) applies to the base upstream app and is documented in the
upstream repository; this fork's changes are described above.*
