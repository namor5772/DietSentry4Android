// util.cpp — formatting, dates, description-marker helpers.
#include "app.h"
#include <ctime>
#include <cstdio>
#include <cstring>
#include <algorithm>
#include <shlobj.h>

const char* NUTRIENT_COLUMNS[NUTRIENT_COUNT] = {
    "Energy", "Protein", "FatTotal", "SaturatedFat", "TransFat",
    "PolyunsaturatedFat", "MonounsaturatedFat", "Carbohydrate", "Sugars",
    "DietaryFibre", "SodiumNa", "CalciumCa", "PotassiumK", "ThiaminB1",
    "RiboflavinB2", "NiacinB3", "Folate", "IronFe", "MagnesiumMg",
    "VitaminC", "Caffeine", "Cholesterol", "Alcohol"
};

const char* NUTRIENT_EDIT_LABELS[NUTRIENT_COUNT] = {
    "Energy (kJ)", "Protein (g)", "Fat, Total (g)", "- Saturated (g)", "- Trans (mg)",
    "- Polyunsaturated (g)", "- Monounsaturated (g)", "Carbohydrate (g)", "- Sugars (g)",
    "Dietary Fibre (g)", "Sodium (mg)", "Calcium (mg)", "Potassium (mg)", "Thiamin B1 (mg)",
    "Riboflavin B2 (mg)", "Niacin B3 (mg)", "Folate (ug)", "Iron (mg)", "Magnesium (mg)",
    "Vitamin C (mg)", "Caffeine (mg)", "Cholesterol (mg)", "Alcohol (g)"
};

static const char* MONTHS_ABBREV[12] = {
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
};

// ---------------------------------------------------------------------------
// Number formatting (NumberFormatUtils.kt)
// ---------------------------------------------------------------------------
static std::string addGrouping(const std::string& intPart) {
    std::string out;
    int count = 0;
    for (int i = (int)intPart.size() - 1; i >= 0; --i) {
        out.insert(out.begin(), intPart[i]);
        if (++count % 3 == 0 && i > 0) out.insert(out.begin(), ',');
    }
    return out;
}

std::string formatNumber(double value, int decimals, bool trimTrailingZero) {
    char buf[64];
    snprintf(buf, sizeof(buf), "%.*f", decimals, value);
    std::string s = buf;
    // split sign / int / frac
    std::string sign;
    if (!s.empty() && s[0] == '-') { sign = "-"; s.erase(0, 1); }
    std::string intPart = s, fracPart;
    size_t dot = s.find('.');
    if (dot != std::string::npos) { intPart = s.substr(0, dot); fracPart = s.substr(dot + 1); }
    std::string out = sign + addGrouping(intPart);
    if (decimals > 0) out += "." + fracPart;
    if (trimTrailingZero && decimals > 0) {
        while (!out.empty() && out.back() == '0') out.pop_back();
        if (!out.empty() && out.back() == '.') out.pop_back();
    }
    return out;
}

std::string formatAmount(double value, int decimals) {
    bool isWhole = std::fmod(value, 1.0) == 0.0;
    return isWhole ? formatNumber(value, 0) : formatNumber(value, decimals);
}

std::string formatWeight(double value) {
    char buf[32];
    snprintf(buf, sizeof(buf), "%.1f", value);
    return buf;
}

std::string formatOneDecimal(double value) { return formatWeight(value); }

std::string formatUsdCost(double amount) {
    char buf[32];
    snprintf(buf, sizeof(buf), "$%.4f", amount);
    return buf;
}

double roundTo2dp(double v) {
    return std::llround(v * 100.0) / 100.0;
}

// ---------------------------------------------------------------------------
// Dates. All millisecond timestamps are Unix epoch ms; formatting/parsing is
// done in local time, mirroring SimpleDateFormat with the default locale
// (month names pinned to English so database contents stay portable).
// ---------------------------------------------------------------------------
long long nowMillis() {
    FILETIME ft; GetSystemTimeAsFileTime(&ft);
    unsigned long long t = ((unsigned long long)ft.dwHighDateTime << 32) | ft.dwLowDateTime;
    return (long long)(t / 10000ULL) - 11644473600000LL;
}

static void millisToTm(long long millis, tm& out) {
    time_t secs = (time_t)(millis / 1000);
    // handle negative rounding for pre-1970 (not expected, but be safe)
    if (millis < 0 && millis % 1000 != 0) secs -= 1;
    localtime_s(&out, &secs);
}

static long long tmToMillis(tm t) {
    t.tm_isdst = -1;
    time_t secs = mktime(&t);
    return (long long)secs * 1000LL;
}

void millisToYmd(long long millis, int& y, int& m, int& d) {
    tm t; millisToTm(millis, t);
    y = t.tm_year + 1900; m = t.tm_mon + 1; d = t.tm_mday;
}

long long ymdToMillis(int y, int m, int d) {
    tm t = {};
    t.tm_year = y - 1900; t.tm_mon = m - 1; t.tm_mday = d;
    return tmToMillis(t);
}

long long localMidnight(long long millis) {
    int y, m, d; millisToYmd(millis, y, m, d);
    return ymdToMillis(y, m, d);
}

int daysInMonth(int y, int m) {
    static const int dm[12] = {31,28,31,30,31,30,31,31,30,31,30,31};
    if (m == 2) {
        bool leap = (y % 4 == 0 && y % 100 != 0) || (y % 400 == 0);
        return leap ? 29 : 28;
    }
    return dm[m - 1];
}

std::string formatDMMMYY(long long millis) {
    tm t; millisToTm(millis, t);
    char buf[32];
    snprintf(buf, sizeof(buf), "%d-%s-%02d", t.tm_mday, MONTHS_ABBREV[t.tm_mon], (t.tm_year + 1900) % 100);
    return buf;
}

std::string formatHHMM(long long millis) {
    tm t; millisToTm(millis, t);
    char buf[16];
    snprintf(buf, sizeof(buf), "%02d:%02d", t.tm_hour, t.tm_min);
    return buf;
}

std::string formatDDMMYYYY(long long millis) {
    tm t; millisToTm(millis, t);
    char buf[16];
    snprintf(buf, sizeof(buf), "%02d/%02d/%04d", t.tm_mday, t.tm_mon + 1, t.tm_year + 1900);
    return buf;
}

std::string formatDMMM(long long millis) {
    tm t; millisToTm(millis, t);
    char buf[16];
    snprintf(buf, sizeof(buf), "%d %s", t.tm_mday, MONTHS_ABBREV[t.tm_mon]);
    return buf;
}

std::optional<long long> parseDMMMYY(const std::string& s) {
    // "d-MMM-yy", e.g. "3-Jul-26" or "30-Jul-26"
    int day = 0, year2 = 0;
    char mon[8] = {0};
    if (sscanf_s(s.c_str(), "%d-%3s-%d", &day, mon, (unsigned)sizeof(mon), &year2) != 3) return std::nullopt;
    int monIdx = -1;
    for (int i = 0; i < 12; i++) {
        if (_stricmp(mon, MONTHS_ABBREV[i]) == 0) { monIdx = i; break; }
    }
    if (monIdx < 0 || day < 1 || day > 31) return std::nullopt;
    int year = (year2 < 100) ? 2000 + year2 : year2;   // SimpleDateFormat "yy" pivot ~2000
    return ymdToMillis(year, monIdx + 1, day);
}

std::optional<long long> parseDMMMYYHHMM(const std::string& d, const std::string& t) {
    auto dateMs = parseDMMMYY(d);
    if (!dateMs) return std::nullopt;
    int hh = 0, mm = 0;
    if (sscanf_s(t.c_str(), "%d:%d", &hh, &mm) != 2) return dateMs;
    return *dateMs + (long long)hh * 3600000LL + (long long)mm * 60000LL;
}

// ---------------------------------------------------------------------------
// Description conventions (regexes at the top of MainActivity.kt)
//   mlSuffixRegex        = "mL#?$"           (ignore case)
//   trailingMarkersRegex = " #$| mL#?$"      (ignore case)
//   recipeMarkerRegex    = "\{recipe=[^}]+\}" (ignore case)
// ---------------------------------------------------------------------------
bool isLiquidDescription(const std::string& description) {
    const std::string& s = description;
    size_t n = s.size();
    auto ieq = [](char a, char b) { return tolower((unsigned char)a) == tolower((unsigned char)b); };
    if (n >= 3 && ieq(s[n-3], 'm') && ieq(s[n-2], 'l') && s[n-1] == '#') return true;
    if (n >= 2 && ieq(s[n-2], 'm') && ieq(s[n-1], 'l')) return true;
    return false;
}

std::string descriptionUnit(const std::string& description) {
    return isLiquidDescription(description) ? "mL" : "g";
}

static bool findRecipeMarker(const std::string& s, size_t& start, size_t& end) {
    // find "{recipe=" (case-insensitive) ... "}"
    std::string lower = toLowerAscii(s);
    size_t p = lower.find("{recipe=");
    if (p == std::string::npos) return false;
    size_t close = s.find('}', p);
    if (close == std::string::npos) return false;
    start = p; end = close + 1;
    return true;
}

bool isRecipeDescription(const std::string& description) {
    size_t a, b;
    return findRecipeMarker(description, a, b);
}

std::string stripTrailingRecipeSuffix(const std::string& description) {
    std::string s = description;
    // remove trailing "\s*\*$" then "\s*#\s*$" then trailing spaces
    auto rtrimSpaces = [](std::string& x) {
        while (!x.empty() && isspace((unsigned char)x.back())) x.pop_back();
    };
    std::string t = s;
    rtrimSpaces(t);
    if (!t.empty() && t.back() == '*') { t.pop_back(); rtrimSpaces(t); s = t; }
    t = s;
    rtrimSpaces(t);
    if (!t.empty() && t.back() == '#') { t.pop_back(); rtrimSpaces(t); s = t; }
    rtrimSpaces(s);
    return s;
}

static std::string removeTrailingMarkers(const std::string& s) {
    // trailingMarkersRegex = " #$| mL#?$" (ignore case), single replacement
    size_t n = s.size();
    auto ieq = [](char a, char b) { return tolower((unsigned char)a) == tolower((unsigned char)b); };
    if (n >= 4 && s[n-4] == ' ' && ieq(s[n-3], 'm') && ieq(s[n-2], 'l') && s[n-1] == '#')
        return s.substr(0, n - 4);
    if (n >= 3 && s[n-3] == ' ' && ieq(s[n-2], 'm') && ieq(s[n-1], 'l'))
        return s.substr(0, n - 3);
    if (n >= 2 && s[n-2] == ' ' && s[n-1] == '#')
        return s.substr(0, n - 2);
    return s;
}

std::string descriptionDisplayName(const std::string& description) {
    std::string cleaned = removeTrailingMarkers(description);
    if (isRecipeDescription(cleaned)) {
        return stripTrailingRecipeSuffix(cleaned);
    }
    return cleaned;
}

std::string removeRecipeMarker(const std::string& description) {
    std::string s = description;
    size_t a, b;
    while (findRecipeMarker(s, a, b)) {
        s.erase(a, b - a);
    }
    while (!s.empty() && isspace((unsigned char)s.back())) s.pop_back();
    return stripTrailingRecipeSuffix(s);
}

void extractDescriptionParts(const std::string& description, std::string& base, std::string& suffix) {
    if (endsWith(description, " mL#")) { base = description.substr(0, description.size() - 4); suffix = " mL#"; return; }
    if (endsWith(description, " mL"))  { base = description.substr(0, description.size() - 3); suffix = " mL";  return; }
    if (endsWith(description, " #"))   { base = description.substr(0, description.size() - 2); suffix = " #";   return; }
    base = description; suffix = "";
}

// ---------------------------------------------------------------------------
// String helpers
// ---------------------------------------------------------------------------
std::string toLowerAscii(const std::string& s) {
    std::string out = s;
    std::transform(out.begin(), out.end(), out.begin(),
                   [](unsigned char c) { return (char)tolower(c); });
    return out;
}

bool startsWithIgnoreCase(const std::string& s, const std::string& prefix) {
    if (prefix.size() > s.size()) return false;
    for (size_t i = 0; i < prefix.size(); i++)
        if (tolower((unsigned char)s[i]) != tolower((unsigned char)prefix[i])) return false;
    return true;
}

bool containsIgnoreCase(const std::string& hay, const std::string& needle) {
    if (needle.empty()) return true;
    std::string h = toLowerAscii(hay), n = toLowerAscii(needle);
    return h.find(n) != std::string::npos;
}

bool endsWith(const std::string& s, const std::string& suffix) {
    return s.size() >= suffix.size() &&
           s.compare(s.size() - suffix.size(), suffix.size(), suffix) == 0;
}

std::string trim(const std::string& s) {
    size_t b = 0, e = s.size();
    while (b < e && isspace((unsigned char)s[b])) b++;
    while (e > b && isspace((unsigned char)s[e - 1])) e--;
    return s.substr(b, e - b);
}

std::vector<std::string> splitString(const std::string& s, char sep) {
    std::vector<std::string> out;
    size_t start = 0;
    for (size_t i = 0; i <= s.size(); i++) {
        if (i == s.size() || s[i] == sep) {
            out.push_back(s.substr(start, i - start));
            start = i + 1;
        }
    }
    return out;
}

std::string base64Encode(const unsigned char* data, size_t len) {
    static const char* tbl = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    std::string out;
    out.reserve(((len + 2) / 3) * 4);
    for (size_t i = 0; i < len; i += 3) {
        unsigned int v = data[i] << 16;
        if (i + 1 < len) v |= data[i + 1] << 8;
        if (i + 2 < len) v |= data[i + 2];
        out.push_back(tbl[(v >> 18) & 63]);
        out.push_back(tbl[(v >> 12) & 63]);
        out.push_back(i + 1 < len ? tbl[(v >> 6) & 63] : '=');
        out.push_back(i + 2 < len ? tbl[v & 63] : '=');
    }
    return out;
}

std::wstring utf8ToWide(const std::string& s) {
    if (s.empty()) return L"";
    int n = MultiByteToWideChar(CP_UTF8, 0, s.c_str(), (int)s.size(), nullptr, 0);
    std::wstring w(n, 0);
    MultiByteToWideChar(CP_UTF8, 0, s.c_str(), (int)s.size(), &w[0], n);
    return w;
}

std::string wideToUtf8(const std::wstring& w) {
    if (w.empty()) return "";
    int n = WideCharToMultiByte(CP_UTF8, 0, w.c_str(), (int)w.size(), nullptr, 0, nullptr, nullptr);
    std::string s(n, 0);
    WideCharToMultiByte(CP_UTF8, 0, w.c_str(), (int)w.size(), &s[0], n, nullptr, nullptr);
    return s;
}

std::optional<double> parseDouble(const std::string& s) {
    std::string t = trim(s);
    if (t.empty()) return std::nullopt;
    char* end = nullptr;
    double v = strtod(t.c_str(), &end);
    if (end == nullptr || *end != '\0') return std::nullopt;
    return v;
}

std::string csvCell(const std::string& value) {
    std::string escaped;
    for (char c : value) {
        if (c == '"') escaped += "\"\"";
        else escaped += c;
    }
    return "\"" + escaped + "\"";
}

// ---------------------------------------------------------------------------
// Filesystem
// ---------------------------------------------------------------------------
std::wstring exeDirectory() {
    wchar_t buf[MAX_PATH];
    GetModuleFileNameW(nullptr, buf, MAX_PATH);
    std::wstring p = buf;
    size_t slash = p.find_last_of(L"\\/");
    return (slash == std::wstring::npos) ? p : p.substr(0, slash);
}

std::wstring appDataDirectory() {
    wchar_t* raw = nullptr;
    std::wstring dir;
    if (SUCCEEDED(SHGetKnownFolderPath(FOLDERID_RoamingAppData, 0, nullptr, &raw))) {
        dir = raw;
        CoTaskMemFree(raw);
    } else {
        dir = exeDirectory();
    }
    dir += L"\\DietSentry4Windows";
    CreateDirectoryW(dir.c_str(), nullptr);
    return dir;
}

bool fileExists(const std::wstring& path) {
    DWORD attrs = GetFileAttributesW(path.c_str());
    return attrs != INVALID_FILE_ATTRIBUTES && !(attrs & FILE_ATTRIBUTE_DIRECTORY);
}

std::optional<std::string> readTextFile(const std::wstring& path) {
    HANDLE h = CreateFileW(path.c_str(), GENERIC_READ, FILE_SHARE_READ, nullptr,
                           OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (h == INVALID_HANDLE_VALUE) return std::nullopt;
    LARGE_INTEGER size;
    GetFileSizeEx(h, &size);
    std::string content((size_t)size.QuadPart, 0);
    DWORD read = 0;
    bool ok = true;
    if (size.QuadPart > 0)
        ok = ReadFile(h, &content[0], (DWORD)size.QuadPart, &read, nullptr) && read == (DWORD)size.QuadPart;
    CloseHandle(h);
    if (!ok) return std::nullopt;
    // strip UTF-8 BOM
    if (content.size() >= 3 && (unsigned char)content[0] == 0xEF &&
        (unsigned char)content[1] == 0xBB && (unsigned char)content[2] == 0xBF)
        content.erase(0, 3);
    return content;
}

bool writeTextFile(const std::wstring& path, const std::string& content) {
    HANDLE h = CreateFileW(path.c_str(), GENERIC_WRITE, 0, nullptr,
                           CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (h == INVALID_HANDLE_VALUE) return false;
    DWORD written = 0;
    bool ok = WriteFile(h, content.data(), (DWORD)content.size(), &written, nullptr) &&
              written == content.size();
    CloseHandle(h);
    return ok;
}

// ---------------------------------------------------------------------------
// aggregateDailyTotals (MainActivity.kt) — groupBy preserves first-seen order,
// which for EatenTs-desc input means newest date first, same as Android.
// ---------------------------------------------------------------------------
std::vector<DailyTotals> aggregateDailyTotals(const std::vector<EatenFood>& eatenFoods) {
    std::vector<DailyTotals> out;
    std::vector<std::string> order;
    for (const auto& ef : eatenFoods) {
        DailyTotals* slot = nullptr;
        for (auto& dt : out) {
            if (dt.date == ef.dateEaten) { slot = &dt; break; }
        }
        if (!slot) {
            out.push_back({});
            slot = &out.back();
            slot->date = ef.dateEaten;
        }
        slot->amountEaten += ef.amountEaten;
        slot->n.add(ef.n);
    }
    // unit labels need the full group; recompute per date
    for (auto& dt : out) {
        bool allMl = true, allGrams = true;
        for (const auto& ef : eatenFoods) {
            if (ef.dateEaten != dt.date) continue;
            if (isLiquidDescription(ef.foodDescription)) allGrams = false;
            else allMl = false;
        }
        dt.unitLabel = allMl ? "mL" : (allGrams ? "g" : "mixed units");
    }
    return out;
}
