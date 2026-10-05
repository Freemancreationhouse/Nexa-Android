from pathlib import Path
import sys
root=Path(__file__).resolve().parents[1]
theme=(root/'app/src/main/java/com/studiokinematics/nexa/ui/theme/NexaTheme.kt').read_text(encoding='utf-8')
main=(root/'app/src/main/java/com/studiokinematics/nexa/MainActivity.kt').read_text(encoding='utf-8')
checks={
    'theme uses valid composable function type': 'content: @Composable () -> Unit' in theme,
    'theme invokes MaterialTheme with content': 'MaterialTheme(' in theme and 'content = content' in theme,
    'activity keeps composable entrypoint': 'setContent { NexaTheme { NexaApp(this@MainActivity) } }' in main,
    'invalid compact composable type removed': 'content:@Composable()->Unit' not in theme,
}
failed=[k for k,v in checks.items() if not v]
if failed:
    print('FAIL V0.4 COMPILEFIX1 contract')
    for k in failed: print(' -',k)
    sys.exit(1)
print('PASS V0.4 COMPILEFIX1 contract')
for k in checks: print(' -',k)
