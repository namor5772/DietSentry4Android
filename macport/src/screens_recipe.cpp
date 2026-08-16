// screens_recipe.cpp — AddRecipeScreen (+ Editing/Copying wrappers), the
// ingredient staging flows (FoodId=0 temp rows, CopyFg=1 edit copies) and the
// recipe Confirm math. Mirrors AddRecipeScreen/EditRecipeScreen/CopyRecipeScreen.
#include "app.h"
#include "ui.h"
#include "dialogs.h"
#include "helptexts.h"
#include <cmath>

namespace {

enum class RecipeMode { Add, Copy, Edit };

struct AddRecipeScreen : Screen {
    RecipeMode mode;
    std::string screenTitle;
    std::optional<int> editingFoodId;
    std::optional<int> copySourceFoodId;

    std::string description;
    std::string searchQuery;
    std::vector<Food> foods;
    std::vector<RecipeItem> recipes;
    std::optional<Food> selectedFood;
    std::optional<RecipeItem> selectedRecipe;
    bool showCannotAddDialog = false;
    AmountDialog recipeAmountDialog;
    AmountDialog editRecipeAmountDialog;
    bool showEditNotesDialog = false;
    std::string notesDraft;
    std::string recipeNotes;
    bool showHelp = false;
    bool focusSearch = false;
    bool loadFailed = false;

    ui::FoodListState foodListState;
    ui::FoodListState recipeListState;

    AddRecipeScreen(App& app, RecipeMode m, int foodId) : mode(m) {
        screenTitle = mode == RecipeMode::Add ? "Add Recipe"
                     : mode == RecipeMode::Copy ? "Copying Recipe" : "Editing Recipe";
        if (mode == RecipeMode::Edit) editingFoodId = foodId;
        if (mode == RecipeMode::Copy) copySourceFoodId = foodId;

        if (mode != RecipeMode::Add) {
            auto food = app.db.getFoodById(foodId);
            if (!food) { loadFailed = true; return; }
            description = removeRecipeMarker(food->foodDescription);
            recipeNotes = food->notes;
        }
        if (mode == RecipeMode::Edit) {
            if (!app.db.copyRecipesForFood(*editingFoodId))
                app.toast("Unable to prepare recipe items for editing");
        } else if (mode == RecipeMode::Copy) {
            if (!app.db.duplicateRecipesToFoodIdZero(*copySourceFoodId))
                app.toast("Unable to prepare recipe items for copying");
        }
        searchQuery = mode == RecipeMode::Add ? app.sessionAddRecipeSearchQuery
                     : mode == RecipeMode::Copy ? app.sessionCopyRecipeSearchQuery
                     : app.sessionEditRecipeSearchQuery;
        foods = app.db.readFoodsFromDatabase();
        recipes = loadRecipes(app);
    }

    const char* route() const override {
        switch (mode) {
        case RecipeMode::Add: return "addRecipe";
        case RecipeMode::Copy: return "copyRecipe";
        default: return "editRecipe";
        }
    }

    std::vector<RecipeItem> loadRecipes(App& app) {
        return editingFoodId ? app.db.readCopiedRecipes(*editingFoodId) : app.db.readRecipes();
    }

    void storeSearchQuery(App& app) {
        if (mode == RecipeMode::Add) app.sessionAddRecipeSearchQuery = searchQuery;
        else if (mode == RecipeMode::Copy) app.sessionCopyRecipeSearchQuery = searchQuery;
        else app.sessionEditRecipeSearchQuery = searchQuery;
    }

    void exitAddRecipe(App& app) {
        app.db.deleteRecipesWithFoodIdZero();
        app.db.deleteAllCopiedRecipes();
        if (!app.popTo("foodSearch")) app.pop();
    }

    bool onBack(App& app) override {
        if (showHelp) { showHelp = false; return true; }
        if (showEditNotesDialog) { showEditNotesDialog = false; return true; }
        if (selectedFood) {
            selectedFood.reset();
            showCannotAddDialog = false;
            recipeAmountDialog.open = false;
            return true;
        }
        if (selectedRecipe) {
            selectedRecipe.reset();
            editRecipeAmountDialog.open = false;
            return true;
        }
        exitAddRecipe(app);
        return true;
    }

    void draw(App& app) override {
        if (loadFailed) {
            app.toast("Food not found");
            app.pop();
            return;
        }
        auto bar = ui::topBar(app, screenTitle.c_str(), false, 0, false);
        if (bar.helpClicked) showHelp = true;
        if (bar.navClicked) exitAddRecipe(app);
        if (ImGui::IsKeyPressed(ImGuiKey_Escape) && !showHelp && !showEditNotesDialog &&
            !showCannotAddDialog && !recipeAmountDialog.open && !editRecipeAmountDialog.open)
            app.requestBack();

        float bottomH = ui::dp(60);
        ImGui::SetCursorPosX(ui::dp(16));
        float w = ImGui::GetContentRegionAvail().x - ui::dp(16);

        // Description field (single-line, as on Android recipe screens)
        ImGui::PushFont(app.fontRegular, ui::fsBody());
        ImGui::AlignTextToFramePadding();
        ImGui::TextUnformatted("Description");
        ImGui::SameLine();
        ui::inputText("##recipedesc", description, nullptr, 0,
                      w - (ImGui::GetCursorPosX() - ui::dp(16)));
        ImGui::PopFont();

        // Search field
        ImGui::SetCursorPosX(ui::dp(16));
        float clearW = ui::dp(36);
        ImGui::PushFont(app.fontRegular, ui::fsBody());
        if (focusSearch) { ImGui::SetKeyboardFocusHere(); focusSearch = false; }
        ImGui::SetNextItemWidth(w - clearW - ui::dp(8));
        char qbuf[512];
        snprintf(qbuf, sizeof(qbuf), "%s", searchQuery.c_str());
        if (ImGui::InputTextWithHint("##rsearch", "Enter food filter text", qbuf, sizeof(qbuf),
                                     ImGuiInputTextFlags_EnterReturnsTrue)) {
            searchQuery = qbuf;
            storeSearchQuery(app);
            foods = trim(searchQuery).empty() ? app.db.readFoodsFromDatabase()
                                              : app.db.searchFoods(searchQuery);
            foodListState.revision++;
        } else if (ImGui::IsItemEdited()) {
            searchQuery = qbuf;
            storeSearchQuery(app);
        }
        ImGui::PopFont();
        ImGui::SameLine();
        if (ui::iconTextButton(u8"✕##rclear")) {
            searchQuery.clear();
            storeSearchQuery(app);
            focusSearch = true;
        }

        // Panel space when an ingredient is selected
        float panelH = 0;
        if (selectedRecipe) {
            ImGui::PushFont(app.fontBold, ui::fsBody());
            float descH = ImGui::CalcTextSize(selectedRecipe->foodDescription.c_str(), nullptr, false,
                                              w - ui::dp(48)).y;
            ImGui::PopFont();
            panelH = descH + ui::dp(16 * 2 + 40 + 8);
        }

        float availY = ImGui::GetContentRegionAvail().y - bottomH - panelH;
        float labelH = ui::dp(26);
        float listH = std::max(ui::dp(60), (availY - labelH - ui::dp(10)) / 2);

        // Upper: foods
        ImGui::SetCursorPosX(ui::dp(16));
        ui::foodList(app, "##rfoods", ImVec2(w, listH), foods, foodListState, false, false,
                     [&](const Food& f) {
                         selectedFood = f;
                         selectedRecipe.reset();
                         if (isLiquidDescription(f.foodDescription)) {
                             showCannotAddDialog = true;
                             recipeAmountDialog.open = false;
                         } else {
                             recipeAmountDialog.openFor("");
                             showCannotAddDialog = false;
                         }
                     });

        // Ingredients label
        double totalRecipeAmount = 0;
        for (const auto& r : recipes) totalRecipeAmount += r.amount;
        ImGui::SetCursorPosX(ui::dp(16));
        ImGui::PushFont(app.fontRegular, ui::fsBody());
        ImGui::Text("Ingredients %s (g) Total", formatAmount(totalRecipeAmount, 1).c_str());
        ImGui::PopFont();

        // Lower: recipe ingredients
        ImGui::SetCursorPosX(ui::dp(16));
        ui::recipeList(app, "##ringredients", ImVec2(w, listH), recipes, recipeListState,
                       selectedRecipe ? std::optional<int>(selectedRecipe->recipeId) : std::nullopt,
                       [&](const RecipeItem& r) {
                           if (selectedRecipe && selectedRecipe->recipeId == r.recipeId)
                               selectedRecipe.reset();
                           else
                               selectedRecipe = r;
                       });

        // Ingredient selection panel
        if (selectedRecipe) {
            RecipeItem recipe = *selectedRecipe;
            ImVec2 pos = ImGui::GetCursorScreenPos();
            ui::cardBackground(ImVec2(pos.x + ui::dp(16), pos.y), ImVec2(w, panelH), ui::COL_CARD);
            ImGui::SetCursorScreenPos(ImVec2(pos.x + ui::dp(32), pos.y + ui::dp(12)));
            ImGui::BeginGroup();
            float innerW = w - ui::dp(32);
            ImGui::PushFont(app.fontBold, ui::fsBody());
            ImVec2 ts = ImGui::CalcTextSize(recipe.foodDescription.c_str(), nullptr, false, innerW);
            ImGui::SetCursorPosX(ImGui::GetCursorPosX() + std::max(0.0f, (innerW - ts.x) / 2));
            ImGui::PushTextWrapPos(ImGui::GetCursorPosX() + innerW);
            ImGui::TextUnformatted(recipe.foodDescription.c_str());
            ImGui::PopTextWrapPos();
            ImGui::PopFont();
            ImGui::Dummy(ImVec2(0, ui::dp(4)));
            float bw = ui::dp(110);
            float gap = (innerW - 2 * bw) / 3;
            ImGui::SetCursorPosX(ImGui::GetCursorPosX() + gap);
            if (ui::primaryButton("Edit", ImVec2(bw, 0))) {
                char amt[32];
                snprintf(amt, sizeof(amt), "%g", recipe.amount);
                editRecipeAmountDialog.openFor(amt);
            }
            ImGui::SameLine(0, gap);
            if (ui::primaryButton("Delete", ImVec2(bw, 0))) {
                if (!app.db.deleteRecipe(recipe.recipeId))
                    app.toast("Unable to delete recipe item");
                recipes = loadRecipes(app);
                recipeListState.revision++;
                selectedRecipe.reset();
            }
            ImGui::EndGroup();
        }

        // Bottom bar: Set notes / Edit notes / Confirm
        ImGui::Dummy(ImVec2(0, ui::dp(6)));
        ImGui::SetCursorPosX(ui::dp(16));
        if (ui::primaryButton("Set notes")) {
            std::string joined;
            for (size_t i = 0; i < recipes.size(); i++) {
                int roundedAmount = (int)std::llround(recipes[i].amount);
                if (i) joined += "\n";
                joined += std::to_string(roundedAmount) + " g : " + recipes[i].foodDescription;
            }
            recipeNotes = joined;
        }
        ImGui::SameLine(0, ui::dp(8));
        if (ui::primaryButton("Edit notes")) {
            notesDraft = recipeNotes;
            showEditNotesDialog = true;
        }
        ImGui::SameLine();
        float cw = ui::dp(120);
        ImGui::SetCursorPosX(ImGui::GetWindowWidth() - cw - ui::dp(16));
        if (ui::primaryButton("Confirm", ImVec2(cw, 0))) confirm(app);

        drawDialogs(app);
        ui::helpBottomSheet(app, "##recipehelp", &showHelp, buildRecipeHelpText(screenTitle));
    }

    void drawDialogs(App& app) {
        // Cannot-add (liquid) dialog
        if (showCannotAddDialog) {
            bool wasOpen = showCannotAddDialog;
            if (ui::beginDialog("##cannotadd", &showCannotAddDialog)) {
                ui::dialogTitle(app, "CANNOT ADD THIS FOOD");
                ImGui::PushFont(app.fontRegular, ui::fsBody());
                ImGui::PushTextWrapPos(0.0f);
                ImGui::TextUnformatted("Only foods measured in grams can be added to a recipe");
                ImGui::PopTextWrapPos();
                ImGui::PopFont();
                ImGui::Dummy(ImVec2(0, ui::dp(8)));
                float cw = ui::dp(90);
                ImGui::SetCursorPosX((ImGui::GetContentRegionAvail().x - cw) / 2 + ImGui::GetCursorPosX());
                if (ui::primaryButton("OK", ImVec2(cw, 0))) showCannotAddDialog = false;
                ui::endDialog();
            }
            if (wasOpen && !showCannotAddDialog) selectedFood.reset();
        }

        // Amount dialog for new ingredient
        if (selectedFood && recipeAmountDialog.open) {
            Food food = *selectedFood;
            double amount = 0;
            auto res = recipeAmountDialog.draw(app, "##ringamount",
                                               descriptionDisplayName(food.foodDescription),
                                               descriptionUnit(food.foodDescription), &amount);
            if (res == AmountDialog::Confirmed) {
                bool inserted = app.db.insertRecipeFromFood(
                    food, amount, editingFoodId.value_or(0), editingFoodId ? 1 : 0);
                if (!inserted) app.toast("Unable to add item to recipe");
                else {
                    recipes = loadRecipes(app);
                    recipeListState.revision++;
                    selectedRecipe.reset();
                }
                selectedFood.reset();
            } else if (res == AmountDialog::Dismissed) {
                selectedFood.reset();
            }
        }

        // Edit ingredient amount
        if (selectedRecipe && editRecipeAmountDialog.open) {
            RecipeItem recipe = *selectedRecipe;
            double newAmount = 0;
            auto res = editRecipeAmountDialog.draw(app, "##ringedit",
                                                   descriptionDisplayName(recipe.foodDescription),
                                                   isLiquidDescription(recipe.foodDescription) ? "mL" : "g",
                                                   &newAmount);
            if (res == AmountDialog::Confirmed) {
                if (newAmount <= 0.0) {
                    app.toast("Amount must be greater than zero");
                } else {
                    double factor = recipe.amount == 0.0 ? 0.0 : newAmount / recipe.amount;
                    RecipeItem updated = recipe;
                    updated.amount = newAmount;
                    for (int i = 0; i < NUTRIENT_COUNT; i++)
                        updated.n[i] = roundTo2dp(recipe.n[i] * factor);
                    if (!app.db.updateRecipe(updated))
                        app.toast("Unable to update recipe item");
                    recipes = loadRecipes(app);
                    recipeListState.revision++;
                    selectedRecipe.reset();
                }
            } else if (res == AmountDialog::Dismissed) {
                selectedRecipe.reset();
            }
        }

        // Edit notes dialog
        if (showEditNotesDialog) {
            if (ui::beginDialog("##editnotes", &showEditNotesDialog)) {
                ui::dialogTitle(app, "Edit notes");
                ImGui::PushFont(app.fontRegular, ui::fsBody());
                ui::inputMultiline("##notesedit", notesDraft, 0, 6, nullptr, 8);   // grows to 8 lines, then scrolls (as on Android)
                ImGui::PopFont();
                ImGui::Dummy(ImVec2(0, ui::dp(8)));
                float cw = ui::dp(110);
                ImGui::SetCursorPosX((ImGui::GetContentRegionAvail().x - cw) / 2 + ImGui::GetCursorPosX());
                if (ui::primaryButton("Confirm", ImVec2(cw, 0))) {
                    recipeNotes = notesDraft;
                    showEditNotesDialog = false;
                }
                ui::endDialog();
            }
        }
    }

    void confirm(App& app) {
        double totalAmount = 0;
        for (const auto& r : recipes) totalAmount += r.amount;
        std::string sanitizedDescription = trim(stripTrailingRecipeSuffix(description));
        if (sanitizedDescription.empty()) {
            app.toast("Please enter a description");
            return;
        }
        if (totalAmount <= 0.0) {
            app.toast("Add at least one ingredient");
            return;
        }
        Nutrients totals;
        for (const auto& r : recipes) totals.add(r.n);
        double scale = 100.0 / totalAmount;
        std::string recipeWeightText = formatNumber(totalAmount, 0);

        Food baseFood;
        baseFood.foodId = 0;
        baseFood.foodDescription = sanitizedDescription + " {recipe=" + recipeWeightText + "g}";
        baseFood.n = totals.scaled(scale);
        baseFood.notes = recipeNotes;

        if (!editingFoodId) {
            auto newFoodId = app.db.insertFoodReturningId(baseFood);
            if (!newFoodId) {
                app.toast("Unable to save recipe to Foods table");
                return;
            }
            if (!app.db.updateRecipeFoodIdForTemporaryRecords(*newFoodId)) {
                app.toast("Recipe items not linked to new food");
                app.db.deleteRecipesWithFoodIdZero();
            }
            recipes = loadRecipes(app);
            app.foodsResult.foodInserted = true;
            app.foodsResult.foodInsertedDescription = baseFood.foodDescription;
            app.foodsResult.sortFoodsDescOnce = true;
            if (!app.popTo("foodSearch")) app.pop();
        } else {
            Food updatedFood = baseFood;
            updatedFood.foodId = *editingFoodId;
            if (!app.db.updateFood(updatedFood)) {
                app.toast("Unable to update recipe food");
                return;
            }
            if (!app.db.replaceOriginalRecipesWithCopies(*editingFoodId)) {
                app.toast("Unable to update recipe items");
                return;
            }
            recipes = loadRecipes(app);
            app.foodsResult.foodUpdated = true;
            app.foodsResult.foodUpdatedDescription = baseFood.foodDescription;
            if (!app.popTo("foodSearch")) app.pop();
        }
    }
};

} // namespace

Screen* makeAddRecipeScreen(App& app) { return new AddRecipeScreen(app, RecipeMode::Add, 0); }
Screen* makeEditRecipeScreen(App& app, int foodId) { return new AddRecipeScreen(app, RecipeMode::Edit, foodId); }
Screen* makeCopyRecipeScreen(App& app, int foodId) { return new AddRecipeScreen(app, RecipeMode::Copy, foodId); }
