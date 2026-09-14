#!/usr/bin/env bash
#
# Pulls capture files off the phone and pushes them to the repository.
#
#   ./scripts/collect.sh                 pull, commit, push
#   ./scripts/collect.sh --keep          same, but leave the files on the phone
#
# Files land in data/sweeps/<date>/ . Anything already committed anywhere
# under data/sweeps is skipped, so running this twice does not duplicate.
#
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PHONE_DIR="/sdcard/Download/UnisonMaster"
DEST="$REPO/data/sweeps/$(date +%Y-%m-%d)"
KEEP=0
[ "${1:-}" = "--keep" ] && KEEP=1

command -v adb >/dev/null || { echo "adb not found."; exit 1; }

if [ -z "$(adb devices | sed -n '2p')" ]; then
    echo "No phone connected. Plug it in, unlock it, and allow USB debugging."
    exit 1
fi

if ! adb shell "test -d $PHONE_DIR" 2>/dev/null; then
    echo "Nothing recorded yet: $PHONE_DIR does not exist on the phone."
    exit 1
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
adb pull -a "$PHONE_DIR/." "$TMP/" >/dev/null 2>&1 || true

shopt -s nullglob
PULLED=("$TMP"/*.csv)
if [ ${#PULLED[@]} -eq 0 ]; then
    echo "No capture files on the phone."
    exit 1
fi

mkdir -p "$DEST"
NEW=0
for f in "${PULLED[@]}"; do
    name="$(basename "$f")"
    # Already collected in an earlier run? Leave it alone.
    if find "$REPO/data/sweeps" -name "$name" -print -quit 2>/dev/null | grep -q .; then
        continue
    fi
    cp "$f" "$DEST/$name"
    NEW=$((NEW + 1))
done

if [ "$NEW" -eq 0 ]; then
    echo "Nothing new — all ${#PULLED[@]} files on the phone are already committed."
    exit 0
fi

echo "$NEW new capture(s) → data/sweeps/$(date +%Y-%m-%d)/"
# Show what arrived, so a wrong label or a REJECT is noticed now and not
# tomorrow: the filename carries session, sequence, label and verdict.
ls -1 "$DEST" | sed 's/^/    /'

cd "$REPO"
git add data/sweeps
git commit -q -m "Captures $(date +%Y-%m-%d): $NEW file(s)"
git push -q
echo "Pushed."

if [ "$KEEP" -eq 0 ]; then
    # Only after a successful push, so nothing is deleted that is not safe
    # in the repository.
    adb shell "rm -f $PHONE_DIR/*.csv" >/dev/null 2>&1 || true
    echo "Phone folder cleared. Next session starts empty."
fi
