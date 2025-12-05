# DietSentry Food Database Viewer

DietSentry is a Kotlin/Jetpack Compose Android app for browsing foods and logging what you eat. It ships with a bundled SQLite database (`foods.db`) and lets you search, view nutrition, and record eaten amounts with timestamps.

## App overview

- Two-screen flow: Food search list (`foodSearch`) and eaten log (`eatenLog`).
- Scrollable food catalog with search/filter and an optional nutrition-details toggle.
- Bottom selection panel to select a food; actions include logging an eaten amount (with date/time pickers) or deleting the food with confirmation.
- Eaten log screen to review, edit, and delete previously recorded entries.
- Compose-only UI following modern Android patterns.

## Quick start

### Prerequisites

- Android Studio (with Android SDK)
- Device or emulator running a recent Android version
- JDK available on your `PATH` or `JAVA_HOME` set (required for Gradle)

### Build and run

1. Clone the repo and open it in Android Studio.
2. Build a debug APK: `./gradlew assembleDebug`.
3. Install/run on a device or emulator from Android Studio. On first launch the bundled `foods.db` in `app/src/main/assets` is copied into app storage automatically.

### Running tests

- Unit tests: `./gradlew test`

## Project structure

- `app/` – main Android module.
- `app/src/main/java/au/dietsentry/myapplication/` – Kotlin sources.
- `app/src/main/assets/foods.db` – bundled SQLite database copied on first run.
- `DietSentry3.png` – sample screenshot.

## Database

The app uses SQLite (`foods.db`) and does not alter the schema at runtime. Key tables:

### Foods

- `FoodId` (Integer, Primary Key)
- `FoodDescription` (Text)
- `Energy`, `Protein`, `FatTotal`, `SaturatedFat`, `TransFat`, `PolyunsaturatedFat`, `MonounsaturatedFat`, `Carbohydrate`, `Sugars`, `DietaryFibre`, `SodiumNa`, `CalciumCa`, `PotassiumK`, `ThiaminB1`, `RiboflavinB2`, `NiacinB3`, `Folate`, `IronFe`, `MagnesiumMg`, `VitaminC`, `Caffeine`, `Cholesterol`, `Alcohol` (Real)

### Eaten

- `EatenId` (Integer, Primary Key)
- `DateEaten` (Text), `TimeEaten` (Text), `EatenTs` (Integer)
- `AmountEaten` (Real), `FoodDescription` (Text)
- Nutrition snapshot columns: same set as `Foods` (`Energy` through `Alcohol`)

## Usage tips

- Toggle “Show Nutrition” to expand or collapse per-food nutrient rows in the list.
- Tap a food to open the bottom action panel; choose “Select” to log an eaten amount or “Delete” to remove the food (with confirmation).
- In the eaten log, use edit/delete actions to adjust past entries; nutrient values reflect the snapshot stored when the entry was created.

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change. Please avoid schema changes unless explicitly planned with a migration.
