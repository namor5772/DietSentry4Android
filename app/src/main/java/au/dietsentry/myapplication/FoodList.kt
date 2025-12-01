package au.dietsentry.myapplication

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp

@Composable
fun FoodList(
    foods: List<Food>,
    selectedFood: Food?,
    onFoodSelected: (Food) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .border(1.dp, Color.Gray)
    ) {
        items(foods) { food ->
            val isSelected = food == selectedFood
            val backgroundColor = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                Color.Transparent
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .clickable { onFoodSelected(food) }
                    .padding(8.dp)
            ) {
                Text(text = food.foodDescription, fontWeight = FontWeight.Bold)
                Text(text = "Energy (kJ): ${"%.0f".format(food.energy)}")
                Text(text = "Protein (g): ${"%.1f".format(food.protein)}")
                Text(text = "Fat, Total (g): ${"%.1f".format(food.fatTotal)}")
                Text(text = "Saturated Fat (g): ${"%.1f".format(food.saturatedFat)}")
                Text(text = "Carbohydrate (g): ${"%.1f".format(food.carbohydrate)}")
                Text(text = "Sugars (g): ${"%.1f".format(food.sugars)}")
                Text(text = "Sodium (mg): ${"%.1f".format(food.sodium)}")
                Text(text = "Dietary Fibre (g): ${"%.1f".format(food.dietaryFibre)}")
            }
        }
    }
}
