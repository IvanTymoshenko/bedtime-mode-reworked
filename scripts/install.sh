#!/usr/bin/env bash
# Build the signed release APK, install it over adb, and pre-grant the special permissions.
# Usage: scripts/install.sh [--debug]
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/share/devtools/jdk}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
PKG=io.github.ivantymoshenko.bedtimemode
SVC=app.bedtimemode.BedtimeListenerService

if [ "${1:-}" = "--debug" ]; then
  PKG="$PKG.debug"
  ./gradlew assembleDebug --console=plain -q; APK=app/build/outputs/apk/debug/app-debug.apk
else
  ./gradlew assembleRelease --console=plain -q; APK=app/build/outputs/apk/release/app-release.apk
fi
# ColorOS may show a "Continue installation" security prompt on the phone — confirm it there.
adb install -r "$APK"

adb shell cmd notification allow_listener "$PKG/$SVC" || true
adb shell cmd notification allow_dnd "$PKG" || true
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
adb shell cmd deviceidle whitelist "+$PKG" || true
adb shell cmd appops set "$PKG" RUN_ANY_IN_BACKGROUND allow || true
echo "Installed $APK. Logs: adb logcat -s BedtimeMode"
