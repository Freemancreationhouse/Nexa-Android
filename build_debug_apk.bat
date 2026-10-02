@echo off
setlocal
cd /d "%~dp0"
echo ============================================================
echo NEXA Android V0.2 LIVE2 - Debug APK Build
echo ============================================================
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle is not on PATH. Use the included GitHub Actions workflow,
  echo or open this project in Android Studio and Build APK.
  exit /b 1
)
gradle --no-daemon :app:assembleDebug --stacktrace
if errorlevel 1 exit /b 1
echo.
echo BUILD PASS
echo APK: app\build\outputs\apk\debug\app-debug.apk
endlocal
