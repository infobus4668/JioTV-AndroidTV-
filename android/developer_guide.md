# JioTV Go TV - Developer Guide & Architecture

This document provides a comprehensive overview of the application's architecture, authentication flow, stream extraction (plugin) logic, and UI structure. It is intended to help developers (and AI assistants) easily maintain and update the application if JioTV's APIs or IPTV mechanisms change.

## 1. Project Structure

The project is structured around the modern Android recommended architecture using Kotlin, Jetpack Compose, and Material 3 for Android TV. The base package is `com.fenyx.jtv`.

- `com.fenyx.jtv.MainActivity`: The main entry point. Sets up the Compose UI surface and the device
  capability flags (touch / form factor / mouse) that drive the adaptive UX.
- `com.fenyx.jtv.Navigation.kt`: First-boot setup chooser (SetupScreen / ServerSetupScreen / LoginScreen
  are switched by the persisted `setupMode`), then `androidx.navigation3` manages the app screens
  (`Main`, `Search`, `Settings`, `Player`) as a serializable back stack.
- `com.fenyx.jtv.ui`: Contains all Compose UI screens.
- `com.fenyx.jtv.data`: Contains data classes, API clients, Settings manager, and Plugin logic.

**Modules:** `:app` (the TV app) and `:baselineprofile` (a `com.android.test` Macrobenchmark module
that generates a Baseline Profile for the launch → browse → play journey). Generate the profile on an
API 33+ device/emulator with `./gradlew :app:generateReleaseBaselineProfile`; the result is embedded in
release builds via ProfileInstaller and benefits API 24+ devices at runtime.

**Tests:** pure helpers are unit-tested under `app/src/test` (12 files, 50+ cases) — channel
filtering/sorting/hidden rules (`ChannelFilterTest`), language-variant collapsing
(`ChannelLanguageTest`), EPG timestamp parsing and window clipping (`EpgRepositoryTest`,
`EpgWindowClipTest`, `NativeEpgParserTest`), the catch-up wire format (`PlaybackBodyTest`),
Akamai-token extraction (`JioApiClientTokenTest`), and more. Run with `./gradlew testDebugUnitTest`.

## 2. Authentication & Login Flow

The app authenticates against the Jio API via SMS/OTP login.

- **Files:** `JioApiClient.kt`, `LoginScreen.kt`
- **Mechanism:**
  1. User enters their Jio Mobile Number.
  2. `JioApiClient.sendOTP()` sends a POST request to `https://jiotvapi.media.jio.com/userservice/apis/v1/loginotp/send` with the base64-encoded `+91`-prefixed number.
  3. User enters the received OTP.
  4. `JioApiClient.verifyOTP()` POSTs the number + OTP + device info to `https://jiotvapi.media.jio.com/userservice/apis/v1/loginotp/verify`.
  5. The API returns `ssoToken`, `authToken`, a `refreshToken`, and session attributes (`subscriberId`/crmid, `unique`, `uid`).
  6. `SettingsManager.kt` stores these credentials in Android DataStore.

> [!NOTE]
> **Session token refresh** (distinct from the per-stream `__hdnea__` refresh in §7): when `geturl`
> returns 401/403/419, `JioApiClient.refreshToken()` POSTs to
> `https://auth.media.jio.com/tokenservice/apis/v1/refreshtoken` with the stored `refreshToken` in the
> body **and** the current `authToken` as the `accesstoken` header (both are required — without the
> header Jio replies "refresh token has expired"). Older logins that predate `refreshToken` capture
> must sign in again to enable it.

> [!WARNING]
> If JioTV updates their login endpoints or headers in the future, check the Kodi plugin's updated Python files, map the new endpoint URLs, and update the HTTP headers in `JioApiClient.kt`.

## 3. Stream Extraction (The Plugin Logic)

To play a channel, the app must convert a channel number into a playable M3U8/MPD stream URL and extract necessary DRM keys.

- **Files:** `JioApiClient.getStreamUrl()`
- **Mechanism:**
  1. The app requests `https://jiotvapi.media.jio.com/playback/v1/geturl?channel_id={channelNumber}&stream_type=Seek` using the user's `ssotoken`, `uniqueId`, `crmid`, and `deviceId`.
  2. The JSON response contains a bitrates array or a direct URL.
  3. The app parses the JSON and forms the final stream URL.
  4. If the stream is DRM protected (`isMpd = true`), the response includes DRM license URLs and headers. These are bundled into a `StreamData` object.
  5. `TvPlayerScreen.kt` passes these DRM parameters to AndroidX Media3 ExoPlayer.

> [!TIP]
> **Updating the Plugin:** If the stream extraction fails, or if you have a newer Kodi plugin zip file, extract it and look at `plugin.video.jiotv/resources/lib/utils.py` or `api.py`. Compare the headers, payload structures, and endpoint URLs. Update `JioApiClient.getStreamUrl()` to match the Python implementation's logic.

## 4. UI Components

### Main Screen (`MainScreen.kt`)
- Uses `MainViewModel` to manage state; list shaping is a single pure pipeline
  (`ChannelFilter.apply`: hidden exclusion → language filter → category/favorites → sort).
- Layout: a horizontal **category chip row with live counts** above a full-width adaptive grid
  (phones clamp tile size so 3 columns always fit), with an optional ★ Favorites rail pinned above.
- **EPG styles** (Settings): off / **rows** (now+next cards) / **grid** (5-hour scrolling time axis
  with catch-up ▶ badges and ±24h time-shift).
- **Hide channels**: long-press / long-OK / MENU on any tile opens the Hide confirm; the manager in
  **Settings → Hide / Unhide Channels** hides/unhides for every surface with All/Hidden/Visible views.
- **Auto-Play:** On startup, `Navigation.kt` intercepts the `allChannels` state and if `autoplayLastChannel` is enabled, automatically redirects to `TvPlayerScreen`.
- **Refresh** (top bar) force-reloads the channel list from the network, bypassing the 24h disk cache.

### Player Screen (`TvPlayerScreen.kt`)
- Uses `ExoPlayer` for playback with a custom overlay that auto-hides after 5 seconds of inactivity.
- **Remote:** ↑/↓ (or CH±) zap channels; **←** opens the channel list (with A–Z jump rail and
  category sidebar); **→** opens the player settings panel (long-press → cycles aspect ratio);
  digits 0–9 tune by list position; **⏩/⏪** seek ±30 s in a replay or open the programme sheet on
  live; double-INFO opens the programme sheet; Back peels overlays in order.
- **Touch/mouse:** tap toggles the overlay, tap outside closes any panel, right-edge swipe = volume,
  a configurable bottom dock (position or split nav/playback groups) plus an edge ▲▼ zap pill carry
  every remote action.
- Continuously saves the `LAST_CHANNEL_ID` to `SettingsManager` for the Autoplay feature.

## 5. Electronic Program Guide (EPG)

- **Files:** `EpgRepository.kt`, `MainViewModel.kt`
- **Mechanism:**
  1. The app downloads an XMLTV gzip file from the URL specified in Settings (default: `https://avkb.short.gy/epg.xml.gz`).
  2. The file is unzipped and parsed chunk-by-chunk to prevent OutOfMemory errors.
  3. Parsed programs are mapped to `Channel.id`.
  4. To support missing channels, `MainViewModel` fetches native EPG data dynamically using `https://jiotv.data.cdn.jio.com/apis/v1.3/getepg/get`.

## 6. Settings Management

All persistent data is managed by `SettingsManager.kt` using Jetpack DataStore Preferences.
- Preferences include: Auth Tokens (SSO/auth/refresh + device IDs), Server config + access code,
  EPG URL/style/mode, Video Quality, Audio Language, Player Resize Mode, Autoplay + last channel/
  category, Favorite Channels, Hidden Channels, Language Filter, A–Z sort, Grid density, On-screen
  dock buttons/layout/position, Zap-preview + edge zap buttons, Voice Boost, Tunneling, and Playback
  Buffer (seconds).
- Stored asynchronously and accessed as Kotlin `Flows`. The DataStore directory is excluded from
  Android cloud/device backups (`backup_rules.xml` / `data_extraction_rules.xml`) so tokens never
  leave the device through backups.

## 7. Playback Resilience & Token Refresh

Jio's live stream URLs carry a short-lived Akamai token (`__hdnea__`, `exp - st ≈ 120s`). Both the
manifest URL **and** each segment are authorized by this token — supplied as a URL query parameter
*and* as a `Cookie: __hdnea__=...` header. When it expires the CDN returns HTTP 403/404, which would
otherwise surface as a fatal player error and a full reload (a visible "buffering/black-screen").

The player keeps playback alive transparently (`TvPlayerScreen.kt`):

1. **Token holder** — an `AtomicReference<String>` holding the freshest token.
2. **`ResolvingDataSource`** — wraps the HTTP data source and, on every request, rewrites the URL's
   `__hdnea__` query param **and** the `Cookie` header with the latest token.
3. **Refresh loop** — a coroutine parses `exp` from the current token and, ~15s before expiry, calls
   `JioApiClient.getStreamUrl()` again, extracts the new token (`JioApiClient.extractHdneaToken`),
   and publishes it to the holder. Playback never sees an expired token.
4. **`JioLoadErrorHandlingPolicy`** — a safety net that makes 403/404 retryable for a few attempts
   with a short backoff (the retry then goes out with the refreshed token).

Buffering is tuned in `JioExoPlayerFactory.kt` (large min/max buffers, back-buffer, live speed
control) and the max buffer is user-configurable via the **Playback Buffer** setting. Tunneling is
**off by default** (it causes black screens on many Amlogic/MediaTek TVs) and exposed as a toggle.

> [!TIP]
> If channels start cutting out again after a JioTV change, first log the raw `geturl` response and
> confirm the token still appears as `__hdnea__` in the stream URL. If Jio changes the token name or
> delivery mechanism, update `extractHdneaToken` and the `ResolvingDataSource` resolver accordingly.
