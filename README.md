# DietSentry4Android

DietSentry is an offline Android app for food/nutrition lookup, eaten-food logging, recipe-based foods, and daily weight tracking.
great
This repository is developed in Kotlin + Jetpack Compose. The app uses a bundled SQLite `foods.db` on first run, then reads/writes the internal app database.

## 1. What the app does

- Foods Table:
  - Search foods by description (`text1|text2` means both terms must match).
  - Switch detail view with `Min`, `NIP`, `All`.
  - Open per-food actions: `Log`, `Edit`, `Add`, `Json`, `Copy`, `Convert`, `Delete`, `Utilities`.
- Eaten Table:
  - View eaten logs in `Min`, `NIP`, `All`.
  - Toggle `Daily totals` to aggregate by date.
  - Toggle `Filter by date` and choose a date from the date picker.
  - Edit/delete individual log entries when daily totals are off.
- Utilities:
  - `Export db`: export internal `foods.db`.
  - `Import db`: replace the internal database from external `foods.db`.
  - `Export csv`: export daily totals as `EatenDailyAll.csv`.
  - `Weight Table`: add/edit/delete dated weight entries with optional comments.

## 2. Recent behavior updates reflected in this README

- Exchange folder flow for import/export now uses Android SAF folder picking:
  - On first use, pick a folder (initial location starts in Downloads).
  - The folder URI is remembered and reused for export db/import db/export csv.
  - Each export/import dialog has a `Change folder` action.
- Daily totals now carry clearer amount units:
  - `g`, `mL`, or `mixed units` when a day includes both solid and liquid foods.
- Weight integration in daily totals and CSV:
  - `All` daily totals include `My weight (kg)` plus optional comments from Weight records for matching dates.
  - CSV export includes these columns too.
- Recipe workflows:
  - Recipe foods are stored in Foods with a ` {recipe=<weight>g}` marker.
  - Only gram-based foods can be added as recipe ingredients.
  - Recipe screens include `Set notes` (auto-generate from ingredients) and `Edit notes`.
  - Food search query in recipe screens is remembered during the current app session.
- Notes support:
  - Food `notes` are supported in add/edit/copy flows and JSON import, and shown in Foods Table when `All` is selected.
- In-app help:
  - Main screens provide a `?` help button with markdown-rendered guidance.

## 3. Food type rules

- Solid food:
  - Description does not end with ` mL` or ` mL#`.
  - Nutrients are treated as per 100 g.
- Liquid food:
  - Description ends with ` mL` or ` mL#`.
  - Nutrients are treated as per 100 mL.
- Converted liquid to solid:
  - Uses density (g/mL), creates a new food, and adds `{density=...g/mL}` in description.
- Recipe food:
  - Description ends with ` {recipe=<weight>g}`.
  - Nutrients are stored per 100 g, derived from ingredient totals.

## 4. Build and run

- Open this project in Android Studio on Windows (or compatible environment) with Android SDK installed.
- Current module config in `app/build.gradle.kts`:
  - `compileSdk = 36`
  - `minSdk = 36`
  - `targetSdk = 36`
  - Java/Kotlin JVM target 11
- Common commands from repo root:
  - Build debug APK: `./gradlew assembleDebug`
  - Run unit tests: `./gradlew test`

## 5. Project structure

- Main module: `app/`
- Kotlin source root: `app/src/main/java/au/dietsentry/myapplication/`
  - `MainActivity.kt`:
    - Navigation routes and screen composables.
    - Foods/Eaten/Recipe/Utilities screen logic and dialogs.
    - Daily totals aggregation and CSV building.
    - Markdown help rendering and session preference helpers.
  - `DatabaseHelper.kt`:
    - SQLite bootstrap/import and singleton access.
    - CRUD for Foods, Eaten, Recipe, and Weight tables.
    - Search helpers and cursor-to-model mapping.
    - Runtime table/column guards for `Weight` and `Foods.notes`.
  - Models:
    - `Food.kt`, `EatenFood.kt`, `RecipeItem.kt`, `WeightEntry.kt`
  - UI helpers:
    - `FoodList.kt`, `RecipeList.kt`, `NumberFormatUtils.kt`, `ToastUtils.kt`
  - Theme:
    - `ui/theme/Theme.kt`, `ui/theme/Color.kt`, `ui/theme/Type.kt`
- Bundled DB asset: `app/src/main/assets/foods.db`
