package eu.akaiko.rplayer

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
 * Falls back to app-private filesDir if external storage is not writable yet.
 */
object CrashLog {
    private const val TAG = "CrashLog"
    private const val DIR_NAME = "rplayer"
    private const val FILE_NAME = "log.txt"
    private const val MAX_BYTES = 512 * 1024

    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Preferred external path (may be unwritable without all-files access). */
    val externalLogFile: File
        get() = File(File(Environment.getExternalStorageDirectory(), DIR_NAME), FILE_NAME)

    private fun resolveLogFile(): File? {
        // 1) External public path
        try {
            val external = externalLogFile
            val dir = external.parentFile
            if (dir != null && (dir.exists() || dir.mkdirs()) && (external.canWrite() || !external.exists())) {
                return external
            }
        } catch (_: Throwable) {
        }
        // 2) App-private fallback (always available)
        return try {
            val ctx = appContext ?: return null
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
