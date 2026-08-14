package app.astra.desktop

import com.sun.jna.Native
import com.sun.jna.Structure
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

// AS PLACAS DE VIDEO DA MAQUINA, e qual delas desenha a tela.
//
// POR QUE ISTO PRECISA EXISTIR: notebook com duas placas e o caso comum, e as duas nao
// sao intercambiaveis. O quadro de uma captura de tela NASCE no aparelho D3D11 da placa
// que desenha o monitor, e um encoder so consegue ler textura da propria placa. Pedir pra
// outra comprimir aquele quadro nao da erro -- da SILENCIO: um quadro entra, nenhum sai.
// Foi assim que a transmissao deste aplicativo ficou muda por versoes seguidas.
//
// Entao "qual placa desenha a tela" nao e curiosidade: e o que decide quem pode comprimir.
//
// PELO WINDOWS, e nao pelo GStreamer. Daria pra descobrir isto pelos nomes dos elementos
// do GStreamer, mas ai a tela de configuracoes so funcionaria depois de baixar o pacote de
// video -- e a pergunta "que placas eu tenho?" nao depende de nada disso. O
// `EnumDisplayDevices` responde na hora, sem carregar biblioteca nenhuma.
object Placas {

    // `id` e o par fabricante+modelo do PCI (ex.: PCI\VEN_10DE&DEV_28A0). E o que se
    // guarda na preferencia: sobrevive a troca de driver e a reordenacao do sistema, que
    // um indice de posicao nao sobrevive.
    data class Placa(
        val id: String,
        val nome: String,
        val desenhaATela: Boolean,
        val dedicada: Boolean,
        // "NVIDIA" | "Intel" | "AMD" | null. E o vocabulario que o lado do video usa pra
        // casar com o encoder certo, e vem do codigo PCI -- nao do nome escrito.
        val marca: String?,
    )

    // Publica de proposito, e nao por descuido: a JNA preenche esta estrutura por
    // REFLEXAO, e classe privada do Kotlin vira classe inacessivel no JVM -- a leitura
    // falha com "Exception reading field 'cb'" antes de qualquer chamada nativa.
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

    // Lista descoberta uma vez. Trocar de placa de video exige desligar o computador, e
    // quem faz isso reabre o aplicativo.
    val todas: List<Placa> by lazy { descobrir() }

    val daTela: Placa? get() = todas.firstOrNull { it.desenhaATela }

    fun porId(id: String?): Placa? = id?.let { alvo -> todas.firstOrNull { it.id == alvo } }

    private fun descobrir(): List<Placa> {
        val u = U32.I ?: return emptyList()
        // Por ID, e nao por nome: o Windows lista UMA ENTRADA POR SAIDA DE VIDEO, entao a
        // mesma placa aparece quatro vezes num notebook com tela, HDMI e duas portas USB-C.
        // Quatro linhas identicas numa lista de escolha seriam um defeito visivel.
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
                // Basta UMA saida anexada pra placa estar desenhando algo.
                desenhaATela = desenha || antes?.desenhaATela == true,
                dedicada = ehDedicada(id),
                marca = marcaDe(id),
            )
        }
        return achadas.values.toList()
    }

    // O Windows escreve o texto terminado em ZERO dentro de um vetor de tamanho fixo, e o
    // que vem depois e lixo. Corta-se no zero -- `trim()` nao remove caractere nulo.
    private fun texto(c: CharArray): String {
        val fim = c.indexOfFirst { it.code == 0 }.let { if (it < 0) c.size else it }
        return String(c, 0, fim).trim()
    }

    // "PCI\VEN_10DE&DEV_28A0&SUBSYS_...&REV_A1" -> "PCI\VEN_10DE&DEV_28A0". O resto
    // (subsistema, revisao) e detalhe de placa-mae e so atrapalha a comparacao.
    private fun curto(deviceId: String): String =
        deviceId.split("&").take(2).joinToString("&")

    // Pelo codigo de fabricante do PCI, e nao pelo nome escrito. Nome de placa muda com o
    // driver e vem traduzido em alguns idiomas; 0x8086 e a Intel desde sempre.
    //
    // A Intel entra como integrada e as outras como dedicadas. E heuristica -- existe Intel
    // Arc dedicada e existe AMD integrada em APU -- mas ela so decide a PALAVRA que aparece
    // ao lado do nome. Quem decide se a placa serve pra transmitir e `desenhaATela`, que e
    // medido, nao adivinhado.
    private fun ehDedicada(id: String): Boolean = marcaDe(id) != "Intel"

    // Os tres codigos de fabricante que importam aqui. 0x8086 e a Intel (o numero e uma
    // piada interna deles com o 8086), 0x10DE a NVIDIA, 0x1002 a AMD/ATI.
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
