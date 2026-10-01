@echo off
setlocal
set GRADLE_VERSION=9.6.0
set BASE_DIR=%~dp0
set DIST_ROOT=%BASE_DIR%.gradle-dist
set DIST_DIR=%DIST_ROOT%\gradle-%GRADLE_VERSION%
set ZIP=%DIST_ROOT%\gradle-%GRADLE_VERSION%-bin.zip
set URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip
if not exist "%DIST_DIR%\bin\gradle.bat" (
  if not exist "%DIST_ROOT%" mkdir "%DIST_ROOT%"
  echo Downloading Gradle %GRADLE_VERSION%...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri '%URL%' -OutFile '%ZIP%'"
  if errorlevel 1 exit /b 1
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%ZIP%' -DestinationPath '%DIST_ROOT%' -Force"
  if errorlevel 1 exit /b 1
)
call "%DIST_DIR%\bin\gradle.bat" %*
endlocal
