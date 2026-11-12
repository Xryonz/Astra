package app.astra.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.FrameWindowScope
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.LongByReference
import com.sun.jna.win32.StdCallLibrary
import org.jetbrains.skia.Canvas
import org.jetbrains.skiko.SkikoRenderDelegate
import java.awt.GraphicsDevice
import java.awt.Window

private const val INTERVALO_A_60_NS = 1_000_000_000L / 60
private const val FOLGA_DO_MONITOR_NS = 1_500_000L
private const val MENOR_TAXA_CONHECIDA = 2
private const val TAXA_QUE_JA_LIMITA = 61
private const val RECONFERIR_A_TAXA_NS = 2_000_000_000L
private const val NS_POR_TIQUE_DO_RELOGIO = 100L
private const val RELOGIO_DE_ALTA_RESOLUCAO = 0x00000002
private const val ACESSO_TOTAL_AO_RELOGIO = 0x1F0003

@Composable
fun FrameWindowScope.LimitandoOsQuadros(acompanharATela: () -> Boolean) {
    DisposableEffect(window) {
        val relogio = RelogioPreciso.abrir() ?: return@DisposableEffect onDispose {}
        val compasso = Compasso(INTERVALO_A_60_NS, relogio)
        val monitor = TaxaDoMonitor(window)
        val desfazer = embrulharODesenho(window) { original ->
            compassado(original, compasso) { acompanharATela() || monitor.jaLimita() }
        }
        onDispose {
            desfazer()
            relogio.fechar()
        }
    }
}

private fun compassado(
    original: SkikoRenderDelegate,
    compasso: Compasso,
    semEsperar: () -> Boolean,
): SkikoRenderDelegate = object : SkikoRenderDelegate {
    override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
        val instante = if (semEsperar()) nanoTime else compasso.esperarAVez()
        original.onRender(canvas, width, height, instante)
    }
}

private class TaxaDoMonitor(private val janela: Window) {
    private var aparelho: GraphicsDevice? = null
    private var taxa = 0
    private var conferidaEm = 0L

    fun jaLimita(): Boolean {
        val agora = System.nanoTime()
        val atual = janela.graphicsConfiguration?.device
        if (atual !== aparelho || agora - conferidaEm > RECONFERIR_A_TAXA_NS) {
            aparelho = atual
            taxa = atual?.displayMode?.refreshRate ?: 0
            conferidaEm = agora
        }
        return taxa in MENOR_TAXA_CONHECIDA..TAXA_QUE_JA_LIMITA
    }
}

private class Compasso(private val intervalo: Long, private val relogio: RelogioPreciso) {
    private var proximo = 0L
    private var anterior = 0L

    fun esperarAVez(): Long {
        var agora = System.nanoTime()
        if (agora - anterior >= intervalo - FOLGA_DO_MONITOR_NS) {
            proximo = agora + intervalo
        } else {
            if (agora < proximo) {
                relogio.dormir(proximo - agora)
                agora = System.nanoTime()
            }
            proximo += intervalo
        }
        anterior = agora
        return agora
    }
}

private class RelogioPreciso(private val alca: WinNT.HANDLE) {
    private val prazo = LongByReference()

    fun dormir(ns: Long) {
        prazo.value = -(ns / NS_POR_TIQUE_DO_RELOGIO)
        if (Temporizador.I?.SetWaitableTimer(alca, prazo, 0, null, null, false) == true) {
            Kernel32.INSTANCE.WaitForSingleObject(alca, WinBase.INFINITE)
        }
    }

    fun fechar() {
        Kernel32.INSTANCE.CloseHandle(alca)
    }

    companion object {
        fun abrir(): RelogioPreciso? = runCatching {
            Temporizador.I?.CreateWaitableTimerExW(null, null, RELOGIO_DE_ALTA_RESOLUCAO, ACESSO_TOTAL_AO_RELOGIO)
        }.getOrNull()?.let(::RelogioPreciso)
    }
}

@Suppress("FunctionNaming")
private interface Temporizador : StdCallLibrary {
    fun CreateWaitableTimerExW(atributos: Pointer?, nome: WString?, opcoes: Int, acesso: Int): WinNT.HANDLE?
    fun SetWaitableTimer(
        alca: WinNT.HANDLE,
        prazo: LongByReference,
        periodo: Int,
        rotina: Pointer?,
        argumento: Pointer?,
        retomar: Boolean,
    ): Boolean
    companion object {
        val I: Temporizador? = runCatching { Native.load("kernel32", Temporizador::class.java) }.getOrNull()
    }
}
