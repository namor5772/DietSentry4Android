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
fun RecipeList(
    recipes: List<RecipeItem>,
    modifier: Modifier = Modifier,
    onRecipeClicked: (RecipeItem) -> Unit = {},
    selectedRecipeId: Int? = null
) {
    LazyColumn(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .border(1.dp, Color.Gray)
    ) {
        items(recipes) { recipe ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRecipeClicked(recipe) }
                    .padding(8.dp)
            ) {
                Text(
                    text = recipe.foodDescription,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    color = if (selectedRecipeId == recipe.recipeId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                NutrientRow(label = "Amount (${recipe.unitLabel()}):", value = recipe.amount)
                NutrientRow(label = "Energy (kJ):", value = recipe.energy)
                NutrientRow(label = "Fat (g):", value = recipe.fatTotal)
                NutrientRow(label = "Fibre (g):", value = recipe.dietaryFibre)
            }
        }
    }
}

private fun RecipeItem.unitLabel(): String {
    return if (Regex("mL#?$", RegexOption.IGNORE_CASE).containsMatchIn(foodDescription)) "mL" else "g"
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
