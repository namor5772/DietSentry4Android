// screens_ai.cpp — AddFoodByAiScreen: chat with Claude, AI settings dialog,
// multi-image attach, live tool-call indicator, session cost row, and the
// auto-pump into AddFoodByJsonScreen. Mirrors AddFoodByAiScreen in Kotlin.
#include "app.h"
#include "ui.h"
#include "helptexts.h"
#include "anthropic.h"
#include "imageutil.h"

namespace {

struct UiChatImage {
    LoadedAiImage img;
};

struct UiChatMessage {
    std::string role;
    std::string text;
    std::vector<UiChatImage> images;
};

struct AddFoodByAiScreen : Screen {
    App* appPtr = nullptr;

    std::string apiKey, model;
    bool webSearchEnabled = true;
    bool nipModeEnabled = true;
    bool extendedThinkingEnabled = false;

    bool showSettings = false;
    bool showHelp = false;
    bool keyVisible = false;

    // settings drafts
    std::string draftKey, draftModel;
    bool draftWebSearch = true, draftNip = true, draftThinking = false;

    std::vector<UiChatMessage> messages;
    std::vector<UiChatImage> pendingImages;
    std::string inputText;
    bool loading = false;
    std::optional<std::string> errorText;
    double sessionCostUsd = 0.0;
    int turnCount = 0;
    std::vector<std::string> toolStatusHistory;
    std::shared_ptr<AiJob> job;
    bool jobRecipeIntent = false;
    bool jobEffectiveNipMode = false;
    size_t lastMessageCount = 0;

    explicit AddFoodByAiScreen(App& app) : appPtr(&app) {
        apiKey = app.prefs.getString(PREF_KEY_ANTHROPIC_API_KEY, "");
        model = app.prefs.getString(PREF_KEY_ANTHROPIC_MODEL, DEFAULT_ANTHROPIC_MODEL);
        webSearchEnabled = app.prefs.getBool(PREF_KEY_AI_WEB_SEARCH, true);
        nipModeEnabled = app.prefs.getBool(PREF_KEY_AI_USE_NIP_PROMPT, true);
        extendedThinkingEnabled = app.prefs.getBool(PREF_KEY_AI_EXTENDED_THINKING, false);
        if (trim(apiKey).empty()) openSettings();
    }

    ~AddFoodByAiScreen() override {
        if (appPtr) {
            for (auto& m : messages)
                for (auto& im : m.images)
                    if (im.img.texture) appPtr->releaseTexture(im.img.texture);
            for (auto& im : pendingImages)
                if (im.img.texture) appPtr->releaseTexture(im.img.texture);
        }
    }

    const char* route() const override { return "addFoodByAi"; }

    void openSettings() {
        draftKey = apiKey;
        draftModel = model;
        draftWebSearch = webSearchEnabled;
        draftNip = nipModeEnabled;
        draftThinking = extendedThinkingEnabled;
        showSettings = true;
    }

    void draw(App& app) override {
        auto bar = ui::topBar(app, "Add Food using AI", false, 0, false, true);
        if (bar.settingsClicked) openSettings();
        if (bar.helpClicked) showHelp = true;
        if (bar.navClicked) app.pop();
        if (ImGui::IsKeyPressed(ImGuiKey_Escape) && !showHelp && !showSettings) app.requestBack();

        pollJob(app);

        // Session cost row
        if (sessionCostUsd > 0.0) {
            ImVec2 pos = ImGui::GetCursorScreenPos();
            float w = ImGui::GetContentRegionAvail().x;
            ImGui::PushFont(app.fontRegular, ui::fsBodySmall());
            std::string label = "Session cost: " + formatUsdCost(sessionCostUsd) + " (" +
                                std::to_string(turnCount) + (turnCount == 1 ? " turn" : " turns") + ")";
            float bh = ImGui::GetTextLineHeight() + ui::dp(8);
            ImGui::GetWindowDrawList()->AddRectFilled(pos, ImVec2(pos.x + w, pos.y + bh),
                                                      IM_COL32(0xEF, 0xE9, 0xF3, 0xFF));
            ImGui::SetCursorScreenPos(ImVec2(pos.x + ui::dp(12), pos.y + ui::dp(4)));
            ImGui::TextUnformatted(label.c_str());
            ImGui::PopFont();
            ImGui::SetCursorScreenPos(ImVec2(pos.x, pos.y + bh));
            ImGui::Dummy(ImVec2(0, 0));
        }

        // Bottom stack heights
        float errorH = 0;
        if (errorText) {
            ImGui::PushFont(app.fontRegular, ui::fsBodySmall());
            errorH = ImGui::CalcTextSize(errorText->c_str(), nullptr, false,
                                         ImGui::GetContentRegionAvail().x - ui::dp(70)).y + ui::dp(16);
            ImGui::PopFont();
        }
        float thumbsH = pendingImages.empty() ? 0 : ui::dp(84);
        float inputH = ui::dp(56);

        // Chat list
        float listH = ImGui::GetContentRegionAvail().y - errorH - thumbsH - inputH;
        ImGui::BeginChild("##chat", ImVec2(0, listH));
        ImGui::SetCursorPosX(ui::dp(12));
        ImGui::BeginGroup();
        float w = ImGui::GetContentRegionAvail().x - ui::dp(12);

        if (messages.empty() && !loading) {
            ImGui::Dummy(ImVec2(0, ui::dp(24)));
            ImGui::PushFont(app.fontBold, ui::fsTitleLarge());
            const char* t = "Chat with Claude";
            ImVec2 ts = ImGui::CalcTextSize(t);
            ImGui::SetCursorPosX((w - ts.x) / 2 + ui::dp(12));
            ImGui::TextUnformatted(t);
            ImGui::PopFont();
            ImGui::Dummy(ImVec2(0, ui::dp(8)));
            std::string sub = trim(apiKey).empty()
                ? "Click the gear icon to add your Anthropic API key, then describe a food or attach a label photo."
                : nipModeEnabled
                ? "NIP mode is ON. Describe a food (or attach a label photo) and the reply - a Diet Sentry JSON - will be auto-pumped into the Json screen for one-click Confirm."
                : "General chat mode (NIP mode off in settings). Ask anything; replies stay here.";
            ImGui::PushFont(app.fontRegular, ui::fsBody());
            ImGui::SetCursorPosX(ui::dp(36));
            ImGui::PushTextWrapPos(ImGui::GetCursorPosX() + w - ui::dp(48));
            ImVec2 ss = ImGui::CalcTextSize(sub.c_str(), nullptr, false, w - ui::dp(48));
            ImGui::SetCursorPosX((w - ss.x) / 2 + ui::dp(12) > ui::dp(36) ? (w - ss.x) / 2 + ui::dp(12) : ui::dp(36));
            ImGui::TextUnformatted(sub.c_str());
            ImGui::PopTextWrapPos();
            ImGui::PopFont();
        }

        for (size_t mi = 0; mi < messages.size(); mi++) {
            ImGui::PushID((int)mi);
            drawBubble(app, messages[mi], w);
            ImGui::PopID();
            ImGui::Dummy(ImVec2(0, ui::dp(8)));
        }

        if (loading) {
            // spinner + "Thinking…" + tool status lines
            ImVec2 c = ImGui::GetCursorScreenPos();
            float r = ui::dp(9);
            float t = (float)ImGui::GetTime() * 6.0f;
            ImGui::GetWindowDrawList()->PathArcTo(ImVec2(c.x + r + ui::dp(4), c.y + r + ui::dp(2)), r, t, t + 4.6f, 20);
            ImGui::GetWindowDrawList()->PathStroke(ui::COL_PRIMARY, 0, ui::dp(2.4f));
            ImGui::SetCursorPosX(ImGui::GetCursorPosX() + ui::dp(30));
            ImGui::PushFont(app.fontRegular, ui::fsBody());
            ImGui::TextUnformatted(u8"Thinking…");
            ImGui::PopFont();
            ImGui::PushFont(app.fontRegular, ui::fsBodySmall());
            for (const auto& status : toolStatusHistory) {
                ImGui::SetCursorPosX(ImGui::GetCursorPosX() + ui::dp(30));
                ImGui::TextColored(ImGui::ColorConvertU32ToFloat4(ui::COL_ON_SURFACE_VARIANT),
                                   "%s", status.c_str());
            }
            ImGui::PopFont();
        }
        ImGui::EndGroup();
        ImGui::Dummy(ImVec2(0, ui::dp(6)));
        // autoscroll on new content
        size_t contentCount = messages.size() + (loading ? 1 + toolStatusHistory.size() : 0);
        if (contentCount != lastMessageCount) {
            ImGui::SetScrollHereY(1.0f);
            lastMessageCount = contentCount;
        }
        ImGui::EndChild();

        // Error banner
        if (errorText) {
            ImVec2 pos = ImGui::GetCursorScreenPos();
            float bw = ImGui::GetContentRegionAvail().x;
            ImGui::GetWindowDrawList()->AddRectFilled(pos, ImVec2(pos.x + bw, pos.y + errorH),
                                                      ui::COL_ERROR_CONTAINER);
            ImGui::SetCursorScreenPos(ImVec2(pos.x + ui::dp(12), pos.y + ui::dp(8)));
            ImGui::PushFont(app.fontRegular, ui::fsBodySmall());
            ImGui::PushTextWrapPos(ImGui::GetCursorPosX() + bw - ui::dp(70));
            ImGui::PushStyleColor(ImGuiCol_Text, IM_COL32(0x41, 0x0E, 0x0B, 0xFF));
            ImGui::TextUnformatted(errorText->c_str());
            ImGui::PopStyleColor();
            ImGui::PopTextWrapPos();
            ImGui::PopFont();
            ImGui::SameLine();
            ImGui::SetCursorScreenPos(ImVec2(pos.x + bw - ui::dp(38), pos.y + (errorH - ui::dp(26)) / 2));
            if (ui::iconTextButton(u8"✕##dismisserr")) errorText.reset();
            ImGui::SetCursorScreenPos(ImVec2(pos.x, pos.y + errorH));
            ImGui::Dummy(ImVec2(0, 0));
        }

        // Pending image thumbnails
        if (!pendingImages.empty()) {
            ImGui::SetCursorPosX(ui::dp(12));
            int removeIdx = -1;
            for (size_t i = 0; i < pendingImages.size(); i++) {
                if (i) ImGui::SameLine(0, ui::dp(8));
                ImGui::PushID((int)i);
                ImVec2 pos = ImGui::GetCursorScreenPos();
                float side = ui::dp(72);
                if (pendingImages[i].img.texture) {
                    ImGui::Image((ImTextureID)(intptr_t)pendingImages[i].img.texture,
                                 ImVec2(side, side));
                } else {
                    ImGui::Dummy(ImVec2(side, side));
                    ImGui::GetWindowDrawList()->AddRectFilled(pos, ImVec2(pos.x + side, pos.y + side),
                                                              ui::COL_SURFACE_VARIANT, ui::dp(6));
                }
                ImGui::SetCursorScreenPos(ImVec2(pos.x + side - ui::dp(20), pos.y + ui::dp(2)));
                if (ui::iconTextButton(u8"✕##rm")) removeIdx = (int)i;
                ImGui::SetCursorScreenPos(ImVec2(pos.x + side, pos.y));
                ImGui::Dummy(ImVec2(0, side));
                ImGui::PopID();
                ImGui::SameLine();
            }
            ImGui::NewLine();
            if (removeIdx >= 0) {
                if (pendingImages[removeIdx].img.texture)
                    app.releaseTexture(pendingImages[removeIdx].img.texture);
                pendingImages.erase(pendingImages.begin() + removeIdx);
            }
        }

        // Input row
        {
            ImVec2 pos = ImGui::GetCursorScreenPos();
            float bw = ImGui::GetContentRegionAvail().x;
            ImGui::GetWindowDrawList()->AddRectFilled(pos, ImVec2(pos.x + bw, pos.y + inputH),
                                                      IM_COL32(0xF2, 0xEC, 0xF6, 0xFF));
            ImGui::SetCursorScreenPos(ImVec2(pos.x + ui::dp(6), pos.y + ui::dp(10)));
            if (!loading) {
                if (ui::iconTextButton("+##attach")) {
                    auto paths = pickImageFiles();
                    int failures = 0;
                    std::string firstError;
                    for (const auto& p : paths) {
                        std::string err;
                        auto img = loadImageForAi(app, p, err);
                        if (img) pendingImages.push_back({*img});
                        else { failures++; if (firstError.empty()) firstError = err; }
                    }
                    if (failures > 0)
                        errorText = "Skipped " + std::to_string(failures) + " image(s): " + firstError;
                }
            } else {
                ImGui::BeginDisabled();
                ui::iconTextButton("+##attach");
                ImGui::EndDisabled();
            }
            ImGui::SameLine();
            float sendW = ui::dp(44);
            ImGui::PushFont(app.fontRegular, ui::fsBody());
            ImGui::SetNextItemWidth(bw - ImGui::GetCursorPosX() - sendW - ui::dp(6));
            char ibuf[4096];
            snprintf(ibuf, sizeof(ibuf), "%s", inputText.c_str());
            bool enterPressed = ImGui::InputTextWithHint("##aiinput", "Message Claude", ibuf, sizeof(ibuf),
                                                         ImGuiInputTextFlags_EnterReturnsTrue);
            if (ImGui::IsItemEdited() || enterPressed) inputText = ibuf;
            ImGui::PopFont();
            ImGui::SameLine(0, ui::dp(4));
            bool canSend = !loading && (!trim(inputText).empty() || !pendingImages.empty());
            ImGui::PushFont(app.fontBold, ui::dp(18));
            ImGui::PushStyleColor(ImGuiCol_Text, canSend ? ui::COL_PRIMARY : ui::COL_OUTLINE);
            ImGui::PushStyleColor(ImGuiCol_Button, IM_COL32(0, 0, 0, 0));
            ImGui::PushStyleColor(ImGuiCol_ButtonHovered, IM_COL32(0x67, 0x50, 0xA4, 0x18));
            ImGui::PushStyleColor(ImGuiCol_ButtonActive, IM_COL32(0x67, 0x50, 0xA4, 0x30));
            bool sendClicked = ImGui::Button(u8"➤##send", ImVec2(ui::dp(38), ui::dp(34)));
            ImGui::PopStyleColor(4);
            ImGui::PopFont();
            if ((sendClicked || enterPressed) && canSend) send(app);
        }

        drawSettingsDialog(app);
        ui::helpBottomSheet(app, "##aihelp", &showHelp, aiHelpText());
    }

    void drawBubble(App& app, const UiChatMessage& msg, float w) {
        bool isUser = msg.role == "user";
        float maxBubbleW = isUser ? std::min(ui::dp(320), w * 0.8f) : w;
        float pad = ui::dp(10);

        // measure content
        float contentW = maxBubbleW - pad * 2;
        float imgSide = ui::dp(160);
        float textH = 0;
        if (!msg.text.empty() && isUser) {
            ImGui::PushFont(app.fontRegular, ui::fsBody());
            textH = ImGui::CalcTextSize(msg.text.c_str(), nullptr, false, contentW).y;
            ImGui::PopFont();
        }
        // user bubble width shrinks to text
        float bubbleW = maxBubbleW;
        if (isUser) {
            ImGui::PushFont(app.fontRegular, ui::fsBody());
            float tw = msg.text.empty() ? 0 : ImGui::CalcTextSize(msg.text.c_str(), nullptr, false, contentW).x;
            ImGui::PopFont();
            float iw = msg.images.empty() ? 0 : imgSide;
            bubbleW = std::min(maxBubbleW, std::max(tw, iw) + pad * 2);
            if (bubbleW < ui::dp(48)) bubbleW = ui::dp(48);
        }

        float x0 = isUser ? ImGui::GetCursorPosX() + w - bubbleW : ImGui::GetCursorPosX();
        ImGui::SetCursorPosX(x0);
        ImVec2 pos = ImGui::GetCursorScreenPos();

        // Split draw channels: content on top (1), bubble background below (0).
        ImDrawList* dl = ImGui::GetWindowDrawList();
        dl->ChannelsSplit(2);
        dl->ChannelsSetCurrent(1);

        ImGui::SetCursorScreenPos(ImVec2(pos.x + pad, pos.y + pad));
        ImGui::BeginGroup();
        for (const auto& im : msg.images) {
            if (im.img.texture) {
                float aspect = im.img.height > 0 ? (float)im.img.height / im.img.width : 1.0f;
                ImGui::Image((ImTextureID)(intptr_t)im.img.texture, ImVec2(imgSide, imgSide * aspect));
            }
        }
        if (!msg.text.empty()) {
            if (isUser) {
                ImGui::PushFont(app.fontRegular, ui::fsBody());
                ImGui::PushStyleColor(ImGuiCol_Text, ui::COL_ON_PRIMARY_CONTAINER);
                ImGui::PushTextWrapPos(ImGui::GetCursorPosX() + bubbleW - pad * 2);
                ImGui::TextUnformatted(msg.text.c_str());
                ImGui::PopTextWrapPos();
                ImGui::PopStyleColor();
                ImGui::PopFont();
            } else {
                ImGui::BeginGroup();
                ui::renderMarkdownWidth(app, msg.text, bubbleW - pad * 2);
                ImGui::EndGroup();
            }
        }
        ImGui::EndGroup();
        ImVec2 innerMax = ImGui::GetItemRectMax();
        float bubbleH = innerMax.y - pos.y + pad;

        dl->ChannelsSetCurrent(0);
        dl->AddRectFilled(pos, ImVec2(pos.x + bubbleW, pos.y + bubbleH),
                          isUser ? ui::COL_PRIMARY_CONTAINER : ui::COL_SURFACE_VARIANT, ui::dp(12));
        dl->ChannelsMerge();

        ImGui::SetCursorScreenPos(ImVec2(pos.x, pos.y + bubbleH));
        ImGui::Dummy(ImVec2(bubbleW, 0));

        if (!isUser && !msg.text.empty()) {
            if (ui::textButton("Copy")) {
                ImGui::SetClipboardText(msg.text.c_str());
                app.toast("Copied");
            }
        }
    }

    void send(App& app) {
        std::string txt = trim(inputText);
        if (trim(apiKey).empty()) {
            errorText = "Set your Anthropic API key first (gear icon).";
            openSettings();
            return;
        }
        UiChatMessage userMsg;
        userMsg.role = "user";
        userMsg.text = txt;
        userMsg.images = std::move(pendingImages);
        pendingImages.clear();

        bool recipeIntent = nipModeEnabled && containsIgnoreCase(txt, "recipe") &&
                            !trim(app.recipePrompt).empty();
        bool effectiveNipMode = nipModeEnabled || recipeIntent;
        std::string activePrompt;
        bool enableFoodLookupTool = false;
        if (recipeIntent) {
            activePrompt = app.recipePrompt;
            enableFoodLookupTool = true;
        } else if (nipModeEnabled) {
            activePrompt = app.nipPrompt;
            enableFoodLookupTool = true;
        }
        messages.push_back(std::move(userMsg));
        inputText.clear();
        errorText.reset();
        loading = true;
        toolStatusHistory.clear();
        jobRecipeIntent = recipeIntent;
        jobEffectiveNipMode = effectiveNipMode;

        AiRequest req;
        req.apiKey = apiKey;
        req.model = model;
        for (const auto& m : messages) {
            AiChatMessage am;
            am.role = m.role;
            am.text = m.text;
            for (const auto& im : m.images)
                am.images.push_back({im.img.jpegBytes, im.img.mediaType});
            req.messages.push_back(std::move(am));
        }
        req.enableWebSearch = webSearchEnabled;
        req.nipMode = effectiveNipMode;
        req.primaryPrompt = activePrompt;
        req.generalSystemPrompt = buildGeneralSystemPrompt(app, webSearchEnabled);
        req.extendedThinking = extendedThinkingEnabled;
        req.enableFoodLookupTool = enableFoodLookupTool;
        req.db = &app.db;
        req.recipeMode = recipeIntent;
        job = AiJob::start(std::move(req));
    }

    void pollJob(App& app) {
        if (!job) return;
        bool done = false;
        bool ok = false;
        AiResponse response;
        std::string error;
        {
            std::lock_guard<std::mutex> lock(job->mu);
            toolStatusHistory = job->toolEvents;
            if (job->done) {
                done = true;
                ok = job->ok;
                response = job->response;
                error = job->error;
            }
        }
        if (!done) return;
        job.reset();
        loading = false;
        toolStatusHistory.clear();
        if (!ok) {
            errorText = error.empty() ? "Request failed." : error;
            return;
        }
        double callCostUsd = computeAiCostUsd(response.usage, model);
        sessionCostUsd += callCostUsd;
        turnCount += 1;
        std::string annotatedReply = injectCostIntoJsonNotes(response.text, callCostUsd);
        UiChatMessage assistantMsg;
        assistantMsg.role = "assistant";
        assistantMsg.text = annotatedReply;
        messages.push_back(std::move(assistantMsg));
        if (jobEffectiveNipMode) {
            size_t openIdx = annotatedReply.find('{');
            size_t closeIdx = annotatedReply.rfind('}');
            if (openIdx != std::string::npos && closeIdx != std::string::npos && closeIdx > openIdx) {
                app.sessionPrefilledJson = annotatedReply;
                app.sessionPrefilledJsonCost = callCostUsd;
                app.push(makeAddFoodByJsonScreen(app));
            }
        }
    }

    void drawSettingsDialog(App& app) {
        if (!showSettings) return;
        if (ui::beginDialog("##aisettings", &showSettings, ui::dp(440))) {
            ui::dialogTitle(app, "AI settings");
            ImGuiViewport* vp = ImGui::GetMainViewport();
            ImGui::BeginChild("##settingsscroll", ImVec2(0, std::min(vp->Size.y * 0.62f, ui::dp(560))));
            ImGui::PushFont(app.fontRegular, ui::fsBody());

            ImGui::PushFont(app.fontBold, ui::fsLabel());
            ImGui::TextUnformatted("Anthropic API key");
            ImGui::PopFont();
            char kbuf[256];
            snprintf(kbuf, sizeof(kbuf), "%s", draftKey.c_str());
            ImGui::SetNextItemWidth(ImGui::GetContentRegionAvail().x - ui::dp(60));
            if (ImGui::InputTextWithHint("##apikey", u8"sk-ant-…", kbuf, sizeof(kbuf),
                                         keyVisible ? 0 : ImGuiInputTextFlags_Password))
                draftKey = kbuf;
            ImGui::SameLine();
            if (ui::textButton(keyVisible ? "Hide" : "Show")) keyVisible = !keyVisible;
            ImGui::PushFont(app.fontRegular, ui::fsBodySmall());
            ImGui::TextColored(ImGui::ColorConvertU32ToFloat4(ui::COL_ON_SURFACE_VARIANT),
                               "Stored locally on this device. Never leaves except to api.anthropic.com.");
            ImGui::PopFont();
            ImGui::Dummy(ImVec2(0, ui::dp(10)));

            ImGui::PushFont(app.fontBold, ui::fsLabel());
            ImGui::TextUnformatted("Model");
            ImGui::PopFont();
            struct ModelOpt { const char* id; const char* label; };
            static const ModelOpt knownModels[] = {
                {"claude-opus-4-7", u8"Opus 4.7 — highest quality"},
                {"claude-sonnet-4-6", u8"Sonnet 4.6 — balanced (default)"},
                {"claude-haiku-4-5-20251001", u8"Haiku 4.5 — fastest / cheapest"},
            };
            for (const auto& m : knownModels) {
                if (ui::radioM(m.label, draftModel == m.id)) draftModel = m.id;
            }
            ImGui::Dummy(ImVec2(0, ui::dp(10)));

            ImGui::PushFont(app.fontBold, ui::fsLabel());
            ImGui::TextUnformatted("Server-side tools");
            ImGui::PopFont();
            ui::switchM("##ws", &draftWebSearch);
            ImGui::SameLine(0, ui::dp(10));
            ImGui::BeginGroup();
            ImGui::TextUnformatted("Web search");
            ImGui::PushFont(app.fontRegular, ui::fsBodySmall());
            ImGui::PushTextWrapPos(0.0f);
            ImGui::TextColored(ImGui::ColorConvertU32ToFloat4(ui::COL_ON_SURFACE_VARIANT),
                "Lets Claude look up current product / NIP info on the web (~$0.01 per search, max %d per turn).",
                WEB_SEARCH_MAX_USES);
            ImGui::PopTextWrapPos();
            ImGui::PopFont();
            ImGui::EndGroup();
            ImGui::Dummy(ImVec2(0, ui::dp(10)));

            ImGui::PushFont(app.fontBold, ui::fsLabel());
            ImGui::TextUnformatted("System prompt");
            ImGui::PopFont();
            ui::switchM("##nip", &draftNip);
            ImGui::SameLine(0, ui::dp(10));
            ImGui::BeginGroup();
            ImGui::TextUnformatted("NIP mode");
            ImGui::PushFont(app.fontRegular, ui::fsBodySmall());
            ImGui::PushTextWrapPos(0.0f);
            ImGui::TextColored(ImGui::ColorConvertU32ToFloat4(ui::COL_ON_SURFACE_VARIANT),
                "On: use the bundled NIP-extraction prompt with the lookup_food tool against the live foods.db; "
                "replies are JSON and auto-pumped into the Json screen for one-click Confirm. "
                "Off: Claude is a general-purpose assistant.");
            ImGui::PopTextWrapPos();
            ImGui::PopFont();
            ImGui::EndGroup();
            ImGui::Dummy(ImVec2(0, ui::dp(10)));

            ImGui::PushFont(app.fontBold, ui::fsLabel());
            ImGui::TextUnformatted("Reasoning");
            ImGui::PopFont();
            bool thinkingSupported = modelSupportsThinking(draftModel);
            bool thinkingShown = draftThinking && thinkingSupported;
            if (ui::switchM("##think", &thinkingShown, thinkingSupported))
                draftThinking = thinkingShown;
            ImGui::SameLine(0, ui::dp(10));
            ImGui::BeginGroup();
            ImGui::TextUnformatted("Extended thinking (adaptive)");
            ImGui::PushFont(app.fontRegular, ui::fsBodySmall());
            ImGui::PushTextWrapPos(0.0f);
            ImGui::TextColored(ImGui::ColorConvertU32ToFloat4(ui::COL_ON_SURFACE_VARIANT), "%s",
                thinkingSupported
                ? "Lets Claude reason internally before replying - the model decides when it's worthwhile. "
                  "Thinking tokens are billed at the output rate, adding ~$0.001-$0.03 per harder turn "
                  "(e.g. recipe nutrient calculations). No effect on simple lookups."
                : "Not supported on Haiku 4.5. Switch to Sonnet 4.6 or Opus 4.7 above to enable extended "
                  "thinking. Your preference is preserved and will re-apply if you go back to a supported model.");
            ImGui::PopTextWrapPos();
            ImGui::PopFont();
            ImGui::EndGroup();

            ImGui::PopFont();
            ImGui::EndChild();

            ImGui::Dummy(ImVec2(0, ui::dp(6)));
            ImGui::SetCursorPosX(ImGui::GetContentRegionAvail().x - ui::dp(140));
            if (ui::textButton("Cancel")) showSettings = false;
            ImGui::SameLine();
            if (ui::textButton("Save")) {
                apiKey = trim(draftKey);
                model = draftModel;
                webSearchEnabled = draftWebSearch;
                nipModeEnabled = draftNip;
                extendedThinkingEnabled = draftThinking;
                app.prefs.putString(PREF_KEY_ANTHROPIC_API_KEY, apiKey);
                app.prefs.putString(PREF_KEY_ANTHROPIC_MODEL, model);
                app.prefs.putBool(PREF_KEY_AI_WEB_SEARCH, webSearchEnabled);
                app.prefs.putBool(PREF_KEY_AI_USE_NIP_PROMPT, nipModeEnabled);
                app.prefs.putBool(PREF_KEY_AI_EXTENDED_THINKING, extendedThinkingEnabled);
                showSettings = false;
            }
            ui::endDialog();
        }
    }
};

} // namespace

Screen* makeAddFoodByAiScreen(App& app) { return new AddFoodByAiScreen(app); }
