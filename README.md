# DietSentry Food Database Viewer

This is a simple Android application for viewing and managing nutritional information for various foods. The app reads data from a local SQLite database, allows for searching, and provides options to interact with the food items.

## Features

*   **Two-Screen Navigation**: A `foodSearch` screen for browsing and a `eatenLog` screen for tracking consumption.
*   **Food List Display**: Shows a scrollable list of food items from the database.
*   **Search/Filter**: Filter the food list by entering text in the search bar.
*   **Nutritional Information Toggle**: A persistent toggle button allows the user to show or hide detailed nutritional information for all items in the list.
*   **Selection Panel**: Tapping a food item reveals a bottom panel with options to interact with the selected food.
*   **"Eaten" Dialog**: The "Select" button opens a dialog to log the amount of food consumed, complete with native date and time pickers.
*   **Delete with Confirmation**: The "Delete" button opens a styled confirmation dialog to prevent accidental deletions. Deleting a food removes it from the database and refreshes the list.
*   **Eaten Food Log**: A dedicated screen to view, edit, and delete logged food entries.
*   **Modern UI**: Built entirely with Jetpack Compose, following modern Android development practices.

## Getting Started

### Prerequisites

*   Android Studio
*   An Android device or emulator

### Setup

1.  Clone this repository to your local machine.
2.  Open the project in Android Studio.
3.  Build and run the project. The `foods.db` database in `app/src/main/assets` will be copied for use on the first run.

## Database

The app uses a SQLite database named `foods.db`.

### Foods Table

It must contain a `Foods` table with the following columns:

*   `FoodId` (Integer, Primary Key)
*   `FoodDescription` (Text)
*   `Energy` (Real)
*   `Protein` (Real)
*   `FatTotal` (Real)
*   `SaturatedFat` (Real)
*   `TransFat` (Real)
*   `PolyunsaturatedFat` (Real)
*   `MonounsaturatedFat` (Real)
*   `Carbohydrate` (Real)
*   `Sugars` (Real)
*   `DietaryFibre` (Real)
*   `SodiumNa` (Real)
*   `CalciumCa` (Real)
*   `PotassiumK` (Real)
*   `ThiaminB1` (Real)
*   `RiboflavinB2` (Real)
*   `NiacinB3` (Real)
*   `Folate` (Real)
*   `IronFe` (Real)
*   `MagnesiumMg` (Real)
*   `VitaminC` (Real)
*   `Caffeine` (Real)
*   `Cholesterol` (Real)
*   `Alcohol` (Real)

### Eaten Table

It also must contain an `Eaten` table with the following columns:

*   `EatenId` (Integer, Primary Key)
*   `DateEaten` (Text)
*   `TimeEaten` (Text)
*   `EatenTs` (Integer)
*   `AmountEaten` (Real)
*   `FoodDescription` (Text)
*   `Energy` (Real)
*   `Protein` (Real)
*   `FatTotal` (Real)
*   `SaturatedFat` (Real)
*   `TransFat` (Real)
*   `PolyunsaturatedFat` (Real)
*   `MonounsaturatedFat` (Real)
*   `Carbohydrate` (Real)
*   `Sugars` (Real)
*   `DietaryFibre` (Real)
*   `SodiumNa` (Real)
*   `CalciumCa` (Real)
*   `PotassiumK` (Real)
*   `ThiaminB1` (Real)
*   `RiboflavinB2` (Real)
*   `NiacinB3` (Real)
*   `Folate` (Real)
*   `IronFe` (Real)
*   `MagnesiumMg` (Real)
*   `VitaminC` (Real)
*   `Caffeine` (Real)
*   `Cholesterol` (Real)
*   `Alcohol` (Real)

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.
