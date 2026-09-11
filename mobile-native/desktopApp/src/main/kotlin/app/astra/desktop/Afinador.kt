package app.astra.desktop

import app.astra.desktop.prefs.DEGRAU_MAXIMO
import app.astra.desktop.prefs.DEGRAU_SEM_CASCATA
import app.astra.desktop.prefs.DEGRAU_SEM_ESTRELAS
import app.astra.desktop.prefs.DEGRAU_SEM_FUNDO
import app.astra.desktop.prefs.DEGRAU_SEM_PET
import app.astra.desktop.prefs.DesktopPrefs
import app.astra.desktop.ui.Quadros
import app.astra.desktop.voice.Transmitindo
import com.sun.jna.Native
import com.sun.jna.Structure
import com.sun.jna.win32.StdCallLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val COMPASSO_MS = 4_000L
private const val FOLGA_PARA_DESCER = 1.35
private const val FOLGA_PARA_SUBIR = 1.05
private const val LEITURAS_PARA_DESCER = 2
private const val LEITURAS_PARA_SUBIR = 8
private const val ALVO_PADRAO_MS = 16.7

object Afinador {

    private class SYSTEM_POWER_STATUS : Structure() {
        @JvmField var ACLineStatus: Byte = 0
        @JvmField var BatteryFlag: Byte = 0
        @JvmField var BatteryLifePercent: Byte = 0
        @JvmField var SystemStatusFlag: Byte = 0
        @JvmField var BatteryLifeTime: Int = 0
        @JvmField var BatteryFullLifeTime: Int = 0

        override fun getFieldOrder() = listOf(
            "ACLineStatus", "BatteryFlag", "BatteryLifePercent",
            "SystemStatusFlag", "BatteryLifeTime", "BatteryFullLifeTime",
        )
    }

    private interface Kernel32 : StdCallLibrary {
        fun GetSystemPowerStatus(estado: SYSTEM_POWER_STATUS): Boolean
        companion object {
            val I: Kernel32? = runCatching {
                Native.load("kernel32", Kernel32::class.java)
            }.getOrNull()
        }
    }

    val alvoMs: Double by lazy {
        runCatching {
            val hz = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .defaultScreenDevice.displayMode.refreshRate
            if (hz > 0) maxOf(1000.0 / hz, ALVO_PADRAO_MS) else ALVO_PADRAO_MS
        }.getOrDefault(ALVO_PADRAO_MS)
    }

    @Volatile var degrau: Int = 0
        private set

    @Volatile var motivo: String = ""
        private set

    private var apertadas = 0
    private var folgadas = 0

    fun naBateria(): Boolean {
        val k = Kernel32.I ?: return false
        val estado = SYSTEM_POWER_STATUS()
        if (!runCatching { k.GetSystemPowerStatus(estado) }.getOrDefault(false)) return false
        return estado.ACLineStatus.toInt() == 0
    }

    private fun pisoDoContexto(): Int = when {
        Transmitindo.ativo.value -> DEGRAU_SEM_ESTRELAS
        VozNaBandeja.sessao != null -> DEGRAU_SEM_FUNDO
        naBateria() -> DEGRAU_SEM_FUNDO
        else -> 0
    }

    private fun motivoDoPiso(): String? = when {
        Transmitindo.ativo.value -> "enquanto você transmite"
        VozNaBandeja.sessao != null -> "enquanto você está em chamada"
        naBateria() -> "porque o notebook está na bateria"
        else -> null
    }

    private fun afetados(de: Int, ate: Int, p: DesktopPrefs.Prefs): List<String> {
        val faixa = (minOf(de, ate) + 1)..maxOf(de, ate)
        return buildList {
            if (p.auroraEnabled && DEGRAU_SEM_FUNDO in faixa) add("a aurora")
            if (p.starsEnabled && DEGRAU_SEM_ESTRELAS in faixa) add("as estrelas")
            if (p.petLigado && DEGRAU_SEM_PET in faixa) add("o companheiro")
            if (DEGRAU_SEM_CASCATA in faixa) add("as animações de entrada")
        }
    }

    private fun recado(de: Int, ate: Int, p: DesktopPrefs.Prefs, porContexto: Boolean): String? {
        val itens = afetados(de, ate, p)
        if (itens.isEmpty()) return null
        val lista =
            if (itens.size == 1) itens[0]
            else itens.dropLast(1).joinToString(", ") + " e " + itens.last()
        if (ate < de) return "O Astra devolveu $lista."
        val porque = (if (porContexto) motivoDoPiso() else null) ?: "porque os quadros estavam atrasando"
        return "O Astra suspendeu $lista $porque."
    }

    fun afinar(escopo: CoroutineScope, prefs: DesktopPrefs, avisar: (String) -> Unit = {}) {
        escopo.launch(Dispatchers.Default) {
            var aplicado = -1
            while (true) {
                delay(COMPASSO_MS)
                val piso = pisoDoContexto()
                val p95 = Quadros.p95Ms
                if (Quadros.amostras > 0 && p95 > 0.0) {
                    if (p95 > alvoMs * FOLGA_PARA_DESCER) {
                        apertadas++
                        folgadas = 0
                    } else if (p95 < alvoMs * FOLGA_PARA_SUBIR) {
                        folgadas++
                        apertadas = 0
                    }
                }
                var novo = degrau
                if (apertadas >= LEITURAS_PARA_DESCER && novo < DEGRAU_MAXIMO) {
                    novo++
                    apertadas = 0
                    Quadros.esquecer()
                    motivo = "o quadro estava atrasando"
                } else if (folgadas >= LEITURAS_PARA_SUBIR && novo > 0) {
                    novo--
                    folgadas = 0
                    Quadros.esquecer()
                    motivo = "sobrou folga"
                }
                degrau = novo
                val efetivo = maxOf(novo, piso)
                if (aplicado >= 0 && efetivo != aplicado) {
                    recado(aplicado, efetivo, prefs.state.value, piso > novo)?.let(avisar)
                }
                aplicado = efetivo
                prefs.aplicarDegrau(efetivo)
            }
        }
    }
}
