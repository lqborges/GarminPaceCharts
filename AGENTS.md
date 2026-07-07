# GarminPaceCharts — Agent Context

Private Android app for **Progression A** running pace charts and Garmin-based health
assessment. Primary user: Lucas (`lqborges@gmail.com`). Personal/local-first — no multi-user
backend, no web client. Offline viewing after import matters.

**Stack:** Kotlin 2.0, Gradle 8.9, AGP 8.7, Jetpack Compose, Room, DataStore, OkHttp.
Single `:app` module. JDK **17** (full JDK — `jlink` required for AGP; JRE-only fails the build).
Android SDK 35, `minSdk` 26.

Spec / roadmap: [issue #1](https://github.com/lqborges/GarminPaceCharts/issues/1).

## Build, test, run

All commands run from the **repo root** (`GarminPaceCharts/`).

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64   # adjust on your machine
export ANDROID_HOME=~/Android/Sdk                     # or ~/android-sdk on cloud VMs

# Canonical local gate before opening a PR
./gradlew test assembleDebug

# Unit tests only
./gradlew test

# Debug APK → app/build/outputs/apk/debug/garmin-pace-charts-v<version>-debug.apk
# Also copied to artifacts/ by scripts/garmin-pace-ops.sh release
./gradlew assembleDebug

# Build + upload APK + zip to onedrive:apk/ (owner's machine, rclone required)
scripts/garmin-pace-ops.sh release

# Run one test class
./gradlew test --tests "com.lqborges.garminpacecharts.PaceExtractorTest"

# Clean rebuild when Kotlin daemon / cache acts up
./gradlew --stop && ./gradlew clean test assembleDebug
```

**No emulator in CI / most cloud VMs.** Only JVM unit tests and APK assembly run headless.
On-device verification (import 267-workout JSON, chart tabs, airplane mode) is manual on a phone.

## Module layout

```
GarminPaceCharts/
  app/
    src/main/java/com/lqborges/garminpacecharts/
      data/
        local/          Room (AppDatabase, entities, DAOs, PreferencesManager, mappers)
        garmin/         GarminApiClient, GarminTokenStore (encrypted)
        repository/     WorkoutRepository, RefreshRepository, HealthRepository
      domain/           PaceExtractor, ImportExport, ChartDataBuilder, HealthAssessmentEngine
      domain/model/     Workout, ChartData, HealthAssessment, etc.
      ui/               Compose screens, PaceChart, AppViewModel, navigation, theme
      AppContainer.kt   manual DI wiring
      MainActivity.kt   single-activity + NavHost
    src/test/           JVM unit tests (pace, import/export, chart bucketing)
    src/test/resources/ fixtures (e.g. progression_a_sample.json)
  .github/workflows/android.yml
  scripts/garmin-pace-ops.sh   # doctor | release (build + zip + rclone → onedrive:apk/)
  artifacts/                   # gitignored APK + zip output from release
```

## Architecture (the 30-second version)

**Data flow:** Compose UI → `AppViewModel` → repositories → Room / Garmin API / DataStore.

**MVP path (offline-first):** Import `progression_a_workouts.json` → persist workouts in Room
→ build chart datasets in memory via `ChartDataBuilder` → render with `PaceChart` Compose canvas.

**Garmin refresh (online):** Encrypted token import → `GarminApiClient` fetches activities by
date → `PaceExtractor` filters Progression A + extracts split pace → dedupe by `activity_id` →
upsert Room → optional wellness metrics → `HealthAssessmentEngine` regenerates assessment.
Failed refresh must **not** delete or corrupt existing local data.

**Health assessment:** Rule-based, local only (no LLM). Mirrors concepts from the Hermes
`garmin-data-analysis` skill — not medical advice. Missing metrics are reported explicitly.

## Key files (load-bearing)

| File | Role |
|------|------|
| `domain/PaceExtractor.kt` | Activity filter + split selection + pace validation (must match Python script) |
| `domain/ImportExport.kt` | Script-compatible JSON import/export |
| `domain/ChartDataBuilder.kt` | ISO-week grouping, chart ranges, month colors |
| `domain/HealthAssessmentEngine.kt` | Local assessment + recommendations |
| `data/repository/WorkoutRepository.kt` | Workout CRUD, import/export, dashboard stats |
| `data/repository/RefreshRepository.kt` | Garmin fetch orchestration + refresh summary |
| `data/garmin/GarminTokenStore.kt` | Encrypted token storage |
| `data/garmin/GarminApiClient.kt` | Unofficial Garmin Connect API client |
| `ui/AppViewModel.kt` | Screen state coordinator |
| `ui/components/PaceChart.kt` | Interactive pace chart (zoom/pan) |

**Canonical Python reference** (behavior must stay aligned):

- `/home/lqborges/garmin_pace_charts/update_progression_a_incremental.py`
- Persistent store shape: `progression_a_workouts.json` (`date`, `pace`, `activity_id`, `name`)

**Pace rules (do not drift):**

- Activity filter: name contains `progression a` (case-insensitive; configurable in Settings later)
- Split priority: `INTERVAL_ACTIVE` → `RWD_RUN` → longest split with RUN/ACTIVE in type
- Pace: `distanceKm = splitDistanceMeters / 1000`, `durationMin = splitDurationSeconds / 60`,
  `pace = durationMin / distanceKm`
- Valid range: `3.0 < pace < 9.5` min/km
- Dedup: by Garmin `activity_id`; fallback `date + name + pace` when id missing

## Rules (do not violate)

1. **Android only.** Do not add a web app, backend service, or KMP shared module unless the
   owner explicitly asks. Reuse logic in `domain/` — don't fork a second implementation in Python.
2. **Don't commit APKs.** `artifacts/*.apk` and `artifacts/*.zip` are gitignored. Distribution is
   via OneDrive (`rclone copyto` to `onedrive:apk/`). Use `scripts/garmin-pace-ops.sh release`
   — it runs tests, builds, zips APK + `progression_a_workouts.json`, and uploads all three to
   `onedrive:apk/` (`.apk`, APK `.zip`, and `progression_a_workouts.zip`).
3. **Auto-upload after release builds on the owner's machine.** After every intentional
   `assembleDebug` meant for phone install, run `scripts/garmin-pace-ops.sh release` (or upload
   manually) — don't hand-roll rclone unless debugging upload itself.
4. **Never echo/print/log secrets:** Garmin tokens, auth headers, `tokens.json` contents,
   `GarminTokenStore` values. Diagnostics export must redact auth material.
5. **Preserve import/export compatibility** with `progression_a_workouts.json` and the Python
   updater. Export field names stay `date`, `pace`, `activity_id`, `name`.
6. **Failed Garmin refresh leaves local data unchanged.** Show an actionable error; keep charts
   and the last assessment available offline.
7. **Don't touch unrelated uncommitted files.** Only stage what you changed.
8. **PR workflow:** branch off `origin/main`, commit, push, `gh pr create`. Don't merge unless
   the owner explicitly asks.
9. **Scope MVP increments sensibly.** Garmin auth is high-risk — local import + offline charts
   should keep working even when refresh is broken. Don't block charting on network.
10. **Health assessment is fitness awareness, not diagnosis.** Keep the disclaimer in
    `HealthAssessmentEngine.DISCLAIMER` visible in the UI.
11. **Never edit `versionCode`/`versionName` manually — Android versions derive from git.**
    `versionCode` = commit count, `versionName` = `VERSION_PREFIX.<count>` (`app/build.gradle.kts`).
    A release is "build from current main" — no bump PR. Only change `versionPrefix` for a milestone
    (e.g. 0.1 → 0.2) via a normal PR.
12. **Room migrations:** schema changes need a deliberate migration strategy — `fallbackToDestructiveMigration`
    is acceptable for this personal MVP only while data is re-importable from JSON; prefer proper
    migrations once the owner relies on on-device-only history.

## Garmin / token gotchas

- **Token format:** `~/.garminconnect/tokens.json` uses `di_token`, `di_refresh_token`,
  `di_client_id`. Import via Settings; stored in EncryptedSharedPreferences.
- **Unofficial API:** Garmin Connect is not a stable public API. Auth/session behavior can break.
  Treat native refresh as best-effort; import + export remain the reliable path.
- **Token refresh:** `GarminApiClient` currently uses bearer token calls; full OAuth refresh parity
  with the Python `garminconnect` library may need future work. Document limitations rather than
  silently failing.
- **Activity details:** List API may lack `splitSummaries`; refresh falls back to per-activity fetch.
- **Cleartext:** Garmin API is HTTPS only — no cleartext exceptions needed.

## Testing expectations

**Unit tests (required for domain changes):**

- Activity name filter, pace validation, split selection
- Import parser, deduplication, export shape
- ISO-week chart bucketing, trend calculation

**Manual verification (before calling a milestone done):**

- Import the real 267-workout `progression_a_workouts.json` (count + date range match)
- All three chart tabs render
- App works in airplane mode after import
- Export JSON round-trips through the Python script if changed

Fixtures live in `app/src/test/resources/`. Add sanitized Garmin-like JSON for new split paths.

## GitHub Actions CI

`.github/workflows/android.yml` on `push` / `pull_request` to `main`:

- `./gradlew test`
- `./gradlew assembleDebug`

No Playwright, no backend, no OWASP gate yet. Keep CI fast.

### Agent PR policy

When **you** open a PR:

1. Run `./gradlew test assembleDebug` locally first (with JDK 17).
2. Push and open the PR; summarize what was verified manually vs unit-tested.
3. **If CI fails — fix it.** Don't hand off a red PR.
4. **Never merge** unless the owner explicitly asks.
5. Prefer focused PRs tied to issue milestones (import/charts → Garmin refresh → health → polish).

Docs-only commits can use `[skip ci]` if a skip workflow is added later; today CI is cheap.

## Milestones (from issue #1)

| Milestone | Scope | Acceptance hint |
|-----------|--------|-----------------|
| 0 | Project setup | App builds and launches |
| 1 | Import + offline charts | 267-workout import, 3 chart tabs, export, airplane mode |
| 2 | Garmin refresh | Token import, manual refresh, dedupe, failed refresh safe |
| 3 | Health assessment | Offline assessment from stored metrics + workouts |
| 4 | Polish | Fullscreen chart, share image, diagnostics, background refresh |

Do not let Milestone 2 block shipping Milestone 1.

## User preferences

- Communicates briefly — read intent generously
- Prefers autonomous action on build/fix tasks; draws the line at merging PRs
- Tests by using the actual phone app, not only unit test output
- Wants parity with the existing Python pace-chart workflow and Hermes health assessment *concepts*

## Cursor Cloud / WSL notes

- **JDK 17 required.** If `compileDebugJavaWithJavac` fails with missing `jlink`, install
  `openjdk-17-jdk-headless` (JRE is not enough).
- **Android SDK:** `~/Android/Sdk` on the owner's WSL machine; set `ANDROID_HOME` accordingly.
- **Gradle 8.9** — AGP 8.7 rejects Gradle 8.7 (see `gradle/wrapper/gradle-wrapper.properties`).
- **No device/emulator** in cloud VMs — APK build + JVM tests only.
- **Garmin tokens** for live refresh testing exist only on the owner's machine
  (`~/.garminconnect/tokens.json`) — use fixtures in tests; never commit tokens.
- **APK upload** (`scripts/garmin-pace-ops.sh release`, rclone → OneDrive) runs on the owner's
  machine only — cloud VMs don't have `onedrive:` configured.

## Docs map

| Doc | What |
|-----|------|
| `README.md` | User-facing build + first-launch steps |
| `AGENTS.md` | This file — agent context |
| GitHub issue #1 | Full product spec, data model, screens |
| `~/garmin_pace_charts/update_progression_a_incremental.py` | Canonical pace extraction + chart behavior |
| `~/.hermes/.../garmin-data-analysis/SKILL.md` | Health assessment concepts (reference only) |
