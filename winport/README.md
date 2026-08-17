# DietSentry for Windows (C++ port)

A native Windows 11 port of **DietSentry4Android**, written in C++17 and compiled to a
single self-contained `DietSentry.exe` (~4 MB, no runtime dependencies, no installer).
The GUI structure and functionality mirror the Android app screen-for-screen.

> **This directory is upstream of the macOS port.** The sibling
> [`macport/`](../macport/README.md) build compiles Dear ImGui, SQLite and
> nlohmann/json straight out of `winport/vendor/`, and copies `winport/assets/`
> (seed `foods.db` + the AI system prompts) into its app bundle at build time —
> there is deliberately only one copy of each. Editing anything under `vendor/`
> or `assets/` therefore changes the **Mac** build too, so rebuild and sanity-check
> both ports after such a change. Files under `src/` are mirrored rather than
> shared: see *Relationship to the Windows port* in `macport/README.md`.

## Running

```
winport\build\DietSentry.exe
```

The exe needs the `assets\` folder next to it (bundled database + AI system prompts).
On first run the seed database is copied to `%APPDATA%\DietSentry4Windows\foods.db`;
settings live in `%APPDATA%\DietSentry4Windows\prefs.json`. Delete that folder to
reset the app to factory state.

`foods.db` files are fully interchangeable with the Android app — same schema, same
tables (Foods / Eaten / Recipe / Weight), same description-marker conventions
(` mL`, ` #`, ` {recipe=Xg}`, ` (AI)`), same 2-decimal rounding. You can export the
db on the phone and import it here (Utilities → Import db), or vice versa.

## Building from source

Requires Visual Studio (Community is fine) with the C++ workload. From a normal
command prompt:

```
winport\build.bat
```

The script locates `VsDevCmd.bat` itself, compiles SQLite and Dear ImGui once into
`build\*.obj`, compiles the icon resource (`app.rc` → `build\app.res`), then compiles
the app sources and links `build\DietSentry.exe` statically (`/MT`, no VC redist
needed). Subsequent builds only recompile `src\`.

## What is ported (everything)

| Android screen | Windows equivalent |
|---|---|
| Foods Table (search, Min/NIP/All, 9-button selection panel) | identical |
| LOG dialog (amount + date + time pickers) | identical (custom calendar/time pickers) |
| Eaten Table (Min/NIP/All, Daily totals, Filter by date, edit/delete) | identical |
| Daily-totals action sheet → **Explain this day (AI)** + **Edit my profile** | identical |
| Editing/Copying Solid & Liquid Food, Add Food (Solid/Liquid/Recipe radios) | identical |
| Add Food using Json (NIP JSON + Recipe JSON pipelines, auto-pump) | identical |
| Add Recipe / Editing Recipe / Copying Recipe (staging via FoodId=0 / CopyFg=1) | identical |
| Convert liquid→solid (density marker) | identical |
| Utilities: Export db / Import db / Export csv (remembered exchange folder) | folder picker instead of Android SAF |
| Weight Table (add/edit/delete, duplicate-date guard) | identical |
| Eaten Graph (25 metrics, 1W/1M/3M/1Y/All/Custom, nice y-min, stats, 0.1 kg sentinel) | custom-drawn chart instead of Vico |
| Add Food using AI (chat, settings, images, lookup_food tool loop, web search, extended thinking, cost tracking, auto-pump) | identical, via WinHTTP |
| In-app `?` help on every screen (markdown) | identical texts |
| Toasts | identical |

Preference keys, session-scoped state (recipe search queries, filter date), the
`EatenTs` minute counter, CSV column order, AI request JSON (prompt caching
breakpoints, `web_search_20250305`, adaptive thinking), pricing table and cost
formulas all match the Kotlin source.

## Implementation notes

- **UI**: [Dear ImGui](https://github.com/ocornut/imgui) (MIT) rendered via
  Direct3D 11, styled to Material 3 light (same look family as the Compose app).
  Fonts: Segoe UI / Segoe UI Symbol / Consolas from `C:\Windows\Fonts`.
- **Database**: SQLite amalgamation (public domain) compiled in; port of
  `DatabaseHelper.kt` in `src/db.cpp`.
- **JSON**: nlohmann/json (MIT), `ordered_json` so reply JSON keeps field order.
- **Networking**: WinHTTP directly against `api.anthropic.com/v1/messages` —
  same request/response handling as the Kotlin `callAnthropicApi`, including the
  client-side `lookup_food` tool loop (max 12 iterations) running on a worker thread.
- **Images**: attachments are decoded/downscaled (≤1568 px) and JPEG-encoded via
  WIC, matching `loadImageForAi`.
- **Markdown**: a small renderer (`src/markdown.cpp`) covering the constructs the
  help texts and Claude's replies use.
- **Dates**: `DateEaten`/`DateWeight` strings are written in the exact dialect
  Android's `SimpleDateFormat("d-MMM-yy")` produces under en_AU — three-letter
  months except *June*, *July* and *Sept* spelled out (`30-July-26`) — so rows
  written on either device are byte-identical. Parsing is tolerant: any ≥3-letter
  month prefix with optional trailing period (`Jul`, `July`, `Sept`, `Dec.`, …),
  so databases from phones in other locales still filter/sort/graph correctly.
- The keyboard **Esc** key acts as the Android system Back button (clears the
  selection first, then leaves the screen).
- **Keyboard navigation** (since 2026-08-17): the whole app can be driven without
  the mouse. **Tab** / **Shift+Tab** move the focus through the controls in visual
  order (top bar first, then the content) and a purple focus ring marks the focused
  control — it appears as soon as you use the keyboard and hides again on mouse use.
  **Arrow keys** move between neighbouring controls and **Up/Down** step through the
  rows of a list (a list is a single Tab stop, so Tab steps *past* it while the
  arrows walk inside it and scroll it). **Enter** or **Space** activates the focused
  control (button, list row, checkbox, switch, drop-down; on a text field it starts
  editing — Tab moves on, Enter finishes). **Escape** goes back a screen or closes
  the open dialog / drop-down / help sheet, and leaves the focus where it was (a
  closed dialog or drop-down hands it back to the control that opened it, so Tab
  carries on from there). Dialogs with an amount field focus it on open and
  confirm on **Enter**; delete confirmations open with nothing armed (Tab, then
  Enter). In a help sheet the arrows, PageUp/PageDown, Home/End scroll.
- **App icon**: `assets/DietSentry.ico` (a dinner plate with a nutrition bar
  chart on Material purple) is embedded into the exe via `app.rc`, so Explorer,
  the taskbar and desktop shortcuts pick it up automatically. To make a desktop
  shortcut, right-click `build\DietSentry.exe` → *Send to* → *Desktop*, or run:
  `powershell -c "$s=(New-Object -ComObject WScript.Shell).CreateShortcut([Environment]::GetFolderPath('Desktop')+'\DietSentry.lnk');$s.TargetPath='<full path>\DietSentry.exe';$s.Save()"`
- `DIETSENTRY_AUTONAV=<route>` environment variable opens a screen directly at
  launch (used for testing). Recognised routes: `eatenLog`, `utilities`,
  `eatenGraph`, `addFoodByJson`, `addFoodByAi`, `addRecipe`, `insertFood`, and
  `editFirst` (opens Edit Food on the lowest-numbered food). Unknown values are
  ignored and the app starts on the Foods Table as usual.
- `DIETSENTRY_DATA_DIR=<folder>` environment variable redirects the app-data
  folder (`foods.db` + `prefs.json`) — a testing hook that keeps an automated or
  experimental instance away from the real data (created if missing).

## Source layout

```
winport/
├── build.bat               one-step build script (also compiles the app.rc icon)
├── app.rc                  embeds assets/DietSentry.ico into the exe
├── assets/                 foods.db + AI system prompts + DietSentry.ico
│                           (copied beside the exe; db + prompts also consumed
│                            by macport at build time — shared, not a copy)
├── vendor/                 Dear ImGui, SQLite amalgamation, nlohmann/json
│                           (single pinned copy; macport compiles from here too)
└── src/
    ├── main.cpp            WinMain, D3D11/ImGui bootstrap, navigation host
    ├── app.h               models, constants, App/Screen declarations
    ├── util.cpp            formatting, dates (d-MMM-yy etc.), marker helpers
    ├── db.cpp              DatabaseHelper port (SQLite)
    ├── prefs.cpp           SharedPreferences port (JSON file)
    ├── ui.h / ui.cpp       Material-ish widgets, pickers, dialogs, virtual lists
    ├── markdown.cpp        markdown renderer for help + chat
    ├── helptexts.*         in-app help texts (from MainActivity.kt)
    ├── dialogs.h           amount/date-time dialog helpers
    ├── anthropic.*         Messages API client + tool loop (WinHTTP)
    ├── imageutil.*         WIC image load/scale/encode + file pickers
    └── screens_*.cpp       one file per screen, mirroring the Kotlin composables
```
