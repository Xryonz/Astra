package app.astra.desktop

import app.astra.desktop.prefs.DEGRAU_MAXIMO
import app.astra.desktop.prefs.DEGRAU_SEM_ESTRELAS
import app.astra.desktop.prefs.DEGRAU_SEM_FUNDO
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
            if (hz > 0) 1000.0 / hz else ALVO_PADRAO_MS
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

    fun afinar(escopo: CoroutineScope, prefs: DesktopPrefs) {
        escopo.launch(Dispatchers.Default) {
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
                prefs.aplicarDegrau(maxOf(novo, piso))
            }
        }
    }
}
