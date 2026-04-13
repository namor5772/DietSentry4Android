# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

DietSentry4Android is an offline Android app for food/nutrition lookup, eaten-food logging, recipe-based foods, and daily weight tracking. Written in Kotlin with Jetpack Compose. Uses a bundled SQLite database (`app/src/main/assets/foods.db`) bootstrapped on first run; no network layer.

## Build commands

```bash
./gradlew assembleDebug   # Build debug APK
./gradlew test            # Run unit tests
```

Always run `./gradlew assembleDebug` after non-trivial code changes to catch compile errors.

## Architecture

**Single-activity Compose app** — `MainActivity.kt` (~5700 lines) contains all navigation routes, screen composables, dialogs, daily totals aggregation, CSV export, and markdown help rendering. There is no MVVM/MVI layer; screens call `DatabaseHelper` directly.

**Key files** (all under `app/src/main/java/au/dietsentry/myapplication/`):
- `MainActivity.kt` — NavHost with 10+ routes: foodSearch, eatenLog, editFood, copyFood, insertFood, addFoodByJson, addRecipe, copyRecipe, editRecipe, utilities
- `DatabaseHelper.kt` — Singleton SQLite helper with CRUD for Foods, Eaten, Recipe, Weight tables; schema migration guards; cursor-to-model mapping; database import/export via streams
- `Food.kt`, `EatenFood.kt`, `RecipeItem.kt`, `WeightEntry.kt` — Data model classes
- `FoodList.kt`, `RecipeList.kt` — LazyColumn composables for food/recipe display
- `NumberFormatUtils.kt` — Locale-aware decimal formatting
- `ToastUtils.kt` — Custom toast helper

## Food type conventions

These conventions are load-bearing throughout the codebase (search, display, recipe logic):
- **Solid food**: description does NOT end with ` mL` or ` mL#`; nutrients per 100 g
- **Liquid food**: description ends with ` mL` or ` mL#`; nutrients per 100 mL
- **Converted liquid-to-solid**: description contains `{density=...g/mL}`
- **Recipe food**: description ends with ` {recipe=<weight>g}`; nutrients per 100 g derived from ingredients

## Database rules

- **Do NOT change database schema, table names, or column names** unless explicitly asked.
- If a feature needs additional data, first propose using the existing schema or in-memory calculation.
- If a schema change is truly necessary, present a migration plan before editing code.

## Coding guidelines

- Follow existing patterns: reuse `Food`, `EatenFood`, etc. instead of creating duplicates.
- Match the style of existing Composables for look and feel.
- Do not introduce new third-party libraries without asking.
- Do not add new build tools or scripts without explicit instructions.
- Keep changes scoped to the requested task.
- When formatting nutritional info, align with `FoodSearchScreen` style — two-column label/value layouts with right-aligned numbers.

## UI preservation

- Existing features must keep working after changes. New options/flags should default to current behavior.
- `reference-MAUI/` (when present) is the MAUI reference app for UI/behavior parity — treat as read-only.

## Tech stack versions

- Gradle 9.2.1, AGP 9.0.0, Kotlin 2.3.10
- compileSdk/minSdk/targetSdk: 36, JVM target: 11
- Compose BOM 2026.01.01, Navigation Compose 2.9.7, Material 3
- commonmark 0.27.1 (markdown rendering with autolink, GFM tables, strikethrough, task lists)
