@file:Suppress(
    "unused",
    "UNUSED_IMPORT",
    "UNUSED_PARAMETER",
    "UNUSED_VARIABLE",
    "UNUSED_VALUE",
    "UNUSED_ANONYMOUS_PARAMETER",
    "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE"
)

package au.dietsentry.myapplication

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import androidx.core.net.toUri
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "DietSentryPrefs"
private const val KEY_SHOW_NUTRITIONAL_INFO = "showNutritionalInfo" // legacy boolean; kept for fallback
private const val KEY_NUTRITION_SELECTION_FOOD = "nutritionSelectionFood"
private const val KEY_NUTRITION_SELECTION_EATEN = "nutritionSelectionEaten"
private const val KEY_DISPLAY_DAILY_TOTALS = "displayDailyTotals"
private const val KEY_FILTER_EATEN_BY_DATE = "filterEatenByDate"
private const val KEY_EXPORT_OVERWRITE_URI = "exportOverwriteUri"
private const val KEY_IMPORT_URI = "importUri"

// Session-scoped in-memory state (persists while app stays alive)
private var sessionSelectedFilterDateMillis: Long? = null
private var sessionAddRecipeSearchQuery: String = ""
private var sessionCopyRecipeSearchQuery: String = ""
private var sessionEditRecipeSearchQuery: String = ""

private enum class RecipeSearchMode {
    ADD,
    COPY,
    EDIT
}

private fun resolveRecipeSearchMode(screenTitle: String, editingFoodId: Int?): RecipeSearchMode =
    if (editingFoodId != null) {
        RecipeSearchMode.EDIT
    } else if (screenTitle == "Copying Recipe") {
        RecipeSearchMode.COPY
    } else {
        RecipeSearchMode.ADD
    }

private fun loadRecipeSearchQuery(mode: RecipeSearchMode): String = when (mode) {
    RecipeSearchMode.ADD -> sessionAddRecipeSearchQuery
    RecipeSearchMode.COPY -> sessionCopyRecipeSearchQuery
    RecipeSearchMode.EDIT -> sessionEditRecipeSearchQuery
}

private fun storeRecipeSearchQuery(mode: RecipeSearchMode, value: String) {
    when (mode) {
        RecipeSearchMode.ADD -> sessionAddRecipeSearchQuery = value
        RecipeSearchMode.COPY -> sessionCopyRecipeSearchQuery = value
        RecipeSearchMode.EDIT -> sessionEditRecipeSearchQuery = value
    }
}

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
                    composable("addFoodByJson") {
                        AddFoodByJsonScreen(navController = navController)
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
                    composable("utilities") {
                        UtilitiesScreen(navController = navController)
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
    val initialFilterByDate = remember {
        sharedPreferences.getBoolean(KEY_FILTER_EATEN_BY_DATE, false)
    }
    var nutritionalInfoSelection by remember { mutableIntStateOf(initialEatenSelection) }
    var showNutritionalInfo by remember { mutableStateOf(initialEatenSelection != 0) }
    val showExtraNutrients = nutritionalInfoSelection == 2
    var showHelpSheet by remember { mutableStateOf(false) }
    val helpSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val eatenHelpText = """
# **Eaten Table**
The main purpose of this screen is to display a log of the foods you have consumed. You can also change their time stamps, amount eaten or delete them.
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
- A **check box** labeled "Filter by Date"
    - When **unchecked** all food logs are displayed. For all dates and times.
    - When **checked** only food logs of foods logged during the displayed date are displayed, whether summed or not.
- A **date dialog** which displays a selected date.
    - When this app is started the default is today's date. It is however persistent between app restarts.
- A **scrollable table viewer** which displays records (possibly consolidated by date) from the Eaten table. If a particular logged food is selected (by tapping it) a selection panel appears at the bottom of the screen. It displays the description of the selected food log followed by two buttons below it:
    - **Edit**: It enables the amount and date stamp of the logged eaten food to be modified.
        - It opens a dialog box where you can specify the amount eaten as well as the date and time this has occurred (with the default being now).
        - Press the **Confirm** button when you are ready to confirm your changes. This then transfers focus back to the Eaten Table screen and the where the just modified food log will be visible and selected. The selection panel for this log (with the Edit and Deleted buttons) is still then open.
        - You can abort this process by tapping anywhere outside the dialog box. This closes it and transfers focus in the same way as described above.
    - **Delete**: deletes the selected food lof from the Eaten table.
        - It opens a dialog which warns you that you will be deleting the selected food log from the Eaten table.
        - This is irrevocable if you press the **Delete** button.
        - You can change you mind about doing this by just tapping anywhere outside the dialog box. This closes it and returns focus to the Eaten Table screen. The selection panel (with the Edit and Deleted buttons) is also closed.
    - If food logs consolidated by date are displayed (ie. the "Daily totals" check box is ticked), selection for editing or deletion is not possible, so nothing happens.       
***
# **Eaten table structure**
```
Field name              Type    Units

EatenId                 INTEGER
DateEaten               TEXT    d-MMM-yy
TimeEaten               TEXT    HH:mm
EatenTs                 INTEGER
AmountEaten             REAL    g or mL
FoodDescription         TEXT	
Energy                  REAL    kJ
Protein                 REAL    g
FatTotal                REAL    g
SaturatedFat            REAL    g
TransFat                REAL    mg
PolyunsaturatedFat      REAL    g
MonounsaturatedFat      REAL    g
Carbohydrate            REAL    g
Sugars                  REAL    g
DietaryFibre            REAL    g
SodiumNa                REAL    mg
CalciumCa               REAL    mg
PotassiumK              REAL    mg
ThiaminB1               REAL    mg
RiboflavinB2            REAL    mg
NiacinB3                REAL    mg
Folate                  REAL    µg
IronFe                  REAL    mg
MagnesiumMg             REAL    mg
VitaminC                REAL    mg
Caffeine                REAL    mg
Cholesterol             REAL    mg
Alcohol                 REAL    g
```
The **EatenId** field is never explicitly displayed or considered. It is a Primary Key that is auto incremented when a record is created.

The **DateEaten** and **TimeEaten** text fields store the food logs time stamp

The **EatenTs** field is an integer that specifies the number of minutes since a reference time stamp. It allows easy sorting by date/time of when a food was logged (it is re1calculated if the Date and Time eaten are changed. 

The **FoodDescription** is the same field as for a Foods table record.

The remaining (**Energy** and **Nutrient fields**) are the same as for the corresponding Foods table record, except that they are scaled by the amount of the food eaten. Eg. if EatenAmount=300 then all these field values are multiplied by 3.
*** 
""".trimIndent()
    var displayDailyTotals by remember { mutableStateOf(initialDisplayDailyTotals) }
    var filterByDate by remember { mutableStateOf(initialFilterByDate) }
    val initialFilterDate = remember { sessionSelectedFilterDateMillis ?: System.currentTimeMillis() }
    var selectedFilterDateMillis by remember { mutableLongStateOf(initialFilterDate) }
    val filterDateFormatter = remember { SimpleDateFormat("d-MMM-yy", Locale.getDefault()) }
    val filterDateDisplayFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val filteredEatenFoods = remember(eatenFoods, filterByDate, selectedFilterDateMillis) {
        if (!filterByDate) eatenFoods else {
            val matchDate = filterDateFormatter.format(Date(selectedFilterDateMillis))
            eatenFoods.filter { it.dateEaten == matchDate }
        }
    }
    val dailyTotals = remember(filteredEatenFoods) { aggregateDailyTotals(filteredEatenFoods) }
    var showFilterDatePicker by remember { mutableStateOf(false) }
    val filterDatePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedFilterDateMillis)
    LaunchedEffect(selectedFilterDateMillis) {
        filterDatePickerState.selectedDateMillis = selectedFilterDateMillis
        sessionSelectedFilterDateMillis = selectedFilterDateMillis
    }

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
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    Text(
                        text = "Daily totals",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Checkbox(
                        checked = filterByDate,
                        onCheckedChange = {
                            filterByDate = it
                            sharedPreferences.edit {
                                putBoolean(KEY_FILTER_EATEN_BY_DATE, it)
                            }
                            if (it) selectedEatenFood = null
                        }
                    )
                    Text(
                        text = "Filter by date",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = { showFilterDatePicker = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(
                            filterDateDisplayFormatter.format(Date(selectedFilterDateMillis)),
                            maxLines = 1
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
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
                        items(filteredEatenFoods) { eatenFood ->
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

    if (showFilterDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showFilterDatePicker = false },
            confirmButton = {
                Button(onClick = {
                    filterDatePickerState.selectedDateMillis?.let { millis ->
                        selectedFilterDateMillis = millis
                    }
                    showFilterDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                Button(onClick = { showFilterDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = filterDatePickerState)
        }
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
    
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val sharedPreferences = remember {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    var foods by remember { mutableStateOf(dbHelper.readFoodsFromDatabase()) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }
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

Its purpose is to display a list of foods from the Foods table and allow interaction with a selected food. The primary purpose being to **LOG** the selected food.
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
    - You can also type {text1}|{text2} to match descriptions that contain BOTH of these terms.
    - It is NOT case sensitive
- The **clear text field button** `x` which clears the above text field    
- A **scrollable table viewer** which displays records from the Foods table. When a particular food is selected (by tapping it) a selection panel appears at the bottom of the screen. It displays the description of the selected food followed by seven buttons below it:
    - **LOG**: logs the selected food into the Eaten Table.
        - It opens a dialog box where you can specify the amount eaten as well as the date and time this has occurred (with the default being now).
        - Press the **Confirm** button when you are ready to log your food. This transfers focus to the Eaten Table screen where the just logged food will be visible. Read the help on thet Screen for more help.
        - You can abort this process by tapping anywhere outside the dialog box. This closes it.
    - **Edit**: allows editing of the selected food.
        - It opens "Editing Solid Food" or "Editing Liquid Food" unless the description contains `{recipe=...g}`, in which case it opens "Editing Recipe".
    - **Add**: adds a new food to the database.
        - It opens a screen titled "Add Food". Press the help button on that screen for more help.
        - The original selected food has no relevance to this activity. It is just a way of making the Add button available.
    - **Json**: adds a new food to the database based on Json text (not applicable to recipe foods).
        - It opens a screen titled "Add Food using Json".  Press the help button on that screen for more help.
        - The original selected food has no relevance to this activity. It is just a way of making the Json button available.
    - **Copy**: makes a copy of the selected food.
        - If the selected food is a Solid it opens a screen titled "Copying Solid Food"
        - If the selected food is a Liquid it opens a screen titled "Copying Liquid Food"
        - If the selected food is a Recipe it opens a screen titled "Copying Recipe"
        The type of a food (Solid, Liquid or Recipe) is coded in its description field, as explained in the next section.
    - **Convert**: converts a liquid food to a solid. 
        - If the food is a liquid it displays a dialog that enables the foods density to be input in g/mL
        - A new solid food is then created on a per 100g basis.
        - The description of this new food looses the liquids indicator " mL" near its end.
    - **Delete**: deletes the selected food from the database.
        - It opens a dialog which warns you that you will be deleting the selected food.
        - This is irrevocable if you press the **Confirm** button.
        - You can change your mind about doing this by just tapping anywhere outside the dialog box. This closes it.
    - **Utilities**: opens the Utilities screen for database maintenance tools.
        - **Export db** button. Saves a backup of your current "internal" Foods database. This is not reversible and should be used with care. It really only needs to be done just before this app is upgraded to a new version and you wish to maintain your database.
        - **Import db** button. Replace your current "internal" Foods database with the above backup. This is not reversible and should be used with care. It really only needs to be done if this app has been upgraded to a new version and you wish to maintain your database (you should have exported just before).
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

- **If a FoodDescription ends in the characters " mL" or " mL#"** the food is considered a Liquid, and nutrient values are per 100mL, The "#" character indicates that it is not part of the original database of foods.

- **If a FoodDescription ends in the characters " {recipe=[weight]g} #" or " {recipe=[weight]g}"** the food is considered a Recipe and can only be made up of solids and thus its nutrient values are per 100g. It is also never a part of the original database.

- **If a FoodDescription ends in any other pattern of characters than those specified above** the food is considered a Solid, and nutrient values are per 100g. If additionally it ends in "#" then it is also never a part of the original database.

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

- **The Foods table does not explicitly consider servings, though they might be noted in the FoodDescription text field.** 

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
        val foodInsertedDescription = savedStateHandle.get<String>("foodInsertedDescription")
        val foodUpdatedDescription = savedStateHandle.get<String>("foodUpdatedDescription")
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
        LaunchedEffect(foodInsertedDescription) {
            if (!foodInsertedDescription.isNullOrBlank()) {
                searchQuery = foodInsertedDescription
                foods = dbHelper.searchFoods(foodInsertedDescription)
                selectedFood = null
                savedStateHandle.remove<String>("foodInsertedDescription")
            }
        }
        LaunchedEffect(foodUpdatedDescription) {
            if (!foodUpdatedDescription.isNullOrBlank()) {
                searchQuery = foodUpdatedDescription
                foods = dbHelper.searchFoods(foodUpdatedDescription)
                selectedFood = null
                savedStateHandle.remove<String>("foodUpdatedDescription")
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
                title = { Text("Foods Table", fontWeight = FontWeight.Bold) },
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
                                dbHelper.searchFoods(searchQuery)
                            } else {
                                dbHelper.readFoodsFromDatabase()
                            }
                            keyboardController?.hide()
                        }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchFocusRequester)
                    )
                    IconButton(
                        onClick = {
                            searchQuery = ""
                            searchFocusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Clear food filter"
                        )
                    }
                }
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
                        onJson = { navController.navigate("addFoodByJson") },
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
                        onDelete = { showDeleteDialog = true },
                        onUtilities = { navController.navigate("utilities") }
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
                        searchQuery = newDescription
                        foods = dbHelper.searchFoods(newDescription)
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
    var description by rememberSaveable(food) { mutableStateOf(initialDescription) }
    var energy by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.energy)) }
    var protein by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.protein)) }
    var fatTotal by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.fatTotal)) }
    var saturatedFat by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.saturatedFat)) }
    var transFat by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.transFat)) }
    var polyunsaturatedFat by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.polyunsaturatedFat)) }
    var monounsaturatedFat by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.monounsaturatedFat)) }
    var carbohydrate by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.carbohydrate)) }
    var sugars by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.sugars)) }
    var dietaryFibre by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.dietaryFibre)) }
    var sodium by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.sodium)) }
    var calciumCa by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.calciumCa)) }
    var potassiumK by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.potassiumK)) }
    var thiaminB1 by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.thiaminB1)) }
    var riboflavinB2 by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.riboflavinB2)) }
    var niacinB3 by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.niacinB3)) }
    var folate by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.folate)) }
    var ironFe by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.ironFe)) }
    var magnesiumMg by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.magnesiumMg)) }
    var vitaminC by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.vitaminC)) }
    var caffeine by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.caffeine)) }
    var cholesterol by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.cholesterol)) }
    var alcohol by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.alcohol)) }

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
# **Editing Liquid Food**
- These are foods for which the Energy and nutrient values are given on a **per 100mL basis**
- On first displaying this screen all the input fields will be populated with values from the selected Food, however the Description field will have the " mL" or " mL#" markers omitted. These will be reinstated after edit confirmation. This means you cannot change a Liquid food into a Solid one directly through editing. However it is possible to convert it to a solid by tapping the Convert button and then editing that solid food.
- Modify fields as required using decimals where needed and tap Confirm to save your changes. If a field entry is not valid (eg. text, blank or not a number in a field requiring numbers) the Confirm button will be disabled.
- You can press either of two "back" buttons to cancel the editing process and return focus to the Foods Table screen.
- If confirmation succeeds the selected food is amended and focus passes to the Foods Table screen with the filter text being set to the just edited foods description (with markers reinstated). This allows you to review the results of the edit and is especially important if the description has changed significantly and you would not have been able find the food again.    
""".trimIndent()
    } else {
        """
# **Editing Solid Food**
- These are foods for which the Energy and nutrient values are given on a **per 100g basis**
- On first displaying this screen all the input fields will be populated with values from the selected Food, however the Description field will have the " #" marker (if any) omitted. This will be reinstated after edit confirmation. Interestingly you can convert this food to a liquid by appending the text " mL" to the possibly edited description.
- Modify fields as required using decimals where needed and tap Confirm to save your changes. If a field entry is not valid (eg. text, blank or not a number in a field requiring numbers) the Confirm button will be disabled.
- You can press either of two "back" buttons to cancel the editing process and return focus to the Foods Table screen.
- If confirmation succeeds the selected food is amended and focus passes to the Foods Table screen with the filter text being set to the just edited foods description (with any markers reinstated). This allows you to review the results of the edit and is especially important if the description has changed significantly and you would not have been able find the food again.    
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
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("foodUpdatedDescription", updatedFood.foodDescription)
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

    var description by rememberSaveable(food) { mutableStateOf(initialDescription) }
    var energy by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.energy)) }
    var protein by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.protein)) }
    var fatTotal by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.fatTotal)) }
    var saturatedFat by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.saturatedFat)) }
    var transFat by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.transFat)) }
    var polyunsaturatedFat by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.polyunsaturatedFat)) }
    var monounsaturatedFat by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.monounsaturatedFat)) }
    var carbohydrate by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.carbohydrate)) }
    var sugars by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.sugars)) }
    var dietaryFibre by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.dietaryFibre)) }
    var sodium by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.sodium)) }
    var calciumCa by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.calciumCa)) }
    var potassiumK by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.potassiumK)) }
    var thiaminB1 by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.thiaminB1)) }
    var riboflavinB2 by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.riboflavinB2)) }
    var niacinB3 by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.niacinB3)) }
    var folate by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.folate)) }
    var ironFe by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.ironFe)) }
    var magnesiumMg by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.magnesiumMg)) }
    var vitaminC by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.vitaminC)) }
    var caffeine by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.caffeine)) }
    var cholesterol by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.cholesterol)) }
    var alcohol by rememberSaveable(food) { mutableStateOf(formatOneDecimal(food!!.alcohol)) }

    val scrollState = rememberScrollState()
    val numericEntries = listOf(
        energy, protein, fatTotal, saturatedFat, transFat, polyunsaturatedFat, monounsaturatedFat,
        carbohydrate, sugars, dietaryFibre, sodium, calciumCa, potassiumK, thiaminB1,
        riboflavinB2, niacinB3, folate, ironFe, magnesiumMg, vitaminC, caffeine, cholesterol, alcohol
    )
    val isValid = description.isNotBlank() && numericEntries.all { it.toDoubleOrNull() != null }
    var showHelpSheet by remember { mutableStateOf(false) }
    val helpSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val copyHelpText = if (isLiquidFood) {
        """          
        # **Copying Liquid Food**
        - When the Copy button is tapped from the Foods Table screen with a **liquid food selected**, this screen headed Copying Liquid Food is displayed
        - Its layout and presentation is identical to the Editing Liquid Food screen, the difference being that instead of modifying the selected food record, a new one is created with the displayed field values.
        - Clearly before pressing the Confirm button you can modify any of the field entries, so you are not really creating an exact copy just a food based on the selection.
        - Focus will then pass to the Foods Table screen with the filter text being set to the just created foods description (with the liquid marker appended). This allows you to review the results of the foods creation and is especially important if the Description is unintuitive and finding the food in the table might be difficult.
        - As before you can press either of the two "back" buttons to cancel the copying process and return focus to the Foods Table screen.
        """.trimIndent()
    } else {
        """          
        # **Copying Solid Food**
        - When the Copy button is tapped from the Foods Table screen with a **solid food selected**, this screen headed Copying Solid Food is displayed
        - Its layout and presentation is identical to the Editing Solid Food screen, the difference being that instead of modifying the selected food record, a new one is created with the displayed field values.
        - Clearly before pressing the Confirm button you can modify any of the field entries, so you are not really creating an exact copy just a food based on the selection.
        - Focus will then pass to the Foods Table screen with the filter text being set to the just created foods description. This allows you to review the results of the foods creation and is especially important if the Description is unintuitive and finding the food in the table might be difficult.
        - As before you can press either of the two "back" buttons to cancel the copying process and return focus to the Foods Table screen.
        """.trimIndent()
    }

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
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("foodInsertedDescription", processedDescription)
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

    var description by rememberSaveable { mutableStateOf("") }
    var energy by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var fatTotal by rememberSaveable { mutableStateOf("") }
    var saturatedFat by rememberSaveable { mutableStateOf("") }
    var transFat by rememberSaveable { mutableStateOf("") }
    var polyunsaturatedFat by rememberSaveable { mutableStateOf("") }
    var monounsaturatedFat by rememberSaveable { mutableStateOf("") }
    var carbohydrate by rememberSaveable { mutableStateOf("") }
    var sugars by rememberSaveable { mutableStateOf("") }
    var dietaryFibre by rememberSaveable { mutableStateOf("") }
    var sodium by rememberSaveable { mutableStateOf("") }
    var calciumCa by rememberSaveable { mutableStateOf("") }
    var potassiumK by rememberSaveable { mutableStateOf("") }
    var thiaminB1 by rememberSaveable { mutableStateOf("") }
    var riboflavinB2 by rememberSaveable { mutableStateOf("") }
    var niacinB3 by rememberSaveable { mutableStateOf("") }
    var folate by rememberSaveable { mutableStateOf("") }
    var ironFe by rememberSaveable { mutableStateOf("") }
    var magnesiumMg by rememberSaveable { mutableStateOf("") }
    var vitaminC by rememberSaveable { mutableStateOf("") }
    var caffeine by rememberSaveable { mutableStateOf("") }
    var cholesterol by rememberSaveable { mutableStateOf("") }
    var alcohol by rememberSaveable { mutableStateOf("") }

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
# **Add Food**
- When the Add button is tapped from the Foods Table screen this screen is displayed.
- Like other screens it has a help and a navigation button in the top row.
- The second row has three radio buttons titled:
    - **Solid**. This is the default selection when this screen is opened.
        - It indicates that the new food will be of solid type and thus the final FoodDescription will terminate with the marker " *". You do not need to do this explicitly in the Description field.
        - The Energy and nutrition fields are assumed to be on a per 100g basis. 
    - **Liquid**. If this is selected the new food will be of liquid type.
        - The final FoodDescription will terminate with the marker " mL*". You do not need to do this explicitly in the Description field.
        - The Energy and nutrition fields are assumed to be on a per 100mL basis.
    - **Recipe**. If this radio button is selected focus will immediately pass to the Add Recipe screen.
        - Any input fields you might have filled in will be ignored.
- The following rows display the record fields that need to be filled in to create a new (non recipe) food.
    - Enter the description and the nutritient values. Blanks outside of the Description field are treated as 0. As long as the Description field is not blank the Confirm button will be enabled.
    - You can press either of the two "back" buttons to cancel the creation process and return focus to the Foods Table screen.
    - If however the Confirm button is pressed the Solid or Liquid food (as designated by the selected radio button) will be added to the Foods table. Focus will then pass to the Foods Table screen with the filter text being set to the just created foods description (with the liquid marker appended if necessary). This allows you to review the results of the foods creation and is especially important if the Description is unintuitive and finding the food in the table might be difficult.    
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
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("foodInsertedDescription", processedDescription)
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
fun AddFoodByJsonScreen(navController: NavController) {
    val context = LocalContext.current
    val dbHelper = remember { DatabaseHelper.getInstance(context) }
    var jsonText by rememberSaveable { mutableStateOf("") }
    var showHelpSheet by remember { mutableStateOf(false) }
    val helpSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val jsonHelpText = """
# **Add Food using Json**
- When the **Json** button is tapped from the **Foods Table** screen, this screen called **Add Food using Json** is displayed.
- Like other screens it has a **help** and a **navigation** button in the top row.
- Following this is a **text field** that takes up the rest of the screen, and followed by a **Confirm** button.
- Paste or enter JSON text describing a food item (liquid or solid, but not recipe).
- The format of the JSON text needs to be precisely as shown in the example below:
```
{
  "FoodDescription": "Cheese, Mersey Valley Classic #",
  "Energy": 1690,
  "Protein": 23.7,
  "FatTotal": 34.9,
  "SaturatedFat": 22.4,
  "TransFat": 1,
  "PolyunsaturatedFat": 0.5,
  "MonounsaturatedFat": 10,
  "Carbohydrate": 0.1,
  "Sugars": 0.1,
  "DietaryFibre": 0,
  "SodiumNa": 643,
  "CalciumCa": 720,
  "PotassiumK": 100,
  "ThiaminB1": 0,
  "RiboflavinB2": 0.3,
  "NiacinB3": 0.1,
  "Folate": 10,
  "IronFe": 0.2,
  "MagnesiumMg": 30,
  "VitaminC": 0,
  "Caffeine": 0,
  "Cholesterol": 100,
  "Alcohol": 0,
  "notes": "Used on-pack NIP for core nutrients. Remaining micronutrients estimated from AFCD/NUTTAB cheddar cheese equivalents. Website not checked—no URL provided."
}
```
NOTE: **Any line feeds, tabs and spaces outside of "any text" are entirely optional** which means that this Json text is also valid though not easy to read for a human:
 ```
{"FoodDescription":"Cheese, Mersey Valley Classic #","Energy":1690,"Protein":23.7,"FatTotal":34.9,"SaturatedFat":22.4,"TransFat":1,"PolyunsaturatedFat":0.5,"MonounsaturatedFat":10,"Carbohydrate":0.1,"Sugars":0.1,"DietaryFibre":0,"SodiumNa":643,"CalciumCa":720,"PotassiumK":100,"ThiaminB1":0,"RiboflavinB2":0.3,"NiacinB3":0.1,"Folate":10,"IronFe":0.2,"MagnesiumMg":30,"VitaminC":0,"Caffeine":0,"Cholesterol":100,"Alcohol":0,"notes":"Used on-pack NIP for core nutrients. Remaining micronutrients estimated from AFCD/NUTTAB cheddar cheese equivalents. Website not checked—no URL provided."}   
```
- Tap **Confirm** to process the JSON which adds the food to the Foods table. Focus will then pass to the Foods Table screen with the filter text being set to the just created foods description (with the liquid marker appended if relevant). This allows you to review the results of the foods creation, with this being especially important if the Description is unintuitive and finding the food in the table might be difficult.
    - If the Json text is missing or invalid a Toast message will appear ("Please paste valid JSON" or "Invalid JSON or missing fields" and focus will remain unchanged.
- **To abort any actions on this screen** you can press either of the two "back" buttons to clear any text from the text field and set focus to the Foods Table screen.
***
# **AI generation of JSON**
The easiest and intended way of obtaining JSON text is to use AI. The following workflow is recommended:
- You are assumed to have access to the ChatGPT Pro paid plan (or better). This give you access to GPTs.
- Log into ChatGpt (https://chatgpt.com) and Explore GPTs. Find the **NIP generator** GPT and Start Chat.
```
Given a food description returns its expanded Nutrition Information Panel (NIP) in Json format. It can be directly added to the Foods table of any Diet Sentry app database. It follows the FSANZ standard 1.2.8 and Schedules 11–12.
```
- You can attach photos of labels and product NIPs to the chat prompt as well as just a text description of the food you are interested in.
- A Diet Sentry compatible JSON text will be generated as a chat response. This can be copied and pasted into the text field of this screen.
""".trimIndent()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Food using Json", fontWeight = FontWeight.Bold) },
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
                Button(onClick = {
                    val jsonStart = jsonText.indexOf('{')
                    val jsonEnd = jsonText.lastIndexOf('}')
                    if (jsonStart == -1 || jsonEnd == -1 || jsonEnd <= jsonStart) {
                        showPlainToast(context, "Please paste valid JSON")
                        return@Button
                    }
                    val jsonPayload = jsonText.substring(jsonStart, jsonEnd + 1)
                    try {
                        val json = org.json.JSONObject(jsonPayload)
                        val description = json.getString("FoodDescription").trim()
                        if (description.isBlank()) {
                            showPlainToast(context, "FoodDescription is required")
                            return@Button
                        }
                        val newFood = Food(
                            foodId = 0,
                            foodDescription = description,
                            energy = json.getDouble("Energy"),
                            protein = json.getDouble("Protein"),
                            fatTotal = json.getDouble("FatTotal"),
                            saturatedFat = json.getDouble("SaturatedFat"),
                            transFat = json.getDouble("TransFat"),
                            polyunsaturatedFat = json.getDouble("PolyunsaturatedFat"),
                            monounsaturatedFat = json.getDouble("MonounsaturatedFat"),
                            carbohydrate = json.getDouble("Carbohydrate"),
                            sugars = json.getDouble("Sugars"),
                            dietaryFibre = json.getDouble("DietaryFibre"),
                            sodium = json.getDouble("SodiumNa"),
                            calciumCa = json.getDouble("CalciumCa"),
                            potassiumK = json.getDouble("PotassiumK"),
                            thiaminB1 = json.getDouble("ThiaminB1"),
                            riboflavinB2 = json.getDouble("RiboflavinB2"),
                            niacinB3 = json.getDouble("NiacinB3"),
                            folate = json.getDouble("Folate"),
                            ironFe = json.getDouble("IronFe"),
                            magnesiumMg = json.getDouble("MagnesiumMg"),
                            vitaminC = json.getDouble("VitaminC"),
                            caffeine = json.getDouble("Caffeine"),
                            cholesterol = json.getDouble("Cholesterol"),
                            alcohol = json.getDouble("Alcohol")
                        )
                        val inserted = dbHelper.insertFood(newFood)
                        if (inserted) {
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("foodInsertedDescription", description)
                            navController.popBackStack()
                        } else {
                            showPlainToast(context, "Failed to insert food")
                        }
                    } catch (e: Exception) {
                        showPlainToast(context, "Invalid JSON or missing fields")
                    }
                }) {
                    Text("Confirm")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            TextField(
                value = jsonText,
                onValueChange = { jsonText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                placeholder = { Text("Paste JSON here") },
                maxLines = Int.MAX_VALUE
            )
        }
    }

    if (showHelpSheet) {
        HelpBottomSheet(
            helpText = jsonHelpText,
            sheetState = helpSheetState,
            onDismiss = { showHelpSheet = false }
        )
    }
}

private fun buildRecipeHelpText(screenTitle: String): String {
    val modeIntro = when (screenTitle) {
        "Editing Recipe" -> "This screen lets you edit the recipe food you selected."
        "Copying Recipe" -> "This screen lets you create and modify a new recipe food by copying the one you selected."
        else -> "This screen lets you creates a new recipe food."
    }
    val descriptionHint = when (screenTitle) {
        "Add Recipe" -> "It starts empty."
        else -> "It starts with the selected recipe's description (without the recipe marker)."
    }
    val confirmHint = when (screenTitle) {
        "Editing Recipe" -> "When pressed the recipe is updated and focus shifts to the Foods Table screen."
        "Copying Recipe" -> "When pressed a new recipe is created and focus shifts to the Foods Table screen."
        else -> "When pressed the recipe is created and focus shifts to the Foods Table screen."
    }
    return """
# **$screenTitle**
$modeIntro

By its very nature a recipe food is more complicated than a normal liquid or solid food and hence this screen is more complex.

***
# **What is a recipe food? **
A recipe food is a record in the Foods table AND and a collection of ingredient records in the Recipe table. Each ingredient is linked to its Foods table record by its FoodId.

For the purposes of logging consumption you select a recipe food (from the scrollable table viewer in the Foods Table screen) just like with the simpler solid and liquid foods. It is considered a solid in that its amount is measured in grams. Differences in processing are only apparent when you Edit, Add or Copy it. 

You can identify a recipe food by noting that its FoodDescription field ends in text of the form " {recipe=[weight]g}" or " {recipe=[weight]g} *" where [weight] is the total amount in grams of the ingredient foods (all required to be solids or recipes).

The **Recipe table structure** is as follows:
```
Field name              Type    Units

RecipeId                INTEGER
FoodId                  INTEGER
CopyFg                  INTEGER
Amount                  REAL    g only
FoodDescription         TEXT	
Energy                  REAL    kJ
Protein                 REAL    g
FatTotal                REAL    g
SaturatedFat            REAL    g
TransFat                REAL    mg
PolyunsaturatedFat      REAL    g
MonounsaturatedFat      REAL    g
Carbohydrate            REAL    g
Sugars                  REAL    g
DietaryFibre            REAL    g
SodiumNa                REAL    mg
CalciumCa               REAL    mg
PotassiumK              REAL    mg
ThiaminB1               REAL    mg
RiboflavinB2            REAL    mg
NiacinB3                REAL    mg
Folate                  REAL    µg
IronFe                  REAL    mg
MagnesiumMg             REAL    mg
VitaminC                REAL    mg
Caffeine                REAL    mg
Cholesterol             REAL    mg
Alcohol                 REAL    g
```

The **RecipeId** field is never explicitly displayed or considered. It is a Primary Key that is auto incremented when a record is created.

The **FoodId** field is the Primary Key of this recipe foods record in the Foods table. This is what identifies which Recipe table records are part of this particular recipe food.

The **CopyFg** field can only be 0 (default) or 1. It is used internally when a recipe food is being Edited, Added or Copied. Between any modifications to the Recipe table the value of that field is 0 for all records.
 
The **FoodDescription** is the same field as for that ingredients record in the Foods table.

The remaining (**Energy** and **Nutrient fields**) are the same as for the corresponding Foods table record (the ingredient), except that they are scaled by the amount of the food used in the recipe. Eg. if Amount=250 then all these field values are multiplied by 2.5. This is analogous to what happens to records in the Eaten table.    
   
Once all the ingredients of a recipe are known, the end markers of the recipes FoodDescription field in the Foods table record are set to " {recipe=[weight]g}" or " {recipe=[weight]g} *" where [weight] is the total amount in grams of all the ingredient foods. Furthermore the Energy and Nutrient fields (of this Foods table record) are scaled by 100/[weight]. This guarantees that when you LOG this recipe food using [weight] as the amount consumed you will get the correct Energy and Nutrient values (as if you had consumed the meal represented by the recipe).
 
*** 
# **Explanation of GUI elements**
The GUI elements on the screen are (starting at the top left hand corner and working across and down):   
- The **heading** of the screen (for example "Add Recipe", "Editing Recipe", or "Copying Recipe"). 
- The **help button** `?` which displays this help screen.
- The **navigation button** `<-` which transfers you to the Foods Table screen.
- A **text field** titled Description.
    - $descriptionHint
    - It will be the FoodDescription field of this recipe's Foods table record.
    - Once the recipe is saved by pressing the Confirm button the appropriate markers (described above) will be appended to create the final FoodDescription.
    - If left blank a toast titled "Please enter a description" will briefly appear after the Confirm button is pressed. Focus will remain on this screen.  
- A **text field** which when empty displays the text "Enter food filter text"
    - Type any text in the field and press the Enter key or equivalent. This filters the list of foods to those that contain this text anywhere in their description.
    - You can also type {text1}|{text2} to match descriptions that contain BOTH of these terms.
    - It is persistent while the app is running.
    - It is NOT case sensitive. 
- The **clear text field button** `x` which clears the above text field.    
- A **scrollable table viewer** which displays records from the Foods table.
    - When a particular food is selected (by tapping it) a dialog box appears where you can specify the amount in grams of the food that the recipe requires.
    - Press the **Confirm** button when you are ready to accept this recipe ingredient. This transfers focus back to this screen where the added ingredient will appear in the lower scrollable table viewer.
    - You can abort this process by tapping anywhere outside the dialog box. This closes it and focus returns to this screen.
    - If you select a liquid food a dialog with the title "CANNOT ADD THIS FOOD" will appear. Press the OK button or tap anywhere outside the dialog box to close it. Nothing happens and focus returns to this screen.    
- A **text label** which is of the form "Ingredients[weight] (g) Total"
    - The [weight] is the total amount of the ingredients in the recipe in grams.
    - It is automatically updated whenever the ingredients are added, edited or deleted.
- A **scrollable table viewer** which displays the recipe ingredient records from the Recipe table.
    - When an ingredient is selected (by tapping it) a selection panel appears at the bottom of the screen. It displays the description of the selected food followed by two buttons below it:
        - **Edit**: It enables the amount of the ingredient to be modified.
            - It opens a dialog box where you can modify the amount of the ingredient.
            - Press the **Confirm** button when you are ready to confirm your changes. This then transfers focus back to this screen where the just modified ingredient will be visible. The Total Ingredients amount will also be adjusted.        
            - You can abort this process by tapping anywhere outside the dialog box. This closes it and transfers focus back to this screen. The selection panel is also closed. 
        - **Delete**: deletes the selected ingredient from the Recipe table.
            - There is no warning dialog, it just irrevocably deletes the ingredient.
- A **Confirm** button.
    - $confirmHint
    - If the Description is blank or there are no ingredients in the recipe an informative Toast appears and nothing changes.
    - If you want to abort any actions on this screen you can press either of the two "back" buttons which sets focus back to the  Foods Table screen.    
""".trimIndent()
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
    val recipeHelpText = remember(screenTitle) { buildRecipeHelpText(screenTitle) }

    var description by rememberSaveable(initialDescription) { mutableStateOf(initialDescription) }
    val recipeSearchMode = remember(screenTitle, editingFoodId) {
        resolveRecipeSearchMode(screenTitle, editingFoodId)
    }
    val initialSearchQuery = remember(recipeSearchMode) {
        loadRecipeSearchQuery(recipeSearchMode)
    }
    var searchQuery by rememberSaveable(recipeSearchMode) { mutableStateOf(initialSearchQuery) }
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
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(searchQuery, recipeSearchMode) {
        storeRecipeSearchQuery(recipeSearchMode, searchQuery)
    }

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
                        foodSearchEntry?.savedStateHandle?.set("foodInsertedDescription", baseFood.foodDescription)
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
                        foodSearchEntry?.savedStateHandle?.set("foodUpdatedDescription", baseFood.foodDescription)

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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
                                dbHelper.searchFoods(searchQuery)
                            } else {
                                dbHelper.readFoodsFromDatabase()
                            }
                            keyboardController?.hide()
                        }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchFocusRequester)
                    )
                    IconButton(
                        onClick = {
                            searchQuery = ""
                            searchFocusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Clear food filter"
                        )
                    }
                }
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

    LaunchedEffect(foodId) {
        val duplicated = dbHelper.duplicateRecipesToFoodIdZero(foodId)
        if (!duplicated) {
            showPlainToast(context, "Unable to prepare recipe items for copying")
        }
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

    var amount by rememberSaveable(eatenFood) { mutableStateOf(eatenFood.amountEaten.toString()) }
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
    var amount by rememberSaveable { mutableStateOf("") }
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
    var amount by rememberSaveable { mutableStateOf("") }
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
    var densityText by rememberSaveable { mutableStateOf("") }
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
    onJson: () -> Unit = {},
    onDelete: () -> Unit,
    onCopy: () -> Unit = {},
    onConvert: () -> Unit = {},
    onUtilities: () -> Unit = {}
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
                Button(onClick = onJson) { Text("Json") }
                Button(onClick = onCopy) { Text("Copy") }
                Button(onClick = onConvert) { Text("Convert") }
                Button(onClick = onDelete) { Text("Delete") }
                Button(onClick = onUtilities) { Text("Utilities") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UtilitiesScreen(navController: NavController) {
    val context = LocalContext.current
    val dbHelper = remember { DatabaseHelper.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    var showHelpSheet by remember { mutableStateOf(false) }
    var showExportWarning by remember { mutableStateOf(false) }
    var showImportWarning by remember { mutableStateOf(false) }
    var weightInput by rememberSaveable { mutableStateOf("") }
    var weightDateMillis by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var showWeightDatePicker by remember { mutableStateOf(false) }
    var weightEntries by remember { mutableStateOf(emptyList<WeightEntry>()) }
    var selectedWeight by remember { mutableStateOf<WeightEntry?>(null) }
    var editingWeight by remember { mutableStateOf<WeightEntry?>(null) }
    var deletingWeight by remember { mutableStateOf<WeightEntry?>(null) }
    var editWeightInput by rememberSaveable { mutableStateOf("") }
    var editWeightDateMillis by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var showEditWeightDatePicker by remember { mutableStateOf(false) }
    val weightDateFormat = remember { SimpleDateFormat("d-MMM-yy", Locale.getDefault()) }
    val weightDatePickerState = rememberDatePickerState(initialSelectedDateMillis = weightDateMillis)
    val editWeightDatePickerState = rememberDatePickerState(initialSelectedDateMillis = editWeightDateMillis)
    val helpSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val utilitiesHelpText = """
# **Utilities**
This screen contains maintenance tools for your Foods database.

- **Export db**: Pressing this button overwrites the `foods.db` file in the `Internal storage\Download` directory.
    - For safety it is mediated by a warning dialog.
    - If the is no such file (eg. on first app use) then you are prompted to select that file in order to create a persistent link to it. Since it does not exist you will have to put an arbitrary file named foods.db into that directory first and then manually select it.
    - The first time this occurs cancel the (attempted) export, create/put foods.db (it can contain anything) in the `Internal storage\Download` directory and try again.
- **Import db**: Pressing this button replaces the app database with `foods.db` from the `Internal storage\Download` directory.
    - For safety it is mediated by a warning dialog.
    - If needed, you'll be prompted to select the file once.
- **Add weight**: Save weight measurements into the local Weight table; the date is required and entries appear below for edit/delete.
""".trimIndent()

    fun refreshWeights() {
        coroutineScope.launch {
            val entries = withContext(Dispatchers.IO) {
                dbHelper.readWeights()
            }
            weightEntries = entries
            selectedWeight = selectedWeight?.let { selected ->
                entries.find { it.weightId == selected.weightId }
            }
        }
    }

    fun parseWeightInput(input: String): Double? {
        val normalized = input.trim().replace(" ", "").replace(',', '.')
        return normalized.toDoubleOrNull()
    }

    LaunchedEffect(Unit) {
        refreshWeights()
    }

    LaunchedEffect(editingWeight?.weightId) {
        editWeightInput = editingWeight?.let { entry ->
            if (entry.weight % 1.0 == 0.0) {
                entry.weight.toInt().toString()
            } else {
                entry.weight.toString()
            }
        } ?: ""
        editWeightDateMillis = editingWeight?.dateWeight
            ?.takeIf { it.isNotBlank() }
            ?.let { dateString ->
                runCatching { weightDateFormat.parse(dateString)?.time }.getOrNull()
            } ?: System.currentTimeMillis()
        showEditWeightDatePicker = false
    }

    BackHandler(enabled = selectedWeight != null) {
        selectedWeight = null
    }

    LaunchedEffect(weightDateMillis) {
        weightDatePickerState.selectedDateMillis = weightDateMillis
    }

    LaunchedEffect(editWeightDateMillis) {
        editWeightDatePickerState.selectedDateMillis = editWeightDateMillis
    }

    fun exportDatabaseToUri(uri: Uri, onResult: (Boolean) -> Unit) {
        coroutineScope.launch {
            val exportSuccess = withContext(Dispatchers.IO) {
                try {
                    val dbFile = context.getDatabasePath("foods.db")
                    if (!dbFile.exists()) return@withContext false
                    context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        dbFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    } ?: return@withContext false
                    true
                } catch (_: Exception) {
                    false
                }
            }
            onResult(exportSuccess)
        }
    }

    fun importDatabaseFromUri(uri: Uri, onResult: (Boolean) -> Unit) {
        coroutineScope.launch {
            val importSuccess = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        dbHelper.replaceDatabaseFromStream(input)
                    } ?: false
                } catch (_: Exception) {
                    false
                }
            }
            onResult(importSuccess)
        }
    }

    fun findDownloadsDbUri(displayName: String): Uri? {
        val volumes = buildList {
            add(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            add(MediaStore.VOLUME_EXTERNAL)
            addAll(MediaStore.getExternalVolumeNames(context))
        }.distinct()
        val downloadsProjection = arrayOf(MediaStore.MediaColumns._ID)
        val filesProjection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.RELATIVE_PATH)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=?"
        val selectionArgs = arrayOf(displayName)
        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        for (volume in volumes) {
            val downloadsCollection = MediaStore.Downloads.getContentUri(volume)
            context.contentResolver.query(
                downloadsCollection,
                downloadsProjection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    return ContentUris.withAppendedId(downloadsCollection, id)
                }
            }
            val collection = MediaStore.Files.getContentUri(volume)
            var fallbackUri: Uri? = null
            context.contentResolver.query(
                collection,
                filesProjection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val relPath = cursor.getString(pathIndex) ?: ""
                    val uri = ContentUris.withAppendedId(collection, id)
                    if (relPath.startsWith("Download/") || relPath.startsWith("Downloads/")) {
                        return uri
                    }
                    if (fallbackUri == null) {
                        fallbackUri = uri
                    }
                }
            }
            if (fallbackUri != null) {
                return fallbackUri
            }
        }
        return null
    }

    fun loadExportOverwriteUri(): Uri? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString(KEY_EXPORT_OVERWRITE_URI, null) ?: return null
        return runCatching { uriString.toUri() }.getOrNull()
    }

    fun storeExportOverwriteUri(uri: Uri) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_EXPORT_OVERWRITE_URI, uri.toString()) }
    }

    fun clearExportOverwriteUri() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { remove(KEY_EXPORT_OVERWRITE_URI) }
    }

    fun loadImportUri(): Uri? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val importUriString = prefs.getString(KEY_IMPORT_URI, null)
        val importUri = importUriString?.let { runCatching { it.toUri() }.getOrNull() }
        if (importUri != null) return importUri
        val exportUriString = prefs.getString(KEY_EXPORT_OVERWRITE_URI, null) ?: return null
        return runCatching { exportUriString.toUri() }.getOrNull()
    }

    fun storeImportUri(uri: Uri) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_IMPORT_URI, uri.toString()) }
    }

    fun clearImportUri() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { remove(KEY_IMPORT_URI) }
    }

    val exportOverwritePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            showPlainToast(context, "Export cancelled")
            return@rememberLauncherForActivityResult
        }
        val uri = result.data?.data
        if (uri == null) {
            showPlainToast(context, "Export cancelled")
            return@rememberLauncherForActivityResult
        }
        val flags = result.data?.flags ?: 0
        val hasReadGrant = flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
        val hasWriteGrant = flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0
        if (hasReadGrant || hasWriteGrant) {
            try {
                when {
                    hasReadGrant && hasWriteGrant -> context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    hasReadGrant -> context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    else -> context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
            } catch (_: SecurityException) {
                // Best effort; some providers don't allow persistable permissions.
            }
        }
        storeExportOverwriteUri(uri)
        exportDatabaseToUri(uri) { exportSuccess ->
            if (exportSuccess) {
                showPlainToast(context, "Database exported")
            } else {
                showPlainToast(context, "Failed to export database")
            }
        }
    }

    val importPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            showPlainToast(context, "Import cancelled")
            return@rememberLauncherForActivityResult
        }
        val uri = result.data?.data
        if (uri == null) {
            showPlainToast(context, "Import cancelled")
            return@rememberLauncherForActivityResult
        }
        val flags = result.data?.flags ?: 0
        val hasReadGrant = flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
        if (hasReadGrant) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Best effort; some providers don't allow persistable permissions.
            }
        }
        storeImportUri(uri)
        importDatabaseFromUri(uri) { importSuccess ->
            if (importSuccess) {
                showPlainToast(context, "Database imported")
            } else {
                showPlainToast(context, "Failed to import database")
            }
        }
    }

    fun launchExport() {
        coroutineScope.launch {
            val storedUri = loadExportOverwriteUri()
            if (storedUri != null) {
                exportDatabaseToUri(storedUri) { exportSuccess ->
                    if (exportSuccess) {
                        showPlainToast(context, "Database exported")
                    } else {
                        clearExportOverwriteUri()
                        showPlainToast(context, "Please relink foods.db")
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                            putExtra(
                                Intent.EXTRA_MIME_TYPES,
                                arrayOf("application/octet-stream", "application/x-sqlite3", "application/vnd.sqlite3")
                            )
                            putExtra(
                                DocumentsContract.EXTRA_INITIAL_URI,
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI
                            )
                        }
                        exportOverwritePickerLauncher.launch(intent)
                    }
                }
                return@launch
            }
            val uri = withContext(Dispatchers.IO) { findDownloadsDbUri("foods.db") }
            if (uri != null) {
                storeExportOverwriteUri(uri)
                exportDatabaseToUri(uri) { exportSuccess ->
                    if (exportSuccess) {
                        showPlainToast(context, "Database exported")
                    } else {
                        showPlainToast(context, "Failed to export database")
                    }
                }
                return@launch
            }
            showPlainToast(context, "Pick foods.db once to enable overwrite")
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf("application/octet-stream", "application/x-sqlite3", "application/vnd.sqlite3")
                )
                putExtra(
                    DocumentsContract.EXTRA_INITIAL_URI,
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                )
            }
            exportOverwritePickerLauncher.launch(intent)
        }
    }

    fun launchImport() {
        coroutineScope.launch {
            val storedUri = loadImportUri()
            if (storedUri != null) {
                importDatabaseFromUri(storedUri) { importSuccess ->
                    if (importSuccess) {
                        showPlainToast(context, "Database imported")
                    } else {
                        clearImportUri()
                        showPlainToast(context, "Please relink foods.db")
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                            putExtra(
                                Intent.EXTRA_MIME_TYPES,
                                arrayOf("application/octet-stream", "application/x-sqlite3", "application/vnd.sqlite3")
                            )
                            putExtra(
                                DocumentsContract.EXTRA_INITIAL_URI,
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI
                            )
                        }
                        importPickerLauncher.launch(intent)
                    }
                }
                return@launch
            }
            val uri = withContext(Dispatchers.IO) { findDownloadsDbUri("foods.db") }
            if (uri != null) {
                storeImportUri(uri)
                importDatabaseFromUri(uri) { importSuccess ->
                    if (importSuccess) {
                        showPlainToast(context, "Database imported")
                    } else {
                        showPlainToast(context, "Failed to import database")
                    }
                }
                return@launch
            }
            showPlainToast(context, "Pick foods.db once to enable import")
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf("application/octet-stream", "application/x-sqlite3", "application/vnd.sqlite3")
                )
                putExtra(
                    DocumentsContract.EXTRA_INITIAL_URI,
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                )
            }
            importPickerLauncher.launch(intent)
        }
    }

    if (showWeightDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showWeightDatePicker = false },
            confirmButton = {
                Button(onClick = {
                    weightDatePickerState.selectedDateMillis?.let { selected ->
                        weightDateMillis = selected
                    }
                    showWeightDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                Button(onClick = { showWeightDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = weightDatePickerState)
        }
    }

    if (showEditWeightDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showEditWeightDatePicker = false },
            confirmButton = {
                Button(onClick = {
                    editWeightDatePickerState.selectedDateMillis?.let { selected ->
                        editWeightDateMillis = selected
                    }
                    showEditWeightDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                Button(onClick = { showEditWeightDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = editWeightDatePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Utilities", fontWeight = FontWeight.Bold) },
                actions = {
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { showExportWarning = true }) {
                        Text("Export db")
                    }
                    Button(onClick = { showImportWarning = true }) {
                        Text("Import db")
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text(
                    text = "Add weight",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        label = { Text("Weight") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("kg")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Date")
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { showWeightDatePicker = true }) {
                        Text(weightDateFormat.format(Date(weightDateMillis)))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            val weightValue = parseWeightInput(weightInput)
                            if (weightValue == null) {
                                showPlainToast(context, "Enter a valid weight")
                                return@Button
                            }
                            val dateValue = weightDateFormat.format(Date(weightDateMillis))
                            if (dateValue.isBlank()) {
                                showPlainToast(context, "Pick a date")
                                return@Button
                            }
                            coroutineScope.launch {
                                val success = withContext(Dispatchers.IO) {
                                    dbHelper.insertWeight(dateValue, weightValue)
                                }
                                if (success) {
                                    weightInput = ""
                                    refreshWeights()
                                    showPlainToast(context, "Weight saved")
                                } else {
                                    showPlainToast(context, "Failed to save weight")
                                }
                            }
                        }
                    ) {
                        Text("Save weight")
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Weight history",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (weightEntries.isEmpty()) {
                    Text(
                        text = "No weight entries yet.",
                        modifier = Modifier.align(Alignment.Start)
                    )
                } else {
                    WeightList(
                        weights = weightEntries,
                        selectedWeightId = selectedWeight?.weightId,
                        onWeightClicked = { entry ->
                            selectedWeight = if (selectedWeight?.weightId == entry.weightId) null else entry
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
            if (selectedWeight != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            selectedWeight = null
                        }
                )
            }
            AnimatedVisibility(
                visible = selectedWeight != null,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                selectedWeight?.let { entry ->
                    WeightSelectionPanel(
                        entry = entry,
                        onEdit = { editingWeight = entry },
                        onDelete = { deletingWeight = entry }
                    )
                }
            }
        }
    }

    if (showExportWarning) {
        AlertDialog(
            onDismissRequest = { showExportWarning = false },
            title = {
                Text(
                    text = "Export Database?",
                    color = Color.Red,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text("This will overwrite foods.db in the `Internal storage\\Download` directory. Think carefully before confirming!")
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(onClick = {
                        showExportWarning = false
                        launchExport()
                    }) {
                        Text("Confirm")
                    }
                }
            },
            dismissButton = {}
        )
    }

    if (showImportWarning) {
        AlertDialog(
            onDismissRequest = { showImportWarning = false },
            title = {
                Text(
                    text = "Import Database?",
                    color = Color.Red,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text("This will replace the app database with foods.db from the `Internal storage\\Download` directory. Think carefully before confirming!")
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(onClick = {
                        showImportWarning = false
                        launchImport()
                    }) {
                        Text("Confirm")
                    }
                }
            },
            dismissButton = {}
        )
    }

    if (editingWeight != null) {
        WeightEditDialog(
            weightValue = editWeightInput,
            onWeightChange = { editWeightInput = it },
            dateText = weightDateFormat.format(Date(editWeightDateMillis)),
            onPickDate = { showEditWeightDatePicker = true },
            onSave = {
                val entry = editingWeight ?: return@WeightEditDialog
                val weightValue = parseWeightInput(editWeightInput)
                if (weightValue == null) {
                    showPlainToast(context, "Enter a valid weight")
                    return@WeightEditDialog
                }
                val dateValue = weightDateFormat.format(Date(editWeightDateMillis))
                if (dateValue.isBlank()) {
                    showPlainToast(context, "Pick a date")
                    return@WeightEditDialog
                }
                coroutineScope.launch {
                    val success = withContext(Dispatchers.IO) {
                        dbHelper.updateWeight(entry.weightId, dateValue, weightValue)
                    }
                    if (success) {
                        editingWeight = null
                        refreshWeights()
                        showPlainToast(context, "Weight updated")
                    } else {
                        showPlainToast(context, "Failed to update weight")
                    }
                }
            },
            onDismiss = { editingWeight = null }
        )
    }

    if (deletingWeight != null) {
        WeightDeleteDialog(
            onDelete = {
                val entry = deletingWeight ?: return@WeightDeleteDialog
                coroutineScope.launch {
                    val success = withContext(Dispatchers.IO) {
                        dbHelper.deleteWeight(entry.weightId)
                    }
                    if (success) {
                        deletingWeight = null
                        selectedWeight = null
                        refreshWeights()
                        showPlainToast(context, "Weight deleted")
                    } else {
                        showPlainToast(context, "Failed to delete weight")
                    }
                }
            },
            onDismiss = { deletingWeight = null }
        )
    }

    if (showHelpSheet) {
        HelpBottomSheet(
            helpText = utilitiesHelpText,
            sheetState = helpSheetState,
            onDismiss = { showHelpSheet = false }
        )
    }
}

@Composable
private fun WeightList(
    weights: List<WeightEntry>,
    selectedWeightId: Int?,
    onWeightClicked: (WeightEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .border(1.dp, Color.Gray)
            .padding(8.dp)
    ) {
        items(weights) { entry ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onWeightClicked(entry) }
                    .padding(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = entry.dateWeight.ifBlank { "-" },
                        fontWeight = if (selectedWeightId == entry.weightId) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        }
                    )
                    Text(
                        text = "${formatAmount(entry.weight, decimals = 1)} kg",
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
private fun WeightSelectionPanel(
    entry: WeightEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = entry.dateWeight.ifBlank { "Unknown date" },
                fontWeight = FontWeight.Bold
            )
            WeightValueRow(label = "Weight:", value = formatAmount(entry.weight, decimals = 1))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                Button(onClick = onEdit) { Text("Edit") }
                Button(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun WeightValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(0.6f),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun WeightEditDialog(
    weightValue: String,
    onWeightChange: (String) -> Unit,
    dateText: String,
    onPickDate: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.98f)
                .widthIn(min = 360.dp, max = 720.dp)
                .height(IntrinsicSize.Min)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    text = "Edit weight",
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = weightValue,
                        onValueChange = onWeightChange,
                        label = { Text("Weight") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("kg")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Date")
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onPickDate) {
                        Text(dateText)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(onClick = onSave) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}

@Composable
private fun WeightDeleteDialog(
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete weight?",
                color = Color.Red,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text("This will remove the selected weight entry.")
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(onClick = onDelete) {
                    Text("Confirm")
                }
            }
        },
        dismissButton = {}
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DietSentry4AndroidTheme {
        // This preview will not work correctly with navigation
        // FoodSearchScreen(navController = rememberNavController())
    }
}
