@echo off
setlocal
cd /d "%~dp0"
echo ============================================================
echo NEXA Android V0.1 - Debug APK Build
echo ============================================================
echo Requires Android Studio / Android SDK API 37 and JDK 17+.
call gradlew.bat :app:assembleDebug
if errorlevel 1 (
  echo.
  echo BUILD FAILED - read the Gradle message above.
  exit /b 1
)
echo.
echo BUILD PASS
echo APK: app\build\outputs\apk\debug\app-debug.apk
endlocal
