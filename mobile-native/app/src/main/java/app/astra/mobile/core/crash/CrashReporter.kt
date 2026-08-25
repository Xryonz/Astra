package app.astra.mobile.core.crash

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

object CrashReporter {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val text = buildString {
                    append("when: ").append(Date().toString()).append('\n')
                    append("thread: ").append(thread.name).append('\n')
                    append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                        .append(" (Android ").append(Build.VERSION.RELEASE)
                        .append(" / API ").append(Build.VERSION.SDK_INT).append(")\n\n")
                    append(sw.toString())
                }
                File(appContext.filesDir, FILE).writeText(text)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun read(context: Context): String? {
        val f = File(context.applicationContext.filesDir, FILE)
        return if (f.exists()) runCatching { f.readText() }.getOrNull() else null
    }

    fun clear(context: Context) {
        runCatching { File(context.applicationContext.filesDir, FILE).delete() }
    }
}
