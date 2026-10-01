# NEXA — Music Beyond Borders V0.2 LIVE1

V0.2 LIVE1 converts the approved NEXA V0.1 UI from a hardcoded playback demo into a live Android radio/music discovery application.

## Real behavior in this checkpoint
- Home loads live India + global stations from the Radio Browser directory.
- Explore searches the live directory and has language/genre filters.
- Radio is populated from live network data rather than fake NEXA Sessions.
- Media3 plays broadcaster stream URLs directly, including HLS support.
- If a stream exposes ICY/current-track metadata, NEXA displays it in the mini-player and Now Playing screen.
- Favorites and recent stations are saved locally, with no account required.
- Sleep timer cycles 15 → 30 → 60 → off.
- Stream failures show a real playback error rather than silently pretending to play.

## Text-visibility correction
All main titles and station/current-song names are explicitly rendered with a high-contrast near-white color. Secondary information uses a lighter grey so titles remain legible on the dark NEXA design.

## Build
GitHub: Actions → **NEXA Android Debug Build** → **Run workflow**.
Artifact: `NEXA-V0.2-LIVE1-debug-apk`.

The app uses the community Radio Browser directory. Individual broadcaster stream availability, metadata and licensing are controlled by the broadcaster. NEXA does not bypass paid music services.
