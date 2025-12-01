# DietSentry Food Database Viewer

This is a simple Android application that displays nutritional information for various foods. The app reads data from a local SQLite database and displays it in a scrollable list.

## Features

*   Displays a list of foods with detailed nutritional information.
*   Reads data from a pre-populated SQLite database (`foods.db`).
*   Built with modern Android development tools: Kotlin and Jetpack Compose.

## Getting Started

### Prerequisites

*   Android Studio
*   An Android device or emulator

### Setup

1.  Clone this repository to your local machine.
2.  Open the project in Android Studio.
3.  Obtain a copy of the `foods.db` SQLite database.
4.  Place the `foods.db` file in the `app/src/main/assets` directory.

### Building and Running

1.  In Android Studio, click the **Run** button or press `Shift` + `F10`.
2.  Select an available Android device or emulator.
3.  The app will build, install, and run on the selected device.

## Database

The app uses a SQLite database named `foods.db` to store food information. The database must contain a table named `Foods` with the following columns:

*   `FoodId` (Integer)
*   `FoodDescription` (Text)
*   `Energy` (Real)
*   `Protein` (Real)
*   `FatTotal` (Real)
*   `SaturatedFat` (Real)
*   `Carbohydrate` (Real)
*   `Sugars` (Real)
*   `Sodium` (Real)
*   `DietaryFibre` (Real)

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.
