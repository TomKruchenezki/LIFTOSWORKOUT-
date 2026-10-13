# CLAUDE.md - LiftOS project memory

Read this first in every new session. It keeps decisions and context between conversations.
Update it whenever a decision changes. The owner writes in Hebrew; UI strings and user-facing docs are Hebrew.

## What this is
LiftOS: a personal Android strength-training app (Kotlin, Jetpack Compose, Room). Offline-only, sideloaded
APK (no Play Store), target device Samsung Galaxy S26 Ultra. Hebrew + RTL, dark charcoal + lime theme.
Owner timezone Asia/Jerusalem. Product rules: every button must work, real data only, nothing invented.

## Modules
- `:domain` - pure Kotlin/JVM (no Android). Models, logic, export/backup/import, exercise-library parser,
  default plan. Fully unit-tested on the JVM (`./gradlew -Pliftos.domainOnly=true :domain:test`).
- `:app` - Android app: Room (`data/db`), repositories (`data/repo`), foreground service (`service`),
  Compose UI (`ui/*`), manual DI (`di/AppContainer`).
- Library content: `app/src/main/assets/library/*.json` (104 exercises, Hebrew+English). The domain tests read
  these same files. Built-ins are re-seeded when the content hash changes; custom exercises are never touched.

## Build and verify
- Stack (verified Oct 2026): AGP 9.4.0 (built-in Kotlin, no `kotlin-android` plugin), Gradle 9.6.0, Kotlin 2.4.21,
  KSP 2.3.12, Compose BOM 2026.09.00 (Material3 1.4.0), Room 2.8.5, Navigation 2.10.2, Lifecycle 2.11.0,
  DataStore 1.2.1, kotlinx-serialization 1.11.0, coroutines 1.11.0, reorderable 3.1.0. compileSdk 37 (required by
  these libraries), targetSdk 36, minSdk 26, JDK 17+ (CI uses 21).
- The cloud sandbox this was built in **cannot reach dl.google.com** (org network policy), so the Android SDK and
  Google Maven are unavailable there. The domain module builds locally; the app is built and tested by
  **GitHub Actions** (`.github/workflows/android.yml`) on every push. Read failures from the "FAILURE SUMMARY" step
  at the end of the job log. Locally with Android Studio everything builds normally.
- Maven Central rate-limits the sandbox IP (HTTP 429); just retry.
- Pitfall seen twice: never write `/*` inside a KDoc comment (Kotlin nests comments) - e.g. paths like `assets/x/*.json`.

## Key decisions (do not undo without a reason)
- **No DI framework**: hand-written `AppContainer`. Small app, faster builds, easy to test.
- **IDs are String UUIDs** everywhere (stable across backup/merge/import). Time = UTC epoch millis + stored local
  date string + zone id per record (DST-safe). Week starts Sunday; weekdays are ISO numbers (1=Mon..7=Sun).
- **History is snapshotted**: a session copies exercise names, targets and rest times at start. Editing/deleting a
  plan or exercise never rewrites history. session.planId/plannedWorkoutId are soft references (no FK).
- **Unilateral exercises**: one row per side (RIGHT/LEFT) sharing a set number. Rest starts after the LEFT row.
- **Volume** = weight x reps for completed, non-warm-up sets only. Duration sets (plank) have no volume.
- **Rest timer** = absolute timestamps stored on the session row (survives process death). The foreground
  `WorkoutService` + a backup `AlarmManager` alarm race to end it; `claimRestFinish` makes exactly one win.
- **User settings live in Room** (`app_settings`, so they are backed up); `DevicePrefs` (DataStore) holds only
  device flags (library hash, default-plan-seeded, last export time).
- **No INTERNET permission.** Video buttons open a YouTube *search* unless the user typed a direct link. Never
  invent video URLs. AI analysis = copy/share text only; never claim it was sent anywhere.
- **No destructive DB migration** (`fallbackToDestructiveMigration` is forbidden). Each schema change needs a Migration
  and the new `app/schemas/.../N.json` committed (DurabilityTest enforces this).
- **Backups**: JSON `liftos-backup` with explicit `schemaVersion`; validated before import; REPLACE needs explicit
  confirmation and writes an automatic safety copy to app-private `files/backups/`. MERGE never overwrites.
- Floot import adapter is best-effort (field names from the owner's description, never seen a real file).

## Conventions
- Hebrew UI strings are hardcoded in Compose for now (extraction to resources = Part 2). Enum labels live in
  `domain` (`labelHe`). Wrap numbers in `ltr()` (`ui/util/UiFormat.kt`) inside Hebrew sentences.
- Repositories expose Flows + suspend functions; ViewModels created via `appViewModel { ... }`.
- Tests: JVM tests in `domain/src/test`; Robolectric Room tests in `app/src/test` (`TestEnv` builds all repositories).

## Status
See `DEVELOPMENT_PLAN.md` (what is done / what is next) and `CHANGELOG.md`.
