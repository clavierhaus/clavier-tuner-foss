#!/usr/bin/env bash
# =============================================================================
# Live logs from the app on the connected phone (crashes, our own log lines).
# Ctrl-C to stop.
#
# Usage:   ./scripts/logs.sh
# =============================================================================
set -euo pipefail

SDK_DIR="${ANDROID_HOME:-$HOME/android-sdk}"
ADB="$SDK_DIR/platform-tools/adb"
command -v adb >/dev/null 2>&1 && ADB="$(command -v adb)"

PID=$("$ADB" shell pidof at.clavierhaus.claviertuner | tr -d '\r' || true)
if [ -n "$PID" ]; then
  exec "$ADB" logcat --pid="$PID"
else
  echo "(App not running yet — showing crash-relevant log; start the app on the phone.)"
  exec "$ADB" logcat AndroidRuntime:E UnisonMaster:V "*:S"
fi
