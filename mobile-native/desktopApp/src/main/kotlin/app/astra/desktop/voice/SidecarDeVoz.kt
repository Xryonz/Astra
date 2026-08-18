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

// O SIDECAR DE VOZ — o processo à parte que carrega a mídia.
//
// POR QUE ELE EXISTE. A voz do Astra vivia dentro da JVM, falando com objetos
// nativos por JNI. Objeto nativo liberado enquanto outra thread ainda o usa não
// lança exceção em Kotlin: derruba o processo inteiro. Na prática, abrir uma call
// podia fechar o Astra, e encerrar uma call também — inclusive em máquina folgada,
// o que descartou "falta de recurso" como explicação.
//
// Num processo separado, o pior caso de um defeito de mídia deixa de ser "o Astra
// fechou" e passa a ser "a call caiu e voltou". A conversa em texto, os servidores
// e as janelas abertas continuam de pé. Essa é a razão principal desta classe
// existir, e ela vale mesmo que o código do outro lado tenha os próprios defeitos —
// defeitos vão existir de qualquer jeito, e o que muda é o tamanho do estrago.
//
// O PROTOCOLO é uma linha de JSON por mensagem, pela entrada e saída padrão. Sem
// porta de rede: um socket local acorda o Firewall do Windows, e ninguém merece um
// alerta de segurança por causa de um app de conversa. A entrada padrão fechando
// também mata o processo filho sozinha — Astra fechado não deixa sidecar órfão
// segurando o microfone.

@Serializable
data class ComandoDeVoz(
    val cmd: String,
    val stun: List<String>? = null,
    val par: String? = null,
    val iniciar: Boolean? = null,
    val tipo: String? = null,
    val dados: String? = null,
    val ligado: Boolean? = null,
)

@Serializable
data class EventoDeVoz(
    val ev: String,
    val par: String? = null,
    val tipo: String? = null,
    val dados: String? = null,
    @SerialName("v") val valor: String? = null,
    val msg: String? = null,
)

class SidecarDeVoz(private val scope: CoroutineScope) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private val _eventos = MutableSharedFlow<EventoDeVoz>(extraBufferCapacity = 256)
    val eventos = _eventos.asSharedFlow()

    private val processo = AtomicReference<Process?>(null)
    private val entrada = AtomicReference<BufferedWriter?>(null)

    @Volatile private var querendoViver = false

    // Sobe o processo e mantém ele vivo até `parar()`.
    fun ligar() {
        if (querendoViver) return
        querendoViver = true
        scope.launch(Dispatchers.IO) { supervisionar() }
    }

    fun parar() {
        querendoViver = false
        // "sair" primeiro, e destruir depois só se ele não obedecer: o desligamento
        // limpo solta microfone e conexões na ordem certa. Matar direto deixa o
        // aparelho de áudio preso por alguns segundos, e a próxima call abre com
        // erro de dispositivo ocupado.
        mandar(ComandoDeVoz(cmd = "sair"))
        val p = processo.getAndSet(null)
        scope.launch(Dispatchers.IO) {
            if (p != null && !p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly()
            }
        }
    }

    // ---- comandos ----

    fun configurar(stun: List<String>) =
        mandar(ComandoDeVoz(cmd = "config", stun = stun))

    // `iniciar` decide quem faz a oferta, e a decisão é DETERMINÍSTICA: quem tem o
    // id menor oferece. Isso resolve o encontro de duas ofertas sem precisar de
    // combinação em tempo real — os dois lados chegam à mesma conclusão sozinhos.
    fun conectar(meuId: String, outroId: String) =
        mandar(ComandoDeVoz(cmd = "conectar", par = outroId, iniciar = meuId < outroId))

    fun repassarSinal(de: String, tipo: String, dados: String) =
        mandar(ComandoDeVoz(cmd = "sinal", par = de, tipo = tipo, dados = dados))

    fun desconectar(outroId: String) =
        mandar(ComandoDeVoz(cmd = "desconectar", par = outroId))

    fun mudo(on: Boolean) = mandar(ComandoDeVoz(cmd = "mudo", ligado = on))

    fun surdo(on: Boolean) = mandar(ComandoDeVoz(cmd = "surdo", ligado = on))

    // DEVOLVE SE A ORDEM SAIU MESMO, e quem chama precisa disso.
    //
    // O processo demora um instante para subir, e comando mandado antes disso não
    // vai a lugar nenhum. Engolir esse caso em silêncio foi o que fez a malha achar
    // que tinha conectado em gente que nunca recebeu ordem nenhuma — e como o
    // conjunto de conectados já continha a pessoa, nenhuma conferência tentava de
    // novo. Um par mudo pelo resto da call, sem erro em lugar nenhum.
    private fun mandar(c: ComandoDeVoz): Boolean {
        val w = entrada.get() ?: return false
        // Escrita SINCRONIZADA no escritor: os comandos nascem em várias
        // corrotinas (botão de mudo, socket, navegação) e duas escritas
        // concorrentes intercalariam bytes no meio de uma linha. O outro lado lê
        // linha a linha — meia linha de JSON é um erro que só aparece sob carga.
        return synchronized(w) {
            runCatching {
                w.write(json.encodeToString(c))
                w.newLine()
                w.flush()
            }.isSuccess
        }
    }

    // ---- supervisão ----

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

            // Saída com 0 é desligamento pedido; qualquer outra coisa é queda.
            if (codigo == 0) return

            _eventos.tryEmit(
                EventoDeVoz(ev = "caiu", msg = "o componente de voz parou (código $codigo) e vai reiniciar"),
            )

            // Espera CRESCENTE, com teto. Sem o crescimento, um defeito que impede
            // o processo de subir viraria um laço de milhares de execuções por
            // minuto — que aquece a máquina e enche o disco de log sem nunca
            // consertar nada.
            delay(esperaMs)
            esperaMs = (esperaMs * 2).coerceAtMost(15_000L)
        }
    }

    private suspend fun rodarUmaVez(exe: File): Int = withContext(Dispatchers.IO) {
        val p = ProcessBuilder(exe.absolutePath)
            .directory(exe.parentFile)
            .start()
        processo.set(p)
        entrada.set(p.outputStream.bufferedWriter())

        // A saída de ERRO vai pro registro, não pro protocolo. Misturar as duas
        // quebraria o leitor de linha — e é justamente na hora de um defeito que a
        // saída de erro tem mais a dizer.
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
                // Linha ilegível não derruba a leitura: é a única via de comunicação
                // com a voz, e fechá-la por causa de um JSON torto tiraria a call do
                // ar por um erro de formatação.
                VoiceLog.nota("[voz] linha ilegível: $linha")
                return@forEachLine
            }
            _eventos.tryEmit(ev)
        }
    }
}

// Acha o executável do sidecar. Mesmo padrão do FfmpegLocator: o Compose copia
// `appResources/windows/*` pro diretório de recursos do app empacotado; rodando
// pelo Gradle, cai no appResources do módulo.
object LocalizadorDoSidecar {
    val caminho: File? by lazy { resolver() }

    private fun resolver(): File? {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return null
        val candidatos = buildList {
            System.getProperty("compose.application.resources.dir")
                ?.let { add(File(it, "astra-voz.exe")) }
            // Dev (gradle run): o diretório corrente é a raiz do projeto Gradle.
            add(File("desktopApp/appResources/windows/astra-voz.exe"))
        }
        return candidatos.firstOrNull { it.isFile }
    }
}
