#!/usr/bin/env bash
#
# release-ios.sh -- the iPhone app to TestFlight, with what its testers read (Peter, 10 Nov 2026: "Could you do this
# as a release-ios.sh in foss as soon as new things happen?").
#
#   ./scripts/release-ios.sh          the checks, then one question
#   ./scripts/release-ios.sh --yes    without the question: Peter's yes on Android is already given
#   ./scripts/release-ios.sh --notes-for 175
#                                     only the notes and the testers, for build 175 already on TestFlight; nothing
#                                     is built (10 Nov 2026: 175 was uploaded and its notes refused)
#
# The order does not change: the iPhone is built after Android, never before. Android is built on the X1, Peter tests
# it and says yes, and only then does this run, on the X1 or in a session on his word. What it does:
#   1. checks: a clean tree on main, in sync with origin; the iPhone's version the same as Android's; the notes
#      fastlane/metadata/ios/testflight/<locale>.txt written for this version (first line the version, en-US at
#      least, at most 4000 characters); gh signed in;
#   2. starts the build workflow by hand with TestFlight chosen: the iPhone's checks, the app signed and uploaded;
#   3. in that run scripts/testflight.py waits for Apple's processing, writes What to Test from the notes and gives
#      the build to the tester groups (Beta App Review when an external group is among them);
#   4. follows the run here until it ends and says what came of it, the build number with it.
# A session keeps the notes current with every change a tester would notice, as it keeps CHANGELOG.md.
#
set -euo pipefail
B=$'\033[1m'; X=$'\033[0m'
ok()  { printf '  %sok%s %s\n' "$B" "$X" "$*"; }
die() { printf '  %sx%s  %s\n' "$B" "$X" "$*" >&2; exit 1; }

YES=0; NOTES_FOR=""
while [ $# -gt 0 ]; do
  case "$1" in
    --yes) YES=1 ;;
    --notes-for) shift; NOTES_FOR="${1:-}"; [[ "$NOTES_FOR" =~ ^[0-9]+$ ]] || die "--notes-for takes a build number" ;;
    *) die "unknown argument $1 (--yes, --notes-for <build>)" ;;
  esac
  shift
done

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")/.." && pwd)"
cd "$ROOT"
VERSION="$(sed -n 's/.*versionName = "\(.*\)"/\1/p' androidApp/build.gradle.kts)"
IOS="$(sed -n 's/^ *MARKETING_VERSION: "\(.*\)"/\1/p' iosApp/project.yml)"
NOTES=fastlane/metadata/ios/testflight
REPO="$(git remote get-url origin | sed -E 's#\.git$##; s#.*[/:]([^/]+/[^/]+)$#\1#')"

# 1. the checks
[ -n "$VERSION" ] || die "no versionName in androidApp/build.gradle.kts"
[ "$IOS" = "$VERSION" ] || die "the iPhone says $IOS (iosApp/project.yml), Android $VERSION"
[ -f "$NOTES/en-US.txt" ] || die "$NOTES/en-US.txt missing: what the testers read"
for f in "$NOTES"/*.txt; do
  first="$(head -n1 "$f" | tr -d '\r' | sed 's/[[:space:]]*$//')"
  [ "$first" = "$VERSION" ] || die "$f is for '$first', this is $VERSION: write its notes first"
  n="$(python3 -c 'import sys; print(len(open(sys.argv[1], encoding="utf-8").read().strip()))' "$f")"
  [ "$n" -le 4000 ] || die "$f: $n characters, TestFlight takes 4000"
done
[ -z "$(git status --porcelain)" ] || die "working tree is dirty"
[ "$(git rev-parse --abbrev-ref HEAD)" = "main" ] || die "not on main"
git fetch -q origin
[ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] || die "main not in sync with origin"
command -v gh >/dev/null || die "gh missing"
gh api "repos/$REPO" --jq .full_name >/dev/null 2>&1 || die "gh cannot reach $REPO"
HEAD="$(git rev-parse HEAD)"
ok "$VERSION, $(git rev-parse --short HEAD), notes: $(cd "$NOTES" && ls *.txt | sed 's/\.txt$//' | paste -sd' ')"

if [ -n "$NOTES_FOR" ]; then
  YES=1    # the build is already on TestFlight: nothing new goes to Apple but its notes
fi
if [ "$YES" -ne 1 ]; then
  printf '  Send %s to TestFlight? Only after the yes on Android. [y/N] ' "$VERSION"
  read -r answer
  [ "$answer" = "y" ] || [ "$answer" = "Y" ] || die "not sent"
fi

# 2. the run
SINCE="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
if [ -n "$NOTES_FOR" ]; then
  gh workflow run build.yml -R "$REPO" --ref main -f notes_for_build="$NOTES_FOR" >/dev/null
else
  gh workflow run build.yml -R "$REPO" --ref main -f testflight=true >/dev/null
fi
RUN=""
for _ in $(seq 1 30); do
  RUN="$(gh run list -R "$REPO" --workflow build.yml --event workflow_dispatch --limit 5 \
    --json databaseId,createdAt,headSha --jq ".[] | select(.createdAt >= \"$SINCE\" and .headSha == \"$HEAD\") | .databaseId" | head -n1)"
  [ -n "$RUN" ] && break
  sleep 4
done
[ -n "$RUN" ] || die "the run did not appear on GitHub within two minutes"
NUMBER="$(gh run view "$RUN" -R "$REPO" --json number --jq .number)"
if [ -n "$NOTES_FOR" ]; then
  BUILD="$NOTES_FOR"; JOB=testflight-notes
  ok "run $RUN started: the notes and the testers for build $BUILD, a few minutes"
else
  BUILD=$((100 + NUMBER)); JOB=testflight
  ok "run $RUN started: build $BUILD, about 40 minutes with Apple's processing"
fi

# 4. followed to its end: a line when a job changes, and a line for each step of the TestFlight job, so the screen
# moves every few minutes (Peter, 10 Nov 2026: "CLI is static", twenty minutes without a line)
seen=""; step=""
while :; do
  state="$(gh run view "$RUN" -R "$REPO" --json status,conclusion,jobs \
    --jq '[.status, (.conclusion // ""), (.jobs | map(.name + ":" + (if .status == "completed" then .conclusion else .status end)) | join(" ")),
           ((.jobs[] | select(.name == "'"$JOB"'") | .steps | map(select(.status == "in_progress")) | .[0].name) // "")] | join("|")' 2>/dev/null || true)"
  if [ -n "$state" ] && [ "$(echo "$state" | cut -d'|' -f1-3)" != "$(echo "$seen" | cut -d'|' -f1-3)" ]; then
    printf '  .. %s\n' "$(echo "$state" | cut -d'|' -f3)"
  fi
  now="$(echo "$state" | cut -d'|' -f4)"
  if [ -n "$now" ] && [ "$now" != "$step" ]; then
    printf '  .. %s  %s: %s\n' "$(date +%H:%M)" "$JOB" "$now"
    step="$now"
  fi
  [ -n "$state" ] && seen="$state"
  [ "$(echo "$state" | cut -d'|' -f1)" = "completed" ] && break
  sleep 30
done
if [ "$(echo "$seen" | cut -d'|' -f2)" = "success" ]; then
  ok "$VERSION build $BUILD on TestFlight, What to Test written, given to the testers"
else
  die "run $RUN: $(echo "$seen" | cut -d'|' -f2); what failed: gh run view $RUN -R $REPO --log-failed"
fi
