# Hisaab (हिसाब)

A personal ledger app for Android — separate accounts/pages for people, businesses,
and money pools, each with its own transactions and automatically calculated balance.

## What's implemented (MVP, per the spec's own priority list)

1. Create account (name + optional starting balance)
2. Starting/Initial Balance — stored as a special transaction, edited (not duplicated) via the "Initial Balance" stat on the account page
3. Add Received
4. Add Spent
5. Automatic balance calculation — `Current Balance = Initial Balance + Received - Spent`, always derived live from Room via `Flow`, never stored/cached
6. Edit transaction (type, amount, description, date, category)
7. Delete transaction, with confirmation dialog
8. Local database — Room + SQLite, survives app restarts and phone reboots
9. Individual account/Hisab pages
10. Share Hisab as text (Android share sheet — WhatsApp/Telegram/SMS/Gmail etc.)
11. Dark Material 3 UI (ink + gold palette matching the logo)
12. Hisaab / हिसाब branding — logo + adaptive launcher icon generated and wired in

Basic account search (by name) and transaction search-by-category exist at the
DAO/repository layer (`HisaabRepository.searchAccounts` / `.searchTransactions`);
the Home screen search box currently filters accounts. Money math uses integer
"paise" (`Long`) end-to-end — never `Float`/`Double` — so totals can't drift from
rounding errors, per the spec's requirement for decimal-safe amounts.

## Not yet built (the spec's own "add after MVP" list)

- Share as PDF / Share as Image (text share is wired up; these need `pdf` generation
  and a `Canvas`-drawn bitmap respectively — straightforward to add on top of the
  same `buildShareText`-style summary once you want them)
- JSON backup/export and restore
- Full-text search wired into the UI (categories are already filterable at the data layer)

None of this needs an architecture change — same Account/Transaction tables, same
repository — it's additive.

## Project structure

Standard Android Studio / Gradle layout:

```
Hisaab/
  app/src/main/java/com/palan/hisaab/
    data/            Room entities, DAOs, database, repository
    ui/               Compose screens (home, account, add/edit transaction, theme)
    viewmodel/        HomeViewModel, AccountViewModel
    util/             Money.kt — decimal-safe rupee formatting
  app/src/main/res/
    mipmap-*/         Generated हिसाब launcher icon (adaptive + legacy, all densities)
```

## Building it via GitHub Actions (no Android Studio needed)

A workflow is already included at `.github/workflows/build.yml`. GitHub-hosted
runners come with the Android SDK preinstalled, so this builds a real debug APK
in the cloud — no local Gradle wrapper or Android Studio required:

1. Create a new (private or public) repo on GitHub — e.g. under `meetpalan-dev`.
2. From inside the unzipped `Hisaab/` folder:
   ```
   git init
   git add .
   git commit -m "Initial Hisaab app"
   git branch -M main
   git remote add origin https://github.com/<your-username>/<repo-name>.git
   git push -u origin main
   ```
3. Open the repo's **Actions** tab — the "Build Hisaab APK" workflow runs automatically on push (or trigger it manually via "Run workflow").
4. When it finishes (green check), open the workflow run and download the `hisaab-debug-apk` artifact from the **Artifacts** section at the bottom of the run page. Unzip it to get `app-debug.apk`.
5. Transfer that APK to your phone (or a browser download link) and install it — you'll need to allow "install unknown apps" for whatever app you use to open it, since it's not from the Play Store.

This is also the best way to catch any build errors, since I couldn't compile
the project myself in this environment — the Actions log will show the exact
error and line if anything needs a small fix.

## Building it in Android Studio (alternative)

This was written and organized here, but there's no Android SDK / emulator in this
environment to compile or run it — you'll need Android Studio (Hedgehog or newer) to
actually build and test it:

1. Unzip the project, open the `Hisaab/` folder in Android Studio.
2. Let it sync Gradle (it'll offer to generate the Gradle wrapper on first open —
   accept that, or point it at a local Gradle 8.7+ install).
3. Run on a device/emulator with **minSdk 26** (Android 8.0) or newer.

Because I couldn't compile it myself here, treat this as a strong first pass rather
than a guaranteed zero-error build — if Android Studio flags anything on sync (an
import, a Compose API that shifted between library versions), it should be a small
fix, not a redesign. Worth a build-and-click-through before you rely on it.

## The logo

`हिसाब` set in Noto Sans Devanagari Black, gold-on-ink. Files are under
`logo-assets/` in this delivery (master logo, light/dark variants, transparent
wordmark, Play Store 512px icon) in addition to being wired into the app's
launcher icon at every density.
