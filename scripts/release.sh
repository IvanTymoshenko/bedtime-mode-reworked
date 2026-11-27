#!/usr/bin/env bash
# Build the signed release APK and publish it as a GitHub Release.
# Notes come from the matching "## <version>" section of CHANGELOG.md.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/share/devtools/jdk}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"

[ -f keystore.properties ] || { echo "keystore.properties missing — refusing to publish a debug-signed APK"; exit 1; }
VERSION=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)
TAG="v$VERSION"
NOTES=$(awk -v v="## $VERSION" '$0==v{f=1;next} /^## /{f=0} f' CHANGELOG.md)
[ -n "$NOTES" ] || { echo "No CHANGELOG.md section for $VERSION"; exit 1; }
if git rev-parse "$TAG" >/dev/null 2>&1; then echo "Tag $TAG already exists — bump versionName first"; exit 1; fi

./gradlew clean assembleRelease --console=plain -q
OUT="build/BedtimeMode-$VERSION.apk"
mkdir -p build && cp app/build/outputs/apk/release/app-release.apk "$OUT"

git tag "$TAG"
git push origin "$TAG"
gh release create "$TAG" "$OUT" --title "Bedtime Mode $VERSION" --notes "$NOTES"
echo "Published $TAG"
