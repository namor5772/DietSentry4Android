package au.dietsentry.myapplication

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import au.dietsentry.myapplication.ui.theme.DietSentry4AndroidTheme

private const val PREFS_NAME = "DietSentryPrefs"
private const val KEY_SHOW_NUTRITIONAL_INFO = "showNutritionalInfo"

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
    val sharedPreferences = remember {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    var foods by remember { mutableStateOf(readFoodsFromDatabase(context, "foods.db")) }
    val keyboardController = LocalSoftwareKeyboardController.current
    var showNutritionalInfo by remember {
        mutableStateOf(sharedPreferences.getBoolean(KEY_SHOW_NUTRITIONAL_INFO, false))
    }
    var selectedFood by remember { mutableStateOf<Food?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    modifier = Modifier.weight(1f)
                )
                IconToggleButton(
                    checked = showNutritionalInfo,
                    onCheckedChange = {
                        showNutritionalInfo = it
                        sharedPreferences.edit {
                            putBoolean(KEY_SHOW_NUTRITIONAL_INFO, it)
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (showNutritionalInfo) Icons.Default.ToggleOn else Icons.Default.ToggleOff,
                        contentDescription = "Toggle nutritional information"
                    )
                }
            }
            FoodList(
                foods = foods,
                onFoodClicked = { food ->
                    selectedFood = if (selectedFood == food) null else food
                },
                showNutritionalInfo = showNutritionalInfo
            )
        }

        AnimatedVisibility(
            visible = selectedFood != null,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            selectedFood?.let { food ->
                SelectionPanel(
                    food = food,
                    onSelect = { /* TODO */ },
                    onEdit = { /* TODO */ },
                    onInsert = { /* TODO */ },
                    onDelete = { /* TODO */ }
                )
            }
        }
    }
}

@Composable
fun SelectionPanel(
    food: Food,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onInsert: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = food.foodDescription, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = onSelect) { Text("Select") }
                Button(onClick = onEdit) { Text("Edit") }
                Button(onClick = onInsert) { Text("Insert") }
                Button(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DietSentry4AndroidTheme {
        FoodSearchScreen()
    }
}