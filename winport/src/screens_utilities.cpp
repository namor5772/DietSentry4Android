// screens_utilities.cpp — UtilitiesScreen: export/import db, export csv
// (with a remembered exchange folder — the Windows equivalent of the Android
// SAF folder flow, using IFileDialog), Eaten Graph entry, Weight Table CRUD.
#include "app.h"
#include "ui.h"
#include "helptexts.h"
#include <shobjidl.h>
#include <algorithm>

namespace {

std::optional<std::wstring> pickFolder(const std::wstring& initial) {
    std::optional<std::wstring> result;
    IFileDialog* dlg = nullptr;
    if (FAILED(CoCreateInstance(CLSID_FileOpenDialog, nullptr, CLSCTX_INPROC_SERVER,
                                IID_PPV_ARGS(&dlg))))
        return result;
    DWORD opts = 0;
    dlg->GetOptions(&opts);
    dlg->SetOptions(opts | FOS_PICKFOLDERS | FOS_FORCEFILESYSTEM);
    if (!initial.empty()) {
        IShellItem* folderItem = nullptr;
        if (SUCCEEDED(SHCreateItemFromParsingName(initial.c_str(), nullptr, IID_PPV_ARGS(&folderItem)))) {
            dlg->SetFolder(folderItem);
            folderItem->Release();
        }
    }
    if (SUCCEEDED(dlg->Show(nullptr))) {
        IShellItem* item = nullptr;
        if (SUCCEEDED(dlg->GetResult(&item))) {
            PWSTR path = nullptr;
            if (SUCCEEDED(item->GetDisplayName(SIGDN_FILESYSPATH, &path))) {
                result = path;
                CoTaskMemFree(path);
            }
            item->Release();
        }
    }
    dlg->Release();
    return result;
}

std::string buildEatenDailyAllCsv(const std::vector<DailyTotals>& dailyTotalsIn,
                                  const std::vector<WeightEntry>& weights) {
    std::vector<std::string> lines;
    const char* header[] = {
        "Date", "My weight (kg)", "Comments", "Amount (g or mL)",
        "Energy (kJ):", "Protein (g):", "Fat, total (g):", "- Saturated (g):", "- Trans (mg):",
        "- Polyunsaturated (g):", "- Monounsaturated (g):", "Carbohydrate (g):", "- Sugars (g):",
        "Sodium (mg):", "Dietary Fibre (g):", "Calcium (mg):", "Potassium (mg):",
        "Thiamin B1 (mg):", "Riboflavin B2 (mg):", "Niacin B3 (mg):", "Folate (ug):",
        "Iron (mg):", "Magnesium (mg):", "Vitamin C (mg):", "Caffeine (mg):",
        "Cholesterol (mg):", "Alcohol (g):"
    };
    std::string headerLine;
    for (size_t i = 0; i < sizeof(header) / sizeof(header[0]); i++) {
        if (i) headerLine += ",";
        headerLine += csvCell(header[i]);
    }
    lines.push_back(headerLine);

    std::vector<DailyTotals> sorted = dailyTotalsIn;
    std::stable_sort(sorted.begin(), sorted.end(), [](const DailyTotals& a, const DailyTotals& b) {
        long long ta = parseDMMMYY(a.date).value_or(LLONG_MIN);
        long long tb = parseDMMMYY(b.date).value_or(LLONG_MIN);
        return ta > tb;
    });
    for (const auto& totals : sorted) {
        const WeightEntry* we = nullptr;
        for (const auto& w : weights)
            if (w.dateWeight == totals.date) { we = &w; break; }
        Nutrients n = totals.n;
        std::vector<std::string> row = {
            totals.date,
            we ? formatWeight(we->weight) : "NA",
            we ? we->comments : "",
            formatNumber(totals.amountEaten),
            formatNumber(n.energy()), formatNumber(n.protein()), formatNumber(n.fatTotal()),
            formatNumber(n.saturatedFat()), formatNumber(n.transFat()),
            formatNumber(n.polyunsaturatedFat()), formatNumber(n.monounsaturatedFat()),
            formatNumber(n.carbohydrate()), formatNumber(n.sugars()), formatNumber(n.sodiumNa()),
            formatNumber(n.dietaryFibre()), formatNumber(n.calciumCa()), formatNumber(n.potassiumK()),
            formatNumber(n.thiaminB1()), formatNumber(n.riboflavinB2()), formatNumber(n.niacinB3()),
            formatNumber(n.folate()), formatNumber(n.ironFe()), formatNumber(n.magnesiumMg()),
            formatNumber(n.vitaminC()), formatNumber(n.caffeine()), formatNumber(n.cholesterol()),
            formatNumber(n.alcohol())
        };
        std::string line;
        for (size_t i = 0; i < row.size(); i++) {
            if (i) line += ",";
            line += csvCell(row[i]);
        }
        lines.push_back(line);
    }
    std::string out;
    for (size_t i = 0; i < lines.size(); i++) {
        if (i) out += "\n";
        out += lines[i];
    }
    return out;
}

struct UtilitiesScreen : Screen {
    bool showHelp = false;
    bool showExportWarning = false;
    bool showImportWarning = false;
    bool showExportCsvDialog = false;
    std::wstring exchangeFolder;

    std::vector<WeightEntry> weightEntries;
    std::optional<WeightEntry> selectedWeight;
    bool showAddWeightDialog = false;
    std::optional<WeightEntry> editingWeight;
    std::optional<WeightEntry> deletingWeight;
    std::string weightInput, weightCommentsInput;
    long long weightDateMillis = 0;
    bool showWeightDatePicker = false;
    std::string editWeightInput, editWeightCommentsInput;
    long long editWeightDateMillis = 0;
    ui::HeightCache weightCache;
    int weightRevision = 0;

    explicit UtilitiesScreen(App& app) {
        std::string stored = app.prefs.getString(PREF_KEY_EXCHANGE_FOLDER, "");
        if (!stored.empty()) {
            std::wstring w = utf8ToWide(stored);
            DWORD attrs = GetFileAttributesW(w.c_str());
            if (attrs != INVALID_FILE_ATTRIBUTES && (attrs & FILE_ATTRIBUTE_DIRECTORY))
                exchangeFolder = w;
            else
                app.prefs.remove(PREF_KEY_EXCHANGE_FOLDER);
        }
        weightDateMillis = nowMillis();
        refreshWeights(app);
    }

    const char* route() const override { return "utilities"; }

    bool onBack(App& app) override {
        (void)app;
        if (selectedWeight) { selectedWeight.reset(); return true; }
        return false;
    }

    void refreshWeights(App& app) {
        weightEntries = app.db.readWeights();
        if (selectedWeight) {
            int id = selectedWeight->weightId;
            selectedWeight.reset();
            for (auto& w : weightEntries)
                if (w.weightId == id) { selectedWeight = w; break; }
        }
        weightRevision++;
    }

    static std::optional<double> parseWeightInput(const std::string& input) {
        std::string normalized;
        for (char c : trim(input)) {
            if (c == ' ') continue;
            normalized += c == ',' ? '.' : c;
        }
        return parseDouble(normalized);
    }

    bool ensureExchangeFolder(App& app) {
        if (!exchangeFolder.empty()) {
            DWORD attrs = GetFileAttributesW(exchangeFolder.c_str());
            if (attrs != INVALID_FILE_ATTRIBUTES && (attrs & FILE_ATTRIBUTE_DIRECTORY)) return true;
            exchangeFolder.clear();
            app.prefs.remove(PREF_KEY_EXCHANGE_FOLDER);
        }
        auto picked = pickFolder(L"");
        if (!picked) {
            app.toast("Folder selection cancelled");
            return false;
        }
        exchangeFolder = *picked;
        app.prefs.putString(PREF_KEY_EXCHANGE_FOLDER, wideToUtf8(exchangeFolder));
        return true;
    }

    void changeFolder(App& app) {
        auto picked = pickFolder(exchangeFolder);
        if (!picked) {
            app.toast("Folder selection cancelled");
            return;
        }
        exchangeFolder = *picked;
        app.prefs.putString(PREF_KEY_EXCHANGE_FOLDER, wideToUtf8(exchangeFolder));
    }

    std::string displayPath(const wchar_t* fileName) const {
        if (exchangeFolder.empty()) return "";
        return wideToUtf8(exchangeFolder + L"\\" + fileName);
    }

    void draw(App& app) override {
        auto bar = ui::topBar(app, "Utilities", false, 0, false);
        if (bar.helpClicked) showHelp = true;
        if (bar.navClicked) app.pop();
        if (ImGui::IsKeyPressed(ImGuiKey_Escape) && !showHelp && !anyDialogOpen())
            app.requestBack();

        ImGui::SetCursorPosX(ui::dp(16));
        float w = ImGui::GetContentRegionAvail().x - ui::dp(16);

        // Export / Import / Export csv row
        float bw = (w - 2 * ui::dp(8)) / 3;
        if (ui::primaryButton("Export db", ImVec2(bw, 0))) {
            if (ensureExchangeFolder(app)) showExportWarning = true;
        }
        ImGui::SameLine(0, ui::dp(8));
        if (ui::primaryButton("Import db", ImVec2(bw, 0))) {
            if (ensureExchangeFolder(app)) {
                std::wstring src = exchangeFolder + L"\\" + utf8ToWide(DATABASE_FILE_NAME);
                if (!fileExists(src)) {
                    app.toast(std::string("No ") + DATABASE_FILE_NAME + " found in " +
                              wideToUtf8(exchangeFolder));
                } else {
                    showImportWarning = true;
                }
            }
        }
        ImGui::SameLine(0, ui::dp(8));
        if (ui::primaryButton("Export csv", ImVec2(bw, 0))) {
            if (ensureExchangeFolder(app)) showExportCsvDialog = true;
        }
        ImGui::Dummy(ImVec2(0, ui::dp(8)));

        // Eaten Graph button (full width, with a small drawn bar-chart icon)
        ImGui::SetCursorPosX(ui::dp(16));
        {
            ImVec2 pos = ImGui::GetCursorScreenPos();
            bool clicked = ui::primaryButton(u8"      Eaten Graph", ImVec2(w, 0));
            // draw 3 bars as an icon inside the button
            ImDrawList* dl = ImGui::GetWindowDrawList();
            float bx = pos.x + ui::dp(14), byBase = pos.y + ui::dp(26), bwid = ui::dp(4);
            dl->AddRectFilled(ImVec2(bx, byBase - ui::dp(8)), ImVec2(bx + bwid, byBase), ui::COL_ON_PRIMARY);
            dl->AddRectFilled(ImVec2(bx + ui::dp(6), byBase - ui::dp(14)), ImVec2(bx + ui::dp(6) + bwid, byBase), ui::COL_ON_PRIMARY);
            dl->AddRectFilled(ImVec2(bx + ui::dp(12), byBase - ui::dp(11)), ImVec2(bx + ui::dp(12) + bwid, byBase), ui::COL_ON_PRIMARY);
            if (clicked) app.push(makeEatenGraphScreen(app));
        }
        ImGui::Separator();

        ImGui::SetCursorPosX(ui::dp(16));
        ImGui::PushFont(app.fontBold, ui::fsBody());
        ImGui::TextUnformatted("Weight Table");
        ImGui::PopFont();
        ImGui::Dummy(ImVec2(0, ui::dp(4)));

        bool panelVisible = selectedWeight.has_value();
        float panelH = panelVisible ? ui::dp(112) : 0;

        if (weightEntries.empty()) {
            ImGui::SetCursorPosX(ui::dp(16));
            ImGui::PushFont(app.fontRegular, ui::fsBody());
            ImGui::TextUnformatted("No weight entries yet.");
            ImGui::PopFont();
            ImGui::Dummy(ImVec2(0, ui::dp(4)));
            ImGui::SetCursorPosX(ui::dp(16));
            if (ui::primaryButton("Add")) {
                weightInput.clear();
                weightCommentsInput.clear();
                weightDateMillis = nowMillis();
                showAddWeightDialog = true;
            }
        } else {
            ImGui::SetCursorPosX(ui::dp(16));
            ImVec2 listSize(w, ImGui::GetContentRegionAvail().y - panelH - ui::dp(12));
            drawWeightList(app, listSize);
        }

        if (panelVisible && selectedWeight) drawWeightPanel(app, panelH);

        drawDialogs(app);
        ui::helpBottomSheet(app, "##utilhelp", &showHelp, utilitiesHelpText());
    }

    bool anyDialogOpen() const {
        return showExportWarning || showImportWarning || showExportCsvDialog ||
               showAddWeightDialog || editingWeight.has_value() || deletingWeight.has_value() ||
               showWeightDatePicker;
    }

    void drawWeightList(App& app, const ImVec2& size) {
        float pad = ui::dp(4);
        auto measure = [&](int i, float w2) -> float {
            const WeightEntry& e = weightEntries[i];
            ImGui::PushFont(app.fontRegular, ui::fsBodySmall());
            float commentsW = w2 * 0.56f - ui::dp(8);
            float ch = e.comments.empty()
                ? ImGui::GetTextLineHeight()
                : std::min(ImGui::CalcTextSize(e.comments.c_str(), nullptr, false, commentsW).y,
                           ImGui::GetTextLineHeight() * 2.1f);
            float h = std::max(ImGui::GetTextLineHeight(), ch) + pad * 2 + ui::dp(2);
            ImGui::PopFont();
            return h;
        };
        auto draw = [&](int i, float w2) {
            const WeightEntry& e = weightEntries[i];
            ImVec2 pos = ImGui::GetCursorScreenPos();
            float h = weightCache.heights[i];
            ImGui::InvisibleButton("wrow", ImVec2(w2, h));
            bool clicked = ImGui::IsItemClicked();
            if (ImGui::IsItemHovered())
                ImGui::GetWindowDrawList()->AddRectFilled(pos, ImVec2(pos.x + w2, pos.y + h),
                                                          IM_COL32(0x1D, 0x1B, 0x20, 0x0A));
            bool isSel = selectedWeight && selectedWeight->weightId == e.weightId;
            ImDrawList* dl = ImGui::GetWindowDrawList();
            float y = pos.y + pad;
            ImGui::PushFont(isSel ? app.fontBold : app.fontRegular, ui::fsBodySmall());
            dl->AddText(isSel ? app.fontBold : app.fontRegular, ui::fsBodySmall(),
                        ImVec2(pos.x + pad, y), ui::COL_ON_SURFACE, e.dateWeight.c_str());
            ImGui::PopFont();
            ImGui::PushFont(app.fontRegular, ui::fsBodySmall());
            std::string kg = formatWeight(e.weight) + " kg";
            ImVec2 ks = ImGui::CalcTextSize(kg.c_str());
            dl->AddText(app.fontRegular, ui::fsBodySmall(),
                        ImVec2(pos.x + w2 * 0.44f - ks.x, y), ui::COL_ON_SURFACE, kg.c_str());
            if (!e.comments.empty()) {
                dl->AddText(app.fontRegular, ui::fsBodySmall(),
                            ImVec2(pos.x + w2 * 0.44f + ui::dp(10), y), ui::COL_ON_SURFACE,
                            e.comments.c_str(), nullptr, w2 * 0.56f - ui::dp(14));
            }
            ImGui::PopFont();
            if (clicked) {
                if (isSel) selectedWeight.reset();
                else selectedWeight = e;
            }
        };
        ui::virtualList("##weights", size, (int)weightEntries.size(), 0, weightCache,
                        weightRevision, measure, draw, true);
    }

    void drawWeightPanel(App& app, float panelH) {
        WeightEntry entry = *selectedWeight;
        ImVec2 pos = ImGui::GetCursorScreenPos();
        float w = ImGui::GetContentRegionAvail().x;
        ui::cardBackground(ImVec2(pos.x + ui::dp(16), pos.y), ImVec2(w - ui::dp(32), panelH), ui::COL_CARD);
        ImGui::SetCursorScreenPos(ImVec2(pos.x + ui::dp(32), pos.y + ui::dp(14)));
        ImGui::BeginGroup();
        float innerW = w - ui::dp(64);
        ImGui::PushFont(app.fontBold, ui::fsBody());
        std::string header = (entry.dateWeight.empty() ? "Unknown date" : entry.dateWeight);
        ImGui::PushFont(app.fontRegular, ui::fsBody());
        std::string full = header + "  " + formatWeight(entry.weight) + " kg";
        ImVec2 ts = ImGui::CalcTextSize(full.c_str());
        ImGui::SetCursorPosX(ImGui::GetCursorPosX() + std::max(0.0f, (innerW - ts.x) / 2));
        ImGui::TextUnformatted(full.c_str());
        ImGui::PopFont();
        ImGui::PopFont();
        ImGui::Dummy(ImVec2(0, ui::dp(8)));
        float bw = ui::dp(96);
        float gap = (innerW - 3 * bw) / 4;
        ImGui::SetCursorPosX(ImGui::GetCursorPosX() + gap);
        if (ui::primaryButton("Add", ImVec2(bw, 0))) {
            weightInput.clear();
            weightCommentsInput.clear();
            weightDateMillis = nowMillis();
            showAddWeightDialog = true;
        }
        ImGui::SameLine(0, gap);
        if (ui::primaryButton("Edit", ImVec2(bw, 0))) {
            editingWeight = entry;
            editWeightInput = formatWeight(entry.weight);
            editWeightCommentsInput = entry.comments;
            editWeightDateMillis = entry.dateWeight.empty()
                ? nowMillis() : parseDMMMYY(entry.dateWeight).value_or(nowMillis());
        }
        ImGui::SameLine(0, gap);
        if (ui::primaryButton("Delete", ImVec2(bw, 0))) deletingWeight = entry;
        ImGui::EndGroup();
    }

    void drawDialogs(App& app) {
        // Export db
        if (showExportWarning) {
            if (ui::beginDialog("##exportdb", &showExportWarning)) {
                ui::dialogTitle(app, "Export Database?", IM_COL32(0xC0, 0x18, 0x18, 0xFF), true);
                ImGui::PushFont(app.fontRegular, ui::fsBody());
                ImGui::PushTextWrapPos(0.0f);
                ImGui::Text("This will overwrite %s in the selected folder.", DATABASE_FILE_NAME);
                ImGui::PushFont(app.fontBold, ui::fsBody());
                ImGui::TextUnformatted(displayPath(L"foods.db").c_str());
                ImGui::PopFont();
                ImGui::PopTextWrapPos();
                ImGui::PopFont();
                ImGui::Dummy(ImVec2(0, ui::dp(8)));
                if (ui::outlinedButton("Change folder")) changeFolder(app);
                ImGui::SameLine(0, ui::dp(12));
                if (ui::primaryButton("Confirm", ImVec2(0, 0), !exchangeFolder.empty())) {
                    showExportWarning = false;
                    std::wstring dest = exchangeFolder + L"\\" + utf8ToWide(DATABASE_FILE_NAME);
                    bool success = app.db.exportDatabaseTo(dest);
                    app.toast(success ? "Database exported" : "Failed to export database");
                }
                ui::endDialog();
            }
        }

        // Import db
        if (showImportWarning) {
            if (ui::beginDialog("##importdb", &showImportWarning)) {
                ui::dialogTitle(app, "Import Database?", IM_COL32(0xC0, 0x18, 0x18, 0xFF), true);
                ImGui::PushFont(app.fontRegular, ui::fsBody());
                ImGui::PushTextWrapPos(0.0f);
                ImGui::Text("This will replace the app database with %s from:", DATABASE_FILE_NAME);
                ImGui::PushFont(app.fontBold, ui::fsBody());
                ImGui::TextUnformatted(displayPath(L"foods.db").c_str());
                ImGui::PopFont();
                ImGui::PopTextWrapPos();
                ImGui::PopFont();
                ImGui::Dummy(ImVec2(0, ui::dp(8)));
                if (ui::outlinedButton("Change folder")) {
                    changeFolder(app);
                    std::wstring src = exchangeFolder + L"\\" + utf8ToWide(DATABASE_FILE_NAME);
                    if (!fileExists(src))
                        app.toast(std::string("No ") + DATABASE_FILE_NAME + " found in " +
                                  wideToUtf8(exchangeFolder));
                }
                ImGui::SameLine(0, ui::dp(12));
                std::wstring src = exchangeFolder + L"\\" + utf8ToWide(DATABASE_FILE_NAME);
                if (ui::primaryButton("Confirm", ImVec2(0, 0),
                                      !exchangeFolder.empty() && fileExists(src))) {
                    showImportWarning = false;
                    bool success = app.db.replaceDatabaseFrom(src);
                    if (success) {
                        refreshWeights(app);
                        app.foodsResult.foodInserted = true; // refresh Foods list on return
                        app.toast("Database imported");
                    } else {
                        app.toast("Failed to import database");
                    }
                }
                ui::endDialog();
            }
        }

        // Export csv
        if (showExportCsvDialog) {
            if (ui::beginDialog("##exportcsv", &showExportCsvDialog)) {
                ui::dialogTitle(app, "Export CSV?", ui::COL_ON_SURFACE, true);
                ImGui::PushFont(app.fontRegular, ui::fsBody());
                ImGui::PushTextWrapPos(0.0f);
                ImGui::Text("This will save %s to:", DAILY_CSV_FILE_NAME);
                ImGui::PushFont(app.fontBold, ui::fsBody());
                ImGui::TextUnformatted(displayPath(L"EatenDailyAll.csv").c_str());
                ImGui::PopFont();
                ImGui::PopTextWrapPos();
                ImGui::PopFont();
                ImGui::Dummy(ImVec2(0, ui::dp(8)));
                if (ui::outlinedButton("Change folder")) changeFolder(app);
                ImGui::SameLine(0, ui::dp(12));
                if (ui::primaryButton("Confirm", ImVec2(0, 0), !exchangeFolder.empty())) {
                    showExportCsvDialog = false;
                    auto dailyTotals = aggregateDailyTotals(app.db.readEatenFoods());
                    auto weights = app.db.readWeights();
                    std::string csv = buildEatenDailyAllCsv(dailyTotals, weights);
                    std::wstring dest = exchangeFolder + L"\\" + utf8ToWide(DAILY_CSV_FILE_NAME);
                    bool success = writeTextFile(dest, csv);
                    app.toast(success ? "CSV exported" : "Failed to export CSV");
                }
                ui::endDialog();
            }
        }

        // Add weight
        if (showAddWeightDialog) {
            if (ui::beginDialog("##addweight", &showAddWeightDialog, ui::dp(420))) {
                ui::dialogTitle(app, "Add weight", ui::COL_ON_SURFACE, true);
                ImGui::PushFont(app.fontRegular, ui::fsBody());
                if (ui::primaryButton(formatDMMMYY(weightDateMillis).c_str()))
                    showWeightDatePicker = true;
                ImGui::SameLine(0, ui::dp(8));
                ImGui::SetNextItemWidth(-FLT_MIN);
                char wbuf[32];
                snprintf(wbuf, sizeof(wbuf), "%s", weightInput.c_str());
                if (ImGui::InputTextWithHint("##weightval", "Weight (kg)", wbuf, sizeof(wbuf)))
                    weightInput = wbuf;
                ImGui::Dummy(ImVec2(0, ui::dp(6)));
                ImGui::TextColored(ImGui::ColorConvertU32ToFloat4(ui::COL_ON_SURFACE_VARIANT), "Comments");
                ui::inputMultiline("##weightcomments", weightCommentsInput, 0, 3);
                ImGui::PopFont();
                ui::datePickerModal(app, "##weightdate", &showWeightDatePicker, &weightDateMillis);
                ImGui::Dummy(ImVec2(0, ui::dp(8)));
                float cw = ui::dp(110);
                ImGui::SetCursorPosX((ImGui::GetContentRegionAvail().x - cw) / 2 + ImGui::GetCursorPosX());
                if (ui::primaryButton("Confirm", ImVec2(cw, 0))) {
                    auto weightValue = parseWeightInput(weightInput);
                    if (!weightValue) {
                        app.toast("Enter a valid weight");
                    } else {
                        std::string dateValue = formatDMMMYY(weightDateMillis);
                        bool exists = false;
                        for (const auto& e : weightEntries)
                            if (e.dateWeight == dateValue) { exists = true; break; }
                        if (exists) {
                            app.toast("Date already exists");
                        } else if (app.db.insertWeight(dateValue, *weightValue, weightCommentsInput)) {
                            weightInput.clear();
                            weightCommentsInput.clear();
                            refreshWeights(app);
                            app.toast("Weight saved");
                            showAddWeightDialog = false;
                            selectedWeight.reset();
                        } else {
                            app.toast("Failed to save weight");
                        }
                    }
                }
                ui::endDialog();
            }
            if (!showAddWeightDialog) selectedWeight.reset();
        }

        // Edit weight
        if (editingWeight) {
            bool open = true;
            if (ui::beginDialog("##editweight", &open, ui::dp(420))) {
                ui::dialogTitle(app, "Edit weight", ui::COL_ON_SURFACE, true);
                ImGui::PushFont(app.fontRegular, ui::fsBody());
                ImGui::PushFont(app.fontBold, ui::fsBody());
                ImGui::TextUnformatted(formatDMMMYY(editWeightDateMillis).c_str());
                ImGui::PopFont();
                ImGui::SameLine(0, ui::dp(12));
                ImGui::SetNextItemWidth(-FLT_MIN);
                char wbuf[32];
                snprintf(wbuf, sizeof(wbuf), "%s", editWeightInput.c_str());
                if (ImGui::InputTextWithHint("##editweightval", "Weight (kg)", wbuf, sizeof(wbuf)))
                    editWeightInput = wbuf;
                ImGui::Dummy(ImVec2(0, ui::dp(6)));
                ImGui::TextColored(ImGui::ColorConvertU32ToFloat4(ui::COL_ON_SURFACE_VARIANT), "Comments");
                ui::inputMultiline("##editweightcomments", editWeightCommentsInput, 0, 3);
                ImGui::PopFont();
                ImGui::Dummy(ImVec2(0, ui::dp(8)));
                float cw = ui::dp(110);
                ImGui::SetCursorPosX((ImGui::GetContentRegionAvail().x - cw) / 2 + ImGui::GetCursorPosX());
                if (ui::primaryButton("Confirm", ImVec2(cw, 0))) {
                    auto weightValue = parseWeightInput(editWeightInput);
                    if (!weightValue) {
                        app.toast("Enter a valid weight");
                    } else {
                        std::string dateValue = formatDMMMYY(editWeightDateMillis);
                        if (app.db.updateWeight(editingWeight->weightId, dateValue, *weightValue,
                                                editWeightCommentsInput)) {
                            editingWeight.reset();
                            selectedWeight.reset();
                            refreshWeights(app);
                            app.toast("Weight updated");
                            open = false;
                        } else {
                            app.toast("Failed to update weight");
                        }
                    }
                }
                ui::endDialog();
            }
            if (!open && editingWeight) { editingWeight.reset(); selectedWeight.reset(); }
        }

        // Delete weight
        if (deletingWeight) {
            bool open = true;
            if (ui::beginDialog("##deleteweight", &open)) {
                ui::dialogTitle(app, "Delete weight?", IM_COL32(0xC0, 0x18, 0x18, 0xFF), true);
                ImGui::PushFont(app.fontRegular, ui::fsBody());
                ImGui::TextUnformatted("This will remove the selected weight record.");
                ImGui::PopFont();
                ImGui::Dummy(ImVec2(0, ui::dp(8)));
                float cw = ui::dp(110);
                ImGui::SetCursorPosX((ImGui::GetContentRegionAvail().x - cw) / 2 + ImGui::GetCursorPosX());
                if (ui::primaryButton("Confirm", ImVec2(cw, 0))) {
                    if (app.db.deleteWeight(deletingWeight->weightId)) {
                        deletingWeight.reset();
                        selectedWeight.reset();
                        refreshWeights(app);
                        app.toast("Weight deleted");
                        open = false;
                    } else {
                        app.toast("Failed to delete weight");
                    }
                }
                ui::endDialog();
            }
            if (!open && deletingWeight) { deletingWeight.reset(); selectedWeight.reset(); }
        }
    }
};

} // namespace

Screen* makeUtilitiesScreen(App& app) { return new UtilitiesScreen(app); }
