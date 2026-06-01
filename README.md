# TechHurts Android Widgets

A containerized Android widget build system producing signed debug APKs for two home-screen widgets, both under the `com.techhurts` package namespace.

---

## Widgets

| Module | Package | Widget | Size |
|--------|---------|--------|------|
| `:app` | `com.techhurts.weatherwidget` | TechHurts Weather | Resizable |
| `:calculator` | `com.techhurts.calculator` | TechHurts Calc | 1×1 fixed |
| `:goeseast` | `com.techhurts.goeseast` | GOES East | 4×4 min |

### Weather Widget
Displays current conditions and temperature from the National Weather Service (weather.gov). Tap the widget to open a full 7-day forecast screen with copy-to-clipboard. Tap the refresh icon to force an update. Configure your location by zip code on first use.

### GOES East Widget
Fetches the latest NOAA GOES-16 East GEOCOLOR full-disk satellite image every 10 minutes, crops it to focus on North America (matching the clip offsets from the companion C# tool at `~/Dev/goeseast`), and displays it in a 4×4 home-screen widget. The last 24 hours of fetched frames (~144 images) are stored in app-private storage. Tapping the widget opens a full-screen animation that plays all 24 hours of imagery in a 20-second loop (each frame shown for `20 000 ms ÷ frameCount`). Images older than 24 h are automatically pruned.

Fetching is driven by `AlarmManager` (repeating, wakeup, 10 min interval). The alarm is rescheduled automatically on device boot via `RECEIVE_BOOT_COMPLETED`. Battery optimization exemption is requested on first launch so fetching is not deferred by Doze mode.

### Calculator Widget
A 1×1 home-screen widget that shows the calculator icon until a calculation is performed, then displays the last result auto-sized to fill the cell. Tap to open the full calculator. **CE** clears the widget back to the icon; **Close** saves the last result in place.

---

## Prerequisites

- **Podman** (or swap `podman` for `docker` in `make.sh`)
- `notifyf` helper script on PATH (wraps Telegram bot upload — optional, only needed to push APKs to a device)

No Android SDK or JDK installation required on the host — the build runs entirely inside the container defined by `Dockerfile`.

---

## Building

```bash
bash make.sh
```

`make.sh` does the following in order:

1. **Reads and increments** each module's version counter:
   - `app/version.properties` → `versionCode` for `:app`
   - `calculator/version.properties` → `versionCode` for `:calculator`
2. **Builds the container image** `android-widget-builder` from `Dockerfile` (cached after first run).
3. **Runs one `gradlew assembleDebug`** inside the container, building both modules in parallel and passing each module its own `versionCode` via Gradle project properties.
4. **Moves the signed APKs** to `output/`:

```
output/
  com.techhurts.WeatherWidget.1.0.0.<N>.apk
  com.techhurts.Calculator.1.0.0.<N>.apk
```

### Sending to a device

```bash
notifyf output/com.techhurts.Calculator.1.0.0.<N>.apk
notifyf output/com.techhurts.WeatherWidget.1.0.0.<N>.apk
```

---

## Project structure

```
├── app/                        # Weather widget module
│   ├── build.gradle
│   ├── version.properties      # Auto-incremented build counter
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
│   ├── version.properties      # Auto-incremented build counter
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
│   ├── version.properties      # Auto-incremented build counter
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/techhurts/calculator/
│       │   ├── CalculatorWidgetProvider.java  # AppWidgetProvider, persists result
│       │   └── CalculatorActivity.java        # Full calculator UI
│       └── res/
│
├── Dockerfile                  # Android SDK build image
├── make.sh                     # Single command to build all targets
├── settings.gradle             # Multi-module Gradle root (TechHurtsWidgets)
├── build.gradle                # Root build file
└── debug.keystore              # Shared debug signing key
```

---

## Adding a new widget module

1. Create a new directory (e.g., `clock/`) mirroring the `calculator/` structure.
2. Add `include ':clock'` to `settings.gradle`.
3. Add version tracking and an output `mv` line to `make.sh`.
4. Pass the new version code via `-PclockVersionCode=...` in the `gradlew` invocation inside `make.sh`.

---

## Versioning

Each module tracks its own monotonically increasing `versionCode` in a `version.properties` file that is committed to source control. `versionName` follows `1.0.0.<versionCode>`. Both are set at build time via Gradle project properties — no manual editing required.
