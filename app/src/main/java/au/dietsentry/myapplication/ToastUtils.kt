package au.dietsentry.myapplication

import android.content.Context
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.widget.TextView
import android.widget.Toast

fun showPlainToast(context: Context, message: String) {
    val density = context.resources.displayMetrics.density
    val horizontal = (16 * density).toInt()
    val vertical = (10 * density).toInt()
    val themedContext = ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_Light)
    val textView = TextView(themedContext).apply {
        text = message
        setPadding(horizontal, vertical, horizontal, vertical)
        setTextColor(Color.WHITE)
        setBackgroundResource(android.R.drawable.toast_frame)
    }
    Toast(context).apply {
        duration = Toast.LENGTH_SHORT
        view = textView
    }.show()
}
