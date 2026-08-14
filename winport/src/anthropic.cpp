// anthropic.cpp — WinHTTP port of the Kotlin callAnthropicApi + helpers.
// Mirrors: request JSON shape, prompt-cache breakpoints, client tool loop
// (lookup_food), web-search accounting, usage/cost math.
#include "anthropic.h"
#include "json.hpp"
#include <winhttp.h>
#include <thread>
#include <algorithm>

using json = nlohmann::ordered_json;

struct AiPricing { double inputPerMillion; double outputPerMillion; };

static const std::pair<const char*, AiPricing> ANTHROPIC_PRICING[] = {
    {"claude-opus-5", {5.0, 25.0}},
    {"claude-sonnet-5", {3.0, 15.0}},
    {"claude-haiku-4-5-20251001", {1.0, 5.0}},
    // Previous generation — kept so previously-saved selections still get cost estimates.
    {"claude-opus-4-7", {5.0, 25.0}},
    {"claude-sonnet-4-6", {3.0, 15.0}},
};

bool modelSupportsThinking(const std::string& model) {
    return model == "claude-opus-5" || model == "claude-sonnet-5" ||
           model == "claude-opus-4-7" || model == "claude-sonnet-4-6";
}

// Models that run adaptive thinking when the `thinking` field is OMITTED — a change
// from the 4.x generation, where omitting meant off. For these the request builder
// sends an explicit {type: "disabled"} while the Extended-thinking toggle is off.
static bool modelThinksByDefault(const std::string& model) {
    return model == "claude-opus-5" || model == "claude-sonnet-5";
}

double computeAiCostUsd(const AiUsage& usage, const std::string& model) {
    const AiPricing* pricing = nullptr;
    for (auto& p : ANTHROPIC_PRICING)
        if (model == p.first) { pricing = &p.second; break; }
    if (!pricing) return 0.0;
    const double M = 1000000.0;
    double inputCost = usage.inputTokens * pricing->inputPerMillion / M;
    double outputCost = usage.outputTokens * pricing->outputPerMillion / M;
    double cacheCreationCost = usage.cacheCreationTokens * pricing->inputPerMillion * CACHE_WRITE_MULTIPLIER / M;
    double cacheReadCost = usage.cacheReadTokens * pricing->inputPerMillion * CACHE_READ_MULTIPLIER / M;
    double webSearchCost = usage.webSearchRequests * WEB_SEARCH_COST_PER_REQUEST;
    return inputCost + outputCost + cacheCreationCost + cacheReadCost + webSearchCost;
}

std::string injectCostIntoJsonNotes(const std::string& reply, double costUsd) {
    size_t openIdx = reply.find('{');
    size_t closeIdx = reply.rfind('}');
    if (openIdx == std::string::npos || closeIdx == std::string::npos || closeIdx <= openIdx)
        return reply;
    std::string jsonText = reply.substr(openIdx, closeIdx - openIdx + 1);
    json obj = json::parse(jsonText, nullptr, false);
    if (obj.is_discarded() || !obj.is_object()) return reply;
    std::string existingNotes;
    if (obj.contains("notes") && obj["notes"].is_string())
        existingNotes = trim(obj["notes"].get<std::string>());
    std::string costAnnotation = "API cost: " + formatUsdCost(costUsd);
    obj["notes"] = existingNotes.empty() ? costAnnotation : existingNotes + " " + costAnnotation;
    return reply.substr(0, openIdx) + obj.dump(2) + reply.substr(closeIdx + 1);
}

std::string buildGeneralSystemPrompt(const App& app, bool enableWebSearch) {
    if (enableWebSearch && !app.genericWebSearchClause.empty())
        return app.genericPrompt + "\n\n" + app.genericWebSearchClause;
    return app.genericPrompt;
}

std::string formatDailyTotalsForAi(const DailyTotals& totals, const WeightEntry* weightEntry,
                                   const std::string& userProfile) {
    Nutrients n = totals.n;
    std::string sb;
    sb += "My daily food totals:\n";
    sb += "Date: " + totals.date + "\n";
    sb += "Total amount eaten: " + formatNumber(totals.amountEaten, 1) + " " + totals.unitLabel + "\n";
    sb += "Energy: " + formatNumber(n.energy(), 0) + " kJ\n";
    sb += "Protein: " + formatNumber(n.protein(), 1) + " g\n";
    sb += "Fat, total: " + formatNumber(n.fatTotal(), 1) + " g\n";
    sb += "Saturated fat: " + formatNumber(n.saturatedFat(), 1) + " g\n";
    sb += "Trans fat: " + formatNumber(n.transFat(), 1) + " mg\n";
    sb += "Polyunsaturated fat: " + formatNumber(n.polyunsaturatedFat(), 1) + " g\n";
    sb += "Monounsaturated fat: " + formatNumber(n.monounsaturatedFat(), 1) + " g\n";
    sb += "Carbohydrate: " + formatNumber(n.carbohydrate(), 1) + " g\n";
    sb += "Sugars: " + formatNumber(n.sugars(), 1) + " g\n";
    sb += "Dietary fibre: " + formatNumber(n.dietaryFibre(), 1) + " g\n";
    sb += "Sodium (Na): " + formatNumber(n.sodiumNa(), 0) + " mg\n";
    sb += "Calcium (Ca): " + formatNumber(n.calciumCa(), 0) + " mg\n";
    sb += "Potassium (K): " + formatNumber(n.potassiumK(), 0) + " mg\n";
    sb += "Thiamin (B1): " + formatNumber(n.thiaminB1(), 2) + " mg\n";
    sb += "Riboflavin (B2): " + formatNumber(n.riboflavinB2(), 2) + " mg\n";
    sb += "Niacin (B3): " + formatNumber(n.niacinB3(), 2) + " mg\n";
    sb += "Folate: " + formatNumber(n.folate(), 0) + " \xC2\xB5g\n";
    sb += "Iron (Fe): " + formatNumber(n.ironFe(), 1) + " mg\n";
    sb += "Magnesium (Mg): " + formatNumber(n.magnesiumMg(), 0) + " mg\n";
    sb += "Vitamin C: " + formatNumber(n.vitaminC(), 1) + " mg\n";
    sb += "Caffeine: " + formatNumber(n.caffeine(), 0) + " mg\n";
    sb += "Cholesterol: " + formatNumber(n.cholesterol(), 0) + " mg\n";
    sb += "Alcohol: " + formatNumber(n.alcohol(), 1) + " g\n";
    if (weightEntry) {
        sb += "My weight that day: " + formatWeight(weightEntry->weight) + " kg\n";
        if (!trim(weightEntry->comments).empty())
            sb += "Weight notes: " + trim(weightEntry->comments) + "\n";
    }
    if (!trim(userProfile).empty())
        sb += "\nMy personal profile:\n" + trim(userProfile) + "\n";
    sb += "\nPlease explain my nutritional intake for this day.";
    return sb;
}

// ---------------------------------------------------------------------------
// Request JSON builders (chatMessagesToJsonArray / buildFoodLookupToolDefinition
// / buildAnthropicRequestJson)
// ---------------------------------------------------------------------------
static json chatMessagesToJsonArray(const std::vector<AiChatMessage>& messages) {
    json arr = json::array();
    for (const auto& m : messages) {
        json msgObj;
        msgObj["role"] = m.role;
        if (!m.images.empty()) {
            json contentArr = json::array();
            for (const auto& img : m.images) {
                json source;
                source["type"] = "base64";
                source["media_type"] = img.mediaType;
                source["data"] = base64Encode(img.bytes.data(), img.bytes.size());
                json block;
                block["type"] = "image";
                block["source"] = source;
                contentArr.push_back(block);
            }
            if (!m.text.empty()) {
                json block;
                block["type"] = "text";
                block["text"] = m.text;
                contentArr.push_back(block);
            }
            msgObj["content"] = contentArr;
        } else {
            msgObj["content"] = m.text;
        }
        arr.push_back(msgObj);
    }
    return arr;
}

static json buildFoodLookupToolDefinition() {
    json querySchema;
    querySchema["type"] = "string";
    querySchema["description"] =
        "The food name or category to search (e.g. 'cheddar cheese', 'olive oil', 'banana'). "
        "Case-insensitive substring match against FoodDescription. "
        "Join two terms with '|' to require both, e.g. 'cheese|cheddar'.";
    json properties;
    properties["query"] = querySchema;
    json schema;
    schema["type"] = "object";
    schema["properties"] = properties;
    schema["required"] = json::array({"query"});
    json tool;
    tool["name"] = LOOKUP_FOOD_TOOL_NAME;
    tool["description"] =
        "Search the Diet Sentry Foods table - an Australian Food Composition Database derived from AFCD/NUTTAB. "
        "Returns up to " + std::to_string(LOOKUP_FOOD_MAX_RESULTS) +
        " matching rows as CSV, each with FoodId, FoodDescription, and full per-100 g (or per-100 mL for liquids) nutrient values across all 24 columns. "
        "Use this when you need exact micronutrient values (calcium, iron, folate, magnesium, vitamin C, etc.) for a specific food and you don't already know them from training or web search. "
        "FoodDescription suffix conventions: ' mL' or ' mL#' = liquid (per 100 mL); '#' alone = AI-generated/user-added record (prefer cleaner records when possible); '{recipe=Xg}' = recipe food.";
    tool["input_schema"] = schema;
    return tool;
}

static std::string doubleToJsonNumber(double v) {
    // Match Kotlin's Double interpolation closely enough for CSV output.
    char buf[64];
    if (v == (long long)v && std::abs(v) < 1e15) {
        snprintf(buf, sizeof(buf), "%.1f", v);   // Kotlin prints 12.0 for whole doubles
    } else {
        snprintf(buf, sizeof(buf), "%g", v);
    }
    return buf;
}

static std::string formatFoodAsCsvRow(const Food& f) {
    std::string desc = "\"";
    for (char c : f.foodDescription) {
        if (c == '"') desc += "\"\"";
        else desc += c;
    }
    desc += "\"";
    std::string row = std::to_string(f.foodId) + "," + desc;
    for (int i = 0; i < NUTRIENT_COUNT; i++)
        row += "," + doubleToJsonNumber(f.n[i]);
    return row;
}

static std::string executeFoodLookupTool(Db* db, const json& input, bool recipeMode) {
    std::string query;
    if (input.contains("query") && input["query"].is_string())
        query = trim(input["query"].get<std::string>());
    if (query.empty()) return "Tool error: 'query' parameter is required.";
    std::vector<Food> matches;
    if (db) matches = db->searchFoods(query);
    else return "Tool error: database unavailable.";
    // Recipe mode: solid + non-user-added only
    std::vector<Food> filtered;
    for (const auto& f : matches) {
        if (recipeMode) {
            const std::string& d = f.foodDescription;
            if (endsWith(d, "#")) continue;
            if (endsWith(d, "mL")) continue;
        }
        filtered.push_back(f);
    }
    if (filtered.empty()) {
        std::string suffix = recipeMode ? " (recipe mode excludes liquids and AI/user-added records)" : "";
        return "No matches found for query: '" + query + "'" + suffix + ".";
    }
    // Rank: descriptions starting with the first query term first (stable).
    std::string firstTerm = query;
    for (auto& part : splitString(query, '|')) {
        std::string t = trim(part);
        if (!t.empty()) { firstTerm = t; break; }
    }
    std::stable_sort(filtered.begin(), filtered.end(), [&](const Food& a, const Food& b) {
        bool sa = startsWithIgnoreCase(a.foodDescription, firstTerm);
        bool sb = startsWithIgnoreCase(b.foodDescription, firstTerm);
        return sa > sb;
    });
    size_t limit = std::min((size_t)LOOKUP_FOOD_MAX_RESULTS, filtered.size());
    std::string sb;
    sb += "Found " + std::to_string(filtered.size()) + " match(es) for '" + query +
          "'; showing top " + std::to_string(limit) + ".\n";
    sb += "FoodId,FoodDescription,Energy,Protein,FatTotal,SaturatedFat,TransFat,PolyunsaturatedFat,"
          "MonounsaturatedFat,Carbohydrate,Sugars,DietaryFibre,SodiumNa,CalciumCa,PotassiumK,ThiaminB1,"
          "RiboflavinB2,NiacinB3,Folate,IronFe,MagnesiumMg,VitaminC,Caffeine,Cholesterol,Alcohol\n";
    for (size_t i = 0; i < limit; i++) {
        sb += formatFoodAsCsvRow(filtered[i]);
        sb += "\n";
    }
    return sb;
}

static std::string buildAnthropicRequestJson(const AiRequest& req, const json& messagesJson) {
    json root;
    root["model"] = req.model;
    root["max_tokens"] = ANTHROPIC_MAX_TOKENS;
    if (req.extendedThinking && modelSupportsThinking(req.model)) {
        json thinking;
        thinking["type"] = "adaptive";
        thinking["display"] = "summarized";
        root["thinking"] = thinking;
    } else if (modelThinksByDefault(req.model)) {
        // Omitting `thinking` means adaptive-ON for these models, so the toggle's
        // off position needs an explicit disable.
        root["thinking"] = {{"type", "disabled"}};
    }
    root["system"] = req.nipMode ? req.primaryPrompt : req.generalSystemPrompt;
    json tools = json::array();
    if (req.enableWebSearch) {
        json ws;
        ws["type"] = WEB_SEARCH_TOOL_TYPE;
        ws["name"] = "web_search";
        ws["max_uses"] = WEB_SEARCH_MAX_USES;
        tools.push_back(ws);
    }
    if (req.enableFoodLookupTool) tools.push_back(buildFoodLookupToolDefinition());
    if (!tools.empty()) {
        // cache breakpoint on the last tool: static prefix (model+system+tools)
        // becomes a ~10x-cheaper cache read on subsequent iterations/turns.
        tools.back()["cache_control"] = {{"type", "ephemeral"}};
        root["tools"] = tools;
    }
    root["messages"] = messagesJson;
    return root.dump();
}

// ---------------------------------------------------------------------------
// WinHTTP transport
// ---------------------------------------------------------------------------
struct HttpResult {
    bool transportOk = false;
    int status = 0;
    std::string body;
    std::string transportError;
};

static HttpResult httpPostAnthropicMessages(const std::string& apiKey, const std::string& body) {
    HttpResult out;
    HINTERNET hSession = WinHttpOpen(L"DietSentry4Windows/1.0",
                                     WINHTTP_ACCESS_TYPE_AUTOMATIC_PROXY,
                                     WINHTTP_NO_PROXY_NAME, WINHTTP_NO_PROXY_BYPASS, 0);
    if (!hSession) { out.transportError = "WinHttpOpen failed"; return out; }
    // connect 30s, receive 240s (matches the Android timeouts)
    WinHttpSetTimeouts(hSession, 30000, 30000, 60000, 240000);

    HINTERNET hConnect = WinHttpConnect(hSession, L"api.anthropic.com", INTERNET_DEFAULT_HTTPS_PORT, 0);
    if (!hConnect) {
        out.transportError = "Could not connect to api.anthropic.com";
        WinHttpCloseHandle(hSession);
        return out;
    }
    HINTERNET hRequest = WinHttpOpenRequest(hConnect, L"POST", L"/v1/messages", nullptr,
                                            WINHTTP_NO_REFERER, WINHTTP_DEFAULT_ACCEPT_TYPES,
                                            WINHTTP_FLAG_SECURE);
    if (!hRequest) {
        out.transportError = "WinHttpOpenRequest failed";
        WinHttpCloseHandle(hConnect);
        WinHttpCloseHandle(hSession);
        return out;
    }
    std::wstring headers = L"x-api-key: " + utf8ToWide(apiKey) +
                           L"\r\nanthropic-version: " + utf8ToWide(ANTHROPIC_VERSION) +
                           L"\r\ncontent-type: application/json";
    BOOL sent = WinHttpSendRequest(hRequest, headers.c_str(), (DWORD)headers.size(),
                                   (LPVOID)body.data(), (DWORD)body.size(), (DWORD)body.size(), 0);
    if (sent) sent = WinHttpReceiveResponse(hRequest, nullptr);
    if (!sent) {
        DWORD err = GetLastError();
        char msg[128];
        if (err == ERROR_WINHTTP_TIMEOUT)
            snprintf(msg, sizeof(msg), "Request timed out");
        else if (err == ERROR_WINHTTP_CANNOT_CONNECT || err == ERROR_WINHTTP_NAME_NOT_RESOLVED)
            snprintf(msg, sizeof(msg), "No network connection to api.anthropic.com");
        else
            snprintf(msg, sizeof(msg), "Network error (WinHTTP %lu)", err);
        out.transportError = msg;
        WinHttpCloseHandle(hRequest);
        WinHttpCloseHandle(hConnect);
        WinHttpCloseHandle(hSession);
        return out;
    }
    DWORD status = 0, statusSize = sizeof(status);
    WinHttpQueryHeaders(hRequest, WINHTTP_QUERY_STATUS_CODE | WINHTTP_QUERY_FLAG_NUMBER,
                        WINHTTP_HEADER_NAME_BY_INDEX, &status, &statusSize, WINHTTP_NO_HEADER_INDEX);
    out.status = (int)status;
    for (;;) {
        DWORD avail = 0;
        if (!WinHttpQueryDataAvailable(hRequest, &avail) || avail == 0) break;
        size_t offset = out.body.size();
        out.body.resize(offset + avail);
        DWORD read = 0;
        if (!WinHttpReadData(hRequest, &out.body[offset], avail, &read)) break;
        out.body.resize(offset + read);
        if (read == 0) break;
    }
    out.transportOk = true;
    WinHttpCloseHandle(hRequest);
    WinHttpCloseHandle(hConnect);
    WinHttpCloseHandle(hSession);
    return out;
}

// ---------------------------------------------------------------------------
// The tool-use loop (callAnthropicApi)
// ---------------------------------------------------------------------------
static void runJob(std::shared_ptr<AiJob> job, AiRequest req) {
    auto fail = [&](const std::string& message) {
        std::lock_guard<std::mutex> lock(job->mu);
        job->ok = false;
        job->error = message;
        job->done = true;
    };
    auto pushEvent = [&](const std::string& ev) {
        std::lock_guard<std::mutex> lock(job->mu);
        job->toolEvents.push_back(ev);
    };

    try {
        json workingMessages = chatMessagesToJsonArray(req.messages);
        int totalInput = 0, totalOutput = 0, totalCacheCreate = 0, totalCacheRead = 0, totalWebSearch = 0;
        std::string lastTextOutput;

        for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
            std::string body = buildAnthropicRequestJson(req, workingMessages);
            HttpResult http = httpPostAnthropicMessages(req.apiKey, body);
            if (!http.transportOk) { fail(http.transportError); return; }
            if (http.status < 200 || http.status > 299) {
                std::string reason = "HTTP " + std::to_string(http.status);
                json errJson = json::parse(http.body, nullptr, false);
                if (!errJson.is_discarded() && errJson.contains("error") &&
                    errJson["error"].is_object() && errJson["error"].contains("message") &&
                    errJson["error"]["message"].is_string()) {
                    std::string parsed = errJson["error"]["message"].get<std::string>();
                    if (!trim(parsed).empty()) reason = parsed;
                }
                fail(reason);
                return;
            }
            json j = json::parse(http.body, nullptr, false);
            if (j.is_discarded()) { fail("Malformed JSON response from API"); return; }

            if (j.contains("usage") && j["usage"].is_object()) {
                const json& u = j["usage"];
                totalInput += u.value("input_tokens", 0);
                totalOutput += u.value("output_tokens", 0);
                totalCacheCreate += u.value("cache_creation_input_tokens", 0);
                totalCacheRead += u.value("cache_read_input_tokens", 0);
            }
            int wsFromUsage = 0;
            if (j.contains("usage") && j["usage"].contains("server_tool_use") &&
                j["usage"]["server_tool_use"].is_object())
                wsFromUsage = j["usage"]["server_tool_use"].value("web_search_requests", 0);

            if (!j.contains("content") || !j["content"].is_array()) {
                fail("Response missing content");
                return;
            }
            const json& content = j["content"];
            std::string sb;
            int wsFromContent = 0;
            std::vector<json> toolUseBlocks;
            for (const auto& block : content) {
                std::string type = block.value("type", "");
                if (type == "text") {
                    sb += block.value("text", "");
                } else if (type == "server_tool_use") {
                    if (block.value("name", "") == "web_search") {
                        wsFromContent++;
                        std::string wsQuery;
                        if (block.contains("input") && block["input"].is_object())
                            wsQuery = block["input"].value("query", "");
                        if (!trim(wsQuery).empty())
                            pushEvent("Searched the web: '" + wsQuery + "'");
                    }
                } else if (type == "tool_use") {
                    toolUseBlocks.push_back(block);
                }
            }
            totalWebSearch += std::max(wsFromUsage, wsFromContent);
            lastTextOutput = sb;

            std::string stopReason = j.value("stop_reason", "");
            if (stopReason == "tool_use" && !toolUseBlocks.empty() && req.db != nullptr) {
                json assistantMsg;
                assistantMsg["role"] = "assistant";
                assistantMsg["content"] = content;
                workingMessages.push_back(assistantMsg);

                json resultsContent = json::array();
                for (const auto& toolUse : toolUseBlocks) {
                    std::string toolName = toolUse.value("name", "");
                    std::string toolUseId = toolUse.value("id", "");
                    json toolInput = toolUse.contains("input") && toolUse["input"].is_object()
                                     ? toolUse["input"] : json::object();
                    if (toolName == LOOKUP_FOOD_TOOL_NAME) {
                        std::string q;
                        if (toolInput.contains("query") && toolInput["query"].is_string())
                            q = trim(toolInput["query"].get<std::string>());
                        if (!q.empty())
                            pushEvent("Looking up '" + q + "' in the Foods table\xE2\x80\xA6");
                    }
                    std::string resultText = toolName == LOOKUP_FOOD_TOOL_NAME
                        ? executeFoodLookupTool(req.db, toolInput, req.recipeMode)
                        : "Unknown client tool: " + toolName;
                    json result;
                    result["type"] = "tool_result";
                    result["tool_use_id"] = toolUseId;
                    result["content"] = resultText;
                    resultsContent.push_back(result);
                }
                json userMsg;
                userMsg["role"] = "user";
                userMsg["content"] = resultsContent;
                workingMessages.push_back(userMsg);
                continue;
            }

            {
                std::lock_guard<std::mutex> lock(job->mu);
                job->ok = true;
                job->response.text = lastTextOutput;
                job->response.usage = {totalInput, totalOutput, totalCacheCreate, totalCacheRead, totalWebSearch};
                job->done = true;
            }
            return;
        }
        fail("Tool-use loop exceeded " + std::to_string(MAX_TOOL_ITERATIONS) + " iterations.");
    } catch (const std::exception& e) {
        fail(e.what() ? e.what() : "Unknown error");
    } catch (...) {
        fail("Unknown error");
    }
}

std::shared_ptr<AiJob> AiJob::start(AiRequest req) {
    auto job = std::make_shared<AiJob>();
    std::thread worker(runJob, job, std::move(req));
    worker.detach();
    return job;
}
