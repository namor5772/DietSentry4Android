// screens_editfood.cpp — EditFoodScreen / CopyFoodScreen / InsertFoodScreen.
// One shared form: Description (3-line), 23 numeric fields, Notes (3-line),
// Confirm in a bottom bar. Mirrors the Kotlin screens' behaviour exactly.
#include "app.h"
#include "ui.h"
#include "helptexts.h"

namespace {

enum class FormMode { Edit, Copy, Insert };

struct FoodFormScreen : Screen {
    FormMode mode;
    int foodId = 0;
    std::optional<Food> food;      // Edit/Copy source
    bool loadFailed = false;

    std::string description;
    std::string descriptionSuffix; // Edit only: reinstated on confirm
    bool isLiquidFood = false;
    std::string values[NUTRIENT_COUNT];
    std::string notes;
    std::string selectedType = "Solid"; // Insert only
    bool showHelp = false;

    FoodFormScreen(App& app, FormMode m, int id) : mode(m), foodId(id) {
        if (mode == FormMode::Insert) return;
        food = app.db.getFoodById(foodId);
        if (!food) { loadFailed = true; return; }
        std::string base, suffix;
        extractDescriptionParts(food->foodDescription, base, suffix);
        description = base;
        descriptionSuffix = suffix;
        isLiquidFood = suffix == " mL" || suffix == " mL#";
        for (int i = 0; i < NUTRIENT_COUNT; i++) values[i] = formatOneDecimal(food->n[i]);
        notes = food->notes;
    }

    const char* route() const override {
        switch (mode) {
        case FormMode::Edit: return "editFood";
        case FormMode::Copy: return "copyFood";
        default: return "insertFood";
        }
    }

    std::string title() const {
        switch (mode) {
        case FormMode::Edit: return isLiquidFood ? "Editing Liquid Food" : "Editing Solid Food";
        case FormMode::Copy: return isLiquidFood ? "Copying Liquid Food" : "Copying Solid Food";
        default: return "Add Food";
        }
    }

    bool isValid() const {
        if (trim(description).empty()) return false;
        for (int i = 0; i < NUTRIENT_COUNT; i++) {
            if (mode == FormMode::Insert) {
                if (!values[i].empty() && !parseDouble(values[i])) return false;
            } else {
                if (!parseDouble(values[i])) return false;
            }
        }
        return true;
    }

    void draw(App& app) override {
        if (loadFailed) {
            app.toast("Food not found");
            app.pop();
            return;
        }
        auto bar = ui::topBar(app, title().c_str(), false, 0, false);
        if (bar.helpClicked) showHelp = true;
        if (bar.navClicked) app.pop();
        if (ImGui::IsKeyPressed(ImGuiKey_Escape) && !showHelp) app.requestBack();

        float bottomH = ui::dp(64);
        ImGui::SetCursorPosX(0);
        // NavFlattened: Tab walks from the top bar into the form fields and on to
        // the buttons below it.
        ImGui::BeginChild("##form", ImVec2(0, ImGui::GetContentRegionAvail().y - bottomH),
                          ImGuiChildFlags_NavFlattened);
        ImGui::SetCursorPosX(ui::dp(16));
        ImGui::BeginGroup();
        float formW = ImGui::GetContentRegionAvail().x - ui::dp(16);

        if (mode == FormMode::Insert) {
            // Solid / Liquid / Recipe radio row
            ImGui::PushFont(app.fontRegular, ui::fsBody());
            float rw = formW / 3;
            if (ui::radioM("Solid", selectedType == "Solid")) selectedType = "Solid";
            ImGui::SameLine(rw);
            if (ui::radioM("Liquid", selectedType == "Liquid")) selectedType = "Liquid";
            ImGui::SameLine(rw * 2);
            if (ui::radioM("Recipe", selectedType == "Recipe")) {
                selectedType = "Recipe";
                app.push(makeAddRecipeScreen(app));
            }
            ImGui::PopFont();
            ImGui::Dummy(ImVec2(0, ui::dp(6)));
        }

        ImGui::PushFont(app.fontRegular, ui::fsBody());
        // Description: label + 3-row input filling width
        ImGui::TextUnformatted("Description");
        ImGui::SameLine();
        ui::inputMultiline("##desc", description, formW - ImGui::GetCursorPosX() + ui::dp(16), 3);
        ImGui::Dummy(ImVec2(0, ui::dp(2)));

        // 23 numeric fields: label at left, input at right ~28% width
        float inputW = std::max(ui::dp(96), formW * 0.28f);
        for (int i = 0; i < NUTRIENT_COUNT; i++) {
            ImGui::PushID(i);
            ImGui::AlignTextToFramePadding();
            ImGui::TextUnformatted(NUTRIENT_EDIT_LABELS[i]);
            ImGui::SameLine(formW * 0.5f);
            ImGui::SetNextItemWidth(inputW);
            char buf[48];
            snprintf(buf, sizeof(buf), "%s", values[i].c_str());
            if (ImGui::InputText("##val", buf, sizeof(buf))) {
                std::string filtered;
                for (const char* p = buf; *p; ++p)
                        if (isdigit((unsigned char)*p) || *p == '.') filtered += *p;
                values[i] = filtered;
            }
            ImGui::PopID();
        }

        ImGui::TextUnformatted("Notes");
        ImGui::SameLine();
        ui::inputMultiline("##notes", notes, formW - ImGui::GetCursorPosX() + ui::dp(16), 3);
        ImGui::PopFont();
        ImGui::EndGroup();
        ImGui::Dummy(ImVec2(0, ui::dp(10)));
        ImGui::EndChild();

        // Bottom bar with centered Confirm
        ImGui::Dummy(ImVec2(0, ui::dp(8)));
        float cw = ui::dp(130);
        ImGui::SetCursorPosX((ImGui::GetContentRegionAvail().x - cw) / 2);
        if (ui::primaryButton("Confirm", ImVec2(cw, ui::dp(38)), isValid())) confirm(app);

        std::string help = mode == FormMode::Insert ? insertHelpText()
                          : mode == FormMode::Edit ? editHelpText(isLiquidFood)
                          : copyHelpText(isLiquidFood);
        ui::helpBottomSheet(app, "##edithelp", &showHelp, help);
    }

    void confirm(App& app) {
        if (mode == FormMode::Edit) {
            Food updated = *food;
            updated.foodDescription = description + descriptionSuffix;
            for (int i = 0; i < NUTRIENT_COUNT; i++) updated.n[i] = parseDouble(values[i]).value_or(0.0);
            updated.notes = notes;
            if (app.db.updateFood(updated)) {
                app.foodsResult.foodUpdated = true;
                app.foodsResult.foodUpdatedDescription = updated.foodDescription;
                app.pop();
            } else {
                app.toast("Failed to update food");
            }
        } else if (mode == FormMode::Copy) {
            std::string baseDescription = description;
            while (!baseDescription.empty() && isspace((unsigned char)baseDescription.back()))
                baseDescription.pop_back();
            std::string withUnit = isLiquidFood ? baseDescription + " mL" : baseDescription;
            std::string trimmedUnit = withUnit;
            while (!trimmedUnit.empty() && isspace((unsigned char)trimmedUnit.back()))
                trimmedUnit.pop_back();
            std::string processedDescription;
            if (endsWith(trimmedUnit, "#")) processedDescription = withUnit;
            else if (isLiquidFood) processedDescription = withUnit + "#";
            else processedDescription = withUnit + " #";

            Food newFood;
            newFood.foodId = 0;
            newFood.foodDescription = processedDescription;
            for (int i = 0; i < NUTRIENT_COUNT; i++) newFood.n[i] = parseDouble(values[i]).value_or(0.0);
            newFood.notes = notes;
            if (app.db.insertFood(newFood)) {
                app.foodsResult.foodInserted = true;
                app.foodsResult.foodInsertedDescription = processedDescription;
                app.pop();
            } else {
                app.toast("Failed to copy food");
            }
        } else {
            if (selectedType == "Recipe") {
                app.push(makeAddRecipeScreen(app));
                return;
            }
            std::string processedDescription =
                selectedType == "Solid" ? description + " #" :
                selectedType == "Liquid" ? description + " mL#" : description;
            Food newFood;
            newFood.foodId = 0;
            newFood.foodDescription = processedDescription;
            for (int i = 0; i < NUTRIENT_COUNT; i++) newFood.n[i] = parseDouble(values[i]).value_or(0.0);
            newFood.notes = notes;
            if (app.db.insertFood(newFood)) {
                app.foodsResult.foodInserted = true;
                app.foodsResult.foodInsertedDescription = processedDescription;
                app.pop();
            } else {
                app.toast("Failed to insert food");
            }
        }
    }
};

} // namespace

Screen* makeEditFoodScreen(App& app, int foodId) { return new FoodFormScreen(app, FormMode::Edit, foodId); }
Screen* makeCopyFoodScreen(App& app, int foodId) { return new FoodFormScreen(app, FormMode::Copy, foodId); }
Screen* makeInsertFoodScreen(App& app) { return new FoodFormScreen(app, FormMode::Insert, 0); }
