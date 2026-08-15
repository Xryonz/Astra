package app.astra.desktop

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// O AVISO SONORO — duas notas curtas subindo, sintetizadas na hora.
//
// SEM ARQUIVO DE ÁUDIO de propósito: um .wav no pacote seria mais um binário pra
// versionar, e o som que o dono descreveu ("chamativo mas não alto") são duas
// senoides — descrevê-las em vinte linhas é menor e mais ajustável que empacotar
// o resultado delas.
//
// POR QUE DUAS NOTAS E NÃO UMA: um toque isolado precisa de VOLUME pra ser notado,
// e volume era exatamente o que não se podia gastar. Duas notas com intervalo
// ascendente são reconhecidas pelo desenho, não pela força — o ouvido identifica
// "subiu" mesmo baixinho, e é por isso que campainha, elevador e mensagem de
// celular quase sempre têm mais de uma nota.
//
// A quinta justa (razão 3:2) é o intervalo mais consonante depois da oitava. Isso
// importa aqui porque o som vai tocar mil vezes: intervalo dissonante cansa por
// repetição, e o pedido explícito era não virar um incômodo quando chegam várias.

private const val TAXA = 44_100f
private const val NOTA_GRAVE = 660.0   // mi5
private const val NOTA_AGUDA = 990.0   // si5 — uma quinta acima (3:2)
private const val MS_GRAVE = 75
private const val MS_AGUDA = 105

// Volume de pico. 0.18 da escala é audível em fone e em caixa de notebook sem
// atropelar o que estiver tocando — a régua aqui é "eu ouço", não "eu paro".
private const val PICO = 0.18

// UM SOM POR VEZ, com trava de 1,2s. Cinco mensagens em rajada tocariam cinco
// vezes por cima de si mesmas e virariam ruído — que é exatamente a reclamação que
// o dono levantou antes de a coisa existir. A primeira toca; as de dentro da
// janela são engolidas.
private const val TRAVA_MS = 1_200L

private val ultimaVez = AtomicLong(0L)

// Uma thread só, própria, e daemon: tocar não pode segurar o fechamento do app nem
// travar a interface (abrir a linha de áudio bloqueia por alguns milissegundos).
private val toca by lazy {
    Executors.newSingleThreadExecutor { r ->
        Thread(r, "astra-som").apply { isDaemon = true }
    }
}

fun tocarAvisoDeMensagem() {
    val agora = System.currentTimeMillis()
    val anterior = ultimaVez.get()
    if (agora - anterior < TRAVA_MS) return
    if (!ultimaVez.compareAndSet(anterior, agora)) return
    // runCatching engole tudo: máquina sem placa de som, saída ocupada em modo
    // exclusivo, driver recusando o formato. Nada disso vale derrubar um aviso —
    // a notificação visual já apareceu, o som é o reforço.
    toca.execute { runCatching { emitir(construirOnda()) } }
}

private fun construirOnda(): ByteArray {
    val amostrasGrave = (TAXA * MS_GRAVE / 1000).toInt()
    val amostrasAguda = (TAXA * MS_AGUDA / 1000).toInt()
    val total = amostrasGrave + amostrasAguda
    // 16 bits com sinal, mono -> dois bytes por amostra.
    val saida = ByteArray(total * 2)

    for (i in 0 until total) {
        val naSegunda = i >= amostrasGrave
        val freq = if (naSegunda) NOTA_AGUDA else NOTA_GRAVE
        val posicao = if (naSegunda) i - amostrasGrave else i
        val duracao = if (naSegunda) amostrasAguda else amostrasGrave

        // ENVELOPE, e é ele que separa "nota" de "clique". Ligar e desligar uma
        // senoide na marra deixa um degrau na forma de onda, e degrau é um estalo
        // audível — o defeito clássico de som gerado em código. A janela de cosseno
        // levantado sobe e desce suave, então só sobra a nota.
        val fase = posicao.toDouble() / duracao
        val envelope = 0.5 * (1.0 - cos(2.0 * PI * fase))

        val valor = sin(2.0 * PI * freq * posicao / TAXA) * envelope * PICO
        val amostra = (valor * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767)
        // Little-endian, na ordem que o formato declara lá embaixo.
        saida[i * 2] = (amostra and 0xFF).toByte()
        saida[i * 2 + 1] = ((amostra shr 8) and 0xFF).toByte()
    }
    return saida
}

private fun emitir(pcm: ByteArray) {
    val formato = AudioFormat(TAXA, 16, 1, true, false)
    val linha = AudioSystem.getLine(javax.sound.sampled.DataLine.Info(SourceDataLine::class.java, formato))
        as SourceDataLine
    linha.open(formato)
    linha.start()
    linha.write(pcm, 0, pcm.size)
    // drain antes de fechar: sem isso o close corta o rabo do som, e a segunda
    // nota — que é a que dá o sentido de "subiu" — é justamente a que some.
    linha.drain()
    linha.stop()
    linha.close()
}
