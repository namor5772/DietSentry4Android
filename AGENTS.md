# AGENTS.md – DietSentry4Android

DietSentry4Android is an Android app written in Kotlin. It tracks foods and eaten items
with nutritional information.

## Build & test

- Build debug APK: `./gradlew assembleDebug`
- Run unit tests: `./gradlew test` (or more specific variants if needed)
- Do **not** introduce new build tools or scripts without explicit instructions.

When you make non-trivial code changes, prefer to run `./gradlew assembleDebug` at
the end of your task to catch compile errors.

## Project structure

- Main Android module: `app/`
- Kotlin sources: `app/src/main/java/au/dietsentry/myapplication/`
- Key concepts:
    - `Food` / `FoodList` – master list of foods and their nutrition data.
    - `EatenFood` / Eaten log screens – records of foods actually eaten.
    - `DatabaseHelper` – current persistence layer (SQLite helper).
- UI screens of interest:
    - `FoodSearchScreen` – shows foods and nutrition info.
    - `EatenLogScreen` (and related classes) – shows eaten items and, optionally,
      detailed nutrition per record.

When adding new screens or functions, follow the existing package structure and
naming conventions rather than inventing new top-level packages.

## Coding conventions

- Language: Kotlin.
- Target environment: modern Android (use current AndroidX libraries where appropriate).
- Follow existing patterns in this project:
    - Reuse existing models (`Food`, `EatenFood`, etc.) instead of creating duplicates.
    - Match the style of existing list adapters / Composables for look and feel.
- Prefer clear, readable code over clever one-liners.
- Keep changes scoped to the task that was requested.

## Database & persistence rules

- **Do NOT change database schema, table names, or column names** unless explicitly
  asked to do so.
- If you need additional data for a feature, first propose how to represent it
  using the existing schema or in-memory calculation.
- If a schema change is truly necessary, present a migration plan before editing code.

## UI & behaviour

- Preserve existing behaviour by default:
    - Features that work today should keep working after your changes.
    - When adding options or flags, default behaviour should match the current app.
- When formatting nutritional information:
    - Align with the style used in `FoodSearchScreen` for consistency.
    - Use two-column label/value layouts where appropriate, with numeric values
      right-aligned or otherwise visually lined up.

## Safety & scope

- Do not run `git` commands (commit, reset, checkout, etc.) yourself.
- Do not modify files outside this repository (no changes under `/mnt/c` paths
  other than this project).
- Do not introduce new third-party libraries without asking; prefer using the
  existing AndroidX / Kotlin stdlib stack.

When in doubt about architectural changes, ask for confirmation and present a
short plan before making large refactors.
