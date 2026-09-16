#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK_OUTPUT="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
IMAGE="grimoire-core-builder:latest"
TASK="${1:-assembleDebug}"

# Optional real launcher-icon branding, kept out of this repo on purpose (see
# app/build.gradle.kts) - point this at a res/ directory with your own
# mipmap-anydpi-v26/ic_launcher*.xml + drawable/ic_launcher_foreground.xml +
# ic_launcher_background color to brand the build; omit it to get the
# open-source placeholder icon from branding/oss/res.
BRANDING_MOUNT=()
BRANDING_ENV=()
if [[ -n "${GRIMOIRE_BRANDING_RES_DIR:-}" ]]; then
    BRANDING_MOUNT=(-v "${GRIMOIRE_BRANDING_RES_DIR}:/branding-res:ro")
    BRANDING_ENV=(-e "GRIMOIRE_BRANDING_RES_DIR=/branding-res")
fi

# Optional release signing key, kept out of this repo on purpose (see
# app/build.gradle.kts) - point this at a directory with keystore.properties
# (storeFile, storePassword, keyAlias, keyPassword) + the keystore file itself
# to sign release builds; omit it (or run assembleDebug/bundleDebug) to get an
# unsigned release build instead.
KEYSTORE_MOUNT=()
KEYSTORE_ENV=()
if [[ -n "${GRIMOIRE_KEYSTORE_DIR:-}" ]]; then
    KEYSTORE_MOUNT=(-v "${GRIMOIRE_KEYSTORE_DIR}:/keystore:ro")
    KEYSTORE_ENV=(-e "GRIMOIRE_KEYSTORE_DIR=/keystore")
fi

if [[ -z "${IN_DOCKER:-}" ]]; then
    echo "Building image (first run only, cached after)..."
    docker build -f "$SCRIPT_DIR/Dockerfile.build" -t "$IMAGE" "$SCRIPT_DIR"

    echo "Running gradle $TASK in Docker..."
    docker run --rm \
        -e IN_DOCKER=1 \
        "${BRANDING_ENV[@]}" \
        "${BRANDING_MOUNT[@]}" \
        "${KEYSTORE_ENV[@]}" \
        "${KEYSTORE_MOUNT[@]}" \
        -v "$SCRIPT_DIR":/project \
        -v "$HOME/.gradle":/root/.gradle \
        "$IMAGE" \
        bash /project/build.sh "$TASK"

    if [[ "$TASK" == "assembleDebug" ]]; then
        echo ""
        echo "APK: $APK_OUTPUT"
    elif [[ "$TASK" == "bundleRelease" ]]; then
        echo ""
        echo "AAB: $SCRIPT_DIR/app/build/outputs/bundle/release/app-release.aab"
    fi
    exit 0
fi

# --- Inside Docker ---
cd /project
gradle "$TASK"
