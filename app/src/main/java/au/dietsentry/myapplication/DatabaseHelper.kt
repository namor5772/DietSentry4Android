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

    fun logEatenFood(food: Food, amount: Float, dateTime: Long): Boolean {
        return try {
            val values = ContentValues().apply {
                val date = Date(dateTime)
                put("DateEaten", SimpleDateFormat("d-MMM-yy", Locale.getDefault()).format(date))
                put("TimeEaten", SimpleDateFormat("HH:mm", Locale.getDefault()).format(date))

                // Correct EatenTs calculation
                val referenceTimestampSeconds = 1672491600L // Adjusted by 460 minutes
                val eatenTimestampSeconds = dateTime / 1000
                val eatenTsInMinutes = (eatenTimestampSeconds - referenceTimestampSeconds) / 60
                put("EatenTs", eatenTsInMinutes.toInt())

                put("AmountEaten", amount)
                put("FoodDescription", food.foodDescription)
                val scale = amount / 100.0
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

            val referenceTimestampSeconds = 1672491600L
            val eatenTimestampSeconds = newDateTime / 1000
            val eatenTsInMinutes = (eatenTimestampSeconds - referenceTimestampSeconds) / 60
            values.put("EatenTs", eatenTsInMinutes.toInt())

            values.put("AmountEaten", newAmount)

            values.put("Energy", (eatenFood.energy * scale).roundToTwoDecimalPlaces())
            values.put("Protein", (eatenFood.protein * scale).roundToTwoDecimalPlaces())
            values.put("FatTotal", (eatenFood.fatTotal * scale).roundToTwoDecimalPlaces())
            values.put("SaturatedFat", (eatenFood.saturatedFat * scale).roundToTwoDecimalPlaces())
            values.put("TransFat", (eatenFood.transFat * scale).roundToTwoDecimalPlaces())
            values.put("PolyunsaturatedFat", (eatenFood.polyunsaturatedFat * scale).roundToTwoDecimalPlaces())
            values.put("MonounsaturatedFat", (eatenFood.monounsaturatedFat * scale).roundToTwoDecimalPlaces())
            values.put("Carbohydrate", (eatenFood.carbohydrate * scale).roundToTwoDecimalPlaces())
            values.put("Sugars", (eatenFood.sugars * scale).roundToTwoDecimalPlaces())
            values.put("DietaryFibre", (eatenFood.dietaryFibre * scale).roundToTwoDecimalPlaces())
            values.put("SodiumNa", (eatenFood.sodiumNa * scale).roundToTwoDecimalPlaces())
            values.put("CalciumCa", (eatenFood.calciumCa * scale).roundToTwoDecimalPlaces())
            values.put("PotassiumK", (eatenFood.potassiumK * scale).roundToTwoDecimalPlaces())
            values.put("ThiaminB1", (eatenFood.thiaminB1 * scale).roundToTwoDecimalPlaces())
            values.put("RiboflavinB2", (eatenFood.riboflavinB2 * scale).roundToTwoDecimalPlaces())
            values.put("NiacinB3", (eatenFood.niacinB3 * scale).roundToTwoDecimalPlaces())
            values.put("Folate", (eatenFood.folate * scale).roundToTwoDecimalPlaces())
            values.put("IronFe", (eatenFood.ironFe * scale).roundToTwoDecimalPlaces())
            values.put("MagnesiumMg", (eatenFood.magnesiumMg * scale).roundToTwoDecimalPlaces())
            values.put("VitaminC", (eatenFood.vitaminC * scale).roundToTwoDecimalPlaces())
            values.put("Caffeine", (eatenFood.caffeine * scale).roundToTwoDecimalPlaces())
            values.put("Cholesterol", (eatenFood.cholesterol * scale).roundToTwoDecimalPlaces())
            values.put("Alcohol", (eatenFood.alcohol * scale).roundToTwoDecimalPlaces())

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
