package app.astra.desktop.prefs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import app.astra.desktop.auth.SessionStore

// Qualidade da aurora = numero de oitavas do FBM no shader (custo dominante).
// SkSL exige bound de loop CONSTANTE -> nao da uniform; recompila-se uma variante
// por nivel (barato, so quando muda). HIGH=3, MEDIUM=2, LOW=1.
enum class AuroraQuality(val key: String, val octaves: Int) {
    HIGH("high", 3), MEDIUM("med", 2), LOW("low", 1);
    companion object {
        fun from(raw: String?) = entries.find { it.key == raw } ?: HIGH
    }
}

// Teto de FPS das animacoes de fundo (aurora/estrelas). LIVRE segue o vsync do
// monitor (144Hz de gamer = mais trabalho); 30 poupa GPU pro jogo. 0 = livre.
enum class UiFps(val key: String, val cap: Int) {
    FREE("free", 0), CAP60("60", 60), CAP30("30", 30);
    companion object {
        fun from(raw: String?) = entries.find { it.key == raw } ?: FREE
    }
}

// Presets da transmissao de tela (Settings > Voz). Default = 1080p60 (o requisito
// original do dono: 60fps no minimo); os 30fps sao opt-in pra pouca banda. bitrate
// em bits/s. Aplica ao INICIAR a transmissao.
enum class ScreenQuality(
    val key: String, val label: String,
    val width: Int, val height: Int, val fps: Int, val bitrate: Int,
) {
    HIGH_1080_60("h108060", "1080p 60fps — alta", 1920, 1080, 60, 8_000_000),
    SMOOTH_720_60("s72060", "720p 60fps — fluida", 1280, 720, 60, 4_000_000),
    CRISP_1080_30("c108030", "1080p 30fps — nitida", 1920, 1080, 30, 6_000_000),
    LIGHT_720_30("l72030", "720p 30fps — leve", 1280, 720, 30, 2_500_000);
    companion object {
        fun from(raw: String?) = entries.find { it.key == raw } ?: HIGH_1080_60
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

// Preferencias LOCAIS do desktop (nao vao pro backend): movimento, toasts da
// bandeja e agora DESEMPENHO/GRAFICOS. Persistem no ui.properties (mesmo arquivo
// da ultima selecao, que sobrevive a logout). StateFlow pra UI e shell reagirem
// na hora que muda.
class DesktopPrefs(private val store: SessionStore) {
    data class Prefs(
        // Reduz/desliga as animacoes de fundo (aurora, cascata, pulsos).
        val reduceMotion: Boolean = false,
        // Toast na bandeja quando chega DM / atividade de canal (janela oculta).
        val notifyDms: Boolean = true,
        val notifyChannels: Boolean = true,
        // --- Desempenho & Graficos ---
        // Modo desempenho: kill-switch gamer (aurora+estrelas OFF + reduz movimento).
        val performanceMode: Boolean = false,
        val auroraEnabled: Boolean = true,
        val auroraQuality: AuroraQuality = AuroraQuality.HIGH,
        val starsEnabled: Boolean = true,
        val uiFps: UiFps = UiFps.FREE,
        // Janela translucida (cantos arredondados). Aplica ao REINICIAR (e param
        // de criacao da janela). Opaca = mais nitido/leve.
        val windowTransparent: Boolean = true,
        // --- Aparencia ---
        val accentId: String = "white",
        val bgId: String = "void",
        val fontSize: FontSizePref = FontSizePref.MD,
        val density: DensityPref = DensityPref.COMFORTABLE,
        // --- Voz & Transmissao ---
        val screenQuality: ScreenQuality = ScreenQuality.HIGH_1080_60,
        // Processamento do microfone (aplica ao ENTRAR na proxima sala de voz).
        val micNoiseSuppression: Boolean = true,
        val micEchoCancel: Boolean = true,
        val micAutoGain: Boolean = true,
        // Dispositivos da call (nome exato; null = padrao do sistema). Entrada =
        // mic (Java Sound); saida = alto-falante (ADM do WebRTC).
        val audioInput: String? = null,
        val audioOutput: String? = null,
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
        auroraQuality = AuroraQuality.from(store.uiPref("auroraQuality")),
        starsEnabled = store.uiPref("starsEnabled") != "0",
        uiFps = UiFps.from(store.uiPref("uiFps")),
        windowTransparent = store.uiPref("windowTransparent") != "0",
        accentId = store.uiPref("accentId") ?: "white",
        bgId = store.uiPref("bgId") ?: "void",
        fontSize = FontSizePref.from(store.uiPref("fontSize")),
        density = DensityPref.from(store.uiPref("density")),
        screenQuality = ScreenQuality.from(store.uiPref("screenQuality")),
        micNoiseSuppression = store.uiPref("micNoiseSuppression") != "0",
        micEchoCancel = store.uiPref("micEchoCancel") != "0",
        micAutoGain = store.uiPref("micAutoGain") != "0",
        audioInput = store.uiPref("audioInput")?.ifBlank { null },
        audioOutput = store.uiPref("audioOutput")?.ifBlank { null },
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

    fun setAudioInput(v: String?) {
        store.setUiPref("audioInput", v ?: "")
        _state.update { it.copy(audioInput = v) }
    }

    fun setAudioOutput(v: String?) {
        store.setUiPref("audioOutput", v ?: "")
        _state.update { it.copy(audioOutput = v) }
    }
}
