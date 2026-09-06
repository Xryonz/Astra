package app.astra.desktop.prefs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import app.astra.desktop.Placas
import app.astra.desktop.auth.SessionStore
import com.sun.management.OperatingSystemMXBean
import java.lang.management.ManagementFactory

enum class AuroraQuality(val key: String, val octaves: Int) {
    HIGH("high", 3), MEDIUM("med", 2), LOW("low", 1);
    companion object {
        fun from(raw: String?) = entries.find { it.key == raw } ?: HIGH
    }
}

enum class UiFps(val key: String, val cap: Int) {
    FREE("free", 0), CAP60("60", 60), CAP30("30", 30);
    companion object {
        fun from(raw: String?) = entries.find { it.key == raw } ?: FREE
    }
}

enum class ScreenQuality(
    val key: String, val label: String,
    val width: Int, val height: Int, val fps: Int, val bitrate: Int,
) {
    SHARP_1080_60("s108060", "1080p 60fps — nítida", 1920, 1080, 60, 8_000_000),
    SMOOTH_720_60("s72060", "720p 60fps — fluida", 1280, 720, 60, 4_000_000),
    LIGHT_720_30("l72030", "720p 30fps — leve", 1280, 720, 30, 2_500_000),

    TINY_540_30("t54030", "540p 30fps — economica", 960, 540, 30, 1_200_000);
    companion object {
        fun from(raw: String?) = entries.find { it.key == raw } ?: padraoDaMaquina()

        fun padraoDaMaquina(): ScreenQuality = when (Runtime.getRuntime().availableProcessors()) {
            in 0..4 -> TINY_540_30
            in 5..6 -> LIGHT_720_30
            in 7..8 -> SMOOTH_720_60
            else -> SHARP_1080_60
        }
    }
}

enum class FontSizePref(val key: String, val label: String, val scale: Float) {
    SM("sm", "Pequena", 0.9f), MD("md", "Padrao", 1.0f), LG("lg", "Grande", 1.12f), XL("xl", "Maior", 1.25f);
    companion object {
        fun from(raw: String?) = entries.find { it.key == raw } ?: MD
    }
}

enum class DensityPref(val key: String, val label: String, val topDp: Int, val groupedTopDp: Int) {
    COMPACT("compact", "Compacta", 5, 1),
    COMFORTABLE("comfortable", "Confortavel", 10, 2),
    SPACIOUS("spacious", "Espacosa", 16, 4);
    companion object {
        fun from(raw: String?) = entries.find { it.key == raw } ?: COMFORTABLE
    }
}

const val DEGRAU_SEM_FUNDO = 1
const val DEGRAU_SEM_ESTRELAS = 2
const val DEGRAU_SEM_PET = 3
const val DEGRAU_SEM_CASCATA = 4
const val DEGRAU_MAXIMO = 4

class DesktopPrefs(private val store: SessionStore) {
    data class Prefs(
        val reduceMotion: Boolean = false,
        val notifyDms: Boolean = true,
        val notifyChannels: Boolean = true,
        val atividadeVisivel: Boolean = false,
        val performanceMode: Boolean = false,
        val perfAutomatico: String = "",
        val auroraEnabled: Boolean = false,
        val auroraQuality: AuroraQuality = AuroraQuality.MEDIUM,
        val starsEnabled: Boolean = false,
        val uiFps: UiFps = UiFps.FREE,
        val windowTransparent: Boolean = true,
        val exitOnClose: Boolean = false,
        val altoContraste: Boolean = false,
        val accentId: String = "white",
        val bgId: String = "void",
        val fontSize: FontSizePref = FontSizePref.MD,
        val density: DensityPref = DensityPref.COMFORTABLE,
        val screenQuality: ScreenQuality = ScreenQuality.SMOOTH_720_60,
        val motorNovo: Boolean = false,
        val duasCamadas: Boolean = false,
        val avisoDiscreto: Boolean = false,
        val somDeAviso: Boolean = true,
        val petLigado: Boolean = false,
        val petPelagem: String = "LARANJA",
        val petTipo: String = "SIMPLES",
        val petNome: String = "",
        val modoTransmissao: Boolean = false,
        val modoTransmissaoAuto: Boolean = false,
        val micNoiseSuppression: Boolean = true,
        val micEchoCancel: Boolean = true,
        val micAutoGain: Boolean = true,
        val micSensitivity: Float = 0f,
        val audioInput: String? = null,
        val audioOutput: String? = null,
        val volumeDoMicrofone: Int = 100,
        val volumeDaEscuta: Int = 100,
        val degrau: Int = 0,
        val teclaMudo: Int = 0,
        val teclaEnsurdecer: Int = 0,
        val emojiRecentes: List<String> = emptyList(),
    ) {
        val auroraOn: Boolean get() = auroraEnabled && !performanceMode && degrau < DEGRAU_SEM_FUNDO
        val starsOn: Boolean get() = starsEnabled && !performanceMode && degrau < DEGRAU_SEM_ESTRELAS
        val petOn: Boolean get() = petLigado && degrau < DEGRAU_SEM_PET
        val reduceMotionEff: Boolean get() =
            reduceMotion || performanceMode || degrau >= DEGRAU_SEM_CASCATA
    }

    private val _state = MutableStateFlow(read())
    val state = _state.asStateFlow()

    init { migrarCeu(); aferirAMaquina() }

    private fun aferirAMaquina() {
        if (store.uiPref("maquinaAferida") == "1") return
        store.setUiPref("maquinaAferida", "1")
        if (store.uiPref("performanceMode") != null) return
        val motivo = motivoParaEconomizar() ?: return
        store.setUiPref("performanceMode", "1")
        store.setUiPref("perfAutomatico", motivo)
        _state.update { it.copy(performanceMode = true, perfAutomatico = motivo) }
    }

    private fun motivoParaEconomizar(): String? {
        val nucleos = Runtime.getRuntime().availableProcessors()
        val ram = runCatching {
            (ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean).totalMemorySize
        }.getOrNull() ?: 0L
        val gb = ram / 1024.0 / 1024.0 / 1024.0
        return when {
            ram > 0 && ram < 5L * 1024 * 1024 * 1024 -> "%.1f GB de memória".format(gb)
            nucleos <= 2 -> "$nucleos núcleos de processador"
            placaApertada(nucleos) -> "placa de vídeo integrada"
            else -> null
        }
    }

    private fun placaApertada(nucleos: Int): Boolean {
        val placa = runCatching { Placas.daTela }.getOrNull() ?: return false
        return !placa.dedicada && nucleos <= 4
    }

    fun aplicarDegrau(novo: Int) {
        val alvo = novo.coerceIn(0, DEGRAU_MAXIMO)
        if (_state.value.degrau == alvo) return
        _state.update { it.copy(degrau = alvo) }
    }

    fun dispensarAvisoDePerf() {
        store.setUiPref("perfAutomatico", "")
        _state.update { it.copy(perfAutomatico = "") }
    }

    private fun migrarCeu() {
        if (store.uiPref("ceuMigrado") == "2") return
        store.setUiPref("ceuMigrado", "2")
        store.setUiPref("auroraQuality", AuroraQuality.HIGH.key)
        store.setUiPref("uiFps", UiFps.FREE.key)
        _state.update { it.copy(auroraQuality = AuroraQuality.HIGH, uiFps = UiFps.FREE) }
    }

    private fun read() = Prefs(
        reduceMotion = store.uiPref("reduceMotion") == "1",
        notifyDms = store.uiPref("notifyDms") != "0",
        notifyChannels = store.uiPref("notifyChannels") != "0",
        atividadeVisivel = store.uiPref("atividadeVisivel") == "1",
        performanceMode = store.uiPref("performanceMode") == "1",
        perfAutomatico = store.uiPref("perfAutomatico") ?: "",
        auroraEnabled = store.uiPref("auroraEnabled") == "1",
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
        screenQuality = ScreenQuality.from(store.uiPref("screenQuality")),
        avisoDiscreto = store.uiPref("avisoDiscreto") == "1",
        somDeAviso = store.uiPref("somDeAviso") != "0",
        petLigado = store.uiPref("petLigado") == "1",
        petPelagem = store.uiPref("petPelagem") ?: "LARANJA",
        petTipo = store.uiPref("petTipo") ?: store.uiPref("petBicho") ?: "SIMPLES",
        petNome = store.uiPref("petNome") ?: "",
        modoTransmissao = store.uiPref("modoTransmissao") == "1",
        modoTransmissaoAuto = store.uiPref("modoTransmissaoAuto") == "1",
        micNoiseSuppression = store.uiPref("micNoiseSuppression") != "0",
        micEchoCancel = store.uiPref("micEchoCancel") != "0",
        motorNovo = store.uiPref("motorNovo") == "1",
        duasCamadas = store.uiPref("duasCamadas") == "1",
        micAutoGain = store.uiPref("micAutoGain") != "0",
        micSensitivity = store.uiPref("micSensitivity")?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f,
        audioInput = store.uiPref("audioInput")?.ifBlank { null },
        audioOutput = store.uiPref("audioOutput")?.ifBlank { null },
        volumeDoMicrofone = store.uiPref("volumeDoMicrofone")?.toIntOrNull()?.coerceIn(0, 100) ?: 100,
        volumeDaEscuta = store.uiPref("volumeDaEscuta")?.toIntOrNull()?.coerceIn(0, 100) ?: 100,
        teclaMudo = store.uiPref("teclaMudo")?.toIntOrNull() ?: 0,
        teclaEnsurdecer = store.uiPref("teclaEnsurdecer")?.toIntOrNull() ?: 0,
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

    fun setSomDeAviso(v: Boolean) {
        persist("somDeAviso", v)
        _state.update { it.copy(somDeAviso = v) }
    }

    fun setPetLigado(v: Boolean) {
        persist("petLigado", v)
        _state.update { it.copy(petLigado = v) }
    }

    fun setPetTipo(v: String) {
        store.setUiPref("petTipo", v)
        _state.update { it.copy(petTipo = v) }
    }

    fun setPetPelagem(v: String) {
        store.setUiPref("petPelagem", v)
        _state.update { it.copy(petPelagem = v) }
    }

    fun setPetNome(v: String) {
        val limpo = v.trim().take(16)
        store.setUiPref("petNome", limpo)
        _state.update { it.copy(petNome = limpo) }
    }

    fun setMicNoiseSuppression(v: Boolean) {
        persist("micNoiseSuppression", v)
        _state.update { it.copy(micNoiseSuppression = v) }
    }

    fun setMicEchoCancel(v: Boolean) {
        persist("micEchoCancel", v)
        _state.update { it.copy(micEchoCancel = v) }
    }

    fun setMotorNovo(v: Boolean) {
        persist("motorNovo", v)
        _state.update { it.copy(motorNovo = v) }
    }

    fun setDuasCamadas(v: Boolean) {
        persist("duasCamadas", v)
        _state.update { it.copy(duasCamadas = v) }
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

    fun setVolumeDoMicrofone(v: Int) {
        val n = v.coerceIn(0, 100)
        store.setUiPref("volumeDoMicrofone", n.toString())
        _state.update { it.copy(volumeDoMicrofone = n) }
    }

    fun setVolumeDaEscuta(v: Int) {
        val n = v.coerceIn(0, 100)
        store.setUiPref("volumeDaEscuta", n.toString())
        _state.update { it.copy(volumeDaEscuta = n) }
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

    fun registrarEmoji(glifo: String) {
        val nova = (listOf(glifo) + _state.value.emojiRecentes.filter { it != glifo }).take(TETO_RECENTES)
        store.setUiPref("emojiRecentes", nova.joinToString(" "))
        _state.update { it.copy(emojiRecentes = nova) }
    }

    private companion object {
        const val TETO_RECENTES = 24
    }
}
