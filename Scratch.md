DietSentry4Android
***
# Foods Table
This is the main screen of the app.

It purpose is to display a list of foods in the database, and allow interaction with a selected food.
***
The GUI elements on the screen are (starting at the top left hand corner and working across and down):   
- The heading/name of the screen: "Foods Table". 
- A `segmented button` with three options (Min, NIP, All). The selection is persistent between app restarts. 
  - Min: only displays the text description for food items.
  - NIP: in addition displays the minimum mandated nutrient information (per 100g or 100mL of the food) as required in Australia on their Nutritional Information Panels (NIP)
  - All: Displays all nutrient fields stored in the Foods table (23 which includes Energy)
- The help button ? which displays this help screen.
- The navigation button -> which transfers you to the Eaten Table screen.
- A Text field which when empty/reset displays the text "Enter food filter text"
  - Type any text in the field (and press the Enter key or equivalent) to filter the list of foods visible below by their Description.
  - It is NOT case sensitive
- A scrollable table viewer which display records from the Foods table.
  - When a particular food is selected (by tapping it) a selection panel appears at the bottom of the screen. It displays the description of the selected food and four buttons:
    - Eaten: which logs the selected food to the Eaten Table.
    - Edit: which allows you to edit the selected food.
    - Insert: which allows you to insert a new food.
    - Delete: which deletes the selected food.
