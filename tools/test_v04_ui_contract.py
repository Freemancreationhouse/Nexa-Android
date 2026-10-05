from pathlib import Path
import sys
root=Path(".")
ui=root/"app/src/main/java/com/studiokinematics/nexa/ui"
main=(root/"app/src/main/java/com/studiokinematics/nexa/MainActivity.kt").read_text(encoding="utf-8")
gradle=(root/"app/build.gradle.kts").read_text(encoding="utf-8")
texts="\n".join(x.read_text(encoding="utf-8") for x in ui.rglob("*.kt")) if ui.exists() else ""
checks={
    "neo glass theme": "NexaBg" in texts and "NexaPurple" in texts and "NexaPink" in texts and "NexaCyan" in texts,
    "bright song titles": "NexaBright" in texts and "fontWeight=FontWeight" in texts,
    "artwork component": "fun NexaArtwork" in texts and "effectiveArtworkUrl" in texts and "MediaMetadataRetriever" in texts,
    "persistent mini player": "fun PersistentMiniPlayer" in texts,
    "now playing screen": "fun NowPlayingScreen" in texts and "Shuffle" in texts and "RepeatOne" in texts,
    "on device library": "On Device" in texts and "PermissionRequired" in texts,
    "search by music query": "Song, artist, movie, album" in texts,
    "youtube visible player": "YouTubePlayerOverlay" in texts and "youtube.com/embed" in texts,
    "main uses playback service connection": "PlaybackConnection" in main and "PlayerController" not in main,
    "main requests local media permission": "rememberLauncherForActivityResult" in main and "LocalMediaPermission" in main,
    "lifecycle compose dependency": "lifecycle-runtime-compose" in gradle and "androidx.lifecycle.compose.LocalLifecycleOwner" in texts,
    "no invalid dp constructor": "androidx.compose.ui.unit.dp(" not in main,
    "nexa offline library": "NEXA Offline" in texts and "OfflineRecord" in texts,
    "offline action capability gated": "SourceCapabilities.forTrack" in texts and "capabilities.countsTowardNexaOfflineLimit" in texts,
}
failed=[k for k,v in checks.items() if not v]
if failed:
    print("FAIL V0.4 UI contract")
    for k in failed: print(" -",k)
    sys.exit(1)
print("PASS V0.4 UI contract")
for k in checks: print(" -",k)
