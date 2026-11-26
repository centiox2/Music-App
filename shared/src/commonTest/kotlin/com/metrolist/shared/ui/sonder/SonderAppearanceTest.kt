package com.metrolist.shared.ui.sonder

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.metrolist.shared.ui.theme.sonder
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SonderAppearanceTest {
    @Test
    fun paletteKeepsArtworkProportionsInsteadOfBoostingSmallAccents() {
        val pixels = IntArray(100) { if (it < 90) Color.Black.toArgb() else Color.Red.toArgb() }
        val palette = assertNotNull(coverPalette(pixels))
        assertEquals(2, palette.colors.size)
        assertEquals(0.9f, palette.weights[0], 0.001f)
        assertEquals(0.1f, palette.weights[1], 0.001f)
        assertTrue(palette.dominant.luminance() < 0.001f)
        assertTrue(coverAccent(palette).red > 0.95f)
        assertEquals(palette, coverPalette(pixels))
    }

    @Test
    fun monochromeArtworkStaysMonochrome() {
        val palette = assertNotNull(coverPalette(IntArray(100) { Color(0xFF777777).toArgb() }))
        assertEquals(1, palette.colors.size)
        val tint = miniPlayerTint(palette, Color.Green, isDark = true)
        assertTrue(abs(tint.red - tint.green) < 0.01f)
        assertTrue(abs(tint.green - tint.blue) < 0.01f)
        assertNull(coverPalette(intArrayOf()))
    }

    @Test
    fun hslConversionRetainsHueAndLightnessOnEveryPlatform() {
        for (color in listOf(Color.Red, Color.Green, Color.Blue, Color.Black, Color.White, Color(0xFF9CC9BA))) {
            val hsl = FloatArray(3)
            SonderColorUtils.colorToHSL(color.toArgb(), hsl)
            val restored = Color(SonderColorUtils.HSLToColor(hsl))
            assertEquals(color.red, restored.red, 0.005f)
            assertEquals(color.green, restored.green, 0.005f)
            assertEquals(color.blue, restored.blue, 0.005f)
        }
    }

    @Test
    fun browsingSurfacesAndPrimaryControlsHaveReadableContrast() {
        for (dark in listOf(false, true)) {
            val scheme = (if (dark) darkColorScheme() else lightColorScheme()).sonder(dark)
            fun contrast(a: Color, b: Color): Float {
                val first = a.luminance()
                val second = b.luminance()
                return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
            }
            assertTrue(contrast(scheme.onSurface, scheme.surface) >= 4.5f)
            assertTrue(contrast(scheme.primary, scheme.onPrimary) >= 4.5f)
            assertTrue(contrast(scheme.onSurfaceVariant, scheme.surfaceContainer) >= 4.5f)
        }
    }
}
