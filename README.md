# DietSentry4Android

## 1. What this app is for and how to use it

DietSentry is an offline food database and eaten-food tracker for Android. It ships with a bundled SQLite foods database and lets you search foods, view nutrition panels, and log what you actually ate. Logged entries store a nutrition snapshot scaled to the amount you entered, so later edits to a food do not change historical logs.

How to use it in detail:

- Browse and filter foods on the Foods Table screen:
  - Use the search field to filter by description. You can type `text1|text2` to require both terms (case-insensitive).
  - Use the segmented button to switch detail level: Min (description only), NIP (mandatory nutrition panel fields), or All (full nutrient set).
- Select a food to open the action panel and choose one of the actions:
  - Log: enter amount (g or mL) and date/time, then confirm to create a log entry.
  - Edit: update a food. Solid, liquid, and recipe foods open tailored edit screens.
  - Add: create a new food from scratch.
  - Json: add a food from JSON text (for non-recipe foods).
  - Copy: duplicate a food as a new entry.
  - Convert: convert a liquid food to a solid by providing density (g/mL).
  - Delete: remove a food from your local database.
  - Utilities: export or import the internal foods database to or from the device Download folder (`foods.db`).
- Review and manage logged entries on the Eaten Table screen:
  - Use the segmented button (Min, NIP, All) to control how much nutrition detail is shown.
  - Toggle Daily totals to consolidate entries by day and sum nutrients.
  - Toggle Filter by Date to show only a specific day.
  - When Daily totals is off, tap a log entry to edit its amount or time, or delete it.
- Food types:
  - Solids use per-100g values; liquids use per-100mL values (marked by descriptions ending with " mL").
  - Recipes are encoded in the description and store per-100g values derived from ingredient totals.

## 2. Toolchain used to author this app

- Clone the repo (for example): `git clone <repo-url>`.
- Open the project in Android Studio on a Windows PC (with the Android SDK installed).
- This app was authored with Kotlin, Jetpack Compose, and Gradle in Android Studio.
- Development work was done using Codex in a WSL terminal, editing files directly in this repo.
- The repo includes `AGENTS.md`, which documents build/test commands, code conventions, and project rules that Codex follows during development.
- Useful commands from the project root:
  - Build debug APK: `./gradlew assembleDebug`
  - Run unit tests: `./gradlew test`

## 3. App structure

- `app/` is the main Android module.
- `app/src/main/java/au/dietsentry/myapplication/` contains the Kotlin sources, including:
  - `MainActivity.kt` with Compose navigation and screen composables (Foods Table, Eaten Table, add/edit flows).
  - Core models like `Food`, `FoodList`, and `EatenFood`.
  - `DatabaseHelper` for SQLite persistence and CRUD operations.
- `app/src/main/assets/foods.db` is the bundled SQLite database copied to internal storage on first run.
