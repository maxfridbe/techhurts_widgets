#!/bin/bash

# --- Versioning ---
VERSION_PROPERTIES_FILE="app/version.properties"
if [ ! -f "$VERSION_PROPERTIES_FILE" ]; then
    echo "versionCode=1" > "$VERSION_PROPERTIES_FILE"
fi

# Read current version code, increment it, and write it back
versionCode=$(grep 'versionCode' "$VERSION_PROPERTIES_FILE" | cut -d'=' -f2)
newVersionCode=$((versionCode + 1))
echo "versionCode=$newVersionCode" > "$VERSION_PROPERTIES_FILE"
versionName="1.0.0.${newVersionCode}"
echo "Building version: $versionName"


# --- Build ---
podman build -t android-widget-builder .

podman run --rm \
  -v "$(pwd):/app:Z" \
  -v gradle_cache:/home/gradle/.gradle \
  android-widget-builder ./gradlew assembleDebug -PversionCode="$newVersionCode"

# --- Output ---
rm -rf output && mkdir output
mv app/build/outputs/apk/debug/app-debug.apk "./output/com.techhurts.WeatherWidget.${versionName}.apk"
