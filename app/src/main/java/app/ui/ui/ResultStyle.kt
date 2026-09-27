package app.ui.ui

import androidx.compose.ui.graphics.Color

fun resultColor(result: String): Color = when (result) {
    "Positive" -> Color(0xFFC62828)
    "Negative" -> Color(0xFF2E7D32)
    else -> Color(0xFFEF8F00)
}
