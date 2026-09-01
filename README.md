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

And this round:

- **Split Expense** — new flow off the Home FAB (tap + → "Split Expense"). Enter a
  total and description, pick a split mode (Evenly / Amount / Percent / Shares,
  same four modes as the Google Pay-style reference), and add participants —
  typing a name or phone number suggests existing Hisaab accounts so their share
  posts straight into their own hisab as a `Loan given` (you paid, they owe you
  their share). Typing a name that doesn't match anyone creates a fresh account
  for them. Finishing a split returns you to Home.
- **Phone numbers on accounts** — optional field on Create Account and in the new
  Edit Account dialog (⋮ on an account page), used for the split-participant
  autocomplete and for...
- **Send Hisab via SMS** — an SMS icon next to Share on the account page, enabled
  once that account has a phone number; opens the phone's SMS app pre-filled with
  the number and the same text as "Share as Text."
- **Edit / delete account** — the account page's ⋮ menu now has "Edit Account"
  (rename, edit phone) and, inside that dialog, "Delete this account" with a
  confirmation.
- Added a real Room migration (v1→v2) for the new `phoneNumber` column, so
  existing accounts/transactions on your phone survive the update instead of
  being wiped.
- **Split now includes your own share** — a "Me" participant is included by
  default (like "You" in the reference screenshots); your share posts as a plain
  `Spent` transaction into a persistent "Me" account (reused across splits, not
  recreated each time), using the split's description. Everyone else's share
  still posts as `Loan given` into their own account.
- **Mark a loan as Paid** — editing a Loan given/taken transaction now shows
  "Mark as paid" (or "Mark as unpaid" once settled). Settled loans stay fully
  visible in the transaction list — tagged "Paid" and dimmed — but drop out of
  the outstanding "They owe you / You owe" totals and the balance, since they're
  no longer outstanding. Added a second migration (v2→v3) for this.
- **Share as PDF / Image** — the Share icon on an account page is now a menu with
  Text/PDF/Image, all built from the same statement layout (balance, totals,
  transaction list with Paid loans dimmed).
- **JSON Backup / Restore** — Home's ⋮ menu now has "Export Backup" (writes every
  account and transaction to a `.json` file you choose via Android's file picker)
  and "Import Backup" (restores from one). Always creates new accounts on import
  rather than merging, so re-importing is safe.

## Not yet built

A UI for searching transactions (currently accounts-only in the Home search box;
the repository layer already supports transaction search).

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
