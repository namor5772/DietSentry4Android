// screens_json.cpp — AddFoodByJsonScreen + insertRecipeFromRecipeJson.
#include "app.h"
#include "ui.h"
#include "helptexts.h"
#include "json.hpp"

using json = nlohmann::ordered_json;

// org.json's getDouble coerces numeric strings; mirror that.
static double jsonGetDouble(const json& j, const char* key) {
    const json& v = j.at(key);
    if (v.is_number()) return v.get<double>();
    if (v.is_string()) {
        auto d = parseDouble(v.get<std::string>());
        if (d) return *d;
    }
    throw std::runtime_error(std::string("field not numeric: ") + key);
}

struct RecipeInsertResult {
    bool ok = false;
    std::string value;   // new FoodDescription on success
    std::string error;
};

// The {recipe=Xg} marker means "this row has ingredient rows in the Recipe table". A NIP
// (non-recipe) JSON creates none, so a description carrying the marker — Claude occasionally
// names a photographed composite dish that way in NIP mode — would become a phantom recipe:
// Edit opens the recipe editor with no ingredients. Normalise it to a plain AI/user-added
// food instead: drop the marker (and any trailing '*'/'#') and re-apply the " #" / "mL#" suffix.
static std::string normalizeNonRecipeDescription(const std::string& description) {
    if (!isRecipeDescription(description)) return description;
    std::string base = removeRecipeMarker(description);
    if (base.empty()) return base;
    return isLiquidDescription(base) ? base + "#" : base + " #";
}

// Recipe JSON: the app appends its own " (AI) {recipe=Xg}", so drop any marker or "(AI)" tag
// Claude already put on the name (else you get "X (AI)  (AI) {recipe=965g}").
static std::string baseRecipeName(const std::string& rawDescription) {
    std::string s = removeRecipeMarker(rawDescription);
    for (;;) {
        while (!s.empty() && isspace((unsigned char)s.back())) s.pop_back();
        size_t n = s.size();
        bool aiTag = n >= 4 && s[n-4] == '(' && (s[n-3] == 'A' || s[n-3] == 'a') &&
                     (s[n-2] == 'I' || s[n-2] == 'i') && s[n-1] == ')';
        if (!aiTag) break;
        s.resize(n - 4);
    }
    return s;
}

// Port of insertRecipeFromRecipeJson (MainActivity.kt).
RecipeInsertResult insertRecipeFromRecipeJson(const json& j, Db& db) {
    RecipeInsertResult out;
    std::string rawDescription;
    if (j.contains("FoodDescription") && j["FoodDescription"].is_string())
        rawDescription = trim(j["FoodDescription"].get<std::string>());
    if (rawDescription.empty()) { out.error = "Recipe FoodDescription is required"; return out; }
    if (!j.contains("ingredients") || !j["ingredients"].is_array()) {
        out.error = "Recipe ingredients[] is required";
        return out;
    }
    const json& ingredientsJson = j["ingredients"];
    if (ingredientsJson.empty()) { out.error = "Recipe needs at least one ingredient"; return out; }

    struct ResolvedIngredient { Food food; double amount; };
    std::vector<ResolvedIngredient> resolved;
    for (size_t i = 0; i < ingredientsJson.size(); i++) {
        const json& ing = ingredientsJson[i];
        if (!ing.is_object()) {
            out.error = "ingredients[" + std::to_string(i) + "] is not an object";
            return out;
        }
        int fid = ing.value("FoodId", -1);
        if (fid <= 0) {
            out.error = "ingredients[" + std::to_string(i) + "] missing FoodId";
            return out;
        }
        double amount = -1.0;
        if (ing.contains("AmountUsed") && ing["AmountUsed"].is_number())
            amount = ing["AmountUsed"].get<double>();
        if (!(amount > 0.0)) {
            out.error = "ingredients[" + std::to_string(i) + "] AmountUsed must be > 0";
            return out;
        }
        auto food = db.getFoodById(fid);
        if (!food) {
            out.error = "FoodId " + std::to_string(fid) + " not in Foods table";
            return out;
        }
        if (isLiquidDescription(food->foodDescription)) {
            out.error = "FoodId " + std::to_string(fid) + " is a liquid; recipes need solids";
            return out;
        }
        resolved.push_back({*food, amount});
    }

    // Clear orphan temp rows from any aborted manual recipe-add session.
    db.deleteRecipesWithFoodIdZero();

    for (const auto& ri : resolved) {
        if (!db.insertRecipeFromFood(ri.food, ri.amount, 0, 0)) {
            db.deleteRecipesWithFoodIdZero();
            out.error = "Unable to add ingredient to Recipe table";
            return out;
        }
    }

    double totalAmount = 0;
    for (const auto& ri : resolved) totalAmount += ri.amount;
    Nutrients aggregate;
    for (const auto& ri : resolved)
        aggregate.add(ri.food.n.scaled(ri.amount / 100.0));
    double scale = 100.0 / totalAmount;

    std::string sanitized = baseRecipeName(rawDescription);
    if (sanitized.empty()) {
        db.deleteRecipesWithFoodIdZero();
        out.error = "Recipe FoodDescription is required";
        return out;
    }
    std::string recipeWeightText = formatNumber(totalAmount, 0);
    std::string notes;
    if (j.contains("notes") && j["notes"].is_string()) notes = trim(j["notes"].get<std::string>());

    Food baseFood;
    baseFood.foodId = 0;
    // " (AI)" before the {recipe=Xg} marker mirrors the " (AI) #" convention.
    baseFood.foodDescription = sanitized + " (AI) {recipe=" + recipeWeightText + "g}";
    baseFood.n = aggregate.scaled(scale);
    baseFood.notes = notes;

    auto newFoodId = db.insertFoodReturningId(baseFood);
    if (!newFoodId) {
        db.deleteRecipesWithFoodIdZero();
        out.error = "Unable to save recipe to Foods table";
        return out;
    }
    if (!db.updateRecipeFoodIdForTemporaryRecords(*newFoodId)) {
        db.deleteRecipesWithFoodIdZero();
        out.error = "Recipe items not linked to new food";
        return out;
    }
    out.ok = true;
    out.value = baseFood.foodDescription;
    return out;
}

namespace {

struct AddFoodByJsonScreen : Screen {
    std::string jsonText;
    std::optional<double> initialCallCost;
    bool showHelp = false;

    explicit AddFoodByJsonScreen(App& app) {
        if (app.sessionPrefilledJson) {
            jsonText = *app.sessionPrefilledJson;
            app.sessionPrefilledJson.reset();
        }
        if (app.sessionPrefilledJsonCost) {
            initialCallCost = *app.sessionPrefilledJsonCost;
            app.sessionPrefilledJsonCost.reset();
        }
    }

    const char* route() const override { return "addFoodByJson"; }

    void draw(App& app) override {
        auto bar = ui::topBar(app, "Add Food using Json", false, 0, false);
        if (bar.helpClicked) showHelp = true;
        if (bar.navClicked) app.pop();
        if (ImGui::IsKeyPressed(ImGuiKey_Escape) && !showHelp) app.requestBack();

        float bottomH = ui::dp(64);
        ImGui::SetCursorPosX(ui::dp(16));
        ImGui::BeginGroup();
        float w = ImGui::GetContentRegionAvail().x - ui::dp(16);

        if (initialCallCost) {
            ImVec2 pos = ImGui::GetCursorScreenPos();
            ImGui::PushFont(app.fontRegular, ui::fsBodySmall());
            std::string costLabel = "AI call cost: " + formatUsdCost(*initialCallCost);
            float bh = ImGui::GetTextLineHeight() + ui::dp(8);
            ImGui::GetWindowDrawList()->AddRectFilled(pos, ImVec2(pos.x + w, pos.y + bh),
                                                      IM_COL32(0xEF, 0xE9, 0xF3, 0xFF), ui::dp(4));
            ImGui::SetCursorScreenPos(ImVec2(pos.x + ui::dp(12), pos.y + ui::dp(4)));
            ImGui::TextUnformatted(costLabel.c_str());
            ImGui::PopFont();
            ImGui::SetCursorScreenPos(ImVec2(pos.x, pos.y + bh));
            ImGui::Dummy(ImVec2(0, ui::dp(8)));
        }

        float textH = ImGui::GetContentRegionAvail().y - bottomH - ui::dp(8);
        ImGui::PushFont(app.fontMono, ui::fsBodySmall());
        // large capacity managed by std::string resize callback
        struct Cb {
            static int resize(ImGuiInputTextCallbackData* data) {
                if (data->EventFlag == ImGuiInputTextFlags_CallbackResize) {
                    std::string* str = (std::string*)data->UserData;
                    str->resize(data->BufTextLen);
                    data->Buf = (char*)str->c_str();
                }
                return 0;
            }
        };
        ImGui::InputTextMultiline("##json", (char*)jsonText.c_str(), jsonText.capacity() + 1,
                                  ImVec2(w, textH),
                                  ImGuiInputTextFlags_CallbackResize | ImGuiInputTextFlags_WordWrap,
                                  Cb::resize, &jsonText);
        if (jsonText.empty() && !ImGui::IsItemActive()) {
            ImVec2 min = ImGui::GetItemRectMin();
            ImGui::GetWindowDrawList()->AddText(
                ImVec2(min.x + ui::dp(8), min.y + ui::dp(6)), ui::COL_OUTLINE, "Paste JSON here");
        }
        ImGui::PopFont();
        ImGui::EndGroup();

        ImGui::Dummy(ImVec2(0, ui::dp(8)));
        float cw = ui::dp(130);
        ImGui::SetCursorPosX((ImGui::GetContentRegionAvail().x - cw) / 2);
        if (ui::primaryButton("Confirm", ImVec2(cw, ui::dp(38)))) confirm(app);

        ui::helpBottomSheet(app, "##jsonhelp", &showHelp, jsonHelpText());
    }

    void confirm(App& app) {
        size_t jsonStart = jsonText.find('{');
        size_t jsonEnd = jsonText.rfind('}');
        if (jsonStart == std::string::npos || jsonEnd == std::string::npos || jsonEnd <= jsonStart) {
            app.toast("Please paste valid JSON");
            return;
        }
        std::string payload = jsonText.substr(jsonStart, jsonEnd - jsonStart + 1);
        json j = json::parse(payload, nullptr, false);
        if (j.is_discarded() || !j.is_object()) {
            app.toast("Invalid JSON or missing fields");
            return;
        }
        if (j.value("type", "") == "recipe") {
            RecipeInsertResult res = insertRecipeFromRecipeJson(j, app.db);
            if (res.ok) {
                app.foodsResult.foodInserted = true;
                app.foodsResult.foodInsertedDescription = res.value;
                app.foodsResult.sortFoodsDescOnce = true;
                app.popTo("foodSearch");
            } else {
                app.toast(res.error.empty() ? "Failed to insert recipe" : res.error);
            }
            return;
        }
        try {
            std::string description;
            if (j.contains("FoodDescription") && j["FoodDescription"].is_string())
                description = trim(j["FoodDescription"].get<std::string>());
            else
                throw std::runtime_error("FoodDescription missing");
            // NIP JSON must not carry the {recipe=…} marker (that means "has Recipe rows",
            // which only Recipe JSON creates) — normalise it away.
            description = normalizeNonRecipeDescription(description);
            if (description.empty()) {
                app.toast("FoodDescription is required");
                return;
            }
            std::string notes;
            if (j.contains("notes") && j["notes"].is_string()) notes = trim(j["notes"].get<std::string>());
            Food newFood;
            newFood.foodId = 0;
            newFood.foodDescription = description;
            for (int i = 0; i < NUTRIENT_COUNT; i++)
                newFood.n[i] = jsonGetDouble(j, NUTRIENT_COLUMNS[i]);
            newFood.notes = notes;
            if (app.db.insertFood(newFood)) {
                app.foodsResult.foodInsertedDescription = description;
                app.popTo("foodSearch");
            } else {
                app.toast("Failed to insert food");
            }
        } catch (...) {
            app.toast("Invalid JSON or missing fields");
        }
    }
};

} // namespace

Screen* makeAddFoodByJsonScreen(App& app) { return new AddFoodByJsonScreen(app); }
