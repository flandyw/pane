#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-25-openjdk-arm64}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
[[ -r .signing/password && -f .signing/pane.p12 ]] || { echo 'Missing .signing/pane.p12 and .signing/password. See README.md.' >&2; exit 1; }
export PANE_SIGNING_PASSWORD
PANE_SIGNING_PASSWORD=$(cat .signing/password)
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease --console=plain
./scripts/verify-apk.py
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose app/build/outputs/apk/release/app-release.apk
if [[ ${1:-} == --publish ]]; then
    ./release-server/publish.sh app/build/outputs/apk/release/app-release.apk
fi
