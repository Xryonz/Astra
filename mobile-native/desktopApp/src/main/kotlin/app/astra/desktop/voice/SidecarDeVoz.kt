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
    val sentido: String? = null,
    val id: String? = null,
    // Os três do comando `tratamento`. Ver a função de mesmo nome.
    val eco: Boolean? = null,
    val ruido: Boolean? = null,
    val ganho: Boolean? = null,
    // O preset do comando `transmitir`.
    val monitor: Int? = null,
    val largura: Int? = null,
    val altura: Int? = null,
    val fps: Int? = null,
    val kbps: Int? = null,
)

// Um microfone ou uma saída, do jeito que o processo de voz os enxerga.
//
// O `id` é o identificador do Windows, e é ele que viaja e é guardado — não o nome.
// Nome muda com atualização de driver e é repetido entre aparelhos iguais; o
// identificador é estável e único, e é por isso que a preferência guarda ele.
@Serializable
data class AparelhoDeAudio(val id: String, val nome: String)

@Serializable
data class EventoDeVoz(
    val ev: String,
    val par: String? = null,
    val tipo: String? = null,
    val dados: String? = null,
    @SerialName("v") val valor: String? = null,
    val msg: String? = null,
    val aparelhos: List<AparelhoDeAudio>? = null,
)

class SidecarDeVoz(private val scope: CoroutineScope) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private val _eventos = MutableSharedFlow<EventoDeVoz>(extraBufferCapacity = 256)
    val eventos = _eventos.asSharedFlow()

    private val processo = AtomicReference<Process?>(null)
    private val entrada = AtomicReference<BufferedWriter?>(null)

    // POR ONDE A IMAGEM CHEGA — cano à parte, porque um quadro de 720p (1,4 MB) na mesma
    // fila que carrega "fulano está falando" faria o aviso esperar atrás da imagem.
    //
    // Nasce com este objeto e SOBREVIVE À QUEDA do processo: a porta continua a mesma, e
    // o processo que reinicia recebe o mesmo endereço no ambiente e religa sozinho.
    val quadros = CanoDeQuadros()

    @Volatile private var querendoViver = false

    // Sobe o processo e mantém ele vivo até `parar()`.
    fun ligar() {
        if (querendoViver) return
        querendoViver = true
        scope.launch(Dispatchers.IO) { supervisionar() }
    }

    fun parar() {
        querendoViver = false
        quadros.fechar()
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

    // OS TRÊS AJUSTES DO MICROFONE NUM COMANDO SÓ, e isso é do Windows, não nosso: os
    // três moram no mesmo objeto (o cancelador de eco), e mudar qualquer um obriga a
    // reabrir a fonte — alguns quadros de silêncio. Mandados separados, mexer em dois
    // interruptores seguidos cortaria o som duas vezes.
    //
    // O lado de lá ignora o comando quando nada mudou de verdade.
    fun tratamento(eco: Boolean, ruido: Boolean, ganho: Boolean) =
        mandar(ComandoDeVoz(cmd = "tratamento", eco = eco, ruido = ruido, ganho = ganho))

    // Transmitir a tela. Mandar de novo com `ligado` troca o preset em pleno ar — o
    // lado de lá desliga o laço e sobe outro, que é o mesmo caminho de sempre.
    //
    // NÃO DEVOLVE SE DEU CERTO, e não é descuido: montar a captura e o compressor leva
    // quase um segundo, e a ponte é assíncrona. Quem responde é o evento `transmissao`
    // — ou o `erro`, quando a máquina não tem compressor de H.264.
    fun transmitir(monitor: Int, largura: Int, altura: Int, fps: Int, kbps: Int) =
        mandar(
            ComandoDeVoz(
                cmd = "transmitir", ligado = true,
                monitor = monitor, largura = largura, altura = altura, fps = fps, kbps = kbps,
            ),
        )

    fun pararDeTransmitir() = mandar(ComandoDeVoz(cmd = "transmitir", ligado = false))

    // Pede a lista dos dois sentidos. A resposta não volta aqui: chega como dois
    // eventos `aparelhos`, um por sentido, porque a ponte é assíncrona por natureza
    // e fingir que isto é uma chamada com retorno esconderia essa verdade.
    fun pedirAparelhos() = mandar(ComandoDeVoz(cmd = "aparelhos"))

    // `id` vazio devolve o aparelho de comunicação padrão do Windows.
    fun usarAparelho(sentido: String, id: String?) =
        mandar(ComandoDeVoz(cmd = "usar", sentido = sentido, id = id.orEmpty()))

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
        val construtor = ProcessBuilder(exe.absolutePath).directory(exe.parentFile)
        // O CANO DE QUADROS VIAJA NO AMBIENTE, e por isso não há nada a descobrir depois:
        // a porta já está aberta deste lado quando o processo nasce, então ele pode ligar
        // no primeiro quadro que tiver. Anunciar pela ponte criaria uma corrida entre o
        // anúncio e o nosso leitor — e essa corrida voltaria a cada queda do processo.
        construtor.environment()["ASTRA_QUADROS"] = quadros.endereco
        construtor.environment()["ASTRA_QUADROS_SEGREDO"] = quadros.segredo
        val p = construtor.start()
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
