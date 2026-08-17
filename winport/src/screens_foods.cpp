// screens_foods.cpp — FoodSearchScreen + SelectionPanel + LOG/Delete/Convert
// dialogs (FoodSearchScreen and friends in MainActivity.kt).
#include "app.h"
#include "ui.h"
#include "dialogs.h"
#include "helptexts.h"

namespace {

struct FoodSearchScreen : Screen {
    std::string searchQuery;
    std::vector<Food> foods;
    int nutritionalInfoSelection = 0;
    std::optional<Food> selectedFood;
    bool showDeleteDialog = false;
    bool showConvertDialog = false;
    bool showHelp = false;
    bool focusSearch = false;
    ui::FoodListState listState;
    AmountDateTimeDialog logDialog;
    std::string densityText;

    explicit FoodSearchScreen(App& app) {
        nutritionalInfoSelection = app.prefs.getInt(PREF_KEY_NUTRITION_SELECTION_FOOD, 0);
        foods = app.db.readFoodsFromDatabase();
    }

    const char* route() const override { return "foodSearch"; }

    bool onBack(App& app) override {
        (void)app;
        if (selectedFood) { selectedFood.reset(); return true; }
        return true; // root screen: back never exits the app
    }

    void refreshList() { listState.revision++; }

    void consumeResultFlags(App& app) {
        FoodsResultFlags& fr = app.foodsResult;
        if (fr.foodUpdated) {
            foods = app.db.readFoodsFromDatabase();
            if (selectedFood) {
                int id = selectedFood->foodId;
                selectedFood.reset();
                for (auto& f : foods)
                    if (f.foodId == id) { selectedFood = f; break; }
            }
            refreshList();
            fr.foodUpdated = false;
        }
        if (fr.foodInserted) {
            foods = app.db.readFoodsFromDatabase();
            selectedFood.reset();
            refreshList();
            fr.foodInserted = false;
        }
        if (fr.foodInsertedDescription) {
            searchQuery = *fr.foodInsertedDescription;
            foods = app.db.searchFoods(searchQuery);
            selectedFood.reset();
            refreshList();
            fr.foodInsertedDescription.reset();
        }
        if (fr.foodUpdatedDescription) {
            searchQuery = *fr.foodUpdatedDescription;
            foods = app.db.searchFoods(searchQuery);
            selectedFood.reset();
            refreshList();
            fr.foodUpdatedDescription.reset();
        }
        if (fr.sortFoodsDescOnce) {
            foods = app.db.readFoodsSortedByIdDesc();
            refreshList();
            fr.sortFoodsDescOnce = false;
        }
    }

    void draw(App& app) override {
        consumeResultFlags(app);

        auto bar = ui::topBar(app, "Foods Table", true, nutritionalInfoSelection, true);
        if (bar.segSelection >= 0) {
            nutritionalInfoSelection = bar.segSelection;
            app.prefs.putInt(PREF_KEY_NUTRITION_SELECTION_FOOD, nutritionalInfoSelection);
            refreshList();
        }
        if (bar.helpClicked) showHelp = true;
        if (bar.navClicked) app.push(makeEatenLogScreen(app));
        if (ImGui::IsKeyPressed(ImGuiKey_Escape) && !showHelp && !logDialog.open &&
            !showDeleteDialog && !showConvertDialog)
            app.requestBack();

        bool showNutritionalInfo = nutritionalInfoSelection != 0;
        bool showExtraNutrients = nutritionalInfoSelection == 2;

        // Search row
        ImGui::SetCursorPosX(ui::dp(16));
        ImGui::Dummy(ImVec2(0, ui::dp(2)));
        ImGui::SetCursorPosX(ui::dp(16));
        float clearW = ui::dp(36);
        ImGui::PushFont(app.fontRegular, ui::fsBody());
        if (focusSearch) { ImGui::SetKeyboardFocusHere(); focusSearch = false; }
        ImGui::SetNextItemWidth(ImGui::GetContentRegionAvail().x - clearW - ui::dp(24));
        char qbuf[512];
        snprintf(qbuf, sizeof(qbuf), "%s", searchQuery.c_str());
        if (ImGui::InputTextWithHint("##search", "Enter food filter text", qbuf, sizeof(qbuf),
                                     ImGuiInputTextFlags_EnterReturnsTrue)) {
            searchQuery = qbuf;
            foods = trim(searchQuery).empty() ? app.db.readFoodsFromDatabase()
                                              : app.db.searchFoods(searchQuery);
            selectedFood.reset();
            refreshList();
        } else if (ImGui::IsItemDeactivated()) {
            searchQuery = qbuf; // keep typed text even without Enter
        } else if (ImGui::IsItemEdited()) {
            searchQuery = qbuf;
        }
        ImGui::PopFont();
        ImGui::SameLine();
        if (ui::iconTextButton(u8"✕##clear")) {
            searchQuery.clear();
            focusSearch = true;
        }

        // Selection panel height reservation
        bool panelVisible = selectedFood.has_value() && !logDialog.open && !showDeleteDialog && !showConvertDialog;
        float panelH = 0;
        if (panelVisible) {
            ImGui::PushFont(app.fontBold, ui::fsBody());
            float descH = ImGui::CalcTextSize(selectedFood->foodDescription.c_str(), nullptr, false,
                                              ImGui::GetContentRegionAvail().x - ui::dp(64)).y;
            ImGui::PopFont();
            panelH = descH + ui::dp(2 * 16 + 2 * 40 + 24);
        }

        // Food list
        ImGui::Dummy(ImVec2(0, ui::dp(4)));
        ImGui::SetCursorPosX(ui::dp(16));
        ImVec2 listSize(ImGui::GetContentRegionAvail().x - ui::dp(16),
                        ImGui::GetContentRegionAvail().y - panelH - ui::dp(8));
        ui::foodList(app, "##foods", listSize, foods, listState, showNutritionalInfo, showExtraNutrients,
                     [&](const Food& f) {
                         if (selectedFood && selectedFood->foodId == f.foodId)
                             selectedFood.reset();
                         else
                             selectedFood = f;
                     });

        // Selection panel (two rows of buttons, SelectionPanel in Kotlin)
        if (panelVisible && selectedFood) {
            Food food = *selectedFood;
            ImVec2 pos = ImGui::GetCursorScreenPos();
            float w = ImGui::GetContentRegionAvail().x;
            ui::cardBackground(ImVec2(pos.x + ui::dp(8), pos.y),
                               ImVec2(w - ui::dp(16), panelH), ui::COL_CARD);
            ImGui::SetCursorScreenPos(ImVec2(pos.x + ui::dp(24), pos.y + ui::dp(12)));
            ImGui::BeginGroup();
            float innerW = w - ui::dp(48);
            ImGui::PushFont(app.fontBold, ui::fsBody());
            ImVec2 ts = ImGui::CalcTextSize(food.foodDescription.c_str(), nullptr, false, innerW);
            ImGui::SetCursorPosX(ImGui::GetCursorPosX() + std::max(0.0f, (innerW - ts.x) / 2));
            ImGui::PushTextWrapPos(ImGui::GetCursorPosX() + innerW);
            ImGui::TextUnformatted(food.foodDescription.c_str());
            ImGui::PopTextWrapPos();
            ImGui::PopFont();
            ImGui::Dummy(ImVec2(0, ui::dp(6)));

            float bw = (innerW - 4 * ui::dp(6)) / 5;
            ImVec2 bsz(bw, ui::dp(34));
            if (ui::primaryButton("LOG", bsz)) {
                logDialog.openFor("", nowMillis());
            }
            ImGui::SameLine(0, ui::dp(6));
            if (ui::primaryButton("Edit", bsz)) {
                selectedFood.reset();
                if (isRecipeDescription(food.foodDescription))
                    app.push(makeEditRecipeScreen(app, food.foodId));
                else
                    app.push(makeEditFoodScreen(app, food.foodId));
            }
            ImGui::SameLine(0, ui::dp(6));
            if (ui::primaryButton("Copy", bsz)) {
                selectedFood.reset();
                if (isRecipeDescription(food.foodDescription))
                    app.push(makeCopyRecipeScreen(app, food.foodId));
                else
                    app.push(makeCopyFoodScreen(app, food.foodId));
            }
            ImGui::SameLine(0, ui::dp(6));
            if (ui::primaryButton("Convert", bsz)) {
                if (isLiquidDescription(food.foodDescription)) {
                    densityText.clear();
                    showConvertDialog = true;
                } else {
                    app.toast("Convert is only available for liquid foods");
                    selectedFood.reset();
                }
            }
            ImGui::SameLine(0, ui::dp(6));
            if (ui::primaryButton("Delete", bsz)) showDeleteDialog = true;

            float bw4 = (innerW - 3 * ui::dp(6)) / 4;
            ImVec2 bsz4(bw4, ui::dp(34));
            if (ui::primaryButton("Add", bsz4)) {
                selectedFood.reset();
                app.push(makeInsertFoodScreen(app));
            }
            ImGui::SameLine(0, ui::dp(6));
            if (ui::primaryButton("Json", bsz4)) {
                selectedFood.reset();
                app.push(makeAddFoodByJsonScreen(app));
            }
            ImGui::SameLine(0, ui::dp(6));
            if (ui::primaryButton("AI", bsz4)) {
                selectedFood.reset();
                app.push(makeAddFoodByAiScreen(app));
            }
            ImGui::SameLine(0, ui::dp(6));
            if (ui::primaryButton("Utilities", bsz4)) {
                selectedFood.reset();
                app.push(makeUtilitiesScreen(app));
            }
            ImGui::EndGroup();
        }

        drawDialogs(app);
        ui::helpBottomSheet(app, "##foodshelp", &showHelp, foodsHelpText());
    }

    void drawDialogs(App& app) {
        // LOG amount dialog
        if (selectedFood && logDialog.open) {
            Food food = *selectedFood;
            double amount = 0;
            long long dateTime = 0;
            auto res = logDialog.draw(app, "##logdlg", descriptionDisplayName(food.foodDescription),
                                      descriptionUnit(food.foodDescription), &amount, &dateTime);
            if (res == AmountDateTimeDialog::Confirmed) {
                app.db.logEatenFood(food, amount, dateTime);
                selectedFood.reset();
                app.push(makeEatenLogScreen(app));
            } else if (res == AmountDateTimeDialog::Dismissed) {
                selectedFood.reset();
            }
        }

        // Delete confirmation
        if (selectedFood && showDeleteDialog) {
            Food food = *selectedFood;
            bool wasOpen = showDeleteDialog;
            if (ui::beginDialog("##deletefood", &showDeleteDialog)) {
                ui::dialogNoDefaultFocus();   // destructive dialog: nothing pre-armed
                ui::dialogTitle(app, "Delete Food?", IM_COL32(0xC0, 0x18, 0x18, 0xFF), true);
                ImGui::PushFont(app.fontRegular, ui::fsBody());
                ImGui::TextUnformatted("Are you sure you want to delete :");
                ImGui::PushFont(app.fontBold, ui::fsBody());
                ImGui::PushTextWrapPos(0.0f);
                ImGui::TextUnformatted(food.foodDescription.c_str());
                ImGui::PopTextWrapPos();
                ImGui::PopFont();
                ImGui::PopFont();
                ImGui::Dummy(ImVec2(0, ui::dp(8)));
                float cw = ui::dp(110);
                ImGui::SetCursorPosX((ImGui::GetContentRegionAvail().x - cw) / 2 + ImGui::GetCursorPosX());
                if (ui::primaryButton("Confirm", ImVec2(cw, 0))) {
                    bool isRecipeFood = isRecipeDescription(food.foodDescription);
                    bool deleted = app.db.deleteFood(food.foodId);
                    if (isRecipeFood) app.db.deleteRecipesByFoodId(food.foodId);
                    if (!deleted) app.toast("Failed to delete food");
                    foods = trim(searchQuery).empty() ? app.db.readFoodsFromDatabase()
                                                      : app.db.readFoodsFromDatabase();
                    selectedFood.reset();
                    showDeleteDialog = false;
                    refreshList();
                }
                ui::endDialog();
            }
            if (wasOpen && !showDeleteDialog && selectedFood) selectedFood.reset();
        }

        // Convert dialog
        if (selectedFood && showConvertDialog) {
            Food food = *selectedFood;
            bool wasOpen = showConvertDialog;
            if (ui::beginDialog("##convertfood", &showConvertDialog)) {
                ui::dialogTitle(app, descriptionDisplayName(food.foodDescription).c_str());
                ImGui::PushFont(app.fontRegular, ui::fsBody());
                ImGui::PushTextWrapPos(0.0f);
                ImGui::TextUnformatted("Enter density to convert this liquid into a solid food.");
                ImGui::PopTextWrapPos();
                ImGui::Dummy(ImVec2(0, ui::dp(6)));
                float unitW = ImGui::CalcTextSize("g/mL").x;
                ImGui::SetNextItemWidth(ImGui::GetContentRegionAvail().x - unitW - ui::dp(14));
                char dbuf[32];
                snprintf(dbuf, sizeof(dbuf), "%s", densityText.c_str());
                if (ImGui::InputTextWithHint("##density", "Density", dbuf, sizeof(dbuf))) {
                    std::string filtered;
                    for (const char* p = dbuf; *p; ++p)
                            if (isdigit((unsigned char)*p) || *p == '.') filtered += *p;
                    densityText = filtered;
                }
                ImGui::SameLine();
                ImGui::TextUnformatted("g/mL");
                ImGui::PopFont();
                ImGui::Dummy(ImVec2(0, ui::dp(8)));
                auto densOpt = parseDouble(densityText);
                bool valid = densOpt.has_value() && *densOpt > 0;
                float cw = ui::dp(110);
                ImGui::SetCursorPosX((ImGui::GetContentRegionAvail().x - cw) / 2 + ImGui::GetCursorPosX());
                if (ui::primaryButton("Confirm", ImVec2(cw, 0), valid)) {
                    double density = *densOpt;
                    std::string base, suffix;
                    extractDescriptionParts(food.foodDescription, base, suffix);
                    char dtext[32];
                    snprintf(dtext, sizeof(dtext), "%.6f", density);
                    std::string dstr = dtext;
                    while (!dstr.empty() && dstr.back() == '0') dstr.pop_back();
                    if (!dstr.empty() && dstr.back() == '.') dstr.pop_back();
                    std::string newDescription = base + " {density=" + dstr + "g/mL} #";
                    Food newFood = food;
                    newFood.foodId = 0;
                    newFood.foodDescription = newDescription;
                    for (int i = 0; i < NUTRIENT_COUNT; i++) newFood.n[i] = food.n[i] / density;
                    bool inserted = app.db.insertFood(newFood);
                    if (inserted) {
                        searchQuery = newDescription;
                        foods = app.db.searchFoods(newDescription);
                        selectedFood.reset();
                        showConvertDialog = false;
                        refreshList();
                        app.toast("Converted food added");
                    } else {
                        app.toast("Failed to convert food");
                        selectedFood.reset();
                        showConvertDialog = false;
                    }
                }
                ui::endDialog();
            }
            if (wasOpen && !showConvertDialog && selectedFood) selectedFood.reset();
        }
    }
};

} // namespace

Screen* makeFoodSearchScreen(App& app) { return new FoodSearchScreen(app); }
