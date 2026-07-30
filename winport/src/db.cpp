// db.cpp — port of DatabaseHelper.kt. Same table names, column names, SQL
// semantics and rounding as the Android app; foods.db files are
// interchangeable between the two.
#include "app.h"
#include "sqlite3.h"
#include <algorithm>

static std::wstring dataDbPath() {
    return appDataDirectory() + L"\\" + L"foods.db";
}

static std::wstring assetDbPath() {
    return exeDirectory() + L"\\assets\\foods.db";
}

std::wstring Db::databasePath() const { return dataDbPath(); }

// ---------------------------------------------------------------------------
// Bootstrap + schema guards
// ---------------------------------------------------------------------------
bool Db::open() {
    std::wstring dbFile = dataDbPath();
    if (!fileExists(dbFile)) {
        // copyDatabaseFromAssets
        CopyFileW(assetDbPath().c_str(), dbFile.c_str(), FALSE);
    }
    std::string utf8Path = wideToUtf8(dbFile);
    if (sqlite3_open_v2(utf8Path.c_str(), &handle,
                        SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE, nullptr) != SQLITE_OK) {
        handle = nullptr;
        return false;
    }
    ensureWeightTableExists();
    ensureFoodsTableHasNotes();
    return true;
}

void Db::close() {
    if (handle) { sqlite3_close(handle); handle = nullptr; }
}

static bool execSimple(sqlite3* db, const char* sql) {
    char* err = nullptr;
    int rc = sqlite3_exec(db, sql, nullptr, nullptr, &err);
    if (err) sqlite3_free(err);
    return rc == SQLITE_OK;
}

static bool tableHasColumn(sqlite3* db, const char* table, const char* column) {
    std::string sql = std::string("PRAGMA table_info(") + table + ")";
    sqlite3_stmt* stmt = nullptr;
    bool found = false;
    if (sqlite3_prepare_v2(db, sql.c_str(), -1, &stmt, nullptr) == SQLITE_OK) {
        while (sqlite3_step(stmt) == SQLITE_ROW) {
            const char* name = (const char*)sqlite3_column_text(stmt, 1);
            if (name && _stricmp(name, column) == 0) { found = true; break; }
        }
    }
    sqlite3_finalize(stmt);
    return found;
}

void Db::ensureWeightTableExists() {
    execSimple(handle,
        "CREATE TABLE IF NOT EXISTS Weight ("
        " WeightId INTEGER PRIMARY KEY AUTOINCREMENT,"
        " DateWeight TEXT,"
        " Weight REAL,"
        " Comments TEXT)");
    if (!tableHasColumn(handle, "Weight", "DateWeight"))
        execSimple(handle, "ALTER TABLE Weight ADD COLUMN DateWeight TEXT");
    if (!tableHasColumn(handle, "Weight", "Comments"))
        execSimple(handle, "ALTER TABLE Weight ADD COLUMN Comments TEXT");
}

void Db::ensureFoodsTableHasNotes() {
    if (!tableHasColumn(handle, "Foods", "notes"))
        execSimple(handle, "ALTER TABLE Foods ADD COLUMN notes TEXT NOT NULL DEFAULT ''");
    execSimple(handle, "UPDATE Foods SET notes = '' WHERE notes IS NULL");
}

// ---------------------------------------------------------------------------
// Row mappers
// ---------------------------------------------------------------------------
static std::string colText(sqlite3_stmt* s, int i) {
    const char* t = (const char*)sqlite3_column_text(s, i);
    return t ? t : "";
}

static int columnIndex(sqlite3_stmt* s, const char* name) {
    int n = sqlite3_column_count(s);
    for (int i = 0; i < n; i++) {
        if (_stricmp(sqlite3_column_name(s, i), name) == 0) return i;
    }
    return -1;
}

static void readNutrients(sqlite3_stmt* s, Nutrients& n, int firstIdxHint = -1) {
    for (int i = 0; i < NUTRIENT_COUNT; i++) {
        int idx = columnIndex(s, NUTRIENT_COLUMNS[i]);
        n[i] = idx >= 0 ? sqlite3_column_double(s, idx) : 0.0;
    }
    (void)firstIdxHint;
}

static Food foodFromRow(sqlite3_stmt* s) {
    Food f;
    f.foodId = sqlite3_column_int(s, columnIndex(s, "FoodId"));
    f.foodDescription = colText(s, columnIndex(s, "FoodDescription"));
    readNutrients(s, f.n);
    int notesIdx = columnIndex(s, "notes");
    f.notes = notesIdx >= 0 ? colText(s, notesIdx) : "";
    return f;
}

static EatenFood eatenFromRow(sqlite3_stmt* s) {
    EatenFood e;
    e.eatenId = sqlite3_column_int(s, columnIndex(s, "EatenId"));
    e.dateEaten = colText(s, columnIndex(s, "DateEaten"));
    e.timeEaten = colText(s, columnIndex(s, "TimeEaten"));
    e.eatenTs = sqlite3_column_int(s, columnIndex(s, "EatenTs"));
    e.amountEaten = sqlite3_column_double(s, columnIndex(s, "AmountEaten"));
    e.foodDescription = colText(s, columnIndex(s, "FoodDescription"));
    readNutrients(s, e.n);
    return e;
}

static RecipeItem recipeFromRow(sqlite3_stmt* s) {
    RecipeItem r;
    r.recipeId = sqlite3_column_int(s, columnIndex(s, "RecipeId"));
    r.foodId = sqlite3_column_int(s, columnIndex(s, "FoodId"));
    r.copyFg = sqlite3_column_int(s, columnIndex(s, "CopyFg"));
    r.amount = sqlite3_column_double(s, columnIndex(s, "Amount"));
    r.foodDescription = colText(s, columnIndex(s, "FoodDescription"));
    readNutrients(s, r.n);
    return r;
}

// ---------------------------------------------------------------------------
// Insert / update helpers
// ---------------------------------------------------------------------------
static int calculateEatenTimestampMinutes(long long dateTimeMillis) {
    long long eatenTimestampSeconds = dateTimeMillis / 1000;
    return (int)((eatenTimestampSeconds - REFERENCE_TIMESTAMP_SECONDS) / 60);
}

// Build "INSERT INTO t (c1,c2,...) VALUES (?,?,...)"
static std::string buildInsertSql(const char* table, const std::vector<std::string>& cols) {
    std::string sql = std::string("INSERT INTO ") + table + " (";
    for (size_t i = 0; i < cols.size(); i++) { if (i) sql += ","; sql += cols[i]; }
    sql += ") VALUES (";
    for (size_t i = 0; i < cols.size(); i++) { if (i) sql += ","; sql += "?"; }
    sql += ")";
    return sql;
}

static std::string buildUpdateSql(const char* table, const std::vector<std::string>& cols, const char* where) {
    std::string sql = std::string("UPDATE ") + table + " SET ";
    for (size_t i = 0; i < cols.size(); i++) { if (i) sql += ","; sql += cols[i] + "=?"; }
    sql += " WHERE ";
    sql += where;
    return sql;
}

struct Binder {
    sqlite3_stmt* stmt;
    int idx = 1;
    void text(const std::string& v) { sqlite3_bind_text(stmt, idx++, v.c_str(), -1, SQLITE_TRANSIENT); }
    void real(double v) { sqlite3_bind_double(stmt, idx++, v); }
    void integer(int v) { sqlite3_bind_int(stmt, idx++, v); }
};

// Food nutrient columns, rounded to 2 dp like ContentValues.putFoodNutrients.
static std::vector<std::string> foodColumns() {
    std::vector<std::string> cols = {"FoodDescription", "notes"};
    for (int i = 0; i < NUTRIENT_COUNT; i++) cols.push_back(NUTRIENT_COLUMNS[i]);
    return cols;
}

static void bindFoodValues(Binder& b, const Food& food) {
    b.text(food.foodDescription);
    b.text(food.notes);
    for (int i = 0; i < NUTRIENT_COUNT; i++) b.real(roundTo2dp(food.n[i]));
}

// ---------------------------------------------------------------------------
// Weight CRUD
// ---------------------------------------------------------------------------
bool Db::insertWeight(const std::string& dateWeight, double weight, const std::string& comments) {
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(handle,
            "INSERT INTO Weight (DateWeight, Weight, Comments) VALUES (?,?,?)",
            -1, &stmt, nullptr) != SQLITE_OK) return false;
    Binder b{stmt};
    b.text(dateWeight); b.real(weight); b.text(comments);
    bool ok = sqlite3_step(stmt) == SQLITE_DONE;
    sqlite3_finalize(stmt);
    return ok;
}

std::vector<WeightEntry> Db::readWeights() {
    std::vector<WeightEntry> results;
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(handle,
            "SELECT WeightId, DateWeight, Weight, Comments FROM Weight ORDER BY WeightId DESC",
            -1, &stmt, nullptr) == SQLITE_OK) {
        while (sqlite3_step(stmt) == SQLITE_ROW) {
            WeightEntry w;
            w.weightId = sqlite3_column_int(stmt, 0);
            w.dateWeight = colText(stmt, 1);
            w.weight = sqlite3_column_double(stmt, 2);
            w.comments = colText(stmt, 3);
            results.push_back(std::move(w));
        }
    }
    sqlite3_finalize(stmt);
    // Kotlin: sortedWith(compareByDescending{parse(date)}.thenByDescending{id})
    std::stable_sort(results.begin(), results.end(), [](const WeightEntry& a, const WeightEntry& b) {
        long long ta = parseDMMMYY(a.dateWeight).value_or(0);
        long long tb = parseDMMMYY(b.dateWeight).value_or(0);
        if (ta != tb) return ta > tb;
        return a.weightId > b.weightId;
    });
    return results;
}

bool Db::updateWeight(int weightId, const std::string& dateWeight, double weight, const std::string& comments) {
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(handle,
            "UPDATE Weight SET DateWeight=?, Weight=?, Comments=? WHERE WeightId=?",
            -1, &stmt, nullptr) != SQLITE_OK) return false;
    Binder b{stmt};
    b.text(dateWeight); b.real(weight); b.text(comments); b.integer(weightId);
    bool ok = sqlite3_step(stmt) == SQLITE_DONE && sqlite3_changes(handle) > 0;
    sqlite3_finalize(stmt);
    return ok;
}

bool Db::deleteWeight(int weightId) {
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(handle, "DELETE FROM Weight WHERE WeightId=?", -1, &stmt, nullptr) != SQLITE_OK)
        return false;
    sqlite3_bind_int(stmt, 1, weightId);
    bool ok = sqlite3_step(stmt) == SQLITE_DONE && sqlite3_changes(handle) > 0;
    sqlite3_finalize(stmt);
    return ok;
}

// ---------------------------------------------------------------------------
// Eaten CRUD
// ---------------------------------------------------------------------------
bool Db::logEatenFood(const Food& food, double amount, long long dateTimeMillis) {
    std::vector<std::string> cols = {"DateEaten", "TimeEaten", "EatenTs", "AmountEaten", "FoodDescription"};
    for (int i = 0; i < NUTRIENT_COUNT; i++) cols.push_back(NUTRIENT_COLUMNS[i]);
    std::string sql = buildInsertSql("Eaten", cols);
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(handle, sql.c_str(), -1, &stmt, nullptr) != SQLITE_OK) return false;
    Binder b{stmt};
    b.text(formatDMMMYY(dateTimeMillis));
    b.text(formatHHMM(dateTimeMillis));
    b.integer(calculateEatenTimestampMinutes(dateTimeMillis));
    b.real(amount);
    b.text(food.foodDescription);
    double scale = amount / 100.0;
    for (int i = 0; i < NUTRIENT_COUNT; i++) b.real(roundTo2dp(food.n[i] * scale));
    bool ok = sqlite3_step(stmt) == SQLITE_DONE;
    sqlite3_finalize(stmt);
    return ok;
}

bool Db::updateEatenFood(const EatenFood& eatenFood, double newAmount, long long newDateTimeMillis) {
    std::vector<std::string> cols = {"DateEaten", "TimeEaten", "EatenTs", "AmountEaten"};
    for (int i = 0; i < NUTRIENT_COUNT; i++) cols.push_back(NUTRIENT_COLUMNS[i]);
    std::string sql = buildUpdateSql("Eaten", cols, "EatenId = ?");
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(handle, sql.c_str(), -1, &stmt, nullptr) != SQLITE_OK) return false;
    double scale = newAmount / eatenFood.amountEaten;
    Binder b{stmt};
    b.text(formatDMMMYY(newDateTimeMillis));
    b.text(formatHHMM(newDateTimeMillis));
    b.integer(calculateEatenTimestampMinutes(newDateTimeMillis));
    b.real(newAmount);
    for (int i = 0; i < NUTRIENT_COUNT; i++) b.real(roundTo2dp(eatenFood.n[i] * scale));
    b.integer(eatenFood.eatenId);
    bool ok = sqlite3_step(stmt) == SQLITE_DONE && sqlite3_changes(handle) > 0;
    sqlite3_finalize(stmt);
    return ok;
}

bool Db::deleteEatenFood(int eatenId) {
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(handle, "DELETE FROM Eaten WHERE EatenId = ?", -1, &stmt, nullptr) != SQLITE_OK)
        return false;
    sqlite3_bind_int(stmt, 1, eatenId);
    bool ok = sqlite3_step(stmt) == SQLITE_DONE && sqlite3_changes(handle) > 0;
    sqlite3_finalize(stmt);
    return ok;
}

std::vector<EatenFood> Db::readEatenFoods() {
    std::vector<EatenFood> out;
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(handle, "SELECT * FROM Eaten ORDER BY EatenTs DESC", -1, &stmt, nullptr) == SQLITE_OK) {
        while (sqlite3_step(stmt) == SQLITE_ROW) out.push_back(eatenFromRow(stmt));
    }
    sqlite3_finalize(stmt);
    return out;
}

// ---------------------------------------------------------------------------
// Foods CRUD
// ---------------------------------------------------------------------------
bool Db::deleteFood(int foodId) {
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(handle, "DELETE FROM Foods WHERE FoodId = ?", -1, &stmt, nullptr) != SQLITE_OK)
        return false;
    sqlite3_bind_int(stmt, 1, foodId);
    bool ok = sqlite3_step(stmt) == SQLITE_DONE && sqlite3_changes(handle) > 0;
    sqlite3_finalize(stmt);
    return ok;
}

bool Db::updateFood(const Food& food) {
    std::string sql = buildUpdateSql("Foods", foodColumns(), "FoodId = ?");
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(handle, sql.c_str(), -1, &stmt, nullptr) != SQLITE_OK) return false;
    Binder b{stmt};
    bindFoodValues(b, food);
    b.integer(food.foodId);
    bool ok = sqlite3_step(stmt) == SQLITE_DONE && sqlite3_changes(handle) > 0;
    sqlite3_finalize(stmt);
    return ok;
}

bool Db::insertFood(const Food& food) {
    return insertFoodReturningId(food).has_value();
}

std::optional<int> Db::insertFoodReturningId(const Food& food) {
    std::string sql = buildInsertSql("Foods", foodColumns());
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(handle, sql.c_str(), -1, &stmt, nullptr) != SQLITE_OK) return std::nullopt;
    Binder b{stmt};
    bindFoodValues(b, food);
    bool ok = sqlite3_step(stmt) == SQLITE_DONE;
    sqlite3_finalize(stmt);
    if (!ok) return std::nullopt;
    return (int)sqlite3_last_insert_rowid(handle);
}

std::optional<Food> Db::getFoodById(int foodId) {
    sqlite3_stmt* stmt = nullptr;
    std::optional<Food> out;
    if (sqlite3_prepare_v2(handle, "SELECT * FROM Foods WHERE FoodId = ?", -1, &stmt, nullptr) == SQLITE_OK) {
        sqlite3_bind_int(stmt, 1, foodId);
        if (sqlite3_step(stmt) == SQLITE_ROW) out = foodFromRow(stmt);
    }
    sqlite3_finalize(stmt);
    return out;
}

std::vector<Food> Db::readFoodsFromDatabase() {
    std::vector<Food> out;
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(handle, "SELECT * FROM Foods", -1, &stmt, nullptr) == SQLITE_OK) {
        while (sqlite3_step(stmt) == SQLITE_ROW) out.push_back(foodFromRow(stmt));
    }
    sqlite3_finalize(stmt);
    return out;
}

std::vector<Food> Db::readFoodsSortedByIdDesc() {
    auto foods = readFoodsFromDatabase();
    std::sort(foods.begin(), foods.end(), [](const Food& a, const Food& b) {
        return a.foodId > b.foodId;
    });
    return foods;
}

std::vector<Food> Db::searchFoods(const std::string& query) {
    std::vector<Food> out;
    std::vector<std::string> terms;
    for (auto& raw : splitString(query, '|')) {
        std::string t = trim(raw);
        if (!t.empty()) terms.push_back(t);
    }
    std::string sql;
    std::vector<std::string> args;
    if (terms.size() > 1) {
        sql = "SELECT * FROM Foods WHERE ";
        for (size_t i = 0; i < terms.size(); i++) {
            if (i) sql += " AND ";
            sql += "FoodDescription LIKE ?";
            args.push_back("%" + terms[i] + "%");
        }
    } else {
        std::string single = terms.empty() ? query : terms[0];
        sql = "SELECT * FROM Foods WHERE FoodDescription LIKE ?";
        args.push_back("%" + single + "%");
    }
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(handle, sql.c_str(), -1, &stmt, nullptr) == SQLITE_OK) {
        for (size_t i = 0; i < args.size(); i++)
            sqlite3_bind_text(stmt, (int)i + 1, args[i].c_str(), -1, SQLITE_TRANSIENT);
        while (sqlite3_step(stmt) == SQLITE_ROW) out.push_back(foodFromRow(stmt));
    }
    sqlite3_finalize(stmt);
    return out;
}

// ---------------------------------------------------------------------------
// Recipe CRUD
// ---------------------------------------------------------------------------
static std::vector<std::string> recipeColumns() {
    std::vector<std::string> cols = {"FoodId", "CopyFg", "Amount", "FoodDescription"};
    for (int i = 0; i < NUTRIENT_COUNT; i++) cols.push_back(NUTRIENT_COLUMNS[i]);
    return cols;
}

bool Db::insertRecipeFromFood(const Food& food, double amount, int foodId, int copyFlag) {
    std::string sql = buildInsertSql("Recipe", recipeColumns());
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(handle, sql.c_str(), -1, &stmt, nullptr) != SQLITE_OK) return false;
    Binder b{stmt};
    b.integer(foodId);
    b.integer(copyFlag);
    b.real(amount);
    b.text(food.foodDescription);
    double scale = amount / 100.0;
    for (int i = 0; i < NUTRIENT_COUNT; i++) b.real(roundTo2dp(food.n[i] * scale));
    bool ok = sqlite3_step(stmt) == SQLITE_DONE;
    sqlite3_finalize(stmt);
    return ok;
}

static bool insertRecipeRow(sqlite3* handle, const RecipeItem& r) {
    std::vector<std::string> cols = {"FoodId", "CopyFg", "Amount", "FoodDescription"};
    for (int i = 0; i < NUTRIENT_COUNT; i++) cols.push_back(NUTRIENT_COLUMNS[i]);
    std::string sql = buildInsertSql("Recipe", cols);
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(handle, sql.c_str(), -1, &stmt, nullptr) != SQLITE_OK) return false;
    Binder b{stmt};
    b.integer(r.foodId);
    b.integer(r.copyFg);
    b.real(r.amount);
    b.text(r.foodDescription);
    for (int i = 0; i < NUTRIENT_COUNT; i++) b.real(roundTo2dp(r.n[i]));
    bool ok = sqlite3_step(stmt) == SQLITE_DONE;
    sqlite3_finalize(stmt);
    return ok;
}

bool Db::deleteRecipesWithFoodIdZero() {
    return execSimple(handle, "DELETE FROM Recipe WHERE FoodId = 0");
}

bool Db::updateRecipeFoodIdForTemporaryRecords(int newFoodId) {
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(handle, "UPDATE Recipe SET FoodId = ? WHERE FoodId = 0", -1, &stmt, nullptr) != SQLITE_OK)
        return false;
    sqlite3_bind_int(stmt, 1, newFoodId);
    bool ok = sqlite3_step(stmt) == SQLITE_DONE && sqlite3_changes(handle) > 0;
    sqlite3_finalize(stmt);
    return ok;
}

bool Db::copyRecipesForFood(int foodId) {
    execSimple(handle, "BEGIN TRANSACTION");
    bool ok = true;
    deleteCopiedRecipes(foodId);
    std::vector<RecipeItem> originals;
    {
        sqlite3_stmt* stmt = nullptr;
        if (sqlite3_prepare_v2(handle, "SELECT * FROM Recipe WHERE FoodId = ? AND CopyFg != 1",
                               -1, &stmt, nullptr) == SQLITE_OK) {
            sqlite3_bind_int(stmt, 1, foodId);
            while (sqlite3_step(stmt) == SQLITE_ROW) originals.push_back(recipeFromRow(stmt));
        } else ok = false;
        sqlite3_finalize(stmt);
    }
    for (auto r : originals) {
        r.copyFg = 1;
        if (!insertRecipeRow(handle, r)) { ok = false; break; }
    }
    execSimple(handle, ok ? "COMMIT" : "ROLLBACK");
    return ok;
}

bool Db::duplicateRecipesToFoodIdZero(int foodId) {
    execSimple(handle, "BEGIN TRANSACTION");
    bool ok = execSimple(handle, "DELETE FROM Recipe WHERE FoodId = 0 AND CopyFg = 0");
    std::vector<RecipeItem> originals;
    {
        sqlite3_stmt* stmt = nullptr;
        if (sqlite3_prepare_v2(handle, "SELECT * FROM Recipe WHERE FoodId = ?",
                               -1, &stmt, nullptr) == SQLITE_OK) {
            sqlite3_bind_int(stmt, 1, foodId);
            while (sqlite3_step(stmt) == SQLITE_ROW) originals.push_back(recipeFromRow(stmt));
        } else ok = false;
        sqlite3_finalize(stmt);
    }
    for (auto r : originals) {
        r.foodId = 0; r.copyFg = 0;
        if (!insertRecipeRow(handle, r)) { ok = false; break; }
    }
    execSimple(handle, ok ? "COMMIT" : "ROLLBACK");
    return ok;
}

bool Db::replaceOriginalRecipesWithCopies(int foodId) {
    execSimple(handle, "BEGIN TRANSACTION");
    bool ok = true;
    {
        sqlite3_stmt* stmt = nullptr;
        if (sqlite3_prepare_v2(handle, "DELETE FROM Recipe WHERE FoodId = ? AND CopyFg = 0",
                               -1, &stmt, nullptr) == SQLITE_OK) {
            sqlite3_bind_int(stmt, 1, foodId);
            ok = sqlite3_step(stmt) == SQLITE_DONE;
        } else ok = false;
        sqlite3_finalize(stmt);
    }
    if (ok) {
        sqlite3_stmt* stmt = nullptr;
        if (sqlite3_prepare_v2(handle, "UPDATE Recipe SET CopyFg = 0 WHERE FoodId = ? AND CopyFg = 1",
                               -1, &stmt, nullptr) == SQLITE_OK) {
            sqlite3_bind_int(stmt, 1, foodId);
            ok = sqlite3_step(stmt) == SQLITE_DONE;
        } else ok = false;
        sqlite3_finalize(stmt);
    }
    execSimple(handle, ok ? "COMMIT" : "ROLLBACK");
    return ok;
}

bool Db::deleteRecipesByFoodId(int foodId) {
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(handle, "DELETE FROM Recipe WHERE FoodId = ?", -1, &stmt, nullptr) != SQLITE_OK)
        return false;
    sqlite3_bind_int(stmt, 1, foodId);
    bool ok = sqlite3_step(stmt) == SQLITE_DONE;
    sqlite3_finalize(stmt);
    return ok;
}

bool Db::deleteCopiedRecipes(int foodId) {
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(handle, "DELETE FROM Recipe WHERE FoodId = ? AND CopyFg = 1", -1, &stmt, nullptr) != SQLITE_OK)
        return false;
    sqlite3_bind_int(stmt, 1, foodId);
    bool ok = sqlite3_step(stmt) == SQLITE_DONE;
    sqlite3_finalize(stmt);
    return ok;
}

bool Db::deleteAllCopiedRecipes() {
    return execSimple(handle, "DELETE FROM Recipe WHERE CopyFg = 1");
}

bool Db::updateRecipe(const RecipeItem& recipe) {
    std::string sql = buildUpdateSql("Recipe", recipeColumns(), "RecipeId = ?");
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(handle, sql.c_str(), -1, &stmt, nullptr) != SQLITE_OK) return false;
    Binder b{stmt};
    b.integer(recipe.foodId);
    b.integer(recipe.copyFg);
    b.real(recipe.amount);
    b.text(recipe.foodDescription);
    for (int i = 0; i < NUTRIENT_COUNT; i++) b.real(roundTo2dp(recipe.n[i]));
    b.integer(recipe.recipeId);
    bool ok = sqlite3_step(stmt) == SQLITE_DONE && sqlite3_changes(handle) > 0;
    sqlite3_finalize(stmt);
    return ok;
}

bool Db::deleteRecipe(int recipeId) {
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(handle, "DELETE FROM Recipe WHERE RecipeId = ?", -1, &stmt, nullptr) != SQLITE_OK)
        return false;
    sqlite3_bind_int(stmt, 1, recipeId);
    bool ok = sqlite3_step(stmt) == SQLITE_DONE && sqlite3_changes(handle) > 0;
    sqlite3_finalize(stmt);
    return ok;
}

std::vector<RecipeItem> Db::readRecipes() {
    std::vector<RecipeItem> out;
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(handle, "SELECT * FROM Recipe WHERE FoodId = 0 ORDER BY RecipeId DESC",
                           -1, &stmt, nullptr) == SQLITE_OK) {
        while (sqlite3_step(stmt) == SQLITE_ROW) out.push_back(recipeFromRow(stmt));
    }
    sqlite3_finalize(stmt);
    return out;
}

std::vector<RecipeItem> Db::readCopiedRecipes(int foodId) {
    std::vector<RecipeItem> out;
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(handle, "SELECT * FROM Recipe WHERE CopyFg = 1 AND FoodId = ? ORDER BY RecipeId DESC",
                           -1, &stmt, nullptr) == SQLITE_OK) {
        sqlite3_bind_int(stmt, 1, foodId);
        while (sqlite3_step(stmt) == SQLITE_ROW) out.push_back(recipeFromRow(stmt));
    }
    sqlite3_finalize(stmt);
    return out;
}

// ---------------------------------------------------------------------------
// Import / export — close, copy the file, reopen (+ schema guards on import)
// ---------------------------------------------------------------------------
bool Db::exportDatabaseTo(const std::wstring& destPath) {
    std::wstring src = dataDbPath();
    close();
    bool ok = CopyFileW(src.c_str(), destPath.c_str(), FALSE) != 0;
    open();
    return ok;
}

bool Db::replaceDatabaseFrom(const std::wstring& srcPath) {
    std::wstring dst = dataDbPath();
    std::wstring tmp = dst + L".import";
    if (!CopyFileW(srcPath.c_str(), tmp.c_str(), FALSE)) return false;
    close();
    bool ok = CopyFileW(tmp.c_str(), dst.c_str(), FALSE) != 0;
    DeleteFileW(tmp.c_str());
    open(); // re-runs ensureWeightTableExists + ensureFoodsTableHasNotes
    return ok;
}
