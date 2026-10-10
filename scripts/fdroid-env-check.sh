#!/usr/bin/env bash
#
# fdroid-env-check.sh -- does this machine meet F-Droid's requirements for a
# reproducible build of Clavier Tuner, and does the project?
#
#   ./scripts/fdroid-env-check.sh            report
#   ./scripts/fdroid-env-check.sh --prove    report, then build HEAD twice in two
#                                            independent worktrees and compare the
#                                            unsigned APKs byte for byte
#
# Every check names the requirement it stands on. The requirements were read on
# 10 Oct 2026 from
#   R  https://f-droid.org/docs/Reproducible_Builds/
#   M  https://f-droid.org/docs/Build_Metadata_Reference/
#   D  https://gitlab.com/fdroid/fdroiddata/-/issues/3330  (dependency info block)
# and are quoted beside each check. Nothing here changes the machine or the tree:
# it looks, and says what it found. Components are searched for under
# $SEARCH_ROOT (default /home/erweitert) and on PATH, nowhere else.
#
# What F-Droid does with a reproducible app (R): it builds the tagged commit in
# its own environment, copies the signature from the APK we published onto its
# own build and checks that it verifies -- so the unsigned bytes must be
# identical. For that the recipe carries Binaries: (where our APK is) and
# AllowedAPKSigningKeys: (our certificate's SHA-256) (M).
#
set -u
SEARCH_ROOT="${SEARCH_ROOT:-/home/erweitert}"
PROVE=0
for a in "$@"; do case "$a" in --prove) PROVE=1 ;; *) echo "unknown argument: $a" >&2; exit 2 ;; esac; done

B=$'\033[1m'; X=$'\033[0m'
OK=0; WARN=0; FAIL=0
ok()   { OK=$((OK+1));     printf '  %sok%s  %s\n' "$B" "$X" "$*"; }
warn() { WARN=$((WARN+1)); printf '  %s!!%s  %s\n' "$B" "$X" "$*"; }
fail() { FAIL=$((FAIL+1)); printf '  %sx%s   %s\n' "$B" "$X" "$*"; }
note() { printf '      %s\n' "$*"; }
hr()   { printf -- '----------------------------------------------------------------\n'; }
sect() { hr; printf '%s%s%s\n' "$B" "$*" "$X"; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT" || exit 2
GRADLE_FILE="androidApp/build.gradle.kts"
[ -f "$GRADLE_FILE" ] || { echo "not the foss checkout: $GRADLE_FILE missing" >&2; exit 2; }
APPID="$(sed -n 's/.*applicationId = "\(.*\)"/\1/p' "$GRADLE_FILE")"

hr
printf '%sClavier Tuner -- F-Droid reproducible-build environment check%s\n' "$B" "$X"
printf 'checkout : %s\n' "$ROOT"
printf 'app      : %s\n' "${APPID:-?}"
printf 'search   : %s and PATH\n' "$SEARCH_ROOT"
[ -d "$SEARCH_ROOT" ] || { echo "search root $SEARCH_ROOT does not exist" >&2; exit 2; }

# A bounded find: the search root is a home directory; .git, build and gradle
# caches are pruned, depth is limited, so this takes seconds, not minutes.
findq() { find "$SEARCH_ROOT" -maxdepth "$1" \( -name .git -o -name build -o -name .gradle -o -name node_modules -o -name .cache \) -prune -o "${@:2}" -print 2>/dev/null; }

# --- 1. the checkout --------------------------------------------------------------
sect "1. The checkout (R: build after tagging, from a clean tree at the tagged commit)"
if [ -z "$(git status --porcelain)" ]; then ok "working tree clean"; else warn "working tree is dirty -- a release build is made from a clean tree at a tag"; fi
BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null)"
[ "$BRANCH" = "main" ] && ok "on main" || warn "on $BRANCH, not main"
TAG_AT_HEAD="$(git tag --points-at HEAD 2>/dev/null | head -n1)"
[ -n "$TAG_AT_HEAD" ] && ok "HEAD is tagged $TAG_AT_HEAD" || note "HEAD is not tagged (fine for a check; the release builds a tag)"
if git ls-files | grep -qE '\.(so|a|dex|apk|aar|class)$'; then fail "prebuilt binaries in the tree ($(git ls-files | grep -cE '\.(so|a|dex|apk|aar|class)$') files) -- F-Droid's scanner refuses them"; else ok "no prebuilt binaries in the tree (gradle-wrapper.jar is allowed)"; fi
if git ls-files | grep -E '\.jar$' | grep -vq gradle-wrapper.jar; then warn "jar files in the tree besides the gradle wrapper"; fi

# --- 2. the project's build configuration ------------------------------------------
sect "2. androidApp/build.gradle.kts against the pitfalls F-Droid lists (R, D)"
VN="$(sed -n 's/.*versionName = "\(.*\)"/\1/p' "$GRADLE_FILE")"
VC="$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' "$GRADLE_FILE")"
note "versionName = \"$VN\", versionCode = $VC"
if printf '%s' "$VN" | grep -q '\$'; then
  warn "versionName is computed (\"$VN\"): the recipe's versionName must equal the APK's exactly (M: Builds.versionName); a git hash in it is reproducible per commit but every release block must carry the hash -- keep the hash out of release builds"
else ok "versionName is a plain string"; fi
if grep -q 'buildToolsVersion' "$GRADLE_FILE"; then ok "build-tools version pinned: $(sed -n 's/.*buildToolsVersion = "\(.*\)"/\1/p' "$GRADLE_FILE")   (R: pin the exact build-tools, changes alter XML and PNG output)"
else warn "build-tools version not pinned in $GRADLE_FILE   (R: \"build.gradle should pin the exact build-tools version\")"; fi
if grep -q 'includeInApk = false' "$GRADLE_FILE" && grep -q 'includeInBundle = false' "$GRADLE_FILE"; then ok "dependency info block disabled (dependenciesInfo includeInApk/includeInBundle = false)   (D)"
else warn "dependenciesInfo { includeInApk = false; includeInBundle = false } missing   (D: F-Droid asks apps published via Binaries: to opt out of the Google-signed dependency block)"; fi
if grep -q 'ArtProfile' "$GRADLE_FILE"; then ok "ArtProfile tasks disabled   (R: baseline.prof/baseline.profm are non-deterministic)"
else warn "ArtProfile tasks not disabled   (R: baseline.profm is non-deterministic; Compose libraries ship baseline profiles, so the APK will carry one -- add tasks.whenTaskAdded { if (name.contains(\"ArtProfile\")) enabled = false })"; fi
if grep -q 'isShrinkResources = true' "$GRADLE_FILE"; then warn "resource shrinker on   (R: avoid it unless it decreases the APK size significantly; it can behave non-deterministically)"; else ok "resource shrinker off"; fi
if grep -q 'isMinifyEnabled = true' "$GRADLE_FILE"; then
  note "R8 on (isMinifyEnabled = true)   (R: some optimisations are non-deterministic, e.g. ServiceLoader lists; keep the affected classes)"
  PRO="androidApp/proguard-rules.pro"
  if [ -f "$PRO" ] && grep -q 'kotlinx.coroutines.CoroutineExceptionHandler' "$PRO" && grep -q 'kotlinx.coroutines.internal.MainDispatcherFactory' "$PRO"; then ok "proguard keeps for kotlinx.coroutines ServiceLoader classes present   (R's own example)"
  else warn "proguard-rules.pro does not keep kotlinx.coroutines.CoroutineExceptionHandler and kotlinx.coroutines.internal.MainDispatcherFactory   (R: the ServiceLoader example; the app uses kotlinx-coroutines)"; fi
fi
if find androidApp/src -name '*.png' 2>/dev/null | grep -q .; then warn "$(find androidApp/src -name '*.png' | wc -l) PNGs under androidApp/src   (R: PNG crunching is not deterministic -- optimise them yourself and set aaptOptions cruncherEnabled = false)"; else ok "no PNGs in the app's resources (the icon is vector XML)"; fi
if grep -q 'externalNativeBuild\|ndkVersion' "$GRADLE_FILE"; then warn "native build configured   (R: NDK version, build-id, stripping all matter)"; else ok "no native code: no NDK, no stripping, no build-id   (R's NDK section does not apply)"; fi
if grep -q 'vcsInfo' "$GRADLE_FILE"; then note "vcsInfo configured explicitly"; else ok "VCS info left default   (R: AGP 8.3+ bundles it; build from the tagged commit of a clean tree and it is the same every time)"; fi
WRAPPER_VER="$(sed -n 's#.*gradle-\([0-9][0-9.]*\)-.*#\1#p' gradle/wrapper/gradle-wrapper.properties)"
AGP_VER="$(sed -n 's/^agp = "\(.*\)"/\1/p' gradle/libs.versions.toml)"
note "gradle wrapper $WRAPPER_VER, AGP $AGP_VER, compileSdk $(sed -n 's/.*compileSdk = \([0-9]*\).*/\1/p' "$GRADLE_FILE")"
if [ "${AGP_VER%%.*}" -ge 8 ] 2>/dev/null; then ok "AGP $AGP_VER: ZIP timestamps zeroed, entry order fixed, DEX order fixed   (R: AGP 2.2.2 / 7.1 / 8.8)"; else warn "AGP $AGP_VER is older than the fixes R names"; fi

# --- 3. the JDK -----------------------------------------------------------------------
sect "3. JDK (R: the build server has 25 and 21 from Debian Trixie, 17 from Bookworm; use ONE JDK, remove unused ones)"
if command -v java >/dev/null; then
  JV="$(java -version 2>&1 | grep -m1 'version "')"
  JMAJ="$(printf '%s' "$JV" | sed -n 's/.*version "\([0-9]*\).*/\1/p')"
  case "$JMAJ" in 17|21|25) ok "java on PATH: $JV   (a version the build server has)" ;; "") warn "could not read the java version from: $JV" ;; *) fail "java on PATH is $JMAJ: not one the build server has (17, 21, 25)" ;; esac
  command -v javac >/dev/null && ok "javac on PATH: $(javac -version 2>&1 | grep -m1 javac)" || warn "javac not on PATH -- a JRE is not enough"
else fail "no java on PATH"; fi
if [ -n "${JAVA_HOME:-}" ]; then
  [ -x "$JAVA_HOME/bin/java" ] && ok "JAVA_HOME=$JAVA_HOME ($("$JAVA_HOME/bin/java" -version 2>&1 | grep -m1 'version "'))" || warn "JAVA_HOME=$JAVA_HOME has no bin/java"
else note "JAVA_HOME not set (gradle uses the java on PATH; fine if there is only one JDK)"; fi
# the same JDK under two names (a symlink) counts once
JDKS="$( { findq 5 -type f -path '*/bin/javac'; ls -d /usr/lib/jvm/*/bin/javac 2>/dev/null; } | while read -r j; do readlink -f "$j"; done | sed 's#/bin/javac##' | sort -u)"
NJ="$(printf '%s\n' "$JDKS" | grep -c . )"
if [ "$NJ" -le 1 ]; then ok "one JDK found: ${JDKS:-none}"; else warn "$NJ JDKs found -- gradle may mix them (R: \"use only one JDK\"):"; printf '%s\n' "$JDKS" | sed 's/^/        /'; fi
if [ -f gradle.properties ] && grep -q 'org.gradle.java.home' gradle.properties; then note "gradle.properties pins org.gradle.java.home: $(grep org.gradle.java.home gradle.properties)"; fi

# --- 4. the Android SDK --------------------------------------------------------------
sect "4. Android SDK (R: recent SDK; pinned build-tools; apksigner from build-tools 34 for signing)"
SDK=""
for cand in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$(sed -n 's#^sdk.dir=##p' local.properties 2>/dev/null | sed 's#\\:#:#g')"; do
  [ -n "$cand" ] && [ -d "$cand/platforms" ] && { SDK="$cand"; break; }
done
if [ -z "$SDK" ]; then
  SDK="$(findq 4 -type d -name platforms -path '*/platforms' | sed 's#/platforms$##' | while read -r d; do [ -d "$d/build-tools" ] && echo "$d"; done | head -n1)"
  [ -n "$SDK" ] && note "SDK not in ANDROID_HOME/ANDROID_SDK_ROOT/local.properties; found under $SEARCH_ROOT"
fi
if [ -z "$SDK" ]; then fail "no Android SDK found (ANDROID_HOME, ANDROID_SDK_ROOT, local.properties sdk.dir, or $SEARCH_ROOT)"; else
  ok "SDK at $SDK"
  [ -n "${ANDROID_HOME:-}" ] || note "ANDROID_HOME not set (F-Droid's build server sets it; gradle reads local.properties or ANDROID_HOME)"
  CSDK="$(sed -n 's/.*compileSdk = \([0-9]*\).*/\1/p' "$GRADLE_FILE")"
  [ -d "$SDK/platforms/android-$CSDK" ] && ok "platform android-$CSDK installed (compileSdk)" || fail "platform android-$CSDK (compileSdk) not installed under $SDK/platforms"
  BT="$(ls "$SDK/build-tools" 2>/dev/null | sort -V | tr '\n' ' ')"
  [ -n "$BT" ] && ok "build-tools installed: $BT" || fail "no build-tools under $SDK"
  PIN="$(sed -n 's/.*buildToolsVersion = "\(.*\)"/\1/p' "$GRADLE_FILE")"
  if [ -n "$PIN" ]; then [ -d "$SDK/build-tools/$PIN" ] && ok "the pinned build-tools $PIN are installed" || fail "the pinned build-tools $PIN are not installed"; fi
  SIGNER34="$(ls -d "$SDK"/build-tools/34.* 2>/dev/null | sort -V | tail -n1)"
  if [ -n "$SIGNER34" ] && [ -x "$SIGNER34/apksigner" ]; then ok "apksigner from build-tools $(basename "$SIGNER34") available for signing   (R: 35.0.0-rc1 and later produce APKs apksigcopier cannot verify; use 34)"
  else warn "no build-tools 34.x: sign with apksigner 34   (R: build-tools >= 35.0.0-rc1 produce APKs that fail verification with apksigcopier)"; fi
  LATEST="$(ls "$SDK/build-tools" 2>/dev/null | sort -V | tail -n1)"
  [ -n "$LATEST" ] && [ "${LATEST%%.*}" -ge 35 ] 2>/dev/null && note "build-tools $LATEST present as well: fine for building, not for the signing step"
  [ -f "$SDK/licenses/android-sdk-license" ] && ok "SDK licences accepted" || warn "SDK licences not accepted ($SDK/licenses/android-sdk-license missing) -- gradle cannot install missing components"
  ls "$SDK/cmdline-tools"/*/bin/sdkmanager >/dev/null 2>&1 && ok "sdkmanager present" || note "no cmdline-tools sdkmanager (not needed if the components are installed)"
fi

# --- 5. gradle -----------------------------------------------------------------------
sect "5. Gradle (R: build with ./gradlew assembleRelease, never through Android Studio; F-Droid downloads the wrapper's version from its own verified list)"
[ -x gradlew ] && ok "gradlew is executable" || fail "gradlew missing or not executable"
ok "wrapper asks for gradle $WRAPPER_VER"
LIST_URL="https://gitlab.com/fdroid/fdroidserver/-/raw/master/gradlew-fdroid"
if command -v curl >/dev/null && LIST="$(curl -fsSL --max-time 20 "$LIST_URL" 2>/dev/null)"; then
  if printf '%s' "$LIST" | grep -q "$WRAPPER_VER"; then ok "gradle $WRAPPER_VER is in F-Droid's gradlew-fdroid list (fetched now)"
  else fail "gradle $WRAPPER_VER is NOT in F-Droid's gradlew-fdroid list -- F-Droid cannot build until it is added (or the wrapper is moved to a listed version); newest listed: $(printf '%s' "$LIST" | grep -oE "'[0-9]+\.[0-9]+(\.[0-9]+)?'" | tr -d "'" | sort -V | tail -n1)"; fi
else warn "could not fetch $LIST_URL to check gradle $WRAPPER_VER against F-Droid's list"; fi
GHOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
[ -d "$GHOME" ] && note "gradle user home: $GHOME" || note "no gradle user home yet"

# --- 6. tools ------------------------------------------------------------------------
sect "6. Tools for the release and for checking F-Droid's build against ours (R: apksigcopier, diffoscope)"
for t in git gh python3 curl; do command -v "$t" >/dev/null && ok "$t: $(command -v "$t")" || fail "$t not on PATH"; done
if command -v gh >/dev/null; then gh auth status >/dev/null 2>&1 && ok "gh authenticated" || warn "gh not authenticated (the GitHub release needs it)"; fi
python3 -c 'import yaml' 2>/dev/null && ok "python3 yaml module (for the recipe)" || warn "python3 yaml module missing (pip install pyyaml / dnf install python3-pyyaml)"
for t in apksigcopier diffoscope zipalign; do
  if command -v "$t" >/dev/null; then ok "$t: $(command -v "$t")"
  else
    F="$( { findq 6 -type f -name "$t"; [ -n "$SDK" ] && ls "$SDK"/build-tools/*/"$t" 2>/dev/null; } | head -n1)"
    [ -n "$F" ] && ok "$t found: $F (not on PATH)" || warn "$t not found   (R: apksigcopier verifies the signature copy, diffoscope names the differing file)"
  fi
done
if command -v fdroid >/dev/null; then ok "fdroidserver: $(fdroid --version 2>/dev/null | head -n1)"
else
  F="$(findq 6 -type f -name fdroid -path '*/bin/*' | head -n1)"
  [ -n "$F" ] && ok "fdroidserver found: $F (not on PATH; a venv?)" || warn "fdroidserver (fdroid) not found -- needed for 'fdroid build' / 'fdroid signatures' locally; optional if F-Droid's CI does the verifying"
fi
if command -v podman >/dev/null || command -v docker >/dev/null; then ok "container runtime: $(command -v podman || command -v docker) (can run F-Droid's buildserver image for a local verification build)"; else note "no podman/docker: a local run of F-Droid's own build environment is not possible; the fork's CI can do it"; fi

# --- 7. fdroiddata and signing --------------------------------------------------------
sect "7. fdroiddata clone and signing key (M: Binaries:, AllowedAPKSigningKeys:)"
FDD="$(findq 3 -type d -name fdroiddata | head -n1)"
if [ -n "$FDD" ] && [ -d "$FDD/.git" ]; then
  ok "fdroiddata clone: $FDD"
  (cd "$FDD" && git remote -v | grep -q 'gitlab.com/fdroid/fdroiddata') && ok "  upstream remote points at gitlab.com/fdroid/fdroiddata" || warn "  no remote for gitlab.com/fdroid/fdroiddata"
  (cd "$FDD" && git remote -v | grep -v 'fdroid/fdroiddata' | grep -q gitlab.com) && ok "  a fork remote on gitlab.com: $(cd "$FDD" && git remote -v | grep -v 'fdroid/fdroiddata' | grep gitlab.com | head -n1 | awk '{print $2}')" || warn "  no fork remote on gitlab.com (the recipe is submitted from a fork's branch)"
  [ -f "$FDD/metadata/$APPID.yml" ] && note "  metadata/$APPID.yml exists in the clone" || note "  metadata/$APPID.yml not in the clone yet (first release: seeded from fdroid/$APPID.yml in this repository)"
else warn "no fdroiddata clone under $SEARCH_ROOT (git clone https://gitlab.com/fdroid/fdroiddata.git beside this repository)"; fi
[ -f "fdroid/$APPID.yml" ] && ok "reference recipe in this repository: fdroid/$APPID.yml" || warn "no reference recipe fdroid/$APPID.yml in this repository"
KS="androidApp/keystore.properties"
if [ -f "$KS" ]; then
  ok "$KS present (signing config)"
  git ls-files --error-unmatch "$KS" >/dev/null 2>&1 && fail "  $KS is tracked by git -- a signing secret in the tree" || ok "  not tracked by git"
  STORE="$(sed -n 's/^storeFile=//p' "$KS")"
  [ -n "$STORE" ] && { [ -f "$STORE" ] || [ -f "androidApp/$STORE" ]; } && ok "  keystore file found: $STORE" || warn "  storeFile in $KS not found: ${STORE:-unset}"
else
  KEYS="$(findq 5 -type f \( -name '*.jks' -o -name '*.keystore' \) | head -n3)"
  [ -n "$KEYS" ] && warn "no $KS; keystores under $SEARCH_ROOT: $(printf '%s' "$KEYS" | tr '\n' ' ')" || warn "no $KS and no keystore under $SEARCH_ROOT -- the published APK must be signed with apksigner by our key (R)"
fi
SIGNED="$(ls androidApp/build/outputs/apk/release/*.apk 2>/dev/null | head -n1)"
if [ -n "$SIGNED" ] && [ -n "${SIGNER34:-}" ]; then
  FP="$("$SIGNER34/apksigner" verify --print-certs "$SIGNED" 2>/dev/null | sed -n 's/.*SHA-256 digest: //p' | head -n1)"
  [ -n "$FP" ] && note "signer certificate SHA-256 of $SIGNED (AllowedAPKSigningKeys): $FP" || note "$SIGNED is not signed (unsigned build output)"
fi

# --- 8. the proof -------------------------------------------------------------------
if [ "$PROVE" -eq 1 ]; then
  sect "8. The proof: HEAD built twice in two independent worktrees must give byte-identical unsigned APKs"
  if [ -z "$SDK" ] || ! command -v java >/dev/null; then fail "cannot build: SDK or java missing"; else
    REF="$(git rev-parse HEAD)"
    T="$(mktemp -d "${TMPDIR:-/tmp}/ct-prove.XXXXXX")"
    SHAS=""
    for i in a b; do
      W="$T/$i"
      git worktree add -q --detach "$W" "$REF" || { fail "worktree $i"; break; }
      printf '  building %s ... ' "$i"
      ( cd "$W" && { [ -f "$ROOT/local.properties" ] && cp "$ROOT/local.properties" .; } && ANDROID_HOME="$SDK" ./gradlew -q --no-daemon :androidApp:assembleRelease >"$T/build-$i.log" 2>&1 ) && printf 'done\n' || { printf 'FAILED (see %s)\n' "$T/build-$i.log"; fail "build $i failed"; }
      APK="$(ls "$W"/androidApp/build/outputs/apk/release/*unsigned*.apk 2>/dev/null | head -n1)"
      [ -z "$APK" ] && APK="$(ls "$W"/androidApp/build/outputs/apk/release/*.apk 2>/dev/null | head -n1)"
      if [ -n "$APK" ]; then S="$(sha256sum "$APK" | cut -d' ' -f1)"; note "$i: $S  $(basename "$APK")"; SHAS="$SHAS $S"; fi
    done
    set -- $SHAS
    if [ $# -eq 2 ] && [ "$1" = "$2" ]; then ok "REPRODUCIBLE: both builds of $(git rev-parse --short "$REF") gave the same unsigned APK ($1)"
    elif [ $# -eq 2 ]; then fail "NOT reproducible: the two builds differ -- diffoscope $T/a/... $T/b/... names the file (R: diffoscope)"; note "worktrees kept under $T for diffoscope; remove with: git worktree remove --force $T/a; git worktree remove --force $T/b"
    else fail "the proof did not complete"; fi
    if [ $# -eq 2 ] && [ "$1" = "$2" ]; then git worktree remove --force "$T/a" 2>/dev/null; git worktree remove --force "$T/b" 2>/dev/null; rm -rf "$T"; fi
  fi
fi

hr
printf '%s%d ok, %d to look at, %d failing%s\n' "$B" "$OK" "$WARN" "$FAIL" "$X"
hr
[ "$FAIL" -eq 0 ]
