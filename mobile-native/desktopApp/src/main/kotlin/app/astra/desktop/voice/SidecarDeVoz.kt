package app.astra.desktop.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@Serializable
data class ComandoDeVoz(
    val cmd: String,
    val url: String? = null,
    val token: String? = null,
    val par: String? = null,
    val tipo: String? = null,
    val dados: String? = null,
    val ligado: Boolean? = null,
    val sentido: String? = null,
    val id: String? = null,
    val eco: Boolean? = null,
    val ruido: Boolean? = null,
    val ganho: Boolean? = null,
    val monitor: Int? = null,
    val janela: ULong? = null,
    val largura: Int? = null,
    val altura: Int? = null,
    val fps: Int? = null,
    val kbps: Int? = null,
    val volume: Int? = null,
)

@Serializable
data class AparelhoDeAudio(val id: String, val nome: String)

@Serializable
data class MonitorDaTela(
    val indice: Int,
    val nome: String,
    val largura: Int,
    val altura: Int,
    val principal: Boolean = false,
    val miniatura: String? = null,
)

@Serializable
data class JanelaDaTela(
    val id: ULong,
    val nome: String,
    val largura: Int,
    val altura: Int,
    val miniatura: String? = null,
)

@Serializable
data class LeituraDoCaminho(
    val ida: Int = 0,
    val pico: Int = 0,
    val tremor: Int = 0,
    val perda: Int = 0,
)

@Serializable
data class EventoDeVoz(
    val ev: String,
    val par: String? = null,
    val tipo: String? = null,
    val dados: String? = null,
    @SerialName("v") val valor: String? = null,
    val msg: String? = null,
    val aparelhos: List<AparelhoDeAudio>? = null,
    val monitores: List<MonitorDaTela>? = null,
    val janelas: List<JanelaDaTela>? = null,
    val caminho: LeituraDoCaminho? = null,
)

class SidecarDeVoz(private val scope: CoroutineScope) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private val _eventos = MutableSharedFlow<EventoDeVoz>(extraBufferCapacity = 256)
    val eventos = _eventos.asSharedFlow()

    private val processo = AtomicReference<Process?>(null)
    private val entrada = AtomicReference<BufferedWriter?>(null)

    val quadros = CanoDeQuadros()

    @Volatile private var querendoViver = false

    fun ligar() {
        if (querendoViver) return
        querendoViver = true
        scope.launch(Dispatchers.IO) { supervisionar() }
    }

    fun parar() {
        querendoViver = false
        quadros.fechar()
        mandar(ComandoDeVoz(cmd = "sair"))
        val p = processo.getAndSet(null)
        scope.launch(Dispatchers.IO) {
            if (p != null && !p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly()
            }
        }
    }

    fun entrarNaSala(url: String, token: String) =
        mandar(ComandoDeVoz(cmd = "sala", url = url, token = token))

    fun deixarSala() = mandar(ComandoDeVoz(cmd = "deixar"))

    fun mudo(on: Boolean) = mandar(ComandoDeVoz(cmd = "mudo", ligado = on))

    fun surdo(on: Boolean) = mandar(ComandoDeVoz(cmd = "surdo", ligado = on))

    fun tratamento(eco: Boolean, ruido: Boolean, ganho: Boolean) =
        mandar(ComandoDeVoz(cmd = "tratamento", eco = eco, ruido = ruido, ganho = ganho))

    fun transmitir(monitor: Int, largura: Int, altura: Int, fps: Int, kbps: Int) =
        mandar(
            ComandoDeVoz(
                cmd = "transmitir", ligado = true,
                monitor = monitor, largura = largura, altura = altura, fps = fps, kbps = kbps,
            ),
        )

    fun transmitirJanela(janela: ULong, largura: Int, altura: Int, fps: Int, kbps: Int) =
        mandar(
            ComandoDeVoz(
                cmd = "transmitir", ligado = true,
                janela = janela, largura = largura, altura = altura, fps = fps, kbps = kbps,
            ),
        )

    fun pararDeTransmitir() = mandar(ComandoDeVoz(cmd = "transmitir", ligado = false))

    fun pedirMonitores() = mandar(ComandoDeVoz(cmd = "monitores"))

    fun pedirJanelas() = mandar(ComandoDeVoz(cmd = "janelas"))

    fun assistir(par: String?) = mandar(ComandoDeVoz(cmd = "assistir", par = par.orEmpty()))

    fun volume(par: String, porcento: Int) =
        mandar(ComandoDeVoz(cmd = "volume", par = par, volume = porcento.coerceIn(0, 100)))

    fun pedirAparelhos() = mandar(ComandoDeVoz(cmd = "aparelhos"))

    fun usarAparelho(sentido: String, id: String?) =
        mandar(ComandoDeVoz(cmd = "usar", sentido = sentido, id = id.orEmpty()))

    private fun mandar(c: ComandoDeVoz): Boolean {
        val w = entrada.get() ?: return false
        return synchronized(w) {
            runCatching {
                w.write(json.encodeToString(c))
                w.newLine()
                w.flush()
            }.isSuccess
        }
    }

    private suspend fun supervisionar() {
        var esperaMs = 500L
        while (querendoViver && scope.isActive) {
            val exe = LocalizadorDoSidecar.caminho
            if (exe == null) {
                _eventos.tryEmit(EventoDeVoz(ev = "erro", msg = "o componente de voz não foi encontrado"))
                return
            }

            val codigo = runCatching { rodarUmaVez(exe) }.getOrElse { -1 }
            if (!querendoViver) return

            if (codigo == 0) return

            _eventos.tryEmit(
                EventoDeVoz(ev = "caiu", msg = "o componente de voz parou (código $codigo) e vai reiniciar"),
            )

            delay(esperaMs)
            esperaMs = (esperaMs * 2).coerceAtMost(15_000L)
        }
    }

    private suspend fun rodarUmaVez(exe: File): Int = withContext(Dispatchers.IO) {
        val construtor = ProcessBuilder(exe.absolutePath).directory(exe.parentFile)
        if (quadros.endereco.isNotEmpty()) {
            construtor.environment()["ASTRA_QUADROS"] = quadros.endereco
            construtor.environment()["ASTRA_QUADROS_SEGREDO"] = quadros.segredo
        }
        val p = construtor.start()
        processo.set(p)
        entrada.set(p.outputStream.bufferedWriter())

        launch(Dispatchers.IO) {
            runCatching {
                p.errorStream.bufferedReader().forEachLine { VoiceLog.nota("[voz] $it") }
            }
        }

        runCatching { lerEventos(p.inputStream.bufferedReader()) }

        val codigo = runCatching { p.waitFor() }.getOrDefault(-1)
        entrada.set(null)
        processo.compareAndSet(p, null)
        codigo
    }

    private fun lerEventos(saida: BufferedReader) {
        saida.forEachLine { linha ->
            if (linha.isBlank()) return@forEachLine
            val ev = runCatching { json.decodeFromString<EventoDeVoz>(linha) }.getOrNull()
            if (ev == null) {
                VoiceLog.nota("[voz] linha ilegível: $linha")
                return@forEachLine
            }
            _eventos.tryEmit(ev)
        }
    }
}

object LocalizadorDoSidecar {
    val caminho: File? by lazy { resolver() }

    private fun resolver(): File? {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return null
        val candidatos = buildList {
            System.getProperty("compose.application.resources.dir")
                ?.let { add(File(it, "astra-voz.exe")) }
            add(File("desktopApp/appResources/windows/astra-voz.exe"))
        }
        return candidatos.firstOrNull { it.isFile }
    }
}
