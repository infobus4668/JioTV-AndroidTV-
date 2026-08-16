# JTV v1.5.2-mod.1 — first mod release (unofficial)

Unofficial personal mod of [F-e-n-y-x/JioTV-AndroidTV-](https://github.com/F-e-n-y-x/JioTV-AndroidTV-) — all credit for the original app belongs to the original author. Not affiliated with JioTV/Reliance Jio. Signed with the mod builder's own key: uninstall any other build before installing.

## Channel language filter (new)
- Settings → Channels → **Channel Languages**: select one or multiple languages (with per-language channel counts); every channel list — home grid, EPG view and the player's channel-switching sidebar — loads only those languages. Persisted across restarts.
- While a filter is active, Home shows a "Languages · N" chip next to Search that jumps to the setting.

## Fixed: wrong language mapping (upstream bug)
- The official Jio dictionary's `languageIdMapping` (5=Bengali, 8=Tamil, 9=Gujarati, 10=Odia, 11=Telugu, 13=Kannada, …) is now used everywhere; the previous hand-written table disagreed from id 5 onward and shipped wrong labels (Tamil↔Telugu, Bengali↔Tamil, …) to the web player and M3U generator. `server/src/jio/channels.ts` is fixed too.
- The dictionary's language mapping is also parsed at runtime, so new Jio language ids resolve without an app update.
- Old channel caches self-heal: language ids are persisted and re-resolved; caches without ids revalidate once on next launch.

## Sort option (new)
- Settings → Channels → **Sort Channels A–Z**: alphabetical ordering (favorites still first) instead of by channel number, applied to home and player lists.

## Redesigned navigation
- Home: the left sidebar is replaced by horizontal category chips with live channel counts (respecting the language filter); the grid is full-width with ~2 more tile columns.
- Player: the D-pad-← category sidebar now includes **All** and **★ Favorites** (matching Home), with proper labels in the channel-list header.

## Settings
- Channels section moved to the top: language filter, variant grouping, A–Z sort, and a new **Refresh Channel List** action that bypasses the 24h cache.
- "Default Audio Language" renamed to **Preferred Audio Language** with a clearer description.
- Dialogs sized correctly for real TV panels (~540dp usable height): lists scroll and the Done/Cancel buttons always keep their height; the Done button is a light pill with dark letters so it stays readable under any TV picture processing.

## Internal
- 38 unit tests (12+ new) covering the language mapping, filter semantics (incl. collapsed dub families), counts and both sort modes.
- Release build: R8 minified + resource shrinking, baseline profile retained (~4 MB APK).
