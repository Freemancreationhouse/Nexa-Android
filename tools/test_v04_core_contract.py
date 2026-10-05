from pathlib import Path
import sys

root=Path(".")
models=root/"app/src/main/java/com/studiokinematics/nexa/model/MediaModels.kt"
caps=root/"app/src/main/java/com/studiokinematics/nexa/model/SourceCapabilities.kt"
repos=root/"app/src/main/java/com/studiokinematics/nexa/data/CatalogRepositories.kt"
store=root/"app/src/main/java/com/studiokinematics/nexa/data/LibraryStore.kt"

checks={
    "split media models": models.exists(),
    "source capability boundary": caps.exists(),
    "split catalog repositories": repos.exists(),
    "library store": store.exists(),
}
if models.exists():
    t=models.read_text()
    checks["local source exists"]="LOCAL" in t
    checks["track supports content uri"]="contentUri" in t
    checks["track supports artwork"]="artworkUrl" in t
if caps.exists():
    t=caps.read_text()
    checks["youtube not native"]="Source.YOUTUBE" in t and "nativePlayback = false" in t
    checks["local native offline"]="Source.LOCAL" in t and "background = true" in t
if repos.exists():
    t=repos.read_text()
    checks["provider isolation ready"]="class RadioBrowserRepository" in t and "class AudiusRepository" in t and "class YouTubeRepository" in t
    checks["youtube restricted headers"]="X-Android-Package" in t and "X-Android-Cert" in t
    checks["youtube key header"]="x-goog-api-key" in t and "&key=${enc(key)}" not in t
    checks["audius downloadable schema"]="is_downloadable" in t and '/download' in t
    checks["audius streamable filter"]="is_streamable" in t
if store.exists():
    t=store.read_text()
    checks["v03 favorites migration"]='"favorite_tracks"' in t
    checks["v03 recent migration"]='"recent_tracks"' in t

failed=[k for k,v in checks.items() if not v]
if failed:
    print("FAIL V0.4 core contract")
    for k in failed: print(" -",k)
    sys.exit(1)
print("PASS V0.4 core contract")
for k in checks: print(" -",k)
