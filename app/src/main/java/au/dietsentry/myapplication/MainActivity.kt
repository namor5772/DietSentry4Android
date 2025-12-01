package au.dietsentry.myapplication

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import au.dietsentry.myapplication.ui.theme.DietSentry4AndroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Copy the database from assets
        copyDatabaseFromAssets(this, "foods.db")

        val foods = readFoodsFromDatabase(this, "foods.db")
        Log.d("MainActivity", "Number of foods read: ${foods.size}")

        enableEdgeToEdge()
        setContent {
            DietSentry4AndroidTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    FoodList(
                        foods = foods,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DietSentry4AndroidTheme {
        FoodList(
            foods = listOf(
                Food(1, "Banana", 371.0, 1.1, 0.3, 0.1, 22.8, 12.2, 1.0, 2.6),
                Food(2, "Apple", 218.0, 0.3, 0.2, 0.1, 13.8, 10.4, 1.0, 2.4)
            )
        )
    }
}
