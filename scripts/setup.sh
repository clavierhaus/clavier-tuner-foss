#!/usr/bin/env bash
# =============================================================================
# One-time setup for building UnisonMaster on this machine (Linux / macOS).
# Installs the Android SDK command-line tools into ~/android-sdk, the needed
# SDK packages, and writes local.properties. Safe to re-run.
#
# Usage:   ./scripts/setup.sh
# =============================================================================
set -euo pipefail

SDK_DIR="${ANDROID_HOME:-$HOME/android-sdk}"
REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "==> Checking Java (need JDK 17+ with javac)..."
if ! command -v javac >/dev/null 2>&1; then
  echo "!! No JDK found (javac missing)."
  echo "   Linux (Debian/Ubuntu):  sudo apt-get update && sudo apt-get install -y openjdk-21-jdk-headless"
  echo "   macOS (Homebrew):       brew install openjdk@21 && sudo ln -sfn \\"
  echo "       /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk"
  echo "   Then re-run this script."
  exit 1
fi
JAVA_MAJOR=$(javac -version 2>&1 | sed -E 's/javac ([0-9]+).*/\1/')
if [ "$JAVA_MAJOR" -lt 17 ]; then
  echo "!! JDK $JAVA_MAJOR found, need 17+. Install a newer JDK and re-run."
  exit 1
fi
echo "    JDK $JAVA_MAJOR — ok."

echo "==> Android SDK -> $SDK_DIR"
if [ ! -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
  case "$(uname -s)" in
    Darwin) CLT_URL="https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip" ;;
    *)      CLT_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" ;;
  esac
  mkdir -p "$SDK_DIR/cmdline-tools"
  TMP_ZIP="$(mktemp -u).zip"
  echo "    downloading command-line tools..."
  curl -sfL -o "$TMP_ZIP" "$CLT_URL"
  unzip -q "$TMP_ZIP" -d "$SDK_DIR/cmdline-tools"
  rm -f "$TMP_ZIP"
  mv "$SDK_DIR/cmdline-tools/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
fi

SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
echo "==> Accepting licenses + installing packages (platform 35, build-tools, adb)..."
yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
"$SDKMANAGER" "platforms;android-37.1" "build-tools;35.0.0" "platform-tools" >/dev/null

echo "==> Writing local.properties"
echo "sdk.dir=$SDK_DIR" > "$REPO_DIR/local.properties"

ADB="$SDK_DIR/platform-tools/adb"
echo ""
echo "Setup complete."
echo "  SDK:  $SDK_DIR"
echo "  adb:  $ADB"
echo ""
echo "Optional, so 'adb' works everywhere — add to your shell profile:"
echo "  export ANDROID_HOME=\"$SDK_DIR\""
echo "  export PATH=\"\$PATH:\$ANDROID_HOME/platform-tools\""
echo ""
echo "Next: ./scripts/deploy.sh   (builds and installs on the connected phone)"
