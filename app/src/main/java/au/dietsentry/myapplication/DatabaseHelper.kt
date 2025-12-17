package au.dietsentry.myapplication

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

private const val REFERENCE_TIMESTAMP_SECONDS = 1672491600L // Adjusted by 460 minutes

class DatabaseHelper private constructor(context: Context, private val databaseName: String) {

    private val db: SQLiteDatabase

    init {
        copyDatabaseFromAssets(context)
        db = SQLiteDatabase.openDatabase(context.getDatabasePath(databaseName).path, null, SQLiteDatabase.OPEN_READWRITE)
    }

    private fun copyDatabaseFromAssets(context: Context) {
        val dbPath = context.getDatabasePath(databaseName)
        if (dbPath.exists()) return
        dbPath.parentFile?.mkdirs()
        try {
            context.assets.open(databaseName).use { inputStream ->
                FileOutputStream(dbPath).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error copying database", e)
        }
    }
    
    private fun Double.roundToTwoDecimalPlaces(): Double = (this * 100).roundToInt() / 100.0

    private fun ContentValues.putFoodNutrients(food: Food) {
        put("FoodDescription", food.foodDescription)
        put("Energy", food.energy)
        put("Protein", food.protein)
        put("FatTotal", food.fatTotal)
        put("SaturatedFat", food.saturatedFat)
        put("TransFat", food.transFat)
        put("PolyunsaturatedFat", food.polyunsaturatedFat)
        put("MonounsaturatedFat", food.monounsaturatedFat)
        put("Carbohydrate", food.carbohydrate)
        put("Sugars", food.sugars)
        put("DietaryFibre", food.dietaryFibre)
        put("SodiumNa", food.sodium)
        put("CalciumCa", food.calciumCa)
        put("PotassiumK", food.potassiumK)
        put("ThiaminB1", food.thiaminB1)
        put("RiboflavinB2", food.riboflavinB2)
        put("NiacinB3", food.niacinB3)
        put("Folate", food.folate)
        put("IronFe", food.ironFe)
        put("MagnesiumMg", food.magnesiumMg)
        put("VitaminC", food.vitaminC)
        put("Caffeine", food.caffeine)
        put("Cholesterol", food.cholesterol)
        put("Alcohol", food.alcohol)
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
        put("Energy", recipe.energy)
        put("Protein", recipe.protein)
        put("FatTotal", recipe.fatTotal)
        put("SaturatedFat", recipe.saturatedFat)
        put("TransFat", recipe.transFat)
        put("PolyunsaturatedFat", recipe.polyunsaturatedFat)
        put("MonounsaturatedFat", recipe.monounsaturatedFat)
        put("Carbohydrate", recipe.carbohydrate)
        put("Sugars", recipe.sugars)
        put("DietaryFibre", recipe.dietaryFibre)
        put("SodiumNa", recipe.sodiumNa)
        put("CalciumCa", recipe.calciumCa)
        put("PotassiumK", recipe.potassiumK)
        put("ThiaminB1", recipe.thiaminB1)
        put("RiboflavinB2", recipe.riboflavinB2)
        put("NiacinB3", recipe.niacinB3)
        put("Folate", recipe.folate)
        put("IronFe", recipe.ironFe)
        put("MagnesiumMg", recipe.magnesiumMg)
        put("VitaminC", recipe.vitaminC)
        put("Caffeine", recipe.caffeine)
        put("Cholesterol", recipe.cholesterol)
        put("Alcohol", recipe.alcohol)
    }

    private fun calculateEatenTimestampMinutes(dateTime: Long): Int {
        val eatenTimestampSeconds = dateTime / 1000
        return ((eatenTimestampSeconds - REFERENCE_TIMESTAMP_SECONDS) / 60).toInt()
    }

    fun insertRecipeFromFood(food: Food, amount: Float): Boolean {
        return try {
            val values = ContentValues().apply {
                put("FoodId", 0)
                put("CopyFg", 0)
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

    fun deleteRecipesByFoodId(foodId: Int): Boolean {
        return try {
            db.delete("Recipe", "FoodId = ?", arrayOf(foodId.toString())) >= 0
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error deleting recipes for foodId=$foodId", e)
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
    fun searchFoods(query: String): List<Food> {
        val foodList = mutableListOf<Food>()
        try {
            db.rawQuery("SELECT * FROM Foods WHERE FoodDescription LIKE ?", arrayOf("%$query%")).use { cursor ->
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
