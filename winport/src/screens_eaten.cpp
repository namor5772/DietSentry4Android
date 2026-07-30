// screens_eaten.cpp — EatenLogScreen: eaten log list, daily totals cards,
// date filter, edit/delete dialogs, daily-totals action sheet with the
// "Explain this day (AI)" flow and the profile editor.
#include "app.h"
#include "ui.h"
#include "dialogs.h"
#include "helptexts.h"
#include "anthropic.h"
#include "imgui_internal.h"

namespace {

// Rows displayed by NutritionalInfo (NutritionalInfo composable).
static std::vector<std::pair<std::string, double>> nutritionalInfoRows(
    const Nutrients& nIn, double amountEaten, const std::string& unit,
    bool showExtraNutrients, bool hideFibreAndCalcium) {
    Nutrients n = nIn;
    std::string unitLower = toLowerAscii(unit);
    std::string amountLabel =
        unitLower == "ml" ? "Amount (mL)" :
        unitLower == "g" ? "Amount (g)" :
        unitLower == "mixed units" ? "Amount (g or mL)" : ("Amount (" + unit + ")");
    std::vector<std::pair<std::string, double>> rows;
    rows.push_back({amountLabel, amountEaten});
    rows.push_back({"Energy (kJ):", n.energy()});
    rows.push_back({"Protein (g):", n.protein()});
    rows.push_back({"Fat, total (g):", n.fatTotal()});
    rows.push_back({"- Saturated (g):", n.saturatedFat()});
    if (showExtraNutrients) {
        rows.push_back({"- Trans (mg):", n.transFat()});
        rows.push_back({"- Polyunsaturated (g):", n.polyunsaturatedFat()});
        rows.push_back({"- Monounsaturated (g):", n.monounsaturatedFat()});
    }
    rows.push_back({"Carbohydrate (g):", n.carbohydrate()});
    rows.push_back({"- Sugars (g):", n.sugars()});
    if (showExtraNutrients) {
        rows.push_back({"Sodium (mg):", n.sodiumNa()});
        if (!hideFibreAndCalcium) rows.push_back({"Dietary Fibre (g):", n.dietaryFibre()});
        rows.push_back({"Calcium (mg):", n.calciumCa()});
        rows.push_back({"Potassium (mg):", n.potassiumK()});
        rows.push_back({"Thiamin B1 (mg):", n.thiaminB1()});
        rows.push_back({"Riboflavin B2 (mg):", n.riboflavinB2()});
        rows.push_back({"Niacin B3 (mg):", n.niacinB3()});
        rows.push_back({"Folate (ug):", n.folate()});
        rows.push_back({"Iron (mg):", n.ironFe()});
        rows.push_back({"Magnesium (mg):", n.magnesiumMg()});
        rows.push_back({"Vitamin C (mg):", n.vitaminC()});
        rows.push_back({"Caffeine (mg):", n.caffeine()});
        rows.push_back({"Cholesterol (mg):", n.cholesterol()});
        rows.push_back({"Alcohol (g):", n.alcohol()});
    } else {
        rows.push_back({"Sodium (mg):", n.sodiumNa()});
        rows.push_back({"Dietary Fibre (g):", n.dietaryFibre()});
        if (!hideFibreAndCalcium) rows.push_back({"Calcium (mg):", n.calciumCa()});
    }
    return rows;
}

struct EatenLogScreen : Screen {
    std::vector<EatenFood> eatenFoods;
    std::vector<EatenFood> filteredEatenFoods;
    std::vector<DailyTotals> dailyTotals;
    std::vector<WeightEntry> weightEntries;

    int nutritionalInfoSelection = 0;
    bool displayDailyTotals = false;
    bool filterByDate = false;
    long long selectedFilterDateMillis = 0;
    bool showFilterDatePicker = false;
    bool showHelp = false;

    std::optional<EatenFood> selectedEatenFood;
    AmountDateTimeDialog editDialog;
    bool showDeleteEatenDialog = false;

    // Daily totals action sheet + explain flow
    std::optional<DailyTotals> sheetTotals;
    std::shared_ptr<AiJob> explainJob;
    enum class ExplainState { Idle, Loading, Success, Error };
    ExplainState explainState = ExplainState::Idle;
    std::string explainText, explainError;
    double explainCost = 0;
    bool showProfileDialog = false;
    std::string profileText;

    ui::HeightCache listCache;
    int listRevision = 0;

    explicit EatenLogScreen(App& app) {
        nutritionalInfoSelection = app.prefs.getInt(PREF_KEY_NUTRITION_SELECTION_EATEN, 0);
        displayDailyTotals = app.prefs.getBool(PREF_KEY_DISPLAY_DAILY_TOTALS, false);
        filterByDate = app.prefs.getBool(PREF_KEY_FILTER_EATEN_BY_DATE, false);
        selectedFilterDateMillis = app.sessionSelectedFilterDateMillis.value_or(nowMillis());
        eatenFoods = app.db.readEatenFoods();
        weightEntries = app.db.readWeights();
        rebuild();
    }

    const char* route() const override { return "eatenLog"; }

    bool onBack(App& app) override {
        (void)app;
        if (selectedEatenFood) { selectedEatenFood.reset(); return true; }
        return false;
    }

    void rebuild() {
        filteredEatenFoods.clear();
        if (!filterByDate) {
            filteredEatenFoods = eatenFoods;
        } else {
            std::string matchDate = formatDMMMYY(selectedFilterDateMillis);
            for (const auto& ef : eatenFoods)
                if (ef.dateEaten == matchDate) filteredEatenFoods.push_back(ef);
        }
        dailyTotals = aggregateDailyTotals(filteredEatenFoods);
        listRevision++;
    }

    const WeightEntry* weightByDate(const std::string& date) const {
        for (const auto& w : weightEntries)
            if (w.dateWeight == date) return &w;
        return nullptr;
    }

    void draw(App& app) override {
        auto bar = ui::topBar(app, "Eaten\nTable", true, nutritionalInfoSelection, false);
        if (bar.segSelection >= 0) {
            nutritionalInfoSelection = bar.segSelection;
            app.prefs.putInt(PREF_KEY_NUTRITION_SELECTION_EATEN, nutritionalInfoSelection);
            listRevision++;
        }
        if (bar.helpClicked) showHelp = true;
        if (bar.navClicked) app.pop();
        if (ImGui::IsKeyPressed(ImGuiKey_Escape) && !anyOverlayOpen()) app.requestBack();

        bool showNutritionalInfo = nutritionalInfoSelection != 0;
        bool showExtraNutrients = nutritionalInfoSelection == 2;

        // Checkbox row
        ImGui::SetCursorPosX(ui::dp(10));
        ImGui::PushFont(app.fontRegular, ui::fsBodySmall());
        if (ui::checkboxM("Daily totals", &displayDailyTotals)) {
            if (displayDailyTotals) selectedEatenFood.reset();
            app.prefs.putBool(PREF_KEY_DISPLAY_DAILY_TOTALS, displayDailyTotals);
            weightEntries = app.db.readWeights();
            listRevision++;
        }
        ImGui::SameLine(0, ui::dp(10));
        if (ui::checkboxM("Filter by date", &filterByDate)) {
            app.prefs.putBool(PREF_KEY_FILTER_EATEN_BY_DATE, filterByDate);
            if (filterByDate) selectedEatenFood.reset();
            rebuild();
        }
        ImGui::SameLine(0, ui::dp(6));
        if (ui::textButton(formatDMMMYY(selectedFilterDateMillis).c_str()))
            showFilterDatePicker = true;
        ImGui::PopFont();
        ImGui::Dummy(ImVec2(0, ui::dp(2)));

        // Selection panel space
        bool panelVisible = selectedEatenFood.has_value() && !displayDailyTotals;
        float panelH = 0;
        if (panelVisible) {
            ImGui::PushFont(app.fontBold, ui::fsBody());
            float descH = ImGui::CalcTextSize(selectedEatenFood->foodDescription.c_str(), nullptr, false,
                                              ImGui::GetContentRegionAvail().x - ui::dp(64)).y;
            ImGui::PopFont();
            panelH = descH + ui::dp(16 * 2 + 22 + 40 + 10);
        }

        ImGui::SetCursorPosX(ui::dp(8));
        ImVec2 listSize(ImGui::GetContentRegionAvail().x - ui::dp(8),
                        ImGui::GetContentRegionAvail().y - panelH - ui::dp(6));
        if (displayDailyTotals)
            drawDailyTotalsList(app, listSize, showNutritionalInfo, showExtraNutrients);
        else
            drawEatenList(app, listSize, showNutritionalInfo, showExtraNutrients);

        if (panelVisible && selectedEatenFood) drawSelectionPanel(app, panelH);

        drawDialogs(app);
        drawSheetAndExplain(app);
        ui::helpBottomSheet(app, "##eatenhelp", &showHelp, eatenHelpText());
    }

    bool anyOverlayOpen() const {
        return showHelp || editDialog.open || showDeleteEatenDialog || showFilterDatePicker ||
               sheetTotals.has_value() || explainState != ExplainState::Idle || showProfileDialog;
    }

    // --- individual logs -------------------------------------------------
    void drawEatenList(App& app, const ImVec2& size, bool showNutritionalInfo, bool showExtraNutrients) {
        float pad = ui::dp(16);
        float spacing = ui::dp(8);
        ImGui::PushFont(app.fontRegular, ui::fsBodySmall());
        float rowH = ImGui::GetTextLineHeightWithSpacing();
        ImGui::PopFont();

        auto measure = [&](int i, float w) -> float {
            const EatenFood& ef = filteredEatenFoods[i];
            float wrapW = w - pad * 2;
            float spacingY = ImGui::GetStyle().ItemSpacing.y;
            float h = pad * 2;
            h += rowH; // date+time
            ImGui::PushFont(app.fontBold, ui::fsBody());
            h += ImGui::CalcTextSize(ef.foodDescription.c_str(), nullptr, false, wrapW).y + spacingY;
            ImGui::PopFont();
            h += ui::dp(1) + spacingY; // spacer dummy
            if (showNutritionalInfo) {
                auto rows = nutritionalInfoRows(ef.n, ef.amountEaten,
                                                descriptionUnit(ef.foodDescription),
                                                showExtraNutrients, !showExtraNutrients);
                h += rows.size() * rowH;
            } else {
                h += rowH;
            }
            return h;
        };
        auto draw = [&](int i, float w) {
            const EatenFood& ef = filteredEatenFoods[i];
            ImVec2 pos = ImGui::GetCursorScreenPos();
            float h = listCache.heights[i];
            ImGui::InvisibleButton("card", ImVec2(w, h));
            bool clicked = ImGui::IsItemClicked();
            ui::cardBackground(pos, ImVec2(w, h));
            ImGui::SetCursorScreenPos(ImVec2(pos.x + pad, pos.y + pad));
            ImGui::BeginGroup();
            ImGui::PushFont(app.fontRegular, ui::fsBodySmall());
            ImGui::Text("%s %s", ef.dateEaten.c_str(), ef.timeEaten.c_str());
            ImGui::PopFont();
            ImGui::PushFont(app.fontBold, ui::fsBody());
            ImGui::PushTextWrapPos(ImGui::GetCursorPosX() + w - pad * 2);
            ImGui::TextUnformatted(ef.foodDescription.c_str());
            ImGui::PopTextWrapPos();
            ImGui::PopFont();
            ImGui::Dummy(ImVec2(0, ui::dp(1)));
            std::string unit = descriptionUnit(ef.foodDescription);
            if (showNutritionalInfo) {
                for (auto& row : nutritionalInfoRows(ef.n, ef.amountEaten, unit,
                                                     showExtraNutrients, !showExtraNutrients))
                    ui::nutrientRow(row.first.c_str(), row.second);
            } else {
                ImGui::PushFont(app.fontRegular, ui::fsBodySmall());
                Nutrients n = ef.n;
                ImGui::Text("%s%s  %skJ", formatAmount(ef.amountEaten).c_str(), unit.c_str(),
                            formatNumber(n.energy(), 0).c_str());
                ImGui::PopFont();
            }
            ImGui::EndGroup();
            if (clicked) {
                if (selectedEatenFood && selectedEatenFood->eatenId == ef.eatenId)
                    selectedEatenFood.reset();
                else
                    selectedEatenFood = ef;
            }
        };
        int rev = listRevision * 8 + (showNutritionalInfo ? 1 : 0) + (showExtraNutrients ? 2 : 0);
        ui::virtualList("##eatenlist", size, (int)filteredEatenFoods.size(), spacing,
                        listCache, rev, measure, draw, false);
    }

    // --- daily totals cards ----------------------------------------------
    void drawDailyTotalsList(App& app, const ImVec2& size, bool showNutritionalInfo, bool showExtraNutrients) {
        float pad = ui::dp(16);
        float spacing = ui::dp(8);
        ImGui::PushFont(app.fontRegular, ui::fsBodySmall());
        float rowH = ImGui::GetTextLineHeightWithSpacing();
        ImGui::PopFont();

        auto measure = [&](int i, float w) -> float {
            const DailyTotals& dt = dailyTotals[i];
            const WeightEntry* we = weightByDate(dt.date);
            float wrapW = w - pad * 2;
            float spacingY = ImGui::GetStyle().ItemSpacing.y;
            float h = pad * 2;
            h += rowH;              // date (small)
            ImGui::PushFont(app.fontBold, ui::fsBody());
            h += ImGui::GetTextLineHeight() + spacingY;  // "Daily totals" bold
            ImGui::PopFont();
            h += ui::dp(2) + spacingY;                   // spacer dummy
            if (showExtraNutrients && we && !trim(we->comments).empty()) {
                ImGui::PushFont(app.fontRegular, ui::fsBodySmall());
                float labelW = ImGui::CalcTextSize("Comments: ").x;
                h += ImGui::CalcTextSize(trim(we->comments).c_str(), nullptr, false, wrapW - labelW).y +
                     spacingY;
                ImGui::PopFont();
            }
            if (showExtraNutrients) h += rowH;  // My weight
            if (showNutritionalInfo) {
                auto rows = nutritionalInfoRows(dt.n, dt.amountEaten, dt.unitLabel,
                                                showExtraNutrients, !showExtraNutrients);
                h += rows.size() * rowH;
            } else {
                h += 4 * rowH;
            }
            return h;
        };
        auto draw = [&](int i, float w) {
            const DailyTotals& dt = dailyTotals[i];
            const WeightEntry* we = weightByDate(dt.date);
            ImVec2 pos = ImGui::GetCursorScreenPos();
            float h = listCache.heights[i];
            ImGui::InvisibleButton("card", ImVec2(w, h));
            bool clicked = ImGui::IsItemClicked();
            ui::cardBackground(pos, ImVec2(w, h));
            ImGui::SetCursorScreenPos(ImVec2(pos.x + pad, pos.y + pad));
            ImGui::BeginGroup();
            ImGui::PushFont(app.fontRegular, ui::fsBodySmall());
            ImGui::TextUnformatted(dt.date.c_str());
            ImGui::PopFont();
            ImGui::PushFont(app.fontBold, ui::fsBody());
            ImGui::TextUnformatted("Daily totals");
            ImGui::PopFont();
            ImGui::Dummy(ImVec2(0, ui::dp(2)));
            if (showExtraNutrients && we && !trim(we->comments).empty()) {
                ImGui::PushFont(app.fontRegular, ui::fsBodySmall());
                ImGui::TextUnformatted("Comments:");
                ImGui::SameLine();
                ImGui::PushTextWrapPos(ImGui::GetCursorPosX() + w - pad * 2 -
                                       (ImGui::GetCursorPosX() - (pos.x + pad)));
                ImGui::TextUnformatted(trim(we->comments).c_str());
                ImGui::PopTextWrapPos();
                ImGui::PopFont();
            }
            if (showExtraNutrients)
                ui::nutrientRowText("My weight (kg)", we ? formatWeight(we->weight) : "NA");
            if (showNutritionalInfo) {
                for (auto& row : nutritionalInfoRows(dt.n, dt.amountEaten, dt.unitLabel,
                                                     showExtraNutrients, !showExtraNutrients))
                    ui::nutrientRow(row.first.c_str(), row.second);
            } else {
                Nutrients n = dt.n;
                std::string unitLower = toLowerAscii(dt.unitLabel);
                std::string amountLabel =
                    unitLower == "ml" ? "Amount (mL)" :
                    unitLower == "g" ? "Amount (g)" :
                    unitLower == "mixed units" ? "Amount (g or mL)" : ("Amount (" + dt.unitLabel + ")");
                ui::nutrientRow(amountLabel.c_str(), dt.amountEaten);
                ui::nutrientRow("Energy (kJ):", n.energy());
                ui::nutrientRow("Fat, total (g):", n.fatTotal());
                ui::nutrientRow("Dietary Fibre (g):", n.dietaryFibre());
            }
            ImGui::EndGroup();
            if (clicked) sheetTotals = dt;
        };
        int rev = listRevision * 8 + 4 + (showNutritionalInfo ? 1 : 0) + (showExtraNutrients ? 2 : 0);
        ui::virtualList("##dailylist", size, (int)dailyTotals.size(), spacing,
                        listCache, rev, measure, draw, false);
    }

    // --- selection panel (EatenSelectionPanel) ----------------------------
    void drawSelectionPanel(App& app, float panelH) {
        EatenFood ef = *selectedEatenFood;
        ImVec2 pos = ImGui::GetCursorScreenPos();
        float w = ImGui::GetContentRegionAvail().x;
        ui::cardBackground(ImVec2(pos.x + ui::dp(8), pos.y), ImVec2(w - ui::dp(16), panelH), ui::COL_CARD);
        ImGui::SetCursorScreenPos(ImVec2(pos.x + ui::dp(24), pos.y + ui::dp(12)));
        ImGui::BeginGroup();
        float innerW = w - ui::dp(48);
        ImGui::PushFont(app.fontBold, ui::fsBody());
        ImVec2 ts = ImGui::CalcTextSize(ef.foodDescription.c_str(), nullptr, false, innerW);
        ImGui::SetCursorPosX(ImGui::GetCursorPosX() + std::max(0.0f, (innerW - ts.x) / 2));
        ImGui::PushTextWrapPos(ImGui::GetCursorPosX() + innerW);
        ImGui::TextUnformatted(ef.foodDescription.c_str());
        ImGui::PopTextWrapPos();
        ImGui::PopFont();
        ImGui::PushFont(app.fontRegular, ui::fsBodySmall());
        std::string logged = "Logged on: " + ef.dateEaten + " at " + ef.timeEaten;
        ImVec2 ls = ImGui::CalcTextSize(logged.c_str());
        ImGui::SetCursorPosX(ImGui::GetCursorPosX() + std::max(0.0f, (innerW - ls.x) / 2));
        ImGui::TextUnformatted(logged.c_str());
        ImGui::PopFont();
        ImGui::Dummy(ImVec2(0, ui::dp(6)));
        float bw = ui::dp(110);
        float gap = (innerW - 2 * bw) / 3;
        ImGui::SetCursorPosX(ImGui::GetCursorPosX() + gap);
        if (ui::primaryButton("Edit", ImVec2(bw, 0))) {
            long long initial = parseDMMMYYHHMM(ef.dateEaten, ef.timeEaten).value_or(nowMillis());
            char amt[32];
            snprintf(amt, sizeof(amt), "%g", ef.amountEaten);
            editDialog.openFor(amt, initial);
        }
        ImGui::SameLine(0, gap);
        if (ui::primaryButton("Delete", ImVec2(bw, 0))) showDeleteEatenDialog = true;
        ImGui::EndGroup();
    }

    void drawDialogs(App& app) {
        // Filter date picker
        if (ui::datePickerModal(app, "##filterdate", &showFilterDatePicker, &selectedFilterDateMillis)) {
            app.sessionSelectedFilterDateMillis = selectedFilterDateMillis;
            rebuild();
        }

        // Edit eaten dialog
        if (selectedEatenFood && editDialog.open) {
            EatenFood ef = *selectedEatenFood;
            double amount = 0;
            long long dateTime = 0;
            auto res = editDialog.draw(app, "##editeaten", descriptionDisplayName(ef.foodDescription),
                                       descriptionUnit(ef.foodDescription), &amount, &dateTime);
            if (res == AmountDateTimeDialog::Confirmed) {
                app.db.updateEatenFood(ef, amount, dateTime);
                eatenFoods = app.db.readEatenFoods();
                selectedEatenFood.reset();
                rebuild();
            } else if (res == AmountDateTimeDialog::Dismissed) {
                selectedEatenFood.reset();
            }
        }

        // Delete eaten dialog
        if (selectedEatenFood && showDeleteEatenDialog) {
            EatenFood ef = *selectedEatenFood;
            bool wasOpen = showDeleteEatenDialog;
            if (ui::beginDialog("##deleteeaten", &showDeleteEatenDialog)) {
                ui::dialogTitle(app, "Delete Eaten Food?", IM_COL32(0xC0, 0x18, 0x18, 0xFF), true);
                ImGui::PushFont(app.fontRegular, ui::fsBody());
                ImGui::TextUnformatted("Are you sure you want to delete:");
                ImGui::PushFont(app.fontBold, ui::fsBody());
                ImGui::PushTextWrapPos(0.0f);
                ImGui::TextUnformatted(ef.foodDescription.c_str());
                ImGui::PopTextWrapPos();
                ImGui::PopFont();
                std::string unit = descriptionUnit(ef.foodDescription);
                ImGui::Text("Amount: %s %s", formatAmount(ef.amountEaten).c_str(), unit.c_str());
                ImGui::PopFont();
                ImGui::Dummy(ImVec2(0, ui::dp(8)));
                float cw = ui::dp(110);
                ImGui::SetCursorPosX((ImGui::GetContentRegionAvail().x - cw) / 2 + ImGui::GetCursorPosX());
                if (ui::primaryButton("Confirm", ImVec2(cw, 0))) {
                    app.db.deleteEatenFood(ef.eatenId);
                    eatenFoods = app.db.readEatenFoods();
                    selectedEatenFood.reset();
                    showDeleteEatenDialog = false;
                    rebuild();
                }
                ui::endDialog();
            }
            if (wasOpen && !showDeleteEatenDialog && selectedEatenFood) selectedEatenFood.reset();
        }
    }

    // --- daily totals action sheet + explain flow -------------------------
    void drawSheetAndExplain(App& app) {
        if (sheetTotals) {
            ImGuiViewport* vp = ImGui::GetMainViewport();
            // scrim
            ImGui::SetNextWindowPos(vp->Pos);
            ImGui::SetNextWindowSize(vp->Size);
            ImGui::PushStyleColor(ImGuiCol_WindowBg, ImVec4(0, 0, 0, 0.42f));
            ImGui::PushStyleVar(ImGuiStyleVar_WindowRounding, 0.0f);
            ImGui::Begin("##dtscrim", nullptr,
                ImGuiWindowFlags_NoTitleBar | ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoMove |
                ImGuiWindowFlags_NoScrollbar | ImGuiWindowFlags_NoSavedSettings | ImGuiWindowFlags_NoNav);
            bool scrimClicked = ImGui::IsWindowHovered() && ImGui::IsMouseClicked(0);
            ImGui::End();
            ImGui::PopStyleVar();
            ImGui::PopStyleColor();

            float sheetH = ui::dp(190);
            ImGui::SetNextWindowPos(ImVec2(vp->Pos.x, vp->Pos.y + vp->Size.y - sheetH));
            ImGui::SetNextWindowSize(ImVec2(vp->Size.x, sheetH));
            ImGui::PushStyleColor(ImGuiCol_WindowBg,
                                  ImGui::ColorConvertU32ToFloat4(IM_COL32(0xEC, 0xE6, 0xF0, 0xFF)));
            ImGui::PushStyleVar(ImGuiStyleVar_WindowRounding, ui::dp(24));
            ImGui::PushStyleVar(ImGuiStyleVar_WindowPadding, ImVec2(ui::dp(16), ui::dp(12)));
            ImGui::Begin("##dtsheet", nullptr,
                ImGuiWindowFlags_NoTitleBar | ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoMove |
                ImGuiWindowFlags_NoCollapse | ImGuiWindowFlags_NoSavedSettings);
            ImGui::BringWindowToDisplayFront(ImGui::GetCurrentWindow());

            DailyTotals dt = *sheetTotals;
            std::string title = dt.date + u8" — " + formatNumber(dt.amountEaten, 1) + " " + dt.unitLabel;
            ImGui::PushFont(app.fontBold, ui::fsTitleMedium());
            ImGui::TextUnformatted(title.c_str());
            ImGui::PopFont();
            ImGui::Dummy(ImVec2(0, ui::dp(6)));

            auto sheetRow = [&](const char* label) -> bool {
                ImVec2 pos = ImGui::GetCursorScreenPos();
                float w = ImGui::GetContentRegionAvail().x;
                float h = ui::dp(44);
                bool clicked = ImGui::InvisibleButton(label, ImVec2(w, h));
                if (ImGui::IsItemHovered())
                    ImGui::GetWindowDrawList()->AddRectFilled(pos, ImVec2(pos.x + w, pos.y + h),
                                                              IM_COL32(0x1D, 0x1B, 0x20, 0x10), ui::dp(8));
                ImGui::PushFont(app.fontRegular, ui::fsBody());
                ImGui::GetWindowDrawList()->AddText(
                    ImVec2(pos.x + ui::dp(8), pos.y + (h - ImGui::GetTextLineHeight()) / 2),
                    ui::COL_ON_SURFACE, label);
                ImGui::PopFont();
                return clicked;
            };
            if (sheetRow("Explain this day (AI)")) {
                sheetTotals.reset();
                startExplain(app, dt);
            }
            if (sheetRow("Edit my profile")) {
                sheetTotals.reset();
                profileText = app.prefs.getString(PREF_KEY_AI_USER_PROFILE, "");
                showProfileDialog = true;
            }
            ImGui::End();
            ImGui::PopStyleVar(2);
            ImGui::PopStyleColor();
            if (scrimClicked || ImGui::IsKeyPressed(ImGuiKey_Escape)) sheetTotals.reset();
        }

        // Poll explain job
        if (explainJob) {
            std::lock_guard<std::mutex> lock(explainJob->mu);
            if (explainJob->done) {
                if (explainJob->ok) {
                    explainState = ExplainState::Success;
                    explainText = explainJob->response.text;
                    // model captured at start
                    explainCost = computeAiCostUsd(explainJob->response.usage, explainModel);
                } else {
                    explainState = ExplainState::Error;
                    explainError = explainJob->error.empty() ? "Unknown error" : explainJob->error;
                }
                explainJob.reset();
            }
        }

        // Explain result dialog
        if (explainState != ExplainState::Idle) {
            bool open = true;
            if (ui::beginDialog("##explaindlg", &open, ui::dp(430),
                                explainState != ExplainState::Loading)) {
                const char* title = explainState == ExplainState::Loading ? u8"Analysing…"
                                    : explainState == ExplainState::Success ? "Daily totals explanation"
                                    : "Error";
                ui::dialogTitle(app, title);
                if (explainState == ExplainState::Loading) {
                    // spinner
                    ImVec2 c = ImGui::GetCursorScreenPos();
                    float r = ui::dp(16);
                    ImVec2 center(c.x + ImGui::GetContentRegionAvail().x / 2, c.y + r + ui::dp(12));
                    float t = (float)ImGui::GetTime() * 6.0f;
                    ImGui::GetWindowDrawList()->PathArcTo(center, r, t, t + 4.6f, 24);
                    ImGui::GetWindowDrawList()->PathStroke(ui::COL_PRIMARY, 0, ui::dp(3.2f));
                    ImGui::Dummy(ImVec2(0, r * 2 + ui::dp(24)));
                } else if (explainState == ExplainState::Success) {
                    ImGuiViewport* vp = ImGui::GetMainViewport();
                    float maxH = vp->Size.y * 0.55f;
                    ImGui::BeginChild("##explaintext", ImVec2(0, maxH), ImGuiChildFlags_None);
                    ImGui::PushFont(app.fontRegular, ui::fsBody());
                    ImGui::PushTextWrapPos(0.0f);
                    ImGui::TextUnformatted(explainText.c_str());
                    ImGui::PopTextWrapPos();
                    ImGui::Dummy(ImVec2(0, ui::dp(8)));
                    ImGui::PushFont(app.fontRegular, ui::fsBodySmall());
                    ImGui::Text("API cost: %s", formatUsdCost(explainCost).c_str());
                    ImGui::PopFont();
                    ImGui::PopFont();
                    ImGui::EndChild();
                } else {
                    ImGui::PushFont(app.fontRegular, ui::fsBody());
                    ImGui::PushTextWrapPos(0.0f);
                    ImGui::TextUnformatted(explainError.c_str());
                    ImGui::PopTextWrapPos();
                    ImGui::PopFont();
                }
                if (explainState != ExplainState::Loading) {
                    ImGui::Dummy(ImVec2(0, ui::dp(4)));
                    ImGui::SetCursorPosX(ImGui::GetContentRegionAvail().x - ui::dp(56));
                    if (ui::textButton("OK")) {
                        explainState = ExplainState::Idle;
                        open = false;
                    }
                }
                ui::endDialog();
            }
            if (!open && explainState != ExplainState::Loading)
                explainState = ExplainState::Idle;
        }

        // Profile dialog
        if (showProfileDialog) {
            if (ui::beginDialog("##profiledlg", &showProfileDialog)) {
                ui::dialogTitle(app, "My profile");
                ImGui::PushFont(app.fontRegular, ui::fsBodySmall());
                ImGui::TextColored(ImGui::ColorConvertU32ToFloat4(ui::COL_ON_SURFACE_VARIANT),
                                   "Custom Instructions");
                ImGui::PopFont();
                ImGui::PushFont(app.fontRegular, ui::fsBody());
                ui::inputMultiline("##profile", profileText, 0, 4);
                ImGui::PopFont();
                ImGui::Dummy(ImVec2(0, ui::dp(4)));
                ImGui::SetCursorPosX(ImGui::GetContentRegionAvail().x - ui::dp(140));
                if (ui::textButton("Cancel")) showProfileDialog = false;
                ImGui::SameLine();
                if (ui::textButton("Save")) {
                    app.prefs.putString(PREF_KEY_AI_USER_PROFILE, trim(profileText));
                    showProfileDialog = false;
                }
                ui::endDialog();
            }
        }
    }

    std::string explainModel;

    void startExplain(App& app, const DailyTotals& totals) {
        std::string apiKey = app.prefs.getString(PREF_KEY_ANTHROPIC_API_KEY, "");
        if (trim(apiKey).empty()) {
            explainState = ExplainState::Error;
            explainError = "No Anthropic API key set.\nGo to Foods Table -> AI button -> gear icon to add your key.";
            return;
        }
        explainModel = app.prefs.getString(PREF_KEY_ANTHROPIC_MODEL, DEFAULT_ANTHROPIC_MODEL);
        std::string userProfile = app.prefs.getString(PREF_KEY_AI_USER_PROFILE, "");
        const WeightEntry* we = weightByDate(totals.date);
        std::string userMessage = formatDailyTotalsForAi(totals, we, userProfile);
        AiRequest req;
        req.apiKey = apiKey;
        req.model = explainModel;
        req.messages = {{"user", userMessage, {}}};
        req.enableWebSearch = false;
        req.nipMode = true;
        req.primaryPrompt = app.explainPrompt;
        req.generalSystemPrompt = "";
        req.extendedThinking = false;
        req.enableFoodLookupTool = false;
        req.db = nullptr;
        req.recipeMode = false;
        explainState = ExplainState::Loading;
        explainJob = AiJob::start(std::move(req));
    }
};

} // namespace

Screen* makeEatenLogScreen(App& app) { return new EatenLogScreen(app); }
