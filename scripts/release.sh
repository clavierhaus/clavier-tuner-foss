#!/usr/bin/env bash
#
# release.sh -- the GitHub release of the version the tree stands at: tag, build, sign,
# publish. Called by release-fdroid.sh after the bump; usable alone.
#
#   ./scripts/release.sh                       tag vX.Y.Z (X.Y.Z from build.gradle.kts), build,
#                                              sign, GitHub release with the signed APK
#   EXPECT_UNSIGNED_SHA=<sha256> ./scripts/release.sh
#                                              refuse to publish unless the unsigned APK built
#                                              here has exactly this sha256 (the proof's)
#
# The APK published here is the reference F-Droid compares its own build against
# (fdroid/<appid>.yml: Binaries:), signed by our key (AllowedAPKSigningKeys:). So:
#   - the build is ./gradlew assembleRelease from the tagged commit of a clean tree, never
#     from Android Studio (f-droid.org/docs/Reproducible_Builds);
#   - the unsigned APK is signed as it comes out of the build, with apksigner from build-tools
#     34, nothing aligned or rewritten after the build -- F-Droid copies our signature onto ITS
#     unsigned build, so the bytes before the signature must be the same on both sides;
#   - the signing certificate's SHA-256 is written into the reference recipe.
# Signing needs androidApp/keystore.properties (never committed):
#   storeFile=/absolute/path/to/clavier-tuner.jks
#   storePassword=...
#   keyAlias=...
#   keyPassword=...
#
set -euo pipefail
B=$'\033[1m'; X=$'\033[0m'
ok()  { printf '  %sok%s %s\n' "$B" "$X" "$*"; }
die() { printf '  %sx%s  %s\n' "$B" "$X" "$*" >&2; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT"
GRADLE="androidApp/build.gradle.kts"
APPID="$(sed -n 's/.*applicationId = "\(.*\)"/\1/p' "$GRADLE")"
VERSION="$(sed -n 's/.*versionName = "\(.*\)"/\1/p' "$GRADLE")"
CODE="$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' "$GRADLE")"
TAG="v$VERSION"
KS="androidApp/keystore.properties"
RECIPE="fdroid/$APPID.yml"

[ -z "$(git status --porcelain)" ] || die "working tree is dirty"
[ "$(git rev-parse --abbrev-ref HEAD)" = "main" ] || die "not on main"
git fetch -q origin
[ "$(git rev-parse main)" = "$(git rev-parse origin/main)" ] || die "main not in sync with origin"
command -v gh >/dev/null && gh auth status >/dev/null 2>&1 || die "gh not authenticated"
[ -f "$KS" ] || die "$KS missing (storeFile, storePassword, keyAlias, keyPassword)"
git ls-files --error-unmatch "$KS" >/dev/null 2>&1 && die "$KS is tracked by git -- remove it from the tree first"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$(sed -n 's#^sdk.dir=##p' local.properties 2>/dev/null | sed 's#\\:#:#g')}}"
[ -n "$SDK" ] && [ -d "$SDK/build-tools" ] || die "Android SDK not found (ANDROID_HOME, ANDROID_SDK_ROOT or local.properties)"
APKSIGNER="$(ls -d "$SDK"/build-tools/34.*/apksigner 2>/dev/null | sort -V | tail -n1)"
[ -n "$APKSIGNER" ] || die "apksigner from build-tools 34.x not found under $SDK/build-tools (F-Droid's guidance: build-tools >= 35 produce APKs apksigcopier cannot verify)"
if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
  [ "$(git rev-list -n1 "$TAG")" = "$(git rev-parse HEAD)" ] || die "tag $TAG exists on another commit"
  ok "tag $TAG already on HEAD"
else
  if git config user.signingkey >/dev/null 2>&1; then git tag -s "$TAG" -m "Clavier Tuner $VERSION"; else git tag -a "$TAG" -m "Clavier Tuner $VERSION"; fi
  ok "tagged $TAG"
fi

# The build is made in a FRESH worktree of HEAD, exactly as the proof builds and as F-Droid
# builds: from a clean checkout, never incrementally. On 10 Oct 2026 (the first run of 0.1.0)
# the proof's two worktrees agreed on 835357... and the build in the working tree, with its
# build/, .gradle/ and .kotlin/ from the day's debug builds, gave 373745... of the same commit;
# the working tree's bytes are nobody's reference.
printf 'building %s (%s) from %s in a fresh worktree ...\n' "$VERSION" "$CODE" "$(git rev-parse --short HEAD)"
WT="$(mktemp -d "${TMPDIR:-/tmp}/ct-release.XXXXXX")/src"
git worktree add -q --detach "$WT" HEAD
[ -f local.properties ] && cp local.properties "$WT/"
( cd "$WT" && ./gradlew -q --no-daemon :androidApp:assembleRelease ) || die "the release build failed in $WT"
UNSIGNED_WT="$(ls "$WT"/androidApp/build/outputs/apk/release/*-unsigned.apk | head -n1)"
[ -n "$UNSIGNED_WT" ] || die "no unsigned APK built in $WT"
mkdir -p tmp
UNSIGNED="tmp/clavier-tuner-$VERSION-unsigned.apk"
cp "$UNSIGNED_WT" "$UNSIGNED"
git worktree remove --force "$WT"; rm -rf "$(dirname "$WT")"
SHA="$(sha256sum "$UNSIGNED" | cut -d' ' -f1)"
ok "unsigned APK $SHA ($UNSIGNED, the bytes F-Droid's build must match)"
if [ -n "${EXPECT_UNSIGNED_SHA:-}" ] && [ "$EXPECT_UNSIGNED_SHA" != "$SHA" ]; then
  die "the APK built here ($SHA) is not the one the proof built ($EXPECT_UNSIGNED_SHA) -- nothing is published"
fi

# sign, as it is, with our key
OUT="tmp/clavier-tuner-$VERSION.apk"
STORE="$(sed -n 's/^storeFile=//p' "$KS")"; [ -f "$STORE" ] || STORE="androidApp/$STORE"
"$APKSIGNER" sign --ks "$STORE" \
  --ks-pass "pass:$(sed -n 's/^storePassword=//p' "$KS")" \
  --ks-key-alias "$(sed -n 's/^keyAlias=//p' "$KS")" \
  --key-pass "pass:$(sed -n 's/^keyPassword=//p' "$KS")" \
  --out "$OUT" "$UNSIGNED"
"$APKSIGNER" verify "$OUT" || die "the signed APK does not verify"
CERT="$("$APKSIGNER" verify --print-certs "$OUT" | sed -n 's/.*certificate SHA-256 digest: //p' | head -n1 | tr 'A-F' 'a-f')"
[ -n "$CERT" ] || die "could not read the signer certificate's SHA-256"
ok "signed: $OUT, certificate SHA-256 $CERT"

# the reference recipe carries the key F-Droid must find on our APKs
if [ -f "$RECIPE" ] && ! grep -q "^AllowedAPKSigningKeys: $CERT" "$RECIPE"; then
  sed -i "s/^AllowedAPKSigningKeys: .*/AllowedAPKSigningKeys: $CERT/" "$RECIPE"
  git add "$RECIPE"
  git commit -q -m "fdroid: the signing certificate of the published APKs is $CERT (AllowedAPKSigningKeys)"
  git push -q origin main
  ok "reference recipe: AllowedAPKSigningKeys set"
fi

git push -q origin "$TAG"
NOTES="fastlane/metadata/android/en-US/changelogs/$CODE.txt"
if gh release view "$TAG" >/dev/null 2>&1; then
  gh release upload --clobber "$TAG" "$OUT"
  ok "GitHub release $TAG: APK replaced"
else
  if [ -f "$NOTES" ]; then gh release create "$TAG" "$OUT" --title "Clavier Tuner $VERSION" --notes-file "$NOTES"
  else gh release create "$TAG" "$OUT" --title "Clavier Tuner $VERSION" --notes "Clavier Tuner $VERSION"; fi
  ok "GitHub release $TAG created with $OUT"
fi
printf 'unsigned sha256 %s\nsigner %s\n' "$SHA" "$CERT"
