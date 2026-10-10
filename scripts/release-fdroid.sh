#!/usr/bin/env bash
#
# release-fdroid.sh -- one-command GitHub + F-Droid release of Clavier Tuner, reproducible.
# The cycle that finally worked for gnubg (release_fdroid.sh, 2026-09-11), with gnubg's
# engine gates replaced by this engine's and nothing else changed in its design.
#
#   ./scripts/release-fdroid.sh --version 0.2.0 --summary "..."   the release
#   ./scripts/release-fdroid.sh --dry-run ...                      preflight + proof + plan only
#   ./scripts/release-fdroid.sh --resume ...                       redo steps 4-5 after an abort
#   ./scripts/release-fdroid.sh --version X.Y.Z --retag            F-Droid's CI refused the tag:
#                                                                  drop tag + GitHub release, redo
#
# The cycle:
#   0. preflight: clean tree on main in sync with origin, gh authenticated, the signing key,
#      apksigner 34, the helpers present and the recipe appender self-checked on a copy, the
#      fdroiddata clone beside the repository; the engine gate (the core tests on the JVM).
#   1. bump versionCode/versionName, roll CHANGELOG, write the fastlane changelog, commit, push.
#   2. the REPRODUCIBILITY PROOF on the bump commit -- scripts/verify-reproducible.sh builds it
#      twice in independent worktrees; the unsigned APKs must be byte-identical. No proof, no
#      release. Then scripts/release.sh: tag, the same build again (refused unless its bytes
#      are the proof's), signed by our key, GitHub release. That APK is the reference F-Droid
#      compares against (Binaries:, AllowedAPKSigningKeys: in the recipe).
#   3. append a build block for this version to the fdroiddata fork's recipe (seeded from
#      fdroid/<appid>.yml on the first release), push the fork's branch -> F-Droid's CI rebuilds
#      the app in its own environment and compares its bytes with the reference.
#   4. wait for the 'fdroid build' job. success = F-Droid reproduced our APK: open the merge
#      request with a green pipeline and nothing to explain (first release) -- after that,
#      AutoUpdateMode: Version in the recipe lets F-Droid's bot pick up every new tag by
#      itself. failed = a real finding: the job log says whether the build failed (recipe,
#      environment) or differed (diffoscope names the file); it is fixed in the BUILD and
#      re-tagged. NOTHING is ever uploaded over the reference to make the comparison pass.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT"
GRADLE="androidApp/build.gradle.kts"
APPID="$(sed -n 's/.*applicationId = "\(.*\)"/\1/p' "$GRADLE")"
GL_HOST="https://gitlab.com"
JOB_NAME="fdroid build"
BUILD_BRANCH="$APPID"                       # the fork's branch the CI builds from
POLL_SECS=30
POLL_MAX=90                                 # 45 min ceiling
# The fdroiddata clone lives BESIDE the repository (/home/erweitert/fdroiddata next to
# /home/erweitert/clavier-tuner-foss), never under $HOME: the shell user and the tree's owner
# differ on the X1 (gnubg, 2026-09-11: "dubious ownership").
FDROIDDATA="${FDROIDDATA:-$(dirname "$ROOT")/fdroiddata}"

B=$'\033[1m'; X=$'\033[0m'
ok()   { printf '  %sok%s %s\n' "$B" "$X" "$*"; }
warn() { printf '  %s!!%s %s\n' "$B" "$X" "$*"; }
die()  { printf '  %sx%s  %s\n' "$B" "$X" "$*" >&2; exit 1; }
hr()   { printf -- '----------------------------------------------------------\n'; }

VERSION=""; SUMMARY=""; DRY=0; RESUME=0; RETAG=0
while [ $# -gt 0 ]; do
  case "$1" in
    --version) VERSION="$2"; shift 2 ;;
    --summary) SUMMARY="$2"; shift 2 ;;
    --dry-run) DRY=1; shift ;;
    # --resume: the bump is committed and the tag + GitHub release exist (steps 1-2 done);
    # redo only the fdroiddata push and the CI wait. Refuses unless the tree is AT --version
    # and the tag resolves, so a plain re-run cannot bump the version a second time.
    --resume) RESUME=1; shift ;;
    # --retag: F-Droid's CI did not reproduce the tagged build and the fix is committed on main;
    # the tag and the GitHub release of --version are dropped and made again from HEAD. Refused
    # once the version is in fdroiddata upstream -- from then on a fix is a new version.
    --retag) RETAG=1; shift ;;
    *) die "unknown argument: $1" ;;
  esac
done

# --- 0. current + next version ----------------------------------------------------------
CUR_NAME="$(sed -n 's/.*versionName = "\(.*\)"/\1/p' "$GRADLE")"
CUR_CODE="$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' "$GRADLE")"
[ -n "$CUR_NAME" ] && [ -n "$CUR_CODE" ] || die "cannot read the version from $GRADLE"
[ -n "$VERSION" ] || VERSION="$(echo "$CUR_NAME" | awk -F. '{printf "%d.%d.%d", $1, $2, $3 + 1}')"
# a tree already staged at the requested version is released AS-IS (the first release: 0.1.0
# is in the build file before any bump)
STAGED=0
if [ "$VERSION" = "$CUR_NAME" ]; then STAGED=1; NEW_CODE=$CUR_CODE; else NEW_CODE=$((CUR_CODE + 1)); fi
TAG="v$VERSION"

hr
printf '%sClavier Tuner -- GitHub + F-Droid release%s\n' "$B" "$X"
hr
printf 'current : %s (code %s)\n' "$CUR_NAME" "$CUR_CODE"
printf 'next    : %s (code %s), tag %s\n' "$VERSION" "$NEW_CODE" "$TAG"
hr

# --- preflight -----------------------------------------------------------------------------
[ -z "$(git status --porcelain)" ] || die "working tree is dirty -- commit or stash first"
[ "$(git rev-parse --abbrev-ref HEAD)" = "main" ] || die "not on main"
git fetch -q origin
[ "$(git rev-parse main)" = "$(git rev-parse origin/main)" ] || die "main not in sync with origin"
command -v gh >/dev/null || die "gh required"
gh auth status >/dev/null 2>&1 || die "gh not authenticated"
[ -f androidApp/keystore.properties ] || die "androidApp/keystore.properties missing (signing)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$(sed -n 's#^sdk.dir=##p' local.properties 2>/dev/null | sed 's#\\:#:#g')}}"
ls -d "$SDK"/build-tools/34.*/apksigner >/dev/null 2>&1 || die "apksigner from build-tools 34.x not found under ${SDK:-?}/build-tools"
mkdir -p tmp
for helper in scripts/verify-reproducible.sh scripts/fdroid-recipe-append.py scripts/release.sh; do
  [ -f "$helper" ] || die "missing helper: $helper"
done
[ -f "fdroid/$APPID.yml" ] || die "reference recipe fdroid/$APPID.yml missing"
[ -d "$FDROIDDATA/.git" ] || die "fdroiddata clone not found at $FDROIDDATA (set FDROIDDATA=...)"
# fdroidserver is required, and the CI's own: the recipe goes up in the format `fdroid
# rewritemeta` writes, because the fork's CI rewrites it and fails the MR on any difference
# (10 Oct 2026: the rewritemeta and checkupdates jobs, on a recipe PyYAML had written). The
# format is ruamel.yaml's and changes with its version: 0.17 keeps a long Binaries: line on
# its line, 0.18 folds it onto the next, 0.19 folds AllowedAPKSigningKeys: too. The CI is
# fdroidserver master on Debian trixie, ruamel 0.18.10 (fdroiddata .gitlab-ci.yml), so that is
# what runs here -- the PyPI release pins ruamel below 0.17.22 and is of no use:
#   pipx install git+https://gitlab.com/fdroid/fdroidserver.git
#   pipx inject fdroidserver 'ruamel.yaml==0.18.10'
FDROID_INSTALL="pipx install git+https://gitlab.com/fdroid/fdroidserver.git && pipx inject fdroidserver 'ruamel.yaml==0.18.10'"
command -v fdroid >/dev/null || die "fdroidserver (fdroid) not on PATH -- $FDROID_INSTALL"
FD_PY="$(dirname "$(readlink -f "$(command -v fdroid)")")/python"; [ -x "$FD_PY" ] || FD_PY=python3
RUAMEL="$("$FD_PY" -c 'import ruamel.yaml as r; print(r.__version__)' 2>/dev/null || echo 0)"
case "$RUAMEL" in 0.18.*) ;; *) die "fdroid's ruamel.yaml is $RUAMEL; F-Droid's CI writes recipes with 0.18.10 and a recipe in another version's layout fails its rewritemeta job -- $FDROID_INSTALL" ;; esac
ok "fdroidserver $(fdroid --version 2>/dev/null | head -n1) with ruamel.yaml $RUAMEL"
# Self-check FIRST, before anything that costs CPU: the appender must succeed on a scratch
# copy and yield a valid recipe with one block for the new code.
if [ -f "$FDROIDDATA/metadata/$APPID.yml" ]; then cp "$FDROIDDATA/metadata/$APPID.yml" tmp/recipe_selfcheck.yml; else cp "fdroid/$APPID.yml" tmp/recipe_selfcheck.yml; fi
python3 scripts/fdroid-recipe-append.py tmp/recipe_selfcheck.yml "$VERSION" "$NEW_CODE" 0000000000000000000000000000000000000000 "fdroid/$APPID.yml" >/dev/null \
  || die "recipe appender failed on a copy of the recipe"
python3 -c "import sys,yaml; d=yaml.safe_load(open(sys.argv[1])); b=[x['versionCode'] for x in d['Builds']]; assert len(b)==len(set(b)); assert int(sys.argv[2]) in b" tmp/recipe_selfcheck.yml "$NEW_CODE" \
  || die "recipe appender produced an invalid recipe (tmp/recipe_selfcheck.yml)"
ok "self-check: helpers present, recipe appender yields a valid recipe with one $NEW_CODE block"
# The engine gate: the core suite on the JVM, as the X1 runs it before every push.
./gradlew -q --no-daemon :shared:testAndroidHostTest > tmp/release_tests.log 2>&1 || die "core tests failed -- see tmp/release_tests.log"
ok "core tests green"
# The GitLab fork the CI runs in: the fdroiddata clone's remote that is not upstream.
FORK_URL="$(git -C "$FDROIDDATA" remote -v | grep -v 'fdroid/fdroiddata' | grep gitlab.com | awk '{print $2}' | head -n1)"
[ -n "$FORK_URL" ] || die "no gitlab.com fork remote in $FDROIDDATA"
FORK_REMOTE="$(git -C "$FDROIDDATA" remote -v | grep -F "$FORK_URL" | awk '{print $1}' | head -n1)"
GL_PROJ="$(printf '%s' "$FORK_URL" | sed -E 's#^(git@gitlab\.com:|https://gitlab\.com/)##; s#\.git$##' | sed 's#/#%2F#g')"
ok "fdroiddata fork: $FORK_URL (remote $FORK_REMOTE, project $GL_PROJ)"

prove_reproducible() {
  ./scripts/verify-reproducible.sh "$1" > tmp/release_repro.log 2>&1 \
    || die "scripts/verify-reproducible.sh $1: NOT REPRODUCIBLE -- see tmp/release_repro.log; fix the build, never the release"
  REPRO_SHA="$(sed -n 's/^build a: \([0-9a-f]*\).*/\1/p' tmp/release_repro.log | head -n1)"
  [ -n "$REPRO_SHA" ] || die "could not read the unsigned APK sha256 from tmp/release_repro.log"
  ok "reproducible: unsigned APK $REPRO_SHA (two independent worktrees of $(git rev-parse --short "$1"))"
}
ok "preflight clean"

if [ "$DRY" -eq 1 ]; then
  prove_reproducible HEAD
  hr; ok "DRY RUN -- would do:"
  printf '    bump %s -> %s (code %s), roll CHANGELOG, fastlane changelogs/%s.txt\n' "$CUR_NAME" "$VERSION" "$NEW_CODE" "$NEW_CODE"
  printf '    commit+push main; prove the bump commit; ./scripts/release.sh (tag %s, signed APK, GitHub release)\n' "$TAG"
  printf '    %s: recipe -> branch %s -> push %s -> CI\n' "$FDROIDDATA" "$BUILD_BRANCH" "$FORK_REMOTE"
  printf '    wait for the %s job: success = verified, open the MR; failed = fix the build, re-tag\n' "$JOB_NAME"
  exit 0
fi

if [ "$RESUME" -eq 1 ]; then
  [ "$STAGED" -eq 1 ] || die "--resume: tree is at $CUR_NAME, not $VERSION -- nothing to resume"
  git rev-parse -q --verify "refs/tags/$TAG" >/dev/null || die "--resume: tag $TAG does not exist"
  ok "resume: bump and tag $TAG already done, skipping to fdroiddata"
  prove_reproducible "$TAG"
fi

# --- 1. bump + changelog ------------------------------------------------------------------------
if [ "$RESUME" -eq 0 ]; then
  if [ "$STAGED" -eq 0 ]; then
    sed -i "s/versionCode = $CUR_CODE/versionCode = $NEW_CODE/" "$GRADLE"
    sed -i "s/versionName = \"$CUR_NAME\"/versionName = \"$VERSION\"/" "$GRADLE"
  fi
  # the changelog's Unreleased becomes this version (also for a tree staged at it, as 0.1.0 is)
  grep -q "^## $VERSION" CHANGELOG.md || sed -i "s/^## Unreleased/## Unreleased\n\n## $VERSION -- $(date +%Y-%m-%d)/" CHANGELOG.md
  mkdir -p fastlane/metadata/android/en-US/changelogs
  [ -f "fastlane/metadata/android/en-US/changelogs/$NEW_CODE.txt" ] || printf '%s\n' "${SUMMARY:-Bug fixes and improvements.}" > "fastlane/metadata/android/en-US/changelogs/$NEW_CODE.txt"
  # the reference recipe follows: its one block is set to the new version
  sed -i -e "0,/^  - versionName: .*/s//  - versionName: $VERSION/" \
         -e "0,/^    versionCode: .*/s//    versionCode: $NEW_CODE/" \
         -e "s/^CurrentVersion: .*/CurrentVersion: $VERSION/" \
         -e "s/^CurrentVersionCode: .*/CurrentVersionCode: $NEW_CODE/" "fdroid/$APPID.yml"
  git add "$GRADLE" CHANGELOG.md fastlane "fdroid/$APPID.yml"
  git commit -q -m "release: $VERSION" || ok "nothing to commit: tree already staged at $VERSION"
  git push -q origin main
  ok "version $VERSION (code $NEW_CODE) committed and pushed"

  # --- 2. the proof on THE commit release.sh builds, then the GitHub release ---------------------
  if [ "$RETAG" -eq 1 ]; then
    git -C "$FDROIDDATA" fetch -q upstream master 2>/dev/null || true
    if git -C "$FDROIDDATA" show "upstream/master:metadata/$APPID.yml" 2>/dev/null | grep -q "versionCode: $NEW_CODE\$"; then
      die "--retag: $VERSION ($NEW_CODE) is already in fdroiddata upstream -- F-Droid has it; a fix is a new version"
    fi
    if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
      OLD_SHA="$(git rev-list -n1 "$TAG")"
      [ "$OLD_SHA" != "$(git rev-parse HEAD)" ] || die "--retag: $TAG is already on HEAD -- nothing changed since; fix the build first"
      git tag -d "$TAG" >/dev/null
      git push -q origin ":refs/tags/$TAG" 2>/dev/null || true
      ok "retag: dropped $TAG from $(printf '%s' "$OLD_SHA" | cut -c1-7) (local and origin)"
    fi
    if gh release view "$TAG" >/dev/null 2>&1; then
      gh release delete "$TAG" --yes --cleanup-tag >/dev/null 2>&1 || gh release delete "$TAG" --yes
      ok "retag: deleted GitHub release $TAG (the refused reference APK)"
    fi
  fi
  prove_reproducible HEAD
  EXPECT_UNSIGNED_SHA="$REPRO_SHA" ./scripts/release.sh || die "release.sh failed"
  git pull -q --ff-only origin main   # release.sh may have committed the signing certificate
  ok "GitHub release $TAG published with the reference APK (unsigned bytes $REPRO_SHA, signed by our key)"
fi

# F-Droid's review rule: commit: is the full hash, never a tag name.
TAG_SHA="$(git rev-list -n1 "$TAG")"
[ -n "$TAG_SHA" ] || die "cannot resolve $TAG"
if ! grep -q "^    commit: $TAG_SHA" "fdroid/$APPID.yml"; then
  sed -i "s/^    commit: .*/    commit: $TAG_SHA/" "fdroid/$APPID.yml"
  git add "fdroid/$APPID.yml"
  git commit -q -m "fdroid: reference recipe at $TAG ($TAG_SHA)" || true
  git push -q origin main
fi

# --- 3. fdroiddata fork: recipe -> new version, push -> CI --------------------------------------
cd "$FDROIDDATA"
META="metadata/$APPID.yml"
if [ -n "$(git status --porcelain -- "$META")" ]; then
  warn "restoring local edits to $META (leftover of a previous run; regenerated below)"
  git checkout -q -- "$META"
fi
[ -z "$(git status --porcelain --untracked-files=no)" ] || die "fdroiddata clone has tracked modifications beyond $META -- inspect: git -C $FDROIDDATA status --porcelain --untracked-files=no"
git remote get-url upstream >/dev/null 2>&1 || git remote add upstream https://gitlab.com/fdroid/fdroiddata.git
git fetch -q upstream master
git checkout -q -B "$BUILD_BRANCH" upstream/master
NEW_APP=0
if [ ! -f "$META" ]; then
  NEW_APP=1
  cp "$ROOT/fdroid/$APPID.yml" "$META"           # first submission: the reference, as it is
  # fdroiddata carries no comments of ours
  python3 - "$META" <<'PY'
import sys, yaml
p = sys.argv[1]
d = yaml.safe_load(open(p))
for b in d.get("Builds", []):
    b["commit"] = str(b["commit"])
    if "gradle" in b:
        b["gradle"] = ["yes" if g is True else g for g in b["gradle"]]   # the string, as F-Droid's schema wants it
if "AllowedAPKSigningKeys" in d:
    d["AllowedAPKSigningKeys"] = str(d["AllowedAPKSigningKeys"])
yaml.safe_dump(d, open(p, "w"), sort_keys=False, allow_unicode=True, default_flow_style=False, width=120)
PY
else
  python3 "$ROOT/scripts/fdroid-recipe-append.py" "$META" "$VERSION" "$NEW_CODE" "$TAG_SHA" "$ROOT/fdroid/$APPID.yml" || die "recipe update failed"
fi
# The recipe goes up as fdroidserver writes it -- the Quick Start Guide's own sequence
# (rewritemeta, lint), and what the fork's CI checks it against.
fdroid rewritemeta "$APPID" >/dev/null 2>&1 || die "fdroid rewritemeta failed on $META"
fdroid lint "$APPID" || die "fdroid lint found something in $META -- fix it in fdroid/$APPID.yml"
ok "recipe in fdroidserver's format, lint clean"
git add "$META"
# the commit message fdroiddata's reviewers expect: "New App: <id>" the first time, an update after
if [ "$NEW_APP" -eq 1 ]; then git commit -q -m "New App: $APPID" || true
else git commit -q -m "Update $APPID to $VERSION ($NEW_CODE)" || true; fi
git push -q -f "$FORK_REMOTE" "$BUILD_BRANCH"
SHA="$(git rev-parse HEAD)"
cd "$ROOT"
ok "fork recipe pushed ($SHA) -- CI building in F-Droid's environment"

# --- 4. wait for the 'fdroid build' job ----------------------------------------------------------
printf '  waiting for CI (up to %s min): ' "$((POLL_SECS * POLL_MAX / 60))"
JOB_ID=""; JOB_STATUS=""
for _ in $(seq 1 "$POLL_MAX"); do
  PIPE_ID="$(curl -sf "$GL_HOST/api/v4/projects/$GL_PROJ/pipelines?sha=$SHA&per_page=1" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d[0]['id'] if d else '')" 2>/dev/null || true)"
  if [ -n "$PIPE_ID" ]; then
    read -r JOB_ID JOB_STATUS < <(curl -sf "$GL_HOST/api/v4/projects/$GL_PROJ/pipelines/$PIPE_ID/jobs?per_page=50" | python3 -c "
import json,sys
for j in json.load(sys.stdin):
    if j['name'] == '$JOB_NAME':
        print(j['id'], j['status']); break
else:
    print(' ', 'pending')")
    case "$JOB_STATUS" in success|failed) break ;; esac
  fi
  printf '.'; sleep "$POLL_SECS"
done
printf '\n'
[ -n "$JOB_ID" ] && [ "$JOB_ID" != " " ] || die "CI job '$JOB_NAME' not found for $SHA"
case "$JOB_STATUS" in
  success)
    ok "F-Droid's CI rebuilt $TAG and it MATCHED the reference APK -- the reproducibility claim, proven by F-Droid" ;;
  failed)
    hr
    printf '  x  F-Droid'"'"'s CI build of %s did NOT match the reference APK.\n' "$TAG" >&2
    printf '     Job: %s/%s/-/jobs/%s\n' "$GL_HOST" "$(printf '%s' "$GL_PROJ" | sed 's/%2F/\//g')" "$JOB_ID" >&2
    printf '     Read the job log. Build failed before comparing = the recipe or the environment.\n' >&2
    printf '     Built and differed = download the artifact and diffoscope it against\n' >&2
    printf '     tmp/clavier-tuner-%s-unsigned.apk; the differing file names the cause.\n' "$VERSION" >&2
    printf '     Nothing is replaced on the GitHub release. Fix the build, commit, then:\n' >&2
    printf '       ./scripts/release-fdroid.sh --version %s --retag\n' "$VERSION" >&2
    die "release $TAG is NOT verified; do not open the fdroiddata merge request" ;;
  *) die "CI job did not finish in time (status: $JOB_STATUS). Re-run with --resume later." ;;
esac
hr
if [ "$NEW_APP" -eq 1 ]; then
  ok "released $VERSION -- verified by F-Droid's CI. Now open the merge request on gitlab.com from branch $BUILD_BRANCH of the fork to fdroid/fdroiddata master (template: New App); its pipeline is already green. Then wait for the packagers and answer them; 24-48 h after the merge the app is on F-Droid."
else
  ok "released $VERSION -- verified by F-Droid's CI. F-Droid's bot picks the tag up by itself (AutoUpdateMode: Version); the fork branch is there if it does not."
fi
hr
