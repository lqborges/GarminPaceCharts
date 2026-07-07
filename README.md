# GarminPaceCharts

Private Android app for **Progression A** running pace charts and Garmin-based health assessment.

Implements [issue #1](https://github.com/lqborges/GarminPaceCharts/issues/1).

## Features (MVP)

- Import `progression_a_workouts.json` (script-compatible format)
- Offline dashboard, charts (4 weeks / 1 year / all time), workout list & detail
- Export workouts JSON for backup / Python script compatibility
- Optional Garmin token import + manual refresh
- Local rule-based health assessment from workouts + wellness metrics

## Stack

- Kotlin, Jetpack Compose, MVVM
- Room (workouts, refresh runs, health data)
- DataStore (preferences)
- Encrypted storage for Garmin tokens

## Build

Requirements: JDK 17, Android SDK 35.

```bash
export ANDROID_HOME=~/Android/Sdk   # adjust if needed
./gradlew assembleDebug
./gradlew test
```

APK: `app/build/outputs/apk/debug/garmin-pace-charts-v<version>-debug.apk`

### Release to phone (OneDrive)

On the owner's machine (requires `rclone` remote `onedrive:`):

```bash
scripts/garmin-pace-ops.sh doctor    # check JDK, SDK, rclone
scripts/garmin-pace-ops.sh release   # test → build → zip → upload to onedrive:apk/
```

Uploads `.apk`, APK `.zip`, `progression_a_workouts.zip`, and `garmin_tokens.zip` to `onedrive:apk/`.

```bash
scripts/garmin-pace-ops.sh upload-tokens   # tokens only
```

## First launch

1. Open the app → **Import workouts JSON** and pick your `progression_a_workouts.json`.
2. Continue to the dashboard and open **Charts**.
3. (Optional) **Settings → Import Garmin tokens** — pick `tokens.json`, or download
   `garmin_tokens.zip` from OneDrive (`onedrive:apk/`), unzip, and import `tokens.json`.
4. Use **Refresh** to fetch new Progression A activities when tokens are configured.

## Data compatibility

Export format matches the existing Python updater:

```json
{
  "date": "2022-02-16T07:45:17",
  "pace": 9.42,
  "activity_id": 8305053426,
  "name": "Sheffield - Progression A -"
}
```

Pace extraction rules mirror `update_progression_a_incremental.py`:

- Filter: activity name contains `progression a` (case-insensitive)
- Split priority: `INTERVAL_ACTIVE` → `RWD_RUN` → longest RUN/ACTIVE fallback
- Valid pace: `3.0 < pace < 9.5` min/km

## Privacy

- Tokens stored with Android Keystore / EncryptedSharedPreferences
- No analytics SDK
- Clear data removes DB, tokens, and cached assessments

## Disclaimer

Health assessment is based on Garmin wearable data and is not medical advice.
