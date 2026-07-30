// dialogs.h — dialog helpers shared by several screens.
#pragma once
#include "app.h"
#include "ui.h"

// Amount + date + time dialog (SelectAmountDialog / EditEatenItemDialog).
struct AmountDateTimeDialog {
    bool open = false;
    std::string amount;
    long long dateTime = 0;
    bool showDatePicker = false;
    bool showTimePicker = false;

    void openFor(const std::string& initialAmount, long long initialDateTime) {
        open = true;
        amount = initialAmount;
        dateTime = initialDateTime;
        showDatePicker = showTimePicker = false;
    }

    enum Result { None, Confirmed, Dismissed };

    // Returns Confirmed with outAmount/outDateTime filled, Dismissed on cancel.
    Result draw(App& app, const char* id, const std::string& title, const std::string& unit,
                double* outAmount, long long* outDateTime) {
        if (!open) return None;
        Result result = None;
        bool wasOpen = open;
        if (ui::beginDialog(id, &open)) {
            ui::dialogTitle(app, title.c_str());
            ImGui::PushFont(app.fontRegular, ui::fsBody());
            float unitW = ImGui::CalcTextSize(unit.c_str()).x;
            ImGui::SetNextItemWidth(ImGui::GetContentRegionAvail().x - unitW - ui::dp(14));
            char buf[64];
            snprintf(buf, sizeof(buf), "%s", amount.c_str());
            if (ImGui::InputTextWithHint("##amount", "Amount", buf, sizeof(buf))) {
                std::string filtered;
                for (const char* p = buf; *p; ++p)
                        if (isdigit((unsigned char)*p) || *p == '.') filtered += *p;
                amount = filtered;
            }
            ImGui::SameLine();
            ImGui::TextUnformatted(unit.c_str());
            ImGui::PopFont();
            ImGui::Dummy(ImVec2(0, ui::dp(10)));

            float bw = (ImGui::GetContentRegionAvail().x - ui::dp(8)) / 2;
            if (ui::primaryButton(formatDDMMYYYY(dateTime).c_str(), ImVec2(bw, 0)))
                showDatePicker = true;
            ImGui::SameLine();
            if (ui::primaryButton(formatHHMM(dateTime).c_str(), ImVec2(bw, 0)))
                showTimePicker = true;

            // Nested pickers (opened within this modal's scope)
            ui::datePickerModal(app, "##dlgdate", &showDatePicker, &dateTime);
            int hh = 0, mm = 0;
            {
                long long mid = localMidnight(dateTime);
                long long tod = dateTime - mid;
                hh = (int)(tod / 3600000LL);
                mm = (int)((tod % 3600000LL) / 60000LL);
            }
            if (ui::timePickerModal(app, "##dlgtime", &showTimePicker, &hh, &mm)) {
                dateTime = localMidnight(dateTime) + (long long)hh * 3600000LL + (long long)mm * 60000LL;
            }

            ImGui::Dummy(ImVec2(0, ui::dp(10)));
            bool valid = !trim(amount).empty();
            float cw = ui::dp(110);
            ImGui::SetCursorPosX((ImGui::GetContentRegionAvail().x - cw) / 2 + ImGui::GetCursorPosX());
            if (ui::primaryButton("Confirm", ImVec2(cw, 0), valid)) {
                auto amt = parseDouble(amount);
                *outAmount = amt.value_or(0.0);
                *outDateTime = dateTime;
                result = Confirmed;
                open = false;
            }
            ui::endDialog();
        }
        if (wasOpen && !open && result == None) result = Dismissed;
        return result;
    }
};

// Simple amount-only dialog (RecipeAmountDialog).
struct AmountDialog {
    bool open = false;
    std::string amount;

    void openFor(const std::string& initialAmount) {
        open = true;
        amount = initialAmount;
    }

    enum Result { None, Confirmed, Dismissed };

    Result draw(App& app, const char* id, const std::string& title, const std::string& unit,
                double* outAmount) {
        if (!open) return None;
        Result result = None;
        bool wasOpen = open;
        if (ui::beginDialog(id, &open)) {
            ui::dialogTitle(app, title.c_str());
            ImGui::PushFont(app.fontRegular, ui::fsBody());
            float unitW = ImGui::CalcTextSize(unit.c_str()).x;
            ImGui::SetNextItemWidth(ImGui::GetContentRegionAvail().x - unitW - ui::dp(14));
            char buf[64];
            snprintf(buf, sizeof(buf), "%s", amount.c_str());
            if (ImGui::InputTextWithHint("##amount", "Amount", buf, sizeof(buf))) {
                std::string filtered;
                for (const char* p = buf; *p; ++p)
                        if (isdigit((unsigned char)*p) || *p == '.') filtered += *p;
                amount = filtered;
            }
            ImGui::SameLine();
            ImGui::TextUnformatted(unit.c_str());
            ImGui::PopFont();
            ImGui::Dummy(ImVec2(0, ui::dp(10)));
            bool valid = !trim(amount).empty();
            float cw = ui::dp(110);
            ImGui::SetCursorPosX((ImGui::GetContentRegionAvail().x - cw) / 2 + ImGui::GetCursorPosX());
            if (ui::primaryButton("Confirm", ImVec2(cw, 0), valid)) {
                *outAmount = parseDouble(amount).value_or(0.0);
                result = Confirmed;
                open = false;
            }
            ui::endDialog();
        }
        if (wasOpen && !open && result == None) result = Dismissed;
        return result;
    }
};
