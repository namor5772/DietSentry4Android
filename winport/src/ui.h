// ui.h — shared widgets giving ImGui a Material-3-ish look that mirrors the
// Android app's Compose UI.
#pragma once
#include "app.h"

namespace ui {

// Material 3 baseline (light) palette
static const ImU32 COL_PRIMARY            = IM_COL32(0x67, 0x50, 0xA4, 0xFF);
static const ImU32 COL_ON_PRIMARY         = IM_COL32(0xFF, 0xFF, 0xFF, 0xFF);
static const ImU32 COL_PRIMARY_CONTAINER  = IM_COL32(0xEA, 0xDD, 0xFF, 0xFF);
static const ImU32 COL_ON_PRIMARY_CONTAINER = IM_COL32(0x21, 0x00, 0x5D, 0xFF);
static const ImU32 COL_SURFACE            = IM_COL32(0xFD, 0xF7, 0xFF, 0xFF);
static const ImU32 COL_SURFACE_VARIANT    = IM_COL32(0xE7, 0xE0, 0xEC, 0xFF);
static const ImU32 COL_ON_SURFACE         = IM_COL32(0x1D, 0x1B, 0x20, 0xFF);
static const ImU32 COL_ON_SURFACE_VARIANT = IM_COL32(0x49, 0x45, 0x4F, 0xFF);
static const ImU32 COL_OUTLINE            = IM_COL32(0x79, 0x74, 0x7E, 0xFF);
static const ImU32 COL_OUTLINE_VARIANT    = IM_COL32(0xCA, 0xC4, 0xD0, 0xFF);
static const ImU32 COL_CARD               = IM_COL32(0xF7, 0xF2, 0xFA, 0xFF);
static const ImU32 COL_ERROR              = IM_COL32(0xB3, 0x26, 0x1E, 0xFF);
static const ImU32 COL_ERROR_CONTAINER    = IM_COL32(0xF9, 0xDE, 0xDC, 0xFF);
static const ImU32 COL_SCRIM              = IM_COL32(0, 0, 0, 110);

void applyTheme(App& app);
float dp(float v);            // dp -> pixels using App::uiScale

// Font-size shortcuts (Compose typography equivalents)
float fsTitleLarge();
float fsTitleMedium();
float fsBody();               // bodyLarge/Medium blend used for most text
float fsBodySmall();
float fsLabel();

// ---------------------------------------------------------------------------
// Basic widgets
// ---------------------------------------------------------------------------
bool inputText(const char* id, std::string& value, const char* hint = nullptr,
               ImGuiInputTextFlags flags = 0, float width = 0);
// Word-wrapping text box that grows with its content, like Compose's TextField(minLines,
// maxLines): at least minLines tall, taller as the wrapped text needs, capped at maxLines
// (<= 0: no cap) beyond which it scrolls.
bool inputMultiline(const char* id, std::string& value, float width, int minLines,
                    const char* hint = nullptr, int maxLines = 0);
bool primaryButton(const char* label, const ImVec2& size = ImVec2(0, 0), bool enabled = true);
bool outlinedButton(const char* label, const ImVec2& size = ImVec2(0, 0), bool enabled = true);
bool textButton(const char* label, bool enabled = true);
bool iconTextButton(const char* label);                 // top-bar style borderless
bool checkboxM(const char* label, bool* v);
bool radioM(const char* label, bool selected);
bool switchM(const char* id, bool* v, bool enabled = true);
int  segmented3(const char* id, int current, const char* a = "Min", const char* b = "NIP", const char* c = "All");
bool chip(const char* label, bool selected);

// ---------------------------------------------------------------------------
// Keyboard navigation (ImGuiConfigFlags_NavEnableKeyboard is on in both ports:
// Tab / Shift+Tab step through controls, arrows move directionally, Enter or
// Space activates, Escape cancels). Standard widgets draw ImGui's focus ring
// themselves; the custom InvisibleButton-based widgets in this file call
// focusRing() so the keyboard focus is visible on them too.
// ---------------------------------------------------------------------------
// Draw the standard keyboard-focus ring around the last submitted item (only
// when that item is the nav focus and the cursor is visible). rounding < 0
// uses the current FrameRounding. Call after painting the item's own
// background so the ring stays on top.
void focusRing(float rounding = -1.0f);
// Draw the focus ring for an arbitrary item id/rect (used by virtualList once
// the row content has been painted over the row's hit target).
void focusRingFor(ImGuiID id, const ImVec2& min, const ImVec2& max, float rounding = -1.0f);
// Scrollable read-only text region (help sheet, AI explanation): the child
// takes keyboard focus on the frame it first appears so arrows / PageUp /
// PageDown / Home / End scroll it, and Tab hands focus back to the parent
// window's controls. Pair with endTextScroll().
void beginTextScroll(const char* id, const ImVec2& size);
void endTextScroll();
// Call right after beginDialog() in a *destructive* dialog (Delete …?): ImGui
// would otherwise focus the dialog's first item — the Confirm button — so a
// second Enter would delete. This leaves nothing focused on open; Tab/arrow
// then Enter confirms deliberately, Escape cancels.
void dialogNoDefaultFocus();

// label/value half-width row used across food + eaten displays
void nutrientRow(const char* label, double value);
void nutrientRowText(const char* label, const std::string& value);

// Top app bar. Draw first in every screen.
struct TopBarResult {
    bool helpClicked = false;
    bool navClicked = false;      // back or forward arrow
    bool settingsClicked = false;
    int segSelection = -1;        // -1 = unchanged
};
TopBarResult topBar(App& app, const char* title, bool withSegmented, int segValue,
                    bool navForward, bool withSettings = false);

// ---------------------------------------------------------------------------
// Cards / virtual list
// ---------------------------------------------------------------------------
// Cached per-item heights for manual list virtualization.
struct HeightCache {
    std::vector<float> heights;
    std::vector<float> prefix;   // prefix[i] = sum of heights+spacing before item i
    float width = -1;
    int revision = -1;
    bool valid(int count, float w, int rev) const {
        return (int)heights.size() == count && width == w && revision == rev;
    }
};

// Virtualized vertical list inside a child region. measure() must be cheap
// (it is only called when the cache is invalid). The list owns each row's hit
// target: it submits an InvisibleButton covering row i, then calls
// draw(i, w, pressed) with the cursor back at the row's top-left; `pressed` is
// true when the row was clicked or activated with Enter/Space while keyboard-
// focused. draw() paints background + content and must consume exactly the
// measured height. Keyboard: the child is nav-flattened so Tab/arrows cross
// its border; only one row is a Tab stop (the focused row, else the first
// visible one) so Tab steps *past* the list while Up/Down move through rows;
// a few rows beyond the viewport are submitted so arrow keys scroll it.
// focusRounding shapes the focus ring drawn over the focused row.
void virtualList(const char* id, const ImVec2& size, int count, float spacing,
                 HeightCache& cache, int revision,
                 const std::function<float(int, float)>& measure,
                 const std::function<void(int, float, bool)>& draw,
                 bool border = false, float focusRounding = 0.0f);

// Rounded card background painter for use inside virtualList::draw.
void cardBackground(const ImVec2& pos, const ImVec2& size, ImU32 color = COL_CARD);

// ---------------------------------------------------------------------------
// Dialog / popup helpers
// ---------------------------------------------------------------------------
// Standard AlertDialog-style modal. Call beginDialog; if it returns true the
// dialog content scope is open — draw content then call endDialog().
// Set *open=false to request closing; clicking outside also closes it when
// dismissOnOutside is true.
bool beginDialog(const char* id, bool* open, float width = 0, bool dismissOnOutside = true);
void endDialog();
void dialogTitle(App& app, const char* text, ImU32 color = COL_ON_SURFACE, bool center = false);

// Date picker (Material DatePickerDialog equivalent). Set *open=true to show.
// Returns true on OK with *dateMillis updated (local midnight preserved from
// the picked day; time-of-day of the original value is kept).
bool datePickerModal(App& app, const char* id, bool* open, long long* dateMillis);
bool timePickerModal(App& app, const char* id, bool* open, int* hour, int* minute);
bool dateRangePickerModal(App& app, const char* id, bool* open,
                          std::optional<long long>* startMillis,
                          std::optional<long long>* endMillis);

// Help bottom sheet with markdown body.
void helpBottomSheet(App& app, const char* id, bool* show, const std::string& markdown);

// ---------------------------------------------------------------------------
// Shared list renderers (FoodList.kt / RecipeList.kt / WeightList)
// ---------------------------------------------------------------------------
struct FoodListState {
    HeightCache cache;
    int revision = 0;            // bump when foods/mode change
};
// showNutritionalInfo == NIP or All ; showExtraNutrients == All
void foodList(App& app, const char* id, const ImVec2& size, const std::vector<Food>& foods,
              FoodListState& state, bool showNutritionalInfo, bool showExtraNutrients,
              const std::function<void(const Food&)>& onClicked);

void recipeList(App& app, const char* id, const ImVec2& size, const std::vector<RecipeItem>& recipes,
                FoodListState& state, std::optional<int> selectedRecipeId,
                const std::function<void(const RecipeItem&)>& onClicked);

// Markdown (markdown.cpp)
void renderMarkdown(App& app, const std::string& text);
void renderMarkdownWidth(App& app, const std::string& text, float width);

} // namespace ui
