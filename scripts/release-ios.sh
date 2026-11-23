#!/usr/bin/env bash
#
# release-ios.sh -- the iPhone app to TestFlight, with what its testers read (Peter, 10 Nov 2026: "Could you do this
# as a release-ios.sh in foss as soon as new things happen?"), built on Codemagic since 23 Nov 2026 (Peter, 22:35: "Now I
# would like to try out my new ios build service at Codemagic").
#
#   ./scripts/release-ios.sh          the checks, then one question
#   ./scripts/release-ios.sh --yes    without the question: Peter's yes on Android is already given
#   ./scripts/release-ios.sh --notes-for 1003
#                                     only the notes and the testers, for a build already on TestFlight; nothing is built
#
# The order does not change: the iPhone is built after Android, never before. Android is built on the X1, Peter tests
# it and says yes, and only then does this run, on the X1. What it does:
#   1. checks: a clean tree on main, in sync with origin; the iPhone's version the same as Android's; the notes
#      fastlane/metadata/ios/testflight/<locale>.txt written for this version (first line the version, en-US at
#      least, at most 4000 characters); App Store Connect ready for testers (scripts/testflight_ready.py);
#   2. starts codemagic.yaml's foss-testflight through Codemagic's API and follows it: the framework, the archive signed,
#      the upload;
#   3. here, on the X1, scripts/testflight.py waits for Apple's processing, writes What to Test from the notes and gives
#      the build to the tester groups (Beta App Review when an external group is among them): an hour of waiting costs
#      no Mac minute.
# Codemagic's API token: ~/.clavier-codemagic/token (Codemagic > Teams > Personal Account > Integrations > Codemagic API);
# the app's id is looked up once and kept in ~/.clavier-codemagic/app-foss. The App Store Connect key: ASC_KEY, or the
# newest AuthKey_<ID>.p8 in ~/Downloads, as testflight_ready.py finds it. A session keeps the notes current with every
# change a tester would notice, as it keeps CHANGELOG.md.
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
BUNDLE=at.clavierhaus.claviertuner
VERSION="$(sed -n 's/.*versionName = "\(.*\)"/\1/p' androidApp/build.gradle.kts)"
IOS="$(sed -n 's/^ *MARKETING_VERSION: "\(.*\)"/\1/p' iosApp/project.yml)"
NOTES=fastlane/metadata/ios/testflight
ISSUER_DEFAULT="4aff3636-5757-45f5-b9f5-58987d53bb80"   # the team's, as testflight_ready.py has it

# the App Store Connect key on the X1, for testflight.py here
KEY="${ASC_KEY:-$(ls -t "$HOME"/Downloads/AuthKey_*.p8 2>/dev/null | head -n1 || true)}"
[ -n "$KEY" ] && [ -f "$KEY" ] || die "no App Store Connect key: set ASC_KEY to the .p8, or keep AuthKey_<ID>.p8 in ~/Downloads"
KEY_ID="${ASC_KEY_ID:-$(basename "$KEY" .p8 | sed 's/^AuthKey_//')}"
ISSUER="${ASC_ISSUER:-$ISSUER_DEFAULT}"

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
ok "$VERSION, $(git rev-parse --short HEAD), notes: $(cd "$NOTES" && ls *.txt | sed 's/\.txt$//' | paste -sd' ')"
ASC_KEY="$KEY" ASC_KEY_ID="$KEY_ID" ASC_ISSUER="$ISSUER" python3 scripts/testflight_ready.py --bundle-id "$BUNDLE" \
  || die "App Store Connect not ready (above)"

notes() {  # notes <build>: What to Test and the testers, here on the X1
  python3 scripts/testflight.py --key "$KEY" --key-id "$KEY_ID" --issuer "$ISSUER" --bundle-id "$BUNDLE" \
    --version "$VERSION" --build "$1" --notes "$NOTES" --wait 120
}

if [ -n "$NOTES_FOR" ]; then
  notes "$NOTES_FOR"
  ok "notes and testers for build $NOTES_FOR"
  exit 0
fi

# 2. Codemagic
CM="$HOME/.clavier-codemagic"
TOKEN="${CODEMAGIC_TOKEN:-$(cat "$CM/token" 2>/dev/null || true)}"
APP="${CODEMAGIC_APP:-$(cat "$CM/app-foss" 2>/dev/null || true)}"
[ -n "$TOKEN" ] || die "no Codemagic token: Codemagic > Teams > Personal Account > Integrations > Codemagic API, show, into $CM/token"
api() {  # api GET|POST path [json]
  curl -sS -f -X "$1" "https://api.codemagic.io$2" -H "x-auth-token: $TOKEN" -H "Content-Type: application/json" ${3:+-d "$3"}
}
if [ -z "$APP" ]; then
  APP="$(api GET /apps | python3 -c '
import json, sys
apps = json.load(sys.stdin).get("applications", [])
hits = [a["_id"] for a in apps if "clavier-tuner-foss" in json.dumps(a.get("repository", {})) or a.get("appName") == "clavier-tuner-foss"]
print(hits[0] if len(hits) == 1 else "")')" || true
  [ -n "$APP" ] || die "Codemagic shows no single app for clavier-tuner-foss with this token: add the repository on codemagic.io first"
  mkdir -p "$CM" && printf '%s\n' "$APP" > "$CM/app-foss"
  ok "Codemagic app $APP, kept in $CM/app-foss"
fi
api GET "/apps/$APP" >/dev/null || die "Codemagic does not know app $APP with this token"

if [ "$YES" -ne 1 ]; then
  printf '  Send Clavier Tuner %s to TestFlight? Only after the yes on Android. [y/N] ' "$VERSION"
  read -r answer
  [ "$answer" = "y" ] || [ "$answer" = "Y" ] || die "not sent"
fi

ID="$(api POST /builds "{\"appId\":\"$APP\",\"workflowId\":\"foss-testflight\",\"branch\":\"main\"}" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["buildId"])')" || die "Codemagic refused the build"
ok "Codemagic build $ID started: https://codemagic.io/app/$APP/build/$ID"

seen=""
while :; do
  state="$(api GET "/builds/$ID" | python3 -c '
import json, sys
b = json.load(sys.stdin)["build"]
now = next((a["name"] for a in b.get("buildActions", []) if a.get("status") in ("building", "in_progress")), "")
print(b["status"] + "|" + str(b.get("buildNumber", "")) + "|" + now)' 2>/dev/null || true)"
  if [ -n "$state" ] && [ "$state" != "$seen" ]; then
    printf '  .. %s  %s  %s\n' "$(date +%H:%M)" "$(cut -d'|' -f1 <<<"$state")" "$(cut -d'|' -f3 <<<"$state")"
    seen="$state"
  fi
  case "$(cut -d'|' -f1 <<<"$state")" in
    finished) break ;;
    failed|canceled|timeout|skipped) die "Codemagic build $ID: $(cut -d'|' -f1 <<<"$state") (https://codemagic.io/app/$APP/build/$ID)" ;;
  esac
  sleep 30
done
BUILD=$((1000 + $(cut -d'|' -f2 <<<"$seen")))
ok "uploaded: Clavier Tuner $VERSION build $BUILD"

# 3. the notes and the testers, here
notes "$BUILD"
ok "Clavier Tuner $VERSION build $BUILD on TestFlight, notes and testers set"
