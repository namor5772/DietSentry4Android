// anthropic.h — Anthropic Messages API client (callAnthropicApi in Kotlin),
// executed on a worker thread; the UI polls the AiJob each frame.
#pragma once
#include "app.h"
#include <mutex>
#include <memory>

struct AiImagePayload {
    std::vector<unsigned char> bytes;   // JPEG
    std::string mediaType;              // "image/jpeg"
};

struct AiChatMessage {
    std::string role;                   // "user" | "assistant"
    std::string text;
    std::vector<AiImagePayload> images;
};

struct AiUsage {
    int inputTokens = 0;
    int outputTokens = 0;
    int cacheCreationTokens = 0;
    int cacheReadTokens = 0;
    int webSearchRequests = 0;
};

struct AiResponse {
    std::string text;
    AiUsage usage;
};

struct AiRequest {
    std::string apiKey;
    std::string model;
    std::vector<AiChatMessage> messages;
    bool enableWebSearch = false;
    bool nipMode = false;
    std::string primaryPrompt;
    std::string generalSystemPrompt;
    bool extendedThinking = false;
    bool enableFoodLookupTool = false;
    Db* db = nullptr;                   // for lookup_food (SQLite is serialized-mode safe)
    bool recipeMode = false;
};

struct AiJob {
    std::mutex mu;
    bool done = false;
    bool ok = false;
    AiResponse response;
    std::string error;
    std::vector<std::string> toolEvents;

    static std::shared_ptr<AiJob> start(AiRequest req);
};

double computeAiCostUsd(const AiUsage& usage, const std::string& model);
std::string injectCostIntoJsonNotes(const std::string& reply, double costUsd);
bool modelSupportsThinking(const std::string& model);
std::string buildGeneralSystemPrompt(const App& app, bool enableWebSearch);
std::string formatDailyTotalsForAi(const DailyTotals& totals, const WeightEntry* weightEntry,
                                   const std::string& userProfile);
