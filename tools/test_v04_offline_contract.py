from pathlib import Path
import sys
root=Path(".")
off=root/"app/src/main/java/com/studiokinematics/nexa/offline"
src="\n".join(p.read_text(encoding="utf-8") for p in off.rglob("*.kt")) if off.exists() else ""
play=(root/"app/src/main/java/com/studiokinematics/nexa/playback/NexaPlaybackService.kt").read_text(encoding="utf-8")
manifest=(root/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
checks={
    "five song limit": "FREE_LIMIT = 5" in src,
    "local excluded": "Source.LOCAL" in src and "false" in src,
    "youtube radio excluded": "Source.YOUTUBE" in src and "Source.RADIO" in src,
    "download service": "class NexaDownloadService : DownloadService" in src,
    "download manager": "DownloadManager" in src and "SimpleCache" in src,
    "wifi only": "NETWORK_UNMETERED" in src,
    "shared cache playback": "OfflineEngine.cache" in play and "CacheDataSource.Factory" in play,
    "data sync service": ".offline.NexaDownloadService" in manifest and 'foregroundServiceType="dataSync"' in manifest,
}
failed=[k for k,v in checks.items() if not v]
if failed:
    print("FAIL V0.4 offline contract")
    for k in failed: print(" -",k)
    sys.exit(1)
print("PASS V0.4 offline contract")
for k in checks: print(" -",k)
