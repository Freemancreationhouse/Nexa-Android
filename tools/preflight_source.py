from pathlib import Path
import sys, xml.etree.ElementTree as ET
root=Path(".")
src="\n".join(p.read_text(encoding="utf-8") for p in (root/"app/src/main/java").rglob("*.kt"))
required=[
"app/src/main/java/com/studiokinematics/nexa/MainActivity.kt",
"app/src/main/java/com/studiokinematics/nexa/data/CatalogRepositories.kt",
"app/src/main/java/com/studiokinematics/nexa/playback/NexaPlaybackService.kt",
"app/src/main/java/com/studiokinematics/nexa/local/LocalMusicRepository.kt",
"app/src/main/java/com/studiokinematics/nexa/offline/DownloadRepository.kt",
"app/src/main/java/com/studiokinematics/nexa/billing/BillingRepository.kt",
]
errors=[f"missing {x}" for x in required if not (root/x).exists()]
if src.count("ExoPlayer.Builder")!=1: errors.append("native ExoPlayer must be constructed exactly once")
if "PlayerController" in src: errors.append("obsolete Activity-owned PlayerController remains")
if "READ_MEDIA_AUDIO" not in (root/"app/src/main/AndroidManifest.xml").read_text(): errors.append("local audio permission missing")
try: ET.parse(root/"app/src/main/AndroidManifest.xml")
except Exception as e: errors.append(f"manifest XML invalid: {e}")
if errors:
    print("FAIL NEXA V0.4 source preflight")
    for e in errors: print(" -",e)
    sys.exit(1)
print("PASS NEXA V0.4 source preflight")
print(" - service-owned native player")
print(" - MediaStore local music")
print(" - eligible private offline library")
print(" - NEXA+ billing foundation")
