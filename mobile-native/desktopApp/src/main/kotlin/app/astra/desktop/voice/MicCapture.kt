package app.astra.desktop.voice

import dev.onvoid.webrtc.media.audio.AudioProcessing
import dev.onvoid.webrtc.media.audio.AudioProcessingConfig
import dev.onvoid.webrtc.media.audio.AudioProcessingStreamConfig
import dev.onvoid.webrtc.media.audio.CustomAudioSource
import javax.sound.sampled.AudioFormat
import dev.onvoid.webrtc.media.audio.AudioDeviceModule
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

// Captura o microfone por javax.sound.sampled (JDK puro) e passa CADA bloco de
// 10ms pelo APM do WebRTC (AudioProcessing) antes de empurrar no CustomAudioSource:
// supressao de ruido + high-pass + ganho automatico, já convertendo pra 48kHz mono
// (o caminho feliz do WebRTC/Opus). Antes o PCM ia CRU -> Opus produzia "voz de
// robo com ruido" (mic cru + reamostragem 44.1k/estereo).
//
// AEC (cancelamento de eco): o APM precisa dos DOIS lados. O que entra pelo mic
// vem por processStream (aqui), e o que SAI na caixa de som tem que vir por
// processReverseStream — e vem de fora, pelo `processarReverso` mais abaixo, que
// o VoiceEngine alimenta com o audio decodificado do outro participante. Enquanto
// so existia metade, ligar "cancelar eco" nas preferencias nao fazia nada: o
// cancelador nao tem como subtrair um sinal que nunca viu.
//
// O Core Audio nativo do webrtc-java quebra a captura quando o ADM e anexado ao
// factory ("Start recording failed"), entao a captura vem por este caminho
// independente que roda em qualquer maquina; o APM roda por fora, na mao.
//
// onLevel recebe o RMS (0..1) de cada bloco — usado pra "quem está falando".
//
// PARA ONDE O PCM VAI virou escolha de quem constroi, porque agora ha dois transportes:
// o `CustomAudioSource` do webrtc-java (o de sempre) e o `appsrc` do GStreamer. O que
// acontece ANTES — abrir o mic, o APM, o gate, o RMS — e identico nos dois, e e a parte
// que custou caro pra acertar. Duplicar esta classe pra trocar a ultima linha seria
// manter dois lugares onde a voz pode quebrar de forma diferente.
fun interface DestinoDeAudio {
    // bits/taxa/canais viajam junto porque o caminho de fallback (sem APM) entrega o
    // formato CRU do aparelho, e nao os 48kHz mono do caminho feliz.
    fun empurrar(pcm: ByteArray, bits: Int, taxa: Int, canais: Int, quadros: Int)
}

class MicCapture(
    private val destino: DestinoDeAudio,
    private val noiseSuppress: Boolean,
    private val autoGain: Boolean,
    private val echoCancel: Boolean,
    // Dispositivo de entrada (nome do Mixer; null = padrao do sistema).
    private val inputDeviceName: String? = null,
    // Voice gate: 0 = sempre transmite; >0 = so transmite acima desse RMS (0..1).
    private val sensitivity: Float = 0f,
    private val onLevel: (Float) -> Unit,
) {
    private var line: TargetDataLine? = null
    private var apm: AudioProcessing? = null
    @Volatile private var running = false

    // A thread da captura, guardada pra `stop()` conseguir ESPERAR por ela.
    //
    // ISTO E O QUE IMPEDIA O APP DE FECHAR SOZINHO. `stop()` so baixava a
    // bandeira e voltava na hora; quem chamava seguia adiante e dava dispose() no
    // CustomAudioSource — que e memoria nativa. Se a thread estivesse a um passo
    // do pushAudio (e a cada 10ms ela esta), o push caia em memoria ja liberada.
    // Isso nao lanca excecao: corrompe o heap nativo, e o Windows derruba o
    // processo inteiro com 0x80000003. Da pra ver o resultado do lado de fora
    // como "o Astra fechou do nada", sempre perto de entrar ou sair de call.
    private var thread: Thread? = null

    // O APM passa a ser tocado por DUAS threads: a do mic (processStream) e a do
    // WebRTC que entrega o audio do outro (processReverseStream). O objeto e
    // nativo — usar depois do dispose nao lanca excecao em Kotlin, derruba o
    // processo. A trava existe pra `stop()` esperar quem estiver dentro.
    private val trava = Any()

    // Buffer de saida do lado reverso, reaproveitado. O processReverseStream exige
    // um destino mesmo quando ninguem vai ler: o que importa e o APM ter OUVIDO o
    // sinal, nao o que ele devolve.
    private var bufReverso = ByteArray(0)

    fun start(): Boolean {
        val format = FORMATS.firstOrNull {
            runCatching { AudioSystem.isLineSupported(DataLine.Info(TargetDataLine::class.java, it)) }.getOrDefault(false)
        } ?: return false
        val rate = format.sampleRate.toInt()
        val channels = format.channels
        val inFrames = rate / 100 // bloco de 10ms na taxa capturada
        val inBytes = inFrames * channels * 2
        println("[MicCapture] formato do mic: ${rate}Hz ${channels}ch -> APM -> 48000Hz 1ch")

        // Buffer folgado (~200ms): pausa de GC não derruba amostra (o picote que soa
        // robotico). Tenta o device escolhido; se falhar, cai no padrao do sistema.
        val l = acquireLine(inputDeviceName, format, inBytes * 20)
            ?: (if (inputDeviceName != null) acquireLine(null, format, inBytes * 20) else null)
            ?: return false
        line = l

        // APM: NS (alto) + high-pass sempre + AGC conforme a pref. AEC so tem efeito
        // real com o reverso (fase propria); deixamos o flag conforme a pref.
        apm = runCatching {
            AudioProcessing().apply {
                applyConfig(
                    AudioProcessingConfig().apply {
                        noiseSuppression.enabled = noiseSuppress
                        noiseSuppression.level = AudioProcessingConfig.NoiseSuppression.Level.HIGH
                        highPassFilter.enabled = true
                        gainControl.enabled = autoGain
                        echoCanceller.enabled = echoCancel
                    },
                )
            }
        }.getOrNull()

        val inConfig = AudioProcessingStreamConfig(rate, channels)
        val outConfig = AudioProcessingStreamConfig(48000, 1)
        val outFrames = 480 // 10ms @ 48kHz mono
        val outBytes = outFrames * 2

        running = true
        Thread({
            val inBuf = ByteArray(inBytes)
            val outBuf = ByteArray(outBytes)
            // Silencio pra quando o gate fecha: mantem a cadencia de 10ms (o Opus
            // comprime silencio a quase nada) em vez de picar frames.
            val silenceOut = ByteArray(outBytes)
            val silenceIn = ByteArray(inBytes)
            // Cauda: segue transmitindo ~250ms depois de cair abaixo do limiar, pra
            // não cortar o fim das palavras (gate seco pica a fala).
            var lastActiveNs = 0L
            val hangoverNs = 250_000_000L
            fun gateOpen(level: Float): Boolean {
                if (sensitivity <= 0f) return true // 0 = sem gate (transmite sempre)
                val now = System.nanoTime()
                if (level >= sensitivity) { lastActiveNs = now; return true }
                return now - lastActiveNs < hangoverNs
            }
            while (running) {
                val n = runCatching { l.read(inBuf, 0, inBuf.size) }.getOrDefault(-1)
                if (n <= 0) break
                if (n < inBuf.size) continue // bloco parcial (shutdown): mantem alinhamento
                // Sob a trava: o dispose do APM pode acontecer a qualquer momento
                // vindo de outra thread, e a chamada nativa nao sobrevive a isso.
                val ok = synchronized(trava) {
                    val proc = apm
                    // Limpa (NS/HPF/AGC) + converte pra 48k mono no mesmo passo.
                    proc != null && runCatching { proc.processStream(inBuf, inConfig, outConfig, outBuf) }.isSuccess
                }
                if (ok) {
                    val level = rms(outBuf)
                    onLevel(level)
                    val buf = if (gateOpen(level)) outBuf else silenceOut
                    runCatching { destino.empurrar(buf, 16, 48000, 1, outFrames) }
                } else {
                    // Sem APM (ou processStream falhou): cai pro cru pra não ficar mudo.
                    val level = rms(inBuf)
                    onLevel(level)
                    val buf = if (gateOpen(level)) inBuf else silenceIn
                    runCatching { destino.empurrar(buf, 16, rate, channels, inFrames) }
                }
            }
        }, "mic-capture").also { thread = it }.apply {
            isDaemon = true
            // Prioridade alta, e nao e capricho: esta thread TEM que voltar a cada
            // 10ms. Se o agendador do Windows a deixar de fora por mais tempo que o
            // buffer da placa aguenta, as amostras que chegaram nesse meio-tempo sao
            // PERDIDAS - nao atrasam, somem. O resultado e o picote, e ele aparece
            // exatamente quando a maquina esta ocupada: aurora a 60fps, video
            // comprimindo, duas janelas do Astra abertas no mesmo PC. Em prioridade
            // normal ela disputa CPU de igual pra igual com o desenho da interface,
            // que pode esperar - ela nao.
            runCatching { priority = Thread.MAX_PRIORITY }
            start()
        }
        return true
    }

    // Abre a TargetDataLine no Mixer escolhido (ou padrao) e já liga. Null = falhou.
    private fun acquireLine(deviceName: String?, format: AudioFormat, bufBytes: Int): TargetDataLine? = runCatching {
        val info = DataLine.Info(TargetDataLine::class.java, format)
        val mi = deviceName?.let { n -> AudioSystem.getMixerInfo().firstOrNull { it.name == n } }
        val l = if (mi != null) AudioSystem.getMixer(mi).getLine(info) as TargetDataLine
        else AudioSystem.getLine(info) as TargetDataLine
        l.apply { open(format, bufBytes); start() }
    }.getOrNull()

    // O AUDIO QUE SAI NA CAIXA DE SOM, entregue ao APM pra ele saber o que
    // subtrair do microfone. Chamado da thread do WebRTC que decodifica o audio do
    // outro participante, a cada bloco de 10ms.
    //
    // Sem isto o cancelador de eco fica cego: ele so ve o mic, e o mic sozinho nao
    // tem como distinguir "voz da pessoa aqui" de "voz do outro saindo pela caixa".
    fun processarReverso(data: ByteArray, bitsPorAmostra: Int, taxa: Int, canais: Int, quadros: Int) {
        if (!echoCancel) return
        synchronized(trava) {
            val proc = apm ?: return
            val cfg = AudioProcessingStreamConfig(taxa, canais)
            val precisa = quadros * canais * (bitsPorAmostra / 8)
            if (bufReverso.size < precisa) bufReverso = ByteArray(precisa)
            runCatching { proc.processReverseStream(data, cfg, cfg, bufReverso) }
        }
    }

    // Dica de quanto tempo o som leva pra sair na caixa e voltar pelo mic. O AEC3
    // estima sozinho; isto so encurta a convergencia nos primeiros segundos.
    fun avisarAtraso(ms: Int) {
        synchronized(trava) { runCatching { apm?.setStreamDelayMs(ms) } }
    }

    /**
     * Para a captura e ESPERA a thread morrer.
     *
     * O retorno importa: `false` = a thread nao terminou a tempo, e quem chamou
     * NAO pode liberar o CustomAudioSource. Vazar uma fonte de audio custa alguns
     * KB ate a proxima call; liberar embaixo de um pushAudio em andamento derruba
     * o app inteiro. A escolha entre os dois nao e dificil.
     */
    fun stop(): Boolean {
        // Ordem obrigatoria: baixa a bandeira ANTES de fechar a linha. Ao contrario,
        // o read() volta com erro, o laco continua achando que deve rodar e tenta
        // ler de uma linha ja fechada.
        running = false
        // Fechar a linha e o que DESBLOQUEIA o read(): sem isso a thread ficaria
        // parada esperando 10ms de audio de um microfone que ninguem mais alimenta.
        runCatching { line?.stop() }
        runCatching { line?.close() }
        line = null

        val t = thread
        thread = null
        val morreu = if (t == null || t === Thread.currentThread()) {
            true
        } else {
            runCatching { t.join(FIM_MS); !t.isAlive }.getOrDefault(false)
        }

        // Sob a trava: se a thread do audio remoto estiver dentro do
        // processReverseStream agora, o dispose aqui derrubaria o processo.
        synchronized(trava) {
            runCatching { apm?.dispose() }
            apm = null
        }
        return morreu
    }

    private fun rms(buf: ByteArray): Float {
        var sum = 0.0
        var i = 0
        while (i + 1 < buf.size) {
            val s = ((buf[i].toInt() and 0xFF) or (buf[i + 1].toInt() shl 8)).toShort().toInt()
            sum += s.toDouble() * s
            i += 2
        }
        val count = buf.size / 2
        if (count == 0) return 0f
        return (Math.sqrt(sum / count) / 32768.0).toFloat()
    }

    companion object {
        // Quanto esperar a thread da captura morrer. Ela so precisa terminar o
        // bloco de 10ms em que estiver; meio segundo e folga enorme, e existe pra
        // nao travar a interface se o driver do microfone engasgar no close().
        private const val FIM_MS = 500L

        // 48k/44.1k, mono depois estereo. O APM converte qualquer um pra 48k mono.
        private val FORMATS = listOf(
            AudioFormat(48000f, 16, 1, true, false),
            AudioFormat(44100f, 16, 1, true, false),
            AudioFormat(48000f, 16, 2, true, false),
            AudioFormat(44100f, 16, 2, true, false),
        )
    }
}

// Enumera dispositivos de audio. A ENTRADA (mic) vem do Java Sound; a SAIDA
// (alto-falante) so o WebRTC conhece, e o ADM da chamada so existe DURANTE uma
// chamada — por isso `outputs()` abre um modulo temporario e descarta. E o que
// permite listar dispositivos nas Configuracoes, fora de qualquer call.
object AudioDevices {
    fun inputs(): List<String> = runCatching {
        AudioSystem.getMixerInfo().filter { mi ->
            runCatching {
                AudioSystem.getMixer(mi).targetLineInfo.any { it.lineClass == TargetDataLine::class.java }
            }.getOrDefault(false)
        }.map { it.name }.distinct()
    }.getOrDefault(emptyList())

    fun outputs(): List<String> = runCatching {
        val m = AudioDeviceModule()
        val names = runCatching { m.playoutDevices.map { it.name } }.getOrDefault(emptyList())
        runCatching { m.dispose() }
        names.distinct()
    }.getOrDefault(emptyList())
}
