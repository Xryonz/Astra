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
}
