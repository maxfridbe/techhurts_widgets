podman build -t android-widget-builder .

podman run --rm \
  -v "$(pwd):/app:Z" \
  -v gradle_cache:/home/gradle/.gradle \
  android-widget-builder

rm -rf output && mkdir output
mv app/build/outputs/apk/debug/app-debug.apk ./output/HelloWorldWidget.apk

# or: docker run --rm -v "$(pwd):/app" -v gradle_cache:/home/gradle/.gradle --user $(id -u):$(id -g) android-widget-builder

#podman run --rm \
#  -v "$(pwd):/app:Z" \
#  android-widget-builder
