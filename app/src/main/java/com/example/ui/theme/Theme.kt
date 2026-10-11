package com.example.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = NetraEmerald,
    onPrimary = Color.Black,
    primaryContainer = NetraSurfaceVariant,
    onPrimaryContainer = NetraCyan,
    secondary = NetraCyan,
    onSecondary = Color.Black,
    secondaryContainer = NetraSurfaceVariant,
    onSecondaryContainer = NetraCyan,
    tertiary = NetraTeal,
    background = NetraDarkBg,
    onBackground = TextPrimary,
    surface = NetraSurface,
    onSurface = TextPrimary,
    surfaceVariant = NetraSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = NetraCardBorder,
    error = StatusRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00796B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2F1),
    onPrimaryContainer = Color(0xFF004D40),
    secondary = Color(0xFF00838F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F7FA),
    onSecondaryContainer = Color(0xFF006064),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = StatusRed,
    onError = Color.White
)

/** New look (from 11 Oct 2026): dark navy surfaces, orange primary, mint accent. */
private val NewColorScheme = darkColorScheme(
    primary = Color(0xFFFF8A00),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF121C30),
    onPrimaryContainer = Color(0xFFFFB74D),
    secondary = Color(0xFF3DDC97),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF121C30),
    onSecondaryContainer = Color(0xFF3DDC97),
    tertiary = Color(0xFF3DDC97),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF121C30),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1A2742),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF2A3A5A),
    error = StatusRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            dynamicLightColorScheme(context)
        }
        RedesignGate.isOn() -> NewColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = com.example.festival.festiveScheme(colorScheme),
        typography = Typography,
        content = content
    )
}
