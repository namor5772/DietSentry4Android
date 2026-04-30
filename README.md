# DietSentry4Android

DietSentry is a primarily-offline Android app for food/nutrition lookup, eaten-food logging, recipe-based foods, and daily weight tracking. It now also has an optional **Add Food using AI** screen that uses Anthropic's Claude models (with your own API key) to generate Nutrition Information Panel JSON from a description and/or label photos.

This repository is developed in Kotlin + Jetpack Compose. The app uses a bundled SQLite `foods.db` on first run, then reads/writes the internal app database.

## 1. What the app does

- Foods Table:
  - Search foods by description (`text1|text2` means both terms must match).
  - Switch detail view with `Min`, `NIP`, `All`.
  - Open per-food actions arranged in two rows. Top row (operate on selected food): `Log`, `Edit`, `Copy`, `Convert`, `Delete`. Bottom row (do not depend on selection): `Add`, `Json`, `AI`, `Utilities`.
- Eaten Table:
  - View eaten logs in `Min`, `NIP`, `All`.
  - Toggle `Daily totals` to aggregate by date.
  - Toggle `Filter by date` and choose a date from the date picker.
  - Edit/delete individual log entries when daily totals are off.
  - With `Daily totals` ticked, tap any day's totals card to slide up a bottom sheet with **Explain this day (AI)** and **Edit my profile** actions — the AI assesses the day's intake against Australian NHMRC NRVs in 2–3 short paragraphs, personalised by a free-text user profile (e.g. "age 67 male, weight 89kg, dietary goals: low sodium") that persists across launches.
- Utilities:
  - `Export db`: export internal `foods.db`.
  - `Import db`: replace the internal database from external `foods.db`.
  - `Export csv`: export daily totals as `EatenDailyAll.csv`.
  - `Weight Table`: add/edit/delete dated weight entries with optional comments.
- Add Food using AI:
  - Tap the `AI` button on the Foods Table to open a chat with Anthropic's Claude.
  - Settings (gear icon) hold your Anthropic API key, model selection (Opus 4.7 / Sonnet 4.6 / Haiku 4.5), and three toggles: **Web search**, **NIP mode**, and **Extended thinking (adaptive)** (the latter has no effect on Haiku 4.5, which doesn't support thinking).
  - With **NIP mode** on (default), the bundled `NIPsysprompt.txt` system prompt is sent as the system field and Claude has access to a `lookup_food` client-side tool that queries the live Foods table SQLite database for nutrient values on demand. Replies are Diet Sentry compatible JSON and are auto-pumped into the **Add Food using Json** screen — one tap on Confirm adds the food and lands on the Foods Table with the new food highlighted (same as the manual Json flow).
  - When the user message contains the word "recipe" (case-insensitive), the screen swaps to a recipe-mode prompt (`RECIPEsysprompt.txt`) and runs `lookup_food` in *recipe mode* — pre-filtering out liquids and AI/user-added records (FoodDescriptions ending in `mL`, `mL#`, or `#`) so only solid, non-user-added foods are eligible as ingredients. Claude returns a recipe JSON (`type: "recipe"`, `ingredients[]`) that auto-pumps to the Json screen; Confirm there creates the recipe (Foods row + Recipe rows linked by FoodId) — same database state as building it by hand on the Add Recipe screen. AI-generated recipes get ` (AI)` appended before the trailing `{recipe=<weight>g}` marker.
  - With **NIP mode** off, Claude is a general assistant; replies stay in the chat with no auto-navigation.
  - Multi-image attach: tap `+`, long-press to multi-select on-pack NIP photos, then `Done`.
  - **Live tool-call indicator**: while a query is processing, the loading row shows "Looking up '<query>' in the Foods table…" when `lookup_food` fires and "Searched the web: '<query>'" after each `web_search` call. Falls back to "Thinking…" between tool calls.
  - **Cost transparency**: a small status row at the top of the AI screen shows the cumulative session cost (e.g. "Session cost: $0.0143 (3 turns)"). Per-call cost is also appended to the JSON `notes` field of every reply, so the cost rides through to the Foods table when you Confirm. The Json screen header shows the single AI call cost on the auto-pumped path.
  - The chat is in-memory only; settings persist across launches.

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
- Description input field:
  - Edit/Copy/Insert Food screens now show the Description as a 3-row text area that grows for longer content, with the label top-aligned to the first text row.
  - Recipe screens (Add/Edit/Copy Recipe) intentionally keep a single-row Description input.
- AI integration (Anthropic Claude):
  - The Foods Table selection panel now has nine buttons in two rows; the second row includes an `AI` button.
  - The AI screen calls `api.anthropic.com/v1/messages` directly with your own API key. No SDK dependency.
  - System prompts and reference data ship as bundled assets — see *Bundled assets* in section 5. Edit any and rebuild to update at runtime.
  - **Client-side tool use** (`lookup_food`): rather than attaching big knowledge bases to every request, Claude calls the tool when it needs nutrient values. It runs `DatabaseHelper.searchFoods()` against the live `foods.db` and returns up to 5 matches ranked by first-term prefix match (so `Tomato, paste, ...` beats `Baked beans, canned in tomato sauce` for query "tomato"). Both NIP mode and recipe mode use this; recipe mode passes a `recipeMode=true` flag that pre-filters out liquids and AI/user-added rows.
  - **Recipe Confirm wiring**: `AddFoodByJsonScreen`'s Confirm button detects `type: "recipe"` and dispatches to a recipe-insert helper (`insertRecipeFromRecipeJson`). It validates every `FoodId` against the live Foods table, rejects liquid ingredients, then writes a Foods row + Recipe rows linked by FoodId — replicating the math of the manual `AddRecipeScreen` Confirm flow. AI-generated recipes get ` (AI)` appended to the FoodDescription before the `{recipe=Xg}` marker.
  - **Prompt caching**: the system prompt + tool definitions carry `cache_control: ephemeral`, so subsequent iterations of a tool-use loop and consecutive turns within ~5 min reuse the static prefix at ~10× discount on Anthropic's input rate.
  - **Web search** (`web_search_20250305`) is wired as an optional Anthropic-hosted tool.
  - **Extended thinking (adaptive)** is wired as an optional toggle for Sonnet 4.6 / Opus 4.7. Haiku 4.5 doesn't support extended thinking — the toggle is automatically disabled when Haiku is selected.
  - **Output token cap** (`max_tokens`) set to 16,384 to leave room for thinking + multi-iteration tool use + a full NIP JSON without truncation.
  - **Daily totals explain flow**: a second AI entry point lives on the **Eaten Table** screen. With `Daily totals` ticked, tapping any day's card opens a bottom sheet with **Explain this day (AI)** and **Edit my profile** actions. Explain sends all 24 nutrient totals + total amount + weight + a free-text user profile to Claude using the bundled `EXPLAINsysprompt.txt` system prompt. Reuses the API key and model from the AI Settings dialog (`KEY_ANTHROPIC_API_KEY`, `KEY_ANTHROPIC_MODEL`); profile persists at `KEY_AI_USER_PROFILE`. Hardcodes web search and extended thinking off for cost predictability — typical cost is ~$0.01/call on Sonnet 4.6, ~$0.003 on Haiku 4.5. The bottom sheet → AlertDialog handoff uses `scope.launch { sheetState.hide() }` so the modal-focus stack unwinds cleanly before the TextField claims keyboard focus.
  - The app gains the `INTERNET` permission for the AI flow only; all other features remain offline.

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
- AI-generated food:
  - Solid: description ends with ` (AI) #` (per 100 g).
  - Liquid: description ends with ` (AI) mL#` (per 100 mL).
  - Recipe: description ends with ` (AI) {recipe=<weight>g}` (per 100 g, derived from ingredient totals like any other recipe).
  - The `(AI)` substring makes AI-sourced rows easy to identify and filter in the Foods Table.

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
- Bundled assets: `app/src/main/assets/`
  - `foods.db` — SQLite seed database (also the live nutrient source the `lookup_food` AI tool queries via `DatabaseHelper.searchFoods`).
  - `NIPsysprompt.txt` — system prompt for AI NIP-mode replies (FSANZ Std 1.2.8 / Schedules 11–12 + JSON output schema, with explicit `lookup_food` tool guidance).
  - `RECIPEsysprompt.txt` — system prompt for AI recipe-mode replies (triggered when the user message contains "recipe"). Tells Claude to use `lookup_food` for ingredient lookup, the AFCD `Category, descriptor` query strategy, and the recipe JSON output schema (`type: "recipe"`, `ingredients[]`).
  - `EXPLAINsysprompt.txt` — system prompt for the **Eaten Table → Daily totals → Explain this day (AI)** flow. Instructs Claude to assess the day's intake against Australian NHMRC NRVs in 2–3 plain-language paragraphs, in Australian English, flagging nutrients that are notably under- or over-consumed.
  - `GenericSysprompt.txt` + `GenericSysprompt_websearch.txt` — base + optional web-search clause for AI general-chat mode (NIP toggle off).
