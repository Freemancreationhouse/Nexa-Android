# NEXA V0.3.1 COMPILEFIX1

This checkpoint preserves the V0.3 Music Search architecture (YouTube + Audius + Radio Browser) and the locked NEXA Gen-Z UI.

## Root-cause correction
The V0.3 source rewrite referenced 13 UI/persistence helpers that were absent from `MainActivity.kt`, which causes `:app:compileDebugKotlin` to fail. COMPILEFIX1 restores those definitions and adds a source regression preflight to prevent the same truncation from reaching Kotlin compilation again.

## Build
GitHub Actions → **NEXA Android Debug Build** → **Run workflow**.

Artifact: `NEXA-V0.3.1-COMPILEFIX1-debug-apk`

For YouTube mainstream search, set repository secret `NEXA_YOUTUBE_API_KEY`, or paste a YouTube Data API key in NEXA Settings after installation. Audius and Radio Browser do not depend on that key.
