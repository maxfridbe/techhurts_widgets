#!/bin/bash

bump() {
  local f="$1/version.properties"
  [ -f "$f" ] || echo "versionCode=1" > "$f"
  local c=$(grep 'versionCode' "$f" | cut -d'=' -f2)
  local n=$((c + 1)); echo "versionCode=$n" > "$f"; echo $n
}

wwCode=$(bump app);     wwName="1.0.0.${wwCode}";     echo "WeatherWidget:  $wwName"
calcCode=$(bump calculator); calcName="1.0.0.${calcCode}"; echo "Calculator:     $calcName"
goesCode=$(bump goeseast);   goesName="1.0.0.${goesCode}"; echo "GOES East:      $goesName"
himCode=$(bump himawari8);   himName="1.0.0.${himCode}";   echo "Himawari-8:     $himName"

podman build -t android-widget-builder .

podman run --rm \
  -v "$(pwd):/app:Z" \
  -v gradle_cache:/home/gradle/.gradle \
  android-widget-builder ./gradlew assembleDebug \
    -PversionCode="$wwCode" \
    -PcalcVersionCode="$calcCode" \
    -PgoesVersionCode="$goesCode" \
    -PhimawariVersionCode="$himCode"

rm -rf output && mkdir output
mv app/build/outputs/apk/debug/app-debug.apk         "./output/com.techhurts.WeatherWidget.${wwName}.apk"
mv calculator/build/outputs/apk/debug/calculator-debug.apk "./output/com.techhurts.Calculator.${calcName}.apk"
mv goeseast/build/outputs/apk/debug/goeseast-debug.apk     "./output/com.techhurts.GOESEast.${goesName}.apk"
mv himawari8/build/outputs/apk/debug/himawari8-debug.apk   "./output/com.techhurts.Himawari8.${himName}.apk"

echo; echo "Output:"; ls -lh output/
