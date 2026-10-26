package com.music.bitchord.ui.glow

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode

/**
 * The Cover Glow design tokens. Dark is the design's home; light is the
 * variant drawn in the light-theme screens. Every surface is a translucent
 * wash of the text colour so it picks up whatever glow sits behind it.
 */
@Immutable
data class GlowColors(
    val isDark: Boolean,
    val base: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    /** Chips, glass cards and round buttons. */
    val glass: Color,
    val glassStrong: Color,
    val divider: Color,
    val miniPlayer: Color,
    val sheet: Color,
    val navBar: Color,
    val danger: Color,
    /** The "primary" control: white circle with a black glyph on dark. */
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val warning: Color,
    val warningSurface: Color,
    val skeleton: Color,
)

val DarkGlowColors = GlowColors(
    isDark = true,
    base = Color(0xFF0A1416),
    text = Color.White,
    textSecondary = Color.White.copy(alpha = 0.68f),
    textTertiary = Color.White.copy(alpha = 0.45f),
    glass = Color.White.copy(alpha = 0.09f),
    glassStrong = Color.White.copy(alpha = 0.14f),
    divider = Color.White.copy(alpha = 0.08f),
    miniPlayer = Color(0xFF1A3A3A),
    sheet = Color(0xFF13292A),
    navBar = Color(0xFF1E2628),
    danger = Color(0xFFFF9A8F),
    primary = Color.White,
    onPrimary = Color(0xFF111111),
    accent = Color(0xFF9CC9BA),
    warning = Color(0xFFF2C76B),
    warningSurface = Color(0xFF3B3015),
    skeleton = Color.White.copy(alpha = 0.07f),
)

/**
 * Light is drawn for light, not as dark turned over: a bright, faintly cool page, solid grey
 * text instead of washes of black, white sheets and bars, and one colour (Sonder's teal) for
 * the controls that act, where dark uses white. Glass stays a translucent wash so the cover
 * glow still tints the cards.
 */
val LightGlowColors = GlowColors(
    isDark = false,
    base = Color(0xFFF8F9FB),
    text = Color(0xFF111418),
    textSecondary = Color(0xFF5D636B),
    textTertiary = Color(0xFF9AA0A8),
    glass = Color(0xFF14243A).copy(alpha = 0.055f),
    glassStrong = Color(0xFF14243A).copy(alpha = 0.095f),
    divider = Color(0xFF14243A).copy(alpha = 0.08f),
    miniPlayer = Color(0xFFFFFFFF),
    sheet = Color(0xFFFFFFFF),
    navBar = Color(0xFFFFFFFF),
    danger = Color(0xFFD13A2A),
    primary = Color(0xFF14806D),
    onPrimary = Color.White,
    accent = Color(0xFF14806D),
    warning = Color(0xFF7A5A10),
    warningSurface = Color(0xFFF8EDCF),
    skeleton = Color(0xFF14243A).copy(alpha = 0.06f),
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Archivo, condensed, black: the artist page's name (OFL, see ARCHIVO-LICENSE.txt). */
val ArchivoCondensed = FontFamily(Font(R.font.archivo_condensed_black, FontWeight.Black))

/** Type scale lifted from the design's CSS (sizes are its px). */
object GlowType {
    val display = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.02).em)
    val hero = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 40.sp, letterSpacing = (-0.03).em, lineHeight = 44.sp)
    val title = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.01).em)
    val titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    val playerTitle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, letterSpacing = (-0.01).em)
    val body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    val bodyRegular = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp)
    val secondary = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp)
    val label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    val caption = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 11.sp)
    val section = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.01.em)
    val lyric = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.01).em)
}

val LocalGlowColors = staticCompositionLocalOf { DarkGlowColors }

object Glow {
    val colors: GlowColors
        @Composable get() = LocalGlowColors.current
}

@Composable
fun GlowTheme(content: @Composable () -> Unit) {
    val mode by AppSettings.themeMode.collectAsStateWithLifecycle()
    val pureBlack by GlowPrefs.pureBlack.collectAsStateWithLifecycle()
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = when {
        !dark -> LightGlowColors
        pureBlack -> DarkGlowColors.copy(base = Color.Black, sheet = Color(0xFF101A1B), navBar = Color(0xFF151B1C))
        else -> DarkGlowColors
    }
    val scheme = if (dark) {
        darkColorScheme(
            background = colors.base, surface = colors.base, onBackground = colors.text, onSurface = colors.text,
            primary = colors.primary, onPrimary = colors.onPrimary, surfaceVariant = colors.glass,
            onSurfaceVariant = colors.textSecondary, error = colors.danger,
        )
    } else {
        lightColorScheme(
            background = colors.base, surface = colors.base, onBackground = colors.text, onSurface = colors.text,
            primary = colors.primary, onPrimary = colors.onPrimary, surfaceVariant = colors.glass,
            onSurfaceVariant = colors.textSecondary, error = colors.danger,
        )
    }
    // The bars' icons follow the app's theme, not the phone's: light chosen here on a phone set
    // to dark used to leave white clock and battery icons on a white page.
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalGlowColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
