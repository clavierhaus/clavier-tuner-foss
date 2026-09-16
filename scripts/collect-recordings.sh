#!/usr/bin/env bash
#
# Pulls the wobble-study recordings off the phone into the pro repository
# (private: the recordings are clavierhaus data) and pushes them.
#
#   ./scripts/collect-recordings.sh            pull, commit, push
#   ./scripts/collect-recordings.sh --dry      only list what would be pulled
#
set -euo pipefail
# adb reads standard input; inside a pasted box that would swallow the rest of the box.
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PRO="${PRO_REPO:-$REPO/../clavier-tuner-pro}"
ADB="${ANDROID_HOME:-$HOME/android-sdk}/platform-tools/adb"
command -v adb >/dev/null 2>&1 && ADB="$(command -v adb)"

"$ADB" get-state </dev/null >/dev/null 2>&1 || { echo "!! no phone: run w-adb (or plug in the cable)"; exit 1; }
SRC=""
for d in /sdcard/Recordings/ClavierTuner /sdcard/Music/ClavierTuner; do
  if "$ADB" shell "test -d $d" </dev/null 2>/dev/null; then SRC="$d"; break; fi
done
[ -n "$SRC" ] || { echo "!! nothing recorded yet"; exit 1; }

mapfile -t FILES < <("$ADB" shell "ls $SRC" </dev/null | tr -d '\r' | grep '\.wav$' || true)
DEST="$PRO/data/recordings"
mkdir -p "$DEST"
NEW=()
for f in "${FILES[@]}"; do
  [ -n "$(find "$DEST" -name "$f" -print -quit)" ] || NEW+=("$f")
done
echo "==> ${#FILES[@]} on the phone, ${#NEW[@]} new"
[ "${1:-}" = "--dry" ] && { printf '   %s\n' "${NEW[@]}"; exit 0; }
[ ${#NEW[@]} -gt 0 ] || exit 0

DAY="$DEST/$(date +%Y-%m-%d)"
mkdir -p "$DAY"
for f in "${NEW[@]}"; do "$ADB" pull -q "$SRC/$f" "$DAY/$f" </dev/null; done
cd "$PRO"
git pull -q --ff-only
git add data/recordings
git commit -q -m "wobble study: ${#NEW[@]} recordings ($(date +%Y-%m-%d))"
git push -q
echo "==> pushed ${#NEW[@]} recordings to $(basename "$PRO")/data/recordings/$(date +%Y-%m-%d)"
