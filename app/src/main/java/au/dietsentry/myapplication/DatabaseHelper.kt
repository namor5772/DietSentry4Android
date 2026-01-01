package au.dietsentry.myapplication

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

private const val REFERENCE_TIMESTAMP_SECONDS = 1672491600L // Adjusted by 460 minutes

class DatabaseHelper private constructor(context: Context, private val databaseName: String) {

    private val databaseFile: File = context.getDatabasePath(databaseName)
    private var db: SQLiteDatabase

    init {
        copyDatabaseFromAssets(context)
        db = SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READWRITE)
        ensureWeightTableExists()
    }

    private fun copyDatabaseFromAssets(context: Context) {
        if (databaseFile.exists()) return
        databaseFile.parentFile?.mkdirs()
        try {
            context.assets.open(databaseName).use { inputStream ->
                FileOutputStream(databaseFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error copying database", e)
        }
    }

    private fun ensureWeightTableExists() {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS Weight (
                WeightId INTEGER PRIMARY KEY AUTOINCREMENT,
                DateWeight TEXT,
                Weight REAL
            )
            """.trimIndent()
        )
        val cursor = db.rawQuery("PRAGMA table_info(Weight)", null)
        var hasDateWeight = false
        cursor.use {
            val nameIndex = it.getColumnIndexOrThrow("name")
            while (it.moveToNext()) {
                if (it.getString(nameIndex) == "DateWeight") {
                    hasDateWeight = true
                    break
                }
            }
        }
        if (!hasDateWeight) {
            db.execSQL("ALTER TABLE Weight ADD COLUMN DateWeight TEXT")
        }
    }

    @Synchronized
    fun replaceDatabaseFromStream(inputStream: InputStream): Boolean {
        val parentDir = databaseFile.parentFile ?: return false
        val tempFile = File(parentDir, "${databaseFile.name}.import")
        return try {
            inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (db.isOpen) {
                db.close()
            }
            tempFile.copyTo(databaseFile, overwrite = true)
            db = SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READWRITE)
            ensureWeightTableExists()
            true
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error importing database", e)
            if (!db.isOpen) {
                runCatching {
                    db = SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READWRITE)
                    ensureWeightTableExists()
                }
            }
            false
        } finally {
            tempFile.delete()
        }
    }
    
    private fun Double.roundToTwoDecimalPlaces(): Double = (this * 100).roundToInt() / 100.0

    private fun ContentValues.putFoodNutrients(food: Food) {
        put("FoodDescription", food.foodDescription)
        put("Energy", food.energy.roundToTwoDecimalPlaces())
        put("Protein", food.protein.roundToTwoDecimalPlaces())
        put("FatTotal", food.fatTotal.roundToTwoDecimalPlaces())
        put("SaturatedFat", food.saturatedFat.roundToTwoDecimalPlaces())
        put("TransFat", food.transFat.roundToTwoDecimalPlaces())
        put("PolyunsaturatedFat", food.polyunsaturatedFat.roundToTwoDecimalPlaces())
        put("MonounsaturatedFat", food.monounsaturatedFat.roundToTwoDecimalPlaces())
        put("Carbohydrate", food.carbohydrate.roundToTwoDecimalPlaces())
        put("Sugars", food.sugars.roundToTwoDecimalPlaces())
        put("DietaryFibre", food.dietaryFibre.roundToTwoDecimalPlaces())
        put("SodiumNa", food.sodium.roundToTwoDecimalPlaces())
        put("CalciumCa", food.calciumCa.roundToTwoDecimalPlaces())
        put("PotassiumK", food.potassiumK.roundToTwoDecimalPlaces())
        put("ThiaminB1", food.thiaminB1.roundToTwoDecimalPlaces())
        put("RiboflavinB2", food.riboflavinB2.roundToTwoDecimalPlaces())
        put("NiacinB3", food.niacinB3.roundToTwoDecimalPlaces())
        put("Folate", food.folate.roundToTwoDecimalPlaces())
        put("IronFe", food.ironFe.roundToTwoDecimalPlaces())
        put("MagnesiumMg", food.magnesiumMg.roundToTwoDecimalPlaces())
        put("VitaminC", food.vitaminC.roundToTwoDecimalPlaces())
        put("Caffeine", food.caffeine.roundToTwoDecimalPlaces())
        put("Cholesterol", food.cholesterol.roundToTwoDecimalPlaces())
        put("Alcohol", food.alcohol.roundToTwoDecimalPlaces())
    }

    private fun ContentValues.putScaledFoodNutrients(food: Food, scale: Double) {
        put("Energy", (food.energy * scale).roundToTwoDecimalPlaces())
        put("Protein", (food.protein * scale).roundToTwoDecimalPlaces())
        put("FatTotal", (food.fatTotal * scale).roundToTwoDecimalPlaces())
        put("SaturatedFat", (food.saturatedFat * scale).roundToTwoDecimalPlaces())
        put("TransFat", (food.transFat * scale).roundToTwoDecimalPlaces())
        put("PolyunsaturatedFat", (food.polyunsaturatedFat * scale).roundToTwoDecimalPlaces())
        put("MonounsaturatedFat", (food.monounsaturatedFat * scale).roundToTwoDecimalPlaces())
        put("Carbohydrate", (food.carbohydrate * scale).roundToTwoDecimalPlaces())
        put("Sugars", (food.sugars * scale).roundToTwoDecimalPlaces())
        put("DietaryFibre", (food.dietaryFibre * scale).roundToTwoDecimalPlaces())
        put("SodiumNa", (food.sodium * scale).roundToTwoDecimalPlaces())
        put("CalciumCa", (food.calciumCa * scale).roundToTwoDecimalPlaces())
        put("PotassiumK", (food.potassiumK * scale).roundToTwoDecimalPlaces())
        put("ThiaminB1", (food.thiaminB1 * scale).roundToTwoDecimalPlaces())
        put("RiboflavinB2", (food.riboflavinB2 * scale).roundToTwoDecimalPlaces())
        put("NiacinB3", (food.niacinB3 * scale).roundToTwoDecimalPlaces())
        put("Folate", (food.folate * scale).roundToTwoDecimalPlaces())
        put("IronFe", (food.ironFe * scale).roundToTwoDecimalPlaces())
        put("MagnesiumMg", (food.magnesiumMg * scale).roundToTwoDecimalPlaces())
        put("VitaminC", (food.vitaminC * scale).roundToTwoDecimalPlaces())
        put("Caffeine", (food.caffeine * scale).roundToTwoDecimalPlaces())
        put("Cholesterol", (food.cholesterol * scale).roundToTwoDecimalPlaces())
        put("Alcohol", (food.alcohol * scale).roundToTwoDecimalPlaces())
    }

    private fun ContentValues.putScaledEatenNutrients(eatenFood: EatenFood, scale: Double) {
        put("Energy", (eatenFood.energy * scale).roundToTwoDecimalPlaces())
        put("Protein", (eatenFood.protein * scale).roundToTwoDecimalPlaces())
        put("FatTotal", (eatenFood.fatTotal * scale).roundToTwoDecimalPlaces())
        put("SaturatedFat", (eatenFood.saturatedFat * scale).roundToTwoDecimalPlaces())
        put("TransFat", (eatenFood.transFat * scale).roundToTwoDecimalPlaces())
        put("PolyunsaturatedFat", (eatenFood.polyunsaturatedFat * scale).roundToTwoDecimalPlaces())
        put("MonounsaturatedFat", (eatenFood.monounsaturatedFat * scale).roundToTwoDecimalPlaces())
        put("Carbohydrate", (eatenFood.carbohydrate * scale).roundToTwoDecimalPlaces())
        put("Sugars", (eatenFood.sugars * scale).roundToTwoDecimalPlaces())
        put("DietaryFibre", (eatenFood.dietaryFibre * scale).roundToTwoDecimalPlaces())
        put("SodiumNa", (eatenFood.sodiumNa * scale).roundToTwoDecimalPlaces())
        put("CalciumCa", (eatenFood.calciumCa * scale).roundToTwoDecimalPlaces())
        put("PotassiumK", (eatenFood.potassiumK * scale).roundToTwoDecimalPlaces())
        put("ThiaminB1", (eatenFood.thiaminB1 * scale).roundToTwoDecimalPlaces())
        put("RiboflavinB2", (eatenFood.riboflavinB2 * scale).roundToTwoDecimalPlaces())
        put("NiacinB3", (eatenFood.niacinB3 * scale).roundToTwoDecimalPlaces())
        put("Folate", (eatenFood.folate * scale).roundToTwoDecimalPlaces())
        put("IronFe", (eatenFood.ironFe * scale).roundToTwoDecimalPlaces())
        put("MagnesiumMg", (eatenFood.magnesiumMg * scale).roundToTwoDecimalPlaces())
        put("VitaminC", (eatenFood.vitaminC * scale).roundToTwoDecimalPlaces())
        put("Caffeine", (eatenFood.caffeine * scale).roundToTwoDecimalPlaces())
        put("Cholesterol", (eatenFood.cholesterol * scale).roundToTwoDecimalPlaces())
        put("Alcohol", (eatenFood.alcohol * scale).roundToTwoDecimalPlaces())
    }

    private fun ContentValues.putRecipeFields(recipe: RecipeItem) {
        put("FoodId", recipe.foodId)
        put("CopyFg", recipe.copyFg)
        put("Amount", recipe.amount)
        put("FoodDescription", recipe.foodDescription)
        put("Energy", recipe.energy.roundToTwoDecimalPlaces())
        put("Protein", recipe.protein.roundToTwoDecimalPlaces())
        put("FatTotal", recipe.fatTotal.roundToTwoDecimalPlaces())
        put("SaturatedFat", recipe.saturatedFat.roundToTwoDecimalPlaces())
        put("TransFat", recipe.transFat.roundToTwoDecimalPlaces())
        put("PolyunsaturatedFat", recipe.polyunsaturatedFat.roundToTwoDecimalPlaces())
        put("MonounsaturatedFat", recipe.monounsaturatedFat.roundToTwoDecimalPlaces())
        put("Carbohydrate", recipe.carbohydrate.roundToTwoDecimalPlaces())
        put("Sugars", recipe.sugars.roundToTwoDecimalPlaces())
        put("DietaryFibre", recipe.dietaryFibre.roundToTwoDecimalPlaces())
        put("SodiumNa", recipe.sodiumNa.roundToTwoDecimalPlaces())
        put("CalciumCa", recipe.calciumCa.roundToTwoDecimalPlaces())
        put("PotassiumK", recipe.potassiumK.roundToTwoDecimalPlaces())
        put("ThiaminB1", recipe.thiaminB1.roundToTwoDecimalPlaces())
        put("RiboflavinB2", recipe.riboflavinB2.roundToTwoDecimalPlaces())
        put("NiacinB3", recipe.niacinB3.roundToTwoDecimalPlaces())
        put("Folate", recipe.folate.roundToTwoDecimalPlaces())
        put("IronFe", recipe.ironFe.roundToTwoDecimalPlaces())
        put("MagnesiumMg", recipe.magnesiumMg.roundToTwoDecimalPlaces())
        put("VitaminC", recipe.vitaminC.roundToTwoDecimalPlaces())
        put("Caffeine", recipe.caffeine.roundToTwoDecimalPlaces())
        put("Cholesterol", recipe.cholesterol.roundToTwoDecimalPlaces())
        put("Alcohol", recipe.alcohol.roundToTwoDecimalPlaces())
    }

    private fun calculateEatenTimestampMinutes(dateTime: Long): Int {
        val eatenTimestampSeconds = dateTime / 1000
        return ((eatenTimestampSeconds - REFERENCE_TIMESTAMP_SECONDS) / 60).toInt()
    }

    fun insertRecipeFromFood(
        food: Food,
        amount: Float,
        foodId: Int = 0,
        copyFlag: Int = 0
    ): Boolean {
        return try {
            val values = ContentValues().apply {
                put("FoodId", foodId)
                put("CopyFg", copyFlag)
                put("Amount", amount)
                put("FoodDescription", food.foodDescription)
                val scale = amount / 100.0
                putScaledFoodNutrients(food, scale)
            }
            db.insert("Recipe", null, values) != -1L
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error inserting recipe item", e)
            false
        }
    }

    fun insertWeight(dateWeight: String, weight: Double): Boolean {
        return try {
            val values = ContentValues().apply {
                put("DateWeight", dateWeight)
                put("Weight", weight)
            }
            db.insert("Weight", null, values) != -1L
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error inserting weight", e)
            false
        }
    }

    fun readWeights(): List<WeightEntry> {
        val results = mutableListOf<WeightEntry>()
        val cursor = db.rawQuery(
            "SELECT WeightId, DateWeight, Weight FROM Weight ORDER BY WeightId DESC",
            null
        )
        cursor.use {
            val idIndex = it.getColumnIndexOrThrow("WeightId")
            val dateIndex = it.getColumnIndexOrThrow("DateWeight")
            val weightIndex = it.getColumnIndexOrThrow("Weight")
            while (it.moveToNext()) {
                results.add(
                    WeightEntry(
                        weightId = it.getInt(idIndex),
                        dateWeight = it.getString(dateIndex) ?: "",
                        weight = it.getDouble(weightIndex)
                    )
                )
            }
        }
        val dateFormat = SimpleDateFormat("d-MMM-yy", Locale.getDefault())
        return results.sortedWith(
            compareByDescending<WeightEntry> { entry ->
                runCatching { dateFormat.parse(entry.dateWeight)?.time ?: 0L }.getOrElse { 0L }
            }.thenByDescending { entry -> entry.weightId }
        )
    }

    fun updateWeight(weightId: Int, dateWeight: String, weight: Double): Boolean {
        return try {
            val values = ContentValues().apply {
                put("DateWeight", dateWeight)
                put("Weight", weight)
            }
            db.update("Weight", values, "WeightId=?", arrayOf(weightId.toString())) > 0
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error updating weight", e)
            false
        }
    }

    fun deleteWeight(weightId: Int): Boolean {
        return try {
            db.delete("Weight", "WeightId=?", arrayOf(weightId.toString())) > 0
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error deleting weight", e)
            false
        }
    }

    fun logEatenFood(food: Food, amount: Float, dateTime: Long): Boolean {
        return try {
            val values = ContentValues().apply {
                val date = Date(dateTime)
                put("DateEaten", SimpleDateFormat("d-MMM-yy", Locale.getDefault()).format(date))
                put("TimeEaten", SimpleDateFormat("HH:mm", Locale.getDefault()).format(date))

                // Correct EatenTs calculation
                put("EatenTs", calculateEatenTimestampMinutes(dateTime))

                put("AmountEaten", amount)
                put("FoodDescription", food.foodDescription)
                val scale = amount / 100.0
                putScaledFoodNutrients(food, scale)
            }
            db.insert("Eaten", null, values) != -1L
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error logging eaten food", e)
            false
        }
    }

    fun updateEatenFood(eatenFood: EatenFood, newAmount: Float, newDateTime: Long): Boolean {
        return try {
            val values = ContentValues()
            val scale = newAmount / eatenFood.amountEaten

            val date = Date(newDateTime)
            values.put("DateEaten", SimpleDateFormat("d-MMM-yy", Locale.getDefault()).format(date))
            values.put("TimeEaten", SimpleDateFormat("HH:mm", Locale.getDefault()).format(date))

            values.put("EatenTs", calculateEatenTimestampMinutes(newDateTime))

            values.put("AmountEaten", newAmount)

            values.putScaledEatenNutrients(eatenFood, scale)

            db.update("Eaten", values, "EatenId = ?", arrayOf(eatenFood.eatenId.toString())) > 0
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error updating eaten food", e)
            false
        }
    }

    fun deleteFood(foodId: Int): Boolean {
        return try {
            db.delete("Foods", "FoodId = ?", arrayOf(foodId.toString())) > 0
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error deleting food", e)
            false
        }
    }

    fun updateFood(food: Food): Boolean {
        return try {
            val values = ContentValues().apply {
                putFoodNutrients(food)
            }
            db.update("Foods", values, "FoodId = ?", arrayOf(food.foodId.toString())) > 0
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error updating food", e)
            false
        }
    }

    fun insertFood(food: Food): Boolean {
        return try {
            val values = ContentValues().apply { putFoodNutrients(food) }
            db.insert("Foods", null, values) != -1L
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error inserting food", e)
            false
        }
    }

    fun insertFoodReturningId(food: Food): Int? {
        return try {
            val values = ContentValues().apply { putFoodNutrients(food) }
            val rowId = db.insert("Foods", null, values)
            if (rowId == -1L) null else rowId.toInt()
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error inserting food", e)
            null
        }
    }

    @SuppressLint("Range")
    fun getFoodById(foodId: Int): Food? {
        return try {
            db.rawQuery("SELECT * FROM Foods WHERE FoodId = ?", arrayOf(foodId.toString())).use { cursor ->
                if (cursor.moveToFirst()) createFoodFromCursor(cursor) else null
            }
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error fetching food by id", e)
            null
        }
    }

    fun deleteEatenFood(eatenId: Int): Boolean {
        return try {
            db.delete("Eaten", "EatenId = ?", arrayOf(eatenId.toString())) > 0
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error deleting eaten food", e)
            false
        }
    }

    @SuppressLint("Range")
    fun readFoodsFromDatabase(): List<Food> {
        val foodList = mutableListOf<Food>()
        try {
            db.rawQuery("SELECT * FROM Foods", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        foodList.add(createFoodFromCursor(cursor))
                    } while (cursor.moveToNext())
                }
            }
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error reading foods from database", e)
        }
        return foodList
    }

    fun readFoodsSortedByIdDesc(): List<Food> {
        return readFoodsFromDatabase().sortedByDescending { it.foodId }
    }
    
    @SuppressLint("Range")
    fun readEatenFoods(): List<EatenFood> {
        val eatenFoodList = mutableListOf<EatenFood>()
        try {
            db.rawQuery("SELECT * FROM Eaten ORDER BY EatenTs DESC", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        eatenFoodList.add(createEatenFoodFromCursor(cursor))
                    } while (cursor.moveToNext())
                }
            }
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error reading eaten foods", e)
        }
        return eatenFoodList
    }

    @SuppressLint("Range")
    fun deleteRecipesWithFoodIdZero(): Boolean {
        return try {
            db.delete("Recipe", "FoodId = 0", null) >= 0
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error deleting temporary recipes", e)
            false
        }
    }

    fun updateRecipeFoodIdForTemporaryRecords(newFoodId: Int): Boolean {
        return try {
            val values = ContentValues().apply {
                put("FoodId", newFoodId)
            }
            db.update("Recipe", values, "FoodId = 0", null) > 0
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error updating recipe FoodId", e)
            false
        }
    }

    fun copyRecipesForFood(foodId: Int): Boolean {
        return try {
            db.beginTransaction()
            deleteCopiedRecipes(foodId)

            db.rawQuery(
                "SELECT * FROM Recipe WHERE FoodId = ? AND CopyFg != 1",
                arrayOf(foodId.toString())
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        val recipe = createRecipeFromCursor(cursor)
                        val values = ContentValues().apply {
                            putRecipeFields(recipe.copy(copyFg = 1))
                        }
                        db.insert("Recipe", null, values)
                    } while (cursor.moveToNext())
                }
            }

            db.setTransactionSuccessful()
            true
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error copying recipes for editing", e)
            false
        } finally {
            runCatching { db.endTransaction() }
        }
    }

    fun duplicateRecipesToFoodIdZero(foodId: Int): Boolean {
        return try {
            db.beginTransaction()
            db.delete("Recipe", "FoodId = 0 AND CopyFg = 0", null)
            db.rawQuery(
                "SELECT * FROM Recipe WHERE FoodId = ?",
                arrayOf(foodId.toString())
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        val recipe = createRecipeFromCursor(cursor)
                        val values = ContentValues().apply {
                            putRecipeFields(recipe.copy(foodId = 0, copyFg = 0))
                        }
                        db.insert("Recipe", null, values)
                    } while (cursor.moveToNext())
                }
            }
            db.setTransactionSuccessful()
            true
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error duplicating recipes for copy flow", e)
            false
        } finally {
            runCatching { db.endTransaction() }
        }
    }

    fun replaceOriginalRecipesWithCopies(foodId: Int): Boolean {
        return try {
            db.beginTransaction()
            db.delete("Recipe", "FoodId = ? AND CopyFg = 0", arrayOf(foodId.toString()))
            val values = ContentValues().apply { put("CopyFg", 0) }
            db.update("Recipe", values, "FoodId = ? AND CopyFg = 1", arrayOf(foodId.toString()))
            db.setTransactionSuccessful()
            true
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error promoting copied recipes for foodId=$foodId", e)
            false
        } finally {
            runCatching { db.endTransaction() }
        }
    }

    fun deleteRecipesByFoodId(foodId: Int): Boolean {
        return try {
            db.delete("Recipe", "FoodId = ?", arrayOf(foodId.toString())) >= 0
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error deleting recipes for foodId=$foodId", e)
            false
        }
    }

    fun deleteCopiedRecipes(foodId: Int): Boolean {
        return try {
            db.delete("Recipe", "FoodId = ? AND CopyFg = 1", arrayOf(foodId.toString())) >= 0
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error deleting copied recipes for foodId=$foodId", e)
            false
        }
    }

    fun deleteAllCopiedRecipes(): Boolean {
        return try {
            db.delete("Recipe", "CopyFg = 1", null) >= 0
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error deleting all copied recipes", e)
            false
        }
    }

    fun updateRecipe(recipe: RecipeItem): Boolean {
        return try {
            val values = ContentValues().apply { putRecipeFields(recipe) }
            db.update("Recipe", values, "RecipeId = ?", arrayOf(recipe.recipeId.toString())) > 0
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error updating recipe item", e)
            false
        }
    }

    fun deleteRecipe(recipeId: Int): Boolean {
        return try {
            db.delete("Recipe", "RecipeId = ?", arrayOf(recipeId.toString())) > 0
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error deleting recipe item", e)
            false
        }
    }

    @SuppressLint("Range")
    fun readRecipes(): List<RecipeItem> {
        val recipes = mutableListOf<RecipeItem>()
        try {
            db.rawQuery("SELECT * FROM Recipe WHERE FoodId = 0 ORDER BY RecipeId DESC", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        recipes.add(createRecipeFromCursor(cursor))
                    } while (cursor.moveToNext())
                }
            }
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error reading recipes", e)
        }
        return recipes
    }

    @SuppressLint("Range")
    fun readCopiedRecipes(foodId: Int): List<RecipeItem> {
        val recipes = mutableListOf<RecipeItem>()
        try {
            db.rawQuery(
                "SELECT * FROM Recipe WHERE CopyFg = 1 AND FoodId = ? ORDER BY RecipeId DESC",
                arrayOf(foodId.toString())
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        recipes.add(createRecipeFromCursor(cursor))
                    } while (cursor.moveToNext())
                }
            }
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error reading copied recipes", e)
        }
        return recipes
    }

    @SuppressLint("Range")
    fun searchFoods(query: String): List<Food> {
        val foodList = mutableListOf<Food>()
        try {
            val terms = query.split("|")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val (sql, args) = if (terms.size > 1) {
                val whereClause = terms.joinToString(" AND ") { "FoodDescription LIKE ?" }
                "SELECT * FROM Foods WHERE $whereClause" to terms.map { "%$it%" }.toTypedArray()
            } else {
                val singleTerm = terms.firstOrNull() ?: query
                "SELECT * FROM Foods WHERE FoodDescription LIKE ?" to arrayOf("%$singleTerm%")
            }
            db.rawQuery(sql, args).use { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        foodList.add(createFoodFromCursor(cursor))
                    } while (cursor.moveToNext())
                }
            }
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error searching foods", e)
        }
        return foodList
    }
    
    @SuppressLint("Range")
    private fun createFoodFromCursor(cursor: Cursor): Food {
        return Food(
            foodId = cursor.getInt(cursor.getColumnIndexOrThrow("FoodId")),
            foodDescription = cursor.getString(cursor.getColumnIndexOrThrow("FoodDescription")),
            energy = cursor.getDouble(cursor.getColumnIndexOrThrow("Energy")),
            protein = cursor.getDouble(cursor.getColumnIndexOrThrow("Protein")),
            fatTotal = cursor.getDouble(cursor.getColumnIndexOrThrow("FatTotal")),
            saturatedFat = cursor.getDouble(cursor.getColumnIndexOrThrow("SaturatedFat")),
            transFat = cursor.getDouble(cursor.getColumnIndexOrThrow("TransFat")),
            polyunsaturatedFat = cursor.getDouble(cursor.getColumnIndexOrThrow("PolyunsaturatedFat")),
            monounsaturatedFat = cursor.getDouble(cursor.getColumnIndexOrThrow("MonounsaturatedFat")),
            carbohydrate = cursor.getDouble(cursor.getColumnIndexOrThrow("Carbohydrate")),
            sugars = cursor.getDouble(cursor.getColumnIndexOrThrow("Sugars")),
            dietaryFibre = cursor.getDouble(cursor.getColumnIndexOrThrow("DietaryFibre")),
            sodium = cursor.getDouble(cursor.getColumnIndexOrThrow("SodiumNa")),
            calciumCa = cursor.getDouble(cursor.getColumnIndexOrThrow("CalciumCa")),
            potassiumK = cursor.getDouble(cursor.getColumnIndexOrThrow("PotassiumK")),
            thiaminB1 = cursor.getDouble(cursor.getColumnIndexOrThrow("ThiaminB1")),
            riboflavinB2 = cursor.getDouble(cursor.getColumnIndexOrThrow("RiboflavinB2")),
            niacinB3 = cursor.getDouble(cursor.getColumnIndexOrThrow("NiacinB3")),
            folate = cursor.getDouble(cursor.getColumnIndexOrThrow("Folate")),
            ironFe = cursor.getDouble(cursor.getColumnIndexOrThrow("IronFe")),
            magnesiumMg = cursor.getDouble(cursor.getColumnIndexOrThrow("MagnesiumMg")),
            vitaminC = cursor.getDouble(cursor.getColumnIndexOrThrow("VitaminC")),
            caffeine = cursor.getDouble(cursor.getColumnIndexOrThrow("Caffeine")),
            cholesterol = cursor.getDouble(cursor.getColumnIndexOrThrow("Cholesterol")),
            alcohol = cursor.getDouble(cursor.getColumnIndexOrThrow("Alcohol"))
        )
    }

    @SuppressLint("Range")
    private fun createEatenFoodFromCursor(cursor: Cursor): EatenFood {
        return EatenFood(
            eatenId = cursor.getInt(cursor.getColumnIndexOrThrow("EatenId")),
            dateEaten = cursor.getString(cursor.getColumnIndexOrThrow("DateEaten")),
            timeEaten = cursor.getString(cursor.getColumnIndexOrThrow("TimeEaten")),
            eatenTs = cursor.getInt(cursor.getColumnIndexOrThrow("EatenTs")),
            amountEaten = cursor.getDouble(cursor.getColumnIndexOrThrow("AmountEaten")),
            foodDescription = cursor.getString(cursor.getColumnIndexOrThrow("FoodDescription")),
            energy = cursor.getDouble(cursor.getColumnIndexOrThrow("Energy")),
            protein = cursor.getDouble(cursor.getColumnIndexOrThrow("Protein")),
            fatTotal = cursor.getDouble(cursor.getColumnIndexOrThrow("FatTotal")),
            saturatedFat = cursor.getDouble(cursor.getColumnIndexOrThrow("SaturatedFat")),
            transFat = cursor.getDouble(cursor.getColumnIndexOrThrow("TransFat")),
            polyunsaturatedFat = cursor.getDouble(cursor.getColumnIndexOrThrow("PolyunsaturatedFat")),
            monounsaturatedFat = cursor.getDouble(cursor.getColumnIndexOrThrow("MonounsaturatedFat")),
            carbohydrate = cursor.getDouble(cursor.getColumnIndexOrThrow("Carbohydrate")),
            sugars = cursor.getDouble(cursor.getColumnIndexOrThrow("Sugars")),
            dietaryFibre = cursor.getDouble(cursor.getColumnIndexOrThrow("DietaryFibre")),
            sodiumNa = cursor.getDouble(cursor.getColumnIndexOrThrow("SodiumNa")),
            calciumCa = cursor.getDouble(cursor.getColumnIndexOrThrow("CalciumCa")),
            potassiumK = cursor.getDouble(cursor.getColumnIndexOrThrow("PotassiumK")),
            thiaminB1 = cursor.getDouble(cursor.getColumnIndexOrThrow("ThiaminB1")),
            riboflavinB2 = cursor.getDouble(cursor.getColumnIndexOrThrow("RiboflavinB2")),
            niacinB3 = cursor.getDouble(cursor.getColumnIndexOrThrow("NiacinB3")),
            folate = cursor.getDouble(cursor.getColumnIndexOrThrow("Folate")),
            ironFe = cursor.getDouble(cursor.getColumnIndexOrThrow("IronFe")),
            magnesiumMg = cursor.getDouble(cursor.getColumnIndexOrThrow("MagnesiumMg")),
            vitaminC = cursor.getDouble(cursor.getColumnIndexOrThrow("VitaminC")),
            caffeine = cursor.getDouble(cursor.getColumnIndexOrThrow("Caffeine")),
            cholesterol = cursor.getDouble(cursor.getColumnIndexOrThrow("Cholesterol")),
            alcohol = cursor.getDouble(cursor.getColumnIndexOrThrow("Alcohol"))
        )
    }

    @SuppressLint("Range")
    private fun createRecipeFromCursor(cursor: Cursor): RecipeItem {
        return RecipeItem(
            recipeId = cursor.getInt(cursor.getColumnIndexOrThrow("RecipeId")),
            foodId = cursor.getInt(cursor.getColumnIndexOrThrow("FoodId")),
            copyFg = cursor.getInt(cursor.getColumnIndexOrThrow("CopyFg")),
            amount = cursor.getDouble(cursor.getColumnIndexOrThrow("Amount")),
            foodDescription = cursor.getString(cursor.getColumnIndexOrThrow("FoodDescription")),
            energy = cursor.getDouble(cursor.getColumnIndexOrThrow("Energy")),
            protein = cursor.getDouble(cursor.getColumnIndexOrThrow("Protein")),
            fatTotal = cursor.getDouble(cursor.getColumnIndexOrThrow("FatTotal")),
            saturatedFat = cursor.getDouble(cursor.getColumnIndexOrThrow("SaturatedFat")),
            transFat = cursor.getDouble(cursor.getColumnIndexOrThrow("TransFat")),
            polyunsaturatedFat = cursor.getDouble(cursor.getColumnIndexOrThrow("PolyunsaturatedFat")),
            monounsaturatedFat = cursor.getDouble(cursor.getColumnIndexOrThrow("MonounsaturatedFat")),
            carbohydrate = cursor.getDouble(cursor.getColumnIndexOrThrow("Carbohydrate")),
            sugars = cursor.getDouble(cursor.getColumnIndexOrThrow("Sugars")),
            dietaryFibre = cursor.getDouble(cursor.getColumnIndexOrThrow("DietaryFibre")),
            sodiumNa = cursor.getDouble(cursor.getColumnIndexOrThrow("SodiumNa")),
            calciumCa = cursor.getDouble(cursor.getColumnIndexOrThrow("CalciumCa")),
            potassiumK = cursor.getDouble(cursor.getColumnIndexOrThrow("PotassiumK")),
            thiaminB1 = cursor.getDouble(cursor.getColumnIndexOrThrow("ThiaminB1")),
            riboflavinB2 = cursor.getDouble(cursor.getColumnIndexOrThrow("RiboflavinB2")),
            niacinB3 = cursor.getDouble(cursor.getColumnIndexOrThrow("NiacinB3")),
            folate = cursor.getDouble(cursor.getColumnIndexOrThrow("Folate")),
            ironFe = cursor.getDouble(cursor.getColumnIndexOrThrow("IronFe")),
            magnesiumMg = cursor.getDouble(cursor.getColumnIndexOrThrow("MagnesiumMg")),
            vitaminC = cursor.getDouble(cursor.getColumnIndexOrThrow("VitaminC")),
            caffeine = cursor.getDouble(cursor.getColumnIndexOrThrow("Caffeine")),
            cholesterol = cursor.getDouble(cursor.getColumnIndexOrThrow("Cholesterol")),
            alcohol = cursor.getDouble(cursor.getColumnIndexOrThrow("Alcohol"))
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DatabaseHelper(context.applicationContext, "foods.db").also { INSTANCE = it }
            }
        }
    }
}
