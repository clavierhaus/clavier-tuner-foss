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
# adb reads standard input; inside a pasted box that would swallow the rest of the box.
exec </dev/null

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


# -----------------------------------------------------------------------------
# Gradle, silent when it works and complete when it does not.
#
# The script used to run gradle with -q, which says almost nothing on a failure,
# and set -e then stopped the run where it stood. Nothing could be analysed from
# what reached the screen. Success stays silent; a failure now prints the lines
# that name the fault and keeps the whole output in a file that can be read or
# pasted whole.
# -----------------------------------------------------------------------------
GRADLE_LOG=""
gradle_quietly() {
  local what="$1"; shift
  GRADLE_LOG="$(mktemp -t clavier-build-XXXXXX.log)"
  if ./gradlew "$@" --console=plain >"$GRADLE_LOG" 2>&1; then
    rm -f "$GRADLE_LOG"; GRADLE_LOG=""
    return 0
  fi
  echo ""
  echo "!! $what failed."
  echo ""
  # what the compiler and gradle actually said, in the order they said it
  grep -nE '^(e:|FAILURE|\* What went wrong|\* Try|Caused by|Execution failed|> |[[:space:]]+at )' "$GRADLE_LOG" \
    | head -60 | sed 's/^/   /'
  local errs
  errs=$(grep -cE '^e:' "$GRADLE_LOG" || true)
  echo ""
  echo "   $errs compiler error(s); the whole output is in:"
  echo "   $GRADLE_LOG"
  echo "   (paste it whole — the first error is usually the only real one)"
  return 1
}

if [ "$DO_PULL" = 1 ]; then
  echo "==> git pull"
  git pull --ff-only
fi

if [ "$DO_TEST" = 1 ]; then
  echo "==> DSP unit tests"
  gradle_quietly "the engine's own tests" :shared:testAndroidHostTest -q || exit 1
  echo "    tests green."
fi

echo "==> Building debug APK"
gradle_quietly "building the app" :androidApp:assembleDebug -q || exit 1
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
echo "Done. Launch 'Clavier Tuner' on the phone."
echo "Grant the microphone permission on first start."
