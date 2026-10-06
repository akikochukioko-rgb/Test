package eu.akaiko.rplayer

import android.os.Environment
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Appends crash / error reports to `/storage/emulated/0/rplayer/log.txt`.
 * Requires all-files access (already requested by the app).
 */
object CrashLog {
    private const val TAG = "CrashLog"
    private const val DIR_NAME = "rplayer"
    private const val FILE_NAME = "log.txt"
    private const val MAX_BYTES = 512 * 1024 // keep last ~512 KB

    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /** Absolute path of the log file (may not exist yet). */
    val logFile: File
        get() = File(File(Environment.getExternalStorageDirectory(), DIR_NAME), FILE_NAME)

    fun installDefaultHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            append("UNCAUGHT", "Thread: ${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
                ?: run {
                    // If there was no previous handler, kill the process like the system would.
                    android.os.Process.killProcess(android.os.Process.myPid())
                    System.exit(10)
                }
        }
    }

    fun append(kind: String, message: String? = null, error: Throwable? = null) {
        try {
            val dir = logFile.parentFile ?: return
            if (!dir.exists() && !dir.mkdirs()) {
                Log.w(TAG, "Cannot create log dir: ${dir.absolutePath}")
                return
            }
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
            logFile.appendText(body, Charsets.UTF_8)
            trimIfNeeded()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write crash log", e)
        }
    }

    private fun trimIfNeeded() {
        try {
            if (!logFile.exists() || logFile.length() <= MAX_BYTES) return
            val text = logFile.readText(Charsets.UTF_8)
            val cut = text.length - MAX_BYTES / 2
            if (cut <= 0) return
            val idx = text.indexOf("\n==== ", cut).takeIf { it >= 0 } ?: cut
            logFile.writeText(text.substring(idx), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }
}
