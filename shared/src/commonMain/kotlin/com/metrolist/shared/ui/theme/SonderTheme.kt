package com.metrolist.shared.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/** Sonder's surfaces and contrast, shared by every host and Metrolist's menus. */
fun ColorScheme.sonder(dark: Boolean): ColorScheme {
    val base = if (dark) Color(0xFF0A1416) else Color(0xFFF8F9FB)
    val text = if (dark) Color.White else Color(0xFF111418)
    val sheet = if (dark) Color(0xFF13292A) else Color.White
    val accent = if (dark) Color(0xFF9CC9BA) else Color(0xFF14806D)
    return copy(
        primary = if (dark) Color.White else accent,
        onPrimary = if (dark) Color(0xFF111111) else Color.White,
        primaryContainer = if (dark) Color(0xFF1A3A3A) else Color(0xFFDDEFE9),
        onPrimaryContainer = text,
        secondary = accent,
        onSecondary = base,
        secondaryContainer = if (dark) Color(0xFF253234) else Color(0xFFE6EBED),
        onSecondaryContainer = text,
        tertiary = accent,
        onTertiary = base,
        tertiaryContainer = if (dark) Color(0xFF253234) else Color(0xFFE6EBED),
        onTertiaryContainer = text,
        primaryFixed = Color(0xFFDDEFE9),
        primaryFixedDim = Color(0xFF9CC9BA),
        onPrimaryFixed = Color(0xFF112C28),
        onPrimaryFixedVariant = Color(0xFF254C43),
        secondaryFixed = Color(0xFFDDE7E7),
        secondaryFixedDim = Color(0xFFB8C9C9),
        onSecondaryFixed = Color(0xFF172728),
        onSecondaryFixedVariant = Color(0xFF354A4B),
        tertiaryFixed = Color(0xFFDDEFE9),
        tertiaryFixedDim = Color(0xFF9CC9BA),
        onTertiaryFixed = Color(0xFF112C28),
        onTertiaryFixedVariant = Color(0xFF254C43),
        background = base,
        onBackground = text,
        surface = base,
        onSurface = text,
        surfaceDim = base,
        surfaceBright = sheet,
        surfaceContainerLowest = base,
        surfaceContainerLow = sheet,
        surfaceContainer = sheet,
        surfaceContainerHigh = if (dark) Color(0xFF1E3031) else Color(0xFFF0F3F5),
        surfaceContainerHighest = if (dark) Color(0xFF2C3D3E) else Color(0xFFE6EBED),
        surfaceVariant = if (dark) Color(0xFF1E2628) else Color(0xFFECEFF2),
        onSurfaceVariant = if (dark) Color.White.copy(alpha = 0.68f) else Color(0xFF5D636B),
        outline = if (dark) Color(0xFF687879) else Color(0xFF78848C),
        outlineVariant = text.copy(alpha = 0.08f),
        surfaceTint = Color.Transparent,
        error = if (dark) Color(0xFFFF9A8F) else Color(0xFFD13A2A),
        onError = if (dark) Color(0xFF3A1712) else Color.White,
        errorContainer = if (dark) Color(0xFF3A2524) else Color(0xFFFFE9E5),
        onErrorContainer = if (dark) Color(0xFFFFB4AB) else Color(0xFF84251B),
        inverseSurface = if (dark) Color(0xFFF8F9FB) else Color(0xFF1E2628),
        inverseOnSurface = if (dark) Color(0xFF111418) else Color.White,
        inversePrimary = if (dark) Color(0xFF14806D) else Color(0xFF9CC9BA),
    )
}
