// ui.cpp — Material-3-ish widget layer over Dear ImGui.
#include "ui.h"
#include "imgui_internal.h"
#include <map>
#include <algorithm>
#include <ctime>

namespace ui {

static App* g = nullptr;

float dp(float v) { return v * (g ? g->uiScale : 1.0f); }

float fsTitleLarge()  { return dp(23.0f); }
float fsTitleMedium() { return dp(18.0f); }
float fsBody()        { return dp(16.5f); }
float fsBodySmall()   { return dp(14.0f); }
float fsLabel()       { return dp(15.0f); }

void applyTheme(App& app) {
    g = &app;
    ImGuiStyle& s = ImGui::GetStyle();
    s.WindowPadding = ImVec2(0, 0);
    s.WindowBorderSize = 0;
    s.WindowRounding = 0;
    s.FrameRounding = dp(8);
    s.FramePadding = ImVec2(dp(10), dp(7));
    s.ItemSpacing = ImVec2(dp(8), dp(6));
    s.ItemInnerSpacing = ImVec2(dp(6), dp(4));
    s.ChildRounding = dp(12);
    s.PopupRounding = dp(16);
    s.ScrollbarSize = dp(12);
    s.ScrollbarRounding = dp(6);
    s.GrabRounding = dp(6);
    s.CellPadding = ImVec2(dp(4), dp(2));

    ImVec4* c = s.Colors;
    auto v4 = [](ImU32 u) { return ImGui::ColorConvertU32ToFloat4(u); };
    c[ImGuiCol_Text] = v4(COL_ON_SURFACE);
    c[ImGuiCol_TextDisabled] = v4(COL_OUTLINE);
    c[ImGuiCol_WindowBg] = v4(COL_SURFACE);
    c[ImGuiCol_ChildBg] = ImVec4(0, 0, 0, 0);
    c[ImGuiCol_PopupBg] = v4(IM_COL32(0xEC, 0xE6, 0xF0, 0xFF));
    c[ImGuiCol_Border] = v4(COL_OUTLINE_VARIANT);
    c[ImGuiCol_FrameBg] = v4(IM_COL32(0xE6, 0xE0, 0xE9, 0xFF));
    c[ImGuiCol_FrameBgHovered] = v4(IM_COL32(0xDE, 0xD8, 0xE1, 0xFF));
    c[ImGuiCol_FrameBgActive] = v4(IM_COL32(0xD6, 0xD0, 0xDA, 0xFF));
    c[ImGuiCol_ScrollbarBg] = ImVec4(0, 0, 0, 0);
    c[ImGuiCol_ScrollbarGrab] = v4(IM_COL32(0xC4, 0xC0, 0xCA, 0xB0));
    c[ImGuiCol_ScrollbarGrabHovered] = v4(IM_COL32(0xA8, 0xA2, 0xB0, 0xE0));
    c[ImGuiCol_ScrollbarGrabActive] = v4(IM_COL32(0x93, 0x8F, 0x99, 0xFF));
    c[ImGuiCol_CheckMark] = v4(COL_PRIMARY);
    c[ImGuiCol_Button] = v4(COL_PRIMARY);
    c[ImGuiCol_ButtonHovered] = v4(IM_COL32(0x73, 0x5C, 0xB0, 0xFF));
    c[ImGuiCol_ButtonActive] = v4(IM_COL32(0x5A, 0x44, 0x94, 0xFF));
    c[ImGuiCol_Header] = v4(COL_PRIMARY_CONTAINER);
    c[ImGuiCol_HeaderHovered] = v4(IM_COL32(0xDE, 0xD0, 0xF5, 0xFF));
    c[ImGuiCol_HeaderActive] = v4(COL_PRIMARY_CONTAINER);
    c[ImGuiCol_SliderGrab] = v4(COL_PRIMARY);
    c[ImGuiCol_SliderGrabActive] = v4(COL_PRIMARY);
    c[ImGuiCol_ModalWindowDimBg] = ImVec4(0, 0, 0, 0.40f);
    c[ImGuiCol_NavCursor] = ImVec4(0, 0, 0, 0);
}

// ---------------------------------------------------------------------------
// Text input backed by std::string
// ---------------------------------------------------------------------------
static int inputTextResizeCb(ImGuiInputTextCallbackData* data) {
    if (data->EventFlag == ImGuiInputTextFlags_CallbackResize) {
        std::string* str = (std::string*)data->UserData;
        str->resize(data->BufTextLen);
        data->Buf = (char*)str->c_str();
    }
    return 0;
}

bool inputText(const char* id, std::string& value, const char* hint,
               ImGuiInputTextFlags flags, float width) {
    ImGui::SetNextItemWidth(width != 0 ? width : -FLT_MIN);
    flags |= ImGuiInputTextFlags_CallbackResize;
    return ImGui::InputTextWithHint(id, hint ? hint : "", (char*)value.c_str(),
                                    value.capacity() + 1, flags, inputTextResizeCb, &value);
}

bool inputMultiline(const char* id, std::string& value, float width, int minLines, const char* hint) {
    float h = ImGui::GetTextLineHeight() * minLines + ImGui::GetStyle().FramePadding.y * 2;
    // WordWrap: long Description / Notes / Comments lines fold within the box instead of
    // scrolling sideways, matching the Android app's multi-line text fields.
    bool changed = ImGui::InputTextMultiline(id, (char*)value.c_str(), value.capacity() + 1,
                                             ImVec2(width != 0 ? width : -FLT_MIN, h),
                                             ImGuiInputTextFlags_CallbackResize | ImGuiInputTextFlags_WordWrap,
                                             inputTextResizeCb, &value);
    // Empty-field hint (InputTextMultiline has no built-in hint)
    if (hint && value.empty() && !ImGui::IsItemActive()) {
        ImVec2 min = ImGui::GetItemRectMin();
        ImGui::GetWindowDrawList()->AddText(
            ImVec2(min.x + ImGui::GetStyle().FramePadding.x, min.y + ImGui::GetStyle().FramePadding.y),
            COL_OUTLINE, hint);
    }
    return changed;
}

// ---------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------
bool primaryButton(const char* label, const ImVec2& size, bool enabled) {
    ImGui::PushStyleVar(ImGuiStyleVar_FrameRounding, dp(999));
    ImGui::PushStyleVar(ImGuiStyleVar_FramePadding, ImVec2(dp(16), dp(8)));
    ImGui::PushStyleColor(ImGuiCol_Text, COL_ON_PRIMARY);
    if (!enabled) ImGui::BeginDisabled();
    ImGui::PushFont(g->fontRegular, fsLabel());
    bool clicked = ImGui::Button(label, size);
    ImGui::PopFont();
    if (!enabled) ImGui::EndDisabled();
    ImGui::PopStyleColor();
    ImGui::PopStyleVar(2);
    return clicked;
}

bool outlinedButton(const char* label, const ImVec2& size, bool enabled) {
    ImGui::PushStyleVar(ImGuiStyleVar_FrameRounding, dp(999));
    ImGui::PushStyleVar(ImGuiStyleVar_FramePadding, ImVec2(dp(16), dp(8)));
    ImGui::PushStyleVar(ImGuiStyleVar_FrameBorderSize, 1.0f);
    ImGui::PushStyleColor(ImGuiCol_Button, IM_COL32(0, 0, 0, 0));
    ImGui::PushStyleColor(ImGuiCol_ButtonHovered, IM_COL32(0x67, 0x50, 0xA4, 0x14));
    ImGui::PushStyleColor(ImGuiCol_ButtonActive, IM_COL32(0x67, 0x50, 0xA4, 0x28));
    ImGui::PushStyleColor(ImGuiCol_Text, COL_PRIMARY);
    ImGui::PushStyleColor(ImGuiCol_Border, COL_OUTLINE);
    if (!enabled) ImGui::BeginDisabled();
    ImGui::PushFont(g->fontRegular, fsLabel());
    bool clicked = ImGui::Button(label, size);
    ImGui::PopFont();
    if (!enabled) ImGui::EndDisabled();
    ImGui::PopStyleColor(5);
    ImGui::PopStyleVar(3);
    return clicked;
}

bool textButton(const char* label, bool enabled) {
    ImGui::PushStyleColor(ImGuiCol_Button, IM_COL32(0, 0, 0, 0));
    ImGui::PushStyleColor(ImGuiCol_ButtonHovered, IM_COL32(0x67, 0x50, 0xA4, 0x14));
    ImGui::PushStyleColor(ImGuiCol_ButtonActive, IM_COL32(0x67, 0x50, 0xA4, 0x28));
    ImGui::PushStyleColor(ImGuiCol_Text, COL_PRIMARY);
    if (!enabled) ImGui::BeginDisabled();
    ImGui::PushFont(g->fontRegular, fsLabel());
    bool clicked = ImGui::Button(label);
    ImGui::PopFont();
    if (!enabled) ImGui::EndDisabled();
    ImGui::PopStyleColor(4);
    return clicked;
}

bool iconTextButton(const char* label) {
    ImGui::PushStyleColor(ImGuiCol_Button, IM_COL32(0, 0, 0, 0));
    ImGui::PushStyleColor(ImGuiCol_ButtonHovered, IM_COL32(0x1D, 0x1B, 0x20, 0x10));
    ImGui::PushStyleColor(ImGuiCol_ButtonActive, IM_COL32(0x1D, 0x1B, 0x20, 0x20));
    ImGui::PushStyleColor(ImGuiCol_Text, COL_ON_SURFACE);
    ImGui::PushStyleVar(ImGuiStyleVar_FrameRounding, dp(999));
    bool clicked = ImGui::Button(label);
    ImGui::PopStyleVar();
    ImGui::PopStyleColor(4);
    return clicked;
}

bool checkboxM(const char* label, bool* v) {
    ImGui::PushStyleColor(ImGuiCol_FrameBg, IM_COL32(0xFF, 0xFF, 0xFF, 0xFF));
    ImGui::PushStyleColor(ImGuiCol_Border, COL_OUTLINE);
    ImGui::PushStyleVar(ImGuiStyleVar_FrameBorderSize, dp(1.5f));
    ImGui::PushStyleVar(ImGuiStyleVar_FrameRounding, dp(4));
    bool changed = ImGui::Checkbox(label, v);
    ImGui::PopStyleVar(2);
    ImGui::PopStyleColor(2);
    return changed;
}

bool radioM(const char* label, bool selected) {
    ImGui::PushStyleColor(ImGuiCol_FrameBg, IM_COL32(0xFF, 0xFF, 0xFF, 0xFF));
    ImGui::PushStyleColor(ImGuiCol_Border, COL_OUTLINE);
    ImGui::PushStyleVar(ImGuiStyleVar_FrameBorderSize, dp(1.5f));
    bool clicked = ImGui::RadioButton(label, selected);
    ImGui::PopStyleVar();
    ImGui::PopStyleColor(2);
    return clicked;
}

bool switchM(const char* id, bool* v, bool enabled) {
    float h = dp(24), w = dp(44);
    ImVec2 pos = ImGui::GetCursorScreenPos();
    if (!enabled) ImGui::BeginDisabled();
    ImGui::InvisibleButton(id, ImVec2(w, h));
    bool clicked = ImGui::IsItemClicked();
    if (clicked && enabled) *v = !*v;
    if (!enabled) ImGui::EndDisabled();
    ImDrawList* dl = ImGui::GetWindowDrawList();
    float alpha = enabled ? 1.0f : 0.45f;
    ImU32 track = *v ? COL_PRIMARY : IM_COL32(0xE6, 0xE0, 0xE9, 0xFF);
    ImU32 border = *v ? COL_PRIMARY : COL_OUTLINE;
    ImU32 thumb = *v ? IM_COL32(0xFF, 0xFF, 0xFF, 0xFF) : COL_OUTLINE;
    auto withAlpha = [&](ImU32 col) {
        ImVec4 f = ImGui::ColorConvertU32ToFloat4(col); f.w *= alpha;
        return ImGui::ColorConvertFloat4ToU32(f);
    };
    dl->AddRectFilled(pos, ImVec2(pos.x + w, pos.y + h), withAlpha(track), h * 0.5f);
    dl->AddRect(pos, ImVec2(pos.x + w, pos.y + h), withAlpha(border), h * 0.5f, 0, dp(1.5f));
    float r = *v ? h * 0.38f : h * 0.28f;
    float cx = *v ? pos.x + w - h * 0.5f : pos.x + h * 0.5f;
    dl->AddCircleFilled(ImVec2(cx, pos.y + h * 0.5f), r, withAlpha(thumb));
    return clicked && enabled;
}

int segmented3(const char* id, int current, const char* a, const char* b, const char* c) {
    const char* labels[3] = {a, b, c};
    float segW = dp(58), h = dp(32);
    ImVec2 pos = ImGui::GetCursorScreenPos();
    ImDrawList* dl = ImGui::GetWindowDrawList();
    int clicked = -1;
    ImGui::PushID(id);
    for (int i = 0; i < 3; i++) {
        ImGui::SetCursorScreenPos(ImVec2(pos.x + segW * i, pos.y));
        ImGui::PushID(i);
        if (ImGui::InvisibleButton("seg", ImVec2(segW, h))) clicked = i;
        bool hovered = ImGui::IsItemHovered();
        ImVec2 p0(pos.x + segW * i, pos.y), p1(pos.x + segW * (i + 1), pos.y + h);
        ImDrawFlags corners = i == 0 ? ImDrawFlags_RoundCornersLeft
                              : i == 2 ? ImDrawFlags_RoundCornersRight : ImDrawFlags_RoundCornersNone;
        if (i == current)
            dl->AddRectFilled(p0, p1, IM_COL32(0xE8, 0xDE, 0xF8, 0xFF), h * 0.5f, corners);
        else if (hovered)
            dl->AddRectFilled(p0, p1, IM_COL32(0x1D, 0x1B, 0x20, 0x0C), h * 0.5f, corners);
        ImGui::PushFont(g->fontRegular, fsLabel());
        ImVec2 ts = ImGui::CalcTextSize(labels[i]);
        dl->AddText(ImVec2(p0.x + (segW - ts.x) * 0.5f, p0.y + (h - ts.y) * 0.5f),
                    COL_ON_SURFACE, labels[i]);
        ImGui::PopFont();
        ImGui::PopID();
    }
    dl->AddRect(pos, ImVec2(pos.x + segW * 3, pos.y + h), COL_OUTLINE, h * 0.5f, 0, dp(1));
    dl->AddLine(ImVec2(pos.x + segW, pos.y), ImVec2(pos.x + segW, pos.y + h), COL_OUTLINE, dp(1));
    dl->AddLine(ImVec2(pos.x + segW * 2, pos.y), ImVec2(pos.x + segW * 2, pos.y + h), COL_OUTLINE, dp(1));
    ImGui::PopID();
    ImGui::SetCursorScreenPos(ImVec2(pos.x + segW * 3, pos.y));
    ImGui::Dummy(ImVec2(0, h));
    return clicked;
}

bool chip(const char* label, bool selected) {
    ImGui::PushStyleVar(ImGuiStyleVar_FrameRounding, dp(8));
    ImGui::PushStyleVar(ImGuiStyleVar_FramePadding, ImVec2(dp(12), dp(6)));
    ImGui::PushStyleVar(ImGuiStyleVar_FrameBorderSize, selected ? 0.0f : 1.0f);
    ImGui::PushStyleColor(ImGuiCol_Button, selected ? IM_COL32(0xE8, 0xDE, 0xF8, 0xFF) : IM_COL32(0, 0, 0, 0));
    ImGui::PushStyleColor(ImGuiCol_ButtonHovered, selected ? IM_COL32(0xDE, 0xD0, 0xF5, 0xFF) : IM_COL32(0x1D, 0x1B, 0x20, 0x0C));
    ImGui::PushStyleColor(ImGuiCol_ButtonActive, IM_COL32(0xD0, 0xC2, 0xE8, 0xFF));
    ImGui::PushStyleColor(ImGuiCol_Text, selected ? IM_COL32(0x1D, 0x19, 0x2B, 0xFF) : COL_ON_SURFACE_VARIANT);
    ImGui::PushStyleColor(ImGuiCol_Border, COL_OUTLINE);
    ImGui::PushFont(g->fontRegular, fsLabel());
    bool clicked = ImGui::Button(label);
    ImGui::PopFont();
    ImGui::PopStyleColor(5);
    ImGui::PopStyleVar(3);
    return clicked;
}

void nutrientRow(const char* label, double value) {
    nutrientRowText(label, formatNumber(value));
}

void nutrientRowText(const char* label, const std::string& value) {
    float rowW = ImGui::GetContentRegionAvail().x * 0.5f;
    ImGui::PushFont(g->fontRegular, fsBodySmall());
    float startX = ImGui::GetCursorPosX();
    ImGui::TextUnformatted(label);
    ImVec2 vs = ImGui::CalcTextSize(value.c_str());
    ImGui::SameLine();
    ImGui::SetCursorPosX(startX + rowW - vs.x);
    ImGui::TextUnformatted(value.c_str());
    ImGui::PopFont();
}

// ---------------------------------------------------------------------------
// Top app bar
// ---------------------------------------------------------------------------
TopBarResult topBar(App& app, const char* title, bool withSegmented, int segValue,
                    bool navForward, bool withSettings) {
    TopBarResult res;
    bool twoLine = strchr(title, '\n') != nullptr;
    float h = dp(twoLine ? 64.f : 56.f);
    ImVec2 pos = ImGui::GetCursorScreenPos();
    float winW = ImGui::GetContentRegionAvail().x;
    ImDrawList* dl = ImGui::GetWindowDrawList();
    dl->AddRectFilled(pos, ImVec2(pos.x + winW, pos.y + h), COL_SURFACE);

    // Title
    ImGui::PushFont(app.fontBold, twoLine ? dp(17.f) : fsTitleLarge());
    ImVec2 ts = ImGui::CalcTextSize(title);
    dl->AddText(app.fontBold, twoLine ? dp(17.f) : fsTitleLarge(),
                ImVec2(pos.x + dp(14), pos.y + (h - ts.y) * 0.5f), COL_ON_SURFACE, title);
    ImGui::PopFont();

    // Right-aligned actions, laid out right-to-left
    float x = pos.x + winW - dp(8);
    float btnW = dp(34);
    auto actionButton = [&](const char* id, const char* glyph) -> bool {
        x -= btnW;
        ImGui::SetCursorScreenPos(ImVec2(x, pos.y + (h - dp(30)) * 0.5f));
        ImGui::PushStyleColor(ImGuiCol_Button, IM_COL32(0, 0, 0, 0));
        ImGui::PushStyleColor(ImGuiCol_ButtonHovered, IM_COL32(0x1D, 0x1B, 0x20, 0x12));
        ImGui::PushStyleColor(ImGuiCol_ButtonActive, IM_COL32(0x1D, 0x1B, 0x20, 0x22));
        ImGui::PushStyleColor(ImGuiCol_Text, COL_ON_SURFACE);
        ImGui::PushStyleVar(ImGuiStyleVar_FrameRounding, dp(999));
        ImGui::PushStyleVar(ImGuiStyleVar_FramePadding, ImVec2(dp(6), dp(5)));
        ImGui::PushFont(app.fontRegular, dp(17));
        bool clicked = ImGui::Button(glyph, ImVec2(dp(30), dp(30)));
        ImGui::PopFont();
        ImGui::PopStyleVar(2);
        ImGui::PopStyleColor(4);
        (void)id;
        return clicked;
    };

    if (actionButton("nav", navForward ? u8"→" : u8"←")) res.navClicked = true;
    if (actionButton("help", "?")) res.helpClicked = true;
    if (withSettings && actionButton("settings", u8"⚙")) res.settingsClicked = true;
    if (withSegmented) {
        float segTotal = dp(58) * 3;
        x -= segTotal + dp(6);
        ImGui::SetCursorScreenPos(ImVec2(x, pos.y + (h - dp(32)) * 0.5f));
        int seg = segmented3("topseg", segValue);
        if (seg >= 0) res.segSelection = seg;
    }

    ImGui::SetCursorScreenPos(ImVec2(pos.x, pos.y + h));
    ImGui::Dummy(ImVec2(0, 0));
    return res;
}

// ---------------------------------------------------------------------------
// Virtual list
// ---------------------------------------------------------------------------
void virtualList(const char* id, const ImVec2& size, int count, float spacing,
                 HeightCache& cache, int revision,
                 const std::function<float(int, float)>& measure,
                 const std::function<void(int, float)>& draw,
                 bool border) {
    ImGui::PushStyleVar(ImGuiStyleVar_ChildRounding, 0.0f);
    ImGui::PushStyleColor(ImGuiCol_Border, IM_COL32(0x80, 0x80, 0x80, 0xFF));
    ImGui::BeginChild(id, size, border ? ImGuiChildFlags_Borders : ImGuiChildFlags_None);
    ImGui::PopStyleColor();
    float w = ImGui::GetContentRegionAvail().x;
    if (!cache.valid(count, w, revision)) {
        cache.heights.resize(count);
        cache.prefix.resize(count + 1);
        float acc = 0;
        for (int i = 0; i < count; i++) {
            cache.prefix[i] = acc;
            cache.heights[i] = measure(i, w);
            acc += cache.heights[i] + spacing;
        }
        cache.prefix[count] = acc;
        cache.width = w;
        cache.revision = revision;
    }
    float total = count > 0 ? cache.prefix[count] - spacing : 0;
    float scrollY = ImGui::GetScrollY();
    float viewH = ImGui::GetWindowSize().y;
    int first = 0, last = count - 1;
    if (count > 0) {
        // binary search for first visible
        first = (int)(std::upper_bound(cache.prefix.begin(), cache.prefix.end(), scrollY) - cache.prefix.begin()) - 1;
        first = std::max(0, std::min(first, count - 1));
        last = first;
        while (last < count - 1 && cache.prefix[last + 1] < scrollY + viewH) last++;
    }
    float baseY = ImGui::GetCursorPosY();
    for (int i = first; i <= last && i < count; i++) {
        ImGui::SetCursorPosY(baseY + cache.prefix[i]);
        ImGui::PushID(i);
        draw(i, w);
        ImGui::PopID();
    }
    if (count > 0) {
        ImGui::SetCursorPosY(baseY + total);
        ImGui::Dummy(ImVec2(0, 0));
    }
    ImGui::EndChild();
    ImGui::PopStyleVar();
}

void cardBackground(const ImVec2& pos, const ImVec2& size, ImU32 color) {
    ImDrawList* dl = ImGui::GetWindowDrawList();
    dl->AddRectFilled(pos, ImVec2(pos.x + size.x, pos.y + size.y), color, dp(12));
    dl->AddRect(pos, ImVec2(pos.x + size.x, pos.y + size.y), IM_COL32(0, 0, 0, 14), dp(12));
}

// ---------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------
bool beginDialog(const char* id, bool* open, float width, bool dismissOnOutside) {
    if (*open && !ImGui::IsPopupOpen(id)) ImGui::OpenPopup(id);
    if (!*open && !ImGui::IsPopupOpen(id)) return false;

    ImGuiViewport* vp = ImGui::GetMainViewport();
    ImGui::SetNextWindowPos(ImVec2(vp->GetCenter().x, vp->GetCenter().y), ImGuiCond_Always, ImVec2(0.5f, 0.5f));
    float wdt = width > 0 ? width : std::min(dp(400.f), vp->Size.x - dp(32));
    ImGui::SetNextWindowSizeConstraints(ImVec2(wdt, 0), ImVec2(wdt, vp->Size.y * 0.92f));
    ImGui::PushStyleVar(ImGuiStyleVar_WindowPadding, ImVec2(dp(20), dp(18)));
    bool visible = ImGui::BeginPopupModal(id, nullptr,
        ImGuiWindowFlags_NoTitleBar | ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoMove |
        ImGuiWindowFlags_AlwaysAutoResize);
    ImGui::PopStyleVar();
    if (!visible) {
        // popup was closed by ImGui (Esc) — sync flag
        if (*open) *open = false;
        return false;
    }
    if (!*open) {
        ImGui::CloseCurrentPopup();
        ImGui::EndPopup();
        return false;
    }
    if (dismissOnOutside && !ImGui::IsWindowAppearing() && ImGui::IsMouseClicked(0)) {
        ImGuiContext& ctx = *ImGui::GetCurrentContext();
        ImGuiWindow* hovered = ctx.HoveredWindow;
        ImGuiWindow* self = ImGui::GetCurrentWindow();
        bool inside = hovered && (hovered == self || hovered->RootWindow == self);
        bool nestedPopupOpen = ctx.OpenPopupStack.Size > 0 &&
            ctx.OpenPopupStack.back().Window != self;
        if (!inside && !nestedPopupOpen) {
            *open = false;
            ImGui::CloseCurrentPopup();
            ImGui::EndPopup();
            return false;
        }
    }
    return true;
}

void endDialog() {
    ImGui::EndPopup();
}

void dialogTitle(App& app, const char* text, ImU32 color, bool center) {
    ImGui::PushFont(app.fontBold, fsTitleMedium());
    if (center) {
        float w = ImGui::GetContentRegionAvail().x;
        ImVec2 ts = ImGui::CalcTextSize(text);
        ImGui::SetCursorPosX(ImGui::GetCursorPosX() + (w - ts.x) * 0.5f);
    }
    ImGui::PushStyleColor(ImGuiCol_Text, color);
    ImGui::TextUnformatted(text);
    ImGui::PopStyleColor();
    ImGui::PopFont();
    ImGui::Spacing();
}

// ---------------------------------------------------------------------------
// Calendar / date pickers
// ---------------------------------------------------------------------------
struct CalState {
    int viewYear = 2026, viewMonth = 1;   // 1-based month
    long long selected = 0;
    std::optional<long long> rangeStart, rangeEnd;
    int hour = 0, minute = 0;
};
static std::map<ImGuiID, CalState>& calStates() {
    static std::map<ImGuiID, CalState> m;
    return m;
}

static const char* MONTH_NAMES[12] = {
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
};

// Draw the day grid; returns clicked day millis (local midnight) or nullopt.
static std::optional<long long> calendarGrid(App& app, CalState& st,
                                             const std::function<ImU32(long long)>& dayFill) {
    std::optional<long long> clicked;
    float cell = dp(38);
    ImGui::PushFont(app.fontRegular, fsBodySmall());
    static const char* dows[7] = {"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"};
    for (int i = 0; i < 7; i++) {
        if (i) ImGui::SameLine();
        ImVec2 p = ImGui::GetCursorScreenPos();
        ImVec2 ts = ImGui::CalcTextSize(dows[i]);
        ImGui::GetWindowDrawList()->AddText(ImVec2(p.x + (cell - ts.x) / 2, p.y), COL_ON_SURFACE_VARIANT, dows[i]);
        ImGui::Dummy(ImVec2(cell, ImGui::GetTextLineHeight()));
    }
    ImGui::PopFont();

    // First day-of-week of the month (0=Mon)
    tm t = {};
    t.tm_year = st.viewYear - 1900; t.tm_mon = st.viewMonth - 1; t.tm_mday = 1; t.tm_isdst = -1;
    time_t tt = mktime(&t);
    tm t2; localtime_r(&tt, &t2);
    int startDow = (t2.tm_wday + 6) % 7; // tm_wday: 0=Sun
    int days = daysInMonth(st.viewYear, st.viewMonth);
    long long today = localMidnight(nowMillis());

    int cellIdx = 0;
    for (int i = 0; i < startDow; i++) {
        if (cellIdx % 7 != 0) ImGui::SameLine();
        ImGui::Dummy(ImVec2(cell, cell));
        cellIdx++;
    }
    ImGui::PushFont(app.fontRegular, fsBody());
    for (int d = 1; d <= days; d++) {
        if (cellIdx % 7 != 0) ImGui::SameLine();
        long long dayMs = ymdToMillis(st.viewYear, st.viewMonth, d);
        ImVec2 pos = ImGui::GetCursorScreenPos();
        ImGui::PushID(d);
        if (ImGui::InvisibleButton("day", ImVec2(cell, cell))) clicked = dayMs;
        bool hovered = ImGui::IsItemHovered();
        ImDrawList* dl = ImGui::GetWindowDrawList();
        ImVec2 center(pos.x + cell / 2, pos.y + cell / 2);
        ImU32 fill = dayFill(dayMs);
        ImU32 textCol = COL_ON_SURFACE;
        if (fill != 0) {
            dl->AddCircleFilled(center, cell * 0.46f, fill);
            if (fill == COL_PRIMARY) textCol = COL_ON_PRIMARY;
        } else if (hovered) {
            dl->AddCircleFilled(center, cell * 0.46f, IM_COL32(0x1D, 0x1B, 0x20, 0x14));
        }
        if (dayMs == today && fill == 0)
            dl->AddCircle(center, cell * 0.46f, COL_PRIMARY, 0, dp(1.2f));
        char buf[8];
        snprintf(buf, sizeof(buf), "%d", d);
        ImVec2 ts = ImGui::CalcTextSize(buf);
        dl->AddText(ImVec2(center.x - ts.x / 2, center.y - ts.y / 2), textCol, buf);
        ImGui::PopID();
        cellIdx++;
    }
    ImGui::PopFont();
    return clicked;
}

static void calendarHeader(App& app, CalState& st) {
    char hdr[64];
    snprintf(hdr, sizeof(hdr), "%s %d", MONTH_NAMES[st.viewMonth - 1], st.viewYear);
    if (iconTextButton(u8"‹")) {   // ‹
        st.viewMonth--;
        if (st.viewMonth < 1) { st.viewMonth = 12; st.viewYear--; }
    }
    ImGui::SameLine();
    float w = ImGui::GetContentRegionAvail().x - dp(40);
    ImGui::PushFont(app.fontBold, fsBody());
    ImVec2 ts = ImGui::CalcTextSize(hdr);
    ImGui::SetCursorPosX(ImGui::GetCursorPosX() + (w - ts.x) * 0.5f);
    ImGui::TextUnformatted(hdr);
    ImGui::PopFont();
    ImGui::SameLine();
    ImGui::SetCursorPosX(ImGui::GetWindowWidth() - dp(52));
    if (iconTextButton(u8"›")) {   // ›
        st.viewMonth++;
        if (st.viewMonth > 12) { st.viewMonth = 1; st.viewYear++; }
    }
}

bool datePickerModal(App& app, const char* id, bool* open, long long* dateMillis) {
    if (*open && !ImGui::IsPopupOpen(id)) ImGui::OpenPopup(id);
    if (!*open && !ImGui::IsPopupOpen(id)) return false;
    ImGuiViewport* vp = ImGui::GetMainViewport();
    ImGui::SetNextWindowPos(vp->GetCenter(), ImGuiCond_Always, ImVec2(0.5f, 0.5f));
    ImGui::PushStyleVar(ImGuiStyleVar_WindowPadding, ImVec2(dp(18), dp(16)));
    bool visible = ImGui::BeginPopupModal(id, nullptr,
        ImGuiWindowFlags_NoTitleBar | ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoMove |
        ImGuiWindowFlags_AlwaysAutoResize);
    ImGui::PopStyleVar();
    if (!visible) { if (*open) *open = false; return false; }

    ImGuiID key = ImGui::GetID(id);
    CalState& st = calStates()[key];
    if (ImGui::IsWindowAppearing()) {
        long long base = *dateMillis > 0 ? *dateMillis : nowMillis();
        int y, m, d; millisToYmd(base, y, m, d);
        st.viewYear = y; st.viewMonth = m;
        st.selected = localMidnight(base);
    }

    calendarHeader(app, st);
    auto clicked = calendarGrid(app, st, [&](long long day) -> ImU32 {
        return day == st.selected ? COL_PRIMARY : 0;
    });
    if (clicked) st.selected = *clicked;

    ImGui::Spacing();
    bool confirmed = false;
    float bw = dp(86);
    ImGui::SetCursorPosX(ImGui::GetContentRegionAvail().x - bw * 2 + dp(4));
    if (textButton("Cancel")) { *open = false; ImGui::CloseCurrentPopup(); }
    ImGui::SameLine();
    if (primaryButton("OK")) {
        // preserve original time-of-day
        long long timeOfDay = *dateMillis > 0 ? (*dateMillis - localMidnight(*dateMillis)) : 0;
        *dateMillis = st.selected + timeOfDay;
        confirmed = true;
        *open = false;
        ImGui::CloseCurrentPopup();
    }
    ImGui::EndPopup();
    return confirmed;
}

bool timePickerModal(App& app, const char* id, bool* open, int* hour, int* minute) {
    if (*open && !ImGui::IsPopupOpen(id)) ImGui::OpenPopup(id);
    if (!*open && !ImGui::IsPopupOpen(id)) return false;
    ImGuiViewport* vp = ImGui::GetMainViewport();
    ImGui::SetNextWindowPos(vp->GetCenter(), ImGuiCond_Always, ImVec2(0.5f, 0.5f));
    ImGui::PushStyleVar(ImGuiStyleVar_WindowPadding, ImVec2(dp(22), dp(18)));
    bool visible = ImGui::BeginPopupModal(id, nullptr,
        ImGuiWindowFlags_NoTitleBar | ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoMove |
        ImGuiWindowFlags_AlwaysAutoResize);
    ImGui::PopStyleVar();
    if (!visible) { if (*open) *open = false; return false; }

    ImGuiID key = ImGui::GetID(id);
    CalState& st = calStates()[key];
    if (ImGui::IsWindowAppearing()) { st.hour = *hour; st.minute = *minute; }

    ImGui::PushFont(app.fontRegular, fsLabel());
    ImGui::TextColored(ImGui::ColorConvertU32ToFloat4(COL_ON_SURFACE_VARIANT), "Select Time");
    ImGui::PopFont();
    ImGui::Spacing();

    ImGui::PushFont(app.fontRegular, dp(26));
    ImGui::SetNextItemWidth(dp(84));
    if (ImGui::InputInt("##hh", &st.hour, 1, 1)) {
        if (st.hour < 0) st.hour = 23;
        if (st.hour > 23) st.hour = 0;
    }
    ImGui::SameLine();
    ImGui::TextUnformatted(":");
    ImGui::SameLine();
    ImGui::SetNextItemWidth(dp(84));
    if (ImGui::InputInt("##mm", &st.minute, 1, 1)) {
        if (st.minute < 0) st.minute = 59;
        if (st.minute > 59) st.minute = 0;
    }
    ImGui::PopFont();

    ImGui::Spacing();
    bool confirmed = false;
    ImGui::SetCursorPosX(ImGui::GetContentRegionAvail().x - dp(150));
    if (textButton("Cancel")) { *open = false; ImGui::CloseCurrentPopup(); }
    ImGui::SameLine();
    if (textButton("OK")) {
        *hour = std::clamp(st.hour, 0, 23);
        *minute = std::clamp(st.minute, 0, 59);
        confirmed = true;
        *open = false;
        ImGui::CloseCurrentPopup();
    }
    ImGui::EndPopup();
    return confirmed;
}

bool dateRangePickerModal(App& app, const char* id, bool* open,
                          std::optional<long long>* startMillis,
                          std::optional<long long>* endMillis) {
    if (*open && !ImGui::IsPopupOpen(id)) ImGui::OpenPopup(id);
    if (!*open && !ImGui::IsPopupOpen(id)) return false;
    ImGuiViewport* vp = ImGui::GetMainViewport();
    ImGui::SetNextWindowPos(vp->GetCenter(), ImGuiCond_Always, ImVec2(0.5f, 0.5f));
    ImGui::PushStyleVar(ImGuiStyleVar_WindowPadding, ImVec2(dp(18), dp(16)));
    bool visible = ImGui::BeginPopupModal(id, nullptr,
        ImGuiWindowFlags_NoTitleBar | ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoMove |
        ImGuiWindowFlags_AlwaysAutoResize);
    ImGui::PopStyleVar();
    if (!visible) { if (*open) *open = false; return false; }

    ImGuiID key = ImGui::GetID(id);
    CalState& st = calStates()[key];
    if (ImGui::IsWindowAppearing()) {
        st.rangeStart = *startMillis ? std::optional<long long>(localMidnight(**startMillis)) : std::nullopt;
        st.rangeEnd = *endMillis ? std::optional<long long>(localMidnight(**endMillis)) : std::nullopt;
        long long base = st.rangeStart.value_or(nowMillis());
        int y, m, d; millisToYmd(base, y, m, d);
        st.viewYear = y; st.viewMonth = m;
    }

    ImGui::PushFont(app.fontRegular, fsBodySmall());
    std::string rangeLabel = "Select range";
    if (st.rangeStart) {
        rangeLabel = formatDMMM(*st.rangeStart);
        rangeLabel += u8" — ";
        rangeLabel += st.rangeEnd ? formatDMMM(*st.rangeEnd) : "?";
    }
    ImGui::TextColored(ImGui::ColorConvertU32ToFloat4(COL_ON_SURFACE_VARIANT), "%s", rangeLabel.c_str());
    ImGui::PopFont();
    ImGui::Spacing();

    calendarHeader(app, st);
    auto clicked = calendarGrid(app, st, [&](long long day) -> ImU32 {
        if (st.rangeStart && day == *st.rangeStart) return COL_PRIMARY;
        if (st.rangeEnd && day == *st.rangeEnd) return COL_PRIMARY;
        if (st.rangeStart && st.rangeEnd && day > *st.rangeStart && day < *st.rangeEnd)
            return COL_PRIMARY_CONTAINER;
        return 0;
    });
    if (clicked) {
        if (!st.rangeStart || st.rangeEnd) {
            st.rangeStart = *clicked;
            st.rangeEnd.reset();
        } else if (*clicked < *st.rangeStart) {
            st.rangeStart = *clicked;
        } else {
            st.rangeEnd = *clicked;
        }
    }

    ImGui::Spacing();
    bool confirmed = false;
    ImGui::SetCursorPosX(ImGui::GetContentRegionAvail().x - dp(150));
    if (textButton("Cancel")) { *open = false; ImGui::CloseCurrentPopup(); }
    ImGui::SameLine();
    bool ok = st.rangeStart.has_value() && st.rangeEnd.has_value();
    if (textButton("OK", ok)) {
        *startMillis = st.rangeStart;
        *endMillis = st.rangeEnd;
        confirmed = true;
        *open = false;
        ImGui::CloseCurrentPopup();
    }
    ImGui::EndPopup();
    return confirmed;
}

// ---------------------------------------------------------------------------
// Help bottom sheet (ModalBottomSheet + MarkdownText)
// ---------------------------------------------------------------------------
void helpBottomSheet(App& app, const char* id, bool* show, const std::string& markdown) {
    if (!*show) return;
    ImGuiViewport* vp = ImGui::GetMainViewport();

    // Scrim window catching clicks
    ImGui::SetNextWindowPos(vp->Pos);
    ImGui::SetNextWindowSize(vp->Size);
    ImGui::PushStyleColor(ImGuiCol_WindowBg, ImVec4(0, 0, 0, 0.42f));
    ImGui::PushStyleVar(ImGuiStyleVar_WindowRounding, 0.0f);
    std::string scrimId = std::string(id) + "##scrim";
    ImGui::Begin(scrimId.c_str(), nullptr,
        ImGuiWindowFlags_NoTitleBar | ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoMove |
        ImGuiWindowFlags_NoScrollbar | ImGuiWindowFlags_NoSavedSettings | ImGuiWindowFlags_NoNav);
    bool scrimClicked = ImGui::IsWindowHovered() && ImGui::IsMouseClicked(0);
    ImGui::End();
    ImGui::PopStyleVar();
    ImGui::PopStyleColor();

    float sheetH = vp->Size.y * 0.88f;
    ImGui::SetNextWindowPos(ImVec2(vp->Pos.x, vp->Pos.y + vp->Size.y - sheetH));
    ImGui::SetNextWindowSize(ImVec2(vp->Size.x, sheetH));
    ImGui::PushStyleColor(ImGuiCol_WindowBg, ImGui::ColorConvertU32ToFloat4(IM_COL32(0xEC, 0xE6, 0xF0, 0xFF)));
    ImGui::PushStyleVar(ImGuiStyleVar_WindowRounding, dp(24));
    ImGui::PushStyleVar(ImGuiStyleVar_WindowPadding, ImVec2(dp(16), dp(10)));
    std::string sheetId = std::string(id) + "##sheet";
    ImGui::Begin(sheetId.c_str(), nullptr,
        ImGuiWindowFlags_NoTitleBar | ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoMove |
        ImGuiWindowFlags_NoCollapse | ImGuiWindowFlags_NoSavedSettings);
    ImGui::BringWindowToDisplayFront(ImGui::GetCurrentWindow());

    // drag handle
    float w = ImGui::GetContentRegionAvail().x;
    ImVec2 p = ImGui::GetCursorScreenPos();
    ImGui::GetWindowDrawList()->AddRectFilled(
        ImVec2(p.x + w / 2 - dp(20), p.y + dp(4)),
        ImVec2(p.x + w / 2 + dp(20), p.y + dp(8)),
        IM_COL32(0x79, 0x74, 0x7E, 0x66), dp(2));
    ImGui::Dummy(ImVec2(0, dp(14)));

    ImGui::BeginChild("##helpscroll", ImVec2(0, 0), ImGuiChildFlags_None);
    renderMarkdown(app, markdown);
    ImGui::Dummy(ImVec2(0, dp(24)));
    ImGui::EndChild();
    ImGui::End();
    ImGui::PopStyleVar(2);
    ImGui::PopStyleColor();

    if (scrimClicked || ImGui::IsKeyPressed(ImGuiKey_Escape)) *show = false;
}

// ---------------------------------------------------------------------------
// FoodList / RecipeList (FoodList.kt / RecipeList.kt)
// ---------------------------------------------------------------------------
static float nutrientRowHeight() {
    ImGui::PushFont(g->fontRegular, fsBodySmall());
    float h = ImGui::GetTextLineHeightWithSpacing();
    ImGui::PopFont();
    return h;
}

void foodList(App& app, const char* id, const ImVec2& size, const std::vector<Food>& foods,
              FoodListState& state, bool showNutritionalInfo, bool showExtraNutrients,
              const std::function<void(const Food&)>& onClicked) {
    float pad = dp(4);
    float rowH = nutrientRowHeight();
    auto measure = [&](int i, float w) -> float {
        const Food& f = foods[i];
        float wrapW = w - pad * 2;
        float spacingY = ImGui::GetStyle().ItemSpacing.y;
        ImGui::PushFont(showNutritionalInfo || showExtraNutrients ? app.fontBold : app.fontRegular, fsBody());
        float h = ImGui::CalcTextSize(f.foodDescription.c_str(), nullptr, false, wrapW).y + spacingY;
        ImGui::PopFont();
        int rows = 0;
        if (showNutritionalInfo || showExtraNutrients) rows = showExtraNutrients ? 23 : 8;
        h += rows * rowH;
        if (showExtraNutrients) {
            // notes row
            ImGui::PushFont(app.fontRegular, fsBodySmall());
            float labelW = ImGui::CalcTextSize("Notes: ").x;
            float notesH = f.notes.empty()
                ? ImGui::GetTextLineHeightWithSpacing()
                : ImGui::CalcTextSize(f.notes.c_str(), nullptr, false, wrapW - labelW).y + spacingY;
            ImGui::PopFont();
            h += notesH;
        }
        return h + pad * 2;
    };
    auto draw = [&](int i, float w) {
        const Food& f = foods[i];
        ImVec2 pos = ImGui::GetCursorScreenPos();
        float h = state.cache.heights[i];
        ImGui::InvisibleButton("item", ImVec2(w, h));
        bool clicked = ImGui::IsItemClicked();
        if (ImGui::IsItemHovered())
            ImGui::GetWindowDrawList()->AddRectFilled(pos, ImVec2(pos.x + w, pos.y + h),
                                                      IM_COL32(0x1D, 0x1B, 0x20, 0x0A));
        ImGui::SetCursorScreenPos(ImVec2(pos.x + pad, pos.y + pad));
        ImGui::BeginGroup();
        ImGui::PushFont(showNutritionalInfo || showExtraNutrients ? app.fontBold : app.fontRegular, fsBody());
        ImGui::PushTextWrapPos(ImGui::GetCursorPosX() + w - pad * 2);
        ImGui::TextUnformatted(f.foodDescription.c_str());
        ImGui::PopTextWrapPos();
        ImGui::PopFont();
        if (showNutritionalInfo || showExtraNutrients) {
            Nutrients n = f.n; // copy for accessor use
            nutrientRow("Energy (kJ):", n.energy());
            nutrientRow("Protein (g):", n.protein());
            nutrientRow("Fat, Total (g):", n.fatTotal());
            nutrientRow("- Saturated (g):", n.saturatedFat());
            if (showExtraNutrients) {
                nutrientRow("- Trans (mg):", n.transFat());
                nutrientRow("- Polyunsaturated (g):", n.polyunsaturatedFat());
                nutrientRow("- Monounsaturated (g):", n.monounsaturatedFat());
            }
            nutrientRow("Carbohydrate (g):", n.carbohydrate());
            nutrientRow("- Sugars (g):", n.sugars());
            if (showExtraNutrients) {
                nutrientRow("Sodium (mg):", n.sodiumNa());
                nutrientRow("Dietary Fibre (g):", n.dietaryFibre());
                nutrientRow("Calcium (mg):", n.calciumCa());
                nutrientRow("Potassium (mg):", n.potassiumK());
                nutrientRow("Thiamin B1 (mg):", n.thiaminB1());
                nutrientRow("Riboflavin B2 (mg):", n.riboflavinB2());
                nutrientRow("Niacin B3 (mg):", n.niacinB3());
                nutrientRow("Folate (ug):", n.folate());
                nutrientRow("Iron (mg):", n.ironFe());
                nutrientRow("Magnesium (mg):", n.magnesiumMg());
                nutrientRow("Vitamin C (mg):", n.vitaminC());
                nutrientRow("Caffeine (mg):", n.caffeine());
                nutrientRow("Cholesterol (mg):", n.cholesterol());
                nutrientRow("Alcohol (g):", n.alcohol());
                // Notes row
                ImGui::PushFont(app.fontRegular, fsBodySmall());
                ImGui::TextUnformatted("Notes:");
                ImGui::SameLine();
                ImGui::PushTextWrapPos(ImGui::GetCursorPosX() + w - pad * 2 - ImGui::GetCursorPosX());
                ImGui::TextUnformatted(f.notes.c_str());
                ImGui::PopTextWrapPos();
                ImGui::PopFont();
            } else {
                nutrientRow("Sodium (mg):", n.sodiumNa());
                nutrientRow("Dietary Fibre (g):", n.dietaryFibre());
            }
        }
        ImGui::EndGroup();
        if (clicked) onClicked(f);
    };
    int rev = state.revision * 8 + (showNutritionalInfo ? 1 : 0) + (showExtraNutrients ? 2 : 0);
    virtualList(id, size, (int)foods.size(), 0, state.cache, rev, measure, draw, true);
}

void recipeList(App& app, const char* id, const ImVec2& size, const std::vector<RecipeItem>& recipes,
                FoodListState& state, std::optional<int> selectedRecipeId,
                const std::function<void(const RecipeItem&)>& onClicked) {
    float pad = dp(8);
    float rowH = nutrientRowHeight();
    auto measure = [&](int i, float w) -> float {
        const RecipeItem& r = recipes[i];
        ImGui::PushFont(app.fontBold, fsBody());
        float h = ImGui::CalcTextSize(r.foodDescription.c_str(), nullptr, false, w - pad * 2).y +
                  ImGui::GetStyle().ItemSpacing.y;
        ImGui::PopFont();
        return h + rowH * 4 + pad * 2;
    };
    auto draw = [&](int i, float w) {
        const RecipeItem& r = recipes[i];
        ImVec2 pos = ImGui::GetCursorScreenPos();
        float h = state.cache.heights[i];
        ImGui::InvisibleButton("item", ImVec2(w, h));
        bool clicked = ImGui::IsItemClicked();
        if (ImGui::IsItemHovered())
            ImGui::GetWindowDrawList()->AddRectFilled(pos, ImVec2(pos.x + w, pos.y + h),
                                                      IM_COL32(0x1D, 0x1B, 0x20, 0x0A));
        ImGui::SetCursorScreenPos(ImVec2(pos.x + pad, pos.y + pad));
        ImGui::BeginGroup();
        bool selected = selectedRecipeId && *selectedRecipeId == r.recipeId;
        ImGui::PushStyleColor(ImGuiCol_Text, selected ? COL_PRIMARY : COL_ON_SURFACE);
        ImGui::PushFont(app.fontBold, fsBody());
        ImGui::PushTextWrapPos(ImGui::GetCursorPosX() + w - pad * 2);
        ImGui::TextUnformatted(r.foodDescription.c_str());
        ImGui::PopTextWrapPos();
        ImGui::PopFont();
        ImGui::PopStyleColor();
        std::string unit = isLiquidDescription(r.foodDescription) ? "mL" : "g";
        Nutrients n = r.n;
        nutrientRow(("Amount (" + unit + "):").c_str(), r.amount);
        nutrientRow("Energy (kJ):", n.energy());
        nutrientRow("Fat (g):", n.fatTotal());
        nutrientRow("Fibre (g):", n.dietaryFibre());
        ImGui::EndGroup();
        if (clicked) onClicked(r);
    };
    virtualList(id, size, (int)recipes.size(), 0, state.cache, state.revision, measure, draw, true);
}

} // namespace ui

// ---------------------------------------------------------------------------
// App methods that live close to the UI
// ---------------------------------------------------------------------------
void App::toast(const std::string& msg) {
    toasts.push_back({msg, ImGui::GetTime() + 2.4});
}

void App::drawToasts() {
    if (toasts.empty()) return;
    toasts.erase(std::remove_if(toasts.begin(), toasts.end(),
                                [](const Toast& t) { return ImGui::GetTime() > t.expiry; }),
                 toasts.end());
    if (toasts.empty()) return;
    ImGuiViewport* vp = ImGui::GetMainViewport();
    ImDrawList* dl = ImGui::GetForegroundDrawList();
    float y = vp->Pos.y + vp->Size.y - ui::dp(84);
    for (auto it = toasts.rbegin(); it != toasts.rend(); ++it) {
        double remain = it->expiry - ImGui::GetTime();
        float alpha = (float)std::min(1.0, remain / 0.35);
        ImGui::PushFont(fontRegular, ui::fsBodySmall());
        ImVec2 ts = ImGui::CalcTextSize(it->text.c_str(), nullptr, false, vp->Size.x * 0.8f);
        ImGui::PopFont();
        ImVec2 pad(ui::dp(16), ui::dp(10));
        ImVec2 boxSize(ts.x + pad.x * 2, ts.y + pad.y * 2);
        ImVec2 boxPos(vp->Pos.x + (vp->Size.x - boxSize.x) / 2, y - boxSize.y);
        dl->AddRectFilled(boxPos, ImVec2(boxPos.x + boxSize.x, boxPos.y + boxSize.y),
                          IM_COL32(0x32, 0x2F, 0x35, (int)(0xE8 * alpha)), ui::dp(999));
        dl->AddText(fontRegular, ui::fsBodySmall(), ImVec2(boxPos.x + pad.x, boxPos.y + pad.y),
                    IM_COL32(0xF5, 0xEF, 0xF7, (int)(0xFF * alpha)), it->text.c_str(), nullptr, ts.x + 1);
        y -= boxSize.y + ui::dp(8);
    }
}
