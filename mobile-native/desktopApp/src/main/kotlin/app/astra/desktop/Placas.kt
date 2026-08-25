package app.astra.desktop

import com.sun.jna.Native
import com.sun.jna.Structure
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

object Placas {

    data class Placa(
        val id: String,
        val nome: String,
        val desenhaATela: Boolean,
        val dedicada: Boolean,
        val marca: String?,
    )

    class DISPLAY_DEVICE : Structure() {
        @JvmField var cb: Int = 0
        @JvmField var DeviceName: CharArray = CharArray(32)
        @JvmField var DeviceString: CharArray = CharArray(128)
        @JvmField var StateFlags: Int = 0
        @JvmField var DeviceID: CharArray = CharArray(128)
        @JvmField var DeviceKey: CharArray = CharArray(128)

        override fun getFieldOrder() =
            listOf("cb", "DeviceName", "DeviceString", "StateFlags", "DeviceID", "DeviceKey")
    }

    interface U32 : StdCallLibrary {
        fun EnumDisplayDevicesW(aparelho: String?, indice: Int, info: DISPLAY_DEVICE, bandeiras: Int): Boolean
        companion object {
            val I: U32? = runCatching {
                Native.load("user32", U32::class.java, W32APIOptions.UNICODE_OPTIONS)
            }.getOrNull()
        }
    }

    private const val ANEXADA_AO_DESKTOP = 0x00000001

    val todas: List<Placa> by lazy { descobrir() }

    val daTela: Placa? get() = todas.firstOrNull { it.desenhaATela }

    fun porId(id: String?): Placa? = id?.let { alvo -> todas.firstOrNull { it.id == alvo } }

    private fun descobrir(): List<Placa> {
        val u = U32.I ?: return emptyList()
        val achadas = LinkedHashMap<String, Placa>()
        for (i in 0 until 16) {
            val info = DISPLAY_DEVICE()
            info.cb = info.size()
            if (!runCatching { u.EnumDisplayDevicesW(null, i, info, 0) }.getOrDefault(false)) break
            val id = curto(texto(info.DeviceID))
            if (id.isBlank()) continue
            val nome = texto(info.DeviceString).ifBlank { id }
            val desenha = (info.StateFlags and ANEXADA_AO_DESKTOP) != 0
            val antes = achadas[id]
            achadas[id] = Placa(
                id = id,
                nome = nome,
                desenhaATela = desenha || antes?.desenhaATela == true,
                dedicada = ehDedicada(id),
                marca = marcaDe(id),
            )
        }
        return achadas.values.toList()
    }

    private fun texto(c: CharArray): String {
        val fim = c.indexOfFirst { it.code == 0 }.let { if (it < 0) c.size else it }
        return String(c, 0, fim).trim()
    }

    private fun curto(deviceId: String): String =
        deviceId.split("&").take(2).joinToString("&")

    private fun ehDedicada(id: String): Boolean = marcaDe(id) != "Intel"

    private fun marcaDe(id: String): String? {
        val u = id.uppercase()
        return when {
            u.contains("VEN_10DE") -> "NVIDIA"
            u.contains("VEN_8086") -> "Intel"
            u.contains("VEN_1002") -> "AMD"
            else -> null
        }
    }
}
