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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.edit
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import au.dietsentry.myapplication.ui.theme.DietSentry4AndroidTheme
import org.commonmark.Extension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.ThematicBreak
import org.commonmark.node.Text as MdText
import org.commonmark.parser.Parser
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

private const val PREFS_NAME = "DietSentryPrefs"
private const val KEY_SHOW_NUTRITIONAL_INFO = "showNutritionalInfo" // legacy boolean; kept for fallback
private const val KEY_NUTRITION_SELECTION_FOOD = "nutritionSelectionFood"
private const val KEY_NUTRITION_SELECTION_EATEN = "nutritionSelectionEaten"
private const val KEY_DISPLAY_DAILY_TOTALS = "displayDailyTotals"

private val mlSuffixRegex = Regex("mL#?$", RegexOption.IGNORE_CASE)
private val trailingMarkersRegex = Regex(" #$| mL#?$", RegexOption.IGNORE_CASE)
private val recipeMarkerRegex = Regex("\\{recipe=[^}]+\\}", RegexOption.IGNORE_CASE)

private fun isLiquidDescription(description: String): Boolean = mlSuffixRegex.containsMatchIn(description)
private fun descriptionUnit(description: String): String = if (isLiquidDescription(description)) "mL" else "g"
private fun descriptionDisplayName(description: String): String = description.replace(trailingMarkersRegex, "")
private fun isRecipeDescription(description: String): Boolean = recipeMarkerRegex.containsMatchIn(description)
private fun removeRecipeMarker(description: String): String =
    recipeMarkerRegex.replace(description, "").trimEnd()

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
                    composable(
                        route = "editFood/{foodId}",
                        arguments = listOf(navArgument("foodId") { type = NavType.IntType })
                    ) { backStackEntry ->
                        val foodId = backStackEntry.arguments?.getInt("foodId") ?: return@composable
                        EditFoodScreen(navController = navController, foodId = foodId)
                    }
                    composable(
                        route = "copyFood/{foodId}",
                        arguments = listOf(navArgument("foodId") { type = NavType.IntType })
                    ) { backStackEntry ->
                        val foodId = backStackEntry.arguments?.getInt("foodId") ?: return@composable
                        CopyFoodScreen(navController = navController, foodId = foodId)
                    }
                    composable("insertFood") {
                        InsertFoodScreen(navController = navController)
                    }
                    composable("addRecipe") {
                        AddRecipeScreen(navController = navController)
                    }
                    composable(
                        route = "copyRecipe/{foodId}",
                        arguments = listOf(navArgument("foodId") { type = NavType.IntType })
                    ) { backStackEntry ->
                        val foodId = backStackEntry.arguments?.getInt("foodId") ?: return@composable
                        CopyRecipeScreen(navController = navController, foodId = foodId)
                    }
                    composable(
                        route = "editRecipe/{foodId}",
                        arguments = listOf(navArgument("foodId") { type = NavType.IntType })
                    ) { backStackEntry ->
                        val foodId = backStackEntry.arguments?.getInt("foodId") ?: return@composable
                        EditRecipeScreen(navController = navController, foodId = foodId)
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
    val initialEatenSelection = remember {
        sharedPreferences.getInt(
            KEY_NUTRITION_SELECTION_EATEN,
            if (sharedPreferences.getBoolean(KEY_SHOW_NUTRITIONAL_INFO, false)) 1 else 0
        )
    }
    val initialDisplayDailyTotals = remember {
        sharedPreferences.getBoolean(KEY_DISPLAY_DAILY_TOTALS, false)
    }
    var nutritionalInfoSelection by remember { mutableIntStateOf(initialEatenSelection) }
    var showNutritionalInfo by remember { mutableStateOf(initialEatenSelection != 0) }
    val showExtraNutrients = nutritionalInfoSelection == 2
    var showHelpSheet by remember { mutableStateOf(false) }
    val helpSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val eatenHelpText = """
# **Eaten Table**
The purpose of this screen is to display a log of the foods you have consumed as well as modify it to some degree.
***
## Explanation of GUI elements
The GUI elements on the screen are (starting at the top left hand corner and working across and down):   
- The **heading** of the screen: "Eaten Table". 
- A **segmented button** with three options (Min, NIP, All). The selection is persistent between app restarts. 
    - **Min**: There are two cases:
        - when the Daily totals checkbox is **unchecked**, logs for individual foods are displayed comprising three rows:
            - The time stamp of the log (date+time)
            - The food description
            - The amount consumed (in g or mL as appropriate)
        - when the Daily totals checkbox is **checked**, logs consolidated by date are displayed comprising six rows:
            - The date of the foods time stamp
            - The text "Daily totals"
            - The total amount consumed on the day, where the amounts are summed irrespective of units. This is a bit misleading but accurate enough if the density of the liquid foods is not too far away from 1 
    - **NIP**: There are two cases:
        - when the Daily totals checkbox is **unchecked**, logs for individual foods are displayed comprising ten rows:
            - The time stamp of the log (date+time)
            - The food description
            - The amount consumed (in g or mL as appropriate)
            - The seven quantities mandated by FSANZ as the minimum required in a NIP 
        - when the Daily totals checkbox is **checked**, logs consolidated by date are displayed comprising ten rows:
            - The date of the foods time stamp
            - The text "Daily totals"
            - The total amount consumed on the day, where as before the amounts are summed irrespective of units.
            - The seven quantities mandated by FSANZ as the minimum required in a NIP, summed across all of the days food item logs.
    - **All**: There are two cases:
        - when the Daily totals checkbox is **unchecked**, logs for individual foods are displayed comprising 26 rows:
            - The time stamp of the log (date+time)
            - The food description
            - The amount consumed (in g or mL as appropriate)
            - The 23 nutrient quantities we can record in the Foods table (including Energy) 
        - when the Daily totals checkbox is **checked**, logs consolidated by date are displayed comprising 26 rows:
            - The date of the foods time stamp
            - The text "Daily totals"
            - The total amount consumed on the day, where as before the amounts are summed irrespective of units.
            - The 23 nutrient quantities we can record in the Foods table (including Energy), summed across all of the days food item logs.
- The **help button** `?` which displays this help screen.
- The **navigation button** `<-` which transfers you back to the Foods Table screen.
- A **check box** labeled "Daily totals"
    - When **unchecked** logs of individual foods eaten are displayed
    - When **checked** these logs are summed by day, giving you a daily total of each nutrient consumed (as well as Energy), even though which ones are displayed is determined by which segmented button (Min, NIP, All) is pressed. 
- The **help button** `?` which displays this help screen.     
- A **scrollable table viewer** which displays records (possibly consolidated by date) from the Eaten table. If a particular food is selected (by tapping it) a selection panel appears at the bottom of the screen. It displays the description of the selected food log followed by buttons below it:
    - **Eaten**: logs the selected food into the Eaten Table.
        - It opens a dialog box where you can specify the amount eaten as well as the date and time this has occurred (with the default being now).
        - Press the **Confirm** button when you are ready to log your food. This transfers focus to the Eaten Table screen where the just logged food will be visible.
        - You can abort this process by tapping anywhere outside the dialog box. This closes it.
    - **Edit**: allows editing of the selected food.
        - It opens a screen titled "Editing Solid Food" or "Editing Liquid Food", obviously depending on the type of food you have selected. 
    - **Add**: adds a new food record to the Foods table.
        - It opens a screen titled "Add Food".
        - The original selected food has no relevance to this activity. It is just a way of making the Add button available.   
    - **Copy**: opens a Copying Solid/Liquid Food screen with all fields pre-filled and the description suffix removed.
    - **Convert**: converts a liquid food to a solid entry by entering density (g/mL); a new food is added with converted per-100g values.
    - **Delete**: deletes the selected food from the Foods table.
        - It opens a dialog which warns you that you will be deleting the selected food from the Foods table.
        - This is irrevocable if you press the **Delete** button.
        - You can change you mind about doing this by just tapping anywhere outside the dialog box. This closes it.
***
        
# Eaten Table
- Use the Min / NIP / All toggle to change how much nutrition detail is shown.
- Tap a row to expand it; tap again to collapse.
- Long press the selection actions to edit or delete the chosen entry.
- Turn on “Display daily totals” to see aggregated amounts per day.
- The Back arrow returns to the Foods Table.
""".trimIndent()
    var displayDailyTotals by remember { mutableStateOf(initialDisplayDailyTotals) }
    val dailyTotals = remember(eatenFoods) { aggregateDailyTotals(eatenFoods) }

    BackHandler(enabled = selectedEatenFood != null) {
        selectedEatenFood = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Eaten\nTable", fontWeight = FontWeight.Bold) },
                actions = {
                    val options = listOf("Min", "NIP", "All")
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.widthIn(max = 200.dp)
                    ) {
                        options.forEachIndexed { index, label ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                                selected = nutritionalInfoSelection == index,
                                onClick = {
                                    nutritionalInfoSelection = index
                                    val shouldShowInfo = index != 0
                                    showNutritionalInfo = shouldShowInfo
                                    sharedPreferences.edit {
                                        putInt(KEY_NUTRITION_SELECTION_EATEN, index)
                                    }
                                }
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    HelpIconButton(onClick = { showHelpSheet = true })
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = displayDailyTotals,
                        onCheckedChange = {
                            displayDailyTotals = it
                            if (it) {
                                selectedEatenFood = null
                            }
                            sharedPreferences.edit {
                                putBoolean(KEY_DISPLAY_DAILY_TOTALS, it)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Daily totals",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (displayDailyTotals) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(dailyTotals) { totals ->
                            DailyTotalsCard(
                                totals = totals,
                                showNutritionalInfo = showNutritionalInfo,
                                showExtraNutrients = showExtraNutrients
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(eatenFoods) { eatenFood ->
                            EatenLogItem(
                                eatenFood = eatenFood,
                                onClick = {
                                    selectedEatenFood = if (selectedEatenFood == eatenFood) null else eatenFood
                                },
                                showNutritionalInfo = showNutritionalInfo,
                                showExtraNutrients = showExtraNutrients
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = selectedEatenFood != null && !displayDailyTotals,
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

    if (showHelpSheet) {
        HelpBottomSheet(
            helpText = eatenHelpText,
            sheetState = helpSheetState,
            onDismiss = { showHelpSheet = false }
        )
    }
}

data class DailyTotals(
    val date: String,
    val unitLabel: String,
    val amountEaten: Double,
    val energy: Double,
    val protein: Double,
    val fatTotal: Double,
    val saturatedFat: Double,
    val transFat: Double,
    val polyunsaturatedFat: Double,
    val monounsaturatedFat: Double,
    val carbohydrate: Double,
    val sugars: Double,
    val dietaryFibre: Double,
    val sodiumNa: Double,
    val calciumCa: Double,
    val potassiumK: Double,
    val thiaminB1: Double,
    val riboflavinB2: Double,
    val niacinB3: Double,
    val folate: Double,
    val ironFe: Double,
    val magnesiumMg: Double,
    val vitaminC: Double,
    val caffeine: Double,
    val cholesterol: Double,
    val alcohol: Double
)

private fun aggregateDailyTotals(eatenFoods: List<EatenFood>): List<DailyTotals> {
    return eatenFoods
        .groupBy { it.dateEaten }
        .map { (date, items) ->
            val allMl = items.all { isLiquidDescription(it.foodDescription) }
            val allGrams = items.all { !isLiquidDescription(it.foodDescription) }
            val unitLabel = when {
                allMl -> "mL"
                allGrams -> "g"
                else -> "mixed units"
            }
            DailyTotals(
                date = date,
                unitLabel = unitLabel,
                amountEaten = items.sumOf { it.amountEaten },
                energy = items.sumOf { it.energy },
                protein = items.sumOf { it.protein },
                fatTotal = items.sumOf { it.fatTotal },
                saturatedFat = items.sumOf { it.saturatedFat },
                transFat = items.sumOf { it.transFat },
                polyunsaturatedFat = items.sumOf { it.polyunsaturatedFat },
                monounsaturatedFat = items.sumOf { it.monounsaturatedFat },
                carbohydrate = items.sumOf { it.carbohydrate },
                sugars = items.sumOf { it.sugars },
                dietaryFibre = items.sumOf { it.dietaryFibre },
                sodiumNa = items.sumOf { it.sodiumNa },
                calciumCa = items.sumOf { it.calciumCa },
                potassiumK = items.sumOf { it.potassiumK },
                thiaminB1 = items.sumOf { it.thiaminB1 },
                riboflavinB2 = items.sumOf { it.riboflavinB2 },
                niacinB3 = items.sumOf { it.niacinB3 },
                folate = items.sumOf { it.folate },
                ironFe = items.sumOf { it.ironFe },
                magnesiumMg = items.sumOf { it.magnesiumMg },
                vitaminC = items.sumOf { it.vitaminC },
                caffeine = items.sumOf { it.caffeine },
                cholesterol = items.sumOf { it.cholesterol },
                alcohol = items.sumOf { it.alcohol }
            )
        }
}

@Composable
fun DailyTotalsCard(
    totals: DailyTotals,
    showNutritionalInfo: Boolean,
    showExtraNutrients: Boolean
) {
    val amountUnitSuffix = when (totals.unitLabel) {
        "mixed units" -> " (g or mL)"
        else -> " ${totals.unitLabel}"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(totals.date, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Daily totals", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(2.dp))
            if (showNutritionalInfo) {
                NutritionalInfo(
                    eatenFood = totals.toEatenFoodPlaceholder(),
                    unit = totals.unitLabel,
                    showExtraNutrients = showExtraNutrients
                )
            } else {
                val amountText = formatAmount(totals.amountEaten)
                Text(
                    "$amountText$amountUnitSuffix",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(0.5f),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Energy (kJ):", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = formatNumber(totals.energy),
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
                        text = formatNumber(totals.fatTotal),
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
                        text = formatNumber(totals.dietaryFibre),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }

}

private fun DailyTotals.toEatenFoodPlaceholder(): EatenFood {
    return EatenFood(
        eatenId = -1,
        dateEaten = date,
        timeEaten = "",
        eatenTs = 0,
        amountEaten = amountEaten,
        foodDescription = "",
        energy = energy,
        protein = protein,
        fatTotal = fatTotal,
        saturatedFat = saturatedFat,
        transFat = transFat,
        polyunsaturatedFat = polyunsaturatedFat,
        monounsaturatedFat = monounsaturatedFat,
        carbohydrate = carbohydrate,
        sugars = sugars,
        dietaryFibre = dietaryFibre,
        sodiumNa = sodiumNa,
        calciumCa = calciumCa,
        potassiumK = potassiumK,
        thiaminB1 = thiaminB1,
        riboflavinB2 = riboflavinB2,
        niacinB3 = niacinB3,
        folate = folate,
        ironFe = ironFe,
        magnesiumMg = magnesiumMg,
        vitaminC = vitaminC,
        caffeine = caffeine,
        cholesterol = cholesterol,
        alcohol = alcohol
    )
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
                val unit = descriptionUnit(eatenFood.foodDescription)
                val amountText = formatAmount(eatenFood.amountEaten)
                Text("Amount: $amountText $unit")
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(onClick = onConfirm) {
                    Text("Confirm")
                }
            }
        },
        dismissButton = {}
    )
}

@Composable
fun EatenLogItem(
    eatenFood: EatenFood,
    onClick: () -> Unit,
    showNutritionalInfo: Boolean,
    showExtraNutrients: Boolean = false
) {
    val unit = descriptionUnit(eatenFood.foodDescription)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("${eatenFood.dateEaten} ${eatenFood.timeEaten}", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(eatenFood.foodDescription, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(2.dp))
            if (showNutritionalInfo) {
                NutritionalInfo(eatenFood = eatenFood, unit = unit, showExtraNutrients = showExtraNutrients)
            } else {
                val amountText = formatAmount(eatenFood.amountEaten)
                Text("$amountText$unit", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun NutritionalInfo(
    eatenFood: EatenFood,
    unit: String,
    showExtraNutrients: Boolean = false,
    hideFibreAndCalcium: Boolean = !showExtraNutrients
) {
    val amountLabel = when (unit.lowercase(Locale.getDefault())) {
        "ml" -> "Amount (mL)"
        "g" -> "Amount (g)"
        "mixed units" -> "Amount (g or mL)"
        else -> "Amount ($unit)"
    }
    val nutrientRows = buildList {
        add(amountLabel to eatenFood.amountEaten)
        add("Energy (kJ):" to eatenFood.energy)
        add("Protein (g):" to eatenFood.protein)
        add("Fat, total (g):" to eatenFood.fatTotal)
        add("- Saturated (g):" to eatenFood.saturatedFat)
        if (showExtraNutrients) {
            add("- Trans (mg):" to eatenFood.transFat)
            add("- Polyunsaturated (g):" to eatenFood.polyunsaturatedFat)
            add("- Monounsaturated (g):" to eatenFood.monounsaturatedFat)
        }
        add("Carbohydrate (g):" to eatenFood.carbohydrate)
        add("- Sugars (g):" to eatenFood.sugars)
        if (showExtraNutrients) {
            add("Sodium (mg):" to eatenFood.sodiumNa)
            if (!hideFibreAndCalcium) {
                add("Dietary Fibre (g):" to eatenFood.dietaryFibre)
            }
            add("Calcium (mg):" to eatenFood.calciumCa)
            add("Potassium (mg):" to eatenFood.potassiumK)
            add("Thiamin B1 (mg):" to eatenFood.thiaminB1)
            add("Riboflavin B2 (mg):" to eatenFood.riboflavinB2)
            add("Niacin B3 (mg):" to eatenFood.niacinB3)
            add("Folate (ug):" to eatenFood.folate)
            add("Iron (mg):" to eatenFood.ironFe)
            add("Magnesium (mg):" to eatenFood.magnesiumMg)
            add("Vitamin C (mg):" to eatenFood.vitaminC)
            add("Caffeine (mg):" to eatenFood.caffeine)
            add("Cholesterol (mg):" to eatenFood.cholesterol)
            add("Alcohol (g):" to eatenFood.alcohol)
        } else {
            add("Sodium (mg):" to eatenFood.sodiumNa)
            if (!hideFibreAndCalcium) {
                add("Dietary Fibre (g):" to eatenFood.dietaryFibre)
                add("Calcium (mg):" to eatenFood.calciumCa)
            }
        }
    }
    Column(modifier = Modifier.padding(top = 0.dp)) {
        nutrientRows.forEach { (label, value) ->
            NutrientRow(label = label, value = value)
        }
    }
}

@Composable
private fun NutrientRow(label: String, value: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(0.5f),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = formatNumber(value),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End
        )
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
    val initialFoodSelection = remember {
        sharedPreferences.getInt(
            KEY_NUTRITION_SELECTION_FOOD,
            if (sharedPreferences.getBoolean(KEY_SHOW_NUTRITIONAL_INFO, false)) 1 else 0
        )
    }
    var showHelpSheet by remember { mutableStateOf(false) }
    val helpSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val foodsHelpText = """
# **Foods Table**
This is the main screen of the app.

Its purpose is to display a list of foods from the Foods table and allow interaction with a selected food.
***
# **Explanation of GUI elements**
The GUI elements on the screen are (starting at the top left hand corner and working across and down):   
- The **heading** of the screen: "Foods Table". 
- A **segmented button** with three options (Min, NIP, All). The selection is persistent between app restarts. 
    - **Min**: only displays the text description of food items.
    - **NIP**: additionally displays the minimum mandated nutrient information (per 100g or 100mL of the food) as required in by FSANZ on Nutritional Information Panels (NIP)
    - **All**: Displays all nutrient fields stored in the Foods table (there are 23, including Energy)
- The **help button** `?` which displays this help screen.
- The **navigation button** `->` which transfers you to the Eaten Table screen.
- A **text field** which when empty displays the text "Enter food filter text"
    - Type any text in the field and press the Enter key or equivalent. This filters the list of foods to those that contain this text anywhere in their description.
    - It is NOT case sensitive
- A **scrollable table viewer** which displays records from the Foods table. When a particular food is selected (by tapping it) a selection panel appears at the bottom of the screen. It displays the description of the selected food followed by six buttons below it:
    - **LOG**: logs the selected food into the Eaten Table.
        - It opens a dialog box where you can specify the amount eaten as well as the date and time this has occurred (with the default being now).
        - Press the **Confirm** button when you are ready to log your food. This transfers focus to the Eaten Table screen where the just logged food will be visible.
        - You can abort this process by tapping anywhere outside the dialog box. This closes it.
    - **Edit**: allows editing of the selected food.
        - It opens "Editing Solid Food" or "Editing Liquid Food" unless the description contains `{recipe=...g}`, in which case it opens "Editing Recipe".
    - **Add**: adds a new food record to the Foods table.
        - It opens a screen titled "Add Food".
        - The original selected food has no relevance to this activity. It is just a way of making the Add button available.
    - **Copy**: opens a Copying Solid/Liquid Food screen with values pre-filled and the description suffix removed.
    - **Convert**: converts a liquid food to a solid entry by entering density (g/mL); a new food is added with converted per-100g values.
    - **Delete**: deletes the selected food from the Foods table.
        - It opens a dialog which warns you that you will be deleting the selected food from the Foods table.
        - This is irrevocable if you press the **Confirm** button.
        - You can change you mind about doing this by just tapping anywhere outside the dialog box. This closes it.
***
# **Foods table structure**
```
Field name          Type    Units

FoodId              INTEGER	
FoodDescription     TEXT	
Energy              REAL    kJ
Protein             REAL    g
FatTotal            REAL    g
SaturatedFat        REAL    g
TransFat            REAL    mg
PolyunsaturatedFat  REAL    g
MonounsaturatedFat  REAL    g
Carbohydrate        REAL    g
Sugars              REAL    g
DietaryFibre        REAL    g
SodiumNa            REAL    mg
CalciumCa           REAL    mg
PotassiumK          REAL    mg
ThiaminB1           REAL    mg
RiboflavinB2        REAL    mg
NiacinB3            REAL    mg
Folate              REAL    µg
IronFe              REAL    mg
MagnesiumMg         REAL    mg
VitaminC            REAL    mg
Caffeine            REAL    mg
Cholesterol         REAL    mg
Alcohol             REAL    g
```
The FoodId field is never explicitly displayed or considered. It is a Primary Key that is auto incremented when a record is created.
The values of nutrients are per 100g or 100mL as appropriate and the units are as mandated in the FSANZ code.

- **If a FoodDescription ends in the characters " mL" or " mL#" nutrient values are per 100mL, otherwise they are per 100g**

- **If a FoodDescription ends in the characters " #" or " mL#" the food has been added manually to the database at some stage, otherwise it was part of the original base food data**   

### **Mandatory Nutrients on a NIP**
Under Standard 1.2.8 of the FSANZ Food Standards Code, most packaged foods must display a NIP showing:
- Energy (in kilojoules, and optionally kilocalories)
- Protein
- Fat (total)
- Saturated fat (listed separately from total fat)
- Carbohydrate (total)
- Sugars (listed separately from total carbohydrate)
- Sodium (a component of salt)

These values must be shown per serving and per 100 g (or 100 mL for liquids).

- **The Foods table includes these mandatory nutrients.**
 
### **When More Nutrients Are Required**
Additional nutrients must be declared if a nutrition claim is made. For example:
- If a product claims to be a “good source of fibre,” then dietary fibre must be listed.
- If a claim is made about specific fats (e.g., omega-3, cholesterol, trans fats), those must also be included.

- **The Foods table includes most such possible additional nutrients.**

### **Formatting Rules**
- Significant figures: Values must be reported to no more than three significant figures.
- Decimal places: Protein, fat, saturated fat, carbohydrate, and sugars are rounded to 1 decimal place if under 100 g. Energy and sodium are reported as whole numbers (no decimals).
- Serving size: Determined by the food business, but must be clearly stated.

- **The Foods table does not explicitly consider servings, though it might be noted in the FoodDescription text field.** 

### **Exemptions**
Some foods don’t require a NIP unless a nutrition claim is made:
- Unpackaged foods (e.g., fresh fruit, vegetables)
- Foods made and packaged at point of sale (e.g., bakery bread)
- Herbs, spices, tea, coffee, and packaged water (no significant nutritional value)

- **Notwithstanding the above the Foods table includes many such items**
***
""".trimIndent()
    var nutritionalInfoSelection by remember { mutableIntStateOf(initialFoodSelection) }
    var showNutritionalInfo by remember { mutableStateOf(initialFoodSelection != 0) }
    var selectedFood by remember { mutableStateOf<Food?>(null) }

    // State to control the visibility of our new dialog
    var showSelectDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showConvertDialog by remember { mutableStateOf(false) }

    navController.currentBackStackEntry?.savedStateHandle?.let { savedStateHandle ->
        val foodUpdated = savedStateHandle.get<Boolean>("foodUpdated") ?: false
        val foodInserted = savedStateHandle.get<Boolean>("foodInserted") ?: false
        val sortFoodsDescOnce = savedStateHandle.get<Boolean>("sortFoodsDescOnce") ?: false
        LaunchedEffect(foodUpdated) {
            if (foodUpdated) {
                foods = dbHelper.readFoodsFromDatabase()
                selectedFood = foods.find { it.foodId == selectedFood?.foodId }
                savedStateHandle.remove<Boolean>("foodUpdated")
            }
        }
        LaunchedEffect(foodInserted) {
            if (foodInserted) {
                foods = dbHelper.readFoodsFromDatabase()
                selectedFood = null
                savedStateHandle.remove<Boolean>("foodInserted")
            }
        }
        LaunchedEffect(sortFoodsDescOnce) {
            if (sortFoodsDescOnce) {
                foods = dbHelper.readFoodsSortedByIdDesc()
                savedStateHandle.remove<Boolean>("sortFoodsDescOnce")
            }
        }
    }

    BackHandler(enabled = selectedFood != null) {
        selectedFood = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Foods\nTable", fontWeight = FontWeight.Bold) },
                actions = {
                    val options = listOf("Min", "NIP", "All")
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.widthIn(max = 200.dp)
                    ) {
                        options.forEachIndexed { index, label ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                                selected = nutritionalInfoSelection == index,
                                onClick = {
                                    nutritionalInfoSelection = index
                                    val shouldShowInfo = index != 0
                                    showNutritionalInfo = shouldShowInfo
                                    sharedPreferences.edit {
                                        putInt(KEY_NUTRITION_SELECTION_FOOD, index)
                                    }
                                }
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    HelpIconButton(onClick = { showHelpSheet = true })
                    IconButton(onClick = { navController.navigate("eatenLog") }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "View Eaten Log")
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
                    showNutritionalInfo = showNutritionalInfo,
                    showExtraNutrients = nutritionalInfoSelection == 2
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
                        onEdit = {
                            if (isRecipeDescription(food.foodDescription)) {
                                navController.navigate("editRecipe/${food.foodId}")
                            } else {
                                navController.navigate("editFood/${food.foodId}")
                            }
                        },
                        onAdd = { navController.navigate("insertFood") },
                        onCopy = {
                            if (isRecipeDescription(food.foodDescription)) {
                                navController.navigate("copyRecipe/${food.foodId}")
                            } else {
                                navController.navigate("copyFood/${food.foodId}")
                            }
                        },
                        onConvert = {
                            val isLiquid = isLiquidDescription(food.foodDescription)
                            if (isLiquid) {
                                showConvertDialog = true
                            } else {
                                showPlainToast(context, "Convert is only available for liquid foods")
                            }
                        },
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
    if (showDeleteDialog) {
        selectedFood?.let { food ->
            DeleteConfirmationDialog(
                food = food,
                onDismiss = { showDeleteDialog = false },
                onConfirm = {
                    val isRecipeFood = isRecipeDescription(food.foodDescription)
                    val deletedFood = dbHelper.deleteFood(food.foodId)
                    if (isRecipeFood) {
                        dbHelper.deleteRecipesByFoodId(food.foodId)
                    }
                    if (!deletedFood) {
                        showPlainToast(context, "Failed to delete food")
                    }
                    foods = dbHelper.readFoodsFromDatabase()
                    selectedFood = null
                    showDeleteDialog = false
                }
            )
        }
    }

    if (showConvertDialog) {
        selectedFood?.let { food ->
            ConvertFoodDialog(
                food = food,
                onDismiss = { showConvertDialog = false },
                onConfirm = { density ->
                    val (baseDescription, _) = extractDescriptionParts(food.foodDescription)
                    val densityText = density.toString().trimEnd('0').trimEnd('.')
                    val newDescription = "$baseDescription {density=$densityText" + "g/mL} #"

                    val newFood = food.copy(
                        foodId = 0,
                        foodDescription = newDescription,
                        energy = food.energy / density,
                        protein = food.protein / density,
                        fatTotal = food.fatTotal / density,
                        saturatedFat = food.saturatedFat / density,
                        transFat = food.transFat / density,
                        polyunsaturatedFat = food.polyunsaturatedFat / density,
                        monounsaturatedFat = food.monounsaturatedFat / density,
                        carbohydrate = food.carbohydrate / density,
                        sugars = food.sugars / density,
                        dietaryFibre = food.dietaryFibre / density,
                        sodium = food.sodium / density,
                        calciumCa = food.calciumCa / density,
                        potassiumK = food.potassiumK / density,
                        thiaminB1 = food.thiaminB1 / density,
                        riboflavinB2 = food.riboflavinB2 / density,
                        niacinB3 = food.niacinB3 / density,
                        folate = food.folate / density,
                        ironFe = food.ironFe / density,
                        magnesiumMg = food.magnesiumMg / density,
                        vitaminC = food.vitaminC / density,
                        caffeine = food.caffeine / density,
                        cholesterol = food.cholesterol / density,
                        alcohol = food.alcohol / density
                    )

                    val inserted = dbHelper.insertFood(newFood)
                    if (inserted) {
                        foods = dbHelper.readFoodsFromDatabase()
                        selectedFood = null
                        showConvertDialog = false
                        showPlainToast(context, "Converted food added")
                    } else {
                        showPlainToast(context, "Failed to convert food")
                    }
                }
            )
        }
    }

    if (showHelpSheet) {
        HelpBottomSheet(
            helpText = foodsHelpText,
            sheetState = helpSheetState,
            onDismiss = { showHelpSheet = false }
        )
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
                    Text("Confirm")
                }
            }
        },
        dismissButton = {}
    )
}

private fun extractDescriptionParts(description: String): Pair<String, String> {
    return when {
        description.endsWith(" mL#") -> description.removeSuffix(" mL#") to " mL#"
        description.endsWith(" mL") -> description.removeSuffix(" mL") to " mL"
        description.endsWith(" #") -> description.removeSuffix(" #") to " #"
        else -> description to ""
    }
}

private fun formatOneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditFoodScreen(
    navController: NavController,
    foodId: Int
) {
    val context = LocalContext.current
    val dbHelper = remember { DatabaseHelper.getInstance(context) }
    var food by remember { mutableStateOf(dbHelper.getFoodById(foodId)) }

    if (food == null) {
        LaunchedEffect(Unit) {
            showPlainToast(context, "Food not found")
            navController.popBackStack()
        }
        return
    }

    val (initialDescription, descriptionSuffix) = remember(food) {
        extractDescriptionParts(food!!.foodDescription)
    }
    var description by remember(food) { mutableStateOf(initialDescription) }
    var energy by remember(food) { mutableStateOf(formatOneDecimal(food!!.energy)) }
    var protein by remember(food) { mutableStateOf(formatOneDecimal(food!!.protein)) }
    var fatTotal by remember(food) { mutableStateOf(formatOneDecimal(food!!.fatTotal)) }
    var saturatedFat by remember(food) { mutableStateOf(formatOneDecimal(food!!.saturatedFat)) }
    var transFat by remember(food) { mutableStateOf(formatOneDecimal(food!!.transFat)) }
    var polyunsaturatedFat by remember(food) { mutableStateOf(formatOneDecimal(food!!.polyunsaturatedFat)) }
    var monounsaturatedFat by remember(food) { mutableStateOf(formatOneDecimal(food!!.monounsaturatedFat)) }
    var carbohydrate by remember(food) { mutableStateOf(formatOneDecimal(food!!.carbohydrate)) }
    var sugars by remember(food) { mutableStateOf(formatOneDecimal(food!!.sugars)) }
    var dietaryFibre by remember(food) { mutableStateOf(formatOneDecimal(food!!.dietaryFibre)) }
    var sodium by remember(food) { mutableStateOf(formatOneDecimal(food!!.sodium)) }
    var calciumCa by remember(food) { mutableStateOf(formatOneDecimal(food!!.calciumCa)) }
    var potassiumK by remember(food) { mutableStateOf(formatOneDecimal(food!!.potassiumK)) }
    var thiaminB1 by remember(food) { mutableStateOf(formatOneDecimal(food!!.thiaminB1)) }
    var riboflavinB2 by remember(food) { mutableStateOf(formatOneDecimal(food!!.riboflavinB2)) }
    var niacinB3 by remember(food) { mutableStateOf(formatOneDecimal(food!!.niacinB3)) }
    var folate by remember(food) { mutableStateOf(formatOneDecimal(food!!.folate)) }
    var ironFe by remember(food) { mutableStateOf(formatOneDecimal(food!!.ironFe)) }
    var magnesiumMg by remember(food) { mutableStateOf(formatOneDecimal(food!!.magnesiumMg)) }
    var vitaminC by remember(food) { mutableStateOf(formatOneDecimal(food!!.vitaminC)) }
    var caffeine by remember(food) { mutableStateOf(formatOneDecimal(food!!.caffeine)) }
    var cholesterol by remember(food) { mutableStateOf(formatOneDecimal(food!!.cholesterol)) }
    var alcohol by remember(food) { mutableStateOf(formatOneDecimal(food!!.alcohol)) }

    val scrollState = rememberScrollState()
    val numericEntries = listOf(
        energy, protein, fatTotal, saturatedFat, transFat, polyunsaturatedFat, monounsaturatedFat,
        carbohydrate, sugars, dietaryFibre, sodium, calciumCa, potassiumK, thiaminB1,
        riboflavinB2, niacinB3, folate, ironFe, magnesiumMg, vitaminC, caffeine, cholesterol, alcohol
    )
    val isLiquidFood = descriptionSuffix == " mL" || descriptionSuffix == " mL#"
    val isValid = description.isNotBlank() && numericEntries.all { it.toDoubleOrNull() != null }
    var showHelpSheet by remember { mutableStateOf(false) }
    val helpSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editHelpText = if (isLiquidFood) {
        """
# Editing Liquid Food
## Editing Liquid Food
### Editing Liquid Food
- Enter *values* per 100 mL *unless* otherwise ~~noted~~.
- Keep the description clear; the app keeps the “mL” suffix for logging.
- Use decimals where `needed` and tap Confirm to save your changes.
# Editing Liquid Food
## Editing Liquid Food
### Editing Liquid Food
- Enter *values* per 100 mL *unless* otherwise ~~noted~~.
- Keep the description clear; the app keeps the “mL” suffix for logging.
- Use decimals where `needed` and tap Confirm to save your changes.
# Editing Liquid Food
## Editing Liquid Food
### Editing Liquid Food
- Enter *values* per 100 mL *unless* otherwise ~~noted~~.
- Keep the description clear; the app keeps the “mL” suffix for logging.
- Use decimals where `needed` and tap Confirm to save your changes.
# Editing Liquid Food
## Editing Liquid Food
### Editing Liquid Food
- Enter *values* per 100 mL *unless* otherwise ~~noted~~.
- Keep the description clear; the app keeps the “mL” suffix for logging.
- Use decimals where `needed` and tap Confirm to save your changes.
# Editing Liquid Food
## Editing Liquid Food
### Editing Liquid Food
- Enter *values* per 100 mL *unless* otherwise ~~noted~~.
- Keep the description clear; the app keeps the “mL” suffix for logging.
- Use decimals where `needed` and tap Confirm to save your changes.
""".trimIndent()
    } else {
        """
# Editing Solid Food
- Enter values per 100 g unless otherwise noted.
- Keep the description clear; the app keeps the “#” marker for serving size.
- Use decimals where needed and tap Confirm to save your changes.
""".trimIndent()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isLiquidFood) "Editing Liquid Food" else "Editing Solid Food", fontWeight = FontWeight.Bold) },
                actions = {
                    HelpIconButton(onClick = { showHelpSheet = true })
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = {
                        val currentFood = food ?: return@Button
                        val updatedFood = currentFood.copy(
                            foodDescription = description + descriptionSuffix,
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
                        val updated = dbHelper.updateFood(updatedFood)
                        if (updated) {
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("foodUpdated", true)
                            navController.popBackStack()
                        } else {
                            showPlainToast(context, "Failed to update food")
                        }
                    },
                    enabled = isValid
                ) {
                    Text("Confirm")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            LabeledValueField(
                label = "Description",
                value = description,
                onValueChange = { description = it },
                wrapLabel = true,
                labelSpacing = 8.dp,
                valueFillFraction = 1f
            )
            Spacer(modifier = Modifier.height(2.dp))
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
                label = "- Trans (mg)",
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
    }

    if (showHelpSheet) {
        HelpBottomSheet(
            helpText = editHelpText,
            sheetState = helpSheetState,
            onDismiss = { showHelpSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopyFoodScreen(
    navController: NavController,
    foodId: Int
) {
    val context = LocalContext.current
    val dbHelper = remember { DatabaseHelper.getInstance(context) }
    var food by remember { mutableStateOf(dbHelper.getFoodById(foodId)) }

    if (food == null) {
        LaunchedEffect(Unit) {
            showPlainToast(context, "Food not found")
            navController.popBackStack()
        }
        return
    }

    val (initialDescription, descriptionSuffix) = remember(food) {
        extractDescriptionParts(food!!.foodDescription)
    }
    val isLiquidFood = descriptionSuffix == " mL" || descriptionSuffix == " mL#"

    var description by remember(food) { mutableStateOf(initialDescription) }
    var energy by remember(food) { mutableStateOf(formatOneDecimal(food!!.energy)) }
    var protein by remember(food) { mutableStateOf(formatOneDecimal(food!!.protein)) }
    var fatTotal by remember(food) { mutableStateOf(formatOneDecimal(food!!.fatTotal)) }
    var saturatedFat by remember(food) { mutableStateOf(formatOneDecimal(food!!.saturatedFat)) }
    var transFat by remember(food) { mutableStateOf(formatOneDecimal(food!!.transFat)) }
    var polyunsaturatedFat by remember(food) { mutableStateOf(formatOneDecimal(food!!.polyunsaturatedFat)) }
    var monounsaturatedFat by remember(food) { mutableStateOf(formatOneDecimal(food!!.monounsaturatedFat)) }
    var carbohydrate by remember(food) { mutableStateOf(formatOneDecimal(food!!.carbohydrate)) }
    var sugars by remember(food) { mutableStateOf(formatOneDecimal(food!!.sugars)) }
    var dietaryFibre by remember(food) { mutableStateOf(formatOneDecimal(food!!.dietaryFibre)) }
    var sodium by remember(food) { mutableStateOf(formatOneDecimal(food!!.sodium)) }
    var calciumCa by remember(food) { mutableStateOf(formatOneDecimal(food!!.calciumCa)) }
    var potassiumK by remember(food) { mutableStateOf(formatOneDecimal(food!!.potassiumK)) }
    var thiaminB1 by remember(food) { mutableStateOf(formatOneDecimal(food!!.thiaminB1)) }
    var riboflavinB2 by remember(food) { mutableStateOf(formatOneDecimal(food!!.riboflavinB2)) }
    var niacinB3 by remember(food) { mutableStateOf(formatOneDecimal(food!!.niacinB3)) }
    var folate by remember(food) { mutableStateOf(formatOneDecimal(food!!.folate)) }
    var ironFe by remember(food) { mutableStateOf(formatOneDecimal(food!!.ironFe)) }
    var magnesiumMg by remember(food) { mutableStateOf(formatOneDecimal(food!!.magnesiumMg)) }
    var vitaminC by remember(food) { mutableStateOf(formatOneDecimal(food!!.vitaminC)) }
    var caffeine by remember(food) { mutableStateOf(formatOneDecimal(food!!.caffeine)) }
    var cholesterol by remember(food) { mutableStateOf(formatOneDecimal(food!!.cholesterol)) }
    var alcohol by remember(food) { mutableStateOf(formatOneDecimal(food!!.alcohol)) }

    val scrollState = rememberScrollState()
    val numericEntries = listOf(
        energy, protein, fatTotal, saturatedFat, transFat, polyunsaturatedFat, monounsaturatedFat,
        carbohydrate, sugars, dietaryFibre, sodium, calciumCa, potassiumK, thiaminB1,
        riboflavinB2, niacinB3, folate, ironFe, magnesiumMg, vitaminC, caffeine, cholesterol, alcohol
    )
    val isValid = description.isNotBlank() && numericEntries.all { it.toDoubleOrNull() != null }
    var showHelpSheet by remember { mutableStateOf(false) }
    val helpSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val copyHelpText = """
# Copy Food
- Review values and adjust if needed.
- The description shown omits manual markers; they are re-applied when saving.
- Tap Confirm to create a new food entry.
""".trimIndent()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isLiquidFood) "Copying Liquid Food" else "Copying Solid Food", fontWeight = FontWeight.Bold) },
                actions = {
                    HelpIconButton(onClick = { showHelpSheet = true })
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = {
                        val baseDescription = description.trimEnd()
                        val withUnit = if (isLiquidFood) "$baseDescription mL" else baseDescription
                        val processedDescription = if (withUnit.trimEnd().endsWith("#")) {
                            withUnit
                        } else if (isLiquidFood) {
                            "$withUnit#"
                        } else {
                            "$withUnit #"
                        }

                        val newFood = Food(
                            foodId = 0,
                            foodDescription = processedDescription,
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
                        val inserted = dbHelper.insertFood(newFood)
                        if (inserted) {
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("foodInserted", true)
                            navController.popBackStack()
                        } else {
                            showPlainToast(context, "Failed to copy food")
                        }
                    },
                    enabled = isValid
                ) {
                    Text("Confirm")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            LabeledValueField(
                label = "Description",
                value = description,
                onValueChange = { description = it },
                wrapLabel = true,
                labelSpacing = 8.dp,
                valueFillFraction = 1f
            )
            Spacer(modifier = Modifier.height(2.dp))
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
                label = "- Trans (mg)",
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
    }

    if (showHelpSheet) {
        HelpBottomSheet(
            helpText = copyHelpText,
            sheetState = helpSheetState,
            onDismiss = { showHelpSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsertFoodScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val dbHelper = remember { DatabaseHelper.getInstance(context) }

    var description by remember { mutableStateOf("") }
    var energy by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var fatTotal by remember { mutableStateOf("") }
    var saturatedFat by remember { mutableStateOf("") }
    var transFat by remember { mutableStateOf("") }
    var polyunsaturatedFat by remember { mutableStateOf("") }
    var monounsaturatedFat by remember { mutableStateOf("") }
    var carbohydrate by remember { mutableStateOf("") }
    var sugars by remember { mutableStateOf("") }
    var dietaryFibre by remember { mutableStateOf("") }
    var sodium by remember { mutableStateOf("") }
    var calciumCa by remember { mutableStateOf("") }
    var potassiumK by remember { mutableStateOf("") }
    var thiaminB1 by remember { mutableStateOf("") }
    var riboflavinB2 by remember { mutableStateOf("") }
    var niacinB3 by remember { mutableStateOf("") }
    var folate by remember { mutableStateOf("") }
    var ironFe by remember { mutableStateOf("") }
    var magnesiumMg by remember { mutableStateOf("") }
    var vitaminC by remember { mutableStateOf("") }
    var caffeine by remember { mutableStateOf("") }
    var cholesterol by remember { mutableStateOf("") }
    var alcohol by remember { mutableStateOf("") }

    var selectedType by remember { mutableStateOf("Solid") }
    val scrollState = rememberScrollState()
    val numericEntries = listOf(
        energy, protein, fatTotal, saturatedFat, transFat, polyunsaturatedFat, monounsaturatedFat,
        carbohydrate, sugars, dietaryFibre, sodium, calciumCa, potassiumK, thiaminB1,
        riboflavinB2, niacinB3, folate, ironFe, magnesiumMg, vitaminC, caffeine, cholesterol, alcohol
    )
    val isValid = description.isNotBlank() && numericEntries.all { it.isBlank() || it.toDoubleOrNull() != null }
    var showHelpSheet by remember { mutableStateOf(false) }
    val helpSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val insertHelpText = """
# Add Food
- Choose Solid or Liquid; the app will append a serving marker automatically.
- Enter nutrition values per 100 g or 100 mL; blanks are treated as zero.
- Use decimal values where needed; tap Confirm to save the new food.
""".trimIndent()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Food", fontWeight = FontWeight.Bold) },
                actions = {
                    HelpIconButton(onClick = { showHelpSheet = true })
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = {
                        if (selectedType == "Recipe") {
                            navController.navigate("addRecipe")
                            return@Button
                        }

                        val processedDescription = when (selectedType) {
                            "Solid" -> "$description #"
                            "Liquid" -> "$description mL#"
                            else -> description
                        }

                        val newFood = Food(
                            foodId = 0,
                            foodDescription = processedDescription,
                            energy = energy.toDoubleOrNull() ?: 0.0,
                            protein = protein.toDoubleOrNull() ?: 0.0,
                            fatTotal = fatTotal.toDoubleOrNull() ?: 0.0,
                            saturatedFat = saturatedFat.toDoubleOrNull() ?: 0.0,
                            transFat = transFat.toDoubleOrNull() ?: 0.0,
                            polyunsaturatedFat = polyunsaturatedFat.toDoubleOrNull() ?: 0.0,
                            monounsaturatedFat = monounsaturatedFat.toDoubleOrNull() ?: 0.0,
                            carbohydrate = carbohydrate.toDoubleOrNull() ?: 0.0,
                            sugars = sugars.toDoubleOrNull() ?: 0.0,
                            dietaryFibre = dietaryFibre.toDoubleOrNull() ?: 0.0,
                            sodium = sodium.toDoubleOrNull() ?: 0.0,
                            calciumCa = calciumCa.toDoubleOrNull() ?: 0.0,
                            potassiumK = potassiumK.toDoubleOrNull() ?: 0.0,
                            thiaminB1 = thiaminB1.toDoubleOrNull() ?: 0.0,
                            riboflavinB2 = riboflavinB2.toDoubleOrNull() ?: 0.0,
                            niacinB3 = niacinB3.toDoubleOrNull() ?: 0.0,
                            folate = folate.toDoubleOrNull() ?: 0.0,
                            ironFe = ironFe.toDoubleOrNull() ?: 0.0,
                            magnesiumMg = magnesiumMg.toDoubleOrNull() ?: 0.0,
                            vitaminC = vitaminC.toDoubleOrNull() ?: 0.0,
                            caffeine = caffeine.toDoubleOrNull() ?: 0.0,
                            cholesterol = cholesterol.toDoubleOrNull() ?: 0.0,
                            alcohol = alcohol.toDoubleOrNull() ?: 0.0
                        )
                        val inserted = dbHelper.insertFood(newFood)
                        if (inserted) {
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("foodInserted", true)
                            navController.popBackStack()
                        } else {
                            showPlainToast(context, "Failed to insert food")
                        }
                    },
                    enabled = isValid
                ) {
                    Text("Confirm")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedType == "Solid", onClick = { selectedType = "Solid" })
                    Text("Solid", style = MaterialTheme.typography.bodyLarge)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedType == "Liquid", onClick = { selectedType = "Liquid" })
                    Text("Liquid", style = MaterialTheme.typography.bodyLarge)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedType == "Recipe",
                        onClick = {
                            selectedType = "Recipe"
                            navController.navigate("addRecipe")
                        }
                    )
                    Text("Recipe", style = MaterialTheme.typography.bodyLarge)
                }
            }
            LabeledValueField(
                label = "Description",
                value = description,
                onValueChange = { description = it },
                wrapLabel = true,
                labelSpacing = 8.dp,
                valueFillFraction = 1f
            )
            Spacer(modifier = Modifier.height(2.dp))
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
                label = "- Trans (mg)",
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
    }

    if (showHelpSheet) {
        HelpBottomSheet(
            helpText = insertHelpText,
            sheetState = helpSheetState,
            onDismiss = { showHelpSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecipeScreen(
    navController: NavController,
    screenTitle: String = "Add Recipe",
    initialDescription: String = "",
    editingFoodId: Int? = null
) {
    val context = LocalContext.current
    val dbHelper = remember { DatabaseHelper.getInstance(context) }
    var showHelpSheet by remember { mutableStateOf(false) }
    val helpSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val recipeHelpText = """
# Add Recipe
- Placeholder help for recipe creation.
- More detailed guidance will be added when the feature is implemented.
""".trimIndent()

    var description by remember(initialDescription) { mutableStateOf(initialDescription) }
    var searchQuery by remember { mutableStateOf("") }
    var foods by remember { mutableStateOf(dbHelper.readFoodsFromDatabase()) }
    val loadRecipes = remember(editingFoodId, dbHelper) {
        {
            editingFoodId?.let { dbHelper.readCopiedRecipes(it) } ?: dbHelper.readRecipes()
        }
    }
    var recipes by remember(editingFoodId) { mutableStateOf(loadRecipes()) }
    var selectedFood by remember { mutableStateOf<Food?>(null) }
    var selectedRecipe by remember { mutableStateOf<RecipeItem?>(null) }
    var showCannotAddDialog by remember { mutableStateOf(false) }
    var showRecipeAmountDialog by remember { mutableStateOf(false) }
    var showEditRecipeAmountDialog by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val exitAddRecipe: () -> Unit = {
        dbHelper.deleteRecipesWithFoodIdZero()
        dbHelper.deleteAllCopiedRecipes()
        val popped = navController.popBackStack("foodSearch", inclusive = false)
        if (!popped) {
            navController.popBackStack()
        }
    }

    LaunchedEffect(navController.currentBackStackEntry) {
        navController.currentBackStackEntry?.savedStateHandle?.let { savedStateHandle ->
            val foodUpdated = savedStateHandle.get<Boolean>("foodUpdated") ?: false
            val foodInserted = savedStateHandle.get<Boolean>("foodInserted") ?: false
            if (foodUpdated || foodInserted) {
                foods = dbHelper.readFoodsFromDatabase()
                selectedFood = if (foodUpdated) foods.find { it.foodId == selectedFood?.foodId } else null
                savedStateHandle.remove<Boolean>("foodUpdated")
                savedStateHandle.remove<Boolean>("foodInserted")
            }
        }
    }

    LaunchedEffect(editingFoodId) {
        if (editingFoodId != null) {
            val copied = dbHelper.copyRecipesForFood(editingFoodId)
            if (!copied) {
                showPlainToast(context, "Unable to prepare recipe items for editing")
            }
            recipes = loadRecipes()
        } else {
            recipes = loadRecipes()
        }
    }

    BackHandler {
        if (showHelpSheet) {
            showHelpSheet = false
        } else if (selectedFood != null) {
            selectedFood = null
            showCannotAddDialog = false
            showRecipeAmountDialog = false
        } else if (selectedRecipe != null) {
            selectedRecipe = null
            showEditRecipeAmountDialog = false
        } else {
            exitAddRecipe()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle, fontWeight = FontWeight.Bold) },
                actions = {
                    HelpIconButton(onClick = { showHelpSheet = true })
                    IconButton(onClick = exitAddRecipe) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(onClick = {
                    val currentRecipes = recipes
                    val totalAmount = currentRecipes.sumOf { it.amount }
                    if (description.isBlank()) {
                        showPlainToast(context, "Please enter a description")
                        return@Button
                    }
                    if (totalAmount <= 0.0) {
                        showPlainToast(context, "Add at least one ingredient")
                        return@Button
                    }

                    val totalEnergy = currentRecipes.sumOf { it.energy }
                    val totalProtein = currentRecipes.sumOf { it.protein }
                    val totalFat = currentRecipes.sumOf { it.fatTotal }
                    val totalSaturated = currentRecipes.sumOf { it.saturatedFat }
                    val totalTrans = currentRecipes.sumOf { it.transFat }
                    val totalPoly = currentRecipes.sumOf { it.polyunsaturatedFat }
                    val totalMono = currentRecipes.sumOf { it.monounsaturatedFat }
                    val totalCarb = currentRecipes.sumOf { it.carbohydrate }
                    val totalSugars = currentRecipes.sumOf { it.sugars }
                    val totalFibre = currentRecipes.sumOf { it.dietaryFibre }
                    val totalSodium = currentRecipes.sumOf { it.sodiumNa }
                    val totalCalcium = currentRecipes.sumOf { it.calciumCa }
                    val totalPotassium = currentRecipes.sumOf { it.potassiumK }
                    val totalThiamin = currentRecipes.sumOf { it.thiaminB1 }
                    val totalRiboflavin = currentRecipes.sumOf { it.riboflavinB2 }
                    val totalNiacin = currentRecipes.sumOf { it.niacinB3 }
                    val totalFolate = currentRecipes.sumOf { it.folate }
                    val totalIron = currentRecipes.sumOf { it.ironFe }
                    val totalMagnesium = currentRecipes.sumOf { it.magnesiumMg }
                    val totalVitaminC = currentRecipes.sumOf { it.vitaminC }
                    val totalCaffeine = currentRecipes.sumOf { it.caffeine }
                    val totalCholesterol = currentRecipes.sumOf { it.cholesterol }
                    val totalAlcohol = currentRecipes.sumOf { it.alcohol }

                    val scale = 100.0 / totalAmount
                    fun scaled(sum: Double) = sum * scale

                    val recipeWeightText = formatNumber(totalAmount, decimals = 0)
                    val baseFood = Food(
                        foodId = 0,
                        foodDescription = "${description.trim()} {recipe=${recipeWeightText}g}",
                        energy = scaled(totalEnergy),
                        protein = scaled(totalProtein),
                        fatTotal = scaled(totalFat),
                        saturatedFat = scaled(totalSaturated),
                        transFat = scaled(totalTrans),
                        polyunsaturatedFat = scaled(totalPoly),
                        monounsaturatedFat = scaled(totalMono),
                        carbohydrate = scaled(totalCarb),
                        sugars = scaled(totalSugars),
                        dietaryFibre = scaled(totalFibre),
                        sodium = scaled(totalSodium),
                        calciumCa = scaled(totalCalcium),
                        potassiumK = scaled(totalPotassium),
                        thiaminB1 = scaled(totalThiamin),
                        riboflavinB2 = scaled(totalRiboflavin),
                        niacinB3 = scaled(totalNiacin),
                        folate = scaled(totalFolate),
                        ironFe = scaled(totalIron),
                        magnesiumMg = scaled(totalMagnesium),
                        vitaminC = scaled(totalVitaminC),
                        caffeine = scaled(totalCaffeine),
                        cholesterol = scaled(totalCholesterol),
                        alcohol = scaled(totalAlcohol)
                    )

                    if (editingFoodId == null) {
                        val newFoodId = dbHelper.insertFoodReturningId(baseFood)
                        if (newFoodId == null) {
                            showPlainToast(context, "Unable to save recipe to Foods table")
                            return@Button
                        }

                        val updated = dbHelper.updateRecipeFoodIdForTemporaryRecords(newFoodId)
                        if (!updated) {
                            showPlainToast(context, "Recipe items not linked to new food")
                            dbHelper.deleteRecipesWithFoodIdZero()
                        }

                        // Refresh recipe list (should now be empty) and mark Foods screen to sort once.
                        recipes = loadRecipes()
                        val foodSearchEntry = runCatching { navController.getBackStackEntry("foodSearch") }.getOrNull()
                        foodSearchEntry?.savedStateHandle?.set("foodInserted", true)
                        foodSearchEntry?.savedStateHandle?.set("sortFoodsDescOnce", true)

                        val popped = navController.popBackStack("foodSearch", inclusive = false)
                        if (!popped) {
                            navController.navigate("foodSearch") {
                                popUpTo("foodSearch") { inclusive = true }
                            }
                        }
                    } else {
                        val updatedFood = baseFood.copy(foodId = editingFoodId)
                        val updatedFoodSuccess = dbHelper.updateFood(updatedFood)
                        if (!updatedFoodSuccess) {
                            showPlainToast(context, "Unable to update recipe food")
                            return@Button
                        }
                        val replaced = dbHelper.replaceOriginalRecipesWithCopies(editingFoodId)
                        if (!replaced) {
                            showPlainToast(context, "Unable to update recipe items")
                            return@Button
                        }

                        recipes = loadRecipes()
                        val foodSearchEntry = runCatching { navController.getBackStackEntry("foodSearch") }.getOrNull()
                        foodSearchEntry?.savedStateHandle?.set("foodUpdated", true)

                        val popped = navController.popBackStack("foodSearch", inclusive = false)
                        if (!popped) {
                            navController.navigate("foodSearch") {
                                popUpTo("foodSearch") { inclusive = true }
                            }
                        }
                    }
                }) {
                    Text("Confirm")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    LabeledValueField(
                        label = "Description",
                        value = description,
                        onValueChange = { description = it },
                        wrapLabel = true,
                        labelSpacing = 8.dp,
                        valueFillFraction = 1f
                    )
                }
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
                        selectedFood = food
                        selectedRecipe = null
                        val isLiquid = isLiquidDescription(food.foodDescription)
                        if (isLiquid) {
                            showCannotAddDialog = true
                            showRecipeAmountDialog = false
                        } else {
                            showRecipeAmountDialog = true
                            showCannotAddDialog = false
                        }
                    },
                    showNutritionalInfo = false,
                    showExtraNutrients = false,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                val totalRecipeAmount = recipes.sumOf { it.amount }
                Text(
                    text = "Ingredients ${formatAmount(totalRecipeAmount, decimals = 1)} (g) Total",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
                RecipeList(
                    recipes = recipes,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    onRecipeClicked = { recipe ->
                        selectedRecipe = if (selectedRecipe?.recipeId == recipe.recipeId) null else recipe
                    },
                    selectedRecipeId = selectedRecipe?.recipeId
                )
            }

            AnimatedVisibility(
                visible = selectedRecipe != null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            ) {
                selectedRecipe?.let { recipe ->
                    RecipeSelectionPanel(
                        recipe = recipe,
                        onEdit = { showEditRecipeAmountDialog = true },
                        onDelete = {
                            val deleted = dbHelper.deleteRecipe(recipe.recipeId)
                            if (!deleted) {
                                showPlainToast(context, "Unable to delete recipe item")
                            }
                            recipes = loadRecipes()
                            selectedRecipe = null
                        }
                    )
                }
            }
        }
    }

    if (showHelpSheet) {
        HelpBottomSheet(
            helpText = recipeHelpText,
            sheetState = helpSheetState,
            onDismiss = { showHelpSheet = false }
        )
    }

    if (showCannotAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showCannotAddDialog = false
                selectedFood = null
            },
            title = {
                Text(
                    text = "CANNOT ADD THIS FOOD",
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text("Only foods measured in grams can be added to a recipe") },
            confirmButton = {
                Button(
                    onClick = {
                        showCannotAddDialog = false
                        selectedFood = null
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {}
        )
    }

    if (showRecipeAmountDialog) {
        selectedFood?.let { food ->
                    RecipeAmountDialog(
                        food = food,
                        onDismiss = {
                            showRecipeAmountDialog = false
                            selectedFood = null
                        },
                        onConfirm = { amount ->
                            val inserted = dbHelper.insertRecipeFromFood(
                                food = food,
                                amount = amount,
                                foodId = editingFoodId ?: 0,
                                copyFlag = if (editingFoodId != null) 1 else 0
                            )
                            if (!inserted) {
                                showPlainToast(context, "Unable to add item to recipe")
                            } else {
                                recipes = loadRecipes()
                                selectedRecipe = null
                            }
                            showRecipeAmountDialog = false
                            selectedFood = null
                        }
            )
        }
    }

    if (showEditRecipeAmountDialog) {
        selectedRecipe?.let { recipe ->
            RecipeAmountDialog(
                food = recipe.toFoodPlaceholder(),
                onDismiss = {
                    showEditRecipeAmountDialog = false
                    selectedRecipe = null
                },
                onConfirm = { newAmount ->
                    val amountDouble = newAmount.toDouble()
                    if (amountDouble <= 0.0) {
                        showPlainToast(context, "Amount must be greater than zero")
                        return@RecipeAmountDialog
                    }
                    val factor = if (recipe.amount == 0.0) 0.0 else amountDouble / recipe.amount
                    fun Double.scale() = (this * factor * 100).roundToInt() / 100.0

                    val updatedRecipe = recipe.copy(
                        amount = amountDouble,
                        energy = recipe.energy.scale(),
                        protein = recipe.protein.scale(),
                        fatTotal = recipe.fatTotal.scale(),
                        saturatedFat = recipe.saturatedFat.scale(),
                        transFat = recipe.transFat.scale(),
                        polyunsaturatedFat = recipe.polyunsaturatedFat.scale(),
                        monounsaturatedFat = recipe.monounsaturatedFat.scale(),
                        carbohydrate = recipe.carbohydrate.scale(),
                        sugars = recipe.sugars.scale(),
                        dietaryFibre = recipe.dietaryFibre.scale(),
                        sodiumNa = recipe.sodiumNa.scale(),
                        calciumCa = recipe.calciumCa.scale(),
                        potassiumK = recipe.potassiumK.scale(),
                        thiaminB1 = recipe.thiaminB1.scale(),
                        riboflavinB2 = recipe.riboflavinB2.scale(),
                        niacinB3 = recipe.niacinB3.scale(),
                        folate = recipe.folate.scale(),
                        ironFe = recipe.ironFe.scale(),
                        magnesiumMg = recipe.magnesiumMg.scale(),
                        vitaminC = recipe.vitaminC.scale(),
                        caffeine = recipe.caffeine.scale(),
                        cholesterol = recipe.cholesterol.scale(),
                        alcohol = recipe.alcohol.scale()
                    )

                    val updated = dbHelper.updateRecipe(updatedRecipe)
                    if (!updated) {
                        showPlainToast(context, "Unable to update recipe item")
                    }
                    recipes = loadRecipes()
                    selectedRecipe = null
                    showEditRecipeAmountDialog = false
                }
            )
        }
    }
}

@Composable
fun EditRecipeScreen(navController: NavController, foodId: Int) {
    val context = LocalContext.current
    val dbHelper = remember { DatabaseHelper.getInstance(context) }
    var food by remember { mutableStateOf(dbHelper.getFoodById(foodId)) }

    if (food == null) {
        LaunchedEffect(Unit) {
            showPlainToast(context, "Food not found")
            navController.popBackStack()
        }
        return
    }

    val initialDescription = remember(food) {
        removeRecipeMarker(food!!.foodDescription)
    }

    AddRecipeScreen(
        navController = navController,
        screenTitle = "Editing Recipe",
        initialDescription = initialDescription,
        editingFoodId = foodId
    )
}

@Composable
fun CopyRecipeScreen(navController: NavController, foodId: Int) {
    val context = LocalContext.current
    val dbHelper = remember { DatabaseHelper.getInstance(context) }
    var food by remember { mutableStateOf(dbHelper.getFoodById(foodId)) }

    if (food == null) {
        LaunchedEffect(Unit) {
            showPlainToast(context, "Food not found")
            navController.popBackStack()
        }
        return
    }

    val initialDescription = remember(food) {
        removeRecipeMarker(food!!.foodDescription)
    }

    AddRecipeScreen(
        navController = navController,
        screenTitle = "Copying Recipe",
        initialDescription = initialDescription
    )
}

@Composable
fun RecipeSelectionPanel(
    recipe: RecipeItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = recipe.foodDescription, fontWeight = FontWeight.Bold)
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

private fun RecipeItem.toFoodPlaceholder(): Food {
    return Food(
        foodId = foodId,
        foodDescription = foodDescription,
        energy = energy,
        protein = protein,
        fatTotal = fatTotal,
        saturatedFat = saturatedFat,
        transFat = transFat,
        polyunsaturatedFat = polyunsaturatedFat,
        monounsaturatedFat = monounsaturatedFat,
        carbohydrate = carbohydrate,
        sugars = sugars,
        dietaryFibre = dietaryFibre,
        sodium = sodiumNa,
        calciumCa = calciumCa,
        potassiumK = potassiumK,
        thiaminB1 = thiaminB1,
        riboflavinB2 = riboflavinB2,
        niacinB3 = niacinB3,
        folate = folate,
        ironFe = ironFe,
        magnesiumMg = magnesiumMg,
        vitaminC = vitaminC,
        caffeine = caffeine,
        cholesterol = cholesterol,
        alcohol = alcohol
    )
}

@Composable
private fun LabeledValueField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    labelWeight: Float = 1f,
    valueWeight: Float = 1f,
    wrapLabel: Boolean = false,
    labelSpacing: Dp = 0.dp,
    valueFillFraction: Float = 0.5f
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val labelModifier = if (wrapLabel) {
            Modifier.padding(end = labelSpacing)
        } else {
            Modifier.weight(labelWeight)
        }
        Text(
            text = label,
            modifier = labelModifier,
            style = MaterialTheme.typography.bodyLarge
        )
        Box(
            modifier = Modifier
                .weight(valueWeight)
                .wrapContentWidth(Alignment.Start)
        ) {
            CompactTextField(
                value = value,
                onValueChange = onValueChange,
                keyboardType = keyboardType,
                modifier = Modifier.fillMaxWidth(valueFillFraction)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val textColor = MaterialTheme.colorScheme.onSurface
    val colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = textColor,
        unfocusedTextColor = textColor
    )
    val shape = OutlinedTextFieldDefaults.shape

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier
            .fillMaxWidth()
    ) { innerTextField ->
        OutlinedTextFieldDefaults.DecorationBox(
            value = value,
            innerTextField = innerTextField,
            enabled = true,
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            interactionSource = interactionSource,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            container = {
                OutlinedTextFieldDefaults.Container(
                    enabled = true,
                    isError = false,
                    interactionSource = interactionSource,
                    colors = colors,
                    shape = shape
                )
            },
            colors = colors
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpBottomSheet(
    helpText: String,
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            MarkdownText(helpText)
        }
    }
}

private val markdownExtensions: List<Extension> = listOf(
    StrikethroughExtension.create()
)

private val markdownParser: Parser = Parser.builder()
    .extensions(markdownExtensions)
    .build()

@Composable
private fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    val document = remember(text) { markdownParser.parse(text) }
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val codeTextColor = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        RenderMarkdownChildren(
            parent = document,
            codeBackground = codeBackground,
            codeTextColor = codeTextColor,
            indentLevel = 0
        )
    }
}

@Composable
private fun RenderMarkdownChildren(
    parent: Node,
    codeBackground: Color,
    codeTextColor: Color,
    indentLevel: Int
) {
    var child = parent.firstChild
    while (child != null) {
        RenderMarkdownNode(
            node = child,
            codeBackground = codeBackground,
            codeTextColor = codeTextColor,
            indentLevel = indentLevel
        )
        if (child.next != null && parent !is ListItem) {
            Spacer(modifier = Modifier.height(8.dp))
        }
        child = child.next
    }
}

@Composable
private fun RenderMarkdownNode(
    node: Node,
    codeBackground: Color,
    codeTextColor: Color,
    indentLevel: Int
) {
    when (node) {
        is Heading -> RenderHeading(node, codeBackground, codeTextColor)
        is Paragraph -> RenderParagraph(node, codeBackground, codeTextColor)
        is BlockQuote -> RenderBlockQuote(node, codeBackground, codeTextColor, indentLevel)
        is BulletList -> RenderBulletList(node, codeBackground, codeTextColor, indentLevel)
        is OrderedList -> RenderOrderedList(node, codeBackground, codeTextColor, indentLevel)
        is FencedCodeBlock -> RenderCodeBlock(node.literal, indentLevel, codeBackground, codeTextColor)
        is IndentedCodeBlock -> RenderCodeBlock(node.literal, indentLevel, codeBackground, codeTextColor)
        is ThematicBreak -> HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        else -> RenderMarkdownChildren(node, codeBackground, codeTextColor, indentLevel)
    }
}

@Composable
private fun RenderHeading(
    heading: Heading,
    codeBackground: Color,
    codeTextColor: Color
) {
    val annotatedString = buildAnnotatedStringFrom(
        node = heading,
        codeBackground = codeBackground,
        codeTextColor = codeTextColor
    )
    val style = when (heading.level) {
        1 -> MaterialTheme.typography.titleLarge
        2 -> MaterialTheme.typography.titleMedium
        3 -> MaterialTheme.typography.titleSmall
        else -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
    }
    MarkdownTextContent(annotatedString, style)
}

@Composable
private fun RenderParagraph(
    paragraph: Paragraph,
    codeBackground: Color,
    codeTextColor: Color
) {
    val annotatedString = buildAnnotatedStringFrom(
        node = paragraph,
        codeBackground = codeBackground,
        codeTextColor = codeTextColor
    )
    MarkdownTextContent(
        annotatedString = annotatedString,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun RenderBlockQuote(
    blockQuote: BlockQuote,
    codeBackground: Color,
    codeTextColor: Color,
    indentLevel: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (indentLevel * 12).dp)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            RenderMarkdownChildren(
                parent = blockQuote,
                codeBackground = codeBackground,
                codeTextColor = codeTextColor,
                indentLevel = indentLevel + 1
            )
        }
    }
}

@Composable
private fun RenderBulletList(
    list: BulletList,
    codeBackground: Color,
    codeTextColor: Color,
    indentLevel: Int
) {
    Column(modifier = Modifier.padding(start = (indentLevel * 12).dp)) {
        var item = list.firstChild as? ListItem
        while (item != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 8.dp, top = 2.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    RenderMarkdownChildren(
                        parent = item,
                        codeBackground = codeBackground,
                        codeTextColor = codeTextColor,
                        indentLevel = indentLevel + 1
                    )
                }
            }
            if (!list.isTight && item.next != null) {
                Spacer(modifier = Modifier.height(6.dp))
            }
            item = item.next as? ListItem
        }
    }
}

@Composable
private fun RenderOrderedList(
    list: OrderedList,
    codeBackground: Color,
    codeTextColor: Color,
    indentLevel: Int
) {
    var number = orderedListStartNumber(list)
    Column(modifier = Modifier.padding(start = (indentLevel * 12).dp)) {
        var item = list.firstChild as? ListItem
        while (item != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "${number}.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .widthIn(min = 28.dp)
                        .padding(top = 2.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    RenderMarkdownChildren(
                        parent = item,
                        codeBackground = codeBackground,
                        codeTextColor = codeTextColor,
                        indentLevel = indentLevel + 1
                    )
                }
            }
            if (!list.isTight && item.next != null) {
                Spacer(modifier = Modifier.height(6.dp))
            }
            number += 1
            item = item.next as? ListItem
        }
    }
}

@Suppress("DEPRECATION")
private fun orderedListStartNumber(list: OrderedList): Int = list.startNumber

@Composable
private fun RenderCodeBlock(
    code: String,
    indentLevel: Int,
    codeBackground: Color,
    codeTextColor: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (indentLevel * 12).dp),
        shape = RoundedCornerShape(8.dp),
        color = codeBackground
    ) {
        Text(
            text = code.trimEnd(),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = codeTextColor
            ),
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun MarkdownTextContent(
    annotatedString: AnnotatedString,
    style: TextStyle
) {
    Text(
        text = annotatedString,
        style = style.copy(color = MaterialTheme.colorScheme.onSurface)
    )
}

private fun buildAnnotatedStringFrom(
    node: Node,
    codeBackground: Color,
    codeTextColor: Color
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    appendInlineChildren(node, builder, codeBackground, codeTextColor)
    return builder.toAnnotatedString()
}

private fun appendInlineChildren(
    node: Node,
    builder: AnnotatedString.Builder,
    codeBackground: Color,
    codeTextColor: Color
) {
    var child = node.firstChild
    while (child != null) {
        when (child) {
            is MdText -> builder.append(child.literal)
            is SoftLineBreak -> builder.append(" ")
            is HardLineBreak -> builder.append("\n")
            is Emphasis -> {
                val start = builder.length
                appendInlineChildren(child, builder, codeBackground, codeTextColor)
                builder.addStyle(
                    style = SpanStyle(fontStyle = FontStyle.Italic),
                    start = start,
                    end = builder.length
                )
            }
            is StrongEmphasis -> {
                val start = builder.length
                appendInlineChildren(child, builder, codeBackground, codeTextColor)
                builder.addStyle(
                    style = SpanStyle(fontWeight = FontWeight.Bold),
                    start = start,
                    end = builder.length
                )
            }
            is Code -> {
                val start = builder.length
                builder.append(child.literal)
                builder.addStyle(
                    style = SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBackground,
                        color = codeTextColor
                    ),
                    start = start,
                    end = builder.length
                )
            }
            is Strikethrough -> {
                val start = builder.length
                appendInlineChildren(child, builder, codeBackground, codeTextColor)
                builder.addStyle(
                    style = SpanStyle(textDecoration = TextDecoration.LineThrough),
                    start = start,
                    end = builder.length
                )
            }
            else -> appendInlineChildren(child, builder, codeBackground, codeTextColor)
        }
        child = child.next
    }
}

@Composable
private fun HelpIconButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Help,
            contentDescription = "Help",
            modifier = Modifier.size(18.dp)
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
    var selectedDateTime by remember { mutableLongStateOf(initialDateTime) }
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

    val foodUnit = descriptionUnit(eatenFood.foodDescription)
    val displayName = descriptionDisplayName(eatenFood.foodDescription)

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
    var selectedDateTime by remember { mutableLongStateOf(calendar.timeInMillis) }
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

    val foodUnit = descriptionUnit(food.foodDescription)
    val displayName = descriptionDisplayName(food.foodDescription)

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
fun RecipeAmountDialog(
    food: Food,
    onDismiss: () -> Unit,
    onConfirm: (amount: Float) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    val foodUnit = descriptionUnit(food.foodDescription)
    val displayName = descriptionDisplayName(food.foodDescription)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold) },
        text = {
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
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = {
                        val finalAmount = amount.toFloatOrNull() ?: 0f
                        onConfirm(finalAmount)
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
fun ConvertFoodDialog(
    food: Food,
    onDismiss: () -> Unit,
    onConfirm: (density: Double) -> Unit
) {
    var densityText by remember { mutableStateOf("") }
    val isValid = densityText.toDoubleOrNull()?.let { it > 0 } == true
    val displayName = descriptionDisplayName(food.foodDescription)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "Enter density to convert this liquid into a solid food.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = densityText,
                        onValueChange = { densityText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Density") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = "g/mL")
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
                        val density = densityText.toDoubleOrNull() ?: return@Button
                        onConfirm(density)
                    },
                    enabled = isValid
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
    onAdd: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit = {},
    onConvert: () -> Unit = {}
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
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onSelect) { Text("LOG") }
                Button(onClick = onEdit) { Text("Edit") }
                Button(onClick = onAdd) { Text("Add") }
                Button(onClick = onCopy) { Text("Copy") }
                Button(onClick = onConvert) { Text("Convert") }
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
