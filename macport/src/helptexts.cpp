// helptexts.cpp — help markdown transcribed from MainActivity.kt (Kotlin
// string templates resolved; Android-specific wording kept where it still
// applies, adjusted to "tap/click" neutral where harmless).
#include "helptexts.h"

const std::string& foodsHelpText() {
    static const std::string s = R"MD(# **Foods Table**
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
- A **scrollable table viewer** which displays records from the Foods table. When a particular food is selected (by clicking it) a selection panel appears at the bottom of the screen. It displays the description of the selected food followed by nine buttons arranged in two rows. The top row holds the actions that operate on the selected food: **LOG**, **Edit**, **Copy**, **Convert**, **Delete**. The bottom row holds the actions that do not depend on the selection: **Add**, **Json**, **AI**, **Utilities**.
    - **LOG**: logs the selected food into the Eaten Table.
        - It opens a dialog box where you can specify the amount eaten as well as the date and time this has occurred (with the default being now).
        - Press the **Confirm** button when you are ready to log your food. This transfers focus to the Eaten Table screen where the just logged food will be visible. Read the help on that Screen for more help.
        - You can abort this process by clicking anywhere outside the dialog box. This closes it.
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
        - In **NIP mode** (the default) Claude returns a Diet Sentry compatible JSON which is automatically "auto-pumped" into the **Add Food using Json** screen — one click on **Confirm** there adds the food and lands on this Foods Table with the new food highlighted.
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
        - You can change your mind about doing this by just clicking anywhere outside the dialog box. This closes it.
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
Folate              REAL    ug
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
- If a product claims to be a "good source of fibre," then dietary fibre must be listed.
- If a claim is made about specific fats (e.g., omega-3, cholesterol, trans fats), those must also be included.

- **The Foods table includes most such possible additional nutrients.**

### **Formatting Rules**
- Significant figures: Values must be reported to no more than three significant figures.
- Decimal places: Protein, fat, saturated fat, carbohydrate, and sugars are rounded to 1 decimal place if under 100 g. Energy and sodium are reported as whole numbers (no decimals).
- Serving size: Determined by the food business, but must be clearly stated.

- **The Foods table does not explicitly consider servings, though they might be noted in the FoodDescription text field or notes.**

### **Exemptions**
Some foods don't require a NIP unless a nutrition claim is made:
- Unpackaged foods (e.g., fresh fruit, vegetables)
- Foods made and packaged at point of sale (e.g., bakery bread)
- Herbs, spices, tea, coffee, and packaged water (no significant nutritional value)

- **Notwithstanding the above the Foods table includes many such items**
***
# **Keyboard navigation**
Every screen of this app can be driven without the mouse:
- **Tab** / **Shift+Tab** move the focus forwards / backwards through the controls in visual order (top bar first, then the content). A purple ring marks the focused control; it appears as soon as you use the keyboard and hides again when you use the mouse.
- **Arrow keys** move between neighbouring controls, and **Up** / **Down** step through the rows of a list. A list counts as a single Tab stop, so Tab steps *past* it while the arrows walk inside it (the list scrolls along).
- **Enter** or **Space** activates the focused control: presses a button, selects a list row, toggles a checkbox or switch, opens a drop-down. On a text field it starts editing — type, then Tab moves on (Enter finishes editing; in the filter field it also applies the filter).
- **Escape** goes back a screen, or closes an open dialog, drop-down or help sheet.
- **Ctrl+Q** (**Cmd+Q** on a Mac) quits the app from any screen or dialog — the same as the window's close button (or **Alt+F4** on Windows). Escape never quits: on the Foods Table it only clears the selection.
- In the LOG and amount dialogs the amount field is active as soon as the dialog opens: type the amount and press **Enter** to confirm.
- In a help sheet like this one the arrow keys, **PageUp** / **PageDown** and **Home** / **End** scroll the text; **Escape** closes it.
***
)MD";
    return s;
}

const std::string& eatenHelpText() {
    static const std::string s = R"MD(# **Eaten Table**
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
- A **scrollable table viewer** which displays records (possibly consolidated by date) from the Eaten table. If a particular logged food is selected (by clicking it) a selection panel appears at the bottom of the screen. It displays the description of the selected food log and its time stamp followed by two buttons below it:
    - **Edit**: It enables the amount and time stamp of the logged eaten food to be modified.
        - It opens a dialog box where you can specify the amount eaten as well as the date and time this has occurred (with the default being now).
        - Press the **Confirm** button when you are ready to confirm your changes. This then transfers focus back to the Eaten Table screen where the just modified food log will be visible and selected. The selection panel for this log (with the Edit and Deleted buttons) will close.
        - You can abort this process by clicking anywhere outside the dialog box. This closes it and transfers focus in the same way as described above.
    - **Delete**: deletes the selected food log from the Eaten table.
        - It opens a dialog which warns you that you will be deleting the selected food log from the Eaten table.
        - This is irrevocable if you press the **Delete** button.
        - You can change you mind about doing this by just clicking anywhere outside the dialog box. This closes it and returns focus to the Eaten Table screen. The selection panel (with the Edit and Deleted buttons) is also closed.
    - If food logs consolidated by date are displayed (ie. the "Daily totals" check box is ticked), selection for editing or deletion is not possible. Instead, clicking a day's daily totals card opens a sheet with two actions (see next section).
***
# **AI explanation of a day's daily totals**
When the **Daily totals** checkbox is ticked, clicking any day's totals card slides up a sheet from the bottom of the screen with two actions:
- **Explain this day (AI)** — sends the day's complete totals (all 24 nutrient values, the total amount eaten, and any recorded weight + weight comments) plus your **profile** text (see below) to Anthropic's Claude. The reply — 2 to 3 short paragraphs assessing intake against Australian NHMRC NRVs — appears in a dialog with the per-call API cost shown underneath. Requires an Anthropic API key set on the **Add Food using AI** screen (gear icon).
- **Edit my profile** — opens a free-text editor (the **Custom Instructions** field) for a small persistent paragraph that describes you (e.g. "age 67 male, weight 89kg, dietary goals: low sodium"). The text is sent alongside each daily totals query so Claude can tailor its assessment. Empty profile is fine — Claude will give general adult Australian guidance. The text persists across app launches.

**Settings used by this flow:**
- **API key** and **model** are read from the AI Settings dialog on the **Add Food using AI** screen — switching from Sonnet 4.6 to Haiku 4.5 over there changes the next Explain call too.
- **User profile** persists in the app preferences.
- **Web search** and **Extended thinking** toggles are *not* honoured by this flow — they're hardcoded off so each Explain call has a predictable cost (~\$0.01 on Sonnet 4.6, ~\$0.003 on Haiku 4.5). Those toggles still apply to the AI chat screen.
- **System prompt** is always `EXPLAINsysprompt.txt` (different from the AI chat's NIP/Recipe/General prompts) — the NIP-mode toggle in AI Settings has no effect here.

The system prompt is bundled at `assets/EXPLAINsysprompt.txt` and instructs Claude to use NHMRC NRVs, Australian English, and to flag nutrients that are notably under- or over-consumed (e.g. saturated fat above ~22 g, sodium above ~2300 mg, alcohol present in non-trivial amounts).
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
Folate                  REAL    ug
IronFe                  REAL    mg
MagnesiumMg             REAL    mg
VitaminC                REAL    mg
Caffeine                REAL    mg
Cholesterol             REAL    mg
Alcohol                 REAL    g
```
The **EatenId** field is never explicitly displayed or considered. It is a Primary Key that is auto incremented when a record is created.

The **DateEaten** and **TimeEaten** text fields store the food logs time stamp

The **EatenTs** field is an integer that specifies the number of minutes since a reference time stamp. It allows easy sorting by date/time of when a food was logged (it is recalculated if the Date and Time eaten are changed.

The **FoodDescription** is the same field as for a Foods table record.

The remaining (**Energy** and **Nutrient fields**) are the same as for the corresponding Foods table record, except that they are scaled by the amount of the food eaten. Eg. if EatenAmount=300 then all these field values are multiplied by 3.
***
)MD";
    return s;
}

const std::string& graphHelpText() {
    static const std::string s = R"MD(# **Eaten Graph**
Visualises a chosen metric per day from your Eaten Table (and the Weight table) over a chosen date range. Reached from **Utilities -> Eaten Graph**.

## Controls
- **Metric dropdown** — pick what to plot. Default is Energy (kJ). The dropdown lists, in order:
  - **My weight (kg)** — daily weight from the Weight table (Utilities -> Weight Table). Days without a weight entry are skipped, not zero-filled. A weight of exactly **0.1 kg** is treated as a "not measured" sentinel: it's **excluded from the Average / Min / Max stats** and from the y-axis range computation (so the chart's lower bound sits just below your real Min instead of at 0), and the day count line says "N of M days measured". Sentinel bars may sit below the visible y-range and not render. The **Total** row is omitted for weight (summing body weights across days is meaningless).
  - **Amount (g/mL)** — total mass/volume of food eaten that day. The unit is approximate when a day mixes solids and liquids.
  - **Energy (kJ)** and the 22 other nutrient fields tracked in the Foods table.
- **Date range chips** — preset ranges (today is always excluded from presets, since the current day rarely has its full data yet — pick **Custom** if you want to include today):
  - **1W** — the 7 days ending yesterday
  - **1M** — the 30 days ending yesterday (default)
  - **3M** — the 90 days ending yesterday
  - **1Y** — the 365 days ending yesterday
  - **All** — every day with logged data, ending yesterday
  - **Custom** — opens a date-range picker; the picked from/to dates are honoured exactly (today is fine as the end date)
- The **inclusive from-to dates** for the active range are displayed under the chips so you can see what window you're looking at.
- The chart only shows days that actually have eaten records — gaps in your logging produce gaps in the bars (no zero-fill), since zero would imply you ate nothing on those days.
- The **Summary** card below the chart shows total, average per day, max in a day, and min in a day over the selected range.
- The **y-axis lower bound** is a "nice" round value somewhat below the chart's Min (clamped to >= 0), so small variations between days are visually distinguishable instead of being squashed at the top of a 0-anchored axis. The bound auto-recomputes when you change metric or date range.
- **Selections persist** — the chosen metric, the active date-range chip, and any custom from/to dates are saved across navigation away from this screen and across app restarts.

## Notes
- All data comes from your local Eaten Table and Weight table — no network call. The graph is recomputed once when this screen opens, so newly logged foods or weights are picked up next time you visit.
- X-axis labels become sparser as the range gets longer (every 5th day for ~30, every 14th for ~90, every 30th for a year).
)MD";
    return s;
}

const std::string& jsonHelpText() {
    static const std::string s = R"MD(# **Add Food using Json**
- Reached two ways:
    - **Manually**: click the **Json** button on the **Foods Table** screen.
    - **Automatically (auto-pump)**: when the **Add Food using AI** screen produces a JSON reply, it is "auto-pumped" here pre-filled in the text field — ready for one-click **Confirm**. Both NIP-mode JSON (regular food) and recipe-mode JSON (recipe of ingredients) auto-pump.
- Like other screens it has a **help** and a **navigation** button in the top row, then a **text field** that takes up the rest of the screen, followed by a **Confirm** button.
- Two JSON shapes are accepted:
    - **NIP food JSON** — `FoodDescription` plus the 24 nutrient fields. On Confirm a single Food row is added. If its `FoodDescription` carries a `{recipe=…}` marker the marker is removed and the plain ` #` / `mL#` ending applied — that marker means "has ingredient rows", which only Recipe JSON creates, so keeping it would produce a recipe with no ingredients.
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
  "notes": "API cost: $0.0431"
}
```
AI-generated recipes also get ` (AI)` appended to the recipe's FoodDescription before the trailing `{recipe=Xg}` marker the app computes from ingredient totals.

NOTE: **Any line feeds, tabs and spaces outside of "any text" are entirely optional** — minified JSON works too.

- Click **Confirm** to process the JSON. Both shapes end on the **Foods Table** with the new food (or recipe) highlighted, but the processing in between is very different.

**NIP food JSON pipeline (3 steps):**

1. **Parse** — read the `FoodDescription` and the 24 nutrient fields from the JSON.
2. **Insert** — write one row to the **Foods** table with those values exactly. No lookup, no scaling, no linking — the JSON *is* the Foods row.
3. **Navigate** — pop back to the Foods Table with the new food highlighted via the filter.

Errors show as a Toast: `"Please paste valid JSON"` if there are no JSON braces, or `"Invalid JSON or missing fields"` if any of the 24 nutrient fields is missing or non-numeric.

**Recipe JSON pipeline (8 steps)** — produces the same database state as building the recipe by hand on the **Add Recipe** screen, just driven from JSON instead of clicks:

1. **Detect** the `type: "recipe"` discriminator at the top of the JSON. Without that key the JSON falls through to the NIP pipeline above.
2. **Resolve every ingredient** against the live Foods table — for each entry in `ingredients[]`:
    - `FoodId > 0` and `AmountUsed > 0` (in grams).
    - The `FoodId` resolves to an actual Food row via `getFoodById(FoodId)`.
    - The resolved Food is **solid** — its `FoodDescription` must not end in `mL` or `mL#`. Same gram-only rule the manual **Add Recipe** screen enforces with its "Only foods measured in grams can be added to a recipe" dialog.
3. **Atomic check** — if *any* ingredient fails validation, a specific Toast appears (e.g. `"FoodId 12345 not in Foods table"`, `"FoodId 12345 is a liquid; recipes need solids"`, `"ingredients[2] AmountUsed must be > 0"`) and the whole recipe is rejected. **No database writes happen** — there is no partial state to clean up.
4. **Stage Recipe rows** — for each validated ingredient, write one row into the **Recipe** table with `FoodId = 0` (a temp marker, the link target isn't known yet), `Amount = AmountUsed`, `FoodDescription` copied from the resolved Food, and all 24 nutrient fields **scaled by `AmountUsed / 100`** (so a 250 g ingredient stores nutrient values that are 2.5x the per-100 g values from its Food row).
5. **Aggregate** — sum the per-row nutrient values across all staged Recipe rows (that's the recipe's total nutrient mass at its natural weight). Compute `totalAmount = sum of AmountUsed` (in g) and a scaling factor `scale = 100 / totalAmount`.
6. **Build the parent Food row** with:
    - `FoodDescription = "<JSON's FoodDescription> (AI) {recipe=<totalAmount>g}"`. The ` (AI)` marker tags this row as AI-generated; the trailing `{recipe=<weight>g}` marker is the standard recipe identifier the rest of the app reads.
    - The 24 nutrient fields = step 5's totals x `scale` — i.e. per 100 g of recipe.
    - `notes` copied from the JSON (which already includes the API-cost annotation appended by the AI screen).
7. **Insert the parent** to get a fresh `FoodId`, then **link** the staged Recipe rows by running `UPDATE Recipe SET FoodId = <new id> WHERE FoodId = 0` — every staged row now points back at the parent. (The same staging-and-link pattern the manual Add Recipe screen uses on its Confirm.)
8. **Navigate** to the Foods Table with the new recipe highlighted — so the result feels identical regardless of how the recipe was built.

The end state in `foods.db` is byte-identical to clicking Confirm on the manual Add Recipe screen with the same ingredients and amounts. There is no separate "AI recipe" code path in the database — the only visible difference is the ` (AI)` substring in the FoodDescription, which lets you spot AI-built recipes in the Foods Table.
- **To abort any actions on this screen** press either of the two "back" buttons. Destination depends on how you got here:
    - If you came in via the **Json** button: you return to the Foods Table.
    - If you came in via the AI auto-pump: you return to the **Add Food using AI** chat with your conversation preserved, so you can iterate (e.g. ask for a corrected JSON).
***
# **AI generation of JSON**
The recommended way to obtain JSON for this screen is the app's own **Add Food using AI** screen — click the **AI** button on the Foods Table.
- With **NIP mode** on (default), Claude follows a FSANZ-compliant NIP-extraction prompt and calls a `lookup_food` tool that queries the live Foods table for nutrient values on demand (no big knowledge-base attachment). Replies are Diet Sentry compatible NIP JSON and auto-pump straight into this screen.
- With NIP mode on, if your message contains the word "recipe" Claude swaps to the recipe prompt and produces a Recipe JSON instead. Same auto-pump path; Confirm builds the recipe.
- You can attach photos of labels or on-pack NIPs in the AI screen (the **+** button is multi-select) and Claude will read them.
- For an external workflow, paid ChatGPT subscribers can use the "NIP generator" GPT (https://chatgpt.com -> Explore GPTs) and copy-paste its reply into the text field above. The NIP schema is the same.
- You can hand-edit the JSON in the text field before pressing **Confirm** — for example, to tweak the FoodDescription or refine values. Just keep the JSON syntactically valid.
)MD";
    return s;
}

const std::string& aiHelpText() {
    static const std::string s = R"MD(# **Add Food using AI**
- Connects this computer to **Anthropic's Claude** models. Behaviour depends on the **NIP mode** toggle in settings and on whether your message contains the word "recipe":
  - **NIP mode ON, no "recipe":** the bundled NIP system prompt (`NIPsysprompt.txt`) is sent as system context. Claude calls a `lookup_food` tool that queries the live Foods table database for nutrient values on demand (no big knowledge-base attachment). Every reply is a Diet Sentry compatible JSON object inside a ```json``` code block and is **auto-pumped into the Json screen** so you can hit Confirm to add the food.
  - **NIP mode ON, "recipe" in message:** Claude switches to the recipe prompt (`RECIPEsysprompt.txt`). The same `lookup_food` tool runs in *recipe mode* — pre-filtering out liquids and AI/user-added records (FoodDescriptions ending in `mL`, `mL#`, or `#`) so only solid, non-user-added foods can be picked as ingredients. The reply is a recipe JSON (`type: "recipe"`, `ingredients[]`) and auto-pumps into the Json screen too — Confirm there creates the recipe (a Foods row plus linked Recipe rows), then lands on the Foods Table with the new recipe highlighted.
  - **NIP mode OFF:** Claude is a general-purpose assistant introduced as **"Davey Diet"** by the bundled `GenericSysprompt.txt`. The word "recipe" in your message has no special effect (the recipe workflow is gated behind NIP mode). Replies stay in this chat; nothing is auto-pumped.
- **Setup:** Click the **gear** icon, paste your Anthropic API key (from `console.anthropic.com`), pick a model (Opus 4.7 / Sonnet 4.6 / Haiku 4.5), choose your toggles, and **Save**. The key is stored only on this device. All five settings (key, model, Web search, NIP mode, Extended thinking) persist across launches.
- **Asking:** Type a food description (e.g. "Mainland Lite cheddar 250 g block") and click **Send**. In NIP mode Claude follows FSANZ Standard 1.2.8 / Schedules 11-12 rounding and returns per-100 g (solid) or per-100 mL (liquid) values. AI-sourced rows are tagged in the FoodDescription: `(AI) #` (solid), `(AI) mL#` (liquid), or ` (AI) {recipe=Xg}` (recipe). The `(AI)` substring makes AI-sourced rows easy to filter on in the Foods Table. Claude is also instructed to format the descriptive part as `Category1, Category2, Category3, OtherDescription - Brand, Company` (e.g. "Cheese, cheddar, lite, block - Mainland") for consistent sorting and searching — you can hand-edit this in the Json screen before Confirm if you want a different shape.
- **Attaching images:** Click **+** at the left of the input to attach photos of food labels or on-pack NIPs. The file picker is multi-select. Large photos are downsized before sending.
- **Web search** (toggle in settings): Claude looks up the manufacturer or retailer's official product page first and copies on-pack values verbatim. AFCD/NUTTAB and the `lookup_food` tool are used as fallbacks. ~\$0.01 per search, capped at 5 per turn.
- **Extended thinking** (toggle in settings): gives Claude an adaptive thinking budget for harder reasoning tasks. Effective on Opus 4.7 / Sonnet 4.6 — the toggle is automatically disabled when Haiku 4.5 is selected, since it doesn't support thinking.
- **Live tool-call indicator:** while a query is processing, the loading row stacks lines like "Looking up '<query>' in the Foods table..." (each `lookup_food` call) and "Searched the web: '<query>'" (each web search) so you can see what Claude is doing.
- **Markdown rendering** in chat: assistant replies are rendered through the same markdown pipeline as the in-app help — so headers, bullets, bold text, and `code blocks` look the way Claude intended. The **Copy** button still copies the raw markdown source, which is convenient if you want to paste it elsewhere.
- **Cost transparency:** the small status row at the top of this screen shows the cumulative session cost (e.g. "Session cost: \$0.0143 (3 turns)"). Per-call cost is also appended to each reply's JSON `notes` field, so it rides through to the Foods table when you Confirm.
- **Cross-feature** — the **Eaten Table** screen reuses your API key + model (set here) for an *Explain this day (AI)* flow on daily totals. That flow is a single-shot single-message call (no tools, no thinking, no web search) driven by the bundled `EXPLAINsysprompt.txt` system prompt; the Web search / Extended thinking / NIP-mode toggles above don't apply to it. See the Eaten Table's `?` help for details.
- The chat is in-memory only — leaving this screen clears it.
)MD";
    return s;
}

const std::string& utilitiesHelpText() {
    static const std::string s = R"MD(# **Utilities**
This screen contains various miscellaneous utilities .

- **Export db**: Writes/overwrites the `foods.db` file in the folder you choose. On first use you'll be asked to pick a folder; that choice is remembered.
    - The dialog shows the target path and includes a **Change folder** button to relink if you want a new location.
- **Import db**: Replaces the app database with `foods.db` from the currently selected folder.
    - The dialog shows the source path and a **Change folder** button to pick a new location. If `foods.db` is missing there, you'll be prompted to place it first.
- **Export csv**: Writes/overwrites `EatenDailyAll.csv` in the selected folder (same remembered folder as above).
    - It exports the Eaten table daily totals shown in the scrollable table viewer of the Eaten Foods screen, with the All option selected and across all dates.
    - It is in csv format with each date per row. Columns match the scrollable table viewer on the Eaten Table screen and include `My weight (kg)` and `Comments` as the second and third columns.
    - The dialog shows the target path and includes a **Change folder** button to relink when needed.
- **Db last exported / imported**: the two small lines under the buttons record when *this machine* last wrote `foods.db` to the exchange folder and last replaced its database from there — a staleness hint for the pass-the-baton workflow (log on one device at a time: export before switching away, import before logging on the next device). The Android app shows matching `Db last shared/overwritten` / `Db last imported` lines.
- **Eaten Graph**: opens a separate screen that visualises a chosen metric (My weight, Amount, Energy, or any of 22 nutrients) per day from the Eaten Table over a chosen date range. Use the metric dropdown to pick a metric, then the date-range chips (1W / 1M / 3M / 1Y / All / Custom) to scope the view. See the `?` help on that screen for full details.
- **Weight Table**: a scrollable table viewer which displays records from the weight table.
    - Records are displayed in descending date order.
    - When any record is selected (by clicking it) a selection panel appears at the bottom of the screen. It displays details of the selected record followed by three buttons below it:
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
        - You can abort these processes (from the above dialogs) by clicking anywhere outside the dialog box. This closes the dialog and transfers focus back to this screen. The selection panel is also closed.
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
)MD";
    return s;
}

const std::string& insertHelpText() {
    static const std::string s = R"MD(# **Add Food**
- When the Add button is clicked from the Foods Table screen this screen is displayed.
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
)MD";
    return s;
}

std::string editHelpText(bool isLiquidFood) {
    if (isLiquidFood) {
        return R"MD(# **Editing Liquid Food**
- These are foods for which the Energy and nutrient values are given on a **per 100mL basis**
- On first displaying this screen all the input fields will be populated with values from the selected Food, however the Description field will have the " mL" or " mL#" markers omitted. These will be reinstated after edit confirmation. This means you cannot change a Liquid food into a Solid one directly through editing. Use the Convert button on the Foods Table to create a new solid (annotated with a density marker) and edit that instead.
- Modify fields as required using decimals where needed and click Confirm to save your changes. If a field entry is not valid (eg. text, blank or not a number in a field requiring numbers) the Confirm button will be disabled.
- Notes is an optional free text (multi-line) field. It is saved with the food and shown in the Foods table when All is selected.
- You can press either of two "back" buttons to cancel the editing process and return focus to the Foods Table screen.
- If confirmation succeeds the selected food is amended and focus passes to the Foods Table screen with the filter text being set to the just edited foods description (with markers reinstated). This allows you to review the results of the edit and is especially important if the description has changed significantly and you would not have been able find the food again.
)MD";
    }
    return R"MD(# **Editing Solid Food**
- These are foods for which the Energy and nutrient values are given on a **per 100g basis**
- On first displaying this screen all the input fields will be populated with values from the selected Food, however the Description field will have the " #" marker (if any) omitted. This will be reinstated after edit confirmation. This screen preserves the solid suffix, so create a liquid food using Add or Copy if needed.
- Modify fields as required using decimals where needed and click Confirm to save your changes. If a field entry is not valid (eg. text, blank or not a number in a field requiring numbers) the Confirm button will be disabled.
- Notes is an optional free text (multi-line) field. It is saved with the food and shown in the Foods table when All is selected.
- You can press either of two "back" buttons to cancel the editing process and return focus to the Foods Table screen.
- If confirmation succeeds the selected food is amended and focus passes to the Foods Table screen with the filter text being set to the just edited foods description (with any markers reinstated). This allows you to review the results of the edit and is especially important if the description has changed significantly and you would not have been able find the food again.
)MD";
}

std::string copyHelpText(bool isLiquidFood) {
    if (isLiquidFood) {
        return R"MD(# **Copying Liquid Food**
- When the Copy button is clicked from the Foods Table screen with a **liquid food selected**, this screen headed Copying Liquid Food is displayed
- Its layout and presentation is identical to the Editing Liquid Food screen, the difference being that instead of modifying the selected food record, a new one is created with the displayed field values.
- Clearly before pressing the Confirm button you can modify any of the field entries, so you are not really creating an exact copy just a food based on the selection.
- Notes is pre-filled from the selected food, can be edited (multi-line), and is saved with the new record.
- Focus will then pass to the Foods Table screen with the filter text being set to the just created foods description (with the liquid marker appended). This allows you to review the results of the foods creation and is especially important if the Description is unintuitive and finding the food in the table might be difficult.
- As before you can press either of the two "back" buttons to cancel the copying process and return focus to the Foods Table screen.
)MD";
    }
    return R"MD(# **Copying Solid Food**
- When the Copy button is clicked from the Foods Table screen with a **solid food selected**, this screen headed Copying Solid Food is displayed
- Its layout and presentation is identical to the Editing Solid Food screen, the difference being that instead of modifying the selected food record, a new one is created with the displayed field values.
- Clearly before pressing the Confirm button you can modify any of the field entries, so you are not really creating an exact copy just a food based on the selection.
- Notes is pre-filled from the selected food, can be edited (multi-line), and is saved with the new record.
- Focus will then pass to the Foods Table screen with the filter text being set to the just created foods description. This allows you to review the results of the foods creation and is especially important if the Description is unintuitive and finding the food in the table might be difficult.
- As before you can press either of the two "back" buttons to cancel the copying process and return focus to the Foods Table screen.
)MD";
}

std::string buildRecipeHelpText(const std::string& screenTitle) {
    std::string modeIntro, descriptionHint, confirmHint, notesHint;
    if (screenTitle == "Editing Recipe") {
        modeIntro = "This screen lets you edit the recipe food you selected.";
        descriptionHint = "It starts with the selected recipe's description (without the recipe marker).";
        confirmHint = "When pressed the recipe is updated and focus shifts to the Foods Table screen.";
        notesHint = "It starts with the selected recipe's notes.";
    } else if (screenTitle == "Copying Recipe") {
        modeIntro = "This screen lets you create and modify a new recipe food by copying the one you selected.";
        descriptionHint = "It starts with the selected recipe's description (without the recipe marker).";
        confirmHint = "When pressed a new recipe is created and focus shifts to the Foods Table screen.";
        notesHint = "It starts with the selected recipe's notes.";
    } else {
        modeIntro = "This screen lets you create a new recipe food.";
        descriptionHint = "It starts empty.";
        confirmHint = "When pressed the recipe is created and focus shifts to the Foods Table screen.";
        notesHint = "It starts empty.";
    }
    return "# **" + screenTitle + "**\n" + modeIntro + R"MD(

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
Folate                  REAL    ug
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
    - )MD" + descriptionHint + R"MD(
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
    - When a particular food is selected (by clicking it) a dialog box appears where you can specify the amount in grams of the food that this recipe requires.
    - Press the **Confirm** button when you are ready to accept this recipe ingredient. This transfers focus back to this screen where the added ingredient will appear in the lower scrollable table viewer.
    - You can abort this process by clicking anywhere outside the dialog box. This closes it and focus returns to this screen.
    - If you select a liquid food a dialog with the title "CANNOT ADD THIS FOOD" will appear. Press the OK button or click anywhere outside the dialog box to close it. Nothing happens and focus returns to this screen.
- A **text label** which is of the form "Ingredients[weight] (g) Total"
    - The [weight] is the total current amount of ingredients in this recipe in grams.
    - It is automatically updated whenever the ingredients are added, edited or deleted.
- A **scrollable table viewer** which displays this recipes ingredient records from the Recipe table.
    - When an ingredient is selected (by clicking it) a selection panel appears near the bottom of the screen. It displays the description of the selected food followed by two buttons below it:
        - **Edit**: It enables the amount of the ingredient to be modified.
            - It opens a dialog box where you can modify the amount of the ingredient.
            - Press the **Confirm** button when you are ready to confirm your changes. This then transfers focus back to this screen where the just modified ingredient will be visible. The Total Ingredients amount will also be adjusted.
            - You can abort this process by clicking anywhere outside the dialog box. This closes it and transfers focus back to this screen. The selection panel is also closed.
        - **Delete**: deletes the selected ingredient from the Recipe table.
            - There is no warning dialog, it just irrevocably deletes the ingredient.
- Two buttons labeled **Set notes** and **Edit notes** beside the Confirm button.
    - **Set notes** fills the recipes notes field with the current ingredient list (replacing if one exists), one line per ingredient in the format "[amount] [description]".
    - **Edit notes** opens a dialog where you can directly edit (or just examine) the notes text field.
    - Notes are saved with the recipe food and shown in the Foods table when All is selected.
    - )MD" + notesHint + R"MD(
- A **Confirm** button.
    - )MD" + confirmHint + R"MD(
    - If the Description is blank or there are no ingredients in the recipe an informative Toast appears and nothing changes.
    - If you want to abort any actions on this screen you can press either of the two "back" buttons which sets focus back to the Foods Table screen.
)MD";
}
