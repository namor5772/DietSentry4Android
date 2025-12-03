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
    modifier: Modifier = Modifier
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
                if (showNutritionalInfo) {
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
                    Row(modifier = Modifier.fillMaxWidth(0.5f), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Carbohydrate (g):")
                        Text(text = "%.1f".format(food.carbohydrate))
                    }
                    Row(modifier = Modifier.fillMaxWidth(0.5f), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "- Sugars (g):")
                        Text(text = "%.1f".format(food.sugars))
                    }
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
