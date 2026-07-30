#!/bin/bash
# Build DietSentry for macOS (self-contained DietSentry.app, no dependencies
# beyond the OS). Vendor code (Dear ImGui core, SQLite, nlohmann/json) is
# shared with the Windows port and compiled from ../winport/vendor; only the
# Apple ImGui backends live in ./vendor. Requires Xcode Command Line Tools.
set -e
cd "$(dirname "$0")"

WINVENDOR=../winport/vendor
OBJ=build/obj
APP=build/DietSentry.app
MIN=-mmacosx-version-min=11.0

CXX="clang++ -std=c++17 -O2 $MIN"
OBJCXX="clang++ -std=c++17 -O2 -fobjc-arc $MIN"

mkdir -p "$OBJ"

# --- SQLite amalgamation: compiled once, reused ---
if [ ! -f "$OBJ/sqlite3.o" ]; then
    echo "Compiling sqlite3.c ..."
    clang -c -O2 $MIN -DSQLITE_THREADSAFE=1 -DSQLITE_OMIT_LOAD_EXTENSION \
        "$WINVENDOR/sqlite3.c" -o "$OBJ/sqlite3.o"
fi

# --- Dear ImGui core + Apple backends: compiled once, reused ---
if [ ! -f "$OBJ/imgui.o" ]; then
    echo "Compiling Dear ImGui ..."
    for f in imgui imgui_draw imgui_tables imgui_widgets; do
        $CXX -c -I"$WINVENDOR" "$WINVENDOR/$f.cpp" -o "$OBJ/$f.o"
    done
    $OBJCXX -c -I"$WINVENDOR" -Ivendor vendor/imgui_impl_osx.mm -o "$OBJ/imgui_impl_osx.o"
    $OBJCXX -c -I"$WINVENDOR" -Ivendor vendor/imgui_impl_metal.mm -o "$OBJ/imgui_impl_metal.o"
fi

echo "Compiling DietSentry ..."
APPOBJS=()
for src in src/*.cpp; do
    out="$OBJ/$(basename "${src%.cpp}").o"
    $CXX -c -Wall -I"$WINVENDOR" -Ivendor -Isrc "$src" -o "$out"
    APPOBJS+=("$out")
done
for src in src/*.mm; do
    out="$OBJ/$(basename "${src%.mm}").o"
    $OBJCXX -c -Wall -I"$WINVENDOR" -Ivendor -Isrc "$src" -o "$out"
    APPOBJS+=("$out")
done

echo "Linking ..."
$CXX -o "$OBJ/DietSentry" \
    "${APPOBJS[@]}" \
    "$OBJ/sqlite3.o" "$OBJ/imgui.o" "$OBJ/imgui_draw.o" "$OBJ/imgui_tables.o" \
    "$OBJ/imgui_widgets.o" "$OBJ/imgui_impl_osx.o" "$OBJ/imgui_impl_metal.o" \
    -framework Cocoa -framework Metal -framework MetalKit -framework QuartzCore \
    -framework GameController -framework ImageIO -framework CoreGraphics \
    -framework UniformTypeIdentifiers \
    -lcurl

# --- App bundle: binary + Info.plist + icon + assets (shared with winport) ---
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources/assets"
cp "$OBJ/DietSentry" "$APP/Contents/MacOS/DietSentry"
cp Info.plist "$APP/Contents/Info.plist"
cp assets/DietSentry.icns "$APP/Contents/Resources/DietSentry.icns"
cp ../winport/assets/foods.db ../winport/assets/*.txt "$APP/Contents/Resources/assets/"

echo
echo "Build OK: $(pwd)/$APP"
echo "Run with: open \"$(pwd)/$APP\""
