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
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
private const val KEY_EXCHANGE_FOLDER_URI = "exchangeFolderUri"
private const val DATABASE_FILE_NAME = "foods.db"
private const val DAILY_CSV_FILE_NAME = "EatenDailyAll.csv"

// AI chat (Anthropic) — API key is held in plain SharedPreferences. The
// app sandbox protects per-device storage and the user-supplied key is only
// ever sent to api.anthropic.com over HTTPS.
private const val KEY_ANTHROPIC_API_KEY = "anthropicApiKey"
private const val KEY_ANTHROPIC_MODEL = "anthropicModel"
private const val DEFAULT_ANTHROPIC_MODEL = "claude-sonnet-4-6"
private const val ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages"
private const val ANTHROPIC_VERSION = "2023-06-01"
// Output-token cap for /v1/messages. Includes both extended-thinking tokens
// and the visible reply, so it has to be generous enough that long thinking
// + multi-iteration tool loops + a full NIP JSON all fit. Sonnet 4.6 / Opus
// 4.7 accept up to 64000; Haiku 4.5 up to 8192. 16384 is the sweet spot for
// our current workload — Claude only bills for tokens actually used.
private const val ANTHROPIC_MAX_TOKENS = 16384
private const val AI_IMAGE_MAX_DIM = 1568
private const val KEY_AI_WEB_SEARCH = "aiWebSearch"
private const val DEFAULT_AI_WEB_SEARCH = true
private const val WEB_SEARCH_TOOL_TYPE = "web_search_20250305"
private const val WEB_SEARCH_MAX_USES = 5
private const val KEY_AI_USE_NIP_PROMPT = "aiUseNipPrompt"
private const val DEFAULT_AI_USE_NIP_PROMPT = true
private const val KEY_AI_EXTENDED_THINKING = "aiExtendedThinking"
private const val DEFAULT_AI_EXTENDED_THINKING = false
private const val KEY_AI_USER_PROFILE = "aiUserProfile"

// Models for which Anthropic accepts `thinking: {type: "adaptive", ...}` on /v1/messages.
// Haiku 4.5 is NOT on this list — sending the thinking field with that model yields HTTP 400.
private val EXTENDED_THINKING_MODELS = setOf(
    "claude-opus-4-7",
    "claude-sonnet-4-6"
)
private const val WEB_SEARCH_COST_PER_REQUEST = 0.01
private const val CACHE_WRITE_MULTIPLIER = 1.25
private const val CACHE_READ_MULTIPLIER = 0.1
private const val LOOKUP_FOOD_TOOL_NAME = "lookup_food"
private const val LOOKUP_FOOD_MAX_RESULTS = 5
private const val MAX_TOOL_ITERATIONS = 12

private data class AiPricing(val inputPerMillion: Double, val outputPerMillion: Double)

private val ANTHROPIC_PRICING = mapOf(
    "claude-opus-4-7" to AiPricing(15.0, 75.0),
    "claude-sonnet-4-6" to AiPricing(3.0, 15.0),
    "claude-haiku-4-5-20251001" to AiPricing(1.0, 5.0)
)

// Session-scoped in-memory state (persists while app stays alive)
private var sessionSelectedFilterDateMillis: Long? = null
private var sessionAddRecipeSearchQuery: String = ""
private var sessionCopyRecipeSearchQuery: String = ""
private var sessionEditRecipeSearchQuery: String = ""
private var sessionPrefilledJson: String? = null
private var sessionPrefilledJsonCost: Double? = null

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
private val trailingRecipeStarRegex = Regex("\\s*\\*$")
private val trailingRecipeHashRegex = Regex("\\s*#\\s*$")

private fun isLiquidDescription(description: String): Boolean = mlSuffixRegex.containsMatchIn(description)
private fun descriptionUnit(description: String): String = if (isLiquidDescription(description)) "mL" else "g"
private fun descriptionDisplayName(description: String): String {
    val cleaned = description.replace(trailingMarkersRegex, "")
    return if (recipeMarkerRegex.containsMatchIn(cleaned)) {
        stripTrailingRecipeSuffix(cleaned)
    } else {
        cleaned
    }
}
private fun isRecipeDescription(description: String): Boolean = recipeMarkerRegex.containsMatchIn(description)
private fun removeRecipeMarker(description: String): String =
    stripTrailingRecipeSuffix(recipeMarkerRegex.replace(description, "").trimEnd())
private fun stripTrailingRecipeSuffix(description: String): String =
    description.replace(trailingRecipeStarRegex, "")
        .replace(trailingRecipeHashRegex, "")
        .trimEnd()

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
                    composable("addFoodByAi") {
                        AddFoodByAiScreen(navController = navController)
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
The main purpose of this screen is to **display a log of foods** you have consumed. You can also change their time stamps, amount eaten or delete them.
***
## Explanation of GUI elements
The GUI elements on the screen are (starting at the top left hand corner and working across and down):   
- The **heading** of the screen: "Eaten Table". 
- A **segmented button** with three options (Min, NIP, All). The selection is persistent between app restarts. 
    - **Min**: There are two cases:
        - when the Daily totals checkbox is **unchecked**, logs for individual foods are displayed comprising three rows:
            - The time stamp of the log (date+time)
            - The food description
            - The amount consumed (in g or mL as appropriate), followed by the logged energy in kJ
        - when the Daily totals checkbox is **checked**, logs consolidated by date are displayed comprising six rows:
            - The date of the foods time stamp
            - The text "Daily totals"
            - The total amount consumed on the day, labeled as g, mL, or "mixed units" if both are present. Amounts are still summed numerically, so mixed units are only an approximation if densities differ.
            - The total Energy (kJ), Fat, total (g), and Dietary Fibre (g) for the day.
    - **NIP**: There are two cases:
        - when the Daily totals checkbox is **unchecked**, logs for individual foods are displayed comprising eleven rows:
            - The time stamp of the log (date+time)
            - The food description
            - The amount consumed (in g or mL as appropriate)
            - The seven quantities mandated by FSANZ as the minimum required in a NIP, plus Dietary Fibre (g)
        - when the Daily totals checkbox is **checked**, logs consolidated by date are displayed comprising eleven rows:
            - The date of the foods time stamp
            - The text "Daily totals"
            - The total amount consumed on the day, labeled as g, mL, or "mixed units" as above.
            - The seven quantities mandated by FSANZ as the minimum required in a NIP, plus Dietary Fibre (g), summed across all of the days food item logs.
    - **All**: There are two cases:
        - when the Daily totals checkbox is **unchecked**, logs for individual foods are displayed comprising 26 rows:
            - The time stamp of the log (date+time)
            - The food description
            - The amount consumed (in g or mL as appropriate)
            - The 23 nutrient quantities we can record in the Foods table (including Energy) 
        - when the Daily totals checkbox is **checked**, logs consolidated by date are displayed comprising 27 rows (or 28 if comments exist):
            - The date of the foods time stamp
            - The text "Daily totals"
            - The text "Comments" followed by any Weight table comments for that date (only shown if present)
            - The text "My weight (kg)" followed by the corresponding weight entry for that date (or NA if not recorded)
            - The total amount consumed on the day, labeled as g, mL, or "mixed units" as above.
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
    - When this app is started the default is today's date. It remains persistent while the app stays open.
- A **scrollable table viewer** which displays records (possibly consolidated by date) from the Eaten table. If a particular logged food is selected (by tapping it) a selection panel appears at the bottom of the screen. It displays the description of the selected food log and its time stamp followed by two buttons below it:
    - **Edit**: It enables the amount and time stamp of the logged eaten food to be modified.
        - It opens a dialog box where you can specify the amount eaten as well as the date and time this has occurred (with the default being now).
        - Press the **Confirm** button when you are ready to confirm your changes. This then transfers focus back to the Eaten Table screen where the just modified food log will be visible and selected. The selection panel for this log (with the Edit and Deleted buttons) will close.
        - You can abort this process by tapping anywhere outside the dialog box. This closes it and transfers focus in the same way as described above.
    - **Delete**: deletes the selected food log from the Eaten table.
        - It opens a dialog which warns you that you will be deleting the selected food log from the Eaten table.
        - This is irrevocable if you press the **Delete** button.
        - You can change you mind about doing this by just tapping anywhere outside the dialog box. This closes it and returns focus to the Eaten Table screen. The selection panel (with the Edit and Deleted buttons) is also closed.
    - If food logs consolidated by date are displayed (ie. the "Daily totals" check box is ticked), selection for editing or deletion is not possible. Instead, tapping a day's daily totals card opens a bottom sheet with two actions (see next section).
***
# **AI explanation of a day's daily totals**
When the **Daily totals** checkbox is ticked, tapping any day's totals card slides up a bottom sheet from the bottom of the screen with two actions:
- **Explain this day (AI)** — sends the day's complete totals (all 24 nutrient values, the total amount eaten, and any recorded weight + weight comments) plus your **profile** text (see below) to Anthropic's Claude. The reply — 2 to 3 short paragraphs assessing intake against Australian NHMRC NRVs — appears in a dialog with the per-call API cost shown underneath. Requires an Anthropic API key set on the **Add Food using AI** screen (gear icon).
- **Edit my profile** — opens a free-text editor (the **Custom Instructions** field) for a small persistent paragraph that describes you (e.g. "age 67 male, weight 89kg, dietary goals: low sodium"). The text is sent alongside each daily totals query so Claude can tailor its assessment. Empty profile is fine — Claude will give general adult Australian guidance. The text persists across app launches.

**Settings used by this flow:**
- **API key** and **model** are read from the AI Settings dialog on the **Add Food using AI** screen — switching from Sonnet 4.6 to Haiku 4.5 over there changes the next Explain call too.
- **User profile** persists in SharedPreferences under `KEY_AI_USER_PROFILE`.
- **Web search** and **Extended thinking** toggles are *not* honoured by this flow — they're hardcoded off so each Explain call has a predictable cost (~\$0.01 on Sonnet 4.6, ~\$0.003 on Haiku 4.5). Those toggles still apply to the AI chat screen.
- **System prompt** is always `EXPLAINsysprompt.txt` (different from the AI chat's NIP/Recipe/General prompts) — the NIP-mode toggle in AI Settings has no effect here.

The system prompt is bundled at `app/src/main/assets/EXPLAINsysprompt.txt` and instructs Claude to use NHMRC NRVs, Australian English, and to flag nutrients that are notably under- or over-consumed (e.g. saturated fat above ~22 g, sodium above ~2300 mg, alcohol present in non-trivial amounts).
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
    val filterDateDisplayFormatter = remember { SimpleDateFormat("d-MMM-yy", Locale.getDefault()) }
    val filteredEatenFoods = remember(eatenFoods, filterByDate, selectedFilterDateMillis) {
        if (!filterByDate) eatenFoods else {
            val matchDate = filterDateFormatter.format(Date(selectedFilterDateMillis))
            eatenFoods.filter { it.dateEaten == matchDate }
        }
    }
    val dailyTotals = remember(filteredEatenFoods) { aggregateDailyTotals(filteredEatenFoods) }
    var weightEntries by remember { mutableStateOf(emptyList<WeightEntry>()) }
    val weightByDate = remember(weightEntries) { weightEntries.associateBy { it.dateWeight } }
    var showFilterDatePicker by remember { mutableStateOf(false) }
    val filterDatePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedFilterDateMillis)
    LaunchedEffect(selectedFilterDateMillis) {
        filterDatePickerState.selectedDateMillis = selectedFilterDateMillis
        sessionSelectedFilterDateMillis = selectedFilterDateMillis
    }
    LaunchedEffect(displayDailyTotals) {
        if (displayDailyTotals) {
            weightEntries = withContext(Dispatchers.IO) {
                dbHelper.readWeights()
            }
        }
    }

    // Daily-totals AI explain state — populated when the user taps a daily-totals card.
    var selectedDailyTotals by remember { mutableStateOf<DailyTotals?>(null) }
    var explanationDailyTotals by remember { mutableStateOf<DailyTotals?>(null) }
    var explanationState by remember { mutableStateOf<EatenExplanationState>(EatenExplanationState.Idle) }
    var showProfileDialog by remember { mutableStateOf(false) }
    val explainSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val explainSystemPrompt = remember { loadExplainSystemPrompt(context) }
    val sheetScope = rememberCoroutineScope()

    LaunchedEffect(explanationDailyTotals) {
        val totals = explanationDailyTotals ?: return@LaunchedEffect
        val apiKey = sharedPreferences.getString(KEY_ANTHROPIC_API_KEY, null)
        if (apiKey.isNullOrBlank()) {
            explanationState = EatenExplanationState.Error(
                "No Anthropic API key set.\nGo to Foods Table → AI button → gear icon to add your key."
            )
            return@LaunchedEffect
        }
        val model = sharedPreferences.getString(KEY_ANTHROPIC_MODEL, DEFAULT_ANTHROPIC_MODEL)
            ?: DEFAULT_ANTHROPIC_MODEL
        val userProfile = sharedPreferences.getString(KEY_AI_USER_PROFILE, "") ?: ""
        val weightEntry = weightByDate[totals.date]
        val userMessage = formatDailyTotalsForAi(totals, weightEntry, userProfile)
        explanationState = EatenExplanationState.Loading
        val result = callAnthropicApi(
            apiKey = apiKey,
            model = model,
            messages = listOf(AiChatMessage(role = "user", text = userMessage)),
            enableWebSearch = false,
            nipMode = true,
            primaryPrompt = explainSystemPrompt,
            generalSystemPrompt = "",
            extendedThinking = false,
            enableFoodLookupTool = false,
            dbHelper = null,
            recipeMode = false
        )
        explanationState = result.fold(
            onSuccess = { response ->
                EatenExplanationState.Success(
                    text = response.text,
                    costUsd = computeAiCostUsd(response.usage, model)
                )
            },
            onFailure = { e ->
                EatenExplanationState.Error(e.message ?: "Unknown error")
            }
        )
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
                            val weightEntry = weightByDate[totals.date]
                            DailyTotalsCard(
                                totals = totals,
                                showNutritionalInfo = showNutritionalInfo,
                                showExtraNutrients = showExtraNutrients,
                                weightEntry = weightEntry,
                                showWeightComments = showExtraNutrients,
                                onClick = { selectedDailyTotals = totals }
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
                onDismiss = {
                    showEditDialog = false
                    selectedEatenFood = null
                },
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
                onDismiss = {
                    showDeleteEatenDialog = false
                    selectedEatenFood = null
                },
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

    // Action sheet shown when a daily-totals card is tapped.
    val sheetTotals = selectedDailyTotals
    if (sheetTotals != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedDailyTotals = null },
            sheetState = explainSheetState
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                Text(
                    text = "${sheetTotals.date} — ${formatNumber(sheetTotals.amountEaten, decimals = 1)} ${sheetTotals.unitLabel}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // Animate the sheet closed before opening the result
                            // dialog, so the modal-focus stack unwinds cleanly.
                            val t = sheetTotals
                            sheetScope.launch {
                                explainSheetState.hide()
                                selectedDailyTotals = null
                                explanationDailyTotals = t
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Explain this day (AI)",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // Animate the sheet closed before opening the profile
                            // dialog, otherwise the AlertDialog's TextField fails
                            // to claim window focus and the cursor doesn't appear.
                            sheetScope.launch {
                                explainSheetState.hide()
                                selectedDailyTotals = null
                                showProfileDialog = true
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit my profile",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }

    // Result dialog for the explanation request.
    if (explanationState !is EatenExplanationState.Idle) {
        AlertDialog(
            onDismissRequest = {
                explanationState = EatenExplanationState.Idle
                explanationDailyTotals = null
            },
            title = {
                Text(
                    when (explanationState) {
                        is EatenExplanationState.Loading -> "Analysing…"
                        is EatenExplanationState.Success -> "Daily totals explanation"
                        is EatenExplanationState.Error -> "Error"
                        else -> ""
                    }
                )
            },
            text = {
                when (val state = explanationState) {
                    is EatenExplanationState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is EatenExplanationState.Success -> {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text(text = state.text)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "API cost: ${formatUsdCost(state.costUsd)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    is EatenExplanationState.Error -> {
                        Text(text = state.message)
                    }
                    else -> {}
                }
            },
            confirmButton = {
                if (explanationState !is EatenExplanationState.Loading) {
                    TextButton(onClick = {
                        explanationState = EatenExplanationState.Idle
                        explanationDailyTotals = null
                    }) { Text("OK") }
                }
            }
        )
    }

    // Profile editor — mirrors the BloodPressureTracker "Custom Instructions"
    // field: OutlinedTextField in a Column inside AlertDialog.text, with
    // minLines=4 / maxLines=4 (fixed height, no placeholder).
    if (showProfileDialog) {
        var profileText by remember {
            mutableStateOf(sharedPreferences.getString(KEY_AI_USER_PROFILE, "") ?: "")
        }
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text("My profile") },
            text = {
                Column {
                    OutlinedTextField(
                        value = profileText,
                        onValueChange = { profileText = it },
                        label = { Text("Custom Instructions") },
                        minLines = 4,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    sharedPreferences.edit {
                        putString(KEY_AI_USER_PROFILE, profileText.trim())
                    }
                    showProfileDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }) { Text("Cancel") }
            }
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
    showExtraNutrients: Boolean,
    weightEntry: WeightEntry?,
    showWeightComments: Boolean,
    onClick: (() -> Unit)? = null
) {
    val weightText = weightEntry?.let { formatWeight(it.weight) } ?: "NA"
    val commentsText = weightEntry?.comments?.trim()
    val cardModifier = if (onClick != null) {
        Modifier.fillMaxWidth().clickable(onClick = onClick)
    } else {
        Modifier.fillMaxWidth()
    }
    Card(
        modifier = cardModifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(totals.date, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Daily totals", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(2.dp))
            if (showWeightComments && commentsText != null) {
                WeightValueRow(
                    label = "Comments:",
                    value = commentsText,
                    valueMaxLines = Int.MAX_VALUE,
                    valueOverflow = TextOverflow.Clip,
                    valueTextAlign = TextAlign.Start,
                    rowFillFraction = 1f
                )
            }
            if (showExtraNutrients) {
                WeightValueRow(label = "My weight (kg)", value = weightText)
            }
            if (showNutritionalInfo) {
                NutritionalInfo(
                    eatenFood = totals.toEatenFoodPlaceholder(),
                    unit = totals.unitLabel,
                    showExtraNutrients = showExtraNutrients
                )
            } else {
                val amountLabel = when (totals.unitLabel.lowercase(Locale.getDefault())) {
                    "ml" -> "Amount (mL)"
                    "g" -> "Amount (g)"
                    "mixed units" -> "Amount (g or mL)"
                    else -> "Amount (${totals.unitLabel})"
                }
                NutrientRow(label = amountLabel, value = totals.amountEaten)
                NutrientRow(label = "Energy (kJ):", value = totals.energy)
                NutrientRow(label = "Fat, total (g):", value = totals.fatTotal)
                NutrientRow(label = "Dietary Fibre (g):", value = totals.dietaryFibre)
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
                val energyText = formatNumber(eatenFood.energy, decimals = 0)
                Text("$amountText$unit  ${energyText}kJ", style = MaterialTheme.typography.bodyMedium)
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
            add("Dietary Fibre (g):" to eatenFood.dietaryFibre)
            if (!hideFibreAndCalcium) {
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

Its purpose is to display a list of foods from the Foods table and allow interaction with a selected food. The primary purpose being to **LOG the selected food**.
***
# **Explanation of GUI elements**
The GUI elements on the screen are (starting at the top left hand corner and working across and down):   
- The **heading** of the screen: "Foods Table". 
- A **segmented button** with three options (Min, NIP, All). The selection is persistent between app restarts. 
    - **Min**: only displays the text description of food items.
    - **NIP**: additionally displays the minimum mandated nutrient information (per 100g or 100mL of the food) as required in by FSANZ on Nutritional Information Panels (NIP), plus Dietary Fibre (g)
    - **All**: Displays all nutrient fields stored in the Foods table (there are 23, including Energy) PLUS the notes text field
- The **help button** `?` which displays this help screen.
- The **navigation button** `->` which transfers you to the Eaten Table screen.
- A **text field** which when empty displays the text "Enter food filter text"
    - Type any text in the field and press the Enter key or equivalent. This filters the list of foods to those that contain this text anywhere in their description.
    - You can also type {text1}|{text2} to match descriptions that contain BOTH of these terms.
    - It is NOT case sensitive
- The **clear text field button** `x` which clears the above text field    
- A **scrollable table viewer** which displays records from the Foods table. When a particular food is selected (by tapping it) a selection panel appears at the bottom of the screen. It displays the description of the selected food followed by nine buttons arranged in two rows. The top row holds the actions that operate on the selected food: **LOG**, **Edit**, **Copy**, **Convert**, **Delete**. The bottom row holds the actions that do not depend on the selection: **Add**, **Json**, **AI**, **Utilities**.
    - **LOG**: logs the selected food into the Eaten Table.
        - It opens a dialog box where you can specify the amount eaten as well as the date and time this has occurred (with the default being now).
        - Press the **Confirm** button when you are ready to log your food. This transfers focus to the Eaten Table screen where the just logged food will be visible. Read the help on that Screen for more help.
        - You can abort this process by tapping anywhere outside the dialog box. This closes it.
    - **Edit**: allows editing of the selected food.
        - It opens "Editing Solid Food" or "Editing Liquid Food" screens unless the description contains `{recipe=...g}`, in which case it opens "Editing Recipe".
    - **Add**: adds a new food to the database.
        - It opens a screen titled "Add Food". Press the help button on that screen for more help.
        - The original selected food has no relevance to this activity. It is just a way of making the Add button available.
    - **Json**: adds a new food (or a recipe) to the database based on Json text.
        - It opens a screen titled "Add Food using Json".  Press the help button on that screen for more help.
        - Both NIP food JSON (24 nutrient fields) and Recipe JSON (`type: "recipe"`, `ingredients[]`) are accepted on that screen. Recipe JSON creates a full recipe (Foods row + Recipe rows linked by FoodId) on Confirm — same as building it by hand on the Add Recipe screen.
        - The original selected food has no relevance to this activity. It is just a way of making the Json button available.
    - **AI**: adds a new food to the database using Anthropic's Claude (requires your own Anthropic API key — set it in the AI screen's settings dialog).
        - It opens a screen titled "Add Food using AI". Press the help button on that screen for more help.
        - In **NIP mode** (the default) Claude returns a Diet Sentry compatible JSON which is automatically "auto-pumped" into the **Add Food using Json** screen — one tap on **Confirm** there adds the food and lands on this Foods Table with the new food highlighted.
        - With NIP mode on, if your message contains the word "recipe" Claude swaps to a recipe-mode prompt and the auto-pumped JSON is a Recipe JSON instead — Confirm builds the full recipe (Foods row + Recipe rows linked by FoodId).
        - In general mode (NIP toggle off) Claude is a regular chat assistant; replies stay in the AI screen, and "recipe" in the message has no special effect.
        - The original selected food has no relevance to this activity. It is just a way of making the AI button available.
    - **Copy**: makes a copy of the selected food.
        - If the selected food is a Solid it opens a screen titled "Copying Solid Food"
        - If the selected food is a Liquid it opens a screen titled "Copying Liquid Food"
        - If the selected food is a Recipe it opens a screen titled "Copying Recipe"
        The type of a food (Solid, Liquid or Recipe) is coded in its description field, as explained in the next section.
    - **Convert**: converts a liquid food to a solid. 
        - If the food is a liquid it displays a dialog that enables the foods density to be input in g/mL
        - A new solid food is then created on a per 100g basis using that density.
        - The description of this new food removes the trailing " mL" marker and appends `{density=...g/mL}` plus the user-added ` #` suffix so you can see how it was derived.
    - **Delete**: deletes the selected food from the database.
        - It opens a dialog which warns you that you will be deleting the selected food.
        - This is irrevocable if you press the **Confirm** button.
        - You can change your mind about doing this by just tapping anywhere outside the dialog box. This closes it.
    - **Utilities**: various database maintenance tools and other activities.
        - It opens a screen titled "Utilities".  Press the help button on that screen for more help.
        - The original selected food has no relevance to this activity. It is just a way of making the Utilities button available.
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
notes               TEXT
```
The FoodId field is never explicitly displayed or considered. It is a Primary Key that is auto incremented when a record is created.
The values of nutrients are per 100g or 100mL as appropriate and the units are as mandated in the FSANZ code.
The notes field is optional free text and is shown only when the All option is selected.

- **If a FoodDescription ends in the characters " mL" or " mL#"** the food is considered a Liquid, and nutrient values are per 100mL, The "#" character indicates that it is not part of the original database of foods.

- **If a FoodDescription ends in the characters " {recipe=[weight]g}"** the food is considered a Recipe and can only be made up of solids and thus its nutrient values are per 100g. It is also never a part of the original database.

- **If a FoodDescription ends in any other pattern of characters than those specified above** the food is considered a Solid, and nutrient values are per 100g. If additionally it ends in " #" then it is also never a part of the original database.
- **Foods converted from liquids** include a `{density=...g/mL}` marker in the description to record the density used for conversion.
- **AI-generated foods** end with ` (AI) #` (solid), ` (AI) mL#` (liquid), or ` (AI) {recipe=<weight>g}` (recipe). The `(AI)` substring makes AI-sourced rows easy to identify and filter on in the Foods Table.

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

- **The Foods table does not explicitly consider servings, though they might be noted in the FoodDescription text field or notes.** 

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
            val showFoodSelectionPanel = selectedFood != null &&
                !showSelectDialog &&
                !showDeleteDialog &&
                !showConvertDialog
            AnimatedVisibility(
                visible = showFoodSelectionPanel,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                selectedFood?.let { food ->
                    SelectionPanel(
                        food = food,
                        onSelect = { showSelectDialog = true }, // Show the dialog on click
                        onEdit = {
                            selectedFood = null
                            if (isRecipeDescription(food.foodDescription)) {
                                navController.navigate("editRecipe/${food.foodId}")
                            } else {
                                navController.navigate("editFood/${food.foodId}")
                            }
                        },
                        onAdd = {
                            selectedFood = null
                            navController.navigate("insertFood")
                        },
                        onJson = {
                            selectedFood = null
                            navController.navigate("addFoodByJson")
                        },
                        onAi = {
                            selectedFood = null
                            navController.navigate("addFoodByAi")
                        },
                        onCopy = {
                            selectedFood = null
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
                                selectedFood = null
                            }
                        },
                        onDelete = { showDeleteDialog = true },
                        onUtilities = {
                            selectedFood = null
                            navController.navigate("utilities")
                        }
                    )
                }
            }
        }
    }

    if (showSelectDialog) {
        selectedFood?.let { food ->
            SelectAmountDialog(
                food = food,
                onDismiss = {
                    showSelectDialog = false
                    selectedFood = null
                },
                onConfirm = { amount, dateTime ->
                    dbHelper.logEatenFood(food, amount, dateTime)
                    showSelectDialog = false
                    selectedFood = null
                    navController.navigate("eatenLog")
                }
            )
        }
    }
    if (showDeleteDialog) {
        selectedFood?.let { food ->
            DeleteConfirmationDialog(
                food = food,
                onDismiss = {
                    showDeleteDialog = false
                    selectedFood = null
                },
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
                onDismiss = {
                    showConvertDialog = false
                    selectedFood = null
                },
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
                        selectedFood = null
                        showConvertDialog = false
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
    var notes by rememberSaveable(food) { mutableStateOf(food!!.notes) }

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
- On first displaying this screen all the input fields will be populated with values from the selected Food, however the Description field will have the " mL" or " mL#" markers omitted. These will be reinstated after edit confirmation. This means you cannot change a Liquid food into a Solid one directly through editing. Use the Convert button on the Foods Table to create a new solid (annotated with a density marker) and edit that instead.
- Modify fields as required using decimals where needed and tap Confirm to save your changes. If a field entry is not valid (eg. text, blank or not a number in a field requiring numbers) the Confirm button will be disabled.
- Notes is an optional free text (multi-line) field. It is saved with the food and shown in the Foods table when All is selected.
- You can press either of two "back" buttons to cancel the editing process and return focus to the Foods Table screen.
- If confirmation succeeds the selected food is amended and focus passes to the Foods Table screen with the filter text being set to the just edited foods description (with markers reinstated). This allows you to review the results of the edit and is especially important if the description has changed significantly and you would not have been able find the food again.    
""".trimIndent()
    } else {
        """
# **Editing Solid Food**
- These are foods for which the Energy and nutrient values are given on a **per 100g basis**
- On first displaying this screen all the input fields will be populated with values from the selected Food, however the Description field will have the " #" marker (if any) omitted. This will be reinstated after edit confirmation. This screen preserves the solid suffix, so create a liquid food using Add or Copy if needed.
- Modify fields as required using decimals where needed and tap Confirm to save your changes. If a field entry is not valid (eg. text, blank or not a number in a field requiring numbers) the Confirm button will be disabled.
- Notes is an optional free text (multi-line) field. It is saved with the food and shown in the Foods table when All is selected.
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
                            alcohol = alcohol.toDouble(),
                            notes = notes
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
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                LabeledValueField(
                    label = "Description",
                    value = description,
                    onValueChange = { description = it },
                    wrapLabel = true,
                    labelSpacing = 8.dp,
                    valueFillFraction = 1f,
                    singleLine = false,
                    minLines = 3,
                    rowVerticalAlignment = Alignment.Top,
                    labelTopPadding = 3.5.dp
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
                LabeledValueField(
                    label = "Notes",
                    value = notes,
                    onValueChange = { notes = it },
                    wrapLabel = true,
                    labelSpacing = 8.dp,
                    valueFillFraction = 1f,
                    singleLine = false,
                    minLines = 3,
                    rowVerticalAlignment = Alignment.Top
                )
            }
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
    var notes by rememberSaveable(food) { mutableStateOf(food!!.notes) }

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
        - Notes is pre-filled from the selected food, can be edited (multi-line), and is saved with the new record.
        - Focus will then pass to the Foods Table screen with the filter text being set to the just created foods description (with the liquid marker appended). This allows you to review the results of the foods creation and is especially important if the Description is unintuitive and finding the food in the table might be difficult.
        - As before you can press either of the two "back" buttons to cancel the copying process and return focus to the Foods Table screen.
        """.trimIndent()
    } else {
        """          
        # **Copying Solid Food**
        - When the Copy button is tapped from the Foods Table screen with a **solid food selected**, this screen headed Copying Solid Food is displayed
        - Its layout and presentation is identical to the Editing Solid Food screen, the difference being that instead of modifying the selected food record, a new one is created with the displayed field values.
        - Clearly before pressing the Confirm button you can modify any of the field entries, so you are not really creating an exact copy just a food based on the selection.
        - Notes is pre-filled from the selected food, can be edited (multi-line), and is saved with the new record.
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
                            alcohol = alcohol.toDouble(),
                            notes = notes
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
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                LabeledValueField(
                    label = "Description",
                    value = description,
                    onValueChange = { description = it },
                    wrapLabel = true,
                    labelSpacing = 8.dp,
                    valueFillFraction = 1f,
                    singleLine = false,
                    minLines = 3,
                    rowVerticalAlignment = Alignment.Top,
                    labelTopPadding = 3.5.dp
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
                LabeledValueField(
                    label = "Notes",
                    value = notes,
                    onValueChange = { notes = it },
                    wrapLabel = true,
                    labelSpacing = 8.dp,
                    valueFillFraction = 1f,
                    singleLine = false,
                    minLines = 3,
                    rowVerticalAlignment = Alignment.Top
                )
            }
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
    var notes by rememberSaveable { mutableStateOf("") }

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
        - It indicates that the new food will be of solid type and thus the final FoodDescription will terminate with the marker " #". You do not need to do this explicitly in the Description field.
        - The Energy and nutrition fields are assumed to be on a per 100g basis. 
    - **Liquid**. If this is selected the new food will be of liquid type.
        - The final FoodDescription will terminate with the marker " mL#". You do not need to do this explicitly in the Description field.
        - The Energy and nutrition fields are assumed to be on a per 100mL basis.
    - **Recipe**. If this radio button is selected focus will immediately pass to the Add Recipe screen.
        - Any input fields you might have filled in will be ignored.
- The following rows display the record fields that need to be filled in to create a new (non-recipe) food.
    - Enter the description, nutritient values and Notes. Blanks outside of the Description field are treated as 0. As long as the Description field is not blank the Confirm button will be enabled.
    - Notes is an optional free text (multi-line) field. It is saved with the food and shown in the Foods table when All is selected.
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
                            alcohol = alcohol.toDoubleOrNull() ?: 0.0,
                            notes = notes
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
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
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
                    valueFillFraction = 1f,
                    singleLine = false,
                    minLines = 3,
                    rowVerticalAlignment = Alignment.Top,
                    labelTopPadding = 3.5.dp
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
                LabeledValueField(
                    label = "Notes",
                    value = notes,
                    onValueChange = { notes = it },
                    wrapLabel = true,
                    labelSpacing = 8.dp,
                    valueFillFraction = 1f,
                    singleLine = false,
                    minLines = 3,
                    rowVerticalAlignment = Alignment.Top
                )
            }
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

// Inserts a recipe-shaped JSON into the Foods + Recipe tables, replicating the
// AddRecipeScreen Confirm math. On success returns the new recipe's full
// FoodDescription (with the trailing " {recipe=Xg}" marker) so the caller can
// highlight it on the Foods Table.
private fun insertRecipeFromRecipeJson(
    json: org.json.JSONObject,
    dbHelper: DatabaseHelper
): Result<String> {
    val rawDescription = json.optString("FoodDescription").trim()
    if (rawDescription.isBlank()) {
        return Result.failure(Exception("Recipe FoodDescription is required"))
    }
    val ingredientsJson = json.optJSONArray("ingredients")
        ?: return Result.failure(Exception("Recipe ingredients[] is required"))
    if (ingredientsJson.length() == 0) {
        return Result.failure(Exception("Recipe needs at least one ingredient"))
    }

    data class ResolvedIngredient(val food: Food, val amount: Double)
    val resolved = mutableListOf<ResolvedIngredient>()
    for (i in 0 until ingredientsJson.length()) {
        val ing = ingredientsJson.optJSONObject(i)
            ?: return Result.failure(Exception("ingredients[$i] is not an object"))
        val fid = ing.optInt("FoodId", -1)
        if (fid <= 0) {
            return Result.failure(Exception("ingredients[$i] missing FoodId"))
        }
        val amount = ing.optDouble("AmountUsed", -1.0)
        if (amount.isNaN() || amount <= 0.0) {
            return Result.failure(Exception("ingredients[$i] AmountUsed must be > 0"))
        }
        val food = dbHelper.getFoodById(fid)
            ?: return Result.failure(Exception("FoodId $fid not in Foods table"))
        if (isLiquidDescription(food.foodDescription)) {
            return Result.failure(Exception("FoodId $fid is a liquid; recipes need solids"))
        }
        resolved.add(ResolvedIngredient(food, amount))
    }

    // Clear orphan temp rows from any aborted manual recipe-add session so the
    // updateRecipeFoodIdForTemporaryRecords call below only links our rows.
    dbHelper.deleteRecipesWithFoodIdZero()

    for (ri in resolved) {
        val ok = dbHelper.insertRecipeFromFood(
            food = ri.food,
            amount = ri.amount.toFloat(),
            foodId = 0,
            copyFlag = 0
        )
        if (!ok) {
            dbHelper.deleteRecipesWithFoodIdZero()
            return Result.failure(Exception("Unable to add ingredient to Recipe table"))
        }
    }

    val totalAmount = resolved.sumOf { it.amount }
    fun aggregate(field: (Food) -> Double): Double =
        resolved.sumOf { field(it.food) * it.amount / 100.0 }
    val scale = 100.0 / totalAmount
    fun scaled(s: Double) = s * scale

    val sanitized = stripTrailingRecipeSuffix(rawDescription).trim()
    val recipeWeightText = formatNumber(totalAmount, decimals = 0)
    val notes = json.optString("notes", "").trim()
    val baseFood = Food(
        foodId = 0,
        // Append " (AI)" before the trailing {recipe=Xg} marker so AI-generated
        // recipes are visibly distinguishable in the Foods Table — mirrors the
        // " (AI) #" convention used for non-recipe AI foods.
        foodDescription = "$sanitized (AI) {recipe=${recipeWeightText}g}",
        energy = scaled(aggregate { it.energy }),
        protein = scaled(aggregate { it.protein }),
        fatTotal = scaled(aggregate { it.fatTotal }),
        saturatedFat = scaled(aggregate { it.saturatedFat }),
        transFat = scaled(aggregate { it.transFat }),
        polyunsaturatedFat = scaled(aggregate { it.polyunsaturatedFat }),
        monounsaturatedFat = scaled(aggregate { it.monounsaturatedFat }),
        carbohydrate = scaled(aggregate { it.carbohydrate }),
        sugars = scaled(aggregate { it.sugars }),
        dietaryFibre = scaled(aggregate { it.dietaryFibre }),
        sodium = scaled(aggregate { it.sodium }),
        calciumCa = scaled(aggregate { it.calciumCa }),
        potassiumK = scaled(aggregate { it.potassiumK }),
        thiaminB1 = scaled(aggregate { it.thiaminB1 }),
        riboflavinB2 = scaled(aggregate { it.riboflavinB2 }),
        niacinB3 = scaled(aggregate { it.niacinB3 }),
        folate = scaled(aggregate { it.folate }),
        ironFe = scaled(aggregate { it.ironFe }),
        magnesiumMg = scaled(aggregate { it.magnesiumMg }),
        vitaminC = scaled(aggregate { it.vitaminC }),
        caffeine = scaled(aggregate { it.caffeine }),
        cholesterol = scaled(aggregate { it.cholesterol }),
        alcohol = scaled(aggregate { it.alcohol }),
        notes = notes
    )

    val newFoodId = dbHelper.insertFoodReturningId(baseFood)
    if (newFoodId == null) {
        dbHelper.deleteRecipesWithFoodIdZero()
        return Result.failure(Exception("Unable to save recipe to Foods table"))
    }
    val linked = dbHelper.updateRecipeFoodIdForTemporaryRecords(newFoodId)
    if (!linked) {
        dbHelper.deleteRecipesWithFoodIdZero()
        return Result.failure(Exception("Recipe items not linked to new food"))
    }
    return Result.success(baseFood.foodDescription)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodByJsonScreen(navController: NavController) {
    val context = LocalContext.current
    val dbHelper = remember { DatabaseHelper.getInstance(context) }
    var jsonText by rememberSaveable {
        mutableStateOf(sessionPrefilledJson?.also { sessionPrefilledJson = null } ?: "")
    }
    val initialCallCost: Double? = remember {
        sessionPrefilledJsonCost?.also { sessionPrefilledJsonCost = null }
    }
    var showHelpSheet by remember { mutableStateOf(false) }
    val helpSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val jsonHelpText = """
# **Add Food using Json**
- Reached two ways:
    - **Manually**: tap the **Json** button on the **Foods Table** screen.
    - **Automatically (auto-pump)**: when the **Add Food using AI** screen produces a JSON reply, it is "auto-pumped" here pre-filled in the text field — ready for one-tap **Confirm**. Both NIP-mode JSON (regular food) and recipe-mode JSON (recipe of ingredients) auto-pump.
- Like other screens it has a **help** and a **navigation** button in the top row, then a **text field** that takes up the rest of the screen, followed by a **Confirm** button.
- Two JSON shapes are accepted:
    - **NIP food JSON** — `FoodDescription` plus the 24 nutrient fields. On Confirm a single Food row is added.
    - **Recipe JSON** — `type: "recipe"`, `FoodDescription`, `ingredients[]` (each entry has `FoodId`, `AmountUsed`, `FoodDescription`), and `notes`. On Confirm the app validates every ingredient against the live Foods table (rejecting unknown FoodIds and any liquid ingredients), then creates a Foods row plus Recipe rows linked by FoodId — same database state as building the recipe by hand on the **Add Recipe** screen.
- The notes field is optional free text. If provided it is stored with the food (or recipe) and shown in the Foods Table when **All** is selected.

The format of an **NIP food JSON** is precisely:
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
  "notes": "Used on-pack NIP for core nutrients. Remaining micronutrients estimated from AFCD/NUTTAB cheddar cheese equivalents."
}
```

The format of a **Recipe JSON** is:
```
{
  "type": "recipe",
  "FoodDescription": "Spaghetti bolognese, with mince and tomato",
  "ingredients": [
    {
      "FoodId": 1538,
      "AmountUsed": 200,
      "FoodDescription": "Tomato, paste, no added salt"
    },
    {
      "FoodId": 567,
      "AmountUsed": 500,
      "FoodDescription": "Beef, mince, regular, raw"
    }
  ],
  "notes": "API cost: \$0.0431"
}
```
AI-generated recipes also get ` (AI)` appended to the recipe's FoodDescription before the trailing `{recipe=Xg}` marker the app computes from ingredient totals.

NOTE: **Any line feeds, tabs and spaces outside of "any text" are entirely optional** — minified JSON works too.

- Tap **Confirm** to process the JSON. Both shapes end on the **Foods Table** with the new food (or recipe) highlighted, but the processing in between is very different.

**NIP food JSON pipeline (3 steps):**

1. **Parse** — read the `FoodDescription` and the 24 nutrient fields from the JSON.
2. **Insert** — write one row to the **Foods** table with those values exactly. No lookup, no scaling, no linking — the JSON *is* the Foods row.
3. **Navigate** — pop back to the Foods Table with the new food highlighted via the filter.

Errors show as a Toast: `"Please paste valid JSON"` if there are no JSON braces, or `"Invalid JSON or missing fields"` if any of the 24 nutrient fields is missing or non-numeric.

**Recipe JSON pipeline (8 steps)** — produces the same database state as building the recipe by hand on the **Add Recipe** screen, just driven from JSON instead of taps:

1. **Detect** the `type: "recipe"` discriminator at the top of the JSON. Without that key the JSON falls through to the NIP pipeline above.
2. **Resolve every ingredient** against the live Foods table — for each entry in `ingredients[]`:
    - `FoodId > 0` and `AmountUsed > 0` (in grams).
    - The `FoodId` resolves to an actual Food row via `getFoodById(FoodId)`.
    - The resolved Food is **solid** — its `FoodDescription` must not end in `mL` or `mL#`. Same gram-only rule the manual **Add Recipe** screen enforces with its "Only foods measured in grams can be added to a recipe" dialog.
3. **Atomic check** — if *any* ingredient fails validation, a specific Toast appears (e.g. `"FoodId 12345 not in Foods table"`, `"FoodId 12345 is a liquid; recipes need solids"`, `"ingredients[2] AmountUsed must be > 0"`) and the whole recipe is rejected. **No database writes happen** — there is no partial state to clean up.
4. **Stage Recipe rows** — for each validated ingredient, write one row into the **Recipe** table with `FoodId = 0` (a temp marker, the link target isn't known yet), `Amount = AmountUsed`, `FoodDescription` copied from the resolved Food, and all 24 nutrient fields **scaled by `AmountUsed / 100`** (so a 250 g ingredient stores nutrient values that are 2.5× the per-100 g values from its Food row).
5. **Aggregate** — sum the per-row nutrient values across all staged Recipe rows (that's the recipe's total nutrient mass at its natural weight). Compute `totalAmount = sum of AmountUsed` (in g) and a scaling factor `scale = 100 / totalAmount`.
6. **Build the parent Food row** with:
    - `FoodDescription = "<JSON's FoodDescription> (AI) {recipe=<totalAmount>g}"`. The ` (AI)` marker tags this row as AI-generated; the trailing `{recipe=<weight>g}` marker is the standard recipe identifier the rest of the app reads.
    - The 24 nutrient fields = step 5's totals × `scale` — i.e. per 100 g of recipe.
    - `notes` copied from the JSON (which already includes the API-cost annotation appended by the AI screen).
7. **Insert the parent** via `insertFoodReturningId(...)` to get a fresh `FoodId`, then **link** the staged Recipe rows by running `UPDATE Recipe SET FoodId = <new id> WHERE FoodId = 0` — every staged row now points back at the parent. (The same staging-and-link pattern the manual Add Recipe screen uses on its Confirm.)
8. **Navigate** to the Foods Table with the new recipe highlighted, using the same `foodInserted`, `foodInsertedDescription`, and `sortFoodsDescOnce` saved-state-handle keys the manual Add Recipe screen sets — so the result feels identical regardless of how the recipe was built.

The end state in `foods.db` is byte-identical to clicking Confirm on the manual Add Recipe screen with the same ingredients and amounts. There is no separate "AI recipe" code path in the database — the only visible difference is the ` (AI)` substring in the FoodDescription, which lets you spot AI-built recipes in the Foods Table.
- **To abort any actions on this screen** press either of the two "back" buttons. Destination depends on how you got here:
    - If you came in via the **Json** button: you return to the Foods Table.
    - If you came in via the AI auto-pump: you return to the **Add Food using AI** chat with your conversation preserved, so you can iterate (e.g. ask for a corrected JSON).
***
# **AI generation of JSON**
The recommended way to obtain JSON for this screen is the app's own **Add Food using AI** screen — tap the **AI** button on the Foods Table.
- With **NIP mode** on (default), Claude follows a FSANZ-compliant NIP-extraction prompt and calls a `lookup_food` tool that queries the live Foods table for nutrient values on demand (no big knowledge-base attachment). Replies are Diet Sentry compatible NIP JSON and auto-pump straight into this screen.
- With NIP mode on, if your message contains the word "recipe" Claude swaps to the recipe prompt and produces a Recipe JSON instead. Same auto-pump path; Confirm builds the recipe.
- You can attach photos of labels or on-pack NIPs in the AI screen (the **+** button is multi-select) and Claude will read them.
- For an external workflow, paid ChatGPT subscribers can use the "NIP generator" GPT (https://chatgpt.com → Explore GPTs) and copy-paste its reply into the text field above. The NIP schema is the same.
- You can hand-edit the JSON in the text field before pressing **Confirm** — for example, to tweak the FoodDescription or refine values. Just keep the JSON syntactically valid.
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
                        if (json.optString("type") == "recipe") {
                            insertRecipeFromRecipeJson(json, dbHelper).fold(
                                onSuccess = { newDescription ->
                                    val foodSearchEntry = runCatching {
                                        navController.getBackStackEntry("foodSearch")
                                    }.getOrNull()
                                    if (foodSearchEntry != null) {
                                        foodSearchEntry.savedStateHandle.set("foodInserted", true)
                                        foodSearchEntry.savedStateHandle.set("foodInsertedDescription", newDescription)
                                        foodSearchEntry.savedStateHandle.set("sortFoodsDescOnce", true)
                                        navController.popBackStack("foodSearch", inclusive = false)
                                    } else {
                                        navController.previousBackStackEntry
                                            ?.savedStateHandle
                                            ?.set("foodInsertedDescription", newDescription)
                                        navController.popBackStack()
                                    }
                                },
                                onFailure = { e ->
                                    showPlainToast(context, e.message ?: "Failed to insert recipe")
                                }
                            )
                            return@Button
                        }
                        val description = json.getString("FoodDescription").trim()
                        if (description.isBlank()) {
                            showPlainToast(context, "FoodDescription is required")
                            return@Button
                        }
                        val notes = json.optString("notes", "").trim()
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
                            alcohol = json.getDouble("Alcohol"),
                            notes = notes
                        )
                        val inserted = dbHelper.insertFood(newFood)
                        if (inserted) {
                            val foodSearchEntry = runCatching {
                                navController.getBackStackEntry("foodSearch")
                            }.getOrNull()
                            if (foodSearchEntry != null) {
                                foodSearchEntry.savedStateHandle
                                    .set("foodInsertedDescription", description)
                                navController.popBackStack("foodSearch", inclusive = false)
                            } else {
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set("foodInsertedDescription", description)
                                navController.popBackStack()
                            }
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
            initialCallCost?.let { cost ->
                Surface(
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "AI call cost: ${formatUsdCost(cost)}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
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

private data class AiImage(val bytes: ByteArray, val mediaType: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AiImage) return false
        return mediaType == other.mediaType && bytes.contentEquals(other.bytes)
    }
    override fun hashCode(): Int = 31 * bytes.contentHashCode() + mediaType.hashCode()
}

private data class AiChatMessage(
    val role: String,
    val text: String,
    val images: List<AiImage> = emptyList()
)

private data class AiSystemContent(
    val nipPrompt: String,
    val recipePrompt: String,
)

private data class AiUsage(
    val inputTokens: Int,
    val outputTokens: Int,
    val cacheCreationTokens: Int,
    val cacheReadTokens: Int,
    val webSearchRequests: Int
)

private data class AiResponse(val text: String, val usage: AiUsage)

private fun computeAiCostUsd(usage: AiUsage, model: String): Double {
    val pricing = ANTHROPIC_PRICING[model] ?: return 0.0
    val million = 1_000_000.0
    val inputCost = usage.inputTokens * pricing.inputPerMillion / million
    val outputCost = usage.outputTokens * pricing.outputPerMillion / million
    val cacheCreationCost = usage.cacheCreationTokens * pricing.inputPerMillion * CACHE_WRITE_MULTIPLIER / million
    val cacheReadCost = usage.cacheReadTokens * pricing.inputPerMillion * CACHE_READ_MULTIPLIER / million
    val webSearchCost = usage.webSearchRequests * WEB_SEARCH_COST_PER_REQUEST
    return inputCost + outputCost + cacheCreationCost + cacheReadCost + webSearchCost
}

private fun formatUsdCost(amount: Double): String =
    String.format(java.util.Locale.US, "$%.4f", amount)

/**
 * If `reply` contains a `{...}` JSON block, parse it, append a one-line
 * "API cost: $X.XXXX" annotation to the existing `notes` field (or create
 * the field if missing), and return the reply with the modified JSON
 * substituted in place. Surrounding markdown fences and prose are
 * preserved. If no JSON block is found or parsing fails, returns `reply`
 * unchanged so the chat display and auto-pump pipeline both stay safe.
 */
private fun injectCostIntoJsonNotes(reply: String, costUsd: Double): String {
    val openIdx = reply.indexOf('{')
    val closeIdx = reply.lastIndexOf('}')
    if (openIdx < 0 || closeIdx <= openIdx) return reply
    val jsonText = reply.substring(openIdx, closeIdx + 1)
    return try {
        val obj = org.json.JSONObject(jsonText)
        val existingNotes = obj.optString("notes", "").trim()
        val costAnnotation = "API cost: ${formatUsdCost(costUsd)}"
        val newNotes = if (existingNotes.isEmpty()) {
            costAnnotation
        } else {
            "$existingNotes $costAnnotation"
        }
        obj.put("notes", newNotes)
        reply.substring(0, openIdx) + obj.toString(2) + reply.substring(closeIdx + 1)
    } catch (_: Exception) {
        reply
    }
}

private fun loadAiSystemContent(context: Context): AiSystemContent {
    val nip = try {
        context.assets.open("NIPsysprompt.txt").bufferedReader().use { it.readText() }
    } catch (_: Exception) {
        "You are a helpful assistant for the Diet Sentry food tracking app."
    }
    val recipe = try {
        context.assets.open("RECIPEsysprompt.txt").bufferedReader().use { it.readText() }
    } catch (_: Exception) {
        ""
    }
    return AiSystemContent(nip, recipe)
}

private fun loadExplainSystemPrompt(context: Context): String {
    return try {
        context.assets.open("EXPLAINsysprompt.txt").bufferedReader().use { it.readText() }
    } catch (_: Exception) {
        "You are a friendly nutrition assistant for the Diet Sentry food tracking app. Briefly explain the user's daily food totals against Australian NHMRC NRVs in 2–3 plain-language paragraphs."
    }
}

private sealed interface EatenExplanationState {
    data object Idle : EatenExplanationState
    data object Loading : EatenExplanationState
    data class Success(val text: String, val costUsd: Double) : EatenExplanationState
    data class Error(val message: String) : EatenExplanationState
}

private fun formatDailyTotalsForAi(
    totals: DailyTotals,
    weightEntry: WeightEntry?,
    userProfile: String
): String {
    val sb = StringBuilder()
    sb.append("My daily food totals:\n")
    sb.append("Date: ${totals.date}\n")
    sb.append("Total amount eaten: ${formatNumber(totals.amountEaten, decimals = 1)} ${totals.unitLabel}\n")
    sb.append("Energy: ${formatNumber(totals.energy, decimals = 0)} kJ\n")
    sb.append("Protein: ${formatNumber(totals.protein, decimals = 1)} g\n")
    sb.append("Fat, total: ${formatNumber(totals.fatTotal, decimals = 1)} g\n")
    sb.append("Saturated fat: ${formatNumber(totals.saturatedFat, decimals = 1)} g\n")
    sb.append("Trans fat: ${formatNumber(totals.transFat, decimals = 1)} mg\n")
    sb.append("Polyunsaturated fat: ${formatNumber(totals.polyunsaturatedFat, decimals = 1)} g\n")
    sb.append("Monounsaturated fat: ${formatNumber(totals.monounsaturatedFat, decimals = 1)} g\n")
    sb.append("Carbohydrate: ${formatNumber(totals.carbohydrate, decimals = 1)} g\n")
    sb.append("Sugars: ${formatNumber(totals.sugars, decimals = 1)} g\n")
    sb.append("Dietary fibre: ${formatNumber(totals.dietaryFibre, decimals = 1)} g\n")
    sb.append("Sodium (Na): ${formatNumber(totals.sodiumNa, decimals = 0)} mg\n")
    sb.append("Calcium (Ca): ${formatNumber(totals.calciumCa, decimals = 0)} mg\n")
    sb.append("Potassium (K): ${formatNumber(totals.potassiumK, decimals = 0)} mg\n")
    sb.append("Thiamin (B1): ${formatNumber(totals.thiaminB1, decimals = 2)} mg\n")
    sb.append("Riboflavin (B2): ${formatNumber(totals.riboflavinB2, decimals = 2)} mg\n")
    sb.append("Niacin (B3): ${formatNumber(totals.niacinB3, decimals = 2)} mg\n")
    sb.append("Folate: ${formatNumber(totals.folate, decimals = 0)} µg\n")
    sb.append("Iron (Fe): ${formatNumber(totals.ironFe, decimals = 1)} mg\n")
    sb.append("Magnesium (Mg): ${formatNumber(totals.magnesiumMg, decimals = 0)} mg\n")
    sb.append("Vitamin C: ${formatNumber(totals.vitaminC, decimals = 1)} mg\n")
    sb.append("Caffeine: ${formatNumber(totals.caffeine, decimals = 0)} mg\n")
    sb.append("Cholesterol: ${formatNumber(totals.cholesterol, decimals = 0)} mg\n")
    sb.append("Alcohol: ${formatNumber(totals.alcohol, decimals = 1)} g\n")
    if (weightEntry != null) {
        sb.append("My weight that day: ${formatWeight(weightEntry.weight)} kg\n")
        if (weightEntry.comments.isNotBlank()) {
            sb.append("Weight notes: ${weightEntry.comments.trim()}\n")
        }
    }
    if (userProfile.isNotBlank()) {
        sb.append("\nMy personal profile:\n${userProfile.trim()}\n")
    }
    sb.append("\nPlease explain my nutritional intake for this day.")
    return sb.toString()
}

private data class GeneralPromptParts(val base: String, val webSearchClause: String)

private fun loadGeneralPromptParts(context: Context): GeneralPromptParts {
    val base = try {
        context.assets.open("GenericSysprompt.txt").bufferedReader().use { it.readText() }
    } catch (_: Exception) {
        "You are a helpful assistant for the Diet Sentry food tracking app."
    }
    val ws = try {
        context.assets.open("GenericSysprompt_websearch.txt").bufferedReader().use { it.readText() }
    } catch (_: Exception) {
        ""
    }
    return GeneralPromptParts(base.trim(), ws.trim())
}

private fun buildGeneralSystemPrompt(
    parts: GeneralPromptParts,
    enableWebSearch: Boolean
): String {
    return if (enableWebSearch && parts.webSearchClause.isNotEmpty()) {
        "${parts.base}\n\n${parts.webSearchClause}"
    } else {
        parts.base
    }
}

private fun chatMessagesToJsonArray(messages: List<AiChatMessage>): org.json.JSONArray {
    val arr = org.json.JSONArray()
    for (m in messages) {
        val msgObj = org.json.JSONObject()
        msgObj.put("role", m.role)
        if (m.images.isNotEmpty()) {
            val contentArr = org.json.JSONArray()
            for (img in m.images) {
                val source = org.json.JSONObject()
                    .put("type", "base64")
                    .put("media_type", img.mediaType)
                    .put("data", android.util.Base64.encodeToString(img.bytes, android.util.Base64.NO_WRAP))
                contentArr.put(org.json.JSONObject().put("type", "image").put("source", source))
            }
            if (m.text.isNotEmpty()) {
                contentArr.put(org.json.JSONObject().put("type", "text").put("text", m.text))
            }
            msgObj.put("content", contentArr)
        } else {
            msgObj.put("content", m.text)
        }
        arr.put(msgObj)
    }
    return arr
}

private fun buildFoodLookupToolDefinition(): org.json.JSONObject {
    val querySchema = org.json.JSONObject()
        .put("type", "string")
        .put(
            "description",
            "The food name or category to search (e.g. 'cheddar cheese', 'olive oil', 'banana'). " +
            "Case-insensitive substring match against FoodDescription. " +
            "Join two terms with '|' to require both, e.g. 'cheese|cheddar'."
        )
    val properties = org.json.JSONObject().put("query", querySchema)
    val schema = org.json.JSONObject()
        .put("type", "object")
        .put("properties", properties)
        .put("required", org.json.JSONArray().put("query"))
    return org.json.JSONObject()
        .put("name", LOOKUP_FOOD_TOOL_NAME)
        .put(
            "description",
            "Search the Diet Sentry Foods table — an Australian Food Composition Database derived from AFCD/NUTTAB. " +
            "Returns up to $LOOKUP_FOOD_MAX_RESULTS matching rows as CSV, each with FoodId, FoodDescription, and full per-100 g (or per-100 mL for liquids) nutrient values across all 24 columns. " +
            "Use this when you need exact micronutrient values (calcium, iron, folate, magnesium, vitamin C, etc.) for a specific food and you don't already know them from training or web search. " +
            "FoodDescription suffix conventions: ' mL' or ' mL#' = liquid (per 100 mL); '#' alone = AI-generated/user-added record (prefer cleaner records when possible); '{recipe=Xg}' = recipe food."
        )
        .put("input_schema", schema)
}

private fun formatFoodAsCsvRow(f: Food): String {
    val description = "\"" + f.foodDescription.replace("\"", "\"\"") + "\""
    return "${f.foodId},$description,${f.energy},${f.protein},${f.fatTotal},${f.saturatedFat}," +
        "${f.transFat},${f.polyunsaturatedFat},${f.monounsaturatedFat},${f.carbohydrate}," +
        "${f.sugars},${f.dietaryFibre},${f.sodium},${f.calciumCa},${f.potassiumK}," +
        "${f.thiaminB1},${f.riboflavinB2},${f.niacinB3},${f.folate},${f.ironFe}," +
        "${f.magnesiumMg},${f.vitaminC},${f.caffeine},${f.cholesterol},${f.alcohol}"
}

private fun executeFoodLookupTool(
    dbHelper: DatabaseHelper,
    input: org.json.JSONObject,
    recipeMode: Boolean
): String {
    val query = input.optString("query", "").trim()
    if (query.isEmpty()) return "Tool error: 'query' parameter is required."
    val matches = try {
        dbHelper.searchFoods(query)
    } catch (e: Exception) {
        return "Tool error: search failed: ${e.message}"
    }
    // Recipe mode requires solid, non-user-added ingredients. The "#" suffix
    // marks AI-generated/user-added records (covers "#" and " mL#"); the "mL"
    // suffix marks liquids. Filter both out before showing matches to Claude.
    val filtered = if (recipeMode) {
        matches.filter { f ->
            val d = f.foodDescription
            !d.endsWith("#") && !d.endsWith("mL")
        }
    } else {
        matches
    }
    if (filtered.isEmpty()) {
        val suffix = if (recipeMode) " (recipe mode excludes liquids and AI/user-added records)" else ""
        return "No matches found for query: '$query'$suffix."
    }
    // searchFoods returns rows in FoodId order, so canonical AFCD records like
    // "Tomato, paste, ..." (FoodIds 1536–1544) lose the top-5 race against
    // earlier composite records like "Baked beans, canned in tomato sauce"
    // (FoodId 44) that merely contain the query term. Re-rank so records whose
    // FoodDescription STARTS with the first query term surface first.
    val firstTerm = query.split("|").map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: query
    val ranked = filtered.sortedByDescending { f ->
        f.foodDescription.startsWith(firstTerm, ignoreCase = true)
    }
    val limited = ranked.take(LOOKUP_FOOD_MAX_RESULTS)
    val sb = StringBuilder()
    sb.append("Found ${ranked.size} match(es) for '$query'; showing top ${limited.size}.\n")
    sb.append("FoodId,FoodDescription,Energy,Protein,FatTotal,SaturatedFat,TransFat,PolyunsaturatedFat,MonounsaturatedFat,Carbohydrate,Sugars,DietaryFibre,SodiumNa,CalciumCa,PotassiumK,ThiaminB1,RiboflavinB2,NiacinB3,Folate,IronFe,MagnesiumMg,VitaminC,Caffeine,Cholesterol,Alcohol\n")
    for (f in limited) {
        sb.append(formatFoodAsCsvRow(f))
        sb.append('\n')
    }
    return sb.toString()
}

private fun buildAnthropicRequestJson(
    model: String,
    messagesJson: org.json.JSONArray,
    enableWebSearch: Boolean,
    nipMode: Boolean,
    primaryPrompt: String,
    generalSystemPrompt: String,
    extendedThinking: Boolean,
    enableFoodLookupTool: Boolean
): String {
    val root = org.json.JSONObject()
    root.put("model", model)
    root.put("max_tokens", ANTHROPIC_MAX_TOKENS)
    if (extendedThinking && model in EXTENDED_THINKING_MODELS) {
        root.put(
            "thinking",
            org.json.JSONObject()
                .put("type", "adaptive")
                .put("display", "summarized")
        )
    }
    if (nipMode) {
        root.put("system", primaryPrompt)
    } else {
        root.put("system", generalSystemPrompt)
    }
    val tools = org.json.JSONArray()
    if (enableWebSearch) {
        tools.put(
            org.json.JSONObject()
                .put("type", WEB_SEARCH_TOOL_TYPE)
                .put("name", "web_search")
                .put("max_uses", WEB_SEARCH_MAX_USES)
        )
    }
    if (enableFoodLookupTool) {
        tools.put(buildFoodLookupToolDefinition())
    }
    if (tools.length() > 0) {
        // Mark the last tool with cache_control to create a cache breakpoint covering
        // the static prefix (model + system + all tools). Subsequent iterations of a
        // tool-use loop, and consecutive turns within ~5 min, hit cache_read at ~10%
        // of the base input rate. Below-minimum cacheable prefixes are silently
        // ignored by the API, so this is safe even on small payloads.
        val lastTool = tools.getJSONObject(tools.length() - 1)
        lastTool.put("cache_control", org.json.JSONObject().put("type", "ephemeral"))
        root.put("tools", tools)
    }
    root.put("messages", messagesJson)
    return root.toString()
}

private suspend fun callAnthropicApi(
    apiKey: String,
    model: String,
    messages: List<AiChatMessage>,
    enableWebSearch: Boolean,
    nipMode: Boolean,
    primaryPrompt: String,
    generalSystemPrompt: String,
    extendedThinking: Boolean,
    enableFoodLookupTool: Boolean,
    dbHelper: DatabaseHelper?,
    recipeMode: Boolean,
    onToolEvent: ((String) -> Unit)? = null
): Result<AiResponse> = withContext(Dispatchers.IO) {
    try {
        val workingMessages = chatMessagesToJsonArray(messages)
        var totalInput = 0
        var totalOutput = 0
        var totalCacheCreate = 0
        var totalCacheRead = 0
        var totalWebSearch = 0
        var lastTextOutput = ""

        for (iteration in 0 until MAX_TOOL_ITERATIONS) {
            val url = java.net.URL(ANTHROPIC_API_URL)
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
                setRequestProperty("content-type", "application/json")
                connectTimeout = 30_000
                readTimeout = 240_000
                doOutput = true
            }
            val body = buildAnthropicRequestJson(
                model, workingMessages, enableWebSearch, nipMode,
                primaryPrompt, generalSystemPrompt, extendedThinking,
                enableFoodLookupTool
            )
            android.util.Log.d("AnthropicRequest", body)
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) {
                val errBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                android.util.Log.d("AnthropicResponse", "HTTP $code: $errBody")
                val parsed = try {
                    org.json.JSONObject(errBody).optJSONObject("error")?.optString("message", "") ?: ""
                } catch (_: Exception) { "" }
                val reason = if (parsed.isNotBlank()) parsed else "HTTP $code"
                return@withContext Result.failure(Exception(reason))
            }

            val text = conn.inputStream.bufferedReader().use { it.readText() }
            android.util.Log.d("AnthropicResponse", text)
            val json = org.json.JSONObject(text)

            val usageObj = json.optJSONObject("usage")
            totalInput += usageObj?.optInt("input_tokens", 0) ?: 0
            totalOutput += usageObj?.optInt("output_tokens", 0) ?: 0
            totalCacheCreate += usageObj?.optInt("cache_creation_input_tokens", 0) ?: 0
            totalCacheRead += usageObj?.optInt("cache_read_input_tokens", 0) ?: 0
            val wsFromUsage = usageObj
                ?.optJSONObject("server_tool_use")
                ?.optInt("web_search_requests", 0)
                ?: 0

            val content = json.getJSONArray("content")
            val sb = StringBuilder()
            var wsFromContent = 0
            val toolUseBlocks = mutableListOf<org.json.JSONObject>()
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                when (block.optString("type")) {
                    "text" -> sb.append(block.optString("text", ""))
                    "server_tool_use" -> {
                        if (block.optString("name") == "web_search") {
                            wsFromContent++
                            val wsQuery = block.optJSONObject("input")?.optString("query", "")
                            if (!wsQuery.isNullOrBlank()) {
                                onToolEvent?.invoke("Searched the web: '$wsQuery'")
                            }
                        }
                    }
                    "tool_use" -> toolUseBlocks.add(block)
                }
            }
            totalWebSearch += maxOf(wsFromUsage, wsFromContent)
            lastTextOutput = sb.toString()

            val stopReason = json.optString("stop_reason")
            if (stopReason == "tool_use" && toolUseBlocks.isNotEmpty() && dbHelper != null) {
                workingMessages.put(
                    org.json.JSONObject()
                        .put("role", "assistant")
                        .put("content", content)
                )
                val resultsContent = org.json.JSONArray()
                for (toolUse in toolUseBlocks) {
                    val toolName = toolUse.optString("name")
                    val toolUseId = toolUse.optString("id")
                    val toolInput = toolUse.optJSONObject("input") ?: org.json.JSONObject()
                    if (toolName == LOOKUP_FOOD_TOOL_NAME) {
                        val q = toolInput.optString("query", "").trim()
                        if (q.isNotEmpty()) {
                            onToolEvent?.invoke("Looking up '$q' in the Foods table…")
                        }
                    }
                    val resultText = when (toolName) {
                        LOOKUP_FOOD_TOOL_NAME -> executeFoodLookupTool(dbHelper, toolInput, recipeMode)
                        else -> "Unknown client tool: $toolName"
                    }
                    resultsContent.put(
                        org.json.JSONObject()
                            .put("type", "tool_result")
                            .put("tool_use_id", toolUseId)
                            .put("content", resultText)
                    )
                }
                workingMessages.put(
                    org.json.JSONObject()
                        .put("role", "user")
                        .put("content", resultsContent)
                )
                continue
            }

            val totalUsage = AiUsage(totalInput, totalOutput, totalCacheCreate, totalCacheRead, totalWebSearch)
            return@withContext Result.success(AiResponse(lastTextOutput, totalUsage))
        }

        Result.failure(Exception("Tool-use loop exceeded $MAX_TOOL_ITERATIONS iterations."))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

private suspend fun loadImageForAi(context: Context, uri: Uri): Result<AiImage> = withContext(Dispatchers.IO) {
    try {
        val resolver = context.contentResolver

        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri)
            ?: return@withContext Result.failure(Exception("Picker did not return a readable URI."))
        boundsStream.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return@withContext Result.failure(Exception("Image header could not be decoded."))
        }

        var sample = 1
        while (bounds.outWidth / sample > AI_IMAGE_MAX_DIM || bounds.outHeight / sample > AI_IMAGE_MAX_DIM) {
            sample *= 2
        }
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        val decodeStream = resolver.openInputStream(uri)
            ?: return@withContext Result.failure(Exception("Image stream closed before decode."))
        val bmp = decodeStream.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
            ?: return@withContext Result.failure(Exception("Bitmap decoder returned null."))

        val out = java.io.ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
        Result.success(AiImage(out.toByteArray(), "image/jpeg"))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

@Composable
private fun ChatBubble(
    message: AiChatMessage,
    onCopy: (() -> Unit)? = null
) {
    val isUser = message.role == "user"
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(12.dp),
            modifier = if (isUser) Modifier.widthIn(max = 320.dp) else Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                message.images.forEach { img ->
                    val bmp = remember(img) {
                        android.graphics.BitmapFactory.decodeByteArray(img.bytes, 0, img.bytes.size)
                    }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(bottom = if (message.text.isBlank()) 0.dp else 6.dp)
                                .size(160.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
                if (message.text.isNotBlank()) {
                    Text(message.text, color = textColor)
                }
            }
        }
        if (!isUser && onCopy != null && message.text.isNotBlank()) {
            TextButton(
                onClick = onCopy,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Copy", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiSettingsDialog(
    initialApiKey: String,
    initialModel: String,
    initialWebSearch: Boolean,
    initialNipMode: Boolean,
    initialExtendedThinking: Boolean,
    onSave: (String, String, Boolean, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var keyText by rememberSaveable { mutableStateOf(initialApiKey) }
    var modelText by rememberSaveable { mutableStateOf(initialModel) }
    var webSearch by rememberSaveable { mutableStateOf(initialWebSearch) }
    var nipMode by rememberSaveable { mutableStateOf(initialNipMode) }
    var extendedThinking by rememberSaveable { mutableStateOf(initialExtendedThinking) }
    var keyVisible by rememberSaveable { mutableStateOf(false) }
    val knownModels = listOf(
        "claude-opus-4-7" to "Opus 4.7 — highest quality",
        "claude-sonnet-4-6" to "Sonnet 4.6 — balanced (default)",
        "claude-haiku-4-5-20251001" to "Haiku 4.5 — fastest / cheapest"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI settings") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Anthropic API key", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("sk-ant-…") },
                    singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (keyVisible) "Hide key" else "Show key"
                            )
                        }
                    }
                )
                Text(
                    "Stored locally on this device. Never leaves except to api.anthropic.com.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text("Model", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                knownModels.forEach { (id, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { modelText = id }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = modelText == id, onClick = { modelText = id })
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Server-side tools", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { webSearch = !webSearch }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(checked = webSearch, onCheckedChange = { webSearch = it })
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Web search", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Lets Claude look up current product / NIP info on the web (~\$0.01 per search, max $WEB_SEARCH_MAX_USES per turn).",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("System prompt", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { nipMode = !nipMode }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(checked = nipMode, onCheckedChange = { nipMode = it })
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("NIP mode", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "On: use the bundled NIP-extraction prompt with the lookup_food tool against the live foods.db; replies are JSON and auto-pumped into the Json screen for one-tap Confirm. Off: Claude is a general-purpose assistant.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Reasoning", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                val thinkingSupported = modelText in EXTENDED_THINKING_MODELS
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = thinkingSupported) { extendedThinking = !extendedThinking }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = extendedThinking && thinkingSupported,
                        onCheckedChange = { extendedThinking = it },
                        enabled = thinkingSupported
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Extended thinking (adaptive)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (thinkingSupported) {
                                "Lets Claude reason internally before replying — the model decides when it's worthwhile. Thinking tokens are billed at the output rate, adding ~\$0.001–\$0.03 per harder turn (e.g. recipe nutrient calculations). No effect on simple lookups."
                            } else {
                                "Not supported on Haiku 4.5. Switch to Sonnet 4.6 or Opus 4.7 above to enable extended thinking. Your preference is preserved and will re-apply if you go back to a supported model."
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(keyText.trim(), modelText, webSearch, nipMode, extendedThinking) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodByAiScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val dbHelper = remember { DatabaseHelper.getInstance(context) }
    val sysContent = remember { loadAiSystemContent(context) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    var apiKey by rememberSaveable {
        mutableStateOf(prefs.getString(KEY_ANTHROPIC_API_KEY, "") ?: "")
    }
    var model by rememberSaveable {
        mutableStateOf(prefs.getString(KEY_ANTHROPIC_MODEL, DEFAULT_ANTHROPIC_MODEL) ?: DEFAULT_ANTHROPIC_MODEL)
    }
    var webSearchEnabled by rememberSaveable {
        mutableStateOf(prefs.getBoolean(KEY_AI_WEB_SEARCH, DEFAULT_AI_WEB_SEARCH))
    }
    var nipModeEnabled by rememberSaveable {
        mutableStateOf(prefs.getBoolean(KEY_AI_USE_NIP_PROMPT, DEFAULT_AI_USE_NIP_PROMPT))
    }
    var extendedThinkingEnabled by rememberSaveable {
        mutableStateOf(prefs.getBoolean(KEY_AI_EXTENDED_THINKING, DEFAULT_AI_EXTENDED_THINKING))
    }
    val generalPromptParts = remember { loadGeneralPromptParts(context) }
    val generalSystemPrompt = remember(webSearchEnabled, generalPromptParts) {
        buildGeneralSystemPrompt(generalPromptParts, webSearchEnabled)
    }
    var showSettings by rememberSaveable { mutableStateOf(apiKey.isBlank()) }
    var showHelpSheet by remember { mutableStateOf(false) }
    val helpSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var messages by remember { mutableStateOf(listOf<AiChatMessage>()) }
    var pendingImages by remember { mutableStateOf(listOf<AiImage>()) }
    var inputText by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var sessionCostUsd by remember { mutableStateOf(0.0) }
    var turnCount by remember { mutableStateOf(0) }
    var toolStatusHistory by remember { mutableStateOf(listOf<String>()) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val results = uris.map { loadImageForAi(context, it) }
                val loaded = results.mapNotNull { it.getOrNull() }
                val errors = results.mapNotNull { it.exceptionOrNull() }
                if (loaded.isNotEmpty()) {
                    pendingImages = pendingImages + loaded
                }
                if (errors.isNotEmpty()) {
                    errorText = "Skipped ${errors.size} image(s): ${errors.first().message}"
                }
            }
        }
    }

    LaunchedEffect(messages.size, loading) {
        val target = (messages.size - 1).coerceAtLeast(0) + (if (loading) 1 else 0)
        if (messages.isNotEmpty() || loading) {
            listState.animateScrollToItem(target)
        }
    }

    val aiHelpText = """
# **Add Food using AI**
- Connects your phone to **Anthropic's Claude** models. Behaviour depends on the **NIP mode** toggle in settings and on whether your message contains the word "recipe":
  - **NIP mode ON, no "recipe":** the bundled NIP system prompt (`NIPsysprompt.txt`) is sent as system context. Claude calls a `lookup_food` tool that queries the live Foods table SQLite database for nutrient values on demand (no big knowledge-base attachment). Every reply is a Diet Sentry compatible JSON object inside a ```json``` code block and is **auto-pumped into the Json screen** so you can hit Confirm to add the food.
  - **NIP mode ON, "recipe" in message:** Claude switches to the recipe prompt (`RECIPEsysprompt.txt`). The same `lookup_food` tool runs in *recipe mode* — pre-filtering out liquids and AI/user-added records (FoodDescriptions ending in `mL`, `mL#`, or `#`) so only solid, non-user-added foods can be picked as ingredients. The reply is a recipe JSON (`type: "recipe"`, `ingredients[]`) and auto-pumps into the Json screen too — Confirm there creates the recipe (a Foods row plus linked Recipe rows), then lands on the Foods Table with the new recipe highlighted.
  - **NIP mode OFF:** Claude is a general-purpose assistant; the word "recipe" in your message has no special effect (the recipe workflow is gated behind NIP mode). Replies stay in this chat; nothing is auto-pumped.
- **Setup:** Tap the **gear** icon, paste your Anthropic API key (from `console.anthropic.com`), pick a model (Opus 4.7 / Sonnet 4.6 / Haiku 4.5), choose your toggles, and **Save**. The key is stored only on this device. All five settings (key, model, Web search, NIP mode, Extended thinking) persist across launches.
- **Asking:** Type a food description (e.g. "Mainland Lite cheddar 250 g block") and tap **Send** (➤). In NIP mode Claude follows FSANZ Standard 1.2.8 / Schedules 11–12 rounding and returns per-100 g (solid) or per-100 mL (liquid) values. AI-sourced rows are tagged in the FoodDescription: `(AI) #` (solid), `(AI) mL#` (liquid), or ` (AI) {recipe=Xg}` (recipe). The `(AI)` substring makes AI-sourced rows easy to filter on in the Foods Table. Claude is also instructed to format the descriptive part as `Category1, Category2, Category3, OtherDescription - Brand, Company` (e.g. "Cheese, cheddar, lite, block - Mainland") for consistent sorting and searching — you can hand-edit this in the Json screen before Confirm if you want a different shape.
- **Attaching images:** Tap **+** at the left of the input to attach photos of food labels or on-pack NIPs. The picker is multi-select — long-press, then **Done**. Large photos are downsized before sending.
- **Web search** (toggle in settings): Claude looks up the manufacturer or retailer's official product page first and copies on-pack values verbatim. AFCD/NUTTAB and the `lookup_food` tool are used as fallbacks. ~\$0.01 per search, capped at $WEB_SEARCH_MAX_USES per turn.
- **Extended thinking** (toggle in settings): gives Claude an adaptive thinking budget for harder reasoning tasks. Effective on Opus 4.7 / Sonnet 4.6 — the toggle is automatically disabled when Haiku 4.5 is selected, since it doesn't support thinking.
- **Live tool-call indicator:** while a query is processing, the loading row stacks lines like "Looking up '<query>' in the Foods table…" (each `lookup_food` call) and "Searched the web: '<query>'" (each web search) so you can see what Claude is doing.
- **Cost transparency:** the small status row at the top of this screen shows the cumulative session cost (e.g. "Session cost: \$0.0143 (3 turns)"). Per-call cost is also appended to each reply's JSON `notes` field, so it rides through to the Foods table when you Confirm.
- **Cross-feature** — the **Eaten Table** screen reuses your API key + model (set here) for an *Explain this day (AI)* flow on daily totals. That flow is a single-shot single-message call (no tools, no thinking, no web search) driven by the bundled `EXPLAINsysprompt.txt` system prompt; the Web search / Extended thinking / NIP-mode toggles above don't apply to it. See the Eaten Table's `?` help for details.
- The chat is in-memory only — leaving this screen clears it.
""".trimIndent()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Food using AI", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "AI settings")
                    }
                    HelpIconButton(onClick = { showHelpSheet = true })
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (sessionCostUsd > 0.0) {
                Surface(
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Session cost: ${formatUsdCost(sessionCostUsd)} (${turnCount} ${if (turnCount == 1) "turn" else "turns"})",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (messages.isEmpty() && !loading) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Chat with Claude",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                when {
                                    apiKey.isBlank() ->
                                        "Tap the gear icon to add your Anthropic API key, then describe a food or attach a label photo."
                                    nipModeEnabled ->
                                        "NIP mode is ON. Describe a food (or attach a label photo) and the reply — a Diet Sentry JSON — will be auto-pumped into the Json screen for one-tap Confirm."
                                    else ->
                                        "General chat mode (NIP mode off in settings). Ask anything; replies stay here."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                }
                items(messages) { msg ->
                    ChatBubble(
                        message = msg,
                        onCopy = if (msg.role == "assistant") {
                            {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("AI reply", msg.text))
                                showPlainToast(context, "Copied")
                            }
                        } else null
                    )
                }
                if (loading) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Thinking…",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            toolStatusHistory.forEach { status ->
                                Text(
                                    text = status,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 26.dp, top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            errorText?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { errorText = null }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Dismiss")
                        }
                    }
                }
            }

            if (pendingImages.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pendingImages.forEachIndexed { idx, img ->
                        Box(modifier = Modifier.size(72.dp)) {
                            val bmp = remember(img) {
                                android.graphics.BitmapFactory.decodeByteArray(img.bytes, 0, img.bytes.size)
                            }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(6.dp))
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(20.dp)
                                    .clickable {
                                        pendingImages = pendingImages.filterIndexed { i, _ -> i != idx }
                                    }
                            ) {
                                Icon(
                                    Icons.Filled.Clear,
                                    contentDescription = "Remove image",
                                    modifier = Modifier.padding(2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            imagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !loading
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Attach image")
                    }
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message Claude") },
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(
                            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences
                        )
                    )
                    val canSend = !loading && (inputText.isNotBlank() || pendingImages.isNotEmpty())
                    IconButton(
                        onClick = {
                            val txt = inputText.trim()
                            if (apiKey.isBlank()) {
                                errorText = "Set your Anthropic API key first (gear icon)."
                                showSettings = true
                                return@IconButton
                            }
                            val imagesToSend = pendingImages
                            val userMsg = AiChatMessage("user", txt, imagesToSend)
                            val recipeIntent = nipModeEnabled &&
                                txt.contains("recipe", ignoreCase = true) &&
                                sysContent.recipePrompt.isNotBlank()
                            val effectiveNipMode = nipModeEnabled || recipeIntent
                            val activePrompt: String
                            val enableFoodLookupTool: Boolean
                            when {
                                recipeIntent -> {
                                    activePrompt = sysContent.recipePrompt
                                    // recipeMode=true filters liquids and #-suffixed (AI/user-added)
                                    // rows out of lookup_food results before Claude sees them.
                                    enableFoodLookupTool = true
                                }
                                nipModeEnabled -> {
                                    activePrompt = sysContent.nipPrompt
                                    enableFoodLookupTool = true
                                }
                                else -> {
                                    activePrompt = ""
                                    enableFoodLookupTool = false
                                }
                            }
                            messages = messages + userMsg
                            inputText = ""
                            pendingImages = emptyList()
                            errorText = null
                            loading = true
                            toolStatusHistory = emptyList()
                            keyboardController?.hide()
                            scope.launch {
                                val result = callAnthropicApi(
                                    apiKey, model, messages, webSearchEnabled,
                                    effectiveNipMode, activePrompt, generalSystemPrompt,
                                    extendedThinkingEnabled,
                                    enableFoodLookupTool, dbHelper, recipeIntent,
                                    onToolEvent = { status -> toolStatusHistory = toolStatusHistory + status }
                                )
                                loading = false
                                toolStatusHistory = emptyList()
                                result
                                    .onSuccess { response ->
                                        val callCostUsd = computeAiCostUsd(response.usage, model)
                                        sessionCostUsd += callCostUsd
                                        turnCount += 1
                                        // Inject the API cost into the JSON's notes field if a JSON
                                        // block is present. Returns the reply unchanged otherwise.
                                        val annotatedReply = injectCostIntoJsonNotes(response.text, callCostUsd)
                                        messages = messages + AiChatMessage("assistant", annotatedReply)
                                        android.util.Log.d(
                                            "AiAutoPump",
                                            "effectiveNipMode=$effectiveNipMode replyLen=${annotatedReply.length} firstOpen=${annotatedReply.indexOf('{')} lastClose=${annotatedReply.lastIndexOf('}')}"
                                        )
                                        // Both NIP-mode and recipe-mode JSON auto-pump to
                                        // AddFoodByJsonScreen so the user always lands on the
                                        // same review screen. Note: the screen's Confirm flow
                                        // currently only knows the NIP schema, so Confirm on
                                        // recipe JSON toasts "Invalid JSON or missing fields"
                                        // until the recipe-insert path is wired in.
                                        if (effectiveNipMode) {
                                            val openIdx = annotatedReply.indexOf('{')
                                            val closeIdx = annotatedReply.lastIndexOf('}')
                                            if (openIdx >= 0 && closeIdx > openIdx) {
                                                android.util.Log.d("AiAutoPump", "navigating to addFoodByJson")
                                                sessionPrefilledJson = annotatedReply
                                                sessionPrefilledJsonCost = callCostUsd
                                                navController.navigate("addFoodByJson")
                                            } else {
                                                android.util.Log.d("AiAutoPump", "no JSON braces found in reply — auto-pump skipped")
                                            }
                                        } else {
                                            android.util.Log.d("AiAutoPump", "effectiveNipMode is false — auto-pump skipped (NIP toggle off and no 'recipe' in message)")
                                        }
                                    }
                                    .onFailure { e ->
                                        errorText = e.message ?: "Request failed."
                                    }
                            }
                        },
                        enabled = canSend
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }

    if (showSettings) {
        AiSettingsDialog(
            initialApiKey = apiKey,
            initialModel = model,
            initialWebSearch = webSearchEnabled,
            initialNipMode = nipModeEnabled,
            initialExtendedThinking = extendedThinkingEnabled,
            onSave = { newKey, newModel, newWebSearch, newNipMode, newExtendedThinking ->
                apiKey = newKey
                model = newModel
                webSearchEnabled = newWebSearch
                nipModeEnabled = newNipMode
                extendedThinkingEnabled = newExtendedThinking
                prefs.edit {
                    putString(KEY_ANTHROPIC_API_KEY, newKey)
                    putString(KEY_ANTHROPIC_MODEL, newModel)
                    putBoolean(KEY_AI_WEB_SEARCH, newWebSearch)
                    putBoolean(KEY_AI_USE_NIP_PROMPT, newNipMode)
                    putBoolean(KEY_AI_EXTENDED_THINKING, newExtendedThinking)
                }
                showSettings = false
            },
            onDismiss = { showSettings = false }
        )
    }

    if (showHelpSheet) {
        HelpBottomSheet(
            helpText = aiHelpText,
            sheetState = helpSheetState,
            onDismiss = { showHelpSheet = false }
        )
    }
}

private fun buildRecipeHelpText(screenTitle: String): String {
    val modeIntro = when (screenTitle) {
        "Editing Recipe" -> "This screen lets you edit the recipe food you selected."
        "Copying Recipe" -> "This screen lets you create and modify a new recipe food by copying the one you selected."
        else -> "This screen lets you create a new recipe food."
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
    val notesHint = when (screenTitle) {
        "Editing Recipe" -> "It starts with the selected recipe's notes."
        "Copying Recipe" -> "It starts with the selected recipe's notes."
        else -> "It starts empty."
    }
    return """
# **$screenTitle**
$modeIntro

By its very nature a recipe food is more complicated than a normal liquid or solid food and hence this screen is more complex.

***
# **What is a recipe food?**
A recipe food is a record in the Foods table AND and a collection of ingredient records from the Recipe table. Each ingredient is linked to its Foods table record by the FoodId field.

For the purposes of logging consumption you select a recipe food (from the scrollable table viewer in the Foods Table screen) just like with the simpler solid and liquid foods. It is considered a solid in that its amount is measured in grams. Differences in processing are only apparent when you Edit, Add or Copy it. 

You can identify a recipe food by noting that its FoodDescription field ends in text of the form " {recipe=[weight]g}" where [weight] is the total amount in grams of the ingredient foods (all required to be solids or recipes).

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
   
Once all the ingredients of a recipe are known, the end markers of the recipes FoodDescription field in the Foods table record are set to " {recipe=[weight]g}" where [weight] is the total amount in grams of all the ingredient foods. Furthermore the Energy and Nutrient fields (of this Foods table record) are scaled by 100/[weight]. This guarantees that when you LOG this recipe food using [weight] as the amount consumed you will get the correct Energy and Nutrient values (as if you had consumed the meal represented by the recipe).
 
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
    - When a particular food is selected (by tapping it) a dialog box appears where you can specify the amount in grams of the food that this recipe requires.
    - Press the **Confirm** button when you are ready to accept this recipe ingredient. This transfers focus back to this screen where the added ingredient will appear in the lower scrollable table viewer.
    - You can abort this process by tapping anywhere outside the dialog box. This closes it and focus returns to this screen.
    - If you select a liquid food a dialog with the title "CANNOT ADD THIS FOOD" will appear. Press the OK button or tap anywhere outside the dialog box to close it. Nothing happens and focus returns to this screen.    
- A **text label** which is of the form "Ingredients[weight] (g) Total"
    - The [weight] is the total current amount of ingredients in this recipe in grams.
    - It is automatically updated whenever the ingredients are added, edited or deleted.
- A **scrollable table viewer** which displays this recipes ingredient records from the Recipe table.
    - When an ingredient is selected (by tapping it) a selection panel appears near the bottom of the screen. It displays the description of the selected food followed by two buttons below it:
        - **Edit**: It enables the amount of the ingredient to be modified.
            - It opens a dialog box where you can modify the amount of the ingredient.
            - Press the **Confirm** button when you are ready to confirm your changes. This then transfers focus back to this screen where the just modified ingredient will be visible. The Total Ingredients amount will also be adjusted.        
            - You can abort this process by tapping anywhere outside the dialog box. This closes it and transfers focus back to this screen. The selection panel is also closed. 
        - **Delete**: deletes the selected ingredient from the Recipe table.
            - There is no warning dialog, it just irrevocably deletes the ingredient.
- Two buttons labeled **Set notes** and **Edit notes** beside the Confirm button.
    - **Set notes** fills the recipes notes field with the current ingredient list (replacing if one exists), one line per ingredient in the format "[amount] [description]".
    - **Edit notes** opens a dialog where you can directly edit (or just examine) the notes text field.
    - Notes are saved with the recipe food and shown in the Foods table when All is selected.
    - $notesHint
- A **Confirm** button.
    - $confirmHint
    - If the Description is blank or there are no ingredients in the recipe an informative Toast appears and nothing changes.
    - If you want to abort any actions on this screen you can press either of the two "back" buttons which sets focus back to the Foods Table screen.    
""".trimIndent()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecipeScreen(
    navController: NavController,
    screenTitle: String = "Add Recipe",
    initialDescription: String = "",
    editingFoodId: Int? = null,
    copySourceFoodId: Int? = null
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
    var showEditRecipeNotesDialog by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }
    val initialRecipeNotes = remember(editingFoodId, copySourceFoodId) {
        when {
            editingFoodId != null -> dbHelper.getFoodById(editingFoodId)?.notes ?: ""
            copySourceFoodId != null -> dbHelper.getFoodById(copySourceFoodId)?.notes ?: ""
            else -> ""
        }
    }
    var recipeNotes by rememberSaveable(editingFoodId, copySourceFoodId) {
        mutableStateOf(initialRecipeNotes)
    }

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
        } else if (showEditRecipeNotesDialog) {
            showEditRecipeNotesDialog = false
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = {
                        val currentRecipes = recipes
                        recipeNotes = currentRecipes.joinToString(separator = "\n") { recipe ->
                            val roundedAmount = recipe.amount.roundToInt()
                            val amountLabel = String.format(Locale.US, "%d g :", roundedAmount)
                            "$amountLabel ${recipe.foodDescription}"
                        }
                    }) {
                        Text("Set notes")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { showEditRecipeNotesDialog = true }) {
                        Text("Edit notes")
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    val currentRecipes = recipes
                    val totalAmount = currentRecipes.sumOf { it.amount }
                    val sanitizedDescription = stripTrailingRecipeSuffix(description).trim()
                    if (sanitizedDescription.isBlank()) {
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
                        foodDescription = "${sanitizedDescription} {recipe=${recipeWeightText}g}",
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
                        alcohol = scaled(totalAlcohol),
                        notes = recipeNotes
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

    if (showEditRecipeNotesDialog) {
        EditRecipeNotesDialog(
            notes = recipeNotes,
            onDismiss = { showEditRecipeNotesDialog = false },
            onConfirm = { updatedNotes ->
                recipeNotes = updatedNotes
                showEditRecipeNotesDialog = false
            }
        )
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
        initialDescription = initialDescription,
        copySourceFoodId = foodId
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
        alcohol = alcohol,
        notes = ""
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
    valueFillFraction: Float = 0.5f,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    rowVerticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    labelTopPadding: Dp = 0.dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = rowVerticalAlignment
    ) {
        val labelModifier = if (wrapLabel) {
            Modifier.padding(top = labelTopPadding, end = labelSpacing)
        } else {
            Modifier.weight(labelWeight).padding(top = labelTopPadding)
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
            val resolvedMinLines = if (singleLine) 1 else minLines.coerceAtLeast(1)
            val resolvedMaxLines = if (singleLine) {
                1
            } else {
                maxLines.coerceAtLeast(resolvedMinLines)
            }
            CompactTextField(
                value = value,
                onValueChange = onValueChange,
                keyboardType = keyboardType,
                modifier = Modifier.fillMaxWidth(valueFillFraction),
                singleLine = singleLine,
                minLines = resolvedMinLines,
                maxLines = resolvedMaxLines
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
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE
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
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier
            .fillMaxWidth()
    ) { innerTextField ->
        OutlinedTextFieldDefaults.DecorationBox(
            value = value,
            innerTextField = innerTextField,
            enabled = true,
            singleLine = singleLine,
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
fun EditRecipeNotesDialog(
    notes: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var notesText by rememberSaveable(notes) { mutableStateOf(notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Edit notes", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold) },
        text = {
            TextField(
                value = notesText,
                onValueChange = { notesText = it },
                label = { Text("Notes") },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = { onConfirm(notesText) }
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
    onAi: () -> Unit = {},
    onDelete: () -> Unit,
    onCopy: () -> Unit = {},
    onConvert: () -> Unit = {},
    onUtilities: () -> Unit = {}
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelLarge
    val buttonWidth = remember(density, labelStyle) {
        val labels = listOf("LOG", "Edit", "Copy", "Convert", "Delete", "Add", "Json", "AI", "Utilities")
        val maxPx = labels.maxOf { measurer.measure(it, labelStyle).size.width }
        with(density) { maxPx.toDp() } + 16.dp
    }
    val buttonModifier = Modifier.width(buttonWidth)
    val buttonPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)

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
                Button(onClick = onSelect, modifier = buttonModifier, contentPadding = buttonPadding) { Text("LOG") }
                Button(onClick = onEdit, modifier = buttonModifier, contentPadding = buttonPadding) { Text("Edit") }
                Button(onClick = onCopy, modifier = buttonModifier, contentPadding = buttonPadding) { Text("Copy") }
                Button(onClick = onConvert, modifier = buttonModifier, contentPadding = buttonPadding) { Text("Convert") }
                Button(onClick = onDelete, modifier = buttonModifier, contentPadding = buttonPadding) { Text("Delete") }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onAdd, modifier = buttonModifier, contentPadding = buttonPadding) { Text("Add") }
                Button(onClick = onJson, modifier = buttonModifier, contentPadding = buttonPadding) { Text("Json") }
                Button(onClick = onAi, modifier = buttonModifier, contentPadding = buttonPadding) { Text("AI") }
                Button(onClick = onUtilities, modifier = buttonModifier, contentPadding = buttonPadding) { Text("Utilities") }
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

    fun loadExchangeFolderUri(): Uri? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString(KEY_EXCHANGE_FOLDER_URI, null) ?: return null
        return runCatching { uriString.toUri() }.getOrNull()
    }

    fun storeExchangeFolderUri(uri: Uri) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_EXCHANGE_FOLDER_URI, uri.toString()) }
    }

    fun clearExchangeFolderUri() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { remove(KEY_EXCHANGE_FOLDER_URI) }
    }

    fun canReadAndroidTree(folderUri: Uri): Boolean {
        return try {
            val treeId = DocumentsContract.getTreeDocumentId(folderUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, treeId)
            context.contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null,
                null,
                null
            )?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun buildAndroidDirectoryDisplay(uri: Uri): String {
        val fallback = "Selected folder"
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            if (docId.isNullOrBlank()) return fallback
            val parts = docId.split(":", limit = 2).filter { it.isNotBlank() }
            if (parts.isEmpty()) return docId
            if (parts.size == 1) {
                return if (parts[0].equals("primary", ignoreCase = true)) {
                    "Internal storage"
                } else {
                    parts[0]
                }
            }
            val volume = parts[0]
            val path = parts[1].trim('/')
            if (volume.equals("primary", ignoreCase = true)) {
                if (path.isBlank()) "Internal storage" else "Internal storage/$path"
            } else {
                if (path.isBlank()) volume else "$volume/$path"
            }
        } catch (_: Exception) {
            fallback
        }
    }

    fun buildAndroidFileDisplay(uri: Uri, fileName: String): String {
        val directory = buildAndroidDirectoryDisplay(uri)
        return if (directory.isBlank()) fileName else "$directory/$fileName"
    }

    suspend fun findDocumentInFolder(folderUri: Uri, fileName: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val treeId = DocumentsContract.getTreeDocumentId(folderUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, treeId)
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex)
                    if (!name.equals(fileName, ignoreCase = true)) continue
                    val documentId = cursor.getString(idIndex)
                    return@withContext DocumentsContract.buildDocumentUriUsingTree(folderUri, documentId)
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun ensureDocumentInFolder(folderUri: Uri, fileName: String, mimeType: String): Uri? =
        withContext(Dispatchers.IO) {
            findDocumentInFolder(folderUri, fileName)
                ?: runCatching {
                    val treeId = DocumentsContract.getTreeDocumentId(folderUri)
                    val parentUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, treeId)
                    DocumentsContract.createDocument(
                        context.contentResolver,
                        parentUri,
                        mimeType,
                        fileName
                    )
                }.getOrNull()
        }

    suspend fun copyDatabaseToUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val dbFile = context.getDatabasePath(DATABASE_FILE_NAME)
            if (!dbFile.exists()) return@withContext false
            context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                dbFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: return@withContext false
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun copyDatabaseFromUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dbHelper.replaceDatabaseFromStream(input)
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun csvCell(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    fun buildEatenDailyAllCsv(
        dailyTotals: List<DailyTotals>,
        weightByDate: Map<String, WeightEntry>
    ): String {
        val lines = mutableListOf<String>()
        val header = listOf(
            "Date",
            "My weight (kg)",
            "Comments",
            "Amount (g or mL)",
            "Energy (kJ):",
            "Protein (g):",
            "Fat, total (g):",
            "- Saturated (g):",
            "- Trans (mg):",
            "- Polyunsaturated (g):",
            "- Monounsaturated (g):",
            "Carbohydrate (g):",
            "- Sugars (g):",
            "Sodium (mg):",
            "Dietary Fibre (g):",
            "Calcium (mg):",
            "Potassium (mg):",
            "Thiamin B1 (mg):",
            "Riboflavin B2 (mg):",
            "Niacin B3 (mg):",
            "Folate (ug):",
            "Iron (mg):",
            "Magnesium (mg):",
            "Vitamin C (mg):",
            "Caffeine (mg):",
            "Cholesterol (mg):",
            "Alcohol (g):"
        )
        lines.add(header.joinToString(",") { csvCell(it) })
        val dateFormat = SimpleDateFormat("d-MMM-yy", Locale.getDefault())
        val sortedTotals = dailyTotals.sortedWith(
            compareByDescending { dateFormat.parse(it.date)?.time ?: Long.MIN_VALUE }
        )
        sortedTotals.forEach { totals ->
            val weightEntry = weightByDate[totals.date]
            val weightText = weightEntry?.let { formatWeight(it.weight) } ?: "NA"
            val commentsText = weightEntry?.comments.orEmpty()
            val row = listOf(
                totals.date,
                weightText,
                commentsText,
                formatNumber(totals.amountEaten),
                formatNumber(totals.energy),
                formatNumber(totals.protein),
                formatNumber(totals.fatTotal),
                formatNumber(totals.saturatedFat),
                formatNumber(totals.transFat),
                formatNumber(totals.polyunsaturatedFat),
                formatNumber(totals.monounsaturatedFat),
                formatNumber(totals.carbohydrate),
                formatNumber(totals.sugars),
                formatNumber(totals.sodiumNa),
                formatNumber(totals.dietaryFibre),
                formatNumber(totals.calciumCa),
                formatNumber(totals.potassiumK),
                formatNumber(totals.thiaminB1),
                formatNumber(totals.riboflavinB2),
                formatNumber(totals.niacinB3),
                formatNumber(totals.folate),
                formatNumber(totals.ironFe),
                formatNumber(totals.magnesiumMg),
                formatNumber(totals.vitaminC),
                formatNumber(totals.caffeine),
                formatNumber(totals.cholesterol),
                formatNumber(totals.alcohol)
            )
            lines.add(row.joinToString(",") { csvCell(it) })
        }
        return lines.joinToString("\n")
    }

    suspend fun exportDatabaseToFolder(folderUri: Uri): Boolean {
        val fileUri = ensureDocumentInFolder(folderUri, DATABASE_FILE_NAME, "application/octet-stream")
            ?: return false
        return copyDatabaseToUri(fileUri)
    }

    suspend fun importDatabaseFromFolder(folderUri: Uri): Boolean {
        val sourceUri = findDocumentInFolder(folderUri, DATABASE_FILE_NAME) ?: return false
        return copyDatabaseFromUri(sourceUri)
    }

    suspend fun exportCsvToFolder(folderUri: Uri): Boolean {
        val fileUri = ensureDocumentInFolder(folderUri, DAILY_CSV_FILE_NAME, "text/csv") ?: return false
        return withContext(Dispatchers.IO) {
            val dailyTotals = aggregateDailyTotals(dbHelper.readEatenFoods())
            val weightByDate = dbHelper.readWeights().associateBy { it.dateWeight }
            val csv = buildEatenDailyAllCsv(dailyTotals, weightByDate)
            try {
                context.contentResolver.openOutputStream(fileUri, "w")?.bufferedWriter()?.use { writer ->
                    writer.write(csv)
                } ?: return@withContext false
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    var showHelpSheet by remember { mutableStateOf(false) }
    var showExportWarning by remember { mutableStateOf(false) }
    var showImportWarning by remember { mutableStateOf(false) }
    var showExportCsvDialog by remember { mutableStateOf(false) }
    var exchangeFolderUri by remember { mutableStateOf<Uri?>(loadExchangeFolderUri()) }
    var exportTargetPath by remember { mutableStateOf<String?>(null) }
    var importSourcePath by remember { mutableStateOf<String?>(null) }
    var exportCsvTargetPath by remember { mutableStateOf<String?>(null) }
    var importSourceUri by remember { mutableStateOf<Uri?>(null) }
    var pendingFolderAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showAddWeightDialog by remember { mutableStateOf(false) }
    var weightInput by rememberSaveable { mutableStateOf("") }
    var weightCommentsInput by rememberSaveable { mutableStateOf("") }
    var weightDateMillis by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var showWeightDatePicker by remember { mutableStateOf(false) }
    var weightEntries by remember { mutableStateOf(emptyList<WeightEntry>()) }
    var selectedWeight by remember { mutableStateOf<WeightEntry?>(null) }
    var editingWeight by remember { mutableStateOf<WeightEntry?>(null) }
    var deletingWeight by remember { mutableStateOf<WeightEntry?>(null) }
    var editWeightInput by rememberSaveable { mutableStateOf("") }
    var editWeightCommentsInput by rememberSaveable { mutableStateOf("") }
    var editWeightDateMillis by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    val weightDateFormat = remember { SimpleDateFormat("d-MMM-yy", Locale.getDefault()) }
    val weightDatePickerState = rememberDatePickerState(initialSelectedDateMillis = weightDateMillis)
    val helpSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val utilitiesHelpText = """
# **Utilities**
This screen contains various miscellaneous utilities .

- **Export db**: Writes/overwrites the `foods.db` file in the folder you choose. On first use you'll be asked to pick a folder (starts at Downloads); that choice is remembered.
    - The dialog shows the target path and includes a **Change folder** button to relink if permissions are lost or you want a new location.
- **Import db**: Replaces the app database with `foods.db` from the currently selected folder.
    - The dialog shows the source path and a **Change folder** button to pick a new location. If `foods.db` is missing there, you'll be prompted to place it first.
- **Export csv**: Writes/overwrites `EatenDailyAll.csv` in the selected folder (same remembered folder as above).
    - It exports the Eaten table daily totals shown in the scrollable table viewer of the Eaten Foods screen, with the All option selected and across all dates.
    - It is in csv format with each date per row. Columns match the scrollable table viewer on the Eaten Table screen and include `My weight (kg)` and `Comments` as the second and third columns.
    - The dialog shows the target path and includes a **Change folder** button to relink when needed.
- **Weight Table**: a scrollable table viewer which displays records from the weight table.
    - Records are displayed in descending date order.
    - When any record is selected (by tapping it) a selection panel appears at the bottom of the screen. It displays details of the selected record followed by three buttons below it:
        - **Add**: It enables a weight record to be added to the Weight table.
            - It opens the **Add weight** dialog so you can enter a new weight and date.
            - You can optionally enter Comments for the weight entry.
            - The original selected weight record has no relevance to this activity. It is just a way of making the Add button available.
            - You cannot use a date that already exists.
            - Press the **Confirm** button when you are ready to confirm your changes. This wll be ignored if the date already exists or the weight is not a number or is blank. in these cases an appropriate Toast will be temporarily displayed.
        - **Edit**: It enables the selected weight record to be modified.
            - It opens the **Edit weight** dialog where you can modify the weight in kg. The date is shown but not editable.
            - You can edit Comments for the weight entry.
            - Press the **Confirm** button when you are ready to confirm your changes. This then transfers focus back to this screen where the just modified weight record will be visible. The selection panel is also closed.
        - **Delete**: It deletes the selected weight record.
            - It opens the **Delete weight?** warning dialog box.
            - Press the **Confirm** button when you are ready to confirm the delete. This then transfers focus back to this screen where the deleted weight record will be disappear from this scrollable table viewer. The selection panel is also closed.
        - You can abort these processes (from the above dialogs) by tapping anywhere outside the dialog box or pressing the "back" button on the bottom menu. This closes the dialog and transfers focus back to this screen. The selection panel is also closed.
    - If the Weight Table is empty (which is usually the case if the app has just been installed with the default internal databse) there is no way to enable the selection panel so that a weight record can be created by pressing the Add button. Instead the message "The Weight table has no records" is displayed, followed by the **Add** button. Press it to add a new weight record. Once at least one record exists this GUI layout disappears.
    - **The intention is** that each day at preferably the same time you weight yourself naked or with the same weight clothes and record this weight in the Weight table. When analysing your aggregated data from the Eaten table you will see your days weight together with your daily Energy and nutrient amounts. 
***
# **Weight table structure**
```
Field name          Type    Units

WeightId            INTEGER	
Weight              REAL    kg
DateWeight          TEXT    d-MMM-yy
Comments            TEXT
```
The **WeightId** field is never explicitly displayed or considered. It is a Primary Key that is auto incremented when a record is created.

The **Comments** field is optional and may be blank.

The remaining fields are self expanatory.

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

    LaunchedEffect(Unit) {
        val storedFolder = exchangeFolderUri
        if (storedFolder != null && canReadAndroidTree(storedFolder)) {
            exportTargetPath = buildAndroidFileDisplay(storedFolder, DATABASE_FILE_NAME)
            exportCsvTargetPath = buildAndroidFileDisplay(storedFolder, DAILY_CSV_FILE_NAME)
        } else {
            exchangeFolderUri = null
            clearExchangeFolderUri()
        }
    }

    LaunchedEffect(editingWeight?.weightId) {
        editWeightInput = editingWeight?.let { entry ->
            formatWeight(entry.weight)
        } ?: ""
        editWeightCommentsInput = editingWeight?.comments ?: ""
        editWeightDateMillis = editingWeight?.dateWeight
            ?.takeIf { it.isNotBlank() }
            ?.let { dateString ->
                runCatching { weightDateFormat.parse(dateString)?.time }.getOrNull()
            } ?: System.currentTimeMillis()
    }

    BackHandler(enabled = selectedWeight != null) {
        selectedWeight = null
    }

    LaunchedEffect(weightDateMillis) {
        weightDatePickerState.selectedDateMillis = weightDateMillis
    }

    val exchangeFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            showPlainToast(context, "Folder selection cancelled")
            pendingFolderAction = null
            return@rememberLauncherForActivityResult
        }
        val uri = result.data?.data
        if (uri == null) {
            showPlainToast(context, "Folder selection cancelled")
            pendingFolderAction = null
            return@rememberLauncherForActivityResult
        }
        val flags = result.data?.flags ?: 0
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                (flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)) or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Best effort; some providers don't allow persistable permissions.
        }
        storeExchangeFolderUri(uri)
        exchangeFolderUri = uri
        pendingFolderAction?.invoke()
        pendingFolderAction = null
    }

    fun pickExchangeFolder(onPicked: (Uri) -> Unit) {
        pendingFolderAction = {
            val refreshed = exchangeFolderUri
            if (refreshed != null && canReadAndroidTree(refreshed)) {
                onPicked(refreshed)
            } else {
                showPlainToast(context, "Unable to access selected folder")
            }
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
            exchangeFolderUri?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
                ?: putExtra(DocumentsContract.EXTRA_INITIAL_URI, MediaStore.Downloads.EXTERNAL_CONTENT_URI)
        }
        exchangeFolderPickerLauncher.launch(intent)
    }

    fun withExchangeFolder(onReady: (Uri) -> Unit) {
        pendingFolderAction = null
        val stored = exchangeFolderUri
        if (stored != null && !canReadAndroidTree(stored)) {
            exchangeFolderUri = null
            clearExchangeFolderUri()
        }
        if (stored != null && canReadAndroidTree(stored)) {
            exportTargetPath = buildAndroidFileDisplay(stored, DATABASE_FILE_NAME)
            exportCsvTargetPath = buildAndroidFileDisplay(stored, DAILY_CSV_FILE_NAME)
            onReady(stored)
            return
        }
        pendingFolderAction = {
            val refreshed = exchangeFolderUri
            if (refreshed != null && canReadAndroidTree(refreshed)) {
                exportTargetPath = buildAndroidFileDisplay(refreshed, DATABASE_FILE_NAME)
                exportCsvTargetPath = buildAndroidFileDisplay(refreshed, DAILY_CSV_FILE_NAME)
                onReady(refreshed)
            } else {
                showPlainToast(context, "Unable to access selected folder")
            }
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, MediaStore.Downloads.EXTERNAL_CONTENT_URI)
        }
        exchangeFolderPickerLauncher.launch(intent)
    }

    fun startExportFlow() {
        withExchangeFolder { folderUri ->
            exportTargetPath = buildAndroidFileDisplay(folderUri, DATABASE_FILE_NAME)
            showExportWarning = true
        }
    }

    fun startImportFlow() {
        importSourceUri = null
        importSourcePath = null
        withExchangeFolder { folderUri ->
            coroutineScope.launch {
                val sourceUri = findDocumentInFolder(folderUri, DATABASE_FILE_NAME)
                if (sourceUri == null) {
                    val displayDirectory = buildAndroidDirectoryDisplay(folderUri)
                    showPlainToast(context, "No $DATABASE_FILE_NAME found in $displayDirectory")
                    return@launch
                }
                importSourceUri = sourceUri
                importSourcePath = buildAndroidFileDisplay(folderUri, DATABASE_FILE_NAME)
                showImportWarning = true
            }
        }
    }

    fun startExportCsvFlow() {
        withExchangeFolder { folderUri ->
            exportCsvTargetPath = buildAndroidFileDisplay(folderUri, DAILY_CSV_FILE_NAME)
            showExportCsvDialog = true
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
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { startExportFlow() }) {
                        Text("Export db")
                    }
                    Button(onClick = { startImportFlow() }) {
                        Text("Import db")
                    }
                    Button(onClick = { startExportCsvFlow() }) {
                        Text("Export csv")
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text(
                    text = "Weight Table",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (weightEntries.isEmpty()) {
                    Text(
                        text = "No weight entries yet.",
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { showAddWeightDialog = true },
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Text("Add")
                    }
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
                            .align(Alignment.Start)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
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
                        onAdd = { showAddWeightDialog = true },
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This will overwrite $DATABASE_FILE_NAME in the selected folder.")
                    Text(
                        text = exportTargetPath ?: "Pick a folder to continue",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = {
                        pickExchangeFolder { folderUri ->
                            exportTargetPath = buildAndroidFileDisplay(folderUri, DATABASE_FILE_NAME)
                        }
                    }) {
                        Text("Change folder")
                    }
                    Button(
                        enabled = exportTargetPath != null,
                        onClick = {
                        showExportWarning = false
                        withExchangeFolder { folderUri ->
                            coroutineScope.launch {
                                val success = exportDatabaseToFolder(folderUri)
                                showPlainToast(
                                    context,
                                    if (success) "Database exported" else "Failed to export database"
                                )
                            }
                        }
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
            onDismissRequest = {
                showImportWarning = false
                importSourcePath = null
                importSourceUri = null
            },
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This will replace the app database with $DATABASE_FILE_NAME from:")
                    Text(
                        text = importSourcePath ?: "No $DATABASE_FILE_NAME found yet",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = {
                        pickExchangeFolder { folderUri ->
                            coroutineScope.launch {
                                val sourceUri = findDocumentInFolder(folderUri, DATABASE_FILE_NAME)
                                if (sourceUri == null) {
                                    val displayDirectory = buildAndroidDirectoryDisplay(folderUri)
                                    showPlainToast(
                                        context,
                                        "No $DATABASE_FILE_NAME found in $displayDirectory"
                                    )
                                } else {
                                    importSourceUri = sourceUri
                                    importSourcePath = buildAndroidFileDisplay(folderUri, DATABASE_FILE_NAME)
                                }
                            }
                        }
                    }) {
                        Text("Change folder")
                    }
                    Button(
                        enabled = importSourcePath != null,
                        onClick = {
                        showImportWarning = false
                        val folderUri = exchangeFolderUri
                        if (folderUri == null || importSourceUri == null) {
                            startImportFlow()
                            return@Button
                        }
                        coroutineScope.launch {
                            val success = importDatabaseFromFolder(folderUri)
                            if (success) {
                                refreshWeights()
                                showPlainToast(context, "Database imported")
                            } else {
                                showPlainToast(context, "Failed to import database")
                            }
                            importSourcePath = null
                            importSourceUri = null
                        }
                    }) {
                        Text("Confirm")
                    }
                }
            },
            dismissButton = {}
        )
    }

    if (showExportCsvDialog) {
        AlertDialog(
            onDismissRequest = { showExportCsvDialog = false },
            title = {
                Text(
                    text = "Export CSV?",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This will save $DAILY_CSV_FILE_NAME to:")
                    Text(
                        text = exportCsvTargetPath ?: "Pick a folder to continue",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = {
                        pickExchangeFolder { folderUri ->
                            exportCsvTargetPath = buildAndroidFileDisplay(folderUri, DAILY_CSV_FILE_NAME)
                        }
                    }) {
                        Text("Change folder")
                    }
                    Button(
                        enabled = exportCsvTargetPath != null,
                        onClick = {
                        showExportCsvDialog = false
                        withExchangeFolder { folderUri ->
                            coroutineScope.launch {
                                val success = exportCsvToFolder(folderUri)
                                showPlainToast(
                                    context,
                                    if (success) "CSV exported" else "Failed to export CSV"
                                )
                            }
                        }
                    }) {
                        Text("Confirm")
                    }
                }
            },
            dismissButton = {}
        )
    }

    if (showAddWeightDialog) {
        WeightAddDialog(
            weightValue = weightInput,
            onWeightChange = { weightInput = it },
            commentsValue = weightCommentsInput,
            onCommentsChange = { weightCommentsInput = it },
            dateText = weightDateFormat.format(Date(weightDateMillis)),
            onPickDate = { showWeightDatePicker = true },
            onSave = {
                val weightValue = parseWeightInput(weightInput)
                if (weightValue == null) {
                    showPlainToast(context, "Enter a valid weight")
                    return@WeightAddDialog
                }
                val dateValue = weightDateFormat.format(Date(weightDateMillis))
                if (dateValue.isBlank()) {
                    showPlainToast(context, "Pick a date")
                    return@WeightAddDialog
                }
                if (weightEntries.any { it.dateWeight == dateValue }) {
                    showPlainToast(context, "Date already exists")
                    return@WeightAddDialog
                }
                coroutineScope.launch {
                    val success = withContext(Dispatchers.IO) {
                        dbHelper.insertWeight(dateValue, weightValue, weightCommentsInput)
                    }
                    if (success) {
                        weightInput = ""
                        weightCommentsInput = ""
                        refreshWeights()
                        showPlainToast(context, "Weight saved")
                        showAddWeightDialog = false
                        selectedWeight = null
                    } else {
                        showPlainToast(context, "Failed to save weight")
                    }
                }
            },
            onDismiss = {
                showAddWeightDialog = false
                selectedWeight = null
            }
        )
    }

    if (editingWeight != null) {
        WeightEditDialog(
            weightValue = editWeightInput,
            onWeightChange = { editWeightInput = it },
            commentsValue = editWeightCommentsInput,
            onCommentsChange = { editWeightCommentsInput = it },
            dateText = weightDateFormat.format(Date(editWeightDateMillis)),
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
                        dbHelper.updateWeight(entry.weightId, dateValue, weightValue, editWeightCommentsInput)
                    }
                    if (success) {
                        editingWeight = null
                        selectedWeight = null
                        refreshWeights()
                        showPlainToast(context, "Weight updated")
                    } else {
                        showPlainToast(context, "Failed to update weight")
                    }
                }
            },
            onDismiss = {
                editingWeight = null
                selectedWeight = null
            }
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
            onDismiss = {
                deletingWeight = null
                selectedWeight = null
            }
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
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = entry.dateWeight,
                        modifier = Modifier.weight(0.28f),
                        fontWeight = if (selectedWeightId == entry.weightId) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        }
                    )
                    Text(
                        text = "${formatWeight(entry.weight)} kg",
                        modifier = Modifier.weight(0.16f),
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = entry.comments,
                        modifier = Modifier.weight(0.56f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun WeightSelectionPanel(
    entry: WeightEntry,
    onAdd: () -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = entry.dateWeight.ifBlank { "Unknown date" },
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${formatWeight(entry.weight)} kg",
                    textAlign = TextAlign.Start
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                Button(onClick = onAdd) { Text("Add") }
                Button(onClick = onEdit) { Text("Edit") }
                Button(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun WeightAddDialog(
    weightValue: String,
    onWeightChange: (String) -> Unit,
    commentsValue: String,
    onCommentsChange: (String) -> Unit,
    dateText: String,
    onPickDate: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val screenHeight = with(density) { windowInfo.containerSize.height.toDp() }
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
                .heightIn(max = screenHeight * 0.9f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        text = "Add weight",
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(onClick = onPickDate) {
                            Text(dateText)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextField(
                            value = weightValue,
                            onValueChange = onWeightChange,
                            label = { Text("Weight (kg)") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = commentsValue,
                        onValueChange = onCommentsChange,
                        label = { Text("Comments") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = Int.MAX_VALUE
                    )
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
private fun WeightValueRow(
    label: String,
    value: String,
    valueMaxLines: Int = 1,
    valueOverflow: TextOverflow = TextOverflow.Clip,
    valueTextAlign: TextAlign = TextAlign.End,
    rowFillFraction: Float = 0.5f
) {
    Row(
        modifier = Modifier.fillMaxWidth(rowFillFraction),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = valueMaxLines,
            overflow = valueOverflow,
            textAlign = valueTextAlign
        )
    }
}

@Composable
private fun WeightEditDialog(
    weightValue: String,
    onWeightChange: (String) -> Unit,
    commentsValue: String,
    onCommentsChange: (String) -> Unit,
    dateText: String,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val screenHeight = with(density) { windowInfo.containerSize.height.toDp() }
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
                .heightIn(max = screenHeight * 0.9f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
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
                        Text(
                            text = dateText.ifBlank { "-" },
                            modifier = Modifier.weight(0.4f),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextField(
                            value = weightValue,
                            onValueChange = onWeightChange,
                            label = { Text("Weight (kg)") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(0.6f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = commentsValue,
                        onValueChange = onCommentsChange,
                        label = { Text("Comments") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = Int.MAX_VALUE
                    )
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
            Text("This will remove the selected weight record.")
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
