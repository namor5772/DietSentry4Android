package au.dietsentry.myapplication

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun FoodList(
    foods: List<Food>,
    onFoodClicked: (Food) -> Unit, // We add a function to handle clicks
    showNutritionalInfo: Boolean, // New parameter to control visibility
    modifier: Modifier = Modifier,
    showExtraNutrients: Boolean = false
) {
    LazyColumn(
        modifier = modifier
            .padding(16.dp)
            .border(1.dp, Color.Gray)
    ) {
        items(foods) { food ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFoodClicked(food) } // Make the whole column clickable
                    .padding(4.dp)
            ) {
                Text(text = food.foodDescription, fontWeight = FontWeight.Bold)
                if (showNutritionalInfo || showExtraNutrients) {
                    NutrientRow(label = "Energy (kJ):", value = food.energy)
                    NutrientRow(label = "Protein (g):", value = food.protein)
                    NutrientRow(label = "Fat, Total (g):", value = food.fatTotal)
                    NutrientRow(label = "- Saturated (g):", value = food.saturatedFat)
                    if (showExtraNutrients) {
                        NutrientRow(label = "- Trans (mg):", value = food.transFat)
                        NutrientRow(label = "- Polyunsaturated (g):", value = food.polyunsaturatedFat)
                        NutrientRow(label = "- Monounsaturated (g):", value = food.monounsaturatedFat)
                    }
                    NutrientRow(label = "Carbohydrate (g):", value = food.carbohydrate)
                    NutrientRow(label = "- Sugars (g):", value = food.sugars)
                    if (showExtraNutrients) {
                        NutrientRow(label = "Sodium (mg):", value = food.sodium)
                        NutrientRow(label = "Dietary Fibre (g):", value = food.dietaryFibre)
                        NutrientRow(label = "Calcium (mg):", value = food.calciumCa)
                        NutrientRow(label = "Potassium (mg):", value = food.potassiumK)
                        NutrientRow(label = "Thiamin B1 (mg):", value = food.thiaminB1)
                        NutrientRow(label = "Riboflavin B2 (mg):", value = food.riboflavinB2)
                        NutrientRow(label = "Niacin B3 (mg):", value = food.niacinB3)
                        NutrientRow(label = "Folate (ug):", value = food.folate)
                        NutrientRow(label = "Iron (mg):", value = food.ironFe)
                        NutrientRow(label = "Magnesium (mg):", value = food.magnesiumMg)
                        NutrientRow(label = "Vitamin C (mg):", value = food.vitaminC)
                        NutrientRow(label = "Caffeine (mg):", value = food.caffeine)
                        NutrientRow(label = "Cholesterol (mg):", value = food.cholesterol)
                        NutrientRow(label = "Alcohol (g):", value = food.alcohol)
                    } else {
                        NutrientRow(label = "Sodium (mg):", value = food.sodium)
                    }
                }
            }
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
