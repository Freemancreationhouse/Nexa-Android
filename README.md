# NEXA --- Music Beyond Borders V0.4

**Owner / Publisher:** Studio Kinematic

NEXA V0.4 upgrades the working V0.3.2 SEARCHFIX1 project into a native
Android music player focused on multilingual search, artwork, background
playback, local device music and source-permitted offline listening.

## What V0.4 includes

-   Unified search by song, artist/singer, album/movie, language and
    general music query across the available NEXA providers.
-   Hindi, English, Punjabi, Tamil, Telugu and other languages as
    returned by the connected catalogs.
-   Artwork-first Neo-Glass UI: obsidian surfaces with violet, magenta
    and cyan accents, bright readable titles and NEXA fallback artwork.
-   One Media3 `MediaSessionService` for native direct audio, radio,
    user-owned local audio and completed NEXA offline media.
-   Background and screen-off playback for native sources, with
    notification/lock-screen/Bluetooth media controls.
-   Persistent mini-player, queue, previous/next, shuffle, repeat,
    favorites, progress and sleep timer.
-   **Library → On Device** reads user-owned music through Android
    MediaStore. Local music works without internet and never consumes a
    NEXA offline slot.
-   **Library → NEXA Offline** stores only source-permitted direct audio
    in private app storage.
-   NEXA Free permits **5** simultaneous eligible NEXA-managed offline
    songs. Removing one releases its slot.
-   NEXA+ Google Play Billing 9.1.0 foundation with Google-provided
    localized subscription pricing.
-   Lyrics UI states that never scrape or invent lyrics; a permitted
    provider can be connected later.
-   YouTube remains mainstream discovery/fallback using the official
    visible embedded player. NEXA does not extract YouTube audio or make
    YouTube content a native/offline source.

## Build configuration

GitHub secret:

`NEXA_YOUTUBE_API_KEY` --- YouTube Data API v3 key used for mainstream
discovery.

Optional GitHub repository variable:

`NEXA_PLUS_PRODUCT_ID` --- subscription product ID configured in Google
Play Console. If it is empty, NEXA+ clearly reports that it is not
configured for that build.

Run **Actions → NEXA Android V0.4 Build → Run workflow**.

Successful CI artifact:

`NEXA-V0.4-debug-apk`

The workflow runs source contracts, JVM tests, Android lint,
`assembleDebug`, APK existence verification and signing inspection
before uploading the APK.

## Device acceptance test

1.  Grant **Music and audio** access, open **Library → On Device**, and
    confirm saved device songs appear with title/artist/album and
    artwork or NEXA fallback.
2.  Play an On Device song, lock the phone or open another app, and
    confirm playback continues with media controls.
3.  Start a direct online track or radio station, browse/search NEXA,
    and confirm searching alone does not stop playback.
4.  Select another native track and confirm playback switches once
    without overlapping players.
5.  Search by song title, artist and movie/album name and confirm
    partial provider failures do not close the app.
6.  Select a YouTube fallback result and confirm native audio pauses
    while the official visible player opens.
7.  Save five eligible direct tracks to NEXA Offline; the sixth must
    route to NEXA+ or require removal of an existing download.
8.  Remove one NEXA Offline item and confirm a Free slot becomes
    available.
9.  Disable internet and play a completed NEXA Offline item and an On
    Device item.
10. Confirm On Device songs never reduce the five NEXA Offline slots.
11. Confirm YouTube and radio never expose NEXA offline download.
12. Confirm titles remain bright/readable throughout Home, Search,
    Library, mini-player and Now Playing.

## Release note

A production paid release should add server-side purchase verification
before relying on client entitlement for NEXA+. The V0.4 client
architecture intentionally leaves room for that hardening.

The breadth of searchable commercial music is determined by the catalogs
and permissions of the connected providers; NEXA cannot guarantee every
commercial recording in existence.

## V0.4 COMPILEFIX2

GitHub Android Lint may flag Media3 APIs with `UnsafeOptInUsageError`. The Media3 integration files now explicitly import `androidx.annotation.OptIn` before using `@OptIn(UnstableApi::class)`. This fixes the lint boundary at the usage sites rather than disabling lint or creating a lint baseline.
