# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

DietSentry4Android is a primarily-offline Android app for food/nutrition lookup, eaten-food logging, recipe-based foods, and daily weight tracking. Written in Kotlin with Jetpack Compose. Uses a bundled SQLite database (`app/src/main/assets/foods.db`) bootstrapped on first run.

A native **Windows C++ port** lives in `winport/` (Dear ImGui + D3D11 + SQLite + WinHTTP; build with `winport\build.bat`, see `winport/README.md`). It deliberately mirrors the Kotlin code structure — when changing app behaviour here, make the matching change in the corresponding `winport/src/screens_*.cpp` / `db.cpp` file, and vice versa. The two apps share the same `foods.db` schema and description-marker conventions; keep them compatible.

A native **macOS C++ port** lives in `macport/` (Dear ImGui + Metal/AppKit + SQLite + libcurl; build with `macport/build.sh`, see `macport/README.md`). It mirrors `winport/` file-for-file: most `src/` files are byte-identical copies, with platform-specific counterparts only for `main.mm`, `imageutil.mm`, `util.cpp`, `db.cpp`, `anthropic.cpp` (transport), and `screens_utilities.cpp` (folder picker). ImGui/SQLite/json vendor code is compiled from `winport/vendor` (single pinned copy), and app assets come from `winport/assets` at build time. Behaviour changes must be applied to all three: Android, `winport/`, `macport/`. One deliberate Android-only exception: the Utilities screen's `Export db as…` / `Import db from…` / `Share db…` buttons (SAF `CreateDocument`/`OpenDocument` single-file pickers plus an `ACTION_SEND` share via `FileProvider`), which exist because Android's folder-tree picker cannot reach cloud DocumentsProviders like OneDrive — and OneDrive's provider additionally refuses saves even in the file picker (opens work, creates don't; live-tested 2026-08-14), so the share sheet is the only route into it. The desktop ports don't need any of this — their exchange folder is a plain filesystem path that can point at a locally synced cloud folder.

The only network usage is the optional **Add Food using AI** screen, which calls Anthropic's Messages API directly (`api.anthropic.com`) when the user supplies their own API key. Manifest carries `INTERNET` for that path; nothing else hits the network.

## Build commands

```bash
./gradlew assembleDebug   # Build debug APK
./gradlew test            # Run unit tests
```

Always run `./gradlew assembleDebug` after non-trivial code changes to catch compile errors.

## Architecture

**Single-activity Compose app** — `MainActivity.kt` (~6460 lines) contains all navigation routes, screen composables, dialogs, daily totals aggregation, CSV export, markdown help rendering, and the Anthropic API client. There is no MVVM/MVI layer; screens call `DatabaseHelper` directly. There is also no separate networking layer — the AI screen uses `HttpURLConnection` + `org.json` inline.

**Key files** (all under `app/src/main/java/au/dietsentry/myapplication/`):
- `MainActivity.kt` — NavHost with 11 routes: foodSearch, eatenLog, editFood, copyFood, insertFood, addFoodByJson, addFoodByAi, addRecipe, copyRecipe, editRecipe, utilities
- `DatabaseHelper.kt` — Singleton SQLite helper with CRUD for Foods, Eaten, Recipe, Weight tables; schema migration guards; cursor-to-model mapping; database import/export via streams
- `Food.kt`, `EatenFood.kt`, `RecipeItem.kt`, `WeightEntry.kt` — Data model classes
- `FoodList.kt`, `RecipeList.kt` — LazyColumn composables for food/recipe display
- `NumberFormatUtils.kt` — Locale-aware decimal formatting
- `ToastUtils.kt` — Custom toast helper

**Bundled assets** (under `app/src/main/assets/`):
- `foods.db` — SQLite seed database (Foods table; full schema in `DatabaseHelper.kt`)
- `NIPsysprompt.txt` — system prompt for the AI NIP-extraction mode (FSANZ Std 1.2.8 / Schedules 11–12 + JSON output schema)
- `GenericSysprompt.txt` — base system prompt for the AI general-chat mode (when NIP mode is off)
- `GenericSysprompt_websearch.txt` — additional clause appended to `GenericSysprompt.txt` when the web-search toggle is on; instructs Claude to use `web_search` for time/date/recent-events queries

## Food type conventions

These conventions are load-bearing throughout the codebase (search, display, recipe logic):
- **Solid food**: description does NOT end with ` mL` or ` mL#`; nutrients per 100 g
- **Liquid food**: description ends with ` mL` or ` mL#`; nutrients per 100 mL
- **Converted liquid-to-solid**: description contains `{density=...g/mL}`
- **Recipe food**: description ends with ` {recipe=<weight>g}`; nutrients per 100 g derived from ingredients
- **AI-generated food**: description ends with ` (AI) #` (solid) or ` (AI) mL#` (liquid). The system prompt enforces these suffixes so AI-sourced rows are easy to spot/filter in the Foods Table.

## Database rules

- **Do NOT change database schema, table names, or column names** unless explicitly asked.
- If a feature needs additional data, first propose using the existing schema or in-memory calculation.
- If a schema change is truly necessary, present a migration plan before editing code.

## AI integration (Add Food using AI screen)

- **API client**: a single `suspend fun callAnthropicApi` in `MainActivity.kt` POSTs to `https://api.anthropic.com/v1/messages` via `HttpURLConnection`. JSON via `org.json`. No new third-party libraries.
- **Persistence**: API key, selected model, web-search toggle, and NIP-mode toggle live in `SharedPreferences` (`PREFS_NAME`) under keys `KEY_ANTHROPIC_API_KEY`, `KEY_ANTHROPIC_MODEL`, `KEY_AI_WEB_SEARCH`, `KEY_AI_USE_NIP_PROMPT`. Default model is `claude-sonnet-5`; the pickable models are `claude-opus-5`, `claude-sonnet-5`, and `claude-haiku-4-5-20251001`. On Opus 5 / Sonnet 5 the API runs adaptive thinking when the `thinking` field is omitted, so the request builder sends `thinking: {type: "disabled"}` when the extended-thinking toggle is off.
- **System prompts** are picked at request-build time:
  - **NIP mode ON** (default): single `system` string from `NIPsysprompt.txt`. Claude calls the `lookup_food` tool when it needs a specific food's nutrients; no CSV attachment.
  - **NIP mode OFF**: a single short general-assistant string built by `buildGeneralSystemPrompt(enableWebSearch)`. No knowledge base, no caching.
- **Server tools**: web search (`web_search_20250305`, `max_uses: 5`) is added to the `tools` array when the toggle is on. The tool ID is GA so no `anthropic-beta` header is needed. Code execution is not wired in.
- **Auto-pump to Json screen**: when NIP mode is on and a reply contains both `{` and `}`, the reply is stashed in the top-level `sessionPrefilledJson` var and the AI screen calls `navController.navigate("addFoodByJson")`. `AddFoodByJsonScreen` consumes the var inside its `rememberSaveable` initializer. On Confirm, the screen calls `popBackStack("foodSearch", inclusive = false)` so both manual and AI entry paths land on the Foods Table with the new food highlighted.
- **Image attachments**: `ActivityResultContracts.PickMultipleVisualMedia()` for the picker; `loadImageForAi` downsizes via `inSampleSize` to ≤1568 px before JPEG-encoding and base64-stuffing into the request.
- **Diagnostics**: `Log.d("AnthropicRequest", body)` and `Log.d("AnthropicResponse", text)` are emitted on every call. Useful with `adb logcat -s AnthropicRequest:D AnthropicResponse:D`. These should be stripped before any release build (they leak request bodies, including message text and image payloads, to logcat).
- **Cost note**: per-turn input cost scales with conversation messages and any `lookup_food` tool-call rounds. Plus ~$0.01 per web search tool invocation.

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

- Gradle 9.3.1, AGP 9.1.1, Kotlin 2.3.10
- compileSdk/minSdk/targetSdk: 36, JVM target: 11
- Compose BOM 2026.01.01, Navigation Compose 2.9.7, Material 3
- commonmark 0.27.1 (markdown rendering with autolink, GFM tables, strikethrough, task lists)
- Java standard library only for AI networking (`HttpURLConnection`, `org.json`); no OkHttp, Retrofit, or Anthropic SDK dependency
