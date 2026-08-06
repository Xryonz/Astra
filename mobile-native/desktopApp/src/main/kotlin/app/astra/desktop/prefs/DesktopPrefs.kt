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
    LIGHT_720_30("l72030", "720p 30fps — leve", 1280, 720, 30, 2_500_000);
    companion object {
        fun from(raw: String?) = entries.find { it.key == raw } ?: SMOOTH_720_60
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
        // --- Desempenho & Graficos ---
        // Modo desempenho: kill-switch gamer (aurora+estrelas OFF + reduz movimento).
        val performanceMode: Boolean = false,
        val auroraEnabled: Boolean = true,
        // Padrao MEDIUM (decisao do dono): todos comecam nos graficos medios; quem
        // quiser sobe pra HIGH nas configs. So o valor INICIAL — escolha explicita
        // salva prevalece.
        val auroraQuality: AuroraQuality = AuroraQuality.MEDIUM,
        val starsEnabled: Boolean = true,
        val uiFps: UiFps = UiFps.FREE,
        // Janela translucida (cantos arredondados). Aplica ao REINICIAR (e param
        // de criacao da janela). Opaca = mais nitido/leve.
        val windowTransparent: Boolean = true,
        // Fechar o X ENCERRA o Astra de vez (sem bandeja/segundo plano). Default
        // false = minimiza pra bandeja (comportamento antigo). Ligado, tambem some
        // o icone da bandeja — zero presenca em segundo plano ao fechar.
        val exitOnClose: Boolean = false,
        // --- Aparencia ---
        val accentId: String = "white",
        val bgId: String = "void",
        val fontSize: FontSizePref = FontSizePref.MD,
        val density: DensityPref = DensityPref.COMFORTABLE,
        // --- Voz & Transmissao ---
        val screenQuality: ScreenQuality = ScreenQuality.SMOOTH_720_60,
        // Processamento do microfone (aplica ao ENTRAR na próxima sala de voz).
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

    // Ausente = default (toasts ligados; reduceMotion/perfMode desligados; aurora
    // e estrelas ligadas; qualidade alta; fps livre; janela translucida).
    private fun read() = Prefs(
        reduceMotion = store.uiPref("reduceMotion") == "1",
        notifyDms = store.uiPref("notifyDms") != "0",
        notifyChannels = store.uiPref("notifyChannels") != "0",
        performanceMode = store.uiPref("performanceMode") == "1",
        auroraEnabled = store.uiPref("auroraEnabled") != "0",
        auroraQuality = store.uiPref("auroraQuality")?.let(AuroraQuality::from) ?: AuroraQuality.MEDIUM,
        starsEnabled = store.uiPref("starsEnabled") != "0",
        uiFps = UiFps.from(store.uiPref("uiFps")),
        windowTransparent = store.uiPref("windowTransparent") != "0",
        exitOnClose = store.uiPref("exitOnClose") == "1",
        accentId = store.uiPref("accentId") ?: "white",
        bgId = store.uiPref("bgId") ?: "void",
        fontSize = FontSizePref.from(store.uiPref("fontSize")),
        density = DensityPref.from(store.uiPref("density")),
        screenQuality = ScreenQuality.from(store.uiPref("screenQuality")),
        micNoiseSuppression = store.uiPref("micNoiseSuppression") != "0",
        micEchoCancel = store.uiPref("micEchoCancel") != "0",
        micAutoGain = store.uiPref("micAutoGain") != "0",
        micSensitivity = store.uiPref("micSensitivity")?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f,
        audioInput = store.uiPref("audioInput")?.ifBlank { null },
        audioOutput = store.uiPref("audioOutput")?.ifBlank { null },
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

    fun setExitOnClose(v: Boolean) {
        persist("exitOnClose", v)
        _state.update { it.copy(exitOnClose = v) }
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

    fun setMicNoiseSuppression(v: Boolean) {
        persist("micNoiseSuppression", v)
        _state.update { it.copy(micNoiseSuppression = v) }
    }

    fun setMicEchoCancel(v: Boolean) {
        persist("micEchoCancel", v)
        _state.update { it.copy(micEchoCancel = v) }
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
