from pathlib import Path
import sys, xml.etree.ElementTree as ET

root=Path(".")
gradle=(root/"app/build.gradle.kts").read_text(encoding="utf-8")
strings=(root/"app/src/main/res/values/strings.xml").read_text(encoding="utf-8")
manifest=(root/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
workflow=(root/".github/workflows/android-debug-build.yml").read_text(encoding="utf-8")
src="\n".join(p.read_text(encoding="utf-8") for p in (root/"app/src/main/java").rglob("*.kt"))

checks={
    "version 0.4.0": 'versionName = "0.4.0"' in gradle,
    "version code advanced": "versionCode = 7" in gradle,
    "owner metadata": "Studio Kinematic" in strings,
    "product tagline": "Music Beyond Borders" in strings,
    "android 36": "compileSdk = 36" in gradle and "targetSdk = 36" in gradle,
    "single exoplayer": src.count("ExoPlayer.Builder")==1,
    "media session service": "NexaPlaybackService : MediaSessionService" in src,
    "local media permission": "READ_MEDIA_AUDIO" in manifest,
    "five slot policy": "FREE_LIMIT = 5" in src,
    "billing 9.1": "billing-ktx:9.1.0" in gradle,
    "ci unit tests": ":app:testDebugUnitTest" in workflow,
    "ci lint": ":app:lintDebug" in workflow,
    "ci assemble": ":app:assembleDebug" in workflow,
    "ci v04 artifact": "NEXA-V0.4-debug-apk" in workflow,
}
try: ET.parse(root/"app/src/main/AndroidManifest.xml")
except Exception: checks["manifest xml valid"]=False
else: checks["manifest xml valid"]=True

failed=[k for k,v in checks.items() if not v]
if failed:
    print("FAIL NEXA V0.4 release preflight")
    for k in failed: print(" -",k)
    sys.exit(1)
print("PASS NEXA V0.4 release preflight")
for k in checks: print(" -",k)
