# Cannuran Pulse — Claude Code handoff

Android app (Kotlin, no Compose) that puts a Claude-curated NQ/ES news brief on Darenn's
Galaxy S24 Ultra (One UI 8 / Android 16) lock screen. The app has three surfaces:
- a RemoteViews widget (lock screen via One UI or Good Lock LockStar, and the home screen)
- a persistent lock-screen notification card
- alerts

It calls the Anthropic Messages API with the `web_search_20250305` server tool directly from the phone, using
the user's own API key. No backend. See README.md for the full feature, cost and setup write-up.

## Status (2026-11-06)
- Repo: github.com/Daren-Wallace/cannuran-pulse (`main`). CI is green on ubuntu-24.04 with the runner's
  preinstalled Android SDK: 52 unit tests pass, and a signed release APK is published on every push.
- Install link (always latest): https://github.com/Daren-Wallace/cannuran-pulse/releases/latest/download/CannuranPulse.apk
- Not yet verified on device:
  - One UI 8 native lock-screen listing (otherwise use Good Lock → LockStar)
  - Now Bar promotion of the Live Update
  - prompt quality on real briefs
- Security: the repo is PUBLIC and commits `signing/pulse-sideload.jks`. Either make the repo private,
  or move to repo secrets (`PULSE_KEYSTORE_BASE64` etc.; see README → Security).

## Next steps
1. Darenn installs the APK and adds his API key (web search must be enabled in the Console).
   He sets Battery → Unrestricted, allows precise alarms, and adds the widget.
2. Iterate from real briefs: prompt in `core/Prompt.kt`, parsing in `core/BriefParser.kt`, layout in
   `res/layout/widget_pulse.xml`. Run `./gradlew testDebugUnitTest` before every push.

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

## Design (v2 "modern", 2026-11-07)
- Widget: a 28dp glass card with a header (mark, PULSE, session pill, refresh), bias hero, 3-segment
  risk meter, CCH badge, a gold "next release" card with a live Chronometer countdown, quote tiles with
  sparkline bitmaps, and headline tiles (impact bar, Bull/Bear pill, NEW badge). The footer shows the
  model or error and freshness.
- Themes (`WidgetTheme`): MIDNIGHT, GLASS, DYNAMIC (Material You via `@android:color/system_*` in
  `drawable-v31` / `color-v31`, with an accent from `system_accent1_200`).
- App: header, live widget preview (`WidgetRenderer.views(..).apply()`), stat tiles, a one-issue
  banner, and cards with segmented controls and custom switches. Settings autosave (debounced); there
  is no Save button.
- Design reference: `docs/widget-preview.png`.

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
