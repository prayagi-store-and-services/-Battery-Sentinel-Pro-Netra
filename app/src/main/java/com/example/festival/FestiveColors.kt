package com.example.festival

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Navratri colours (red, orange, gold) on top of the app's own scheme, only inside the Navratri window. */
fun festiveScheme(base: ColorScheme): ColorScheme {
    if (!JayMataDi.inWindow()) return base
    return if (base.background.luminance() < 0.5f) base.copy(
        primary = Color(0xFFFFB300), onPrimary = Color.Black,
        secondary = Color(0xFFFF5252), onSecondary = Color.Black,
        tertiary = Color(0xFFFF8A00),
        primaryContainer = Color(0xFF3A1A14), onPrimaryContainer = Color(0xFFFFD27A),
        secondaryContainer = Color(0xFF3A1A14), onSecondaryContainer = Color(0xFFFFB4A9),
        outline = Color(0xFF7A2E1C)
    ) else base.copy(
        primary = Color(0xFFB71C1C), onPrimary = Color.White,
        secondary = Color(0xFFE65100), onSecondary = Color.White,
        tertiary = Color(0xFFF2C14E),
        primaryContainer = Color(0xFFFFE0B2), onPrimaryContainer = Color(0xFF7F0000),
        secondaryContainer = Color(0xFFFFE0B2), onSecondaryContainer = Color(0xFF7F2F00),
        outline = Color(0xFFD7A27A)
    )
}
