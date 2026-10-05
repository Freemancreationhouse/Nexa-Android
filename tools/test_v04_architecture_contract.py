from pathlib import Path
import sys
root=Path(".")
src="\n".join(p.read_text(encoding="utf-8") for p in (root/"app/src/main/java").rglob("*.kt"))
manifest=(root/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
checks={
    "service exists": "class NexaPlaybackService : MediaSessionService()" in src,
    "single native player": src.count("ExoPlayer.Builder") == 1,
    "no activity player controller": "class PlayerController" not in src,
    "playback connection exists": "class PlaybackConnection" in src,
    "local repository exists": "class LocalMusicRepository" in src and "MediaStore.Audio.Media" in src,
    "read media audio permission": "android.permission.READ_MEDIA_AUDIO" in manifest,
    "legacy read permission bounded": "android.permission.READ_EXTERNAL_STORAGE" in manifest and 'android:maxSdkVersion="32"' in manifest,
    "media playback service declared": ".playback.NexaPlaybackService" in manifest and 'foregroundServiceType="mediaPlayback"' in manifest,
}
failed=[k for k,v in checks.items() if not v]
if failed:
    print("FAIL V0.4 architecture contract")
    for k in failed: print(" -",k)
    sys.exit(1)
print("PASS V0.4 architecture contract")
for k in checks: print(" -",k)
