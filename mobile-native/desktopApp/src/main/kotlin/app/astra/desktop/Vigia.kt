package app.astra.desktop

import java.io.File
import kotlin.concurrent.thread
import kotlin.system.exitProcess

object Vigia {
    private const val PRAZO_ATE_A_JANELA_MS = 45_000L
    private const val PRAZO_ATE_O_QUADRO_MS = 8_000L
    private const val PASSO_MS = 200L

    @Volatile private var apareceu = false
    @Volatile private var janelaExiste = false

    fun apareceu() { apareceu = true }

    fun janelaCriada() { janelaExiste = true }

    fun vigiar(nascerEscondido: Boolean) {
        thread(isDaemon = true, name = "astra-vigia") {
            if (!esperar(PRAZO_ATE_A_JANELA_MS) { janelaExiste }) {
                Arranque.marcar(
                    "VIGIA: ${PRAZO_ATE_A_JANELA_MS / 1000}s e a janela nem chegou a ser criada",
                )
                agir()
                return@thread
            }
            if (nascerEscondido) return@thread
            if (!esperar(PRAZO_ATE_O_QUADRO_MS) { apareceu }) {
                Arranque.marcar(
                    "VIGIA: a janela existe ha ${PRAZO_ATE_O_QUADRO_MS / 1000}s e nenhum quadro foi desenhado",
                )
                agir()
            }
        }
    }

    private fun esperar(prazoMs: Long, pronto: () -> Boolean): Boolean {
        val fim = System.nanoTime() + prazoMs * 1_000_000
        while (System.nanoTime() < fim) {
            if (pronto()) return true
            Thread.sleep(PASSO_MS)
        }
        return pronto()
    }

    private fun agir() = runCatching {
        if (apareceu) return@runCatching
        if (Arranque.modoSeguro) {
            desistir(
                "O Astra abriu, mas a janela não apareceu — nem mesmo em modo seguro.\n\n" +
                    "O relatório está em:\n${CrashLog.dataDir()}",
            )
        }
        Arranque.marcar("VIGIA: reabrindo em modo seguro")
        Arranque.armarModoSeguro()
        if (reabrir()) exitProcess(0)
        Arranque.marcar("VIGIA: nao foi possivel reabrir sozinho")
        desistir(
            "O Astra abriu, mas a janela não apareceu, e ele não conseguiu se reabrir.\n\n" +
                "O relatório está em:\n${CrashLog.dataDir()}",
        )
    }

    private fun desistir(texto: String): Nothing {
        CaixaDeAviso.aviso(texto)
        abrirPasta()
        exitProcess(0)
    }

    private fun abrirPasta() = runCatching {
        ProcessBuilder("explorer.exe", CrashLog.dataDir().absolutePath).start()
    }

    private fun reabrir(): Boolean = runCatching {
        val exe = System.getProperty("jpackage.app-path") ?: return@runCatching false
        SingleInstance.release()
        ProcessBuilder(exe).directory(File(exe).parentFile).start()
        true
    }.getOrDefault(false)
}
