#!/usr/bin/env bash
# Build every widget APK inside the builder container and collect them in output/.
#
# Environment overrides:
#   BUILD_TYPE=release          debug (default) or release
#   VERSION_BUILD=42            use this ### instead of bumping version.properties (CI)
#   CONTAINER_ENGINE=docker     force an engine instead of autodetecting
#   SKIP_IMAGE_BUILD=1          assume the builder image already exists (CI)
#   KEYSTORE_DIR=...            dir holding release.jks + keystore.properties
#                               (default: ~/.config/techhurts)
#   KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD
#                               pass the signing key directly (CI)
set -euo pipefail
cd "$(dirname "$0")"

IMAGE=android-widget-builder
BUILD_TYPE="${BUILD_TYPE:-debug}"

# --- Version: YY.MMDD.### -----------------------------------------------------
# ### is the build number within the day (001-999), tracked in version.properties.
# versionCode packs the same fields as the integer YYMMDD###, so it increases
# with every build.
today=$(date +%y%m%d)
if [ -n "${VERSION_BUILD:-}" ]; then
  build=$((10#$VERSION_BUILD))
else
  versionDate=""; versionBuild=0
  [ -f version.properties ] && . ./version.properties
  if [ "$versionDate" = "$today" ]; then build=$((10#$versionBuild + 1)); else build=1; fi
  if [ "$build" -gt 999 ]; then echo "error: more than 999 builds today" >&2; exit 1; fi
  printf 'versionDate=%s\nversionBuild=%03d\n' "$today" "$build" > version.properties
fi
VERSION_NAME=$(printf '%s.%s.%03d' "${today:0:2}" "${today:2:4}" "$build")
VERSION_CODE=$(printf '%s%03d' "$today" "$build")
echo "==> Version: $VERSION_NAME (code $VERSION_CODE, $BUILD_TYPE)"

# --- Container engine ----------------------------------------------------------
ENGINE="${CONTAINER_ENGINE:-$(command -v podman >/dev/null 2>&1 && echo podman || echo docker)}"
case "$ENGINE" in podman) ZFLAG=",Z" ;; *) ZFLAG="" ;; esac

# --- Signing key -----------------------------------------------------------------
# 1. KEYSTORE_FILE + passwords from the environment (CI)
# 2. $KEYSTORE_DIR/release.jks + keystore.properties (local release key)
# 3. ./debug.keystore with the standard debug passwords (legacy local key)
# Keys are never committed; see README "Signing".
prop() { grep -E "^$1=" "$2" | head -1 | cut -d= -f2-; }
KEYSTORE_DIR="${KEYSTORE_DIR:-$HOME/.config/techhurts}"
if [ -n "${KEYSTORE_FILE:-}" ]; then
  : "${KEYSTORE_PASSWORD:?}" "${KEY_ALIAS:?}"; KEY_PASSWORD="${KEY_PASSWORD:-$KEYSTORE_PASSWORD}"
elif [ -f "$KEYSTORE_DIR/release.jks" ] && [ -f "$KEYSTORE_DIR/keystore.properties" ]; then
  KEYSTORE_FILE="$KEYSTORE_DIR/release.jks"
  KEYSTORE_PASSWORD=$(prop storePassword "$KEYSTORE_DIR/keystore.properties")
  KEY_ALIAS=$(prop keyAlias "$KEYSTORE_DIR/keystore.properties")
  KEY_PASSWORD=$(prop keyPassword "$KEYSTORE_DIR/keystore.properties")
elif [ -f debug.keystore ]; then
  KEYSTORE_FILE="$(pwd)/debug.keystore"
  KEYSTORE_PASSWORD=android; KEY_ALIAS=androiddebugkey; KEY_PASSWORD=android
fi
SIGNING=()
if [ -n "${KEYSTORE_FILE:-}" ]; then
  echo "==> Signing with $KEYSTORE_FILE ($KEY_ALIAS)"
  SIGNING=(-v "$KEYSTORE_FILE:/keystore.jks:ro$ZFLAG"
           -e ORG_GRADLE_PROJECT_appKeystoreFile=/keystore.jks
           -e "ORG_GRADLE_PROJECT_appKeystorePassword=$KEYSTORE_PASSWORD"
           -e "ORG_GRADLE_PROJECT_appKeyAlias=$KEY_ALIAS"
           -e "ORG_GRADLE_PROJECT_appKeyPassword=$KEY_PASSWORD")
else
  echo "==> WARNING: no signing key found; APKs get a throwaway debug key and won't upgrade installed apps" >&2
fi

# --- Build -------------------------------------------------------------------------
[ "${SKIP_IMAGE_BUILD:-0}" = 1 ] || "$ENGINE" build -t "$IMAGE" .

task="assemble${BUILD_TYPE^}"
"$ENGINE" run --rm \
  -v "$(pwd):/app:rw$ZFLAG" \
  -v gradle_cache:/home/gradle/.gradle \
  "${SIGNING[@]}" \
  "$IMAGE" ./gradlew "$task" --no-daemon \
    -PappVersionCode="$VERSION_CODE" \
    -PappVersionName="$VERSION_NAME"

# --- Collect -----------------------------------------------------------------------
# cp, not mv: under docker the build dirs are root-owned.
rm -rf output && mkdir output
for pair in app:WeatherWidget calculator:Calculator goeseast:GOESEast himawari8:Himawari8 hisense_remote:AndroidTVRemoteControl timer:Timer; do
  module=${pair%%:*}; name=${pair##*:}
  cp "$module/build/outputs/apk/$BUILD_TYPE/$module-$BUILD_TYPE.apk" "output/com.techhurts.$name.$VERSION_NAME.apk"
done

echo; echo "Output:"; ls -lh output/
