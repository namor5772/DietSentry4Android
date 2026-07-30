// DietSentry for macOS — C++ port of DietSentry4Android (macOS sibling of
// winport/). Models, constants and shared declarations. Mirrors
// MainActivity.kt + DatabaseHelper.kt naming so the codebases stay easy to
// diff; kept line-compatible with winport/src/app.h where possible.
#pragma once
#include <string>
#include <vector>
#include <optional>
#include <functional>
#include <memory>
#include <cmath>
#include <cstdint>
#include <cstring>
#include "imgui.h"

// ---------------------------------------------------------------------------
// Nutrients: the 23 numeric columns shared by Foods / Eaten / Recipe rows.
// Order matches the SQLite column order used throughout the Android app.
// ---------------------------------------------------------------------------
struct Nutrients {
    double v[23] = {0};

    double& energy()             { return v[0]; }
    double& protein()            { return v[1]; }
    double& fatTotal()           { return v[2]; }
    double& saturatedFat()       { return v[3]; }
    double& transFat()           { return v[4]; }
    double& polyunsaturatedFat() { return v[5]; }
    double& monounsaturatedFat() { return v[6]; }
    double& carbohydrate()       { return v[7]; }
    double& sugars()             { return v[8]; }
    double& dietaryFibre()       { return v[9]; }
    double& sodiumNa()           { return v[10]; }
    double& calciumCa()          { return v[11]; }
    double& potassiumK()         { return v[12]; }
    double& thiaminB1()          { return v[13]; }
    double& riboflavinB2()       { return v[14]; }
    double& niacinB3()           { return v[15]; }
    double& folate()             { return v[16]; }
    double& ironFe()             { return v[17]; }
    double& magnesiumMg()        { return v[18]; }
    double& vitaminC()           { return v[19]; }
    double& caffeine()           { return v[20]; }
    double& cholesterol()        { return v[21]; }
    double& alcohol()            { return v[22]; }
    const double& operator[](int i) const { return v[i]; }
    double& operator[](int i) { return v[i]; }

    Nutrients scaled(double s) const {
        Nutrients out;
        for (int i = 0; i < 23; i++) out.v[i] = v[i] * s;
        return out;
    }
    void add(const Nutrients& o) {
        for (int i = 0; i < 23; i++) v[i] += o.v[i];
    }
};

static const int NUTRIENT_COUNT = 23;
// SQLite / JSON column names, in Nutrients order.
extern const char* NUTRIENT_COLUMNS[NUTRIENT_COUNT];
// Edit-form labels, in Nutrients order ("Energy (kJ)", "Protein (g)", ...).
extern const char* NUTRIENT_EDIT_LABELS[NUTRIENT_COUNT];

// ---------------------------------------------------------------------------
// Models (Food.kt, EatenFood.kt, RecipeItem.kt, WeightEntry.kt, DailyTotals)
// ---------------------------------------------------------------------------
struct Food {
    int foodId = 0;
    std::string foodDescription;
    Nutrients n;
    std::string notes;
};

struct EatenFood {
    int eatenId = 0;
    std::string dateEaten;   // "d-MMM-yy"
    std::string timeEaten;   // "HH:mm"
    int eatenTs = 0;         // minutes since REFERENCE_TIMESTAMP_SECONDS
    double amountEaten = 0;  // g or mL
    std::string foodDescription;
    Nutrients n;
};

struct RecipeItem {
    int recipeId = 0;
    int foodId = 0;
    int copyFg = 0;
    double amount = 0;       // grams
    std::string foodDescription;
    Nutrients n;
};

struct WeightEntry {
    int weightId = 0;
    std::string dateWeight;  // "d-MMM-yy"
    double weight = 0;       // kg
    std::string comments;
};

struct DailyTotals {
    std::string date;        // "d-MMM-yy"
    std::string unitLabel;   // "g", "mL" or "mixed units"
    double amountEaten = 0;
    Nutrients n;
};

// ---------------------------------------------------------------------------
// Constants (MainActivity.kt top-level)
// ---------------------------------------------------------------------------
#define PREF_KEY_NUTRITION_SELECTION_FOOD  "nutritionSelectionFood"
#define PREF_KEY_NUTRITION_SELECTION_EATEN "nutritionSelectionEaten"
#define PREF_KEY_DISPLAY_DAILY_TOTALS      "displayDailyTotals"
#define PREF_KEY_FILTER_EATEN_BY_DATE      "filterEatenByDate"
#define PREF_KEY_GRAPH_METRIC              "graphMetric"
#define PREF_KEY_GRAPH_RANGE               "graphRange"
#define PREF_KEY_GRAPH_CUSTOM_START        "graphCustomStart"
#define PREF_KEY_GRAPH_CUSTOM_END          "graphCustomEnd"
#define PREF_KEY_EXCHANGE_FOLDER           "exchangeFolderPath"
#define PREF_KEY_ANTHROPIC_API_KEY         "anthropicApiKey"
#define PREF_KEY_ANTHROPIC_MODEL           "anthropicModel"
#define PREF_KEY_AI_WEB_SEARCH             "aiWebSearch"
#define PREF_KEY_AI_USE_NIP_PROMPT         "aiUseNipPrompt"
#define PREF_KEY_AI_EXTENDED_THINKING      "aiExtendedThinking"
#define PREF_KEY_AI_USER_PROFILE           "aiUserProfile"

#define DATABASE_FILE_NAME  "foods.db"
#define DAILY_CSV_FILE_NAME "EatenDailyAll.csv"

static const long long REFERENCE_TIMESTAMP_SECONDS = 1672491600LL; // as Android
static const char* const DEFAULT_ANTHROPIC_MODEL = "claude-sonnet-4-6";
static const char* const ANTHROPIC_VERSION = "2023-06-01";
static const int ANTHROPIC_MAX_TOKENS = 16384;
static const int AI_IMAGE_MAX_DIM = 1568;
static const char* const WEB_SEARCH_TOOL_TYPE = "web_search_20250305";
static const int WEB_SEARCH_MAX_USES = 5;
static const double WEB_SEARCH_COST_PER_REQUEST = 0.01;
static const double CACHE_WRITE_MULTIPLIER = 1.25;
static const double CACHE_READ_MULTIPLIER = 0.1;
static const char* const LOOKUP_FOOD_TOOL_NAME = "lookup_food";
static const int LOOKUP_FOOD_MAX_RESULTS = 5;
static const int MAX_TOOL_ITERATIONS = 12;

// ---------------------------------------------------------------------------
// util.cpp — formatting, dates, description helpers
// ---------------------------------------------------------------------------
std::string formatNumber(double value, int decimals = 1, bool trimTrailingZero = false);
std::string formatAmount(double value, int decimals = 1);
std::string formatWeight(double value);
std::string formatOneDecimal(double value);
std::string formatUsdCost(double amount);

double roundTo2dp(double v);

// Millisecond epoch timestamps, interpreted in local time.
long long nowMillis();
long long localMidnight(long long millis);          // strip time-of-day
std::string formatDMMMYY(long long millis);          // "30-Jul-26"
std::string formatHHMM(long long millis);            // "13:05"
std::string formatDDMMYYYY(long long millis);        // "30/07/2026"
std::string formatDMMM(long long millis);            // "30 Jul"
std::optional<long long> parseDMMMYY(const std::string& s);          // date only
std::optional<long long> parseDMMMYYHHMM(const std::string& d, const std::string& t);
void millisToYmd(long long millis, int& y, int& m, int& d);
long long ymdToMillis(int y, int m, int d);          // local midnight
int daysInMonth(int y, int m);

// Description conventions (mL / # / {recipe=..} markers)
bool isLiquidDescription(const std::string& description);
std::string descriptionUnit(const std::string& description);         // "mL" or "g"
std::string descriptionDisplayName(const std::string& description);
bool isRecipeDescription(const std::string& description);
std::string removeRecipeMarker(const std::string& description);
std::string stripTrailingRecipeSuffix(const std::string& description);
// -> {base, suffix} where suffix is " mL#", " mL", " #" or ""
void extractDescriptionParts(const std::string& description, std::string& base, std::string& suffix);

// misc string helpers
std::string toLowerAscii(const std::string& s);
bool startsWithIgnoreCase(const std::string& s, const std::string& prefix);
bool containsIgnoreCase(const std::string& hay, const std::string& needle);
bool endsWith(const std::string& s, const std::string& suffix);
std::string trim(const std::string& s);
std::vector<std::string> splitString(const std::string& s, char sep);
std::string base64Encode(const unsigned char* data, size_t len);
std::wstring utf8ToWide(const std::string& s);
std::string wideToUtf8(const std::wstring& w);
std::optional<double> parseDouble(const std::string& s);             // strict, like toDoubleOrNull()
std::string csvCell(const std::string& value);

// filesystem helpers (std::wstring paths for line-compatibility with winport;
// converted to UTF-8 at the POSIX boundary)
std::wstring exeDirectory();
std::wstring assetsDirectory();      // assets next to the binary, or in the .app bundle Resources
std::wstring appDataDirectory();     // ~/Library/Application Support/DietSentry4Mac (created)
bool fileExists(const std::wstring& path);
bool directoryExists(const std::wstring& path);
bool copyFile(const std::wstring& src, const std::wstring& dst);     // overwrite
bool deleteFile(const std::wstring& path);
std::optional<std::string> readTextFile(const std::wstring& path);
bool writeTextFile(const std::wstring& path, const std::string& content);

// ---------------------------------------------------------------------------
// Aggregation (MainActivity.kt aggregateDailyTotals)
// ---------------------------------------------------------------------------
std::vector<DailyTotals> aggregateDailyTotals(const std::vector<EatenFood>& eatenFoods);

// ---------------------------------------------------------------------------
// Prefs (SharedPreferences equivalent) — prefs.cpp
// ---------------------------------------------------------------------------
struct Prefs {
    void load();
    int getInt(const char* key, int def) const;
    bool getBool(const char* key, bool def) const;
    long long getLong(const char* key, long long def) const;
    std::string getString(const char* key, const std::string& def) const;
    void putInt(const char* key, int v);
    void putBool(const char* key, bool v);
    void putLong(const char* key, long long v);
    void putString(const char* key, const std::string& v);
    void remove(const char* key);
private:
    void save();
    void* impl = nullptr; // nlohmann::json*, kept opaque to avoid heavy include
};

// ---------------------------------------------------------------------------
// Database (DatabaseHelper.kt) — db.cpp
// ---------------------------------------------------------------------------
struct sqlite3;
struct Db {
    bool open();                                   // bootstrap from assets on first run
    void close();
    std::wstring databasePath() const;

    bool insertRecipeFromFood(const Food& food, double amount, int foodId, int copyFlag);
    bool insertWeight(const std::string& dateWeight, double weight, const std::string& comments);
    std::vector<WeightEntry> readWeights();        // sorted by date desc then id desc
    bool updateWeight(int weightId, const std::string& dateWeight, double weight, const std::string& comments);
    bool deleteWeight(int weightId);
    bool logEatenFood(const Food& food, double amount, long long dateTimeMillis);
    bool updateEatenFood(const EatenFood& eatenFood, double newAmount, long long newDateTimeMillis);
    bool deleteFood(int foodId);
    bool updateFood(const Food& food);
    bool insertFood(const Food& food);
    std::optional<int> insertFoodReturningId(const Food& food);
    std::optional<Food> getFoodById(int foodId);
    bool deleteEatenFood(int eatenId);
    std::vector<Food> readFoodsFromDatabase();
    std::vector<Food> readFoodsSortedByIdDesc();
    std::vector<EatenFood> readEatenFoods();       // ORDER BY EatenTs DESC
    bool deleteRecipesWithFoodIdZero();
    bool updateRecipeFoodIdForTemporaryRecords(int newFoodId);
    bool copyRecipesForFood(int foodId);
    bool duplicateRecipesToFoodIdZero(int foodId);
    bool replaceOriginalRecipesWithCopies(int foodId);
    bool deleteRecipesByFoodId(int foodId);
    bool deleteCopiedRecipes(int foodId);
    bool deleteAllCopiedRecipes();
    bool updateRecipe(const RecipeItem& recipe);
    bool deleteRecipe(int recipeId);
    std::vector<RecipeItem> readRecipes();         // FoodId = 0
    std::vector<RecipeItem> readCopiedRecipes(int foodId);
    std::vector<Food> searchFoods(const std::string& query);

    bool exportDatabaseTo(const std::wstring& destPath);    // close -> copy -> reopen
    bool replaceDatabaseFrom(const std::wstring& srcPath);  // close -> copy in -> reopen + guards

    sqlite3* handle = nullptr;
private:
    void ensureWeightTableExists();
    void ensureFoodsTableHasNotes();
};

// ---------------------------------------------------------------------------
// Navigation + App state
// ---------------------------------------------------------------------------
struct App;

struct Screen {
    virtual ~Screen() = default;
    virtual const char* route() const = 0;
    virtual void draw(App& app) = 0;
    // Android BackHandler equivalent: return true if the back press was
    // consumed (e.g. cleared a selection); false = pop the screen.
    virtual bool onBack(App& app) { (void)app; return false; }
};

// Result flags the Foods Table consumes on its next frame — the port of the
// savedStateHandle keys foodUpdated / foodInserted / foodInsertedDescription /
// foodUpdatedDescription / sortFoodsDescOnce.
struct FoodsResultFlags {
    bool foodUpdated = false;
    bool foodInserted = false;
    bool sortFoodsDescOnce = false;
    std::optional<std::string> foodInsertedDescription;
    std::optional<std::string> foodUpdatedDescription;
};

struct Toast { std::string text; double expiry; };

struct AiImage; // imageutil.h

struct App {
    Db db;
    Prefs prefs;

    // Navigation stack. Mutations are deferred to end-of-frame so the screen
    // currently drawing is never destroyed mid-draw.
    std::vector<std::unique_ptr<Screen>> nav;
    std::vector<std::function<void()>> pendingNavOps;
    void push(Screen* s);
    void pop();
    bool popTo(const char* route);     // pop back to route (not inclusive)
    void requestBack();                // routed through top screen's onBack()
    Screen* findScreen(const char* route);

    FoodsResultFlags foodsResult;

    // Session-scoped in-memory state (top-level vars in MainActivity.kt)
    std::optional<long long> sessionSelectedFilterDateMillis;
    std::string sessionAddRecipeSearchQuery;
    std::string sessionCopyRecipeSearchQuery;
    std::string sessionEditRecipeSearchQuery;
    std::optional<std::string> sessionPrefilledJson;
    std::optional<double> sessionPrefilledJsonCost;

    // Bundled system prompts (assets/*.txt, loaded once)
    std::string nipPrompt, recipePrompt, explainPrompt, genericPrompt, genericWebSearchClause;

    // Fonts
    ImFont* fontRegular = nullptr;
    ImFont* fontBold = nullptr;
    ImFont* fontMono = nullptr;
    float uiScale = 1.0f;

    // Toasts
    std::vector<Toast> toasts;
    void toast(const std::string& msg);
    void drawToasts();

    // Metal texture creation for AI image thumbnails (implemented in main.mm);
    // returns a retained id<MTLTexture> bridged to void*.
    void* createTextureRGBA(const unsigned char* rgba, int w, int h);
    void releaseTexture(void* tex);

    bool quitRequested = false;
};

// Screen factories (defined in the screens_*.cpp files)
Screen* makeFoodSearchScreen(App& app);
Screen* makeEatenLogScreen(App& app);
Screen* makeEditFoodScreen(App& app, int foodId);
Screen* makeCopyFoodScreen(App& app, int foodId);
Screen* makeInsertFoodScreen(App& app);
Screen* makeAddFoodByJsonScreen(App& app);
Screen* makeAddFoodByAiScreen(App& app);
Screen* makeAddRecipeScreen(App& app);                 // "Add Recipe"
Screen* makeEditRecipeScreen(App& app, int foodId);    // "Editing Recipe"
Screen* makeCopyRecipeScreen(App& app, int foodId);    // "Copying Recipe"
Screen* makeUtilitiesScreen(App& app);
Screen* makeEatenGraphScreen(App& app);
