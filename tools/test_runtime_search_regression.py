from pathlib import Path
import sys

root=Path(".")
search=(root/"app/src/main/java/com/studiokinematics/nexa/ui/screens/SearchScreen.kt").read_text(encoding="utf-8")
repos=(root/"app/src/main/java/com/studiokinematics/nexa/data/CatalogRepositories.kt").read_text(encoding="utf-8")

checks={
    "missing YouTube key is surfaced": "YouTube search is not configured" in repos,
    "YouTube Android restricted-key headers": "X-Android-Package" in repos and "X-Android-Cert" in repos,
    "YouTube key uses header": "x-goog-api-key" in repos,
    "no YouTube key in request URL": "&key=${enc(key)}" not in repos,
    "provider search remains in project": "RadioBrowserRepository" in repos and "AudiusRepository" in repos and "YouTubeRepository" in repos,
    "existing UI still isolates provider failures": "supervisorScope" in search and "runCatching{youtube.search" in search and "runCatching{audius.search" in search and "runCatching{radio.search" in search,
     "loading reset remains in finally": "finally{loading=false}" in search,
    "native audio ranked before YouTube fallback": "local+providerResults.second.getOrDefault(emptyList())+providerResults.first.getOrDefault(emptyList())" in search,
}
failed=[name for name,ok in checks.items() if not ok]
if failed:
    print("FAIL runtime search regression")
    for name in failed: print(" -",name)
    sys.exit(1)
print("PASS runtime search regression")
for name in checks: print(" -",name)
