# Hisaab (हिसाब)

A personal ledger app for Android — separate accounts/pages for people, businesses,
and money pools, each with its own transactions and automatically calculated balance.

## What's implemented

Core MVP: create account, initial balance, add Received/Spent, automatic decimal-safe
balance, edit/delete transaction, local Room/SQLite persistence, individual account
pages, dark Material 3 UI, हिसाब branding as the launcher icon.

Plus this round's additions:

- **Loan tracking** — two new transaction types, `Loan given` (you paid, they owe
  you — adds to balance) and `Loan taken` (you received, you owe them — subtracts
  from balance), kept separate from plain Received/Spent so the app stops
  conflating "money spent for good" with "money you expect back." The balance card
  shows a second row (They owe you / You owe) whenever an account has any loans.
- **Import Hisab** — paste the exact text produced by "Share as Text" (from this
  app) and it reconstructs the account and every transaction, including loan type
  and date, via the overflow menu (⋮) on Home → "Import Hisab."
- **Settings screen** (gear icon on Home):
  - **Material You** toggle — matches app colors to your wallpaper on Android 12+;
    off by default, falls back to the fixed ink/gold theme; the switch is disabled
    with an explanatory subtitle on older Android versions.
  - **Auto-fill today's date** toggle — when off, saving a transaction without
    picking a date stores it with no date (shown as "No date" in the list) instead
    of silently defaulting to today.
- **Material 3 date picker** — replaced the native `DatePickerDialog` (which was
  rendering as a plain light-themed system dialog, clashing with the dark app UI)
  with Compose's own `DatePicker`, themed consistently with the rest of the app.

## Not yet built

Share as PDF / Image, full backup/restore (JSON export), and a UI for searching
transactions (currently accounts-only in the Home search box; the repository layer
already supports transaction search).

## Building it via GitHub Actions (no Android Studio needed)

A workflow is already included at `.github/workflows/build.yml`. GitHub-hosted
runners come with the Android SDK preinstalled, so this builds a real debug APK
in the cloud:

1. Push this project to a GitHub repo (e.g. under `meetpalan-dev`).
2. Open the repo's **Actions** tab — it runs on push, or trigger manually.
3. Download the `hisaab-debug-apk` artifact from the finished run and install
   `app-debug.apk` on your phone (allow "install unknown apps" for whichever app
   opens it).

If the build fails, the Actions log will show the exact file/line — that's also
the fastest way to catch anything I couldn't verify myself, since I don't have an
Android SDK in this environment to compile it directly.

## Building it in Android Studio (alternative)

Open the `Hisaab/` folder in Android Studio (Hedgehog+), let it sync Gradle
(it'll offer to generate the wrapper), and run on minSdk 26 (Android 8.0)+.

## The logo

`हिसाब` set in Noto Sans Devanagari Black, gold-on-ink. Standalone files are under
`logo-assets/` (master logo, light/dark variants, transparent wordmark, Play Store
512px icon) in addition to being wired into the launcher icon at every density.
