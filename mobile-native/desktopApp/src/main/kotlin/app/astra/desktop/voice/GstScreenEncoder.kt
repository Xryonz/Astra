package app.astra.desktop.voice

import com.sun.management.OperatingSystemMXBean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.freedesktop.gstreamer.ElementFactory
import org.freedesktop.gstreamer.Gst
import org.freedesktop.gstreamer.Pipeline
import org.freedesktop.gstreamer.State
import org.freedesktop.gstreamer.elements.AppSink
import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicInteger

// Captura de tela SEM tirar o quadro da placa de video.
//
// ESTE E O PONTO DA MIGRACAO INTEIRA, e nao o encoder — o que a medicao mostrou:
//
//   quadro fica na GPU do comeco ao fim   0,07 nucleos
//   quadro desce pra CPU + encoder de HW  0,68
//   quadro desce pra CPU + software       0,84   <- o caminho de hoje
//
// Trocar so o encoder e mantendo o vaivem rende 0,16 nucleo e nao paga uma migracao.
// Manter o quadro na GPU rende 0,77 — doze vezes mais barato. E e exatamente o que o
// Discord faz: ele nunca traz o quadro pra memoria principal.
//
// O caminho de hoje traz, e quatro vezes: o ffmpeg baixa da GPU (hwdownload), converte
// na CPU, empurra por um cano entre processos (~83 MB/s a 60fps), o Java le, copia pro
// NativeI420Buffer e so entao um encoder de SOFTWARE comprime. `d3d11screencapturesrc !
// d3d11convert ! <encoder de hardware>` apaga os quatro de uma vez.
//
// POR ORA ISTO SO MEDE. Nao ha transporte ligado: o proximo passo e trocar a
// PeerConnection de publicacao por webrtcbin, que e quem sabe receber H264 ja
// comprimido. Medir antes existe pra a pessoa do computador lento conseguir provar o
// ganho na maquina DELA antes de a gente reescrever a voz por causa dela.
object GstScreenEncoder {

    // Ordem de preferencia. Todos aceitam memoria D3D11 direto, que e a condicao pra o
    // quadro nao descer pra CPU. Encoder por software nao entra: com ele o caminho
    // inteiro perde a razao de existir.
    //
    // `nvd3d11h264enc`, e nao `nvh264enc`: o segundo e o modo CUDA da NVIDIA e recusa
    // quadro em memoria D3D11 (ver a lista comentada em GStreamerPack).
    private val ENCODERS = listOf("nvd3d11h264enc", "qsvh264enc", "amfh264enc", "mfh264enc")

    data class Medicao(
        val encoder: String,
        val fps: Double,
        val nucleos: Double,
        val nucleosParado: Double,
    )

    private fun primeiroEncoderDisponivel(): String? =
        ENCODERS.firstOrNull { runCatching { ElementFactory.find(it) != null }.getOrDefault(false) }

    // Roda o caminho de verdade por alguns segundos e devolve quanto custou.
    //
    // Mede em NUCLEOS e nao em porcentagem porque porcentagem depende de quantos nucleos
    // a maquina tem, e o objetivo e comparar maquinas diferentes. 0,07 nucleo e 0,07
    // nucleo num i3 e num Threadripper.
    suspend fun medir(http: OkHttpClient, segundos: Int = 8): Result<Medicao> {
        if (!GStreamerPack.garantir(http)) return Result.failure(IllegalStateException("pacote indisponivel"))
        return withContext(Dispatchers.IO) {
            if (!GStreamerPack.iniciarGst()) return@withContext Result.failure(IllegalStateException("GStreamer nao carregou"))
            val enc = primeiroEncoderDisponivel()
                ?: return@withContext Result.failure(IllegalStateException("sem encoder de hardware"))

            val os = runCatching {
                ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
            }.getOrNull() ?: return@withContext Result.failure(IllegalStateException("sem leitura de CPU"))

            // Linha de base ANTES de ligar nada: a aurora, a lista de mensagens e a
            // propria janela ja consomem. Sem descontar isso, o custo do pipeline sairia
            // inflado pelo custo de o app existir.
            val parado = medirNucleos(os, 2000)

            val pipeline = runCatching {
                Gst.parseLaunch(
                    "d3d11screencapturesrc ! d3d11convert ! " +
                        "video/x-raw(memory:D3D11Memory),format=NV12,width=1280,height=720,framerate=60/1 ! " +
                        "$enc ! h264parse ! appsink name=saida sync=false max-buffers=2 drop=true",
                ) as Pipeline
            }.getOrElse { return@withContext Result.failure(it) }

            val quadros = AtomicInteger(0)
            runCatching {
                (pipeline.getElementByName("saida") as AppSink).apply {
                    set("emit-signals", true)
                    connect(AppSink.NEW_SAMPLE { sink ->
                        // Puxar E descartar: sem o pull o appsink enche e trava o
                        // pipeline inteiro depois de max-buffers.
                        sink.pullSample()?.dispose()
                        quadros.incrementAndGet()
                        org.freedesktop.gstreamer.FlowReturn.OK
                    })
                }
            }

            try {
                pipeline.play()
                delay(1500) // negociar formato e acordar a GPU antes de medir
                quadros.set(0)
                val gasto = medirNucleos(os, segundos * 1000L)
                val fps = quadros.get().toDouble() / segundos
                Result.success(Medicao(enc, fps, (gasto - parado).coerceAtLeast(0.0), parado))
            } catch (t: Throwable) {
                Result.failure(t)
            } finally {
                runCatching { pipeline.setState(State.NULL) }
                runCatching { pipeline.dispose() }
            }
        }
    }

    private suspend fun medirNucleos(os: OperatingSystemMXBean, janelaMs: Long): Double {
        val cpu0 = os.processCpuTime
        val t0 = System.nanoTime()
        delay(janelaMs)
        val dCpu = os.processCpuTime - cpu0
        val dT = System.nanoTime() - t0
        return if (dT > 0) dCpu.toDouble() / dT else 0.0
    }
}
