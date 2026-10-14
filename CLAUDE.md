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
- **CI workflow facts (Part 2):** a push cancels the run in progress (`cancel-in-progress`), so push in batches and wait for the result
  (~6 min). Read results with `mcp__github__get_job_logs` (`return_content=true`, `tail_lines` 40-70); the artifacts (APK) cannot be downloaded
  from the sandbox (blob host 403). The "Failure summary" step is last and now ends with `TEST TOTALS` lines.
- Other pitfalls seen: inside `AppContainer` the property `app` shadows the package `app.liftos` (import classes instead of writing fully qualified
  names); always use named arguments for `MeasurementInput`; the extended FAB label is only in the *unmerged* semantics tree
  (`onNodeWithText(..., useUnmergedTree = true)`); Robolectric cannot run `PdfDocument` (keep paging logic in `:domain` `ReportBlocks`);
  a text field inside an `AlertDialog` never reaches Compose idle in Robolectric (test the rule as a plain unit test instead);
  Room's schema JSON is a KSP side effect, so the KSP tasks declare `app/schemas` as an output (a build-cache hit otherwise leaves it missing).

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

### Part 2 decisions (LiftOS 0.2.0)
- **Analytics are derived, never stored.** Everything is computed from flat `SetFact`/`SessionFact` lists by pure `:domain` code
  (`analytics`, `progression`, `calendar`, `body`, `goals`, `recovery`, `insights`, `export`, `reminders`). `AppContainer.analyticsData` is shared and
  only active while a screen looks at it. A unilateral exercise (right+left rows) counts as ONE set. A record's first value is a baseline, not a PR;
  ties are not PRs. 1RM is Epley and always labelled an estimate.
- **Progression suggestions are never applied automatically.** The user can apply, change manually, or ignore (stored in `progression_decisions`).
  Thin data = "not enough data", never a made-up number. Recovery/insights are rule based and say so; no medical diagnosis (only a Deload hint).
- **Calendar:** planned workouts come from the active plan's weekdays; changing ONE occurrence (move/skip/missed) is a row in `planned_overrides` -
  plan and history are never touched. Repeating events are one row with a rule + exceptions (`Recurrence.kt`). Auto "missed" only for the last 28 days.
- **AI export = copy / share only.** `TextReport.AI_INSTRUCTION` (verbatim from the owner) + `PeriodReport`. Never write "sent"; write "copied". Body data is opt-in per export.
  `domain/ai/AiProvider` is an unused extension point (no INTERNET permission; a future provider needs per-provider consent and a Keystore key).
- **Photos** live only in `files/photos` (JPEG, EXIF stripped); camera via system `TakePicture` + FileProvider, gallery via `PickVisualMedia` (no permissions).
- **Backups schema 2:** JSON or ZIP (`backup.json` + `photos/<file>`). `includesPhotos` decides whether REPLACE touches photos; old files go to a safety folder
  only after the DB transaction succeeded. Preview shows new vs existing per category. File names with `/`, `\`, `..` are rejected.
- **Reminders** use `AlarmManager.setAndAllowWhileIdle` (NOT WorkManager: not time-of-day accurate), one alarm per enabled type for the next occurrence only;
  re-created at app start, on settings change and after boot. `ReminderPlanner` is pure and tested (DST).
- **Honesty rule for reports:** say exactly what was verified. Nothing has been run on a real device; Floot import is unverified against a real file.

## Conventions
- Hebrew UI strings are hardcoded in Compose for now (extraction to resources is still open). Enum labels live in
  `domain` (`labelHe`). Wrap numbers in `ltr()` (`ui/util/UiFormat.kt`) inside Hebrew sentences.
- Repositories expose Flows + suspend functions; ViewModels created via `appViewModel { ... }`.
- Tests: JVM tests in `domain/src/test`; Robolectric Room tests in `app/src/test` (`TestEnv` builds all repositories).

## Status
Part 1 (0.1.0) and Part 2 (0.2.0) are implemented and green in CI. Not done / not verified: real-device testing, Floot import against a real file,
R8 release runtime, camera/gallery/PDF rendering/notification delivery on a device. See `FEATURES.md` (honest per-feature table), `TESTING.md`,
`DEVELOPMENT_PLAN.md` (what is next) and `CHANGELOG.md`.
