# NEXA V0.3.1 COMPILEFIX1

This checkpoint preserves the V0.3 Music Search architecture (YouTube + Audius + Radio Browser) and the locked NEXA Gen-Z UI.

## Root-cause correction
The V0.3 source rewrite referenced 13 UI/persistence helpers that were absent from `MainActivity.kt`, which causes `:app:compileDebugKotlin` to fail. COMPILEFIX1 restores those definitions and adds a source regression preflight to prevent the same truncation from reaching Kotlin compilation again.

## Build
GitHub Actions → **NEXA Android Debug Build** → **Run workflow**.

Artifact: `NEXA-V0.3.1-COMPILEFIX1-debug-apk`

For YouTube mainstream search, set repository secret `NEXA_YOUTUBE_API_KEY`, or paste a YouTube Data API key in NEXA Settings after installation. Audius and Radio Browser do not depend on that key.

## V0.3.2 SEARCHFIX1 runtime correction

This checkpoint fixes the runtime crash seen while searching. Search providers are now isolated, so a Radio Browser/Audius/YouTube network failure cannot terminate the app. YouTube configuration errors are visible instead of silently returning 0 matches.

For mainstream song/artist/movie search, enable **YouTube Data API v3** in your Google Cloud project and provide its API key either as the GitHub repository secret `NEXA_YOUTUBE_API_KEY` before building or in **NEXA > Settings** after installation. This is a developer API credential; listeners still do not sign in to NEXA or YouTube to search public videos. For a restricted Android key, NEXA now sends its package name and signing-certificate SHA-1 headers automatically.
