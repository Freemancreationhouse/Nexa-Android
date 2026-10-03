from pathlib import Path
import sys
s = Path('app/src/main/java/com/studiokinematics/nexa/MainActivity.kt').read_text(encoding='utf-8')
checks = {
    'search isolates provider failures': 'supervisorScope' in s and 'runCatching { youtube.search' in s and 'runCatching { audius.search' in s and 'runCatching { radio.search' in s,
    'loading reset is in finally': 'finally { loading = false }' in s,
    'missing YouTube key is surfaced': 'YouTube search is not configured' in s,
    'YouTube Android restricted-key headers': 'X-Android-Package' in s and 'X-Android-Cert' in s,
    'YouTube key uses header': 'x-goog-api-key' in s,
    'no YouTube key in request URL': '&key=${enc(key)}' not in s,
}
failed=[name for name,ok in checks.items() if not ok]
if failed:
    print('FAIL runtime search regression')
    for x in failed: print(' -', x)
    sys.exit(1)
print('PASS runtime search regression')
for x in checks: print(' -', x)
