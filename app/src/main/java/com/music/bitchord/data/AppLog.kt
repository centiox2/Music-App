package com.music.bitchord.data

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.core.content.FileProvider
import com.music.bitchord.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The app's own log, kept on the phone so a problem can be looked at afterwards.
 *
 * - While the app runs, everything it logs (the UI, playback, the network) is written to
 *   rotating files by Android's own `logcat`, limited to this app's process, which needs no
 *   permission. Each launch keeps the previous session's log, so when the app closes
 *   unexpectedly the lines leading up to it are still there.
 * - A crash also writes a report: the error, where it happened, and the last lines of the log.
 *
 * Settings → Logs shows all of it, and shares it as a file.
 */
object AppLog {
    private const val TAG = "Sonder"
    private const val PREFS = "app_log"
    private const val KEY_PENDING_CRASH = "pending_crash"
    private const val MAX_CRASHES = 10

    /** A log file to read: its title for the list, and the file. */
    class Entry(val title: String, val file: File)

    private fun dir(context: Context) = File(context.filesDir, "logs").apply { mkdirs() }
    private fun sessionFile(context: Context) = File(dir(context), "session.log")
    private fun previousFile(context: Context) = File(dir(context), "previous-session.log")
    private fun olderFile(context: Context) = File(dir(context), "older-session.log")
    private fun crashDir(context: Context) = File(dir(context), "crashes").apply { mkdirs() }

    private var recorder: java.lang.Process? = null

    /** Called once, first thing, when the app process starts. */
    fun install(context: Context) {
        val app = context.applicationContext
        keepPreviousSession(app)
        // Settings → Performance → Diagnostic log. Crash reports are written either way.
        if (PerformanceProfile.storedDiagnosticLog(app)) startRecording(app)
        if (PerformanceProfile.storedDiagnosticLog(app)) MainThreadWatchdog.start()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { writeCrash(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
        Log.i(
            TAG,
            "Started ${BuildConfig.VERSION_NAME} on ${Build.MANUFACTURER} ${Build.MODEL}, " +
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        )
        logMemory(app)
        // Foreground and background, so a log shows when the screen was in use. Two lines per
        // switch, nothing in between.
        runCatching {
            androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(
                androidx.lifecycle.LifecycleEventObserver { _, event ->
                    when (event) {
                        androidx.lifecycle.Lifecycle.Event.ON_START -> state("app in foreground")
                        androidx.lifecycle.Lifecycle.Event.ON_STOP -> state("app in background")
                        else -> Unit
                    }
                },
            )
        }
    }

    /** Memory the system gives the app, once at start: low-memory phones explain a lot. */
    private fun logMemory(context: Context) {
        runCatching {
            val am = context.getSystemService(android.app.ActivityManager::class.java)
            val info = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            state(
                "memory class ${am.memoryClass}MB (large ${am.largeMemoryClass}MB), " +
                    "device ${info.totalMem / (1024 * 1024)}MB, ${info.availMem / (1024 * 1024)}MB free, " +
                    "low=${info.lowMemory}, cores=${Runtime.getRuntime().availableProcessors()}",
            )
        }
    }

    /** One line in the log for something the listener did. */
    fun event(what: String) {
        Log.i(TAG, what)
    }

    /**
     * One line for the state of the app itself (network, foreground, settings), so a log
     * explains the conditions behind what follows it. Called on changes only, never per frame.
     */
    fun state(what: String) {
        Log.i(TAG, "state: $what")
    }

    // Three sessions are kept: the session before last becomes "older", the last one becomes
    // "previous", so this run starts a log of its own.
    private fun keepPreviousSession(context: Context) {
        val logs = dir(context)
        logs.listFiles { f -> f.name.startsWith("older-session.log") }?.forEach { it.delete() }
        logs.listFiles { f -> f.name.startsWith("previous-session.log") }?.forEach { f ->
            f.renameTo(File(logs, f.name.replaceFirst("previous-session.log", "older-session.log")))
        }
        logs.listFiles { f -> f.name.startsWith("session.log") }?.forEach { f ->
            f.renameTo(File(logs, f.name.replaceFirst("session.log", "previous-session.log")))
        }
    }

    private fun startRecording(context: Context) {
        recorder = runCatching {
            ProcessBuilder(
                "logcat",
                "-v", "threadtime",
                "--pid=${Process.myPid()}",
                // Rotating files, 1MB each, the current one and three before it.
                "-f", sessionFile(context).absolutePath,
                "-r", "1024",
                "-n", "3",
            ).redirectErrorStream(true).start()
        }.getOrNull()
    }

    private fun writeCrash(context: Context, thread: Thread, error: Throwable) {
        val time = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(crashDir(context), "crash-$time.txt")
        val text = buildString {
            appendLine("Sonder ${BuildConfig.VERSION_NAME} closed unexpectedly")
            appendLine("When: $time")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Thread: ${thread.name}")
            appendLine()
            appendLine(Log.getStackTraceString(error))
            appendLine("---- Last lines of the log ----")
            append(readLogcat(lines = 800))
        }
        file.writeText(text)
        // Committed now, not applied: the process is about to end.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PENDING_CRASH, file.name).commit()
        crashDir(context).listFiles()?.sortedByDescending { it.name }?.drop(MAX_CRASHES)?.forEach { it.delete() }
    }

    /** The app's latest log lines, straight from Android. */
    private fun readLogcat(lines: Int): String = runCatching {
        val process = ProcessBuilder(
            "logcat", "-d", "-v", "threadtime", "--pid=${Process.myPid()}", "-t", lines.toString(),
        ).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().use { it.readText() }
    }.getOrDefault("")

    /**
     * The crash report written since the app last asked, if the app closed unexpectedly.
     * Asking clears it, so it is mentioned once.
     */
    fun takeUnseenCrash(context: Context): Entry? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_PENDING_CRASH, null) ?: return null
        prefs.edit().remove(KEY_PENDING_CRASH).apply()
        val file = File(crashDir(context), name).takeIf { it.exists() } ?: return null
        return Entry(crashTitle(file), file)
    }

    /** Every log there is to read, newest first: this session, the last one, then the crashes. */
    fun entries(context: Context): List<Entry> = buildList {
        add(Entry("This session", sessionFile(context)))
        if (previousFile(context).exists()) add(Entry("Last session", previousFile(context)))
        if (olderFile(context).exists()) add(Entry("Session before last", olderFile(context)))
        crashDir(context).listFiles()?.sortedByDescending { it.name }?.forEach { add(Entry(crashTitle(it), it)) }
    }

    private fun crashTitle(file: File): String {
        val stamp = file.name.removePrefix("crash-").removeSuffix(".txt")
        return "Crash · " + runCatching {
            val date = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).parse(stamp)!!
            SimpleDateFormat("d MMM, HH:mm:ss", Locale.getDefault()).format(date)
        }.getOrDefault(stamp)
    }

    /**
     * The text of [entry]. A session log is read with its rotated parts in order (oldest
     * first), so it reads as one; only the last [maxChars] are kept for showing on screen.
     */
    fun read(context: Context, entry: Entry, maxChars: Int = Int.MAX_VALUE): String {
        val parts = if (entry.file.parentFile == crashDir(context)) {
            listOf(entry.file)
        } else {
            // logcat names the rotated parts name.1 (newest) … name.3 (oldest).
            val base = entry.file
            (3 downTo 1).map { File(base.path + ".$it") } + base
        }
        val text = parts.filter { it.exists() }.joinToString("") { runCatching { it.readText() }.getOrDefault("") }
        return if (text.length > maxChars) text.takeLast(maxChars) else text
    }

    /** Shares [entry] as a text file another app can open (email, chat, files). */
    fun share(context: Context, entry: Entry) {
        val shared = File(context.cacheDir, "shared").apply { mkdirs() }
        val name = when {
            entry.file.parentFile == crashDir(context) -> entry.file.name
            entry.title == "Last session" -> "sonder-last-session.log"
            entry.title == "Session before last" -> "sonder-older-session.log"
            else -> "sonder-session.log"
        }
        val out = File(shared, name)
        out.writeText(
            "Sonder ${BuildConfig.VERSION_NAME} · ${Build.MANUFACTURER} ${Build.MODEL} · " +
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n\n" + read(context, entry),
        )
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", out)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Sonder log: ${entry.title}")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Deletes the crash reports and the earlier sessions' logs; this session's keeps going. */
    fun clear(context: Context) {
        crashDir(context).listFiles()?.forEach { it.delete() }
        dir(context).listFiles { f -> f.name.startsWith("previous-session.log") || f.name.startsWith("older-session.log") }?.forEach { it.delete() }
    }
}

/**
 * Writes what the main thread is doing whenever it is stuck for [STALL_MS] or more, so a
 * freeze in a field log ("Skipped 75 frames", "Davey!") comes with the code that caused it.
 * A background thread posts a no-op to the main thread every [TICK_MS]; if it hasn't run by
 * the next check, the main thread's stack is logged once for that stall (tag `SonderStall`).
 */
internal object MainThreadWatchdog {
    private const val TICK_MS = 250L
    private const val STALL_MS = 700L
    private const val MAX_REPORTS = 20

    @Volatile private var lastSeen = android.os.SystemClock.uptimeMillis()
    @Volatile private var started = false

    fun start() {
        if (started) return
        started = true
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val mainThread = android.os.Looper.getMainLooper().thread
        Thread({
            var reports = 0
            var reportedThisStall = false
            val ping = Runnable { lastSeen = android.os.SystemClock.uptimeMillis() }
            while (reports < MAX_REPORTS) {
                main.post(ping)
                try { Thread.sleep(TICK_MS) } catch (_: InterruptedException) { return@Thread }
                val stuck = android.os.SystemClock.uptimeMillis() - lastSeen
                if (stuck >= STALL_MS) {
                    if (!reportedThisStall) {
                        reportedThisStall = true
                        reports++
                        val stack = mainThread.stackTrace.take(40).joinToString("\n    at ")
                        Log.w("SonderStall", "main thread stuck ${stuck}ms:\n    at $stack")
                    }
                } else {
                    reportedThisStall = false
                }
            }
        }, "sonder-stall-watchdog").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }
}

