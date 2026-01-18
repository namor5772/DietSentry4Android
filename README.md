# DietSentry4Android

## _To build this app clone this repo into an Android Studio project on your Windows PC_

## 1. What this app is for and how to use it

DietSentry is an offline food database, eaten-food tracker, and weight logger for Android. It ships with a bundled SQLite foods database and lets you search foods, view nutrition panels, log what you actually ate, and record daily weights with optional comments. Logged entries store a nutrition snapshot scaled to the amount you entered, so later edits to a food do not change historical logs.

How to use it in detail - :

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
  - In All + Daily totals, each date also shows "My weight (kg)" and comments from the Weight table (or NA if not recorded).
  - When Daily totals is off, tap a log entry to edit its amount or time, or delete it.
- Manage weights and exports on the Utilities screen:
  - Export db / Import db: copy the internal `foods.db` to or from the device Download folder.
  - Export csv: write `EatenDailyAll.csv` to Download with daily totals (All), including weight and comments columns.
  - Weight Table: add, edit, or delete one weight entry per date, with optional comments.
- Food types:
  - Solids use per-100g values; liquids use per-100mL values (marked by descriptions ending with " mL").
  - Recipes are encoded in the description and store per-100g values derived from ingredient totals.

## 2. Toolchain used to author this app

- Clone the repo (for example): `git clone <repo-url>`.
- Open the project in Android Studio on a Windows PC (with the Android SDK installed).
- This app was authored with Kotlin, Jetpack Compose, and Gradle in Android Studio.
- It was tested and targeted on a Samsung S23 Ultra phone.
- Development work was done using Codex in a WSL terminal, editing files directly in this repo.
- The repo includes `AGENTS.md`, which documents build/test commands, code conventions, and project rules that Codex follows during development.
- Useful commands from the project root:
  - Build debug APK: `./gradlew assembleDebug`
  - Run unit tests: `./gradlew test`

## 3. App structure

- `app/` is the main Android module.
- `app/src/main/java/au/dietsentry/myapplication/` contains the Kotlin sources:
  - `MainActivity.kt` is the single-activity Compose entry point and most UI logic.
    - `MainActivity.onCreate` sets the theme and `NavHost` routes.
    - Screen composables:
      - `FoodSearchScreen` shows the Foods Table search UI and action panel.
      - `EatenLogScreen` shows the Eaten Table list, filters, and daily totals.
      - `InsertFoodScreen`, `EditFoodScreen`, `CopyFoodScreen` handle food creation/editing flows.
      - `AddFoodByJsonScreen` parses JSON input into a new food entry.
      - `AddRecipeScreen`, `EditRecipeScreen`, `CopyRecipeScreen` manage recipe flows.
      - `UtilitiesScreen` handles export/import of the internal database, Eaten daily totals CSV export, and Weight table management.
    - List/detail helpers: `EatenLogItem`, `NutritionalInfo`, `DailyTotalsCard`, `RecipeSelectionPanel`, `FoodList`-style rows for the UI panels.
    - Dialogs and pickers: `SelectAmountDialog`, `EditEatenItemDialog`, `DeleteConfirmationDialog`,
      `DeleteEatenItemConfirmationDialog`, `RecipeAmountDialog`, `ConvertFoodDialog`, `TimePickerDialog`.
    - Help/markdown rendering: `HelpBottomSheet`, `MarkdownText`, and the `RenderMarkdown*` helpers
      convert embedded markdown help text into Compose UI.
    - Session/prefs helpers: description parsing (`isLiquidDescription`, `descriptionUnit`, etc.),
      recipe search state (`resolveRecipeSearchMode`, `loadRecipeSearchQuery`), and persistence
      helpers for export/import URIs.
  - `DatabaseHelper.kt` is the SQLite access layer and singleton.
    - Database bootstrap/import: `copyDatabaseFromAssets`, `replaceDatabaseFromStream`, `getInstance`.
    - Food CRUD: `readFoodsFromDatabase`, `searchFoods`, `insertFood`, `insertFoodReturningId`,
      `updateFood`, `deleteFood`, `getFoodById`.
    - Eaten log CRUD: `logEatenFood`, `updateEatenFood`, `readEatenFoods`, `deleteEatenFood`.
    - Weight table CRUD: `insertWeight`, `readWeights`, `updateWeight`, `deleteWeight`.
    - Recipe CRUD: `insertRecipeFromFood`, `readRecipes`, `readCopiedRecipes`, `updateRecipe`,
      `deleteRecipe`, plus copy/duplicate helpers for temporary recipe records.
    - Cursor mapping helpers: `createFoodFromCursor`, `createEatenFoodFromCursor`, `createRecipeFromCursor`.
  - Model/data classes:
    - `Food.kt` defines the food master record and all nutrient fields.
    - `EatenFood.kt` defines logged entries with timestamps, amounts, and nutrient snapshots.
    - `RecipeItem.kt` defines per-ingredient rows for recipe composition.
    - `WeightEntry.kt` defines weight records with date, value, and comments.
  - UI list components:
    - `FoodList.kt` renders a list of foods and nutrition rows (`FoodList`, `NutrientRow`).
    - `RecipeList.kt` renders recipe items and their key nutrients (`RecipeList`, `unitLabel`).
  - Formatting and UI utilities:
    - `NumberFormatUtils.kt` provides `formatNumber` and `formatAmount` for consistent numeric display.
    - `ToastUtils.kt` provides `showPlainToast` for a styled, consistent toast.
  - Theme definitions:
    - `ui/theme/Theme.kt` provides `DietSentry4AndroidTheme`.
    - `ui/theme/Color.kt` and `ui/theme/Type.kt` define the color palette and typography.
- `app/src/main/assets/foods.db` is the bundled SQLite database copied to internal storage on first run.
