# Cannuran Pulse — Claude Code handoff

Android app (Kotlin, no Compose) that puts a Claude-curated NQ/ES news brief on Darenn's
Galaxy S24 Ultra (One UI 8 / Android 16) lock screen. The app has three surfaces:
- a RemoteViews widget (lock screen via One UI or Good Lock LockStar, and the home screen)
- a persistent lock-screen notification card
- alerts

It calls the Anthropic Messages API with the `web_search_20250305` server tool directly from the phone, using
the user's own API key. No backend. See README.md for the full feature, cost and setup write-up.

## Status at handoff (built in a claude.ai cloud session, 2026-11-06)
- DONE: all source, resources, manifest, Gradle (AGP 8.11.1, Kotlin 2.2.0, Gradle 8.14.3 wrapper,
  compileSdk/targetSdk 36, minSdk 26), GitHub Actions workflow, README, sideload keystore.
- VERIFIED: 46 JUnit tests over `core/` pass (run with a local JUnit shim, `tools/run-core-tests.sh`).
  The Android layer type-checks against hand-written API stubs (`tools/check-android-compile.sh`).
- NOT YET VERIFIED: a real Gradle build against the Android SDK. The cloud sandbox couldn't reach
  maven.google.com / dl.google.com. **The first job here is to make `./gradlew assembleRelease
  testDebugUnitTest` pass for real.** Expect at most small API or signature fixes in `data/`, `work/`,
  `widget/`, `notify/` and `ui/`. `core/` is plain Kotlin and already tested.
- NOT YET DONE: pushing to GitHub. GitHub user is `Daren-Wallace`. Plan: a new **private** repo
  `cannuran-pulse`, then push `main`. The workflow builds, tests and publishes `CannuranPulse.apk` as a
  Release with a stable `releases/latest/download/CannuranPulse.apk` link to install from the phone.

## Next steps, in order
1. Build locally if an Android SDK is present (Android Studio / `~/.android` exists on this PC; set
   `sdk.dir` in `local.properties` or `ANDROID_HOME`). Otherwise, push and let CI build.
   Run `./gradlew testDebugUnitTest assembleRelease` and fix anything that fails.
2. `gh repo create Daren-Wallace/cannuran-pulse --private --source . --push`
   (the repo is already `git init`ed with one commit on `main`).
3. Watch the workflow (`gh run watch`) until it's green. Confirm the Release has `CannuranPulse.apk`.
4. Darenn installs it on the phone, then:
   - pastes his API key and enables web search in the Claude Console
   - sets battery to Unrestricted and allows precise alarms
   - adds the widget via One UI lock screen or Good Lock → LockStar
5. Later, nice-to-haves:
   - verify on-device that One UI 8 lists the widget natively (category `keyguard`)
   - check whether the Android 16 Live Update promotion shows in the Now Bar
   - tune the prompt from real briefs

## Architecture (where things live)
- `app/src/main/java/capital/cannuran/pulse/core/`: platform-free and unit-tested. This is where all logic
  goes.
  - `RefreshEngine`: one cycle of calendar → quotes → `Cadence.decide()` → Claude brief. Handles
    `pause_turn` and error mapping.
  - `Prompt.kt`: the system prompt plus per-refresh context: time in UTC/NY/Nairobi, session,
    calendar ±36h, and the headlines already shown.
  - `BriefParser`: tolerant JSON extraction, dedupe, NEW-marking via `Novelty`, citation URL fallback.
  - `Calendar.kt`: parses the ForexFactory weekly feed; CCH catalyst arming; `MarketClock` CME
    sessions and trading-day bounds.
  - `Cadence`: modes EVENT / ACTIVE / OFF_HOURS / WEEKEND. `Settings` + `Cost`: defaults, clamping,
    and the cost meter.
- `data/`: `Store` (SharedPreferences JSON), `SecretBox` (Keystore AES-GCM for the API key),
  `AndroidHttp`.
- `work/`: `RefreshWorker` (mutex-serialised), `Scheduler` (15-minute periodic plus expedited
  one-off with KEEP), `EventAlarms` (exact alarms at T−N and T+N), `BootReceiver`.
- `widget/`: `WidgetRenderer` (three size tiers via the `RemoteViews(Map<SizeF,…>)` constructor; live
  `Chronometer` countdown), `PulseWidgetProvider`.
- `notify/`: `Notifier` (card, alerts, reflective Android 16 `setRequestPromotedOngoing`),
  `ActionReceiver`.
- `ui/SettingsActivity`: plain `android.app.Activity` + XML; status, preview, key, schedule, focus,
  permission buttons.

## Conventions
- Keep logic in `core/` with tests in `app/src/test/.../core/`. Android classes stay thin.
- RemoteViews layouts may only use RemoteViews-safe views (no plain `<View>`; the divider is a
  TextView).
- Model IDs: `claude-sonnet-5-5` (default), `claude-haiku-5-5`, `claude-opus-5-5`.
  Prices are in `ClaudeModel`.
- Darenn's trading context (default `Settings.focus`):
  - NQ/ES on Tradeify Select prop accounts, ICT method, London + NY AM, Nairobi UTC+3.
  - NAS100 CFD hedge on ThinkMarkets (Cannuran Hedge Protocol).
  - The CCH activates on FOMC, CPI, NFP, PCE and Fed Chair, plus geopolitical shocks.
- Commit trailer used so far: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
