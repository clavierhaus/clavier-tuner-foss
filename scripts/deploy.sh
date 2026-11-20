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
  # what a failed test said, whole, from gradle's own test reports (20 Nov 2026: the short form shows only that a test
  # failed, never the layout check's list of faults)
  find . -path '*/build/test-results/*' -name 'TEST-*.xml' -newer "$GRADLE_LOG" -print0 2>/dev/null \
    | xargs -0 -r python3 -c '
import sys, xml.etree.ElementTree as E
for f in sys.argv[1:]:
    for c in E.parse(f).iter("testcase"):
        for x in c.findall("failure"):
            print("\n   " + c.get("classname", "").rsplit(".", 1)[-1] + "." + c.get("name", "") + " said:")
            for l in (x.get("message") or "").splitlines()[:60]: print("     " + l)
' 2>/dev/null || true
  local errs
  errs=$(grep -cE '^e:' "$GRADLE_LOG" || true)
  echo ""
  echo "   $errs compiler error(s); the whole output is in:"
  echo "   $GRADLE_LOG"
  echo "   (paste it whole — the first error is usually the only real one)"
  return 1
}

# A second Kotlin/Native toolchain in ~/.konan beside KONAN_DATA_DIR is removed (20 Nov 2026: with both there, the iPhone
# compile check found every iOS library twice and failed). ~/.konan is only a download, the same one KONAN_DATA_DIR holds;
# what was compiled against it is compiled again.
if [ -n "${KONAN_DATA_DIR:-}" ] && [ -d "$HOME/.konan" ] && [ "$HOME/.konan" != "$KONAN_DATA_DIR" ]; then
  echo "==> Removing the second iPhone toolchain in $HOME/.konan (KONAN_DATA_DIR is $KONAN_DATA_DIR)"
  ./gradlew --stop -q >/dev/null 2>&1 || true
  rm -rf "$HOME/.konan" */build/classes/kotlin/iosArm64
fi

if [ "$DO_PULL" = 1 ]; then
  echo "==> git pull"
  SELF_BEFORE="$(md5sum < "$REPO_DIR/scripts/deploy.sh")"
  git pull --ff-only
  # a script that changed with the pull is read again: bash goes on with the old one it has open (20 Nov 2026: the
  # run of 16:00 pulled a new deploy.sh and ran the old one)
  if [ "$(md5sum < "$REPO_DIR/scripts/deploy.sh")" != "$SELF_BEFORE" ]; then
    echo "==> deploy.sh changed with the pull: running the new one"
    exec "$REPO_DIR/scripts/deploy.sh" --no-pull "$@"
  fi
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

# The iPhone compile check (Peter, 20 Nov 2026): the engine and the screens compiled for the iPhone on this machine, so an
# iPhone error shows here before a TestFlight run instead of on GitHub's minutes. Linking and signing need a Mac and stay
# on GitHub. The first run downloads the Kotlin/Native toolchain (about 770 MB, into KONAN_DATA_DIR when that is set).
# A failure is said; it does not stop the Android install.
if [ "$DO_TEST" = 1 ]; then
  echo "==> iPhone compile check (engine and screens)"
  if gradle_quietly "the iPhone compile check" :shared:compileKotlinIosArm64 :ui:compileKotlinIosArm64; then
    echo "    compiles for the iPhone."
  else
    echo "    (the Android install goes on)"
  fi
fi

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
