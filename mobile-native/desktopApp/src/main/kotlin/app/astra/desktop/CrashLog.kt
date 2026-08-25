package app.astra.desktop

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime

object CrashLog {
    private const val FILE = "falhas.txt"
    @Volatile private var warned = false

    fun dataDir(): File {
        val home = System.getProperty("user.home")
        val os = System.getProperty("os.name").orEmpty()
        val base = when {
            os.startsWith("Windows", true) -> System.getenv("LOCALAPPDATA") ?: "$home\\AppData\\Local"
            os.contains("Mac", true) -> "$home/Library/Caches"
            else -> System.getenv("XDG_CACHE_HOME") ?: "$home/.cache"
        }
        return File(base, "Astra").apply { runCatching { mkdirs() } }
    }

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { write(thread.name, e) }
            runCatching { warn(e) }
            previous?.uncaughtException(thread, e)
        }
    }

    private fun write(thread: String, e: Throwable) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val rt = Runtime.getRuntime()
        val txt = buildString {
            appendLine("──────────────────────────────────────────")
            appendLine("quando : ${LocalDateTime.now()}")
            appendLine("versao : ${System.getProperty("astra.version") ?: "dev"}")
            appendLine("thread : $thread")
            appendLine(
                "memoria: usada ${(rt.totalMemory() - rt.freeMemory()) / 1024 / 1024}MB " +
                    "/ teto ${rt.maxMemory() / 1024 / 1024}MB",
            )
            appendLine()
            append(sw.toString())
        }
        File(dataDir(), FILE).appendText(txt)
    }

    private fun warn(e: Throwable) {
        if (warned) return
        warned = true
        val motivo = if (e is OutOfMemoryError) {
            "O Astra ficou sem memória."
        } else {
            "${e::class.simpleName}: ${e.message.orEmpty().take(160)}"
        }
        javax.swing.JOptionPane.showMessageDialog(
            null,
            "O Astra fechou por um erro.\n\n$motivo\n\nO registro está em:\n${File(dataDir(), FILE).absolutePath}",
            "Astra",
            javax.swing.JOptionPane.ERROR_MESSAGE,
        )
    }
}
