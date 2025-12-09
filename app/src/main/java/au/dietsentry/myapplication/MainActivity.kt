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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextLayoutResult
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
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
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

private const val PREFS_NAME = "DietSentryPrefs"
private const val KEY_SHOW_NUTRITIONAL_INFO = "showNutritionalInfo" // legacy boolean; kept for fallback
private const val KEY_NUTRITION_SELECTION_FOOD = "nutritionSelectionFood"
private const val KEY_NUTRITION_SELECTION_EATEN = "nutritionSelectionEaten"
private const val KEY_DISPLAY_DAILY_TOTALS = "displayDailyTotals"

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
                    composable("insertFood") {
                        InsertFoodScreen(navController = navController)
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
                        text = "Display daily totals",
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
    val mlRegex = Regex("mL#?$", RegexOption.IGNORE_CASE)
    return eatenFoods
        .groupBy { it.dateEaten }
        .map { (date, items) ->
            val allMl = items.all { mlRegex.containsMatchIn(it.foodDescription) }
            val allGrams = items.all { !mlRegex.containsMatchIn(it.foodDescription) }
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
                    showExtraNutrients = showExtraNutrients,
                    hideFibreAndCalcium = !showExtraNutrients
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
                val unit = if (Regex("mL#?$", RegexOption.IGNORE_CASE).containsMatchIn(eatenFood.foodDescription)) "mL" else "g"
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
                    Text("Delete")
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
    val unit = if (Regex("mL#?$", RegexOption.IGNORE_CASE).containsMatchIn(eatenFood.foodDescription)) "mL" else "g"
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
    hideFibreAndCalcium: Boolean = false
) {
    val amountLabel = when (unit.lowercase(Locale.getDefault())) {
        "ml" -> "Amount (mL)"
        "g" -> "Amount (g)"
        "mixed units" -> "Amount (g or mL)"
        else -> "Amount ($unit)"
    }
    Column(modifier = Modifier.padding(top = 0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(0.5f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = amountLabel, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = formatNumber(eatenFood.amountEaten),
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
                text = formatNumber(eatenFood.energy),
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
                text = formatNumber(eatenFood.protein),
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
                text = formatNumber(eatenFood.fatTotal),
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
                text = formatNumber(eatenFood.saturatedFat),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End
            )
        }
        if (showExtraNutrients) {
            Row(
                modifier = Modifier.fillMaxWidth(0.5f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "- Trans (mg):", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = formatNumber(eatenFood.transFat),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(0.5f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "- Polyunsaturated (g):", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = formatNumber(eatenFood.polyunsaturatedFat),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(0.5f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "- Monounsaturated (g):", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = formatNumber(eatenFood.monounsaturatedFat),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(0.5f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Carbohydrate (g):", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = formatNumber(eatenFood.carbohydrate),
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
                text = formatNumber(eatenFood.sugars),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End
            )
        }
        if (showExtraNutrients) {
            Row(
                modifier = Modifier.fillMaxWidth(0.5f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Sodium (mg):", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = formatNumber(eatenFood.sodiumNa),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End
                )
            }
            if (!hideFibreAndCalcium) {
                Row(
                    modifier = Modifier.fillMaxWidth(0.5f),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Dietary Fibre (g):", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = formatNumber(eatenFood.dietaryFibre),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.End
                    )
                }
            }
        } else {
            if (!hideFibreAndCalcium) {
                Row(
                    modifier = Modifier.fillMaxWidth(0.5f),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Dietary Fibre (g):", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = formatNumber(eatenFood.dietaryFibre),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.End
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(0.5f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Sodium (mg):", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = formatNumber(eatenFood.sodiumNa),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End
                )
            }
        }
        if (showExtraNutrients) {
            Row(
                modifier = Modifier.fillMaxWidth(0.5f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Calcium (mg):", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = formatNumber(eatenFood.calciumCa),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(0.5f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Potassium (mg):", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = formatNumber(eatenFood.potassiumK),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(0.5f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Thiamin B1 (mg):", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = formatNumber(eatenFood.thiaminB1),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(0.5f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Riboflavin B2 (mg):", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = formatNumber(eatenFood.riboflavinB2),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(0.5f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Niacin B3 (mg):", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = formatNumber(eatenFood.niacinB3),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(0.5f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Folate (ug):", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = formatNumber(eatenFood.folate),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(0.5f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Iron (mg):", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = formatNumber(eatenFood.ironFe),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(0.5f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Magnesium (mg):", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = formatNumber(eatenFood.magnesiumMg),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(0.5f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Vitamin C (mg):", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = formatNumber(eatenFood.vitaminC),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(0.5f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Caffeine (mg):", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = formatNumber(eatenFood.caffeine),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(0.5f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Cholesterol (mg):", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = formatNumber(eatenFood.cholesterol),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(0.5f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Alcohol (g):", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = formatNumber(eatenFood.alcohol),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End
                )
            }
        } else if (!hideFibreAndCalcium) {
            Row(
                modifier = Modifier.fillMaxWidth(0.5f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Calcium (mg):", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = formatNumber(eatenFood.calciumCa),
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
                    text = formatNumber(eatenFood.dietaryFibre),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End
                )
            }
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
    val initialFoodSelection = remember {
        sharedPreferences.getInt(
            KEY_NUTRITION_SELECTION_FOOD,
            if (sharedPreferences.getBoolean(KEY_SHOW_NUTRITIONAL_INFO, false)) 1 else 0
        )
    }
    var showHelpSheet by remember { mutableStateOf(false) }
    val helpSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val foodsHelpText = """
DietSentry4Android
***
# Foods Table
This is the main screen of the app.

It purpose is to display a list of foods in the database, and allow interaction with a selected food.
***
The GUI elements on the screen are (starting at the top left hand corner and working across and down):   
- The `heading`/name of the screen: "Foods Table". 
- A `segmented button` with three options (Min, NIP, All). The selection is persistent between app restarts. 
  - Min: only displays the text description for food items.
  - NIP: in addition displays the minimum mandated nutrient information (per 100g or 100mL of the food) as required in Australia on their Nutritional Information Panels (NIP)
  - All: Displays all nutrient fields stored in the Foods table (23 which includes Energy)
- The `help button` ? which displays this help screen.
- The `navigation button` -> which transfers you to the Eaten Table screen.
- A `text field` which when empty/reset displays the text "Enter food filter text"
  - Type any text in the field (and press the Enter key or equivalent) to filter the list of foods visible below by their Description.
  - It is NOT case sensitive
- A `scrollable table viewer` which display records from the Foods table.
  - When a particular food is selected (by tapping it) a selection panel appears at the bottom of the screen. It displays the description of the selected food and four buttons:
    - Eaten: which logs the selected food to the Eaten Table.
    - Edit: which allows you to edit the selected food.
    - Insert: which allows you to insert a new food.
    - Delete: which deletes the selected food.
""".trimIndent()
    var nutritionalInfoSelection by remember { mutableIntStateOf(initialFoodSelection) }
    var showNutritionalInfo by remember { mutableStateOf(initialFoodSelection != 0) }
    var selectedFood by remember { mutableStateOf<Food?>(null) }

    // State to control the visibility of our new dialog
    var showSelectDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    navController.currentBackStackEntry?.savedStateHandle?.let { savedStateHandle ->
        val foodUpdated = savedStateHandle.get<Boolean>("foodUpdated") ?: false
        val foodInserted = savedStateHandle.get<Boolean>("foodInserted") ?: false
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
                        onEdit = { navController.navigate("editFood/${food.foodId}") },
                        onInsert = { navController.navigate("insertFood") },
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
                    dbHelper.deleteFood(food.foodId)
                    foods = dbHelper.readFoodsFromDatabase()
                    selectedFood = null
                    showDeleteDialog = false
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
                    Text("Delete")
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
            Toast.makeText(context, "Food not found", Toast.LENGTH_SHORT).show()
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
- Use decimals where `needed` and tap Edit to save your changes.
# Editing Liquid Food
## Editing Liquid Food
### Editing Liquid Food
- Enter *values* per 100 mL *unless* otherwise ~~noted~~.
- Keep the description clear; the app keeps the “mL” suffix for logging.
- Use decimals where `needed` and tap Edit to save your changes.
# Editing Liquid Food
## Editing Liquid Food
### Editing Liquid Food
- Enter *values* per 100 mL *unless* otherwise ~~noted~~.
- Keep the description clear; the app keeps the “mL” suffix for logging.
- Use decimals where `needed` and tap Edit to save your changes.
# Editing Liquid Food
## Editing Liquid Food
### Editing Liquid Food
- Enter *values* per 100 mL *unless* otherwise ~~noted~~.
- Keep the description clear; the app keeps the “mL” suffix for logging.
- Use decimals where `needed` and tap Edit to save your changes.
# Editing Liquid Food
## Editing Liquid Food
### Editing Liquid Food
- Enter *values* per 100 mL *unless* otherwise ~~noted~~.
- Keep the description clear; the app keeps the “mL” suffix for logging.
- Use decimals where `needed` and tap Edit to save your changes.
""".trimIndent()
    } else {
        """
# Editing Solid Food
- Enter values per 100 g unless otherwise noted.
- Keep the description clear; the app keeps the “#” marker for serving size.
- Use decimals where needed and tap Edit to save your changes.
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
                            Toast.makeText(context, "Failed to update food", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = isValid
                ) {
                    Text("Edit")
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
# Insert Food
- Choose Solid or Liquid; the app will append a serving marker automatically.
- Enter nutrition values per 100 g or 100 mL; blanks are treated as zero.
- Use decimal values where needed; tap Insert to save the new food.
""".trimIndent()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insert Food", fontWeight = FontWeight.Bold) },
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
                            Toast.makeText(context, "To be implemented", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
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
                            Toast.makeText(context, "Failed to insert food", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = isValid
                ) {
                    Text("Insert")
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
                            Toast.makeText(context, "To be implemented", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
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
    AutolinkExtension.create(),
    TablesExtension.create(),
    StrikethroughExtension.create(),
    TaskListItemsExtension.create()
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
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val codeTextColor = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        RenderMarkdownChildren(
            parent = document,
            uriHandler = uriHandler,
            linkColor = linkColor,
            codeBackground = codeBackground,
            codeTextColor = codeTextColor,
            indentLevel = 0
        )
    }
}

@Composable
private fun RenderMarkdownChildren(
    parent: Node,
    uriHandler: UriHandler,
    linkColor: Color,
    codeBackground: Color,
    codeTextColor: Color,
    indentLevel: Int
) {
    var child = parent.firstChild
    while (child != null) {
        RenderMarkdownNode(
            node = child,
            uriHandler = uriHandler,
            linkColor = linkColor,
            codeBackground = codeBackground,
            codeTextColor = codeTextColor,
            indentLevel = indentLevel
        )
        if (child.next != null && parent !is ListItem && parent !is TableRow && parent !is TableHead && parent !is TableBody) {
            Spacer(modifier = Modifier.height(8.dp))
        }
        child = child.next
    }
}

@Composable
private fun RenderMarkdownNode(
    node: Node,
    uriHandler: UriHandler,
    linkColor: Color,
    codeBackground: Color,
    codeTextColor: Color,
    indentLevel: Int
) {
    when (node) {
        is Heading -> RenderHeading(node, uriHandler, linkColor, codeBackground, codeTextColor)
        is Paragraph -> RenderParagraph(node, uriHandler, linkColor, codeBackground, codeTextColor)
        is BlockQuote -> RenderBlockQuote(node, uriHandler, linkColor, codeBackground, codeTextColor, indentLevel)
        is BulletList -> RenderBulletList(node, uriHandler, linkColor, codeBackground, codeTextColor, indentLevel)
        is OrderedList -> RenderOrderedList(node, uriHandler, linkColor, codeBackground, codeTextColor, indentLevel)
        is FencedCodeBlock -> RenderCodeBlock(node.literal, indentLevel, codeBackground, codeTextColor)
        is IndentedCodeBlock -> RenderCodeBlock(node.literal, indentLevel, codeBackground, codeTextColor)
        is ThematicBreak -> HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        is TableBlock -> RenderTable(node, uriHandler, linkColor, codeBackground, codeTextColor, indentLevel)
        is HtmlBlock -> RenderParagraph(Paragraph().apply { appendChild(MdText(node.literal.orEmpty())) }, uriHandler, linkColor, codeBackground, codeTextColor)
        else -> RenderMarkdownChildren(node, uriHandler, linkColor, codeBackground, codeTextColor, indentLevel)
    }
}

@Composable
private fun RenderHeading(
    heading: Heading,
    uriHandler: UriHandler,
    linkColor: Color,
    codeBackground: Color,
    codeTextColor: Color
) {
    val annotatedString = buildAnnotatedStringFrom(
        node = heading,
        linkColor = linkColor,
        codeBackground = codeBackground,
        codeTextColor = codeTextColor
    )
    val style = when (heading.level) {
        1 -> MaterialTheme.typography.titleLarge
        2 -> MaterialTheme.typography.titleMedium
        3 -> MaterialTheme.typography.titleSmall
        else -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
    }
    MarkdownClickableText(annotatedString, style, uriHandler)
}

@Composable
private fun RenderParagraph(
    paragraph: Paragraph,
    uriHandler: UriHandler,
    linkColor: Color,
    codeBackground: Color,
    codeTextColor: Color
) {
    val annotatedString = buildAnnotatedStringFrom(
        node = paragraph,
        linkColor = linkColor,
        codeBackground = codeBackground,
        codeTextColor = codeTextColor
    )
    MarkdownClickableText(
        annotatedString = annotatedString,
        style = MaterialTheme.typography.bodyMedium,
        uriHandler = uriHandler
    )
}

@Composable
private fun RenderBlockQuote(
    blockQuote: BlockQuote,
    uriHandler: UriHandler,
    linkColor: Color,
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
                uriHandler = uriHandler,
                linkColor = linkColor,
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
    uriHandler: UriHandler,
    linkColor: Color,
    codeBackground: Color,
    codeTextColor: Color,
    indentLevel: Int
) {
    Column(modifier = Modifier.padding(start = (indentLevel * 12).dp)) {
        var item = list.firstChild as? ListItem
        while (item != null) {
            val taskChecked = taskListItemChecked(item)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                if (taskChecked != null) {
                    Checkbox(
                        checked = taskChecked,
                        onCheckedChange = null,
                        enabled = false,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                } else {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = 8.dp, top = 2.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    RenderMarkdownChildren(
                        parent = item,
                        uriHandler = uriHandler,
                        linkColor = linkColor,
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
    uriHandler: UriHandler,
    linkColor: Color,
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
                        uriHandler = uriHandler,
                        linkColor = linkColor,
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
private fun RenderTable(
    tableBlock: TableBlock,
    uriHandler: UriHandler,
    linkColor: Color,
    codeBackground: Color,
    codeTextColor: Color,
    indentLevel: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (indentLevel * 12).dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .horizontalScroll(rememberScrollState())
    ) {
        var section = tableBlock.firstChild
        while (section != null) {
            when (section) {
                is TableHead -> RenderTableSection(
                    section = section,
                    isHeader = true,
                    uriHandler = uriHandler,
                    linkColor = linkColor,
                    codeBackground = codeBackground,
                    codeTextColor = codeTextColor
                )
                is TableBody -> RenderTableSection(
                    section = section,
                    isHeader = false,
                    uriHandler = uriHandler,
                    linkColor = linkColor,
                    codeBackground = codeBackground,
                    codeTextColor = codeTextColor
                )
            }
            section = section.next
        }
    }
}

@Composable
private fun RenderTableSection(
    section: Node,
    isHeader: Boolean,
    uriHandler: UriHandler,
    linkColor: Color,
    codeBackground: Color,
    codeTextColor: Color
) {
    var row = section.firstChild as? TableRow
    while (row != null) {
        RenderTableRow(
            row = row,
            isHeader = isHeader,
            uriHandler = uriHandler,
            linkColor = linkColor,
            codeBackground = codeBackground,
            codeTextColor = codeTextColor
        )
        row = row.next as? TableRow
        if (row != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun RenderTableRow(
    row: TableRow,
    isHeader: Boolean,
    uriHandler: UriHandler,
    linkColor: Color,
    codeBackground: Color,
    codeTextColor: Color
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        var cell = row.firstChild as? TableCell
        while (cell != null) {
            val alignment = when (cell.alignment) {
                TableCell.Alignment.CENTER -> TextAlign.Center
                TableCell.Alignment.RIGHT -> TextAlign.End
                else -> TextAlign.Start
            }
            val annotated = buildAnnotatedStringFrom(
                node = cell,
                linkColor = linkColor,
                codeBackground = codeBackground,
                codeTextColor = codeTextColor
            )
            val style = (if (isHeader) {
                MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            } else {
                MaterialTheme.typography.bodyMedium
            }).copy(textAlign = alignment)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                MarkdownClickableText(
                    annotatedString = annotated,
                    style = style,
                    uriHandler = uriHandler
                )
            }
            cell = cell.next as? TableCell
        }
    }
}

@Composable
private fun MarkdownClickableText(
    annotatedString: AnnotatedString,
    style: TextStyle,
    uriHandler: UriHandler
) {
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = annotatedString,
        style = style.copy(color = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.pointerInput(annotatedString) {
            detectTapGestures { position ->
                val offset = layoutResult?.getOffsetForPosition(position) ?: return@detectTapGestures
                annotatedString
                    .getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()
                    ?.let { annotation ->
                        if (annotation.item.isNotBlank()) {
                            uriHandler.openUri(annotation.item)
                        }
                    }
            }
        },
        onTextLayout = { layoutResult = it }
    )
}

private fun buildAnnotatedStringFrom(
    node: Node,
    linkColor: Color,
    codeBackground: Color,
    codeTextColor: Color
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    appendInlineChildren(node, builder, linkColor, codeBackground, codeTextColor)
    return builder.toAnnotatedString()
}

private fun appendInlineChildren(
    node: Node,
    builder: AnnotatedString.Builder,
    linkColor: Color,
    codeBackground: Color,
    codeTextColor: Color
) {
    var child = node.firstChild
    while (child != null) {
        when (child) {
            is MdText -> builder.append(child.literal)
            is SoftLineBreak -> builder.append("\n")
            is HardLineBreak -> builder.append("\n")
            is Emphasis -> {
                val start = builder.length
                appendInlineChildren(child, builder, linkColor, codeBackground, codeTextColor)
                builder.addStyle(
                    style = SpanStyle(fontStyle = FontStyle.Italic),
                    start = start,
                    end = builder.length
                )
            }
            is StrongEmphasis -> {
                val start = builder.length
                appendInlineChildren(child, builder, linkColor, codeBackground, codeTextColor)
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
            is Link -> {
                val start = builder.length
                appendInlineChildren(child, builder, linkColor, codeBackground, codeTextColor)
                val end = builder.length
                child.destination?.takeIf { it.isNotBlank() }?.let {
                    builder.addStringAnnotation(tag = "URL", annotation = it, start = start, end = end)
                }
                builder.addStyle(
                    style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                    start = start,
                    end = end
                )
            }
            is Strikethrough -> {
                val start = builder.length
                appendInlineChildren(child, builder, linkColor, codeBackground, codeTextColor)
                builder.addStyle(
                    style = SpanStyle(textDecoration = TextDecoration.LineThrough),
                    start = start,
                    end = builder.length
                )
            }
            is Image -> {
                builder.append(child.title?.takeIf { it.isNotBlank() } ?: child.destination ?: "[image]")
            }
            is HtmlInline -> builder.append(child.literal)
            else -> appendInlineChildren(child, builder, linkColor, codeBackground, codeTextColor)
        }
        child = child.next
    }
}

private fun taskListItemChecked(item: ListItem): Boolean? {
    return runCatching {
        val clazz = item::class.java
        if (clazz.name == "org.commonmark.ext.task.list.items.TaskListItem") {
            val method = clazz.getMethod("isChecked")
            (method.invoke(item) as? Boolean) ?: false
        } else {
            null
        }
    }.getOrNull()
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
