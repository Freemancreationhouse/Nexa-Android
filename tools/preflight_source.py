from pathlib import Path
import re, sys
p=Path('app/src/main/java/com/studiokinematics/nexa/MainActivity.kt')
s=p.read_text(encoding='utf-8')
errors=[]
required=['SectionTitle','StatCard','EmptyState','LoadingBlock','ErrorBlock','SourceFooter','BottomBar','PlayerAction','formatTime','saveTracks','loadTracks','sourceLabel','share']
for name in required:
    if not re.search(r'\bfun\s+'+re.escape(name)+r'\s*\(', s):
        errors.append(f'missing helper definition: {name}')
for field in ['source','videoId','thumbnailUrl','durationMs']:
    if f'put("{field}"' not in s:
        errors.append(f'missing persisted Track field: {field}')
if 'Source.YOUTUBE -> videoId.isNotBlank()' not in s:
    errors.append('YouTube saved-item restore still incorrectly depends on a native stream URL')
if s.count('fun NexaApp(') != 1:
    errors.append('NexaApp definition count is not exactly one')
if s.count('private fun loadTracks(') != 1 or s.count('private fun saveTracks(') != 1:
    errors.append('track persistence helper definition count is invalid')
if errors:
    print('FAIL NEXA source preflight')
    for e in errors: print(' -', e)
    sys.exit(1)
print('PASS NEXA source preflight')
print(f' - MainActivity lines: {len(s.splitlines())}')
print(f' - required helpers: {len(required)}/{len(required)}')
print(' - V0.3 source-specific persistence fields present')
