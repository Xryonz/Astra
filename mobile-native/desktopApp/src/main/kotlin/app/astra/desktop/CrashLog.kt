package app.astra.desktop

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime

// Rede de seguranca do desktop (o :app Android já tinha a dele; aqui não existia).
// App de janela do jpackage NAO tem console anexado: qualquer excecao não tratada
// mata o processo em silencio — e o "o Astra fecha do nada" fica sem rastro nenhum.
// Aqui a excecao vira LINHA NUM ARQUIVO (%LOCALAPPDATA%\Astra\falhas.txt) e um
// aviso na tela, entao a proxima vez que fechar sozinho a gente sabe POR QUE.
//
// O arquivo ACUMULA (append): fechamento sozinho costuma ser intermitente, e um
// unico registro sobrescrito perderia justo o padrao que interessa.
object CrashLog {
    private const val FILE = "falhas.txt"
    // Um aviso por sessao: composicao quebrada dispara em rajada, e 40 janelinhas
    // empilhadas seriam pior que o silencio.
    @Volatile private var warned = false

    // Pasta de dados do Astra (a mesma do cache de imagens e do diagnostico).
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
            // Heap no momento da morte: separa "estourou a memoria" de "bug de codigo"
            // sem precisar de heap dump (que custaria centenas de MB em disco).
            appendLine(
                "memoria: usada ${(rt.totalMemory() - rt.freeMemory()) / 1024 / 1024}MB " +
                    "/ teto ${rt.maxMemory() / 1024 / 1024}MB",
            )
            appendLine()
            append(sw.toString())
        }
        File(dataDir(), FILE).appendText(txt)
    }

    // Sem isto o app apenas SOME. Uma janelinha do AWT (não do Compose — a essa
    // altura a UI pode estar morta) e o minimo pra pessoa saber que houve erro e
    // onde esta o registro.
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
