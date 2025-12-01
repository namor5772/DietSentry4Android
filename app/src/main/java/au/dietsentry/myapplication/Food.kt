package au.dietsentry.myapplication

data class Food(
    val foodId: Int,
    val foodDescription: String,
    val energy: Double,
    val protein: Double,
    val fatTotal: Double,
    val saturatedFat: Double,
    val carbohydrate: Double,
    val sugars: Double,
    val sodium: Double,
    val dietaryFibre: Double
)
