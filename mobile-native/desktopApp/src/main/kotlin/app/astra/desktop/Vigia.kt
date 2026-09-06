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

    private const val MB_OK = 0x00000000
    private const val MB_ICONWARNING = 0x00000030
    private const val MB_SETFOREGROUND = 0x00010000
    private const val MB_TOPMOST = 0x00040000

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
            agir()
        }
    }

    private fun agir() = runCatching {
        if (Arranque.modoSeguro) {
            avisar(
                "O Astra abriu, mas a janela não apareceu — nem mesmo em modo seguro.\n\n" +
                    "O relatório está em:\n${CrashLog.dataDir()}",
            )
            abrirPasta()
            exitProcess(0)
        }
        Arranque.marcar("VIGIA: reabrindo em modo seguro")
        if (apareceu) return@runCatching
        reabrir()
        exitProcess(0)
    }

    private fun avisar(texto: String) {
        val nativo = U32.I
        if (nativo != null) {
            nativo.MessageBoxW(
                null, texto, "Astra",
                MB_OK or MB_ICONWARNING or MB_SETFOREGROUND or MB_TOPMOST,
            )
            return
        }
        javax.swing.JOptionPane.showMessageDialog(
            null, texto, "Astra", javax.swing.JOptionPane.WARNING_MESSAGE,
        )
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
