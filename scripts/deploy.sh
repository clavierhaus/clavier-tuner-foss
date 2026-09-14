#!/usr/bin/env bash
# =============================================================================
# Everyday workflow: pull latest, run DSP tests, build debug APK, install on
# the connected phone. This is the one script to run after each dev session.
#
# Usage:   ./scripts/deploy.sh            # pull + test + build + install
#          ./scripts/deploy.sh --no-pull  # skip git pull
#          ./scripts/deploy.sh --no-test  # skip unit tests (faster)
# =============================================================================
set -euo pipefail

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_DIR"

SDK_DIR="${ANDROID_HOME:-$HOME/android-sdk}"
ADB="$SDK_DIR/platform-tools/adb"
command -v adb >/dev/null 2>&1 && ADB="$(command -v adb)"

DO_PULL=1
DO_TEST=1
for arg in "$@"; do
  case "$arg" in
    --no-pull) DO_PULL=0 ;;
    --no-test) DO_TEST=0 ;;
  esac
done

if [ "$DO_PULL" = 1 ]; then
  echo "==> git pull"
  git pull --ff-only
fi

if [ "$DO_TEST" = 1 ]; then
  echo "==> DSP unit tests"
  ./gradlew :shared:testAndroidHostTest --console=plain -q
  echo "    tests green."
fi

echo "==> Building debug APK"
./gradlew :androidApp:assembleDebug --console=plain -q
APK="androidApp/build/outputs/apk/debug/androidApp-debug.apk"
echo "    $APK"

echo "==> Looking for device"
if [ ! -x "$ADB" ]; then
  echo "!! adb not found at $ADB — run ./scripts/setup.sh first."
  exit 1
fi
"$ADB" start-server >/dev/null 2>&1
DEVICES=$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')
if [ -z "$DEVICES" ]; then
  echo "!! No device in 'adb devices'."
  echo "   On the phone: Settings > About phone > tap 'Build number' 7x,"
  echo "   then Settings > System > Developer options > enable 'USB debugging',"
  echo "   plug in USB, confirm the RSA fingerprint dialog on the phone."
  UNAUTH=$("$ADB" devices | awk 'NR>1 && $2=="unauthorized" {print $1}')
  [ -n "$UNAUTH" ] && echo "   (A device is connected but UNAUTHORIZED — confirm the dialog on the phone.)"
  exit 1
fi

for DEV in $DEVICES; do
  MODEL=$("$ADB" -s "$DEV" shell getprop ro.product.model | tr -d '\r')
  echo "==> Installing on $DEV ($MODEL)"
  "$ADB" -s "$DEV" install -r "$APK"
done

echo ""
echo "Done. Launch 'UnisonMaster' on the phone."
echo "Grant the microphone permission on first start."
