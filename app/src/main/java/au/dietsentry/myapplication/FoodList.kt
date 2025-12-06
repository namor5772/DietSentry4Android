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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
                    Row(modifier = Modifier.fillMaxWidth(0.5f), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Energy (kJ):")
                        Text(text = "%.0f".format(food.energy))
                    }
                    Row(modifier = Modifier.fillMaxWidth(0.5f), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Protein (g):")
                        Text(text = "%.1f".format(food.protein))
                    }
                    Row(modifier = Modifier.fillMaxWidth(0.5f), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Fat, Total (g):")
                        Text(text = "%.1f".format(food.fatTotal))
                    }
                    Row(modifier = Modifier.fillMaxWidth(0.5f), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "- Saturated (g):")
                        Text(text = "%.1f".format(food.saturatedFat))
                    }
                    if (showExtraNutrients) {
                        Row(modifier = Modifier.fillMaxWidth(0.5f), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "- Trans (mg):")
                            Text(text = "%.1f".format(food.transFat))
                        }
                        Row(modifier = Modifier.fillMaxWidth(0.5f), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "- Polyunsaturated (g):")
                            Text(text = "%.1f".format(food.polyunsaturatedFat))
                        }
                        Row(modifier = Modifier.fillMaxWidth(0.5f), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "- Monounsaturated (g):")
                            Text(text = "%.1f".format(food.monounsaturatedFat))
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(0.5f), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Carbohydrate (g):")
                        Text(text = "%.1f".format(food.carbohydrate))
                    }
                    Row(modifier = Modifier.fillMaxWidth(0.5f), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "- Sugars (g):")
                        Text(text = "%.1f".format(food.sugars))
                    }
                    if (showExtraNutrients) {
                        Row(modifier = Modifier.fillMaxWidth(0.5f), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Dietary Fibre (g):")
                            Text(text = "%.1f".format(food.dietaryFibre))
                        }
                        Row(modifier = Modifier.fillMaxWidth(0.5f), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Sodium (mg):")
                            Text(text = "%.1f".format(food.sodium))
                        }
                        Row(modifier = Modifier.fillMaxWidth(0.5f), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Calcium (mg):")
                            Text(text = "%.1f".format(food.calciumCa))
                        }
                        Row(modifier = Modifier.fillMaxWidth(0.5f), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Potassium (mg):")
                            Text(text = "%.1f".format(food.potassiumK))
                        }
                        Row(modifier = Modifier.fillMaxWidth(0.5f), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Thiamin B1 (mg):")
                            Text(text = "%.1f".format(food.thiaminB1))
                        }
                        Row(modifier = Modifier.fillMaxWidth(0.5f), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Riboflavin B2 (mg):")
                            Text(text = "%.1f".format(food.riboflavinB2))
                        }
                        Row(modifier = Modifier.fillMaxWidth(0.5f), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Niacin B3 (mg):")
                            Text(text = "%.1f".format(food.niacinB3))
                        }
                        Row(modifier = Modifier.fillMaxWidth(0.5f), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Folate (ug):")
                            Text(text = "%.1f".format(food.folate))
                        }
                        Row(modifier = Modifier.fillMaxWidth(0.5f), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Iron (mg):")
                            Text(text = "%.1f".format(food.ironFe))
                        }
                        Row(modifier = Modifier.fillMaxWidth(0.5f), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Magnesium (mg):")
                            Text(text = "%.1f".format(food.magnesiumMg))
                        }
                        Row(modifier = Modifier.fillMaxWidth(0.5f), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Vitamin C (mg):")
                            Text(text = "%.1f".format(food.vitaminC))
                        }
                        Row(modifier = Modifier.fillMaxWidth(0.5f), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Caffeine (mg):")
                            Text(text = "%.1f".format(food.caffeine))
                        }
                        Row(modifier = Modifier.fillMaxWidth(0.5f), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Cholesterol (mg):")
                            Text(text = "%.1f".format(food.cholesterol))
                        }
                        Row(modifier = Modifier.fillMaxWidth(0.5f), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Alcohol (g):")
                            Text(text = "%.1f".format(food.alcohol))
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(0.5f), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Sodium (mg):")
                            Text(text = "%.1f".format(food.sodium))
                        }
                        Row(modifier = Modifier.fillMaxWidth(0.5f), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Dietary Fibre (g):")
                            Text(text = "%.1f".format(food.dietaryFibre))
                        }
                    }
                }
            }
        }
    }
}
