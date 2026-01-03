package au.dietsentry.myapplication

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Formats a numeric value with grouping separators and fixed decimal places.
 * Optionally trims trailing zeros when decimals are not needed for display.
 */
fun formatNumber(
    value: Double,
    decimals: Int = 1,
    trimTrailingZero: Boolean = false
): String {
    val pattern = buildString {
        append("#,##0")
        if (decimals > 0) {
            append(".")
            repeat(decimals) { append("0") }
        }
    }
    val formatter = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).apply {
        maximumFractionDigits = decimals
        minimumFractionDigits = decimals
    }
    val formatted = formatter.format(value)
    if (trimTrailingZero && decimals > 0) {
        return formatted.trimEnd('0').trimEnd('.')
    }
    return formatted
}

/**
 * Formats an amount that may be an integer while preserving grouping separators.
 */
fun formatAmount(value: Double, decimals: Int = 1): String {
    // If the value is effectively an integer, drop the decimal place.
    val isWhole = value % 1.0 == 0.0
    return if (isWhole) {
        formatNumber(value, decimals = 0)
    } else {
        formatNumber(value, decimals = decimals)
    }
}

/**
 * Formats a weight value to exactly one decimal place without grouping.
 */
fun formatWeight(value: Double): String {
    return String.format(Locale.US, "%.1f", value)
}
