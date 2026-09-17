# LLM Notes

Guidance for AI coding agents working in this repo. `README.md` has the full feature and layout description.

## Building

- Build only with `bash make.sh`. Gradle runs inside the `android-widget-builder` container; never install or invoke an Android SDK, JDK or Gradle on the host.
- A successful build leaves 5 APKs in `output/`, named `com.techhurts.<App>.YY.MMDD.###.apk`.
- Never hardcode versions. `make.sh` bumps the daily counter in `version.properties` and injects `appVersionCode` / `appVersionName` into every module; each `build.gradle` just reads those properties.
- `build/`, `.gradle/` and `output/` are build products and are gitignored.
- Signing is configured once in the root `build.gradle`; never add `signingConfigs` to a module or commit a keystore. See README "Signing".
- CI (`.github/workflows/build.yml`) runs the same `make.sh`, so keep it working non-interactively under docker (`CONTAINER_ENGINE=docker SKIP_IMAGE_BUILD=1 VERSION_BUILD=n`).

## Modules

| Module | Package | Logcat tags |
|--------|---------|-------------|
| `app` | `com.techhurts.weatherwidget` | `WeatherWidget`, `WeatherService` |
| `calculator` | `com.techhurts.calculator` | none |
| `goeseast` | `com.techhurts.goeseast` | `GoesEast` |
| `himawari8` | `com.techhurts.himawari8` | `Himawari8` |
| `hisense_remote` | `com.techhurts.hisense_remote` | `KeyStoreManager` |

- `goeseast` and `himawari8` are near-copies (`CropConfig`, `CropPreviewView`, `GifEncoder`, `Mp4Encoder`, `ImageStore`); a fix in one usually belongs in the other.
- `hisense_remote/src/main/java/com/kunal52/` is a vendored Android TV Remote v2 protocol library. `Pairingmessage.java` and `Remotemessage.java` are protoc-generated — don't hand-edit them.

## Gotchas

- When renaming a package, search the XML as well as the Java. The `android:configure` attribute in `res/xml/*_widget_provider_info.xml` (`app`, `hisense_remote`) holds a fully qualified activity name; if it's wrong the configuration activity never launches and the widget fails to add.

## Debugging

```bash
adb logcat -s WeatherWidget WeatherService GoesEast Himawari8   # live logs, filter by tag
./debug.sh                                                      # last 30 WeatherWidget log lines
```
