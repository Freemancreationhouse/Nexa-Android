# NEXA V0.4 Professional Player --- Design Specification

**Product:** NEXA --- Music Beyond Borders\
**Owner / Publisher:** Studio Kinematic\
**Target version:** 0.4.0\
**Source baseline:** NEXA V0.3.2 SEARCHFIX1 from the uploaded project\
**Package:** `com.studiokinematics.nexa`\
**Android baseline:** minSdk 26, compileSdk 36, targetSdk 36, Java 17\
**Primary goal:** Upgrade the working V0.3.2 search application into a
professional music player without regressing the existing YouTube,
Audius, or Radio Browser search behavior.

## 1. Existing V0.3.2 Behavior to Preserve

The uploaded V0.3.2 project already provides:

-   Search across YouTube, Audius, and Radio Browser.
-   Search modes for All, Songs, Artists, Albums, and Movies.
-   YouTube Data API key support through the build configuration or
    local Settings.
-   Android-restricted YouTube API request headers.
-   Official embedded YouTube playback.
-   Native ExoPlayer playback for Audius/direct audio and radio.
-   Local favorites and recent-history persistence.
-   Home, Search, Radio, and Library navigation.
-   Current NEXA dark visual language and local no-login usage.
-   Android 36/JDK 17 GitHub Actions build path.

V0.4 must preserve those working provider boundaries and search failure
isolation. One unavailable provider must not make the other providers
unusable.

## 2. Architecture

V0.3.2 currently concentrates models, repositories, persistence, player
ownership, and most Compose UI in a single `MainActivity.kt`. V0.4 will
separate those responsibilities while preserving behavior.

The target architecture is:

1.  **UI shell** --- Compose navigation and screens only.
2.  **Catalog repositories** --- YouTube, Audius, and Radio Browser
    search/discovery.
3.  **Native playback service** --- one Media3 `MediaSessionService`
    owns the ExoPlayer used by Audius/direct audio and radio.
4.  **Playback connection/state** --- UI observes and controls the
    service without creating or destroying the player.
5.  **Local library store** --- favorites and recent-history persistence
    with migration from V0.3.2 preference keys.
6.  **Offline subsystem** --- private app-storage downloads for
    explicitly eligible direct-audio tracks.
7.  **Entitlement subsystem** --- Free versus NEXA+ offline-slot policy.
8.  **Billing subsystem** --- Google Play Billing for NEXA+.
9.  **Lyrics subsystem** --- provider-neutral UI states; lyrics are
    displayed only when a permitted source is configured and returns
    content.

`MainActivity` remains the application entry point but must no longer
own the native audio engine.

## 3. Source Capability Boundaries

Every track source has explicit capabilities.

### Audius / permitted direct audio

-   Native NEXA playback: yes.
-   Background playback: yes.
-   Lock-screen / notification / Bluetooth controls: yes.
-   Queue participation: yes.
-   Offline storage: only when the source explicitly marks the item
    downloadable and exposes a valid permitted download URI.
-   Lyrics: only through a separate permitted lyrics provider.

### Radio Browser

-   Native NEXA playback: yes.
-   Background playback: yes.
-   Lock-screen / notification / Bluetooth controls: yes.
-   Queue participation: yes.
-   Offline storage: no.
-   Live metadata may replace the static station title/artist when
    supplied by the stream.

### YouTube

-   Discovery/search: yes, through YouTube Data API.
-   Playback: official visible embedded YouTube player only.
-   Audio extraction: no.
-   Conversion to MP3/direct audio: no.
-   NEXA background YouTube playback: no.
-   NEXA offline download: no.
-   When the user selects a YouTube result while native NEXA audio is
    playing, native audio pauses before YouTube playback starts.
-   The YouTube player may be minimized within the NEXA interface while
    remaining visible and controllable.
-   When NEXA itself is backgrounded, the embedded YouTube player is
    paused.

## 4. Native Playback

A single Media3 `MediaSessionService` owns the native ExoPlayer.

Required behavior:

-   Audius/direct audio and radio continue playing when the user changes
    NEXA tabs.
-   Searching for another song does not stop current native playback.
-   Backgrounding NEXA or locking the phone does not stop eligible
    native playback.
-   Notification and lock-screen controls operate the same session.
-   Bluetooth/headset media buttons operate the same session.
-   Supported controls: play, pause, previous, next, seek where duration
    exists, shuffle, repeat-one, and queue.
-   Radio streams that have no seekable duration display a live state
    rather than a fake progress duration.
-   Playback errors are surfaced in the UI without closing the app.
-   Only an explicit playback selection replaces the current native
    track.

## 5. Persistent Player UI

The bottom of Home, Search, Radio, and Library shows a persistent
mini-player whenever native audio is active.

The mini-player includes:

-   artwork or NEXA fallback artwork,
-   high-contrast title,
-   artist/station,
-   play/pause,
-   progress for seekable tracks,
-   tap-to-open full Now Playing.

The full native Now Playing screen includes:

-   large artwork,
-   song/station title,
-   artist,
-   album when available,
-   source label,
-   progress and elapsed/remaining time for seekable tracks,
-   live indicator for radio,
-   previous,
-   play/pause,
-   next,
-   shuffle,
-   repeat-one,
-   favorite,
-   sleep timer,
-   system EQ entry when available,
-   lyrics entry,
-   offline-save entry only when the track is eligible.

## 6. UI / UX Direction

V0.4 uses the locked NEXA Neo-Glass / Spatial Music direction.

Visual requirements:

-   obsidian/near-black base,
-   violet, magenta, and cyan gradient accents,
-   glass-like rounded panels,
-   high-contrast white/near-white song titles,
-   secondary metadata remains readable and never competes with the
    title,
-   artwork-first cards and player surfaces,
-   polished loading, empty, partial-provider-failure, and network-error
    states,
-   consistent Home / Search / Radio / Library bottom navigation,
-   NEXA branding remains prominent but does not obscure music content.

Home includes a strong search/discovery hero, recent listening,
language/genre discovery, and live radio.

Search accepts natural music queries including song, artist,
album/movie, and language-oriented queries. Existing provider isolation
remains mandatory.

Library contains liked music, recent listening, and offline music.

## 7. Artwork

Use provider artwork when a valid image URL is available. When artwork
is absent or fails, show a NEXA gradient fallback rather than an empty
or broken image.

Artwork loading failure must never prevent track playback or crash a
list.

## 8. Offline Library

Offline storage is private to NEXA and is not exported as ordinary
user-visible music files.

Eligibility rules:

-   YouTube: never eligible.
-   Radio: never eligible.
-   Audius/direct audio: eligible only when the source explicitly
    permits downloading and provides a valid download URI.

NEXA Free policy:

-   Maximum five simultaneous eligible offline songs.
-   Completed, queued, downloading, or intentionally stopped offline
    items occupy a slot.
-   Failed downloads do not permanently consume a slot.
-   Removing an offline item releases its slot.
-   Attempting a sixth eligible offline item opens the NEXA+ upgrade
    path.

Offline management includes:

-   download status,
-   progress,
-   removal,
-   Wi-Fi-only option,
-   playback from completed local cache without network access.

Playback and download use the same cache identity so a completed offline
track can be played without creating a duplicate copy.

## 8A. On-Device / Local Music

NEXA V0.4 must also operate as a full offline player for audio files the
user already has saved on the Android device.

Requirements:

-   Add **Library → On Device / Local Music**.
-   Discover device audio through Android `MediaStore.Audio.Media`
    rather than unrestricted filesystem crawling.
-   Request only the modern Android media permission required for the
    running Android version; on Android 13+ use `READ_MEDIA_AUDIO`, with
    the legacy storage-read permission limited to older supported
    Android versions where required.
-   Read and display available title, artist, album, duration, content
    URI, and album/embedded artwork.
-   If local artwork is unavailable, use the same professional NEXA
    fallback artwork system used by online tracks.
-   Local device songs play through the same NEXA Media3 native playback
    service used for direct online audio.
-   Local music supports background and screen-off playback, queue,
    persistent mini-player, full Now Playing,
    notification/lock-screen/Bluetooth controls, shuffle, repeat,
    favorites, and recent history.
-   Local songs remain playable with no internet connection.
-   Local songs must **not** count against the NEXA Free five-song
    offline-download limit.
-   The five-song limit applies only to eligible online tracks that NEXA
    itself stores in its private offline cache.
-   NEXA-downloaded offline media remains private to NEXA and must be
    visually separated from user-owned **On Device** music.
-   If media permission is denied, NEXA must keep the rest of the app
    usable and show a clear permission/request state in the On Device
    section instead of crashing or showing a false empty library.

## 9. NEXA+

NEXA+ uses Google Play Billing.

Requirements:

-   No hard-coded fake price.
-   Price shown to the user comes from Google Play product details.
-   Pending purchase is not treated as active entitlement.
-   Purchased entitlement removes the NEXA five-song count limit for
    eligible tracks.
-   Source licensing restrictions still apply to NEXA+; NEXA+ does not
    make YouTube or radio downloadable.
-   If no Play product ID is configured, the app clearly reports that
    NEXA+ is unavailable for that build rather than simulating a
    purchase.
-   Production hardening should support server-side purchase
    verification before public paid release; the V0.4 client
    architecture must not prevent adding it.

## 10. Lyrics

The player exposes a lyrics surface with explicit states:

-   loading,
-   synchronized lyrics,
-   plain lyrics,
-   instrumental,
-   unavailable,
-   provider error.

NEXA must not scrape, fabricate, or reconstruct copyrighted lyrics. If
no permitted lyrics provider is configured or no lyrics are returned,
the UI says lyrics are unavailable.

## 11. Local Data and Migration

No NEXA account is required for V0.4 core listening.

Local data includes:

-   favorites,
-   recent listening,
-   YouTube consent state,
-   optional local YouTube API key,
-   offline records,
-   Wi-Fi-only preference.

V0.4 must read the V0.3.2 `favorite_tracks` and `recent_tracks`
preference keys so upgrading the app does not silently erase the user's
existing local library/history.

## 12. Settings and Product Identity

Settings show:

-   `NEXA — Music Beyond Borders`,
-   `Owner / Publisher: Studio Kinematic`,
-   YouTube search configuration status,
-   YouTube Data API key entry when needed,
-   relevant YouTube terms/privacy links,
-   NEXA+ availability/status.

The Android package remains `com.studiokinematics.nexa`.

## 13. Build and CI

The existing Android 36/JDK 17 build path remains.

V0.4 CI must:

1.  verify Android 36/build-tools/JDK setup,
2.  run source/architecture regression checks,
3.  run JVM unit tests,
4.  run Android lint,
5.  assemble the debug APK,
6.  verify the APK exists and is signable,
7.  upload the APK only after the build succeeds,
8.  upload diagnostics on failure.

Required build inputs:

-   `NEXA_YOUTUBE_API_KEY` GitHub secret for mainstream YouTube search.
-   `NEXA_PLUS_PRODUCT_ID` build/repository variable for a configured
    Play subscription product; empty is allowed for non-billing test
    builds.

## 14. Regression and Acceptance Criteria

V0.4 is accepted only when all of the following are true:

1.  V0.3.2 YouTube + Audius + Radio Browser search still works with
    provider failure isolation.
2.  Searching or changing tabs does not stop currently playing native
    audio.
3.  Audius/direct audio or radio continues after NEXA is backgrounded
    and after the phone is locked.
4.  Notification, lock-screen, and Bluetooth/headset controls manipulate
    the same native playback session.
5.  Selecting a different native track replaces playback once, without
    duplicate players.
6.  Selecting YouTube pauses native playback and uses the visible
    official embedded player.
7.  YouTube never exposes NEXA audio extraction, background playback, or
    offline download.
8.  Radio never exposes offline download.
9.  Eligible direct-audio tracks can be stored privately and played with
    network disabled after download completion.
10. Free entitlement accepts five eligible offline items and rejects
    item six with the NEXA+ flow.
11. Removing one of the five items restores one Free slot.
12. NEXA+ uses Google-provided product pricing and a confirmed purchased
    state for entitlement.
13. Favorites and recent history from V0.3.2 remain available after
    migration.
14. Song titles are clearly readable on all dark UI surfaces.
15. Artwork failure does not break playback or crash lists.
16. Lyrics unavailable/error states do not break playback.
17. Owner / Publisher is displayed as Studio Kinematic.
18. Version reports 0.4.0.
19. CI must not publish an APK artifact if compilation, tests, or lint
    fail.

## 15. Explicit Non-Goals for V0.4

V0.4 does not:

-   bypass YouTube playback restrictions,
-   extract YouTube audio,
-   download YouTube videos/audio,
-   make radio stations downloadable,
-   invent lyrics,
-   provide a fake paid unlock,
-   require a NEXA listener login,
-   replace the working V0.3.2 provider search with an unrelated catalog
    backend,
-   add device-visible exported MP3 files.

## 16. Delivery

The final delivery is a complete V0.4 Android project ZIP derived from
the uploaded V0.3.2 source, including:

-   source code,
-   Gradle configuration,
-   manifest/resources,
-   tests,
-   GitHub Actions workflow,
-   V0.4 checkpoint/readme,
-   no embedded private API key or billing credential.

A compiled APK may be included only if a real Android build has been
executed successfully in the available environment. Otherwise the
project must include a CI workflow that builds and verifies the APK.
