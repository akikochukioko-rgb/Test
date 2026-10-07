package io.github.tytebyte_dev.rplayer

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Appends crash / error reports to app-specific storage (no runtime permission):
 * prefer [Context.getExternalFilesDir], fall back to [Context.getFilesDir].
 * Path example: Android/data/io.github.tytebyte_dev.rplayer/files/log.txt
 */
object CrashLog {
    private const val TAG = "CrashLog"
    private const val FILE_NAME = "log.txt"
    private const val MAX_BYTES = 512 * 1024

    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** App-specific external log file (visible via USB / Files without special grants). */
    val externalLogFile: File?
        get() {
            val ctx = appContext ?: return null
            val dir = ctx.getExternalFilesDir(null) ?: return null
            return File(dir, FILE_NAME)
        }

    private fun resolveLogFile(): File? {
        val ctx = appContext ?: return null
        // 1) App-specific external (no permission; survives uninstall with clear data policies)
        try {
            val external = ctx.getExternalFilesDir(null)
            if (external != null && (external.exists() || external.mkdirs())) {
                return File(external, FILE_NAME)
            }
        } catch (_: Throwable) {
        }
        // 2) Internal filesDir (always available)
        return try {
            File(ctx.filesDir, FILE_NAME)
        } catch (_: Throwable) {
            null
        }
    }

    fun installDefaultHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                append("UNCAUGHT", "Thread: ${thread.name}", throwable)
            } catch (_: Throwable) {
            }
            try {
                previous?.uncaughtException(thread, throwable)
                    ?: run {
                        android.os.Process.killProcess(android.os.Process.myPid())
                        System.exit(10)
                    }
            } catch (_: Throwable) {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    fun append(kind: String, message: String? = null, error: Throwable? = null) {
        try {
            val file = resolveLogFile() ?: return
            val body = buildString {
                append("==== ").append(kind).append(' ')
                append(timeFmt.format(Date())).append(" ====\n")
                if (!message.isNullOrBlank()) append(message).append('\n')
                if (error != null) {
                    val sw = StringWriter()
                    error.printStackTrace(PrintWriter(sw))
                    append(sw.toString())
                }
                append('\n')
            }
            file.appendText(body, Charsets.UTF_8)
            trimIfNeeded(file)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to write crash log", e)
        }
    }

    private fun trimIfNeeded(file: File) {
        try {
            if (!file.exists() || file.length() <= MAX_BYTES) return
            val text = file.readText(Charsets.UTF_8)
            val cut = text.length - MAX_BYTES / 2
            if (cut <= 0) return
            val idx = text.indexOf("\n==== ", cut).takeIf { it >= 0 } ?: cut
            file.writeText(text.substring(idx), Charsets.UTF_8)
        } catch (_: Throwable) {
        }
    }
}
