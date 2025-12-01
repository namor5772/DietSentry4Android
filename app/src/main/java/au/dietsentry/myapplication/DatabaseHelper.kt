package au.dietsentry.myapplication

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.FileOutputStream

fun copyDatabaseFromAssets(context: Context, databaseName: String) {
    val dbPath = context.getDatabasePath(databaseName)

    if (dbPath.exists()) {
        return // Database already exists
    }

    dbPath.parentFile?.mkdirs()

    try {
        val inputStream = context.assets.open(databaseName)
        val outputStream = FileOutputStream(dbPath)

        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        // Handle exception
    }
}

@SuppressLint("Range")
fun readFoodsFromDatabase(context: Context, databaseName: String): List<Food> {
    val foodList = mutableListOf<Food>()
    val dbPath = context.getDatabasePath(databaseName)
    var db: SQLiteDatabase? = null

    try {
        db = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READONLY)
        val cursor: Cursor = db.rawQuery("SELECT * FROM Foods", null)
        if (cursor.moveToFirst()) {
            do {
                foodList.add(
                    Food(
                        foodId = cursor.getInt(cursor.getColumnIndexOrThrow("FoodId")),
                        foodDescription = cursor.getString(cursor.getColumnIndexOrThrow("FoodDescription")),
                        energy = cursor.getDouble(cursor.getColumnIndexOrThrow("Energy")),
                        protein = cursor.getDouble(cursor.getColumnIndexOrThrow("Protein")),
                        fatTotal = cursor.getDouble(cursor.getColumnIndexOrThrow("FatTotal")),
                        saturatedFat = cursor.getDouble(cursor.getColumnIndexOrThrow("SaturatedFat")),
                        carbohydrate = cursor.getDouble(cursor.getColumnIndexOrThrow("Carbohydrate")),
                        sugars = cursor.getDouble(cursor.getColumnIndexOrThrow("Sugars")),
                        sodium = cursor.getDouble(cursor.getColumnIndexOrThrow("SodiumNa")),
                        dietaryFibre = cursor.getDouble(cursor.getColumnIndexOrThrow("DietaryFibre"))
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
    } catch (e: Exception) {
        Log.e("DatabaseHelper", "Error reading foods from database", e)
        // Handle exception
    } finally {
        db?.close()
    }
    return foodList
}

@SuppressLint("Range")
fun searchFoods(context: Context, databaseName: String, query: String): List<Food> {
    val foodList = mutableListOf<Food>()
    val dbPath = context.getDatabasePath(databaseName)
    var db: SQLiteDatabase? = null

    try {
        db = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READONLY)
        val cursor: Cursor = db.rawQuery("SELECT * FROM Foods WHERE FoodDescription LIKE ?", arrayOf("%$query%"))
        if (cursor.moveToFirst()) {
            do {
                foodList.add(
                    Food(
                        foodId = cursor.getInt(cursor.getColumnIndexOrThrow("FoodId")),
                        foodDescription = cursor.getString(cursor.getColumnIndexOrThrow("FoodDescription")),
                        energy = cursor.getDouble(cursor.getColumnIndexOrThrow("Energy")),
                        protein = cursor.getDouble(cursor.getColumnIndexOrThrow("Protein")),
                        fatTotal = cursor.getDouble(cursor.getColumnIndexOrThrow("FatTotal")),
                        saturatedFat = cursor.getDouble(cursor.getColumnIndexOrThrow("SaturatedFat")),
                        carbohydrate = cursor.getDouble(cursor.getColumnIndexOrThrow("Carbohydrate")),
                        sugars = cursor.getDouble(cursor.getColumnIndexOrThrow("Sugars")),
                        sodium = cursor.getDouble(cursor.getColumnIndexOrThrow("SodiumNa")),
                        dietaryFibre = cursor.getDouble(cursor.getColumnIndexOrThrow("DietaryFibre"))
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
    } catch (e: Exception) {
        Log.e("DatabaseHelper", "Error searching foods", e)
    } finally {
        db?.close()
    }
    return foodList
}
