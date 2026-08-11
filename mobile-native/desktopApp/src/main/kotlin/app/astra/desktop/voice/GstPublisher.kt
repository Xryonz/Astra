package app.astra.desktop.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.freedesktop.gstreamer.ElementFactory
import org.freedesktop.gstreamer.Gst
import org.freedesktop.gstreamer.Pipeline
import org.freedesktop.gstreamer.State
import org.freedesktop.gstreamer.webrtc.WebRTCBin
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

// O TRANSPORTE novo, ainda em ensaio: quem publica video deixa de ser a PeerConnection
// do webrtc-java e passa a ser o `webrtcbin` do GStreamer.
//
// POR QUE TROCAR O TRANSPORTE, e nao so a captura:
//
// O perfil por thread, com o dono transmitindo a 720p60, deu isto (em nucleos):
//
//   ffmpeg-cap        0,912   ler o cano e copiar quadro pro WebRTC
//   ffmpeg.exe        1,095   processo a parte: capturar, baixar da GPU, converter
//   nativa/encoder    0,258   COMPRIMIR de verdade
//   ffmpeg-preview    0,183   a previa da propria janela
//
// Dois nucleos inteiros MOVENDO PIXEL, um quarto de nucleo comprimindo. O trabalho que
// o app existe pra fazer e o item mais barato da lista.
//
// So que esses dois nucleos existem por causa da fronteira: o webrtc-java so aceita
// quadro cru em memoria principal (`CustomVideoSource.pushFrame`), entao o quadro TEM
// que descer da placa de video. Trocar so a captura por GStreamer mantem a fronteira e
// devolve metade. Quem apaga os dois e o webrtcbin, porque ele aceita H264 JA
// COMPRIMIDO — e ai o quadro nasce, e morre, dentro da placa.
//
// O QUE ESTE ARQUIVO FAZ HOJE: monta o caminho inteiro, gera a oferta e olha pra ela.
// Nao manda nada pra lugar nenhum e nao encosta no VoiceEngine — ligar isso numa call
// e a fatia seguinte. Negociacao WebRTC falha de formas silenciosas e chatas, e
// descobrir isso com uma chamada em curso seria descobrir do pior jeito possivel.
object GstPublisher {

    // Ordem de preferencia. Todos aceitam memoria D3D11 direto (a condicao pra o quadro
    // nao descer pra CPU). Encoder por software nao entra: com ele o caminho inteiro
    // perde a razao de existir.
    private val ENCODERS = listOf("nvh264enc", "qsvh264enc", "amfh264enc", "mfh264enc")

    data class Ensaio(
        val encoder: String,
        val temFingerprint: Boolean,
        val temUfrag: Boolean,
        val midias: List<String>,
        val candidatos: Int,
        val linhaMsid: String?,
        val cidNaOferta: Boolean,
    )

    // Monta o caminho, pede a oferta e confere o que veio.
    //
    // Tres coisas na oferta valem mais que o resto, e nenhuma delas o `gst-inspect`
    // consegue responder — ele le metadado de plugin, nao roda criptografia:
    //
    //   a=fingerprint  o certificado DTLS foi gerado => o OpenSSL do pacote esta vivo
    //   a=ice-ufrag    a sessao ICE subiu            => a libnice do pacote esta viva
    //   candidatos     as interfaces foram lidas     => o ICE chega a juntar caminho
    //
    // Sao exatamente as tres que o encolhimento do pacote poderia ter quebrado sem dar
    // sinal, porque so entram em cena quando uma chamada comeca.
    suspend fun ensaiar(http: OkHttpClient, segundos: Int = 5): Result<Ensaio> {
        if (!GStreamerPack.garantir(http)) {
            return Result.failure(IllegalStateException("pacote indisponivel"))
        }
        return withContext(Dispatchers.IO) {
            if (!GStreamerPack.iniciarGst()) {
                return@withContext Result.failure(IllegalStateException("GStreamer nao carregou"))
            }
            val enc = ENCODERS.firstOrNull { runCatching { ElementFactory.find(it) != null }.getOrDefault(false) }
                ?: return@withContext Result.failure(IllegalStateException("sem encoder de hardware"))

            // O `cid` e o nome que o LiveKit usa pra casar a faixa que chega pelo SDP com
            // o AddTrackRequest que foi mandado pela sinalizacao. Hoje o webrtc-java bota
            // o mesmo valor no id da faixa E no id do fluxo (ver attachScreen). No
            // webrtcbin quem controla isso e a propriedade `msid` do pad de entrada —
            // este ensaio existe em boa parte pra confirmar que ela chega no SDP.
            val cid = "screen-" + UUID.randomUUID().toString().take(8)

            // config-interval=-1 nos DOIS: o WebRTC exige que SPS/PPS voltem junto de todo
            // quadro-chave. Sem isso quem entra no meio da transmissao ve tela preta ate o
            // proximo, e "ate o proximo" pode ser meio minuto.
            val descricao =
                "d3d11screencapturesrc ! d3d11convert ! " +
                    "video/x-raw(memory:D3D11Memory),format=NV12,width=1280,height=720,framerate=30/1 ! " +
                    "$enc ! h264parse config-interval=-1 ! " +
                    "rtph264pay pt=96 config-interval=-1 aggregate-mode=zero-latency ! " +
                    "application/x-rtp,media=video,encoding-name=H264,payload=96 ! " +
                    // max-bundle porque o LiveKit so fala assim: uma conexao para todas as
                    // faixas. O padrao do webrtcbin e "none", que abriria uma por faixa e
                    // seria recusado na negociacao.
                    "webrtcbin name=envio bundle-policy=max-bundle latency=0"

            val pipeline = runCatching { Gst.parseLaunch(descricao) as Pipeline }
                .getOrElse { return@withContext Result.failure(it) }

            val bin = runCatching { pipeline.getElementByName("envio") as WebRTCBin }
                .getOrElse {
                    runCatching { pipeline.dispose() }
                    return@withContext Result.failure(IllegalStateException("webrtcbin nao veio tipado: ${it.javaClass.simpleName}"))
                }

            // SEM servidor STUN, de proposito. Candidato local ja prova o que se quer
            // provar aqui (a libnice subiu e leu as interfaces), e um STUN publico faria
            // este botao mandar pacote pra terceiro que o app nao usa em mais lugar
            // nenhum. Na hora de valer, os servidores vem do JoinResponse do LiveKit,
            // como ja acontece com a conexao de hoje.
            val candidatos = ConcurrentLinkedQueue<String>()
            runCatching { bin.connect(WebRTCBin.ON_ICE_CANDIDATE { _, cand -> candidatos.add(cand) }) }

            // O pad ja existe: o parseLaunch pediu ele ao ligar o payloader.
            runCatching { bin.getStaticPad("sink_0")?.set("msid", cid) }

            val sdp = ConcurrentLinkedQueue<String>()
            try {
                pipeline.play()
                delay(800) // negociar formato com a placa antes de descrever a sessao

                bin.createOffer { oferta ->
                    // setLocalDescription e o que DISPARA a coleta de candidatos. Sem ele
                    // o ICE nem comeca, e o ensaio mediria silencio.
                    runCatching { bin.setLocalDescription(oferta) }
                    runCatching { sdp.add(oferta.getSDPMessage().toString()) }
                }

                // A oferta fica pronta em milissegundos; a espera aqui e pela coleta de
                // candidatos, que e assincrona e vai pingando por alguns segundos.
                delay(segundos * 1000L)

                val texto = sdp.firstOrNull()
                    ?: return@withContext Result.failure(IllegalStateException("a oferta nao ficou pronta"))

                // A LINHA QUE DECIDE A MIGRACAO INTEIRA.
                //
                // O LiveKit casa a faixa que chega pelo SDP com o AddTrackRequest pelo
                // `cid`, e o `cid` viaja dentro do `a=msid:<fluxo> <faixa>`. O
                // webrtc-java bota o mesmo valor nos dois campos, entao hoje casa de
                // qualquer jeito. Ja o webrtcbin so deixa escolher o campo do FLUXO — o
                // da faixa ele gera sozinho.
                //
                // Por isso a linha crua vai pro resultado, e nao um "sim/nao": se o
                // segundo campo nao for o `cid`, a migracao precisa reescrever essa linha
                // do SDP antes de mandar a oferta. Melhor descobrir agora, num botao, do
                // que numa call que sobe e nao mostra nada.
                val linhaMsid = texto.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("a=msid:") }

                Result.success(
                    Ensaio(
                        encoder = enc,
                        temFingerprint = texto.contains("a=fingerprint:"),
                        temUfrag = texto.contains("a=ice-ufrag:"),
                        midias = texto.lineSequence().filter { it.startsWith("m=") }.map { it.trim() }.toList(),
                        candidatos = candidatos.size,
                        linhaMsid = linhaMsid,
                        cidNaOferta = linhaMsid?.contains(cid) == true,
                    ),
                )
            } catch (t: Throwable) {
                Result.failure(t)
            } finally {
                runCatching { pipeline.setState(State.NULL) }
                runCatching { pipeline.dispose() }
            }
        }
    }
}
