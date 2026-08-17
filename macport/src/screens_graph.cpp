// screens_graph.cpp — EatenGraphScreen: metric dropdown (25 metrics), date
// range chips, custom bar chart (Vico replacement), stats summary, persisted
// selections, weight sentinel handling.
#include "app.h"
#include "ui.h"
#include "helptexts.h"
#include <algorithm>
#include <cmath>

namespace {

struct GraphMetricDef {
    const char* displayName;
    const char* unit;
    int decimals;
    int extract;   // -1 = weight table, -2 = amountEaten, >=0 = nutrient index
};

static const GraphMetricDef GRAPH_METRICS[] = {
    {"My weight", "kg", 1, -1},
    {"Amount", "g/mL", 1, -2},
    {"Energy", "kJ", 0, 0},
    {"Protein", "g", 1, 1},
    {"Fat, total", "g", 1, 2},
    {"Saturated fat", "g", 1, 3},
    {"Trans fat", "mg", 1, 4},
    {"Polyunsaturated fat", "g", 1, 5},
    {"Monounsaturated fat", "g", 1, 6},
    {"Carbohydrate", "g", 1, 7},
    {"Sugars", "g", 1, 8},
    {"Dietary fibre", "g", 1, 9},
    {"Sodium (Na)", "mg", 0, 10},
    {"Calcium (Ca)", "mg", 0, 11},
    {"Potassium (K)", "mg", 0, 12},
    {"Thiamin (B1)", "mg", 2, 13},
    {"Riboflavin (B2)", "mg", 2, 14},
    {"Niacin (B3)", "mg", 2, 15},
    {u8"Folate", u8"µg", 0, 16},
    {"Iron (Fe)", "mg", 1, 17},
    {"Magnesium (Mg)", "mg", 0, 18},
    {"Vitamin C", "mg", 1, 19},
    {"Caffeine", "mg", 0, 20},
    {"Cholesterol", "mg", 0, 21},
    {"Alcohol", "g", 1, 22},
};
static const int GRAPH_METRIC_COUNT = sizeof(GRAPH_METRICS) / sizeof(GRAPH_METRICS[0]);
static const int METRIC_ENERGY = 2;

enum RangePreset { LAST_WEEK, LAST_MONTH, LAST_3_MONTHS, LAST_YEAR, ALL_TIME, CUSTOM };
static const char* PRESET_NAMES[] = {"1W", "1M", "3M", "1Y", "All", "Custom"};

struct EatenGraphScreen : Screen {
    std::vector<std::pair<long long, DailyTotals>> parsedTotals;   // (dateMs, totals) sorted asc
    std::vector<std::pair<long long, double>> parsedWeights;       // (dateMs, kg) sorted asc

    int selectedMetric = METRIC_ENERGY;
    int selectedRange = LAST_MONTH;
    std::optional<long long> customStartMillis, customEndMillis;
    bool showRangePicker = false;
    bool showHelp = false;

    explicit EatenGraphScreen(App& app) {
        auto allDailyTotals = aggregateDailyTotals(app.db.readEatenFoods());
        for (const auto& dt : allDailyTotals) {
            auto ms = parseDMMMYY(dt.date);
            if (ms) parsedTotals.push_back({*ms, dt});
        }
        std::sort(parsedTotals.begin(), parsedTotals.end(),
                  [](const auto& a, const auto& b) { return a.first < b.first; });
        for (const auto& we : app.db.readWeights()) {
            auto ms = parseDMMMYY(we.dateWeight);
            if (ms) parsedWeights.push_back({*ms, we.weight});
        }
        std::sort(parsedWeights.begin(), parsedWeights.end(),
                  [](const auto& a, const auto& b) { return a.first < b.first; });

        selectedMetric = std::clamp(app.prefs.getInt(PREF_KEY_GRAPH_METRIC, METRIC_ENERGY),
                                    0, GRAPH_METRIC_COUNT - 1);
        selectedRange = std::clamp(app.prefs.getInt(PREF_KEY_GRAPH_RANGE, (int)LAST_MONTH), 0, (int)CUSTOM);
        long long cs = app.prefs.getLong(PREF_KEY_GRAPH_CUSTOM_START, -1);
        long long ce = app.prefs.getLong(PREF_KEY_GRAPH_CUSTOM_END, -1);
        if (cs >= 0) customStartMillis = cs;
        if (ce >= 0) customEndMillis = ce;
    }

    const char* route() const override { return "eatenGraph"; }

    void computeRange(long long& startMillis, long long& endMillis) const {
        const long long dayMs = 24LL * 3600 * 1000;
        long long todayStart = localMidnight(nowMillis());
        long long yesterdayEnd = todayStart - 1;
        switch (selectedRange) {
        case LAST_WEEK: startMillis = todayStart - 7 * dayMs; endMillis = yesterdayEnd; break;
        case LAST_MONTH: startMillis = todayStart - 30 * dayMs; endMillis = yesterdayEnd; break;
        case LAST_3_MONTHS: startMillis = todayStart - 90 * dayMs; endMillis = yesterdayEnd; break;
        case LAST_YEAR: startMillis = todayStart - 365 * dayMs; endMillis = yesterdayEnd; break;
        case ALL_TIME: {
            long long first = LLONG_MAX;
            if (!parsedTotals.empty()) first = std::min(first, parsedTotals.front().first);
            if (!parsedWeights.empty()) first = std::min(first, parsedWeights.front().first);
            startMillis = first == LLONG_MAX ? 0 : first;
            endMillis = yesterdayEnd;
            break;
        }
        default: // CUSTOM: picker values are local midnights; end date inclusive
            startMillis = customStartMillis ? localMidnight(*customStartMillis) : 0;
            endMillis = customEndMillis ? localMidnight(*customEndMillis) + dayMs - 1
                                        : nowMillis() + dayMs;
            break;
        }
    }

    void draw(App& app) override {
        auto bar = ui::topBar(app, "Eaten Graph", false, 0, false);
        if (bar.helpClicked) showHelp = true;
        if (bar.navClicked) app.pop();
        if (ImGui::IsKeyPressed(ImGuiKey_Escape) && !showHelp && !showRangePicker)
            app.requestBack();

        ImGui::BeginChild("##graphscroll", ImVec2(0, 0), ImGuiChildFlags_NavFlattened);   // keyboard reaches the controls inside
        ImGui::SetCursorPosX(ui::dp(16));
        ImGui::BeginGroup();
        float w = ImGui::GetContentRegionAvail().x - ui::dp(16);
        const GraphMetricDef& metric = GRAPH_METRICS[selectedMetric];

        // Metric dropdown
        ImGui::PushFont(app.fontRegular, ui::fsBody());
        std::string metricLabel = std::string(metric.displayName) + " (" + metric.unit + ")";
        ImGui::SetNextItemWidth(w);
        ImGui::PushStyleColor(ImGuiCol_FrameBg, IM_COL32(0, 0, 0, 0));
        ImGui::PushStyleColor(ImGuiCol_Border, ui::COL_OUTLINE);
        ImGui::PushStyleVar(ImGuiStyleVar_FrameBorderSize, 1.0f);
        ImGui::PushStyleVar(ImGuiStyleVar_FrameRounding, ui::dp(999));
        if (ImGui::BeginCombo("##metric", metricLabel.c_str(), ImGuiComboFlags_HeightLargest)) {
            for (int i = 0; i < GRAPH_METRIC_COUNT; i++) {
                std::string label = std::string(GRAPH_METRICS[i].displayName) +
                                    " (" + GRAPH_METRICS[i].unit + ")";
                if (ImGui::Selectable(label.c_str(), i == selectedMetric)) {
                    selectedMetric = i;
                    app.prefs.putInt(PREF_KEY_GRAPH_METRIC, i);
                }
            }
            ImGui::EndCombo();
        }
        ImGui::PopStyleVar(2);
        ImGui::PopStyleColor(2);
        ImGui::PopFont();

        // Range chips
        for (int p = 0; p <= CUSTOM; p++) {
            if (p) ImGui::SameLine(0, ui::dp(4));
            if (ui::chip(PRESET_NAMES[p], selectedRange == p)) {
                selectedRange = p;
                app.prefs.putInt(PREF_KEY_GRAPH_RANGE, p);
                if (p == CUSTOM) showRangePicker = true;
            }
        }

        long long startMillis = 0, endMillis = 0;
        computeRange(startMillis, endMillis);

        // Displayed range
        std::string displayedRange;
        if (selectedRange == CUSTOM) {
            if (customStartMillis && customEndMillis)
                displayedRange = formatDMMM(localMidnight(*customStartMillis)) + u8" — " +
                                 formatDMMM(localMidnight(*customEndMillis));
        } else if (startMillis > 0 && endMillis >= startMillis) {
            displayedRange = formatDMMM(startMillis) + u8" — " + formatDMMM(endMillis);
        }
        if (!displayedRange.empty()) {
            ImGui::PushFont(app.fontRegular, ui::fsBodySmall());
            ImGui::TextColored(ImGui::ColorConvertU32ToFloat4(ui::COL_ON_SURFACE_VARIANT),
                               "%s", displayedRange.c_str());
            ImGui::PopFont();
        }

        // Series
        std::vector<std::pair<long long, double>> series;
        if (metric.extract == -1) {
            for (const auto& pw : parsedWeights)
                if (pw.first >= startMillis && pw.first <= endMillis) series.push_back(pw);
        } else {
            for (const auto& pt : parsedTotals) {
                if (pt.first < startMillis || pt.first > endMillis) continue;
                double v = metric.extract == -2 ? pt.second.amountEaten : pt.second.n[metric.extract];
                series.push_back({pt.first, v});
            }
        }

        float chartH = ui::dp(280);
        if (series.empty()) {
            ImVec2 pos = ImGui::GetCursorScreenPos();
            ImGui::Dummy(ImVec2(w, chartH));
            ImGui::PushFont(app.fontRegular, ui::fsBody());
            const char* msg = "No data in this range";
            ImVec2 ts = ImGui::CalcTextSize(msg);
            ImGui::GetWindowDrawList()->AddText(
                ImVec2(pos.x + (w - ts.x) / 2, pos.y + (chartH - ts.y) / 2),
                ui::COL_ON_SURFACE_VARIANT, msg);
            ImGui::PopFont();
        } else {
            drawChart(app, w, chartH, series, metric);
            drawSummary(app, w, series, metric);
        }
        ImGui::EndGroup();
        ImGui::Dummy(ImVec2(0, ui::dp(24)));
        ImGui::EndChild();

        if (ui::dateRangePickerModal(app, "##graphrange", &showRangePicker,
                                     &customStartMillis, &customEndMillis)) {
            app.prefs.putLong(PREF_KEY_GRAPH_CUSTOM_START, customStartMillis.value_or(-1));
            app.prefs.putLong(PREF_KEY_GRAPH_CUSTOM_END, customEndMillis.value_or(-1));
        }
        ui::helpBottomSheet(app, "##graphhelp", &showHelp, graphHelpText());
    }

    // "nice" lower bound below data min (Kotlin niceMinValue)
    static double computeNiceMin(const std::vector<double>& measured) {
        if (measured.empty()) return 0.0;
        double mn = *std::min_element(measured.begin(), measured.end());
        double mx = *std::max_element(measured.begin(), measured.end());
        double range = mx - mn;
        if (range <= 0.0) return std::max(mn, 0.0);
        double niceStep = range >= 1000 ? 100.0 : range >= 100 ? 10.0 : range >= 10 ? 1.0
                        : range >= 1 ? 0.1 : 0.01;
        double padded = mn - 0.1 * range;
        return std::max(std::floor(padded / niceStep) * niceStep, 0.0);
    }

    void drawChart(App& app, float w, float chartH,
                   const std::vector<std::pair<long long, double>>& series,
                   const GraphMetricDef& metric) {
        // y-range: sentinel-aware min, straight max
        std::vector<double> yValues;
        for (auto& s : series) yValues.push_back(s.second);
        std::vector<double> measured;
        for (double v : yValues)
            if (metric.extract != -1 || v > 0.15) measured.push_back(v);
        double yMin = computeNiceMin(measured);
        double yMax = *std::max_element(yValues.begin(), yValues.end());
        if (yMax <= yMin) yMax = yMin + 1.0;

        ImVec2 pos = ImGui::GetCursorScreenPos();
        ImGui::Dummy(ImVec2(w, chartH));
        ImDrawList* dl = ImGui::GetWindowDrawList();

        // layout: y-axis labels on the left, x labels at bottom
        ImGui::PushFont(app.fontRegular, ui::dp(11.5f));
        float labelH = ImGui::GetTextLineHeight();
        // choose ~5 y ticks with a 1/2/5 step
        double rawStep = (yMax - yMin) / 5.0;
        double mag = std::pow(10.0, std::floor(std::log10(std::max(rawStep, 1e-9))));
        double norm = rawStep / mag;
        double step = (norm <= 1 ? 1 : norm <= 2 ? 2 : norm <= 5 ? 5 : 10) * mag;
        double tickStart = std::ceil(yMin / step) * step;

        // measure y label width
        float yLabelW = 0;
        for (double t = tickStart; t <= yMax + step * 0.001; t += step) {
            std::string s = formatNumber(t, metric.decimals, true);
            yLabelW = std::max(yLabelW, ImGui::CalcTextSize(s.c_str()).x);
        }
        float plotX = pos.x + yLabelW + ui::dp(8);
        float plotW = w - yLabelW - ui::dp(10);
        float plotY = pos.y + ui::dp(4);
        float plotH = chartH - labelH - ui::dp(12);

        auto yToPx = [&](double v) {
            return plotY + plotH * (float)(1.0 - (v - yMin) / (yMax - yMin));
        };

        // gridlines + labels
        for (double t = tickStart; t <= yMax + step * 0.001; t += step) {
            float y = yToPx(t);
            dl->AddLine(ImVec2(plotX, y), ImVec2(plotX + plotW, y), IM_COL32(0xCA, 0xC4, 0xD0, 0x90), 1.0f);
            std::string s = formatNumber(t, metric.decimals, true);
            ImVec2 ts = ImGui::CalcTextSize(s.c_str());
            dl->AddText(ImVec2(plotX - ts.x - ui::dp(5), y - ts.y / 2), ui::COL_ON_SURFACE_VARIANT, s.c_str());
        }
        // baseline
        dl->AddLine(ImVec2(plotX, plotY + plotH), ImVec2(plotX + plotW, plotY + plotH),
                    ui::COL_OUTLINE, 1.0f);

        // bars
        int count = (int)series.size();
        float slot = plotW / count;
        float barW = std::max(ui::dp(2), slot * 0.62f);
        int labelStep = count <= 8 ? 1 : count <= 30 ? 5 : count <= 90 ? 14 : 30;
        for (int i = 0; i < count; i++) {
            double v = series[i].second;
            float cx = plotX + slot * (i + 0.5f);
            float top = yToPx(std::min(std::max(v, yMin), yMax));
            float bottom = plotY + plotH;
            if (v >= yMin) {
                // keep bars visible even when the value sits exactly on the
                // axis lower bound (single-day ranges)
                top = std::min(top, bottom - ui::dp(2));
                dl->AddRectFilled(ImVec2(cx - barW / 2, top), ImVec2(cx + barW / 2, bottom),
                                  ui::COL_PRIMARY, std::min(barW * 0.3f, ui::dp(3)),
                                  ImDrawFlags_RoundCornersTop);
            }
            if (i % labelStep == 0) {
                std::string lbl = formatDMMM(series[i].first);
                ImVec2 ts = ImGui::CalcTextSize(lbl.c_str());
                dl->AddText(ImVec2(cx - ts.x / 2, plotY + plotH + ui::dp(4)),
                            ui::COL_ON_SURFACE_VARIANT, lbl.c_str());
            }
        }
        // hover tooltip: show exact value per bar
        ImVec2 mouse = ImGui::GetIO().MousePos;
        if (mouse.x >= plotX && mouse.x <= plotX + plotW && mouse.y >= plotY && mouse.y <= plotY + plotH) {
            int idx = (int)((mouse.x - plotX) / slot);
            if (idx >= 0 && idx < count) {
                std::string tip = formatDMMM(series[idx].first) + ": " +
                                  formatNumber(series[idx].second, metric.decimals) + " " + metric.unit;
                ImGui::PopFont();
                ImGui::SetTooltip("%s", tip.c_str());
                ImGui::PushFont(app.fontRegular, ui::dp(11.5f));
            }
        }
        ImGui::PopFont();
        ImGui::Dummy(ImVec2(0, ui::dp(8)));
    }

    void drawSummary(App& app, float w,
                     const std::vector<std::pair<long long, double>>& series,
                     const GraphMetricDef& metric) {
        std::vector<double> statsValues;
        for (auto& s : series)
            if (metric.extract != -1 || s.second > 0.15) statsValues.push_back(s.second);
        if (statsValues.empty()) return;
        double total = 0;
        for (double v : statsValues) total += v;
        double avg = total / statsValues.size();
        double maxV = *std::max_element(statsValues.begin(), statsValues.end());
        double minV = *std::min_element(statsValues.begin(), statsValues.end());
        int totalCount = (int)series.size();
        int measuredCount = (int)statsValues.size();
        std::string countLabel = measuredCount < totalCount
            ? std::to_string(measuredCount) + " of " + std::to_string(totalCount) +
              (totalCount == 1 ? " day" : " days") + " measured"
            : std::to_string(totalCount) + (totalCount == 1 ? " day" : " days");

        int rows = metric.extract == -1 ? 3 : 4;
        ImGui::PushFont(app.fontRegular, ui::fsBodySmall());
        float rowH = ImGui::GetTextLineHeightWithSpacing();
        ImGui::PopFont();
        float cardH = ui::dp(16 * 2 + 26) + rows * rowH;
        ImVec2 pos = ImGui::GetCursorScreenPos();
        ImGui::Dummy(ImVec2(w, cardH));
        ui::cardBackground(pos, ImVec2(w, cardH));
        ImGui::SetCursorScreenPos(ImVec2(pos.x + ui::dp(16), pos.y + ui::dp(14)));
        ImGui::BeginGroup();
        ImGui::PushFont(app.fontBold, ui::fsBody());
        ImGui::Text("Summary (%s)", countLabel.c_str());
        ImGui::PopFont();
        ImGui::Dummy(ImVec2(0, ui::dp(2)));
        float innerW = w - ui::dp(32);
        auto statRow = [&](const char* label, double v) {
            ImGui::PushFont(app.fontRegular, ui::fsBodySmall());
            float x0 = ImGui::GetCursorPosX();
            ImGui::TextUnformatted(label);
            std::string val = formatNumber(v, metric.decimals) + " " + metric.unit;
            ImVec2 vs = ImGui::CalcTextSize(val.c_str());
            ImGui::SameLine();
            ImGui::SetCursorPosX(x0 + innerW - vs.x);
            ImGui::TextUnformatted(val.c_str());
            ImGui::PopFont();
        };
        if (metric.extract != -1) statRow("Total", total);
        statRow("Average per day", avg);
        statRow("Max in a day", maxV);
        statRow("Min in a day", minV);
        ImGui::EndGroup();
        ImGui::SetCursorScreenPos(ImVec2(pos.x, pos.y + cardH));
        ImGui::Dummy(ImVec2(0, 0));
    }
};

} // namespace

Screen* makeEatenGraphScreen(App& app) { return new EatenGraphScreen(app); }
