package app.astra.desktop

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.io.File
import kotlin.concurrent.thread
import kotlin.system.exitProcess

object Vigia {
    private const val PRAZO_MS = 12_000L

    private const val MB_YESNO = 0x00000004
    private const val MB_ICONWARNING = 0x00000030
    private const val MB_SETFOREGROUND = 0x00010000
    private const val MB_TOPMOST = 0x00040000
    private const val ID_YES = 6

    private interface U32 : StdCallLibrary {
        fun MessageBoxW(dono: Pointer?, texto: String, titulo: String, tipo: Int): Int
        companion object {
            val I: U32? = runCatching {
                Native.load("user32", U32::class.java, W32APIOptions.UNICODE_OPTIONS)
            }.getOrNull()
        }
    }

    @Volatile private var apareceu = false

    fun apareceu() { apareceu = true }

    fun vigiar(nascerEscondido: Boolean) {
        if (nascerEscondido) return
        thread(isDaemon = true, name = "astra-vigia") {
            Thread.sleep(PRAZO_MS)
            if (apareceu) return@thread
            Arranque.marcar("VIGIA: ${PRAZO_MS / 1000}s sem quadro — a janela nao apareceu")
            perguntar()
        }
    }

    private fun perguntar() = runCatching {
        val jaEstaSeguro = Arranque.arranqueAnteriorFalhou
        val texto = buildString {
            appendLine("O Astra abriu, mas a janela não apareceu.")
            appendLine()
            if (jaEstaSeguro) {
                appendLine("Ele já tentou o modo seguro e mesmo assim não desenhou.")
                appendLine("O relatório está em ${CrashLog.dataDir()}.")
                appendLine()
                append("Abrir essa pasta?")
            } else {
                appendLine("Quase sempre é a placa de vídeo. O modo seguro desenha pelo")
                appendLine("processador e abre a janela sem transparência: fica mais simples,")
                appendLine("mas aparece.")
                appendLine()
                append("Abrir em modo seguro?")
            }
        }
        val sim = avisar(texto) == ID_YES
        if (apareceu) return@runCatching
        if (sim) {
            if (jaEstaSeguro) abrirPasta() else reabrir()
        }
        exitProcess(0)
    }

    private fun avisar(texto: String): Int {
        val nativo = U32.I
        if (nativo != null) {
            return nativo.MessageBoxW(
                null, texto, "Astra",
                MB_YESNO or MB_ICONWARNING or MB_SETFOREGROUND or MB_TOPMOST,
            )
        }
        val escolha = javax.swing.JOptionPane.showConfirmDialog(
            null, texto, "Astra",
            javax.swing.JOptionPane.YES_NO_OPTION,
            javax.swing.JOptionPane.WARNING_MESSAGE,
        )
        return if (escolha == javax.swing.JOptionPane.YES_OPTION) ID_YES else 0
    }

    private fun abrirPasta() = runCatching {
        ProcessBuilder("explorer.exe", CrashLog.dataDir().absolutePath).start()
    }

    private fun reabrir() = runCatching {
        val exe = System.getProperty("jpackage.app-path") ?: return@runCatching
        SingleInstance.release()
        ProcessBuilder(exe).directory(File(exe).parentFile).start()
    }
}
