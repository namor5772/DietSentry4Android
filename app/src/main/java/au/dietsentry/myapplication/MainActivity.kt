package au.dietsentry.myapplication

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import au.dietsentry.myapplication.ui.theme.DietSentry4AndroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        copyDatabaseFromAssets(this, "foods.db")

        enableEdgeToEdge()
        setContent {
            DietSentry4AndroidTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    FoodSearchScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FoodSearchScreen(modifier: Modifier = Modifier) {
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    var foods by remember { mutableStateOf(readFoodsFromDatabase(context, "foods.db")) }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(modifier = modifier) {
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Enter food filter text") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                foods = if (searchQuery.isNotBlank()) {
                    searchFoods(context, "foods.db", searchQuery)
                } else {
                    readFoodsFromDatabase(context, "foods.db")
                }
                keyboardController?.hide()
            }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
        FoodList(
            foods = foods,
            onFoodClicked = { food ->
                // This is the action that happens when you click a food item
                Toast.makeText(context, "${food.foodDescription} clicked!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DietSentry4AndroidTheme {
        FoodSearchScreen()
    }
}
