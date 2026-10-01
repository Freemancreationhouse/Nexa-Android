# NEXA Android V0.1

**NEXA — Music Beyond Borders**

Locked first-build baseline for the Studio Kinematics Gen-Z multilingual music app.

## V0.1 included
- Futuristic dark neon / glassmorphism-inspired Compose UI
- Home, Explore, Radio and Library tabs
- Hindi, English, Punjabi, Tamil, Telugu, Malayalam, Bengali, Marathi and more discovery chips
- Search filtering across the bundled demo catalog
- Local favorites using SharedPreferences
- Recently played state
- Full-screen Now Playing screen
- Real AndroidX Media3 / ExoPlayer playback
- Play/pause, previous, next, seek bar, shuffle and repeat controls
- Mini player above bottom navigation
- No login flow and no payment flow
- Android INTERNET permission only

## Demo audio
The first source build uses royalty-free public demo MP3 streams for playback validation. It does **not** scrape or bypass Spotify, YouTube Music, JioSaavn, Apple Music, or other protected catalogs.

## Build requirements
- Android Studio compatible with AGP 9.4.x
- JDK 17+
- Android SDK API 37
- Gradle 9.6

Open the project folder in Android Studio and run the `app` configuration.

## Next checkpoint
V0.2 should replace the static demo catalog with legal live discovery providers (Radio Browser first, then approved free-track providers), add MediaSession background playback, lock-screen controls, resilient stream fallback and richer local library persistence.

## Build APK on GitHub
A workflow is included at `.github/workflows/android-debug-build.yml`.
Push this project to GitHub, open **Actions → NEXA Android Debug Build → Run workflow**, then download the `NEXA-V0.1-debug-apk` artifact.
