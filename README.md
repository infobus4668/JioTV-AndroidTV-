<div align="center">

# 📺 JTV — Live TV for Android TV

**A fast, lightweight, no-nonsense Live TV client built for Android TV & TV boxes.**

![Platform](https://img.shields.io/badge/Platform-Android%20TV-3DDC84?logo=android&logoColor=white)
![Android](https://img.shields.io/badge/Android-7.0%20%E2%86%92%2016-blue?logo=android&logoColor=white)
[![Latest Release](https://img.shields.io/github/v/release/infobus4668/JioTV-AndroidTV-?label=Download&color=success)](https://github.com/infobus4668/JioTV-AndroidTV-/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/infobus4668/JioTV-AndroidTV-/total?color=orange)](https://github.com/infobus4668/JioTV-AndroidTV-/releases)

<a href="https://github.com/infobus4668/JioTV-AndroidTV-/releases/latest">
  <img src="https://img.shields.io/badge/⬇️%20Download%20Latest%20APK-1f6feb?style=for-the-badge" alt="Download latest APK" />
</a>

</div>

> **A device-adaptive Live TV client** built on [JioTV-AndroidTV-](https://github.com/F-e-n-y-x/JioTV-AndroidTV-)
> `v1.5.2` — a single, maintained release **`v1.1`** (see [Changelog](#-changelog) below). It adds a
> phone/tablet/TV-adaptive UI, a full EPG time-grid with catch-up and time-shift, a channel language
> filter, an A–Z sort, category icons, a Settings-side hide/unhide channel manager, a customizable
> on-screen player dock, and a fully responsive companion web player. The signing key is the mod's
> own: any build installed with a different key must be uninstalled first.

---

## ✨ Highlights

- 🎬 **Plays DRM channels** (Star, Sony, Zee, Colors…) with the stream token refreshed in the background, so premium channels don't cut out mid-show.
- ⚡ **Instant startup** — the channel list loads from cache immediately, then refreshes quietly in the background.
- 🛡️ **Built for weak hardware** — hardware decoding, tuned buffers, and smart error recovery keep playback smooth on low-end TVs.
- 🗣️ **Voice Boost** — a built-in dialogue enhancer that lifts speech and lowers background music/effects (great for TVs with poor built-in audio).
- 🎚️ **Real controls** — pick the actual audio track/language, video quality (up to 1080p), aspect ratio, playback buffer, and a sleep timer.
- 📅 **EPG** — optional Electronic Program Guide with a timeline view.
- 🖥️ **Modern TV UI** — Jetpack Compose for TV, on-screen numpad login, and smooth D-pad navigation.

<div align="center">
  <img src="screenshot/ui_screenshot_1.png" width="32%" alt="Home" />
  <img src="screenshot/ui_screenshot_2.png" width="32%" alt="Channels" />
  <img src="screenshot/ui_screenshot_3.png" width="32%" alt="Player" />
  <img src="screenshot/ui_screenshot_4.png" width="32%" alt="EPG" />
  <img src="screenshot/ui_screenshot_5.png" width="32%" alt="Settings" />
  <img src="screenshot/ui_screenshot_6.png" width="32%" alt="Player settings" />
</div>

---

## 📥 Install

1. **Download** the latest `JTV-v1.1.apk` from the [**Releases page**](https://github.com/infobus4668/JioTV-AndroidTV-/releases/latest).
2. **Sideload** it onto your Android TV with ADB:

   ```bash
   adb connect <YOUR_TV_IP>:5555
   adb uninstall com.fenyx.jtv
   adb install JTV-v1.1.apk
   ```

   > 💡 Don't have ADB set up? You can also copy the APK to a USB drive and install it with a file-manager app like **"File Commander"** or **"X-plore"** on your TV.

3. **Log in** with your Jio mobile number + OTP using the on-screen numpad.

> ⚠️ **Fresh v1.1** (versionCode 1): uninstall any previously installed JTV build once before
> installing — the lower build number blocks direct updates, even with the same signing key.

---

## 🎮 Remote Controls

| Button | In the channel grid | While watching |
|---|---|---|
| **D-pad ↑ / ↓** | Move | Change channel |
| **CH+ / CH−** | — | Change channel |
| **D-pad ←** | — | Open channel list / categories |
| **D-pad →** | — | Open the player side panel (audio, quality, sleep timer…) |
| **OK / Center** | Open channel | Show/hide channel info |
| **0–9** | — | Jump to a channel number |
| **Back** | Exit app (double-press) | Close overlay / exit player |

---

## 🔊 Voice Boost (Dialogue Enhancer)

Many channels mix dialogue too quietly under loud music and effects — and most TVs have no fix for it. JTV adds one.

Open the **player side panel** (D-pad **→**) → **Voice Boost** and cycle through **Off → Low → Medium → High → Max**.

It uses center-channel processing to **lift the voice and lower the background** while **keeping the bass full** (so it never sounds thin). **Medium** or **High** is the sweet spot for most content. Pair it with **Auto Volume** to even out loudness between channels.

---

## ⚙️ Settings Overview

| Setting | What it does |
|---|---|
| **EPG Mode** | Switch the home screen to a program-guide timeline |
| **EPG Source URL** | Choose your own XMLTV guide source |
| **Hide / Unhide Channels** | One manager for hidden channels — language-scoped list, All/Hidden/Visible filters, show-all |
| **On-Screen Player Keys** | Toggle each player key, set the dock position (center/left/right), or split nav/playback groups |
| **Autoplay Last Channel** | Jump straight into your last channel on launch |
| **Default Quality** | Auto / 1080p / 720p / 480p |
| **Playback Buffer** | Data Saver → Max (more buffer = fewer interruptions) |
| **Player View Mode** | Fit, Fill, Zoom, Stretch |
| **Default Audio Language** | Preferred language for multi-audio channels |
| **Hardware Decoder** | Keep **on** for low-end TVs |
| **Tunneling** | Keep **off** unless you have audio-sync issues (can cause black screens on some TVs) |

---

## 🖥️ Companion Server (optional)

A self-hostable **companion server** lets you **log in once and share it across every TV** — plus watch
in a browser and feed any IPTV player. It's a single Docker image (Node + React).

- **One login for all your TVs** — the server stores your Jio login and refreshes tokens centrally.
  Each TV connects with a short access code (**Settings → Sign-in Method → Connect to JTV Proxy
  Server**); you never log in per device, and re-login only ever happens on the server.
- **Web player** — a full JioTV experience in the browser: channel grid, TV guide, **catch-up**,
  favourites and a language filter. Non-DRM channels play over plain HTTP (via hls.js); DRM channels
  play over the server's HTTPS URL.
- **M3U for external players** — generate a playlist (with EPG + catch-up) for **VLC, TiviMate, OTT
  Navigator, Kodi**, filtered by language / category / quality.

```bash
cd server && docker compose up -d --build   # then open http://<host>:8080
```

> If your Docker Compose version errors on a missing `.env`, create an empty one first
> (`touch .env` / `type nul > .env`) — see [`server/README.md`](server/README.md).

See **[`server/README.md`](server/README.md)** for full setup, the API, and details.

---

## 🛠️ Building from Source

**Requirements:** Android Studio (or the command-line SDK) with JDK 17+.

```bash
git clone https://github.com/infobus4668/JioTV-AndroidTV-.git
cd JioTV-AndroidTV-
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

<details>
<summary><b>Release builds (signed)</b></summary>

The signing keystore is intentionally kept **out of the repo tree**. Point the build at your
out-of-tree `keystore.properties` with the `JTV_SIGNING_PROPS` environment variable, then build:

```bash
export JTV_SIGNING_PROPS="$HOME/.jtv/keys/keystore.properties"   # Windows: $env:JTV_SIGNING_PROPS
./gradlew assembleRelease
```

The properties file (gitignored) holds the absolute keystore path + credentials:

```properties
storeFile=/path/to/jtv-release.keystore
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

Generate a keystore once:

```bash
keytool -genkeypair -v -keystore $HOME/.jtv/keys/jtv-release.keystore -alias jtv -keyalg RSA -keysize 2048 -validity 10000
```

**Current release signing key:** `$HOME/.jtv/keys/jtv-release.keystore`,
alias `jtv` (`CN=JTV Release, O=JTV, C=IN`), certificate SHA-256
`dec91d33555cb8297c7f935bf595f16fc6d667e476ee268793249f9880e4cbf0` — every released JTV APK is
signed with it (verify any build with
`apksigner verify --print-certs <apk>` and compare this digest). The out-of-repo
`keystore.properties` points at this exact file; older notes describing a `jtv-release-v2`
keypair are obsolete — no v2 key exists.

- Credentials (password) live **only** in the out-of-repo `keystore.properties` — never in
  the repo, never in this file. The password is also stored alongside the keystore in
  `$HOME/.jtv/keys/.pw-jtv-release.txt` for when the properties file is rebuilt.
- To rotate the password later (keeps the same signing identity, in-place updates keep
  working) — run with the current password in hand:

```bash
keytool -storepasswd -keystore $HOME/.jtv/keys/jtv-release.keystore -storepass OLD -new NEW
keytool -keypasswd  -keystore $HOME/.jtv/keys/jtv-release.keystore -alias jtv -keypass OLD -new NEW
# then update storePassword/keyPassword in the out-of-repo keystore.properties
```

A release build with no `JTV_SIGNING_PROPS` (and no gitignored `keystore.properties`) is left unsigned.

</details>

---

## 🧱 Tech Stack

| Area | Technology |
|---|---|
| **UI** | Jetpack Compose for TV (Material 3) |
| **Media** | AndroidX Media3 / ExoPlayer (HLS + DASH/Widevine) |
| **Architecture** | MVVM · Kotlin Coroutines · StateFlow |
| **Navigation** | AndroidX Navigation 3 |
| **Storage** | DataStore Preferences + on-disk cache |
| **Images** | Coil |

---

## 📝 Changelog

### v1.1 — the single, maintained release
Everything in this repository is folded into this one release. Highlights on top of the upstream
`v1.5.2` base:

- **Device-adaptive UX** — phones/tablets get full touch UX, Android TV/Fire TV pure D-pad, and PC
  emulators / air-mouse boxes hover-to-focus + click everywhere.
- **Hide / Unhide Channels from Settings** — one manager for the hidden-channels list: language-scoped
  rows with one-press hide/unhide, All/Hidden/Visible filter chips, and Show-all. Hidden channels
  disappear from Home, search, EPG and zap lists until restored.
- **Customizable on-screen player dock** — toggle each key, choose its bottom position
  (center/left/right), or split it into two groups: navigation bottom-left, playback bottom-right.
  Edge ▲▼ zap pill included.
- **Tap anywhere outside a player panel closes it** — works in every orientation and on emulators
  (a scrim sits under the panels, over the video and the volume strip).
- **EPG done right** — rows and a 5-hour time-grid with catch-up replay (▶ past programmes) and ±24 h
  time-shift; D-pad focus lands correctly on entry; last-played position restores everywhere.
- **Remote-key completeness** — OK activates focused rows in the player panel/programme sheet; ⏩/⏪
  seek ±30 s in a replay or open the programme sheet on live; CH± respects open panels; double-press
  Back exits from Home.
- **Refresh that refreshes** — the top-bar button force-reloads the channel list from the network
  (bypassing the 24h cache) with a visible spinner; pull-to-refresh was removed.
- **Login polish** — OTP resend with countdown, focused-button feedback, keyboard-safe server setup.
- **Text-overflow fixes in both orientations** across banner, cards, sheets, dialogs and the replay
  bar; text-overflow and focus-border cleanups throughout.

See **[release-notes.md](release-notes.md)** for the full notes.

---

## 🙏 Credits

Built with reference to and inspiration from:

- [dineshintry/plugin.kodi.jiotv](https://github.com/dineshintry/plugin.kodi.jiotv)
- [JioTV-Go/jiotv_go](https://github.com/JioTV-Go/jiotv_go)

---

## ⚖️ Disclaimer

This is an independent, third-party Android TV client made for **educational purposes**. It is **not** affiliated with, authorized, maintained, or endorsed by JioTV or Reliance Jio Infocomm Ltd. You are responsible for how you use it, and you need a valid Jio account to log in. Use at your own risk.
