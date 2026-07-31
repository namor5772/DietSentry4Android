# DietSentry for macOS (C++ port)

A native macOS port of **DietSentry4Android**, written in C++17/Objective-C++ and
built into a self-contained `DietSentry.app` (no runtime dependencies, no installer).
The GUI structure and functionality mirror the Android app screen-for-screen, and the
source mirrors the Windows port (`winport/`) file-for-file — most files are identical.

## Running

```
open macport/build/DietSentry.app
```

On first run the seed database is copied to
`~/Library/Application Support/DietSentry4Mac/foods.db`; settings live in
`~/Library/Application Support/DietSentry4Mac/prefs.json`. Delete that folder to
reset the app to factory state.

`foods.db` files are fully interchangeable with the Android and Windows apps — same
schema, same tables (Foods / Eaten / Recipe / Weight), same description-marker
conventions (` mL`, ` #`, ` {recipe=Xg}`, ` (AI)`), same 2-decimal rounding. You can
export the db on the phone and import it here (Utilities → Import db), or vice versa.

**Desktop shortcut**: a symlink works like a Windows desktop shortcut and shows the
app icon — `ln -sfn "$(pwd)/macport/build/DietSentry.app" ~/Desktop/DietSentry.app`
(or drag the app to the Desktop while holding ⌥⌘ to make a Finder alias).

## Building from source

Requires the Xcode Command Line Tools (`xcode-select --install`). Then:

```
macport/build.sh
```

The script compiles SQLite and Dear ImGui once into `build/obj/*.o`, then compiles
the app sources, links against system frameworks + the system libcurl, and assembles
`build/DietSentry.app` (binary, `Info.plist`, icon, assets). Subsequent builds only
recompile `src/`.

## Relationship to the Windows port

`macport/` deliberately mirrors `winport/` so the two stay easy to diff:

- **Identical files** (copied verbatim): `ui.h`, `markdown.cpp`, `helptexts.*`,
  `dialogs.h`, `anthropic.h`, and all `screens_*.cpp` except
  `screens_utilities.cpp` (folder picker + directory checks differ). `ui.cpp`
  and `prefs.cpp` differ by one line each (`localtime_r`; `/` path separator).
- **Same-logic files with a platform section**: `app.h` (no `<windows.h>`),
  `util.cpp` (POSIX clock/files, hand-rolled UTF-8↔UTF-32), `db.cpp` (POSIX file
  copy, `strcasecmp`, `/` separators), `anthropic.cpp` (libcurl transport instead
  of WinHTTP — everything else, including the `lookup_food` tool loop and cost
  math, is identical).
- **Platform files**: `main.mm` (NSApplication + Metal + MTKView instead of
  WinMain + D3D11; the per-frame body matches 1:1), `imageutil.mm` (ImageIO
  decode/scale/JPEG + `NSOpenPanel` pickers instead of WIC + `IFileDialog`).
- **Vendor**: Dear ImGui core, the SQLite amalgamation and nlohmann/json are
  compiled straight from `../winport/vendor` (single pinned copy for both ports,
  ImGui 1.92.9). Only the Apple backends (`imgui_impl_osx.*`, `imgui_impl_metal.*`,
  same 1.92.9 tag) live in `macport/vendor/`.
- **Assets** (`foods.db` + the AI system prompts) are copied out of
  `../winport/assets` into the app bundle at build time, so there is a single
  source of truth. Only the mac icon is mac-specific: `assets/DietSentry.icns` is
  a Big Sur-style rendition of the DietSentry motif (dinner plate + nutrition
  bars on Material purple), drawn natively by `assets/draw_icon.m`. To
  regenerate: compile it (`clang -fobjc-arc draw_icon.m -framework Foundation
  -framework CoreGraphics -framework ImageIO -framework UniformTypeIdentifiers
  -o draw_icon`), render a 1024-px PNG, build a `.iconset` with `sips`, and run
  `iconutil -c icns`.

When changing app behaviour in one port, make the matching change in the other
(and in the Android app) — see the repo-level `CLAUDE.md`.

## Implementation notes

- **UI**: [Dear ImGui](https://github.com/ocornut/imgui) (MIT) rendered via Metal
  in an `MTKView`, styled to Material 3 light (same look family as the Compose
  app). Fonts: Arial / Arial Bold / Menlo from the system, with Apple Symbols
  merged in for glyphs (arrows, ⚙, ➤). Retina sharpness comes from ImGui 1.92's
  DPI-aware font rasterizer.
- **Database**: SQLite amalgamation compiled in; port of `DatabaseHelper.kt` in
  `src/db.cpp`.
- **Networking**: the system libcurl against `api.anthropic.com/v1/messages` —
  same request/response handling as the Kotlin `callAnthropicApi`, including the
  client-side `lookup_food` tool loop (max 12 iterations) on a worker thread.
- **Images**: attachments are decoded/EXIF-rotated/downscaled (≤1568 px) and
  JPEG-encoded via ImageIO, matching `loadImageForAi`.
- **Dates**: `DateEaten`/`DateWeight` strings are written in the exact dialect
  Android's `SimpleDateFormat("d-MMM-yy")` produces under en_AU — three-letter
  months except *June*, *July* and *Sept* spelled out (`30-July-26`) — so rows
  written on any of the three apps are byte-identical and daily totals group
  correctly. Parsing is tolerant: any ≥3-letter month prefix with optional
  trailing period (`Jul`, `July`, `Sept`, `Dec.`, …), so databases from phones in
  other locales still filter/sort/graph correctly. The month tables and
  `parseDMMMYY` in `src/util.cpp` are identical to the Windows port's.
- The keyboard **Esc** key acts as the Android system Back button (clears the
  selection first, then leaves the screen). **Cmd+Q** quits.
- Text fields accept **Ctrl+V** as paste in addition to the native **Cmd+V** —
  a convenience for driving the Mac over VNC/remote desktop from a Windows
  keyboard, where Ctrl+V arrives as literal Control+V. Supporting this,
  `vendor/imgui_impl_osx.mm` carries a small marked patch that syncs modifier
  state from key events too, because VNC servers inject synthetic keystrokes
  with embedded modifier flags and often no `NSEventTypeFlagsChanged`
  (re-apply the `[DietSentry patch]` block if the backend is ever upgraded).
- While an `NSOpenPanel` is up, the MTKView render loop is paused
  (`macSetRenderPaused`) so the modal run loop can't re-enter an ImGui frame.
- `DIETSENTRY_AUTONAV=<route>` environment variable opens a screen directly at
  launch (used for testing). Recognised routes — the same eight the Windows port
  accepts: `eatenLog`, `utilities`, `eatenGraph`, `addFoodByJson`, `addFoodByAi`,
  `addRecipe`, `insertFood`, and `editFirst` (opens Edit Food on the
  lowest-numbered food). Unknown values are ignored and the app starts on the
  Foods Table as usual. Run the binary inside the bundle to pass it:
  `DIETSENTRY_AUTONAV=eatenGraph build/DietSentry.app/Contents/MacOS/DietSentry`

## Source layout

```
macport/
├── build.sh                one-step build script (produces build/DietSentry.app)
├── Info.plist              app bundle metadata
├── assets/                 DietSentry.icns (db + prompts come from ../winport/assets)
├── vendor/                 ImGui Apple backends only (core vendored in ../winport/vendor)
└── src/
    ├── main.mm             NSApplication, Metal/ImGui bootstrap, navigation host
    ├── app.h               models, constants, App/Screen declarations
    ├── util.cpp            formatting, dates (d-MMM-yy etc.), marker helpers
    ├── db.cpp              DatabaseHelper port (SQLite)
    ├── prefs.cpp           SharedPreferences port (JSON file)
    ├── ui.h / ui.cpp       Material-ish widgets, pickers, dialogs, virtual lists
    ├── markdown.cpp        markdown renderer for help + chat
    ├── helptexts.*         in-app help texts (from MainActivity.kt)
    ├── dialogs.h           amount/date-time dialog helpers
    ├── anthropic.*         Messages API client + tool loop (libcurl)
    ├── imageutil.*         ImageIO load/scale/encode + NSOpenPanel pickers
    └── screens_*.cpp       one file per screen, mirroring the Kotlin composables
```
