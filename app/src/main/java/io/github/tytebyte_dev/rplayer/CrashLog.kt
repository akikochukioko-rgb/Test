package io.github.tytebyte_dev.rplayer

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Appends crash / error reports preferably to `/storage/emulated/0/rplayer/log.txt`.
 * Falls back to app filesDir when external storage is blocked (common on MIUI without all-files access).
 */
object CrashLog {
    private const val TAG = "CrashLog"
    private const val DIR_NAME = "rplayer"
    private const val FILE_NAME = "log.txt"

    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun installDefaultHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                append("CRASH", "Uncaught on ${thread.name}", throwable)
            } catch (_: Exception) { /* never block crash path */ }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun append(tag: String, message: String, error: Throwable? = null) {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val sb = StringBuilder()
        sb.append("[").append(ts).append("] [").append(tag).append("] ").append(message).append('\n')
        if (error != null) {
            val sw = StringWriter()
            error.printStackTrace(PrintWriter(sw))
            sb.append(sw.toString()).append('\n')
        }
        sb.append("-" .repeat(40)).append('\n')
        val text = sb.toString()
        Log.e(TAG, message, error)
        writeToPreferredLocation(text)
    }

    private fun writeToPreferredLocation(text: String) {
        // 1) Prefer public external path requested by user
        try {
            val ext = Environment.getExternalStorageDirectory()
            val dir = File(ext, DIR_NAME)
            if (!dir.exists()) dir.mkdirs()
            if (dir.exists() && dir.canWrite()) {
                File(dir, FILE_NAME).appendText(text)
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "external log failed: ${e.message}")
        }
        // 2) Fallback: app-private filesDir
        try {
            val ctx = appContext ?: return
            val dir = File(ctx.filesDir, DIR_NAME).apply { mkdirs() }
            File(dir, FILE_NAME).appendText(text)
        } catch (e: Exception) {
            Log.e(TAG, "filesDir log failed: ${e.message}")
        }
    }
}
