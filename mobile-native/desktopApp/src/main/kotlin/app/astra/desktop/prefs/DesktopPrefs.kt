package app.astra.desktop.prefs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import app.astra.desktop.auth.SessionStore

// Qualidade da aurora = numero de oitavas do FBM no shader (custo dominante).
// SkSL exige bound de loop CONSTANTE -> não da uniform; recompila-se uma variante
// por nível (barato, so quando muda). HIGH=3, MEDIUM=2, LOW=1.
enum class AuroraQuality(val key: String, val octaves: Int) {
    HIGH("high", 3), MEDIUM("med", 2), LOW("low", 1);
    companion object {
        fun from(raw: String?) = entries.find { it.key == raw } ?: HIGH
    }
}

// Teto de FPS das animações de fundo (aurora/estrelas). LIVRE segue o vsync do
// monitor (144Hz de gamer = mais trabalho); 30 poupa GPU pro jogo. 0 = livre.
enum class UiFps(val key: String, val cap: Int) {
    FREE("free", 0), CAP60("60", 60), CAP30("30", 30);
    companion object {
        fun from(raw: String?) = entries.find { it.key == raw } ?: FREE
    }
}

// Presets da transmissão de tela (Settings > Voz). SO 720p, por decisao de perf: o
// encoder H264 do webrtc-java e por SOFTWARE (sem HW/NVENC) e 1080p não chega nem a
// 30fps na CPU — entao foco total em 720p, priorizando fluidez. Default = 720p60.
// bitrate em bits/s. Aplica ao INICIAR a transmissão. (Chaves antigas de 1080p caem
// no default via from() — quem tinha 1080p salvo sobe pro 720p60 suportado.)
enum class ScreenQuality(
    val key: String, val label: String,
    val width: Int, val height: Int, val fps: Int, val bitrate: Int,
) {
    SMOOTH_720_60("s72060", "720p 60fps — fluida", 1280, 720, 60, 4_000_000),
    LIGHT_720_30("l72030", "720p 30fps — leve", 1280, 720, 30, 2_500_000),

    // O degrau pra maquina fraca. Existe porque o custo do H264 por SOFTWARE e
    // praticamente constante em NUCLEOS: o mesmo encoder que ocupa 8% de um PC forte
    // ocupa mais da metade de um de quatro nucleos. 720p30 ja e metade do trabalho de
    // 720p60; 540p30 tira mais 44% dos pixels em cima disso.
    //
    // 960x540 e exatamente metade de 1080p em cada eixo, entao a reducao cai em
    // limite de pixel inteiro, e os dois lados sao pares (o I420 exige, porque o
    // croma anda de dois em dois).
    TINY_540_30("t54030", "540p 30fps — economica", 960, 540, 30, 1_200_000);
    companion object {
        fun from(raw: String?) = entries.find { it.key == raw } ?: padraoDaMaquina()

        // O preset de estreia sai da MAQUINA, e nao de um valor fixo.
        //
        // Todo mundo comecava em 720p60 e so descia depois de sofrer — e quem tem PC
        // fraco costuma ser exatamente quem nao sabe que existe uma tela de
        // configuracao pra mexer. A primeira transmissao dele era a ruim.
        //
        // O corte e por processador logico porque o encoder H264 por SOFTWARE custa
        // ~1,25 nucleo em 720p60, medido. Numa maquina de 4 threads isso e um terco do
        // computador so pra codificar, com o jogo, o navegador e o proprio Astra
        // disputando o resto. A escolha continua sendo do dono: isto e so o ponto de
        // partida de quem nunca escolheu.
        fun padraoDaMaquina(): ScreenQuality = when (Runtime.getRuntime().availableProcessors()) {
            in 0..4 -> TINY_540_30
            in 5..6 -> LIGHT_720_30
            else -> SMOOTH_720_60
        }
    }
}

// Como o microfone decide transmitir. O portão de sensibilidade (micSensitivity)
// continua valendo nos dois — ele corta silêncio; isto decide se há permissão pra
// falar em primeiro lugar.
enum class ModoDeFala(val key: String, val label: String) {
    VOZ("voz", "Transmitir quando eu falar"),
    APERTAR("apertar", "Apertar para falar");
    companion object {
        fun from(raw: String?) = entries.find { it.key == raw } ?: VOZ
    }
}

// Tamanho da fonte das mensagens (multiplicador). Espelha o FontSizePref do mobile.
enum class FontSizePref(val key: String, val label: String, val scale: Float) {
    SM("sm", "Pequena", 0.9f), MD("md", "Padrao", 1.0f), LG("lg", "Grande", 1.12f), XL("xl", "Maior", 1.25f);
    companion object {
        fun from(raw: String?) = entries.find { it.key == raw } ?: MD
    }
}

// Densidade das mensagens: respiro entre mensagens (topDp) e entre agrupadas
// (groupedTopDp). Espelha o DensityPref do mobile.
enum class DensityPref(val key: String, val label: String, val topDp: Int, val groupedTopDp: Int) {
    COMPACT("compact", "Compacta", 5, 1),
    COMFORTABLE("comfortable", "Confortavel", 10, 2),
    SPACIOUS("spacious", "Espacosa", 16, 4);
    companion object {
        fun from(raw: String?) = entries.find { it.key == raw } ?: COMFORTABLE
    }
}

// Preferencias LOCAIS do desktop (não vao pro backend): movimento, toasts da
// bandeja e agora DESEMPENHO/GRAFICOS. Persistem no ui.properties (mesmo arquivo
// da última selecao, que sobrevive a logout). StateFlow pra UI e shell reagirem
// na hora que muda.
class DesktopPrefs(private val store: SessionStore) {
    data class Prefs(
        // Reduz/desliga as animações de fundo (aurora, cascata, pulsos).
        val reduceMotion: Boolean = false,
        // Toast na bandeja quando chega DM / atividade de canal (janela oculta).
        val notifyDms: Boolean = true,
        val notifyChannels: Boolean = true,
        // Privacidade: mostrar aos outros o programa em primeiro plano.
        // NASCE DESLIGADO, e isso e a decisao mais importante do recurso. Recurso
        // que conta o que voce esta fazendo tem que ser um ato seu — ligado por
        // padrao ele seria uma coisa que aconteceu com voce, mesmo com interruptor
        // disponivel, porque quase ninguem visita a tela de configuracoes.
        val atividadeVisivel: Boolean = false,
        // --- Desempenho & Graficos ---
        // Modo desempenho: kill-switch gamer (aurora+estrelas OFF + reduz movimento).
        val performanceMode: Boolean = false,
        // DESLIGADAS por padrao (decisao do dono): o Astra abre com fundo liso, e
        // aurora/estrelas viram uma escolha em Aparencia > Fundo. Fundo animado
        // como padrao e uma opiniao forte cobrada de quem nunca pediu — e a conta
        // vem em GPU numa maquina que a gente nao conhece.
        val auroraEnabled: Boolean = false,
        // Padrao MEDIUM (decisao do dono): todos comecam nos graficos medios; quem
        // quiser sobe pra HIGH nas configs. So o valor INICIAL — escolha explicita
        // salva prevalece.
        val auroraQuality: AuroraQuality = AuroraQuality.MEDIUM,
        val starsEnabled: Boolean = false,
        // LIVRE, por decisao do dono: com o Astra na frente ele usa o processador e a
        // placa que precisar; o unico recurso com teto e a RAM. Quem paga a conta do
        // segundo plano e o gate de foco (Main.kt), nao um teto de fps. O ajuste continua
        // em Configuracoes > Desempenho pra quem tiver maquina apertada.
        val uiFps: UiFps = UiFps.FREE,
        // Janela translucida (cantos arredondados). Aplica ao REINICIAR (e param
        // de criacao da janela). Opaca = mais nitido/leve.
        val windowTransparent: Boolean = true,
        // Fechar o X ENCERRA o Astra de vez (sem bandeja/segundo plano). Default
        // false = minimiza pra bandeja (comportamento antigo). Ligado, tambem some
        // o icone da bandeja — zero presenca em segundo plano ao fechar.
        val exitOnClose: Boolean = false,
        // Acessibilidade: sobe texto e borda. Opt-in — o padrao e calibrado pra
        // sessao longa a noite (ver Obsidian.kt), e ninguem e empurrado pra ca.
        val altoContraste: Boolean = false,
        // --- Aparencia ---
        val accentId: String = "white",
        val bgId: String = "void",
        val fontSize: FontSizePref = FontSizePref.MD,
        val density: DensityPref = DensityPref.COMFORTABLE,
        // Placa de video preferida, pelo id de PCI (ver `Placas`). Vazio = automatico,
        // que e o certo pra quase todo mundo: o Astra usa a placa que desenha a tela,
        // que e a unica que consegue comprimir a captura dela.
        //
        // A parte do VIDEO vale na proxima transmissao; a parte da INTERFACE so no
        // proximo arranque, porque o Skiko le essa escolha uma vez, ao criar a janela.
        val placaVideo: String = "",
        // --- Voz & Transmissao ---
        val screenQuality: ScreenQuality = ScreenQuality.SMOOTH_720_60,
        // Motor de video novo (GStreamer publicando direto da placa). DESLIGADO por
        // padrao ate rodar em call de verdade: ele troca TUDO o que sai (microfone
        // inclusive), e um defeito aqui nao aparece como tela preta -- aparece como
        // ninguem te ouvindo. Ligado, ainda cai sozinho pro caminho de sempre se faltar
        // o pacote ou o encoder de hardware.
        val motorNovo: Boolean = false,
        // Processamento do microfone (aplica ao ENTRAR na próxima sala de voz).
        // Aviso SEM conteudo: nem quem escreveu, nem o que escreveu. Existe pra quem
        // transmite a tela -- o aviso da bandeja aparece POR CIMA de tudo, inclusive
        // do que esta sendo gravado.
        val avisoDiscreto: Boolean = false,
        // Modo transmissao: aviso sem conteudo + sem som + e-mail escondido. O
        // automatico e opt-in porque exige varrer a lista de processos.
        val modoTransmissao: Boolean = false,
        val modoTransmissaoAuto: Boolean = false,
        val micNoiseSuppression: Boolean = true,
        val micEchoCancel: Boolean = true,
        val micAutoGain: Boolean = true,
        // Sensibilidade de entrada (voice gate): 0 = sempre transmite; >0 = so
        // transmite quando o RMS (0..1) passa do limiar (com cauda de 250ms).
        val micSensitivity: Float = 0f,
        // Dispositivos da call (nome exato; null = padrao do sistema). Entrada =
        // mic (Java Sound); saida = alto-falante (ADM do WebRTC).
        val audioInput: String? = null,
        val audioOutput: String? = null,
        // COMO FALAR. Voz = transmite sempre (o portão de sensibilidade acima ainda
        // vale). Apertar = só transmite enquanto a tecla estiver segurada.
        val modoDeFala: ModoDeFala = ModoDeFala.VOZ,
        // Teclas de atalho GLOBAIS, em código virtual do Windows. 0 = nenhuma.
        // Ficam aqui e não no servidor porque são preferência de MÁQUINA: o teclado
        // é este, não a conta.
        val teclaFalar: Int = 0,
        val teclaMudo: Int = 0,
        val teclaEnsurdecer: Int = 0,
        // Emojis usados por ultimo, do mais recente pro mais antigo. Local e nao no
        // backend de proposito: e preferencia de MAQUINA (o teclado que voce usa
        // aqui), nao de conta — e sincronizar isso custaria uma escrita no servidor
        // a cada emoji clicado.
        val emojiRecentes: List<String> = emptyList(),
    ) {
        // Flags EFETIVAS que o shell consome: o modo desempenho sobrepoe.
        val auroraOn: Boolean get() = auroraEnabled && !performanceMode
        val starsOn: Boolean get() = starsEnabled && !performanceMode
        val reduceMotionEff: Boolean get() = reduceMotion || performanceMode
    }

    private val _state = MutableStateFlow(read())
    val state = _state.asStateFlow()

    init { migrarCeu() }

    // AJUSTE QUE NUNCA VALEU NAO E ESCOLHA — e por isso este remendo de uma vez so.
    //
    // O `LocalRenderPrefs` era provido dentro do ShellScreen, e o ceu (aurora + estrelas)
    // mora ACIMA dele na arvore desde que o login e o shell passaram a dividir o mesmo
    // fundo. CompositionLocal que nao acha provedor cai no default em silencio: nada
    // quebrou visivelmente, os dois ajustes so pararam de obedecer. Por tempo indefinido
    // a aurora desenhou em ALTA e sem teto de fps, qualquer que fosse o que estivesse
    // marcado na tela de Desempenho.
    //
    // Consertado o provedor, o valor gravado passaria a valer DE REPENTE — e o dono
    // veria o fundo mudar de aparencia sozinho, por uma escolha que ele fez uma vez, sem
    // efeito nenhum, e que portanto nunca foi testada por ele. Entao os dois voltam pro
    // padrao bom uma unica vez, marcado por `ceuMigrado`. A partir daqui a tela manda.
    //
    // Os dois voltam pro padrao: aurora ALTA (a que o dono sempre viu, porque era o
    // default que vinha valendo) e fps LIVRE — na frente o Astra nao tem teto de
    // processador nem de placa, so de RAM. O que segura o custo em segundo plano e o
    // gate de foco no Main.kt, e nao um teto que valeria tambem com o app na frente.
    // A marca e VERSIONADA porque esta migracao ja rodou uma vez com outro alvo: a 0.2.17
    // pos os fps em 60, e a 0.2.18 mudou a politica pra "sem teto na frente, gate de foco
    // atras". Marca booleana teria deixado quem instalou a 0.2.17 preso nos 60 pra sempre.
    private fun migrarCeu() {
        if (store.uiPref("ceuMigrado") == "2") return
        store.setUiPref("ceuMigrado", "2")
        store.setUiPref("auroraQuality", AuroraQuality.HIGH.key)
        store.setUiPref("uiFps", UiFps.FREE.key)
        _state.update { it.copy(auroraQuality = AuroraQuality.HIGH, uiFps = UiFps.FREE) }
    }

    // Ausente = default (toasts ligados; reduceMotion/perfMode desligados; aurora
    // e estrelas DESLIGADAS; qualidade media; fps livre; janela translucida).
    //
    // Repare na polaridade de aurora/estrelas: e `== "1"`, e nao `!= "0"`. A
    // diferenca importa pra quem ja usa o Astra — so quem LIGOU de proposito tem
    // "1" gravado e continua com o ceu; quem nunca abriu as configs passa a ver o
    // fundo liso. E o que o dono pediu, e sem apagar escolha de ninguem.
    private fun read() = Prefs(
        reduceMotion = store.uiPref("reduceMotion") == "1",
        notifyDms = store.uiPref("notifyDms") != "0",
        notifyChannels = store.uiPref("notifyChannels") != "0",
        // "1" e a UNICA forma de ligar. Ausente, vazio ou lixo = desligado — o
        // padrao seguro tem que valer tambem pro arquivo corrompido.
        atividadeVisivel = store.uiPref("atividadeVisivel") == "1",
        performanceMode = store.uiPref("performanceMode") == "1",
        auroraEnabled = store.uiPref("auroraEnabled") == "1",
        // MIGRACAO DE UMA VEZ (ver `migrarCeu` abaixo): o que estava gravado nestes dois
        // nunca chegou a valer, entao nao era escolha de ninguem.
        auroraQuality = store.uiPref("auroraQuality")?.let(AuroraQuality::from) ?: AuroraQuality.MEDIUM,
        starsEnabled = store.uiPref("starsEnabled") == "1",
        uiFps = UiFps.from(store.uiPref("uiFps")),
        windowTransparent = store.uiPref("windowTransparent") != "0",
        exitOnClose = store.uiPref("exitOnClose") == "1",
        altoContraste = store.uiPref("altoContraste") == "1",
        accentId = store.uiPref("accentId") ?: "white",
        bgId = store.uiPref("bgId") ?: "void",
        fontSize = FontSizePref.from(store.uiPref("fontSize")),
        density = DensityPref.from(store.uiPref("density")),
        placaVideo = store.uiPref("placaVideo").orEmpty(),
        screenQuality = ScreenQuality.from(store.uiPref("screenQuality")),
        avisoDiscreto = store.uiPref("avisoDiscreto") == "1",
        modoTransmissao = store.uiPref("modoTransmissao") == "1",
        modoTransmissaoAuto = store.uiPref("modoTransmissaoAuto") == "1",
        micNoiseSuppression = store.uiPref("micNoiseSuppression") != "0",
        micEchoCancel = store.uiPref("micEchoCancel") != "0",
        motorNovo = store.uiPref("motorNovo") == "1",
        micAutoGain = store.uiPref("micAutoGain") != "0",
        micSensitivity = store.uiPref("micSensitivity")?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f,
        audioInput = store.uiPref("audioInput")?.ifBlank { null },
        audioOutput = store.uiPref("audioOutput")?.ifBlank { null },
        modoDeFala = ModoDeFala.from(store.uiPref("modoDeFala")),
        teclaFalar = store.uiPref("teclaFalar")?.toIntOrNull() ?: 0,
        teclaMudo = store.uiPref("teclaMudo")?.toIntOrNull() ?: 0,
        teclaEnsurdecer = store.uiPref("teclaEnsurdecer")?.toIntOrNull() ?: 0,
        // Separados por espaco: nenhum emoji contem espaco, entao nao ha o que
        // escapar.
        emojiRecentes = store.uiPref("emojiRecentes")?.split(' ')?.filter { it.isNotBlank() } ?: emptyList(),
    )

    private fun persist(key: String, on: Boolean) = store.setUiPref(key, if (on) "1" else "0")

    fun setReduceMotion(v: Boolean) {
        persist("reduceMotion", v)
        _state.update { it.copy(reduceMotion = v) }
    }

    fun setNotifyDms(v: Boolean) {
        persist("notifyDms", v)
        _state.update { it.copy(notifyDms = v) }
    }

    fun setAtividadeVisivel(v: Boolean) {
        persist("atividadeVisivel", v)
        _state.update { it.copy(atividadeVisivel = v) }
    }

    fun setNotifyChannels(v: Boolean) {
        persist("notifyChannels", v)
        _state.update { it.copy(notifyChannels = v) }
    }

    fun setPerformanceMode(v: Boolean) {
        persist("performanceMode", v)
        _state.update { it.copy(performanceMode = v) }
    }

    fun setAuroraEnabled(v: Boolean) {
        persist("auroraEnabled", v)
        _state.update { it.copy(auroraEnabled = v) }
    }

    fun setAuroraQuality(v: AuroraQuality) {
        store.setUiPref("auroraQuality", v.key)
        _state.update { it.copy(auroraQuality = v) }
    }

    fun setStarsEnabled(v: Boolean) {
        persist("starsEnabled", v)
        _state.update { it.copy(starsEnabled = v) }
    }

    fun setUiFps(v: UiFps) {
        store.setUiPref("uiFps", v.key)
        _state.update { it.copy(uiFps = v) }
    }

    fun setWindowTransparent(v: Boolean) {
        persist("windowTransparent", v)
        _state.update { it.copy(windowTransparent = v) }
    }

    fun setAltoContraste(v: Boolean) {
        persist("altoContraste", v)
        _state.update { it.copy(altoContraste = v) }
    }

    fun setExitOnClose(v: Boolean) {
        persist("exitOnClose", v)
        _state.update { it.copy(exitOnClose = v) }
    }

    fun setPlacaVideo(id: String) {
        store.setUiPref("placaVideo", id)
        _state.update { it.copy(placaVideo = id) }
    }

    fun setScreenQuality(v: ScreenQuality) {
        store.setUiPref("screenQuality", v.key)
        _state.update { it.copy(screenQuality = v) }
    }

    fun setAccent(id: String) {
        store.setUiPref("accentId", id)
        _state.update { it.copy(accentId = id) }
    }

    fun setBg(id: String) {
        store.setUiPref("bgId", id)
        _state.update { it.copy(bgId = id) }
    }

    fun setTheme(accentId: String, bgId: String) {
        store.setUiPref("accentId", accentId)
        store.setUiPref("bgId", bgId)
        _state.update { it.copy(accentId = accentId, bgId = bgId) }
    }

    fun setFontSize(v: FontSizePref) {
        store.setUiPref("fontSize", v.key)
        _state.update { it.copy(fontSize = v) }
    }

    fun setDensity(v: DensityPref) {
        store.setUiPref("density", v.key)
        _state.update { it.copy(density = v) }
    }

    fun setModoTransmissao(v: Boolean) {
        persist("modoTransmissao", v)
        _state.update { it.copy(modoTransmissao = v) }
    }

    fun setModoTransmissaoAuto(v: Boolean) {
        persist("modoTransmissaoAuto", v)
        _state.update { it.copy(modoTransmissaoAuto = v) }
    }

    fun setAvisoDiscreto(v: Boolean) {
        persist("avisoDiscreto", v)
        _state.update { it.copy(avisoDiscreto = v) }
    }

    fun setMicNoiseSuppression(v: Boolean) {
        persist("micNoiseSuppression", v)
        _state.update { it.copy(micNoiseSuppression = v) }
    }

    fun setMicEchoCancel(v: Boolean) {
        persist("micEchoCancel", v)
        _state.update { it.copy(micEchoCancel = v) }
    }

    // Vale na PROXIMA call: o LiveKit da uma conexao de publicacao so, e trocar de
    // transporte com a chamada no ar seria derrubar e refazer tudo com a voz de alguem
    // em cima.
    fun setMotorNovo(v: Boolean) {
        persist("motorNovo", v)
        _state.update { it.copy(motorNovo = v) }
    }

    fun setMicAutoGain(v: Boolean) {
        persist("micAutoGain", v)
        _state.update { it.copy(micAutoGain = v) }
    }

    fun setMicSensitivity(v: Float) {
        val c = v.coerceIn(0f, 1f)
        store.setUiPref("micSensitivity", c.toString())
        _state.update { it.copy(micSensitivity = c) }
    }

    fun setAudioInput(v: String?) {
        store.setUiPref("audioInput", v ?: "")
        _state.update { it.copy(audioInput = v) }
    }

    fun setModoDeFala(v: ModoDeFala) {
        store.setUiPref("modoDeFala", v.key)
        _state.update { it.copy(modoDeFala = v) }
    }

    fun setTeclaFalar(vk: Int) {
        store.setUiPref("teclaFalar", vk.toString())
        _state.update { it.copy(teclaFalar = vk) }
    }

    fun setTeclaMudo(vk: Int) {
        store.setUiPref("teclaMudo", vk.toString())
        _state.update { it.copy(teclaMudo = vk) }
    }

    fun setTeclaEnsurdecer(vk: Int) {
        store.setUiPref("teclaEnsurdecer", vk.toString())
        _state.update { it.copy(teclaEnsurdecer = vk) }
    }

    fun setAudioOutput(v: String?) {
        store.setUiPref("audioOutput", v ?: "")
        _state.update { it.copy(audioOutput = v) }
    }

    // O emoji usado vai pro topo. Guarda poucos de proposito: "recentes" com 60
    // itens nao e recente, e a linha do seletor mostra uma fileira so.
    fun registrarEmoji(glifo: String) {
        val nova = (listOf(glifo) + _state.value.emojiRecentes.filter { it != glifo }).take(TETO_RECENTES)
        store.setUiPref("emojiRecentes", nova.joinToString(" "))
        _state.update { it.copy(emojiRecentes = nova) }
    }

    private companion object {
        const val TETO_RECENTES = 24
    }
}
