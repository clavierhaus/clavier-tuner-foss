#!/usr/bin/env bash
#
# verify-reproducible.sh <ref> -- the reproducibility proof: build ONE commit twice, in two
# independent worktrees, and compare the unsigned release APKs byte for byte.
#
# Prints
#   build a: <sha256>  <path>
#   build b: <sha256>  <path>
# and exits 0 when they are identical, 1 when they differ (the worktrees are kept for
# diffoscope and their paths printed), 2 on a build failure.
#
# Why two worktrees and not two runs in one tree: a second run in the same tree reuses the
# first run's outputs and proves nothing. Why --no-daemon: a daemon carries state between
# builds. What F-Droid does is the same thing in its own environment, so an APK that does not
# reproduce here will not reproduce there, and the release script refuses to publish it
# (scripts/release-fdroid.sh). Requirements: f-droid.org/docs/Reproducible_Builds.
#
# What two worktrees cannot see: content that depends on the ENVIRONMENT and not on the
# commit. Both worktrees are git checkouts, so AGP's version-control-info.textproto agreed
# here and differed against F-Droid's build (v0.1.0, 10 Oct 2026: NO_VALID_GIT_FOUND on their
# side); vcsInfo is off in the release build type for that reason. The fork's CI is the proof
# against the environment; this script is the proof against the tree.
#
set -euo pipefail
REF="${1:-HEAD}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT"
SHA_REF="$(git rev-parse --verify "$REF^{commit}")"
T="$(mktemp -d "${TMPDIR:-/tmp}/ct-repro.XXXXXX")"
declare -A SUM
for i in a b; do
  W="$T/$i"
  git worktree add -q --detach "$W" "$SHA_REF"
  # the SDK location travels with the checkout; nothing else of the working tree does
  [ -f local.properties ] && cp local.properties "$W/"
  ( cd "$W" && ./gradlew -q --no-daemon :androidApp:assembleRelease ) > "$T/build-$i.log" 2>&1 \
    || { echo "build $i failed -- see $T/build-$i.log" >&2; exit 2; }
  APK="$(ls "$W"/androidApp/build/outputs/apk/release/*-unsigned.apk 2>/dev/null | head -n1)"
  [ -n "$APK" ] || { echo "build $i: no unsigned APK under androidApp/build/outputs/apk/release" >&2; exit 2; }
  SUM[$i]="$(sha256sum "$APK" | cut -d' ' -f1)"
  printf 'build %s: %s  %s\n' "$i" "${SUM[$i]}" "$APK"
done
if [ "${SUM[a]}" = "${SUM[b]}" ]; then
  git worktree remove --force "$T/a"; git worktree remove --force "$T/b"; rm -rf "$T"
  echo "reproducible: $(git rev-parse --short "$SHA_REF") builds the same unsigned APK twice"
  exit 0
fi
echo "NOT reproducible: the two builds of $(git rev-parse --short "$SHA_REF") differ." >&2
echo "worktrees kept: $T/a and $T/b -- diffoscope the two APKs; then: git worktree remove --force $T/a $T/b" >&2
exit 1
