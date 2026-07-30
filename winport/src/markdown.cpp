// markdown.cpp — lightweight commonmark-ish renderer covering the constructs
// the app's help texts and Claude's chat replies actually use: headings,
// paragraphs, bullet/ordered lists (nested), fenced code blocks, thematic
// breaks, blockquotes, bold / italic / inline code / strikethrough.
#include "ui.h"
#include <vector>
#include <string>

namespace ui {

// When > 0, constrains rendering width (used by chat bubbles); otherwise the
// current content region width is used.
static float g_mdWidth = 0;

static float mdAvailWidth() {
    return g_mdWidth > 0 ? g_mdWidth : ImGui::GetContentRegionAvail().x;
}

struct InlineTok {
    std::string text;
    bool bold = false, italic = false, code = false, strike = false;
};

// Parse inline markdown of a single logical line into styled tokens.
static std::vector<InlineTok> parseInline(const std::string& s) {
    std::vector<InlineTok> out;
    bool bold = false, italic = false, strike = false;
    std::string cur;
    auto flush = [&]() {
        if (!cur.empty()) {
            out.push_back({cur, bold, italic, false, strike});
            cur.clear();
        }
    };
    size_t i = 0;
    while (i < s.size()) {
        // escapes
        if (s[i] == '\\' && i + 1 < s.size()) {
            cur += s[i + 1];
            i += 2;
            continue;
        }
        if (s.compare(i, 2, "**") == 0) {
            flush(); bold = !bold; i += 2; continue;
        }
        if (s.compare(i, 2, "~~") == 0) {
            flush(); strike = !strike; i += 2; continue;
        }
        if (s[i] == '*') {
            flush(); italic = !italic; i += 1; continue;
        }
        if (s[i] == '`') {
            // inline code to next backtick
            size_t close = s.find('`', i + 1);
            if (close != std::string::npos) {
                flush();
                out.push_back({s.substr(i + 1, close - i - 1), false, false, true, false});
                i = close + 1;
                continue;
            }
        }
        // links: [text](url) -> render text
        if (s[i] == '[') {
            size_t closeBr = s.find(']', i + 1);
            if (closeBr != std::string::npos && closeBr + 1 < s.size() && s[closeBr + 1] == '(') {
                size_t closeP = s.find(')', closeBr + 2);
                if (closeP != std::string::npos) {
                    flush();
                    InlineTok t;
                    t.text = s.substr(i + 1, closeBr - i - 1);
                    out.push_back(t);
                    i = closeP + 1;
                    continue;
                }
            }
        }
        cur += s[i++];
    }
    flush();
    return out;
}

// Render styled tokens with word-wrapping starting at the current cursor.
static void renderInlineWrapped(App& app, const std::vector<InlineTok>& toks,
                                float fontSize, bool boldAll = false) {
    float wrapRight = ImGui::GetCursorPosX() + mdAvailWidth();
    float startX = ImGui::GetCursorPosX();
    bool lineStart = true;
    for (const auto& tok : toks) {
        ImFont* font = (tok.bold || boldAll) ? app.fontBold : app.fontRegular;
        // split into words, keeping trailing spaces
        size_t p = 0;
        while (p < tok.text.size()) {
            size_t sp = tok.text.find(' ', p);
            std::string word = sp == std::string::npos ? tok.text.substr(p)
                                                       : tok.text.substr(p, sp - p + 1);
            p = sp == std::string::npos ? tok.text.size() : sp + 1;
            if (word.empty()) continue;
            ImGui::PushFont(tok.code ? app.fontMono : font, tok.code ? fontSize * 0.92f : fontSize);
            std::string trimmed = word;
            while (!trimmed.empty() && trimmed.back() == ' ') trimmed.pop_back();
            ImVec2 sz = ImGui::CalcTextSize(trimmed.c_str());
            if (!lineStart && ImGui::GetCursorPosX() + sz.x > wrapRight) {
                ImGui::NewLine();
                ImGui::SetCursorPosX(startX);
                lineStart = true;
            }
            ImVec2 pos = ImGui::GetCursorScreenPos();
            if (tok.code) {
                ImVec2 full = ImGui::CalcTextSize(word.c_str());
                ImGui::GetWindowDrawList()->AddRectFilled(
                    ImVec2(pos.x - dp(1), pos.y - dp(1)),
                    ImVec2(pos.x + full.x + dp(1), pos.y + ImGui::GetTextLineHeight() + dp(1)),
                    IM_COL32(0xE7, 0xE0, 0xEC, 0xFF), dp(3));
            }
            ImGui::TextUnformatted(word.c_str());
            if (tok.strike || tok.italic) {
                ImVec2 endPos = ImGui::GetItemRectMax();
                if (tok.strike)
                    ImGui::GetWindowDrawList()->AddLine(
                        ImVec2(pos.x, pos.y + ImGui::GetTextLineHeight() * 0.55f),
                        ImVec2(endPos.x, pos.y + ImGui::GetTextLineHeight() * 0.55f),
                        COL_ON_SURFACE, dp(1));
                // italic: fake with slight underline-free skew is not possible; leave as-is
            }
            ImGui::PopFont();
            ImGui::SameLine(0, 0);
            lineStart = false;
        }
    }
    ImGui::NewLine();
}

static bool isThematicBreak(const std::string& t) {
    if (t.size() < 3) return false;
    char c = t[0];
    if (c != '*' && c != '-' && c != '_') return false;
    for (char ch : t)
        if (ch != c && ch != ' ') return false;
    return true;
}

void renderMarkdown(App& app, const std::string& text) {
    std::vector<std::string> lines = splitString(text, '\n');
    size_t i = 0;
    bool prevWasBlock = false;
    float spacing = dp(7);
    while (i < lines.size()) {
        std::string raw = lines[i];
        if (!raw.empty() && raw.back() == '\r') raw.pop_back();
        std::string t = trim(raw);

        if (t.empty()) { i++; continue; }
        if (prevWasBlock) ImGui::Dummy(ImVec2(0, spacing));
        prevWasBlock = true;

        // fenced code block
        if (t.rfind("```", 0) == 0) {
            std::string code;
            i++;
            while (i < lines.size()) {
                std::string cl = lines[i];
                if (!cl.empty() && cl.back() == '\r') cl.pop_back();
                if (trim(cl).rfind("```", 0) == 0) { i++; break; }
                code += cl + "\n";
                i++;
            }
            while (!code.empty() && (code.back() == '\n' || code.back() == ' ')) code.pop_back();
            ImVec2 pos = ImGui::GetCursorScreenPos();
            float w = mdAvailWidth();
            ImGui::PushFont(app.fontMono, fsBodySmall());
            ImVec2 sz = ImGui::CalcTextSize(code.c_str());
            float boxH = sz.y + dp(20);
            ImGui::GetWindowDrawList()->AddRectFilled(pos, ImVec2(pos.x + w, pos.y + boxH),
                                                      IM_COL32(0xE7, 0xE0, 0xEC, 0xFF), dp(8));
            ImGui::SetCursorScreenPos(ImVec2(pos.x + dp(10), pos.y + dp(10)));
            ImGui::TextUnformatted(code.c_str());
            ImGui::PopFont();
            ImGui::SetCursorScreenPos(ImVec2(pos.x, pos.y + boxH));
            ImGui::Dummy(ImVec2(0, 0));
            continue;
        }

        // thematic break
        if (isThematicBreak(t)) {
            ImGui::Separator();
            i++;
            continue;
        }

        // heading
        int level = 0;
        while (level < (int)t.size() && t[level] == '#') level++;
        if (level > 0 && level <= 6 && level < (int)t.size() && t[level] == ' ') {
            std::string content = trim(t.substr(level + 1));
            float size = level == 1 ? fsTitleLarge() : level == 2 ? fsTitleMedium()
                        : level == 3 ? dp(17) : fsBody();
            ImGui::PushFont(app.fontBold, size);
            auto toks = parseInline(content);
            renderInlineWrapped(app, toks, size, true);
            ImGui::PopFont();
            i++;
            continue;
        }

        // blockquote
        if (t[0] == '>') {
            std::string content = trim(t.substr(1));
            ImVec2 pos = ImGui::GetCursorScreenPos();
            ImGui::Indent(dp(12));
            auto toks = parseInline(content);
            renderInlineWrapped(app, toks, fsBody());
            ImGui::Unindent(dp(12));
            ImVec2 endPos = ImGui::GetCursorScreenPos();
            ImGui::GetWindowDrawList()->AddRectFilled(
                ImVec2(pos.x, pos.y), ImVec2(pos.x + dp(4), endPos.y - dp(4)),
                COL_OUTLINE_VARIANT, dp(2));
            i++;
            continue;
        }

        // list items (bullet or ordered), indentation-aware
        size_t indent = 0;
        while (indent < raw.size() && raw[indent] == ' ') indent++;
        std::string tl = trim(raw);
        bool bullet = tl.size() >= 2 && (tl[0] == '-' || tl[0] == '*' || tl[0] == '+') && tl[1] == ' ';
        size_t numLen = 0;
        while (numLen < tl.size() && isdigit((unsigned char)tl[numLen])) numLen++;
        bool ordered = numLen > 0 && numLen + 1 < tl.size() && tl[numLen] == '.' && tl[numLen + 1] == ' ';
        if (bullet || ordered) {
            int level2 = (int)(indent / 4);
            float indentPx = dp(4) + level2 * dp(14);
            std::string marker = bullet ? u8"•" : tl.substr(0, numLen) + ".";
            std::string content = bullet ? trim(tl.substr(2)) : trim(tl.substr(numLen + 2));
            // Continuation lines: subsequent lines that are more indented and
            // not themselves list items / blanks get appended.
            size_t j = i + 1;
            while (j < lines.size()) {
                std::string nraw = lines[j];
                if (!nraw.empty() && nraw.back() == '\r') nraw.pop_back();
                std::string nt = trim(nraw);
                if (nt.empty()) break;
                size_t nindent = 0;
                while (nindent < nraw.size() && nraw[nindent] == ' ') nindent++;
                bool nbullet = nt.size() >= 2 && (nt[0] == '-' || nt[0] == '*' || nt[0] == '+') && nt[1] == ' ';
                size_t nn = 0;
                while (nn < nt.size() && isdigit((unsigned char)nt[nn])) nn++;
                bool nordered = nn > 0 && nn + 1 < nt.size() && nt[nn] == '.' && nt[nn + 1] == ' ';
                if (nbullet || nordered || nindent <= indent || nt.rfind("```", 0) == 0 ||
                    nt[0] == '#' || isThematicBreak(nt)) break;
                content += " " + nt;
                j++;
            }
            i = j;
            ImGui::SetCursorPosX(ImGui::GetCursorPosX() + indentPx);
            ImGui::PushFont(app.fontRegular, fsBody());
            ImGui::TextUnformatted(marker.c_str());
            ImGui::PopFont();
            ImGui::SameLine();
            auto toks = parseInline(content);
            renderInlineWrapped(app, toks, fsBody());
            prevWasBlock = false; // tight list spacing
            ImGui::Dummy(ImVec2(0, dp(2)));
            continue;
        }

        // paragraph: join soft-wrapped lines
        std::string para = t;
        size_t j = i + 1;
        while (j < lines.size()) {
            std::string nraw = lines[j];
            if (!nraw.empty() && nraw.back() == '\r') nraw.pop_back();
            std::string nt = trim(nraw);
            if (nt.empty() || nt[0] == '#' || nt[0] == '>' || nt.rfind("```", 0) == 0 ||
                isThematicBreak(nt)) break;
            bool nbullet = nt.size() >= 2 && (nt[0] == '-' || nt[0] == '*' || nt[0] == '+') && nt[1] == ' ';
            size_t nn = 0;
            while (nn < nt.size() && isdigit((unsigned char)nt[nn])) nn++;
            bool nordered = nn > 0 && nn + 1 < nt.size() && nt[nn] == '.' && nt[nn + 1] == ' ';
            if (nbullet || nordered) break;
            para += " " + nt;
            j++;
        }
        i = j;
        auto toks = parseInline(para);
        renderInlineWrapped(app, toks, fsBody());
    }
}

void renderMarkdownWidth(App& app, const std::string& text, float width) {
    g_mdWidth = width;
    renderMarkdown(app, text);
    g_mdWidth = 0;
}

} // namespace ui
