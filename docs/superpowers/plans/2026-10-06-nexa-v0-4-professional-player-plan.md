# NEXA V0.4 Professional Player Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps
> use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the uploaded NEXA V0.3.2 SEARCHFIX1 project into NEXA
V0.4.0 with native background playback, broad multi-source music search,
artwork-first Neo-Glass UI, user-owned on-device music, eligible offline
downloads, and NEXA+ entitlement.

**Architecture:** Preserve the working V0.3.2 provider/search behavior,
split the oversized activity into focused models/repositories/screens,
and move native playback into one Media3 `MediaSessionService`. Online
direct audio, radio, completed NEXA offline items, and MediaStore local
songs all feed the same native player; YouTube remains an official
embedded fallback and never becomes an extracted native audio source.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android Media3
ExoPlayer/Session/Download, Android MediaStore, SharedPreferences/JSON
migration, Google Play Billing, JUnit 4, Python source-contract
regressions, GitHub Actions, Android SDK 36/JDK 17.

**Spec:**
`docs/superpowers/specs/2026-10-06-nexa-v0-4-professional-player-design.md`

## Global Constraints

-   Package remains `com.studiokinematics.nexa`.
-   Owner / Publisher remains `Studio Kinematic`.
-   Target version is `0.4.0`.
-   minSdk 26, compileSdk 36, targetSdk 36, Java 17.
-   Preserve V0.3.2 YouTube + Audius + Radio Browser search and provider
    failure isolation.
-   YouTube remains official visible embedded playback only; no audio
    extraction, MP3 conversion, NEXA background YouTube playback, or
    NEXA offline download.
-   Native/direct audio, radio, completed NEXA offline media, and
    user-owned MediaStore audio use one Media3 playback service.
-   NEXA Free permits five simultaneous eligible NEXA-managed offline
    songs.
-   User-owned On Device songs never count toward the five-song NEXA
    offline limit.
-   NEXA-managed offline media stays private to the app.
-   Lyrics are shown only from a permitted provider.
-   Song titles use high-contrast light text on dark surfaces and
    artwork failures always fall back to NEXA artwork.

## Review Focus

-   Android 13+ media permission denied/revoked: On Device must show a
    recoverable permission state while online features continue working.
-   MediaStore rows with missing artist/album/artwork or invalid
    duration: local library must still list/play valid content without
    crashing.
-   Search provider timeout/failure while native audio is playing:
    results from other providers remain usable and playback continues.
-   A sixth eligible NEXA-managed download after five occupied slots:
    must open NEXA+ rather than silently queue or overwrite.
-   Offline/native playback after process/UI recreation: one
    service-owned player/session must remain authoritative; no duplicate
    Activity-owned ExoPlayer.

------------------------------------------------------------------------

### Task 1: Preserve Search, Split Core Models, and Migrate Local Library

**Files:** - Create:
`app/src/main/java/com/studiokinematics/nexa/model/MediaModels.kt` -
Create:
`app/src/main/java/com/studiokinematics/nexa/model/SourceCapabilities.kt` -
Create:
`app/src/main/java/com/studiokinematics/nexa/data/CatalogRepositories.kt` -
Create:
`app/src/main/java/com/studiokinematics/nexa/data/LibraryStore.kt` -
Modify: `app/src/main/java/com/studiokinematics/nexa/MainActivity.kt` -
Test: `tools/test_runtime_search_regression.py` - Test:
`app/src/test/java/com/studiokinematics/nexa/model/SourceCapabilitiesTest.kt`

**Interfaces:** - Produces: `Track`, `Source`, `SearchMode`,
`SourceCapabilities.forTrack(track)`, `RadioBrowserRepository`,
`AudiusRepository`, `YouTubeRepository`, and `LibraryStore`. - `Track`
includes source identity, stream URI, content URI/local flag, artwork,
album, download URI, and download eligibility.

-   [ ] **Step 1: Write failing source/search and source-capability
    tests.**
-   [ ] **Step 2: Run tests and verify failure because split
    models/repositories do not yet exist.**
-   [ ] **Step 3: Extract models/repositories without changing working
    V0.3.2 request semantics; add V0.3.2 `favorite_tracks` /
    `recent_tracks` migration.**
-   [ ] **Step 4: Run tests and verify provider isolation,
    Android-restricted YouTube headers, and source capability boundaries
    pass.**
-   [ ] **Step 5: Commit `refactor: preserve v03 search in v04 core`.**

### Task 2: Service-Owned Native Playback and On-Device Music

**Files:** - Create:
`app/src/main/java/com/studiokinematics/nexa/playback/NexaPlaybackService.kt` -
Create:
`app/src/main/java/com/studiokinematics/nexa/playback/PlaybackConnection.kt` -
Create:
`app/src/main/java/com/studiokinematics/nexa/playback/MediaItemMapper.kt` -
Create:
`app/src/main/java/com/studiokinematics/nexa/local/LocalMusicRepository.kt` -
Create:
`app/src/main/java/com/studiokinematics/nexa/local/LocalMediaPermission.kt` -
Modify: `app/src/main/AndroidManifest.xml` - Modify:
`app/build.gradle.kts` - Test:
`app/src/test/java/com/studiokinematics/nexa/local/LocalMediaPermissionTest.kt` -
Test: `tools/test_v04_architecture_contract.py`

**Interfaces:** - Consumes: Task 1 `Track` and `Source`. - Produces:
`PlaybackConnection.play(track, queue)`, `PlaybackUiState`,
`LocalMusicRepository.load()`, and permission helpers for
`READ_MEDIA_AUDIO` / legacy read permission.

-   [ ] **Step 1: Write failing permission-policy and
    architecture-contract tests requiring one service-owned ExoPlayer
    and no Activity-owned native player.**
-   [ ] **Step 2: Run tests and verify failure.**
-   [ ] **Step 3: Implement `MediaSessionService`, controller
    connection, queue mapping, background controls, and MediaStore
    local-song discovery with nullable metadata/artwork handling.**
-   [ ] **Step 4: Add manifest foreground-media and media-read
    permissions with version limits; run tests and verify pass.**
-   [ ] **Step 5: Commit
    `feat: add background player and on-device music`.**

### Task 3: Neo-Glass UI, Artwork, Persistent Mini-Player, and Local Library

**Files:** - Create:
`app/src/main/java/com/studiokinematics/nexa/ui/theme/NexaTheme.kt` -
Create:
`app/src/main/java/com/studiokinematics/nexa/ui/components/NexaComponents.kt` -
Create:
`app/src/main/java/com/studiokinematics/nexa/ui/screens/HomeScreen.kt` -
Create:
`app/src/main/java/com/studiokinematics/nexa/ui/screens/SearchScreen.kt` -
Create:
`app/src/main/java/com/studiokinematics/nexa/ui/screens/RadioScreen.kt` -
Create:
`app/src/main/java/com/studiokinematics/nexa/ui/screens/LibraryScreen.kt` -
Create:
`app/src/main/java/com/studiokinematics/nexa/ui/screens/NowPlayingScreen.kt` -
Create:
`app/src/main/java/com/studiokinematics/nexa/ui/screens/YouTubePlayerScreen.kt` -
Modify: `app/src/main/java/com/studiokinematics/nexa/MainActivity.kt` -
Test: `tools/test_v04_ui_contract.py`

**Interfaces:** - Consumes: Tasks 1--2 repositories, tracks, local
permission state, and playback state. - Produces:
Home/Search/Radio/Library/On Device surfaces, persistent native
mini-player, full native Now Playing, and embedded YouTube fallback.

-   [ ] **Step 1: Write failing UI contract tests for high-contrast
    titles, artwork/fallback, On Device section, persistent mini-player,
    and full player controls.**
-   [ ] **Step 2: Run contract and verify failure.**
-   [ ] **Step 3: Implement obsidian Neo-Glass screens and artwork-first
    components; use provider/local artwork where available and NEXA
    fallback otherwise.**
-   [ ] **Step 4: Connect On Device permission/request states and local
    songs to the same native player; keep YouTube embedded/fallback
    separate.**
-   [ ] **Step 5: Run UI/search/architecture contracts and commit
    `feat: ship nexa v04 player experience`.**

### Task 4: Eligible Offline Downloads and Five-Song Free Policy

**Files:** - Create:
`app/src/main/java/com/studiokinematics/nexa/offline/OfflineEntitlement.kt` -
Create:
`app/src/main/java/com/studiokinematics/nexa/offline/OfflineSlotPolicy.kt` -
Create:
`app/src/main/java/com/studiokinematics/nexa/offline/OfflineEngine.kt` -
Create:
`app/src/main/java/com/studiokinematics/nexa/offline/NexaDownloadService.kt` -
Create:
`app/src/main/java/com/studiokinematics/nexa/offline/DownloadRepository.kt` -
Modify:
`app/src/main/java/com/studiokinematics/nexa/playback/NexaPlaybackService.kt` -
Modify:
`app/src/main/java/com/studiokinematics/nexa/ui/screens/LibraryScreen.kt` -
Test:
`app/src/test/java/com/studiokinematics/nexa/offline/OfflineSlotPolicyTest.kt`

**Interfaces:** - Consumes: `Track`, `SourceCapabilities`, and native
playback cache. - Produces: `DownloadRepository.enqueue/remove`,
download records/status, Wi-Fi-only requirements, and
`OfflineSlotPolicy.FREE_LIMIT = 5`.

-   [ ] **Step 1: Write failing policy tests proving five Free slots,
    sixth rejection, local songs excluded from slot counting, and
    YouTube/radio ineligible.**
-   [ ] **Step 2: Run tests and verify failure.**
-   [ ] **Step 3: Implement private Media3 cache/download service and
    shared cache playback; count only NEXA-managed eligible downloads.**
-   [ ] **Step 4: Add Library → NEXA Offline management and Wi-Fi-only
    control; run tests and verify pass.**
-   [ ] **Step 5: Commit `feat: add five-slot nexa offline library`.**

### Task 5: NEXA+ Billing and Lyrics State

**Files:** - Create:
`app/src/main/java/com/studiokinematics/nexa/billing/NexaPlusState.kt` -
Create:
`app/src/main/java/com/studiokinematics/nexa/billing/BillingRepository.kt` -
Create:
`app/src/main/java/com/studiokinematics/nexa/data/LyricsRepository.kt` -
Create:
`app/src/main/java/com/studiokinematics/nexa/model/LyricsState.kt` -
Create:
`app/src/main/java/com/studiokinematics/nexa/ui/screens/NexaPlusScreen.kt` -
Modify: `app/build.gradle.kts` - Modify:
`app/src/main/java/com/studiokinematics/nexa/MainActivity.kt` - Test:
`app/src/test/java/com/studiokinematics/nexa/billing/NexaPlusStateTest.kt`

**Interfaces:** - Consumes: offline entitlement from Task 4. - Produces:
Play-provided product price/state, purchased NEXA+ entitlement, and safe
lyrics loading/unavailable states.

-   [ ] **Step 1: Write failing tests proving provider-formatted price
    is preserved and pending purchase is not active.**
-   [ ] **Step 2: Run tests and verify failure.**
-   [ ] **Step 3: Implement Play Billing repository and NEXA+ screen; no
    product ID means explicit unavailable state.**
-   [ ] **Step 4: Implement lyrics state/repository without
    scraping/fabrication and connect it to Now Playing.**
-   [ ] **Step 5: Run tests and commit
    `feat: add nexa plus and lyrics states`.**

### Task 6: Release Identity, CI Gates, and Complete Regression Suite

**Files:** - Modify: `app/build.gradle.kts` - Modify:
`app/src/main/res/values/strings.xml` - Modify:
`.github/workflows/android-debug-build.yml` - Modify: `README.md` -
Create: `NEXA_V0_4_CHECKPOINT.txt` - Create:
`tools/preflight_source.py` - Test: all Task 1--5 tests/contracts

**Interfaces:** - Consumes: all prior tasks. - Produces: reproducible
V0.4 project and CI-built debug APK artifact.

-   [ ] **Step 1: Write failing release preflight requiring version
    0.4.0, Studio Kinematic identity, Android 36/JDK 17, local-music
    permissions, service-owned player, five-slot policy, and no
    Activity-owned ExoPlayer.**
-   [ ] **Step 2: Run preflight and verify failure before release
    metadata/workflow changes.**
-   [ ] **Step 3: Update version/identity, README/checkpoint, and GitHub
    Actions to run source contracts, JVM tests, lint, assembleDebug, APK
    verification, and failure diagnostics.**
-   [ ] **Step 4: Run all available local tests/contracts and, when
    Android SDK/Gradle is available, run
    `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.**
-   [ ] **Step 5: Verify ZIP integrity and commit
    `release: prepare nexa v0.4 professional player`.**
