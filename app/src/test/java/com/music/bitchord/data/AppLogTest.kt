package com.music.bitchord.data

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@ConscryptMode(ConscryptMode.Mode.OFF)
class AppLogTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun aCrashOnAnyThreadIsWrittenAndOfferedOnce() {
        AppLog.install(context)
        val handler = Thread.getDefaultUncaughtExceptionHandler()!!
        val thread = Thread { }
        // What the system does with an exception nothing caught: hands it to the default handler.
        runCatching { handler.uncaughtException(thread, IllegalStateException("queue index -1 out of bounds")) }

        val crash = AppLog.takeUnseenCrash(context)
        assertNotNull(crash)
        val text = crash!!.file.readText()
        assertTrue(text.contains("closed unexpectedly"))
        assertTrue(text.contains("IllegalStateException: queue index -1 out of bounds"))
        assertTrue(AppLog.entries(context).any { it.file == crash.file })
        // Mentioned once.
        assertNull(AppLog.takeUnseenCrash(context))
    }

    @Test
    fun aSessionLogReadsInOrderAcrossItsRotatedParts() {
        val logs = File(context.filesDir, "logs").apply { mkdirs() }
        // logcat's rotation: .3 oldest … .1 newest, then the file being written.
        File(logs, "session.log.2").writeText("first\n")
        File(logs, "session.log.1").writeText("second\n")
        File(logs, "session.log").writeText("third\n")
        val session = AppLog.entries(context).first { it.title == "This session" }
        assertEquals("first\nsecond\nthird\n", AppLog.read(context, session))
        // A new launch keeps it as the last session.
        AppLog.install(context)
        val last = AppLog.entries(context).first { it.title == "Last session" }
        assertEquals("first\nsecond\nthird\n", AppLog.read(context, last))
    }
}
