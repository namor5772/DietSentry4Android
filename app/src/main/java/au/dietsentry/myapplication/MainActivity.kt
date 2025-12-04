package au.dietsentry.myapplication

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.edit
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import au.dietsentry.myapplication.ui.theme.DietSentry4AndroidTheme
import java.text.SimpleDateFormat
import java.util.*

private const val PREFS_NAME = "DietSentryPrefs"
private const val KEY_SHOW_NUTRITIONAL_INFO = "showNutritionalInfo"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DietSentry4AndroidTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "foodSearch") {
                    composable("foodSearch") {
                        FoodSearchScreen(navController = navController)
                    }
                    composable("eatenLog") {
                        EatenLogScreen(navController = navController)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EatenLogScreen(navController: NavController) {
    val context = LocalContext.current
    val dbHelper = remember { DatabaseHelper.getInstance(context) }
    var eatenFoods by remember { mutableStateOf(dbHelper.readEatenFoods()) }
    var selectedEatenFood by remember { mutableStateOf<EatenFood?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteEatenDialog by remember { mutableStateOf(false) }
    val sharedPreferences = remember {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    var showNutritionalInfo by remember {
        mutableStateOf(sharedPreferences.getBoolean(KEY_SHOW_NUTRITIONAL_INFO, false))
    }

    BackHandler(enabled = selectedEatenFood != null) {
        selectedEatenFood = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Eaten Food Log") },
                actions = {
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
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                items(eatenFoods) { eatenFood ->
                    EatenLogItem(
                        eatenFood = eatenFood,
                        onClick = {
                            selectedEatenFood = if (selectedEatenFood == eatenFood) null else eatenFood
                        },
                        showNutritionalInfo = showNutritionalInfo
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            AnimatedVisibility(
                visible = selectedEatenFood != null,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                selectedEatenFood?.let {
                    EatenSelectionPanel(
                        eatenFood = it,
                        onEdit = { showEditDialog = true },
                        onDelete = { showDeleteEatenDialog = true }
                    )
                }
            }
        }
    }

    if (showEditDialog) {
        selectedEatenFood?.let { eatenFood ->
            EditEatenItemDialog(
                eatenFood = eatenFood,
                onDismiss = { showEditDialog = false },
                onConfirm = { amount, dateTime ->
                    dbHelper.updateEatenFood(eatenFood, amount, dateTime)
                    eatenFoods = dbHelper.readEatenFoods()
                    showEditDialog = false
                    selectedEatenFood = null
                }
            )
        }
    }
    
    if (showDeleteEatenDialog) {
        selectedEatenFood?.let { eatenFood ->
            DeleteEatenItemConfirmationDialog(
                eatenFood = eatenFood,
                onDismiss = { showDeleteEatenDialog = false },
                onConfirm = {
                    dbHelper.deleteEatenFood(eatenFood.eatenId)
                    eatenFoods = dbHelper.readEatenFoods()
                    selectedEatenFood = null
                    showDeleteEatenDialog = false
                }
            )
        }
    }
}

@Composable
fun DeleteEatenItemConfirmationDialog(
    eatenFood: EatenFood,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete Eaten Food?",
                color = Color.Red,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text("Are you sure you want to delete:")
                Spacer(modifier = Modifier.height(4.dp))
                Text(eatenFood.foodDescription, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                val unit = if (Regex("mL#?$", RegexOption.IGNORE_CASE).containsMatchIn(eatenFood.foodDescription)) "mL" else "g"
                Text("Amount: ${eatenFood.amountEaten} $unit")
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(onClick = onConfirm) {
                    Text("Delete")
                }
            }
        },
        dismissButton = {}
    )
}

@Composable
fun EatenLogItem(eatenFood: EatenFood, onClick: () -> Unit, showNutritionalInfo: Boolean) {
    val unit = if (Regex("mL#?$", RegexOption.IGNORE_CASE).containsMatchIn(eatenFood.foodDescription)) "mL" else "g"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Date: ${eatenFood.dateEaten} ${eatenFood.timeEaten}", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(eatenFood.foodDescription, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(2.dp))
            if (showNutritionalInfo) {
                NutritionalInfo(eatenFood = eatenFood, unit = unit)
            } else {
                Text("Amount: ${eatenFood.amountEaten}$unit", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun NutritionalInfo(eatenFood: EatenFood, unit: String) {
    Column(modifier = Modifier.padding(top = 0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(0.5f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Amount eaten ($unit):", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "%.1f".format(eatenFood.amountEaten),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(0.5f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Energy (kJ):", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "%.0f".format(eatenFood.energy),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(0.5f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Protein (g):", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "%.1f".format(eatenFood.protein),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(0.5f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Fat, total (g):", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "%.1f".format(eatenFood.fatTotal),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(0.5f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "- Saturated (g):", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "%.1f".format(eatenFood.saturatedFat),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(0.5f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Carbohydrate (g):", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "%.1f".format(eatenFood.carbohydrate),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(0.5f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "- Sugars (g):", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "%.1f".format(eatenFood.sugars),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(0.5f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Dietary Fibre (g):", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "%.1f".format(eatenFood.dietaryFibre),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(0.5f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Sodium (mg):", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "%.1f".format(eatenFood.sodiumNa),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
fun EatenSelectionPanel(
    eatenFood: EatenFood,
    onEdit: () -> Unit,
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
            Text(
                text = eatenFood.foodDescription,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Logged on: ${eatenFood.dateEaten} at ${eatenFood.timeEaten}",
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = onEdit) { Text("Edit") }
                Button(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}


@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FoodSearchScreen(modifier: Modifier = Modifier, navController: NavController) {
    val context = LocalContext.current
    val dbHelper = remember { DatabaseHelper.getInstance(context) }
    
    var searchQuery by remember { mutableStateOf("") }
    val sharedPreferences = remember {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    var foods by remember { mutableStateOf(dbHelper.readFoodsFromDatabase()) }
    val keyboardController = LocalSoftwareKeyboardController.current
    var showNutritionalInfo by remember {
        mutableStateOf(sharedPreferences.getBoolean(KEY_SHOW_NUTRITIONAL_INFO, false))
    }
    var selectedFood by remember { mutableStateOf<Food?>(null) }

    // State to control the visibility of our new dialog
    var showSelectDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditFoodDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = selectedFood != null) {
        selectedFood = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Foods Table") },
                actions = {
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
                    IconButton(onClick = { navController.navigate("eatenLog") }) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "View Eaten Log")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)) {
            Column {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Enter food filter text") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        foods = if (searchQuery.isNotBlank()) {
                            dbHelper.searchFoods(searchQuery)
                        } else {
                            dbHelper.readFoodsFromDatabase()
                        }
                        keyboardController?.hide()
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
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
                        onSelect = { showSelectDialog = true }, // Show the dialog on click
                        onEdit = { showEditFoodDialog = true },
                        onInsert = { /* TODO */ },
                        onDelete = { showDeleteDialog = true }
                    )
                }
            }
        }
    }

    if (showSelectDialog) {
        selectedFood?.let { food ->
            SelectAmountDialog(
                food = food,
                onDismiss = { showSelectDialog = false },
                onConfirm = { amount, dateTime ->
                    dbHelper.logEatenFood(food, amount, dateTime)
                    showSelectDialog = false
                    navController.navigate("eatenLog")
                }
            )
        }
    }
    if (showEditFoodDialog) {
        selectedFood?.let { food ->
            EditFoodDialog(
                food = food,
                onDismiss = { showEditFoodDialog = false },
                onConfirm = { updatedFood ->
                    val updated = dbHelper.updateFood(updatedFood)
                    if (updated) {
                        foods = dbHelper.readFoodsFromDatabase()
                        selectedFood = foods.find { it.foodId == updatedFood.foodId }
                        showEditFoodDialog = false
                    } else {
                        Toast.makeText(context, "Failed to update food", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
    if (showDeleteDialog) {
        selectedFood?.let { food ->
            DeleteConfirmationDialog(
                food = food,
                onDismiss = { showDeleteDialog = false },
                onConfirm = {
                    dbHelper.deleteFood(food.foodId)
                    foods = dbHelper.readFoodsFromDatabase()
                    selectedFood = null
                    showDeleteDialog = false
                }
            )
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    food: Food,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete Food?",
                color = Color.Red,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text("Are you sure you want to delete :")
                Text(food.foodDescription, fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(onClick = onConfirm) {
                    Text("Delete")
                }
            }
        },
        dismissButton = {}
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditFoodDialog(
    food: Food,
    onDismiss: () -> Unit,
    onConfirm: (Food) -> Unit
) {
    var description by remember(food) { mutableStateOf(food.foodDescription) }
    var energy by remember(food) { mutableStateOf(food.energy.toString()) }
    var protein by remember(food) { mutableStateOf(food.protein.toString()) }
    var fatTotal by remember(food) { mutableStateOf(food.fatTotal.toString()) }
    var saturatedFat by remember(food) { mutableStateOf(food.saturatedFat.toString()) }
    var transFat by remember(food) { mutableStateOf(food.transFat.toString()) }
    var polyunsaturatedFat by remember(food) { mutableStateOf(food.polyunsaturatedFat.toString()) }
    var monounsaturatedFat by remember(food) { mutableStateOf(food.monounsaturatedFat.toString()) }
    var carbohydrate by remember(food) { mutableStateOf(food.carbohydrate.toString()) }
    var sugars by remember(food) { mutableStateOf(food.sugars.toString()) }
    var dietaryFibre by remember(food) { mutableStateOf(food.dietaryFibre.toString()) }
    var sodium by remember(food) { mutableStateOf(food.sodium.toString()) }
    var calciumCa by remember(food) { mutableStateOf(food.calciumCa.toString()) }
    var potassiumK by remember(food) { mutableStateOf(food.potassiumK.toString()) }
    var thiaminB1 by remember(food) { mutableStateOf(food.thiaminB1.toString()) }
    var riboflavinB2 by remember(food) { mutableStateOf(food.riboflavinB2.toString()) }
    var niacinB3 by remember(food) { mutableStateOf(food.niacinB3.toString()) }
    var folate by remember(food) { mutableStateOf(food.folate.toString()) }
    var ironFe by remember(food) { mutableStateOf(food.ironFe.toString()) }
    var magnesiumMg by remember(food) { mutableStateOf(food.magnesiumMg.toString()) }
    var vitaminC by remember(food) { mutableStateOf(food.vitaminC.toString()) }
    var caffeine by remember(food) { mutableStateOf(food.caffeine.toString()) }
    var cholesterol by remember(food) { mutableStateOf(food.cholesterol.toString()) }
    var alcohol by remember(food) { mutableStateOf(food.alcohol.toString()) }

    val scrollState = rememberScrollState()
    val numericEntries = listOf(
        energy, protein, fatTotal, saturatedFat, transFat, polyunsaturatedFat, monounsaturatedFat,
        carbohydrate, sugars, dietaryFibre, sodium, calciumCa, potassiumK, thiaminB1,
        riboflavinB2, niacinB3, folate, ironFe, magnesiumMg, vitaminC, caffeine, cholesterol, alcohol
    )
    val isValid = description.isNotBlank() && numericEntries.all { it.toDoubleOrNull() != null }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Food",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                LabeledValueField(
                    label = "Description",
                    value = description,
                    onValueChange = { description = it }
                )
                Spacer(modifier = Modifier.height(8.dp))
                LabeledValueField(
                    label = "Energy (kJ)",
                    value = energy,
                    onValueChange = { energy = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    keyboardType = KeyboardType.Decimal
                )
                LabeledValueField(
                    label = "Protein (g)",
                    value = protein,
                    onValueChange = { protein = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    keyboardType = KeyboardType.Decimal
                )
                LabeledValueField(
                    label = "Fat, Total (g)",
                    value = fatTotal,
                    onValueChange = { fatTotal = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    keyboardType = KeyboardType.Decimal
                )
                LabeledValueField(
                    label = "- Saturated (g)",
                    value = saturatedFat,
                    onValueChange = { saturatedFat = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    keyboardType = KeyboardType.Decimal
                )
                LabeledValueField(
                    label = "- Trans (g)",
                    value = transFat,
                    onValueChange = { transFat = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    keyboardType = KeyboardType.Decimal
                )
                LabeledValueField(
                    label = "- Polyunsaturated (g)",
                    value = polyunsaturatedFat,
                    onValueChange = { polyunsaturatedFat = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    keyboardType = KeyboardType.Decimal
                )
                LabeledValueField(
                    label = "- Monounsaturated (g)",
                    value = monounsaturatedFat,
                    onValueChange = { monounsaturatedFat = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    keyboardType = KeyboardType.Decimal
                )
                LabeledValueField(
                    label = "Carbohydrate (g)",
                    value = carbohydrate,
                    onValueChange = { carbohydrate = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    keyboardType = KeyboardType.Decimal
                )
                LabeledValueField(
                    label = "- Sugars (g)",
                    value = sugars,
                    onValueChange = { sugars = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    keyboardType = KeyboardType.Decimal
                )
                LabeledValueField(
                    label = "Dietary Fibre (g)",
                    value = dietaryFibre,
                    onValueChange = { dietaryFibre = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    keyboardType = KeyboardType.Decimal
                )
                LabeledValueField(
                    label = "Sodium (mg)",
                    value = sodium,
                    onValueChange = { sodium = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    keyboardType = KeyboardType.Decimal
                )
                LabeledValueField(
                    label = "Calcium (mg)",
                    value = calciumCa,
                    onValueChange = { calciumCa = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    keyboardType = KeyboardType.Decimal
                )
                LabeledValueField(
                    label = "Potassium (mg)",
                    value = potassiumK,
                    onValueChange = { potassiumK = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    keyboardType = KeyboardType.Decimal
                )
                LabeledValueField(
                    label = "Thiamin B1 (mg)",
                    value = thiaminB1,
                    onValueChange = { thiaminB1 = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    keyboardType = KeyboardType.Decimal
                )
                LabeledValueField(
                    label = "Riboflavin B2 (mg)",
                    value = riboflavinB2,
                    onValueChange = { riboflavinB2 = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    keyboardType = KeyboardType.Decimal
                )
                LabeledValueField(
                    label = "Niacin B3 (mg)",
                    value = niacinB3,
                    onValueChange = { niacinB3 = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    keyboardType = KeyboardType.Decimal
                )
                LabeledValueField(
                    label = "Folate (ug)",
                    value = folate,
                    onValueChange = { folate = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    keyboardType = KeyboardType.Decimal
                )
                LabeledValueField(
                    label = "Iron (mg)",
                    value = ironFe,
                    onValueChange = { ironFe = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    keyboardType = KeyboardType.Decimal
                )
                LabeledValueField(
                    label = "Magnesium (mg)",
                    value = magnesiumMg,
                    onValueChange = { magnesiumMg = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    keyboardType = KeyboardType.Decimal
                )
                LabeledValueField(
                    label = "Vitamin C (mg)",
                    value = vitaminC,
                    onValueChange = { vitaminC = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    keyboardType = KeyboardType.Decimal
                )
                LabeledValueField(
                    label = "Caffeine (mg)",
                    value = caffeine,
                    onValueChange = { caffeine = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    keyboardType = KeyboardType.Decimal
                )
                LabeledValueField(
                    label = "Cholesterol (mg)",
                    value = cholesterol,
                    onValueChange = { cholesterol = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    keyboardType = KeyboardType.Decimal
                )
                LabeledValueField(
                    label = "Alcohol (g)",
                    value = alcohol,
                    onValueChange = { alcohol = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    keyboardType = KeyboardType.Decimal
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = {
                        val updatedFood = food.copy(
                            foodDescription = description,
                            energy = energy.toDouble(),
                            protein = protein.toDouble(),
                            fatTotal = fatTotal.toDouble(),
                            saturatedFat = saturatedFat.toDouble(),
                            transFat = transFat.toDouble(),
                            polyunsaturatedFat = polyunsaturatedFat.toDouble(),
                            monounsaturatedFat = monounsaturatedFat.toDouble(),
                            carbohydrate = carbohydrate.toDouble(),
                            sugars = sugars.toDouble(),
                            dietaryFibre = dietaryFibre.toDouble(),
                            sodium = sodium.toDouble(),
                            calciumCa = calciumCa.toDouble(),
                            potassiumK = potassiumK.toDouble(),
                            thiaminB1 = thiaminB1.toDouble(),
                            riboflavinB2 = riboflavinB2.toDouble(),
                            niacinB3 = niacinB3.toDouble(),
                            folate = folate.toDouble(),
                            ironFe = ironFe.toDouble(),
                            magnesiumMg = magnesiumMg.toDouble(),
                            vitaminC = vitaminC.toDouble(),
                            caffeine = caffeine.toDouble(),
                            cholesterol = cholesterol.toDouble(),
                            alcohol = alcohol.toDouble()
                        )
                        onConfirm(updatedFood)
                    },
                    enabled = isValid
                ) {
                    Text("Edit")
                }
            }
        },
        dismissButton = {}
    )
}

@Composable
private fun LabeledValueField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEatenItemDialog(
    eatenFood: EatenFood,
    onDismiss: () -> Unit,
    onConfirm: (amount: Float, dateTime: Long) -> Unit
) {
    val initialDateTime = remember(eatenFood) {
        try {
            val dateTimeString = "${eatenFood.dateEaten} ${eatenFood.timeEaten}"
            val parser = SimpleDateFormat("d-MMM-yy HH:mm", Locale.getDefault())
            parser.parse(dateTimeString)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis() // Fallback
        }
    }

    var amount by remember { mutableStateOf(eatenFood.amountEaten.toString()) }
    var selectedDateTime by remember { mutableStateOf(initialDateTime) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    // --- Date Picker State ---
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateTime)

    // --- Time Picker State ---
    var showTimePicker by remember { mutableStateOf(false) }
    val calendarForTime = Calendar.getInstance().apply { timeInMillis = selectedDateTime }
    val timePickerState = rememberTimePickerState(
        initialHour = calendarForTime.get(Calendar.HOUR_OF_DAY),
        initialMinute = calendarForTime.get(Calendar.MINUTE)
    )

    val foodUnit = if (Regex("mL#?$", RegexOption.IGNORE_CASE).containsMatchIn(eatenFood.foodDescription)) "mL" else "g"
    val displayName = eatenFood.foodDescription.replace(Regex(" #$| mL#?$", RegexOption.IGNORE_CASE), "")

    // --- Date Picker Dialog ---
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                Button(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val newDate = Calendar.getInstance().apply { timeInMillis = it }
                        val current = Calendar.getInstance().apply { timeInMillis = selectedDateTime }
                        current.set(newDate.get(Calendar.YEAR), newDate.get(Calendar.MONTH), newDate.get(Calendar.DAY_OF_MONTH))
                        selectedDateTime = current.timeInMillis
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                Button(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // --- Time Picker Dialog ---
    if (showTimePicker) {
        TimePickerDialog(
            onDismiss = { showTimePicker = false },
            onConfirm = {
                val current = Calendar.getInstance().apply { timeInMillis = selectedDateTime }
                current.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                current.set(Calendar.MINUTE, timePickerState.minute)
                selectedDateTime = current.timeInMillis
                showTimePicker = false
            },
        ) {
            TimePicker(state = timePickerState)
        }
    }

    // --- Main Dialog ---
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = amount,
                        onValueChange = { amount = it.filter { char -> char.isDigit() || char == '.' } },
                        label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = foodUnit)
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Button(onClick = { showDatePicker = true }) {
                        Text(text = dateFormat.format(selectedDateTime))
                    }
                    Button(onClick = { showTimePicker = true }) {
                        Text(text = timeFormat.format(selectedDateTime))
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = {
                        val finalAmount = amount.toFloatOrNull() ?: 0f
                        onConfirm(finalAmount, selectedDateTime)
                    },
                    enabled = amount.isNotBlank()
                ) {
                    Text("Confirm")
                }
            }
        },
        dismissButton = {}
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectAmountDialog(
    food: Food,
    onDismiss: () -> Unit,
    onConfirm: (amount: Float, dateTime: Long) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    val calendar = Calendar.getInstance()
    var selectedDateTime by remember { mutableStateOf(calendar.timeInMillis) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    // --- Date Picker State ---
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateTime)

    // --- Time Picker State ---
    var showTimePicker by remember { mutableStateOf(false) }
    val calendarForTime = Calendar.getInstance().apply { timeInMillis = selectedDateTime }
    val timePickerState = rememberTimePickerState(
        initialHour = calendarForTime.get(Calendar.HOUR_OF_DAY),
        initialMinute = calendarForTime.get(Calendar.MINUTE)
    )

    val foodUnit = if (Regex("mL#?$", RegexOption.IGNORE_CASE).containsMatchIn(food.foodDescription)) "mL" else "g"
    val displayName = food.foodDescription.replace(Regex(" #$| mL#?$", RegexOption.IGNORE_CASE), "")

    // --- Date Picker Dialog ---
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                Button(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val newDate = Calendar.getInstance().apply { timeInMillis = it }
                        val current = Calendar.getInstance().apply { timeInMillis = selectedDateTime }
                        current.set(newDate.get(Calendar.YEAR), newDate.get(Calendar.MONTH), newDate.get(Calendar.DAY_OF_MONTH))
                        selectedDateTime = current.timeInMillis
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                Button(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // --- Time Picker Dialog ---
    if (showTimePicker) {
        TimePickerDialog(
            onDismiss = { showTimePicker = false },
            onConfirm = {
                val current = Calendar.getInstance().apply { timeInMillis = selectedDateTime }
                current.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                current.set(Calendar.MINUTE, timePickerState.minute)
                selectedDateTime = current.timeInMillis
                showTimePicker = false
            },
        ) {
            TimePicker(state = timePickerState)
        }
    }

    // --- Main Dialog ---
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = amount,
                        onValueChange = { amount = it.filter { char -> char.isDigit() || char == '.' } },
                        label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = foodUnit)
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Button(onClick = { showDatePicker = true }) {
                        Text(text = dateFormat.format(selectedDateTime))
                    }
                    Button(onClick = { showTimePicker = true }) {
                        Text(text = timeFormat.format(selectedDateTime))
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = {
                        val finalAmount = amount.toFloatOrNull() ?: 0f
                        onConfirm(finalAmount, selectedDateTime)
                    },
                    enabled = amount.isNotBlank()
                ) {
                    Text("Confirm")
                }
            }
        },
        dismissButton = {}
    )
}

// A wrapper for the TimePickerDialog to make it feel more integrated
@Composable
fun TimePickerDialog(
    title: String = "Select Time",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .width(IntrinsicSize.Min)
                .height(IntrinsicSize.Min),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    text = title,
                    style = MaterialTheme.typography.labelMedium
                )
                content()
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .fillMaxWidth()
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = onConfirm) { Text("OK") }
                }
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
                Button(onClick = onSelect) { Text("Eaten") }
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
        // This preview will not work correctly with navigation
        // FoodSearchScreen(navController = rememberNavController())
    }
}
