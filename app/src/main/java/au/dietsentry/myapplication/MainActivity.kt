package au.dietsentry.myapplication

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.edit
import au.dietsentry.myapplication.ui.theme.DietSentry4AndroidTheme
import java.text.SimpleDateFormat
import java.util.*

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

    // State to control the visibility of our new dialog
    var showSelectDialog by remember { mutableStateOf(false) }

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
                    onSelect = { showSelectDialog = true }, // Show the dialog on click
                    onEdit = { /* TODO */ },
                    onInsert = { /* TODO */ },
                    onDelete = { /* TODO */ }
                )
            }
        }

        // When showSelectDialog is true, display the dialog
        if (showSelectDialog) {
            selectedFood?.let {
                SelectAmountDialog(
                    food = it,
                    onDismiss = { showSelectDialog = false },
                    onConfirm = { amount, dateTime ->
                        // TODO: Handle the confirmed amount and date/time
                        showSelectDialog = false
                    }
                )
            }
        }
    }
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
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = onDismiss) {
                    Text("Cancel")
                }
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