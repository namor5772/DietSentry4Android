// util.cpp — formatting, dates, description-marker helpers.
// macOS port: identical logic to winport/src/util.cpp, with the platform
// sections (clock, localtime, UTF conversion, filesystem) on POSIX/Mach.
#include "app.h"
#include <ctime>
#include <cstdio>
#include <cstring>
#include <cstdlib>
#include <algorithm>
#include <sys/time.h>
#include <sys/stat.h>
#include <unistd.h>
#include <mach-o/dyld.h>

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

// Month names as Android's SimpleDateFormat("d-MMM-yy") emits them in the
// en_AU locale the phone app runs under: three letters, except June / July /
// Sept which are spelled out. Matching this exactly keeps rows written on
// macOS byte-identical with rows written on the phone, so daily totals
// group correctly and foods.db round-trips between the apps.
static const char* MONTHS_ABBREV[12] = {
    "Jan", "Feb", "Mar", "Apr", "May", "June",
    "July", "Aug", "Sept", "Oct", "Nov", "Dec"
};

static const char* MONTHS_FULL[12] = {
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
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
    timeval tv;
    gettimeofday(&tv, nullptr);
    return (long long)tv.tv_sec * 1000LL + tv.tv_usec / 1000;
}

static void millisToTm(long long millis, tm& out) {
    time_t secs = (time_t)(millis / 1000);
    // handle negative rounding for pre-1970 (not expected, but be safe)
    if (millis < 0 && millis % 1000 != 0) secs -= 1;
    localtime_r(&secs, &out);
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
    // "d-MMM-yy" as written by any SimpleDateFormat month dialect:
    // "30-Jul-26", "30-July-26", "5-Sept-25", "5-Sep.-25", "1-Dec-25", ...
    // The month token is matched as a case-insensitive prefix (>= 3 letters)
    // of the full English month name, ignoring any trailing period.
    auto parts = splitString(trim(s), '-');
    if (parts.size() != 3) return std::nullopt;
    auto dayOpt = parseDouble(trim(parts[0]));
    if (!dayOpt) return std::nullopt;
    int day = (int)*dayOpt;
    std::string mon;
    for (char c : trim(parts[1]))
        if (isalpha((unsigned char)c)) mon += c;
    if (mon.size() < 3) return std::nullopt;
    int monIdx = -1;
    for (int i = 0; i < 12; i++) {
        if (mon.size() <= strlen(MONTHS_FULL[i]) &&
            strncasecmp(mon.c_str(), MONTHS_FULL[i], mon.size()) == 0) {
            monIdx = i;
            break;
        }
    }
    auto yearOpt = parseDouble(trim(parts[2]));
    if (!yearOpt || monIdx < 0 || day < 1 || day > 31) return std::nullopt;
    int year2 = (int)*yearOpt;
    int year = (year2 < 100) ? 2000 + year2 : year2;   // SimpleDateFormat "yy" pivot ~2000
    return ymdToMillis(year, monIdx + 1, day);
}

std::optional<long long> parseDMMMYYHHMM(const std::string& d, const std::string& t) {
    auto dateMs = parseDMMMYY(d);
    if (!dateMs) return std::nullopt;
    int hh = 0, mm = 0;
    if (sscanf(t.c_str(), "%d:%d", &hh, &mm) != 2) return dateMs;
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

// UTF-8 <-> wide (wchar_t is UTF-32 on macOS). Hand-rolled to avoid the
// deprecated <codecvt>; invalid sequences degrade to '?' rather than throwing.
std::wstring utf8ToWide(const std::string& s) {
    std::wstring out;
    out.reserve(s.size());
    size_t i = 0;
    while (i < s.size()) {
        unsigned char c = s[i];
        unsigned int cp = 0;
        int extra = 0;
        if (c < 0x80) { cp = c; extra = 0; }
        else if ((c >> 5) == 0x6) { cp = c & 0x1F; extra = 1; }
        else if ((c >> 4) == 0xE) { cp = c & 0x0F; extra = 2; }
        else if ((c >> 3) == 0x1E) { cp = c & 0x07; extra = 3; }
        else { out.push_back(L'?'); i++; continue; }
        if (i + extra >= s.size() + (extra == 0 ? 1 : 0) && i + extra > s.size() - 1 && extra > 0) {
            out.push_back(L'?'); i++; continue;
        }
        bool bad = false;
        for (int k = 1; k <= extra; k++) {
            if (i + k >= s.size() || ((unsigned char)s[i + k] >> 6) != 0x2) { bad = true; break; }
            cp = (cp << 6) | ((unsigned char)s[i + k] & 0x3F);
        }
        if (bad) { out.push_back(L'?'); i++; continue; }
        out.push_back((wchar_t)cp);
        i += extra + 1;
    }
    return out;
}

std::string wideToUtf8(const std::wstring& w) {
    std::string out;
    out.reserve(w.size() * 2);
    for (wchar_t wc : w) {
        unsigned int cp = (unsigned int)wc;
        if (cp < 0x80) {
            out.push_back((char)cp);
        } else if (cp < 0x800) {
            out.push_back((char)(0xC0 | (cp >> 6)));
            out.push_back((char)(0x80 | (cp & 0x3F)));
        } else if (cp < 0x10000) {
            out.push_back((char)(0xE0 | (cp >> 12)));
            out.push_back((char)(0x80 | ((cp >> 6) & 0x3F)));
            out.push_back((char)(0x80 | (cp & 0x3F)));
        } else {
            out.push_back((char)(0xF0 | (cp >> 18)));
            out.push_back((char)(0x80 | ((cp >> 12) & 0x3F)));
            out.push_back((char)(0x80 | ((cp >> 6) & 0x3F)));
            out.push_back((char)(0x80 | (cp & 0x3F)));
        }
    }
    return out;
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
// Filesystem (POSIX, UTF-8 at the boundary)
// ---------------------------------------------------------------------------
std::wstring exeDirectory() {
    char small[1024];
    uint32_t size = sizeof(small);
    std::string path;
    if (_NSGetExecutablePath(small, &size) == 0) {
        path = small;
    } else {
        std::vector<char> big(size + 1);
        _NSGetExecutablePath(big.data(), &size);
        path = big.data();
    }
    char resolved[PATH_MAX];
    if (realpath(path.c_str(), resolved)) path = resolved;
    size_t slash = path.find_last_of('/');
    if (slash != std::string::npos) path = path.substr(0, slash);
    return utf8ToWide(path);
}

std::wstring assetsDirectory() {
    // Bare binary: assets/ next to the executable. App bundle:
    // DietSentry.app/Contents/MacOS/DietSentry -> ../Resources/assets.
    std::wstring exeDir = exeDirectory();
    std::wstring beside = exeDir + L"/assets";
    if (directoryExists(beside)) return beside;
    std::wstring bundled = exeDir + L"/../Resources/assets";
    if (directoryExists(bundled)) return bundled;
    return beside;
}

std::wstring appDataDirectory() {
    const char* home = getenv("HOME");
    std::string dir = home ? home : ".";
    dir += "/Library/Application Support/DietSentry4Mac";
    mkdir(dir.c_str(), 0755);   // parents always exist on macOS
    return utf8ToWide(dir);
}

bool fileExists(const std::wstring& path) {
    struct stat st;
    return stat(wideToUtf8(path).c_str(), &st) == 0 && S_ISREG(st.st_mode);
}

bool directoryExists(const std::wstring& path) {
    struct stat st;
    return stat(wideToUtf8(path).c_str(), &st) == 0 && S_ISDIR(st.st_mode);
}

bool copyFile(const std::wstring& src, const std::wstring& dst) {
    FILE* in = fopen(wideToUtf8(src).c_str(), "rb");
    if (!in) return false;
    FILE* out = fopen(wideToUtf8(dst).c_str(), "wb");
    if (!out) { fclose(in); return false; }
    char buf[1 << 16];
    size_t n;
    bool ok = true;
    while ((n = fread(buf, 1, sizeof(buf), in)) > 0) {
        if (fwrite(buf, 1, n, out) != n) { ok = false; break; }
    }
    if (ferror(in)) ok = false;
    fclose(in);
    if (fclose(out) != 0) ok = false;
    return ok;
}

bool deleteFile(const std::wstring& path) {
    return ::remove(wideToUtf8(path).c_str()) == 0;
}

std::optional<std::string> readTextFile(const std::wstring& path) {
    FILE* f = fopen(wideToUtf8(path).c_str(), "rb");
    if (!f) return std::nullopt;
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (size < 0) { fclose(f); return std::nullopt; }
    std::string content((size_t)size, 0);
    bool ok = size == 0 || fread(&content[0], 1, (size_t)size, f) == (size_t)size;
    fclose(f);
    if (!ok) return std::nullopt;
    // strip UTF-8 BOM
    if (content.size() >= 3 && (unsigned char)content[0] == 0xEF &&
        (unsigned char)content[1] == 0xBB && (unsigned char)content[2] == 0xBF)
        content.erase(0, 3);
    return content;
}

bool writeTextFile(const std::wstring& path, const std::string& content) {
    FILE* f = fopen(wideToUtf8(path).c_str(), "wb");
    if (!f) return false;
    bool ok = content.empty() || fwrite(content.data(), 1, content.size(), f) == content.size();
    if (fclose(f) != 0) ok = false;
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
