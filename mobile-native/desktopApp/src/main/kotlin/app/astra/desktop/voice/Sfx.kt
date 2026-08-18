package app.astra.desktop.voice

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

// Sons do app SINTETIZADOS em runtime (sem arquivos .wav): senoides curtas com
// envelope (fade in/out) pra não estalar. Convencao do dono:
//   entrar na call  = agudo/fino  (sobe)
//   sair da call     = grave/grosso (desce)
//   transmitir tela  = 3 fases subindo (cada fase mais fina)
//   parar transmissão= as MESMAS 3 fases, invertidas (descendo)
// Toca numa thread daemon (não trava a UI); so JDK (javax.sound), zero dependencia.
object Sfx {
    private const val RATE = 44100

    // `sino` troca o envelope: em vez do trapézio (sobe, segura, desce), o tom
    // ataca em 4ms e cai exponencialmente, como corpo percutido. Ver `render`.
    private data class Tone(val hz: Float, val ms: Int, val gain: Float = 0.26f, val sino: Boolean = false)

    fun callJoin()   = play(listOf(Tone(620f, 80), Tone(930f, 150)))
    fun callLeave()  = play(listOf(Tone(430f, 90), Tone(300f, 175)))
    fun shareStart() = play(listOf(Tone(500f, 95), Tone(680f, 95), Tone(920f, 155)))
    fun shareStop()  = play(listOf(Tone(920f, 95), Tone(680f, 95), Tone(500f, 155)))

    // ---- Aviso de mensagem ----
    //
    // Duas notas subindo uma TERÇA MAIOR (Lá5 → Dó#6). Terça é consonante e soa
    // como pergunta amável; segunda ou trítono soariam como alarme, e alarme é o
    // que faz alguém desligar o som do app no terceiro dia.
    //
    // POR QUE NÃO É O TRAPÉZIO DOS OUTROS SONS: o envelope de sustentação plana
    // soa como BIPE de aparelho. Aviso toca dezenas de vezes por dia — precisa
    // desaparecer da consciência entre uma vez e outra, e é o decaimento rápido
    // que faz isso. Junto vai uma 4ª harmônica fraca, que é o truque clássico do
    // timbre de marimba: dá corpo de madeira a uma senóide sem custar nada.
    //
    // Total ~350ms e ganho abaixo do toque de chamada de propósito: isto avisa,
    // não convoca.
    fun aviso() = play(
        listOf(
            Tone(880f, 110, gain = 0.20f, sino = true),
            Tone(1108.7f, 240, gain = 0.18f, sino = true),
        ),
    )

    // ---- Carinho no gato ----
    //
    // Isto é um TRINADO, não um ronronar, e a diferença é honesta. Ronronar de
    // verdade é ruído de banda larga modulado a ~25 Hz; com tons puros, a imitação
    // sai como zumbido de motor. O trinado — aquele "prrup" curto de gato
    // cumprimentando — é justamente uma nota subindo depressa, e essa este motor
    // faz bem.
    //
    // Ganho baixo e 200ms no total: acontece quando você clica no bicho, ou seja,
    // dezenas de vezes por sessão se a pessoa gostar. Som de interação frequente
    // precisa ser mais curto e mais quieto do que o instinto pede.
    fun carinho() = play(
        listOf(
            Tone(392f, 70, gain = 0.11f, sino = true),
            Tone(523.3f, 130, gain = 0.09f, sino = true),
        ),
    )

    // ---- Toque de chamada no sussurro ----
    //
    // Repete ate alguem parar (atender, recusar ou o servidor desistir em 45s).
    // Uma thread so, com bandeira: chamar duas vezes nao empilha dois toques.
    //
    // O silencio entre as repeticoes e rendido como amostra ZERO em vez de um
    // sleep entre duas aberturas de linha de audio: abrir e fechar a
    // SourceDataLine a cada 3 segundos estala em alguns drivers do Windows, e o
    // estalo chega mais alto que o proprio toque.
    @Volatile private var tocando = false

    private val TOQUE = listOf(
        Tone(880f, 150), Tone(0f, 90), Tone(1170f, 220), Tone(0f, 2400, gain = 0f),
    )
    // Quem LIGOU ouve algo mais grave e mais baixo: e so pra saber que esta indo,
    // nao pra chamar atencao de ninguem.
    private val CHAMANDO = listOf(
        Tone(392f, 300, gain = 0.10f), Tone(0f, 2200, gain = 0f),
    )

    fun ringStart(souEuQueLiguei: Boolean) {
        if (tocando) return
        tocando = true
        val seq = if (souEuQueLiguei) CHAMANDO else TOQUE
        thread(isDaemon = true, name = "astra-ring") {
            runCatching {
                val fmt = AudioFormat(RATE.toFloat(), 16, 1, true, false)
                val buf = render(seq)
                AudioSystem.getSourceDataLine(fmt).apply {
                    open(fmt)
                    start()
                    // `write` bloqueia enquanto o buffer escoa, entao o laco anda
                    // no ritmo do audio — sem relogio nenhum.
                    while (tocando) write(buf, 0, buf.size)
                    stop()
                    close()
                }
            }
            tocando = false
        }
    }

    fun ringStop() { tocando = false }

    private fun play(seq: List<Tone>) {
        thread(isDaemon = true, name = "astra-sfx") {
            runCatching {
                val fmt = AudioFormat(RATE.toFloat(), 16, 1, true, false)
                val buf = render(seq)
                AudioSystem.getSourceDataLine(fmt).apply {
                    open(fmt)
                    start()
                    write(buf, 0, buf.size)
                    drain()
                    stop()
                    close()
                }
            }
        }
    }

    // PCM 16-bit mono little-endian. Cada tom ganha attack/release (18% da duracao)
    // pra evitar o "clique" de ligar/desligar a senoide seca.
    private fun render(seq: List<Tone>): ByteArray {
        val total = seq.sumOf { it.ms * RATE / 1000 }
        val out = ByteArray(total * 2)
        var idx = 0
        for (t in seq) {
            val n = t.ms * RATE / 1000
            val fade = (n * 0.18f).toInt().coerceAtLeast(1)
            // Ataque de 4ms: rápido o bastante pra soar percutido, longo o bastante
            // pra não estalar. Ligar a senóide seca produz um clique que chega mais
            // alto que a própria nota.
            val ataqueN = (RATE * 0.004f).coerceAtLeast(1f)
            // Solta de 3ms no fim. O decaimento exponencial ainda vale ~4% quando o
            // tom acaba, e cair desses 4% direto pra zero é um degrau — audível
            // como estalinho na emenda entre as duas notas.
            val soltaN = (RATE * 0.003f).coerceAtLeast(1f)
            for (i in 0 until n) {
                val env = if (t.sino) {
                    val ataque = (i / ataqueN).coerceAtMost(1f)
                    val queda = exp(-3.2f * i / n)
                    val solta = ((n - i) / soltaN).coerceAtMost(1f)
                    ataque * queda * solta
                } else when {
                    i < fade -> i.toFloat() / fade
                    i > n - fade -> (n - i).toFloat() / fade
                    else -> 1f
                }
                val fase = 2.0 * PI * t.hz * i / RATE
                // 4ª harmônica a 22%: duas oitavas acima do fundamental. É o que
                // separa "madeira percutida" de "senóide pura" — e some junto com
                // o fundamental porque compartilha o mesmo envelope.
                val onda = if (t.sino) (sin(fase) + 0.22 * sin(4.0 * fase)) / 1.22 else sin(fase)
                val s = onda.toFloat() * t.gain * env
                val v = (s * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767)
                out[idx++] = (v and 0xFF).toByte()
                out[idx++] = ((v shr 8) and 0xFF).toByte()
            }
        }
        return out
    }
}
