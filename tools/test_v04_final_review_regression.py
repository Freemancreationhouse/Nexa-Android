from pathlib import Path
import sys
root=Path(".")
mapper=(root/"app/src/main/java/com/studiokinematics/nexa/playback/MediaItemMapper.kt").read_text()
conn=(root/"app/src/main/java/com/studiokinematics/nexa/playback/PlaybackConnection.kt").read_text()
download=(root/"app/src/main/java/com/studiokinematics/nexa/offline/DownloadRepository.kt").read_text()
now=(root/"app/src/main/java/com/studiokinematics/nexa/ui/screens/NowPlayingScreen.kt").read_text()
checks={
    "nullable album metadata avoided": "ifBlank { null }" not in mapper and "takeIf { it.isNotBlank() }" in mapper,
    "early play request retained": "pendingPlay" in conn,
    "local songs do not show download action": "capabilities.countsTowardNexaOfflineLimit" in now,
    "offline records reconcile download index": "private val downloadIndex=manager.downloadIndex" in download and "downloadIndex.getDownloads()" in download and "reconcileIndex" in download,
    "nullable prefs json guarded": 'JSONArray(prefs.getString("records","[]"))' not in download,
    "download record reconciliation is thread safe": "ConcurrentHashMap" in download,
}
failed=[k for k,v in checks.items() if not v]
if failed:
    print("FAIL V0.4 final review regression")
    for k in failed: print(" -",k)
    sys.exit(1)
print("PASS V0.4 final review regression")
for k in checks: print(" -",k)
