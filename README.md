# DietSentry4Android

DietSentry is a primarily-offline app for food/nutrition lookup, eaten-food logging, recipe-based foods, and daily weight tracking. It also has an optional **Add Food using AI** screen that uses Anthropic's Claude models (with your own API key) to generate Nutrition Information Panel JSON from a description and/or label photos.

This repository contains **three complete implementations** of the app:

| Codebase | Platform | Language / UI | Location |
|---|---|---|---|
| Android app | Android 15+ (phone) | Kotlin + Jetpack Compose | `app/` |
| Windows port | Windows 11 (desktop) | C++17 + Dear ImGui (Direct3D 11) | `winport/` |
| macOS port | macOS (desktop) | C++17/Obj-C++ + Dear ImGui (Metal) | `macport/` |

All three share the same screens, flows, database schema and file conventions; a `foods.db` exported from any one can be imported into the others. Section 1 below describes the app's functionality and applies to **all three** implementations; sections 4–5 cover the Android build; section 6 covers the Windows port in detail; section 7 covers the macOS port.

The Android app uses a bundled SQLite `foods.db` on first run, then reads/writes the internal app database. The desktop ports do the same, seeding from `winport/assets/foods.db` into `%APPDATA%\DietSentry4Windows\` (Windows) or `~/Library/Application Support/DietSentry4Mac/` (macOS).

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
  - **Windows/macOS**: `Export db` / `Import db` / `Export csv` move `foods.db` / `EatenDailyAll.csv` through a remembered exchange folder — point it at a locally synced OneDrive folder (e.g. `OneDrive\MyImportant\DS`) to close the loop with the phone.
  - **Android**: four equal-width buttons in a 2×2 grid. Row 1 is the fixed-name database round-trip — `Overwrite db…` picks an *existing* `foods.db` in the system file picker and writes the current database into it in place (SAF `OpenDocument` + a truncating `openOutputStream`), so the file keeps its name; OneDrive accepts this even though it refuses picker *saves* (live-tested 2026-08-17), which makes it the phone → OneDrive route. The confirmation dialog names the file and its storage, reports the provider's `FLAG_SUPPORTS_WRITE` verdict, and only `.db`/SQLite targets are accepted; after a successful overwrite the file is remembered (persistable URI permission — an `Overwrite target: …` line appears), so later taps skip the picker and go straight to the confirm dialog (`Change file…` re-picks); a vanished file or lost permission is forgotten automatically. `Import db from…` opens a database straight from OneDrive/Drive/local storage, with a SQLite-header check before anything is replaced. Row 2 is the share sheet — `Share db…` / `Share csv…` hand a copy of `foods.db` / `EatenDailyAll.csv` to any app that accepts files (OneDrive's *Upload to OneDrive*, Google Drive, email, messaging); the share sheet can only add a *new* file, so use it for the very first upload and Overwrite thereafter. (The folder-based exchange flow was removed from Android on 2026-08-14: cloud providers don't support its folder picker, so it could never reach OneDrive.)
  - All three Utilities screens show a last-out and a last-in timestamp — `Db last exported` (desktop) / `Db last shared/overwritten` (Android) and `Db last imported` — stored per device in each app's prefs and stamped on success, as a staleness hint for the pass-the-baton workflow: log on one device at a time, export/share/overwrite before switching away, import before logging on the next.
  - `Eaten Graph`: opens a separate screen that visualises any single metric (weight, amount eaten, energy, or any of 22 nutrient fields) per day over a chosen date range — see the *Eaten Graph* section below.
  - `Weight Table`: add/edit/delete dated weight entries with optional comments.
- Eaten Graph (reached from Utilities → `Eaten Graph`):
  - Vertical bar chart drawn with the **Vico** charting library.
  - **Metric dropdown** with 25 entries: `My weight (kg)`, `Amount (g/mL)`, `Energy (kJ)`, plus all 22 nutrient fields. Weight comes from the Weight table; the rest from `aggregateDailyTotals`.
  - **Date range chips** — `1W` / `1M` / `3M` / `1Y` / `All` (each ending **yesterday**, since the current day usually has incomplete data) and `Custom` (opens a date-range picker; the chosen end date is inclusive — today is fine).
  - **Y-axis lower bound** is a "nice" round value somewhat below the data Min (clamped to ≥ 0), so small variations between days are visually distinguishable instead of being squashed at the top of a 0-anchored axis.
  - **Stats summary** shows total, average per day, max, and min over the selected range. Total is omitted for weight (summing body weights is meaningless).
  - **Weight sentinel handling**: if a Weight entry's value is exactly `0.1 kg` (the "I forgot to weigh today" sentinel), the bar is omitted from the y-axis range and from the stats; the day-count line then reads "N of M days measured".
  - **Selections persist** across navigation and app restarts via SharedPreferences (metric, range preset, custom from/to dates).
  - The active range's inclusive from–to dates are shown beneath the chips for all options.
- Add Food using AI:
  - Tap the `AI` button on the Foods Table to open a chat with Anthropic's Claude.
  - Settings (gear icon) hold your Anthropic API key, model selection (Opus 5 / Sonnet 5 / Haiku 4.5), and three toggles: **Web search**, **NIP mode**, and **Extended thinking (adaptive)** (the latter has no effect on Haiku 4.5, which doesn't support thinking).
  - With **NIP mode** on (default), the bundled `NIPsysprompt.txt` system prompt is sent as the system field and Claude has access to a `lookup_food` client-side tool that queries the live Foods table SQLite database for nutrient values on demand. Replies are Diet Sentry compatible JSON and are auto-pumped into the **Add Food using Json** screen — one tap on Confirm adds the food and lands on the Foods Table with the new food highlighted (same as the manual Json flow).
  - When **NIP mode is on** and the message being sent contains the word "recipe" (case-insensitive substring), the screen swaps to a recipe-mode prompt (`RECIPEsysprompt.txt`) and runs `lookup_food` in *recipe mode* — pre-filtering out liquids and AI/user-added records (FoodDescriptions ending in `mL`, `mL#`, or `#`) so only solid, non-user-added foods are eligible as ingredients. Claude returns a recipe JSON (`type: "recipe"`, `ingredients[]`) that auto-pumps to the Json screen; Confirm there creates the recipe (Foods row + Recipe rows linked by FoodId) — same database state as building it by hand on the Add Recipe screen. AI-generated recipes get ` (AI)` appended before the trailing `{recipe=<weight>g}` marker. The check is made **per message**: a follow-up without the word runs under the ordinary NIP prompt and yields a single plain food, so keep "recipe" in every message that should produce or refine a recipe. Without the word (or with NIP mode off) the AI never creates a recipe — a NIP reply is always stored as one ordinary food row, even if Claude names it with a `{recipe=…}` marker (the app strips it; see *No phantom recipes* in section 2).
  - With **NIP mode** off, Claude is a general assistant; replies stay in the chat with no auto-navigation.
  - Multi-image attach: tap `+`, long-press to multi-select on-pack NIP photos, then `Done`.
  - **Live tool-call indicator**: while a query is processing, the loading row shows "Looking up '<query>' in the Foods table…" when `lookup_food` fires and "Searched the web: '<query>'" after each `web_search` call. Falls back to "Thinking…" between tool calls.
  - **Cost transparency**: a small status row at the top of the AI screen shows the cumulative session cost (e.g. "Session cost: $0.0143 (3 turns)"). Per-call cost is also appended to the JSON `notes` field of every reply, so the cost rides through to the Foods table when you Confirm. The Json screen header shows the single AI call cost on the auto-pumped path.
  - **Markdown rendering** in chat: assistant replies (especially in general-chat mode where Claude often returns headers, bullets, bold, and code blocks) are rendered through the same commonmark pipeline used for in-app help. The **Copy** button still copies the raw markdown source so it's pasteable elsewhere.
  - **Davey Diet persona**: in general-chat mode (NIP toggle off), the assistant introduces itself as "Davey Diet" via the bundled `GenericSysprompt.txt`.
  - The chat is in-memory only; settings persist across launches.

## 2. Recent behavior updates reflected in this README (Android)

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
  - **Recipe Confirm wiring**: `AddFoodByJsonScreen`'s Confirm button detects `type: "recipe"` and dispatches to a recipe-insert helper (`insertRecipeFromRecipeJson`). It validates every `FoodId` against the live Foods table, rejects liquid ingredients, then writes a Foods row + Recipe rows linked by FoodId — replicating the math of the manual `AddRecipeScreen` Confirm flow. AI-generated recipes get ` (AI)` appended to the FoodDescription before the `{recipe=Xg}` marker (any marker or `(AI)` tag Claude already put on the name is dropped first, so no `(AI) (AI)` doubles).
  - **No phantom recipes**: the `{recipe=Xg}` marker means "this row has ingredient rows", so the plain NIP-JSON path strips it (re-applying the ` #` / `mL#` ending) and the NIP system prompt forbids it. Previously Claude could name a photographed composite dish `… (AI) {recipe=280g}` in ordinary NIP mode; the row was then treated as a recipe everywhere (Edit opened "Editing Recipe" with no ingredients). Same guard in all three apps.
  - **Prompt caching**: the system prompt + tool definitions carry `cache_control: ephemeral`, so subsequent iterations of a tool-use loop and consecutive turns within ~5 min reuse the static prefix at ~10× discount on Anthropic's input rate.
  - **Web search** (`web_search_20250305`) is wired as an optional Anthropic-hosted tool.
  - **Extended thinking (adaptive)** is wired as an optional toggle for Sonnet 5 / Opus 5 (and the previous-generation Sonnet 4.6 / Opus 4.7 if one of those is still saved in settings). Haiku 4.5 doesn't support extended thinking — the toggle is automatically disabled when Haiku is selected. Because Sonnet 5 / Opus 5 run adaptive thinking by default when the `thinking` field is omitted, the app sends an explicit `thinking: {type: "disabled"}` on those models while the toggle is off, so off really means off.
  - **Output token cap** (`max_tokens`) set to 16,384 to leave room for thinking + multi-iteration tool use + a full NIP JSON without truncation.
  - **Network timeout**: per-iteration `readTimeout` is 240s (4 min), giving Sonnet 5 headroom on slow turns under load. Connect timeout stays at 30s.
  - **Release-build log hygiene**: all Anthropic request/response and AiAutoPump diagnostic logs are routed through a small `aiLog()` helper that no-ops when `BuildConfig.DEBUG` is false, so production builds don't leak request bodies (user messages, image payloads, Claude replies) to logcat.
  - **Daily totals explain flow**: a second AI entry point lives on the **Eaten Table** screen. With `Daily totals` ticked, tapping any day's card opens a bottom sheet with **Explain this day (AI)** and **Edit my profile** actions. Explain sends all 24 nutrient totals + total amount + weight + a free-text user profile to Claude using the bundled `EXPLAINsysprompt.txt` system prompt. Reuses the API key and model from the AI Settings dialog (`KEY_ANTHROPIC_API_KEY`, `KEY_ANTHROPIC_MODEL`); profile persists at `KEY_AI_USER_PROFILE`. Hardcodes web search and extended thinking off for cost predictability — typical cost is ~$0.01/call on Sonnet 5, ~$0.003 on Haiku 4.5. The bottom sheet → AlertDialog handoff uses `scope.launch { sheetState.hide() }` so the modal-focus stack unwinds cleanly before the TextField claims keyboard focus.
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

## 4. Build and run (Android)

- Open this project in Android Studio on Windows (or compatible environment) with Android SDK installed.
- Current module config in `app/build.gradle.kts`:
  - `compileSdk = 36`
  - `minSdk = 36`
  - `targetSdk = 36`
  - Java/Kotlin JVM target 11
- Common commands from repo root:
  - Build debug APK: `./gradlew assembleDebug`
  - Run unit tests: `./gradlew test`

## 5. Project structure (Android)

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

## 6. Windows port (C++)

A native Windows 11 desktop port of the entire app lives in [`winport/`](winport/README.md). It compiles to a **single self-contained `DietSentry.exe`** (~4 MB): no installer, no .NET/JVM/VC-redist runtime, no DLLs to ship. It reproduces the Android app screen-for-screen — every screen, dialog, help page and flow in section 1 works identically on Windows.

### 6.1 Tech stack

| Concern | Android | Windows port |
|---|---|---|
| Language | Kotlin | C++17 (MSVC, `/std:c++17 /utf-8`) |
| UI | Jetpack Compose + Material 3 | [Dear ImGui](https://github.com/ocornut/imgui) 1.92 over Direct3D 11, styled to Material 3 light |
| Database | Android SQLite | SQLite amalgamation compiled in (`winport/vendor/sqlite3.c`) |
| Settings | SharedPreferences | JSON file (`prefs.json`), same key names |
| AI networking | `HttpURLConnection` + `org.json` | WinHTTP + nlohmann/json (`ordered_json`) |
| Label photos for AI | Photo picker + BitmapFactory | File-open dialog + WIC (decode → ≤1568 px → JPEG) |
| Import/export folder | SAF folder picker (remembered URI) | `IFileDialog` folder picker (remembered path) |
| Charts | Vico | custom-drawn bar chart (same metrics, ranges, y-floor and stats) |
| Markdown (help + AI chat) | commonmark-java | small built-in renderer (`winport/src/markdown.cpp`) |
| Multi-line text fields (Description, Notes, weight Comments, JSON paste box, AI profile) | Compose `TextField` (soft-wraps; grows with content between `minLines`/`maxLines`) | ImGui `InputTextMultiline` with `ImGuiInputTextFlags_WordWrap`, sized each frame from the wrapped line count — long lines fold inside the box and the box grows to show them all, with the same caps as Android (Description/Notes/Comments unbounded, recipe Notes 8 lines then scrolls, AI profile fixed at 4); a field growing at the bottom of a scrolling form scrolls itself into view like Compose |
| Fonts | Roboto (system) | Segoe UI / Segoe UI Symbol / Consolas (system) |

All third-party code is vendored in `winport/vendor/` (MIT / public-domain licences) and compiled into the exe, so a clone builds offline.

### 6.2 Building

Requirements: Visual Studio 2022/18 (Community is fine) with the **Desktop development with C++** workload. Then:

```
winport\build.bat
```

The script locates `VsDevCmd.bat` itself, compiles SQLite and Dear ImGui once into `winport\build\*.obj` (reused on later builds), compiles the icon resource (`app.rc`), builds all `winport\src\*.cpp`, links `winport\build\DietSentry.exe` statically (`/MT`), and copies `winport\assets\` next to the exe. Incremental rebuilds take a few seconds.

### 6.3 Running and data locations

- Run `winport\build\DietSentry.exe` (the `assets\` folder must sit beside the exe — `build.bat` arranges this).
- **First run** copies the seed `assets\foods.db` to `%APPDATA%\DietSentry4Windows\foods.db`; that copy is the live database from then on. Settings (Min/NIP/All selections, graph state, AI key/model/toggles, user profile, exchange folder) persist in `%APPDATA%\DietSentry4Windows\prefs.json`.
- **Factory reset**: delete the `%APPDATA%\DietSentry4Windows` folder; the next launch re-seeds.
- **App icon**: `winport/assets/DietSentry.ico` (a dinner plate with a nutrition bar chart on Material purple) is embedded in the exe, so Explorer, the taskbar and shortcuts show it. To make a desktop shortcut: right-click the exe → *Send to → Desktop (create shortcut)*.
- The **Esc** key acts as the Android system Back button (clears the current selection first, then leaves the screen). The window is resizable; the default size is phone-shaped.

### 6.4 Moving data between phone and PC

The two apps read and write byte-compatible databases:

The intended loop runs through OneDrive; on the PC point the exchange folder at a folder inside the locally synced OneDrive tree (e.g. `OneDrive\MyImportant\DS`):

1. **Phone → PC**: on Android use Utilities → `Overwrite db…`, open that folder's existing `foods.db` in the file picker (OneDrive) and confirm — it is rewritten in place, so the name stays `foods.db` (which the desktop import expects); from then on the file is remembered and the button goes straight to the confirm dialog. Only for the very first upload, when the folder has no `foods.db` yet, use `Share db…` → **Upload to OneDrive** instead (the share sheet can only add a *new* file; on a name clash OneDrive numbers it `foods 1.db`). Once OneDrive syncs, use Utilities → `Import db` on the PC.
2. **PC → phone**: on Windows/macOS use Utilities → `Export db` into that synced folder, then on Android use Utilities → `Import db from…` and open `foods.db` straight from OneDrive.
3. **Without OneDrive**: any transfer works — `Share db…` reaches email/Drive/messaging, and `Import db from…` opens from local storage (e.g. Downloads) as well as cloud locations. **Google Drive** fits the same loop end to end (Drive for desktop syncing the exchange folder on the PC/Mac; `Share db…` once to seed the file, then `Overwrite db…` / `Import db from…` via the picker's Google Drive entry), with one caveat: Drive allows several files of the same name in a folder, so repeated `Share db…` uploads produce multiple `foods.db` files (mirrored locally as `foods.db`, `foods (1).db`, …) — another reason to overwrite rather than re-share.

`Export csv` (desktop) and `Share csv…` (Android) produce the same `EatenDailyAll.csv` (same columns, including `My weight (kg)` and `Comments`).

**Date compatibility note**: Android's `SimpleDateFormat("d-MMM-yy")` in the en_AU locale spells out *June*, *July* and *Sept* (e.g. `30-July-26`) while abbreviating other months. The Windows port writes exactly this dialect so rows created on either device are byte-identical, and its parser additionally accepts any month spelling (`Jul`, `July`, `Sept`, `Dec.`, …) so databases from phones in other locales still filter, sort and graph correctly.

### 6.5 AI screen on Windows

Identical behaviour to Android: set your Anthropic API key via the gear icon (stored locally in `prefs.json`, sent only to `api.anthropic.com`); pick Opus 5 / Sonnet 5 / Haiku 4.5; the Web search, NIP mode and Extended thinking toggles, the `lookup_food` client tool loop against the live database, prompt caching, live tool-call status lines, session/per-call cost accounting, JSON auto-pump into the Json screen, and the Eaten Table's *Explain this day (AI)* flow all work the same. Requests run on a worker thread so the UI never blocks; the per-iteration read timeout is 240 s, connect 30 s, matching Android.

### 6.6 Windows source layout

```
winport/
├── build.bat               one-step build script (also compiles app.rc icon resource)
├── app.rc                  embeds assets/DietSentry.ico into the exe
├── assets/                 foods.db seed + AI system prompts + icon (copied beside exe)
├── vendor/                 Dear ImGui, SQLite amalgamation, nlohmann/json (vendored)
└── src/
    ├── main.cpp            WinMain, D3D11/ImGui bootstrap, navigation host
    ├── app.h               models (Nutrients/Food/EatenFood/...), constants, App/Screen
    ├── util.cpp            number/date formatting (en_AU month dialect), marker helpers
    ├── db.cpp              DatabaseHelper.kt port (schema guards, CRUD, search, import/export)
    ├── prefs.cpp           SharedPreferences equivalent (prefs.json)
    ├── ui.h / ui.cpp       Material-ish widget layer: top bar, segmented buttons, chips,
    │                       switches, dialogs, calendar/time/range pickers, virtual lists, toasts
    ├── markdown.cpp        markdown renderer for help sheets and AI chat replies
    ├── helptexts.*         the in-app help manuals (carried over from MainActivity.kt)
    ├── dialogs.h           shared amount / amount+date+time dialogs
    ├── anthropic.*         Messages API client + lookup_food tool loop (WinHTTP, worker thread)
    ├── imageutil.*         WIC image decode/downscale/JPEG-encode + file pickers
    └── screens_*.cpp       one file per screen, mirroring the Kotlin composables:
                            foods, eaten, editfood (edit/copy/insert), json, recipe
                            (add/edit/copy), utilities (+ Weight Table), graph, ai
```

Each `screens_*.cpp` file corresponds to the like-named composable(s) in `MainActivity.kt`; when changing behaviour in one codebase, make the matching change in the other (see `CLAUDE.md`). More build/architecture detail is in [`winport/README.md`](winport/README.md).

### 6.7 Known differences from Android

- The look is Material-3-flavoured but rendered by ImGui — close to, not pixel-identical with, Compose.
- Folder/photo pickers are the native Windows dialogs rather than SAF / the Android photo picker.
- The bar chart is a custom renderer (same data, ranges, y-axis behaviour and summary stats as Vico; adds a hover tooltip per bar).
- The chat and edit screens use the system clipboard via Ctrl+C/Ctrl+V as usual on desktop; the assistant bubble's **Copy** button copies the raw markdown, as on Android.
- `DIETSENTRY_AUTONAV=<route>` (e.g. `eatenLog`, `utilities`, `eatenGraph`, `addFoodByAi`) opens a screen directly at launch, and `DIETSENTRY_DATA_DIR=<folder>` redirects the app-data folder — testing hooks with no Android equivalent.
- **Full keyboard operation** (both desktop ports, since 2026-08-17): Tab / Shift+Tab step through the controls in visual order with a purple focus ring, arrows move between neighbours and Up/Down walk (and scroll) list rows, Enter or Space activates (buttons, rows, checkboxes, switches, drop-downs; text fields start editing), Escape goes back / closes dialogs. Amount dialogs focus their field on open and confirm on Enter; delete confirmations open unarmed. See the *Keyboard navigation* section of the Foods Table help.

## 7. macOS port (C++)

A native macOS build of the same C++ app lives in [`macport/`](macport/README.md), producing a self-contained `DietSentry.app` bundle (no installer, no runtime dependencies). It deliberately mirrors `winport/` **file-for-file** so the two desktop ports stay easy to diff and evolve together.

### 7.1 Building and running

Requires the Xcode Command Line Tools (`xcode-select --install`). Then:

```
macport/build.sh        # builds macport/build/DietSentry.app
open macport/build/DietSentry.app
```

Like the other builds, SQLite and Dear ImGui compile once and are reused; subsequent builds only recompile `src/`. First run seeds the database into `~/Library/Application Support/DietSentry4Mac/` (`foods.db` + `prefs.json`); delete that folder to factory-reset. For a Desktop shortcut, symlink or alias the app bundle: `ln -sfn "$(pwd)/macport/build/DietSentry.app" ~/Desktop/DietSentry.app`.

### 7.2 How it relates to the Windows port

- **Shared source**: `ui.h`, `markdown.cpp`, `helptexts.*`, `dialogs.h`, `anthropic.h` and all `screens_*.cpp` except `screens_utilities.cpp` are byte-identical copies of the `winport/` files; a few others differ only in small platform sections (`util.cpp`, `db.cpp`, `prefs.cpp`, `app.h`).
- **Platform swaps**: `main.mm` (NSApplication + Metal/MTKView instead of WinMain + D3D11), `imageutil.mm` (ImageIO + `NSOpenPanel` instead of WIC + `IFileDialog`), and libcurl instead of WinHTTP as the Anthropic transport — the request JSON, `lookup_food` tool loop and cost math are identical.
- **Single source of truth**: ImGui core / SQLite / nlohmann-json compile from `winport/vendor/`, and the database + AI prompts come from `winport/assets/` at build time; only the Apple ImGui backends and the `.icns` icon are mac-specific (a Big Sur-style rendition of the same plate-and-bars motif, generated natively by `macport/assets/draw_icon.m`).
- **Fonts**: Arial / Arial Bold / Menlo from the system. Apple Symbols is merged in for symbol glyphs (arrows, ⚙) and Menlo after it for the Dingbats glyphs neither Arial nor Apple Symbols has (✕ on the clear buttons, ➤ on the AI send button) — the role Segoe UI Symbol plays on Windows. Without that second fallback ImGui draws its "?" placeholder for those glyphs.
- **Conveniences**: Esc acts as Back (as on Windows), Cmd+Q quits, and — for driving the Mac over VNC/remote desktop from a Windows keyboard — text fields additionally accept **Ctrl+V** for paste and the menu bar offers **Edit → Paste** for mouse-only pasting. The `DIETSENTRY_AUTONAV` testing hook works here too.

When changing app behaviour, apply the matching change to all three implementations (`app/`, `winport/`, `macport/`) — see `CLAUDE.md`. Full build/architecture detail is in [`macport/README.md`](macport/README.md).
