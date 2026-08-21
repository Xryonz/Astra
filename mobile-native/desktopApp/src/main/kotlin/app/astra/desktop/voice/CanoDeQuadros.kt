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

// O CANO DE QUADROS — por onde a tela de outra pessoa chega ao Astra.
//
// A ponte de comandos (stdin/stdout, JSON por linha) carrega coisas de dezenas de
// bytes. Um quadro de 720p em NV12 são 1,4 MB, e a 30 por segundo são 40 MB/s: passar
// isso por lá faria o aviso de "fulano está falando" esperar atrás de megabytes de
// imagem. Por isso um cano à parte.
//
// QUEM ESCUTA É ESTE LADO, e é o contrário do que parece natural — o dono do dado é o
// processo de voz. A razão é uma corrida: se ele abrisse a porta, teria de ANUNCIAR o
// número dela pela ponte, e nós teríamos de estar ouvindo antes de ele falar. Com a
// porta aberta AQUI, o endereço já existe quando o processo é lançado e viaja no
// ambiente dele. Não há o que perder e não há o que reencontrar depois de uma queda.
//
// O SEGREDO NÃO É CERIMÔNIA. Uma porta de escuta na volta local aceita conexão de
// qualquer programa da máquina, e o que passa por aqui é a TELA DE ALGUÉM. O segredo é
// sorteado a cada execução, viaja pelo ambiente (que só pai e filho enxergam) e é a
// primeira coisa que a conexão precisa apresentar.
//
// FORMATO DE CADA QUADRO — implementado duas vezes, aqui e em `sidecar-voz/entrega.go`,
// e nenhum compilador confere que os dois concordam. Do lado de lá há um teste que trava
// campo a campo; aqui o que protege é a marca no começo, que transforma
// desalinhamento em erro alto em vez de imagem embaralhada.
//
//	0  uint32  marca ('ASTV')
//	4  uint32  bytes do id do par
//	8  uint32  largura
//	12 uint32  altura
//	16 uint32  passo — bytes por linha, PODE SER MAIOR que a largura
//	20 uint32  bytes do quadro
//	24 [..]    id do par, em UTF-8
//	   [..]    o quadro, em NV12

/**
 * Um quadro pronto para desenhar, em NV12.
 *
 * [passo] separado de [largura] porque o decodificador alinha as linhas ao que a placa
 * gosta, e assumir que são iguais não dá erro — dá imagem enviesada em diagonal.
 *
 * [serie] existe para o desenho saber que o conteúdo mudou: o vetor de bytes é
 * REAPROVEITADO (ver `Rodizio`), então comparar a referência diria "é o mesmo" quando já
 * é outro quadro.
 */
class QuadroDeTela(
    val largura: Int,
    val altura: Int,
    val passo: Int,
    val dados: ByteArray,
    val serie: Long,
)

class CanoDeQuadros {

    private val ouvinte = ServerSocket(0, 2, InetAddress.getLoopbackAddress())
    private val vivo = AtomicBoolean(true)

    /** Para o ambiente do processo de voz: `ASTRA_QUADROS`. */
    val endereco: String = "127.0.0.1:${ouvinte.localPort}"

    /** Para o ambiente do processo de voz: `ASTRA_QUADROS_SEGREDO`. */
    val segredo: String = ByteArray(24)
        .also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }

    private val _quadros = MutableStateFlow<Map<String, QuadroDeTela>>(emptyMap())

    /** A última tela de cada pessoa que está transmitindo. */
    val telas: StateFlow<Map<String, QuadroDeTela>> = _quadros.asStateFlow()

    init {
        thread(isDaemon = true, name = "astra-quadros") { aceitar() }
    }

    fun fechar() {
        if (!vivo.compareAndSet(true, false)) return
        runCatching { ouvinte.close() }
        _quadros.value = emptyMap()
    }

    /** Esquece a tela de alguém — chamado quando a pessoa sai ou para de transmitir. */
    fun esquecer(par: String) {
        _quadros.value = _quadros.value - par
    }

    private fun aceitar() {
        while (vivo.get()) {
            val con = runCatching { ouvinte.accept() }.getOrNull() ?: return
            // UMA CONEXÃO POR VEZ, e não uma thread por conexão: o processo de voz é um
            // só e liga uma vez. Aceitar em série significa que uma segunda conexão
            // (outro programa bisbilhotando) espera na fila em vez de ganhar uma thread.
            runCatching { atender(con) }
                .onFailure { VoiceLog.nota("[quadros] a ligação caiu: ${it.message}") }
            runCatching { con.close() }
        }
    }

    private fun atender(con: Socket) {
        con.tcpNoDelay = true
        val fonte = DataInputStream(con.getInputStream().buffered(1 shl 16))

        // O SEGREDO ANTES DE QUALQUER PIXEL. Linha simples: quem não apresenta o que
        // combinamos é desligado sem receber nem responder nada.
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
                // Desalinhou. Continuar lendo daqui produziria imagem embaralhada e uma
                // caçada no decodificador; cortar a ligação faz o outro lado religar
                // limpo, que é a única recuperação honesta de um fluxo posicional.
                VoiceLog.nota("[quadros] fluxo fora de compasso — religando")
                return
            }
            val tamPar = leU32(cabecalho, 4)
            val largura = leU32(cabecalho, 8)
            val altura = leU32(cabecalho, 12)
            val passo = leU32(cabecalho, 16)
            val tamDados = leU32(cabecalho, 20)

            // TETO NOS TAMANHOS. Nada aqui é hostil hoje — o outro lado é nosso —, mas
            // um número corrompido viraria uma alocação de gigabytes, e um app que morre
            // por falta de memória é pior de diagnosticar que uma imagem que some.
            if (tamPar !in 0..512 || tamDados !in 0..MAX_QUADRO || largura <= 0 || altura <= 0) {
                VoiceLog.nota("[quadros] cabeçalho fora de faixa (${largura}x$altura, $tamDados bytes)")
                return
            }

            val par = if (tamPar == 0) "" else ByteArray(tamPar).also { fonte.readFully(it) }.decodeToString()
            val novo = par !in rodizios
            val rodizio = rodizios.getOrPut(par) { Rodizio() }
            val destino = rodizio.proximo(tamDados)
            fonte.readFully(destino, 0, tamDados)

            // O PRIMEIRO QUADRO DE CADA PESSOA VAI PARA O REGISTRO, e uma vez só.
            //
            // Este formato é escrito em Go e lido aqui, e nenhum compilador confere que
            // os dois concordam. Quando a imagem não aparecer, a primeira pergunta será
            // "chegou alguma coisa, e com que forma?" — e a resposta precisa existir sem
            // depender de alguém estar com o depurador aberto no momento certo.
            if (novo) {
                VoiceLog.nota("[quadros] primeira tela de $par: ${largura}x$altura, passo $passo, $tamDados bytes")
            }

            serie++
            _quadros.value = _quadros.value + (par to QuadroDeTela(largura, altura, passo, destino, serie))
        }
    }

    private fun leU32(b: ByteArray, i: Int): Int =
        (b[i].toInt() and 0xFF) or
            ((b[i + 1].toInt() and 0xFF) shl 8) or
            ((b[i + 2].toInt() and 0xFF) shl 16) or
            ((b[i + 3].toInt() and 0xFF) shl 24)

    /**
     * TRÊS VETORES POR PESSOA, EM RODÍZIO — e não um vetor novo por quadro.
     *
     * Um novo a cada quadro seriam 1,4 MB trinta vezes por segundo: 40 MB/s de lixo, no
     * app onde já se lutou para segurar a memória. Um só seria pior de outro jeito — a
     * leitura sobrescreveria o quadro que a tela está desenhando naquele instante.
     *
     * Três resolve os dois: quando a leitura volta ao primeiro, já se passaram dois
     * quadros (uns 66ms a 30 por segundo). Se o desenho ainda estiver no de 66ms atrás,
     * ele já está perdendo quadros de qualquer jeito, e o pior caso é UM quadro rasgado
     * — não um travamento.
     */
    private class Rodizio {
        private val vetores = arrayOfNulls<ByteArray>(3)
        private var i = 0

        fun proximo(tamanho: Int): ByteArray {
            i = (i + 1) % vetores.size
            val atual = vetores[i]
            if (atual == null || atual.size < tamanho) {
                vetores[i] = ByteArray(tamanho)
            }
            return vetores[i]!!
        }
    }

    private companion object {
        const val CABECALHO = 24
        const val MARCA = 0x56545341 // 'ASTV'
        // 4K em NV12 com folga de passo. Teto de sanidade, não de capacidade.
        const val MAX_QUADRO = 4096 * 2160 * 3 / 2
    }
}
