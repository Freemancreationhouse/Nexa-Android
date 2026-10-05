from pathlib import Path
import sys

root = Path('.')
kt_files = list((root / 'app/src/main/java').rglob('*.kt'))
failures = []
checked = []
for path in kt_files:
    text = path.read_text(encoding='utf-8')
    if '@OptIn(UnstableApi::class)' in text:
        checked.append(str(path))
        if 'import androidx.annotation.OptIn' not in text:
            failures.append(f'{path}: @OptIn resolves to kotlin.OptIn instead of androidx.annotation.OptIn')
        if 'import kotlin.OptIn' in text:
            failures.append(f'{path}: kotlin.OptIn must not be used for Media3 UnstableApi lint opt-in')

if not checked:
    failures.append('No Media3 @OptIn usage found; contract is not exercising the lint boundary')

if failures:
    print('FAIL V0.4 Media3 lint opt-in contract')
    for failure in failures:
        print(' -', failure)
    sys.exit(1)

print('PASS V0.4 Media3 lint opt-in contract')
for path in checked:
    print(' -', path)
