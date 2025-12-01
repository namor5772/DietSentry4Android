package au.dietsentry.myapplication

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
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
fun FoodList(foods: List<Food>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .padding(16.dp)
            .border(1.dp, Color.Gray)
    ) {
        items(foods) { food ->
            Column(modifier = Modifier.padding(8.dp)) {
                Text(text = food.foodDescription, fontWeight = FontWeight.Bold)
                Text(text = "Energy: ${food.energy}")
                Text(text = "Protein: ${food.protein}")
                Text(text = "Fat (Total): ${food.fatTotal}")
                Text(text = "Saturated Fat: ${food.saturatedFat}")
                Text(text = "Carbohydrate: ${food.carbohydrate}")
                Text(text = "Sugars: ${food.sugars}")
                Text(text = "Sodium: ${food.sodium}")
                Text(text = "Dietary Fibre: ${food.dietaryFibre}")
            }
        }
    }
}
