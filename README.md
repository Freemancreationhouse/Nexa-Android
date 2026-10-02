# NEXA Android V0.3 — Music Search

**NEXA — Music Beyond Borders**

This checkpoint upgrades NEXA from a radio-first prototype into a real multi-source music discovery app.

## What changed

- Search by **song name**
- Search by **artist name**
- Search by **movie name / soundtrack**
- Search by **album name**
- Free-form search across languages and genres
- **YouTube** results using the official YouTube Data API + official embedded YouTube player
- **Audius** results using native NEXA/Media3 audio playback where the catalog provides a stream
- **Radio Browser** remains available for live radio
- Results clearly identify their source
- Local favorites and recently played now work across search results as well as radio
- Bright, high-contrast song/artist titles remain locked from the previous UI correction
- No listener login is required
- No paid subscription is required
- YouTube playback does not use extracted audio URLs, does not remove ads, and does not enable background/audio-only playback

## Mainstream music search

The large commercial-song catalog is provided through YouTube's official APIs and player. YouTube Data API `search.list` requires an API key. The app supports two ways to provide it:

1. **Recommended for development:** open NEXA → Settings → NEXA Search Sources → paste a YouTube Data API key.
2. **GitHub build secret:** create repository secret `NEXA_YOUTUBE_API_KEY`. The workflow passes it to Gradle at build time.

The app does not ask the listener to sign into YouTube.

### Create the API key

In Google Cloud:

1. Create/select a project.
2. Enable **YouTube Data API v3**.
3. Create an API key.
4. Restrict the key to **YouTube Data API v3**.
5. For production, also apply the appropriate application restriction for the signed NEXA Android application.

Do not commit an API key to source control.

## YouTube playback compliance

NEXA uses the official embedded YouTube player. The standard YouTube player, controls, branding, links and ads are retained. NEXA never extracts or separates the audio stream from a YouTube video and does not implement background YouTube playback.

The first-run YouTube consent screen links to YouTube Terms and Google Privacy Policy.

## Build baseline

- compileSdk 36
- targetSdk 36
- Build Tools 36.0.0
- AGP 9.4.0
- Gradle 9.6.0
- JDK 17
- Kotlin 2.2.10
- Compose BOM 2026.03.00
- Media3 1.9.3

## GitHub Actions

Workflow artifact:

`NEXA-V0.3-MUSICSEARCH-debug-apk`

The workflow can build without the GitHub secret because the app also supports entering a key at runtime. If the secret is present, it is injected as `BuildConfig.YOUTUBE_API_KEY`.
