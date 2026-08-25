package app.astra.desktop.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.DataInputStream
import java.io.EOFException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class QuadroDeTela(
    val largura: Int,
    val altura: Int,
    val passo: Int,
    val dados: ByteArray,
    val serie: Long,
)

class CanoDeQuadros {

    private val ouvinte: ServerSocket? = runCatching {
        ServerSocket(0, 2, InetAddress.getLoopbackAddress())
    }.onFailure {
        VoiceLog.nota("[quadros] sem cano de imagem: ${it.message}")
    }.getOrNull()

    private val vivo = AtomicBoolean(ouvinte != null)

    val endereco: String = ouvinte?.let { "127.0.0.1:${it.localPort}" } ?: ""

    val segredo: String = ByteArray(24)
        .also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }

    private val _quadros = MutableStateFlow<Map<String, QuadroDeTela>>(emptyMap())

    val telas: StateFlow<Map<String, QuadroDeTela>> = _quadros.asStateFlow()

    private val _quemTransmite = MutableStateFlow<Set<String>>(emptySet())

    val quemTransmite: StateFlow<Set<String>> = _quemTransmite.asStateFlow()

    init {
        if (ouvinte != null) thread(isDaemon = true, name = "astra-quadros") { aceitar(ouvinte) }
    }

    fun fechar() {
        if (!vivo.compareAndSet(true, false)) return
        runCatching { ouvinte?.close() }
        _quadros.value = emptyMap()
        _quemTransmite.value = emptySet()
    }

    fun esquecer(par: String) {
        _quadros.value = _quadros.value - par
        _quemTransmite.value = _quemTransmite.value - par
    }

    private fun aceitar(ouvinte: ServerSocket) {
        while (vivo.get()) {
            val con = runCatching { ouvinte.accept() }.getOrNull() ?: return
            runCatching { atender(con) }
                .onFailure { VoiceLog.nota("[quadros] a ligação caiu: ${it.message}") }
            runCatching { con.close() }
        }
    }

    private fun atender(con: Socket) {
        con.tcpNoDelay = true
        val fonte = DataInputStream(con.getInputStream().buffered(1 shl 16))

        val apresentado = StringBuilder()
        while (apresentado.length <= segredo.length + 1) {
            val c = fonte.read()
            if (c < 0) return
            if (c == '\n'.code) break
            apresentado.append(c.toChar())
        }
        if (apresentado.toString() != segredo) {
            VoiceLog.nota("[quadros] alguém tentou entrar no cano sem o segredo")
            return
        }

        val rodizios = HashMap<String, Rodizio>()
        var serie = 0L
        val cabecalho = ByteArray(CABECALHO)

        while (vivo.get()) {
            try {
                fonte.readFully(cabecalho)
            } catch (_: EOFException) {
                return
            }
            if (leU32(cabecalho, 0) != MARCA) {
                VoiceLog.nota("[quadros] fluxo fora de compasso — religando")
                return
            }
            val tamPar = leU32(cabecalho, 4)
            val largura = leU32(cabecalho, 8)
            val altura = leU32(cabecalho, 12)
            val passo = leU32(cabecalho, 16)
            val tamDados = leU32(cabecalho, 20)

            if (tamPar !in 0..512 || tamDados !in 0..MAX_QUADRO || largura <= 0 || altura <= 0) {
                VoiceLog.nota("[quadros] cabeçalho fora de faixa (${largura}x$altura, $tamDados bytes)")
                return
            }

            val par = if (tamPar == 0) "" else ByteArray(tamPar).also { fonte.readFully(it) }.decodeToString()
            val novo = par !in rodizios
            val rodizio = rodizios.getOrPut(par) { Rodizio() }
            val destino = rodizio.proximo(tamDados)
            fonte.readFully(destino, 0, tamDados)

            if (novo) {
                VoiceLog.nota("[quadros] primeira tela de $par: ${largura}x$altura, passo $passo, $tamDados bytes")
            }

            serie++
            val antes = _quadros.value
            _quadros.value = antes + (par to QuadroDeTela(largura, altura, passo, destino, serie))

            if (par !in antes) _quemTransmite.value = _quemTransmite.value + par

            if (serie % CONFERIR_A_CADA == 0L) descartarParados(rodizios)
        }
    }

    private fun leU32(b: ByteArray, i: Int): Int =
        (b[i].toInt() and 0xFF) or
            ((b[i + 1].toInt() and 0xFF) shl 8) or
            ((b[i + 2].toInt() and 0xFF) shl 16) or
            ((b[i + 3].toInt() and 0xFF) shl 24)

    private class Rodizio {
        private val vetores = arrayOfNulls<ByteArray>(3)
        private var i = 0

        var visto = System.nanoTime()
            private set

        fun proximo(tamanho: Int): ByteArray {
            visto = System.nanoTime()
            i = (i + 1) % vetores.size
            val atual = vetores[i]
            if (atual == null || atual.size < tamanho) {
                vetores[i] = ByteArray(tamanho)
            }
            return vetores[i]!!
        }
    }

    private fun descartarParados(rodizios: HashMap<String, Rodizio>) {
        val agora = System.nanoTime()
        val it = rodizios.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (agora - e.value.visto > PARADO_NS) {
                VoiceLog.nota("[quadros] soltando os vetores de ${e.key}: 30s sem quadro")
                it.remove()
            }
        }
    }

    private companion object {
        const val CABECALHO = 24
        const val MARCA = 0x56545341
        const val MAX_QUADRO = 4096 * 2160 * 3 / 2

        val PARADO_NS = 30_000_000_000L

        const val CONFERIR_A_CADA = 600
    }
}
