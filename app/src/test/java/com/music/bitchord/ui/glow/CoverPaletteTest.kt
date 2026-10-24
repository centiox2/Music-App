package com.music.bitchord.ui.glow

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [35])
class CoverPaletteTest {
    private fun cover(vararg bands: Pair<Int, Int>): Bitmap {
        // Horizontal bands: (colour, rows out of 100).
        val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        var y = 0f
        bands.forEach { (color, rows) ->
            canvas.drawRect(0f, y, 100f, y + rows, Paint().apply { this.color = color })
            y += rows
        }
        return bmp
    }

    @Test
    fun `a little red on black stays a little red`() {
        val p = coverPalette(cover(0xFF000000.toInt() to 90, 0xFFD01010.toInt() to 10).asImageBitmap())!!
        assertEquals(2, p.colors.size)
        assertTrue("black leads", p.dominant.red < 0.05f && p.dominant.green < 0.05f)
        assertEquals(0.9f, p.weights[0], 0.02f)
        assertEquals(0.1f, p.weights[1], 0.02f)
        val red = p.colors[1]
        assertTrue("red kept as red", red.red > 0.7f && red.green < 0.15f)
    }

    @Test
    fun `three colours keep their shares`() {
        val p = coverPalette(cover(0xFF1F3A5F.toInt() to 60, 0xFFF2A541.toInt() to 30, 0xFFFFFFFF.toInt() to 10).asImageBitmap())!!
        assertEquals(listOf(0.6f, 0.3f, 0.1f), p.weights.map { (it * 100).toInt() / 100f }.let { w -> w.map { kotlin.math.round(it * 10) / 10 } })
    }
}
