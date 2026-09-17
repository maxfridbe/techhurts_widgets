# TechHurts Android Widgets

A containerized Android build system producing signed APKs for five home-screen widgets under the `com.techhurts` package namespace. GitHub Actions builds every push to `main` and publishes the APKs as a release.

---

## Widgets

| Module | Package | Widget | Size |
|--------|---------|--------|------|
| `:app` | `com.techhurts.weatherwidget` | TechHurts Weather | Resizable |
| `:calculator` | `com.techhurts.calculator` | TechHurts Calc | 1×1 fixed |
| `:goeseast` | `com.techhurts.goeseast` | GOES East | 4×4 min |
| `:hisense_remote` | `com.techhurts.hisense_remote` | Hisense Remote | Resizable |

### Weather Widget
Displays current conditions and temperature from the National Weather Service (weather.gov). Tap the widget to open a full 7-day forecast screen with copy-to-clipboard. Tap the refresh icon to force an update. Configure your location by zip code on first use.

### GOES East Widget
Fetches the latest NOAA GOES-19 East GEOCOLOR full-disk satellite image every 10 minutes, crops it to focus on North America (matching the clip offsets from the companion C# tool at `~/Dev/goeseast`), and displays it in a 4×4 home-screen widget. The last 24 hours of fetched frames (~144 images) are stored in app-private storage. Tapping the widget opens a full-screen animation that plays all 24 hours of imagery in a 20-second loop (each frame shown for `20 000 ms ÷ frameCount`). Images older than 24 h are automatically pruned.

Fetching is driven by `AlarmManager` (repeating, wakeup, 10 min interval). The alarm is rescheduled automatically on device boot via `RECEIVE_BOOT_COMPLETED`. Battery optimization exemption is requested on first launch so fetching is not deferred by Doze mode.

### Calculator Widget
A 1×1 home-screen widget that shows the calculator icon until a calculation is performed, then displays the last result auto-sized to fill the cell. Tap to open the full calculator. **CE** clears the widget back to the icon; **Close** saves the last result in place.

### AndroidTVRemoteControl Widget
A resizable home-screen widget styled as a TV remote controller with support for local Wi-Fi remote control protocols. It can be configured with a custom IP address and supports four protocol modes:
- **Android TV / Google TV (PIN Pairing)**: The official Android TV Remote Protocol v2 (ports 6466/6467) using mutual TLS credentials. First-time connection triggers a pairing challenge and prompts the user to enter the PIN code displayed on the TV.
- **Hisense Roku TV (ECP)**: Simple, zero-pairing HTTP connection on port 8060.
- **IP Control ASCII**: Text-based TCP commands on port 8088 (common on B2B/Prosumer series).
- **IP Control HEX**: Binary serial command packets on port 5000 (common on commercial screens).

Tapping buttons on the widget sends background HTTP/TCP/Protobuf control commands asynchronously (e.g. Power, Input, Volume, Mute, D-pad navigation, Back, Home). Tapping the title opens the configuration activity to edit the target IP/protocol, dynamically scan the network using mDNS (for Android/Google TVs) and SSDP (for Roku TVs), and pair new devices.

---

## Prerequisites

- **Podman** or **Docker** (podman is used when installed; `CONTAINER_ENGINE=docker` forces docker)
- `notifyf` helper script on PATH (wraps Telegram bot upload — optional, only needed to push APKs to a device)

No Android SDK or JDK installation required on the host — the build runs entirely inside the container defined by `Dockerfile`.

---

## Building

```bash
bash make.sh
```

`make.sh` does the following in order:

1. **Computes the build version** `YY.MMDD.###` (see [Versioning](#versioning)).
2. **Picks the signing key** (see [Signing](#signing)).
3. **Builds the container image** `android-widget-builder` from `Dockerfile` (cached after first run).
4. **Runs one `gradlew assembleDebug`** inside the container (`BUILD_TYPE=release` for `assembleRelease`), building every module with the same version.
5. **Copies the signed APKs** to `output/`:

```
output/
  com.techhurts.WeatherWidget.YY.MMDD.###.apk
  com.techhurts.Calculator.YY.MMDD.###.apk
  com.techhurts.GOESEast.YY.MMDD.###.apk
  com.techhurts.Himawari8.YY.MMDD.###.apk
  com.techhurts.AndroidTVRemoteControl.YY.MMDD.###.apk
```

### Sending to a device

```bash
notifyf output/com.techhurts.Calculator.YY.MMDD.###.apk
notifyf output/com.techhurts.WeatherWidget.YY.MMDD.###.apk
```

---

## Project structure

```
├── app/                        # Weather widget module
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/techhurts/weatherwidget/
│       │   ├── WeatherWidgetProvider.java   # AppWidgetProvider + refresh
│       │   ├── WeatherService.java          # NWS API calls
│       │   ├── WidgetConfigureActivity.java # Zip-code setup + battery opt prompt
│       │   ├── ForecastActivity.java        # 7-day forecast screen
│       │   └── WidgetLogger.java
│       └── res/
│
├── goeseast/                   # GOES East satellite widget module
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/techhurts/goeseast/
│       │   ├── GoesEastWidgetProvider.java    # AppWidgetProvider
│       │   ├── GoesEastFetchReceiver.java     # AlarmManager-driven fetch + BOOT handler
│       │   ├── GoesEastAnimationActivity.java # 24h animation player
│       │   ├── GoesEastService.java           # NOAA page scrape + crop logic
│       │   └── ImageStore.java               # 24h rolling frame storage
│       └── res/
│
├── calculator/                 # Calculator widget module
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/techhurts/calculator/
│       │   ├── CalculatorWidgetProvider.java  # AppWidgetProvider, persists result
│       │   └── CalculatorActivity.java        # Full calculator UI
│       └── res/
│
├── hisense_remote/             # Hisense WiFi Remote widget module
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/techhurts/hisense_remote/
│       │   ├── HisenseRemoteWidgetProvider.java  # AppWidgetProvider & clicks
│       │   ├── WidgetConfigureActivity.java      # Configuration & latency test
│       │   └── RemoteCommandExecutor.java        # HTTP/TCP protocol logic
│       └── res/
│
├── .github/workflows/build.yml # CI: build, verify signatures, publish release
├── Dockerfile                  # Android SDK build image
├── make.sh                     # Single command to build all targets
├── version.properties          # Daily build counter (YY.MMDD.###)
├── settings.gradle             # Multi-module Gradle root (TechHurtsWidgets)
├── build.gradle                # Root build file; shared signing config
└── llm.md                      # Notes for AI coding agents
```

---

## Adding a new widget module

1. Create a new directory (e.g., `clock/`) mirroring the `calculator/` structure.
2. Add `include ':clock'` to `settings.gradle`.
3. In its `build.gradle`, read `versionCode`/`versionName` from the `appVersionCode`/`appVersionName` properties exactly like the other modules.
4. Add a `module:ApkName` pair to the collect loop in `make.sh`.

---

## Versioning

Every build of every app gets one version: **`YY.MMDD.###`**, where `###` is the build number within that day (e.g. the third build on 2026-09-17 is `26.0917.003`).

- Locally, `make.sh` keeps the counter in the committed root `version.properties` (`versionDate` / `versionBuild`), resetting to `001` on the first build of a new day (max 999 per day).
- `versionName` = `YY.MMDD.###`; `versionCode` = the same digits packed as the integer `YYMMDD###` (`260917003`), so it strictly increases and `adb install -r` upgrades always work.
- Both are passed to Gradle as `-PappVersionCode` / `-PappVersionName`; no module hardcodes a version.
- In CI, `###` is the workflow run number (mod 1000) and `version.properties` is left alone.

---

## Signing

Every module signs debug and release builds with one key, configured once in the root `build.gradle` from properties that `make.sh` passes in. **Keys are never committed** (`*.keystore`, `*.jks` and `keystore.properties` are gitignored). `make.sh` looks for a key in this order:

1. `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` environment variables (used by CI).
2. `~/.config/vibe_widgets/release.jks` + `keystore.properties` (`storePassword=`, `keyPassword=`, `keyAlias=`; override the directory with `KEYSTORE_DIR`).
3. `./debug.keystore` with the standard Android debug passwords (the legacy local key).

With none of these, AGP signs with a throwaway debug key that won't upgrade already-installed apps.

### Creating the release key

```bash
mkdir -p ~/.config/vibe_widgets
podman run --rm -it --entrypoint keytool -v ~/.config/vibe_widgets:/ks:Z android-widget-builder \
  -genkeypair -keystore /ks/release.jks -alias vibewidgets \
  -keyalg RSA -keysize 4096 -validity 10950 -dname "CN=vibe_widgets"
printf 'storePassword=%s\nkeyPassword=%s\nkeyAlias=vibewidgets\n' '<password>' '<password>' \
  > ~/.config/vibe_widgets/keystore.properties
```

Back up `release.jks` and its password: without them no future build can upgrade the installed apps. Switching keys requires uninstalling each app once.

### GitHub secrets

Under **Settings → Secrets and variables → Actions**, add:

| Secret | Value |
|--------|-------|
| `ANDROID_KEYSTORE_B64` | output of `base64 -w0 ~/.config/vibe_widgets/release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | store password |
| `ANDROID_KEY_ALIAS` | `vibewidgets` |
| `ANDROID_KEY_PASSWORD` | key password (optional if same as the store password) |

---

## CI

`.github/workflows/build.yml` runs on pushes to `main`, tags, pull requests and manual dispatch:

1. Builds the builder image with a GitHub Actions layer cache.
2. With the signing secrets set, runs `make.sh` with `BUILD_TYPE=release`; without them, builds debug APKs with a throwaway key and warns.
3. Verifies every APK signature with `apksigner` and uploads `output/*.apk` as the `apks` artifact.
4. For signed builds, publishes a GitHub Release: every push to `main` replaces the rolling **latest** pre-release, and every pushed tag gets a permanent release.
