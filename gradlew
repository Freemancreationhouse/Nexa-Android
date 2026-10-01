#!/usr/bin/env sh
set -eu
GRADLE_VERSION=9.6.0
BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
DIST_DIR="$BASE_DIR/.gradle-dist/gradle-$GRADLE_VERSION"
ZIP="$BASE_DIR/.gradle-dist/gradle-$GRADLE_VERSION-bin.zip"
URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
if [ ! -x "$DIST_DIR/bin/gradle" ]; then
  mkdir -p "$BASE_DIR/.gradle-dist"
  if command -v curl >/dev/null 2>&1; then curl -L "$URL" -o "$ZIP"
  elif command -v wget >/dev/null 2>&1; then wget -O "$ZIP" "$URL"
  else echo "curl or wget is required for first-time Gradle download." >&2; exit 1; fi
  unzip -q -o "$ZIP" -d "$BASE_DIR/.gradle-dist"
fi
exec "$DIST_DIR/bin/gradle" "$@"
