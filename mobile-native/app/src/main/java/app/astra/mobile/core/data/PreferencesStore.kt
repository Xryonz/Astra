package app.astra.mobile.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class FontSizePref(val id: String, val label: String, val scale: Float) {
    SM("sm", "Pequena", 0.9f),
    MD("md", "Padrão", 1.0f),
    LG("lg", "Grande", 1.12f),
    XL("xl", "Maior", 1.25f);

    companion object {
        fun from(id: String?): FontSizePref = entries.firstOrNull { it.id == id } ?: MD
    }
}

enum class DensityPref(val id: String, val label: String, val topDp: Int, val groupedTopDp: Int) {
    COMPACT("compact", "Compacta", 5, 1),
    COMFORTABLE("comfortable", "Confortável", 10, 2),
    SPACIOUS("spacious", "Espacosa", 16, 4);

    companion object {
        fun from(id: String?): DensityPref = entries.firstOrNull { it.id == id } ?: COMFORTABLE
    }
}

enum class QualidadeAoAssistir(val id: String, val rotulo: String, val explicacao: String) {
    AUTOMATICA("auto", "Automática", "Leve no palco, completa em tela cheia."),
    COMPLETA("completa", "Completa", "Sempre a imagem mais nítida. Gasta mais dados."),
    LEVE("leve", "Leve", "Sempre a versão leve. Poupa dados no 4G.");

    companion object {
        fun from(id: String?): QualidadeAoAssistir = entries.firstOrNull { it.id == id } ?: AUTOMATICA
    }
}

enum class QualidadeAoTransmitir(
    val id: String,
    val rotulo: String,
    val ladoMaior: Int,
    val ladoMenor: Int,
    val quadros: Int,
    val kbps: Int,
) {
    NITIDA("s108060", "1080p a 60 quadros, nítida", 1920, 1080, 60, 8_000),
    FLUIDA("s72060", "720p a 60 quadros, fluida", 1280, 720, 60, 4_000),
    LEVE("l72030", "720p a 60 quadros, leve", 1280, 720, 60, 2_500),
    ECONOMICA("t54030", "540p a 60 quadros, econômica", 960, 540, 60, 1_200);

    companion object {
        fun from(id: String?): QualidadeAoTransmitir = entries.firstOrNull { it.id == id } ?: FLUIDA
    }
}

data class AppPrefs(
    val reduceMotion: Boolean = false,
    val haptics: Boolean = true,
    val fontSize: FontSizePref = FontSizePref.MD,
    val density: DensityPref = DensityPref.COMFORTABLE,
    val accentId: String = "white",
    val bgId: String = "void",
    val animAurora: Boolean = true,
    val animStars: Boolean = true,
    val animTransitions: Boolean = true,
    val animSkyTouch: Boolean = true,
    val aoAssistir: QualidadeAoAssistir = QualidadeAoAssistir.AUTOMATICA,
    val aoTransmitir: QualidadeAoTransmitir = QualidadeAoTransmitir.FLUIDA,
) {
    val auroraOn: Boolean get() = !reduceMotion && animAurora
    val starsOn: Boolean get() = !reduceMotion && animStars
    val transitionsOn: Boolean get() = !reduceMotion && animTransitions
    val skyTouchOn: Boolean get() = auroraOn && animSkyTouch
}

@Singleton
class PreferencesStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val reduceMotionKey = booleanPreferencesKey("reduce_motion")
    private val hapticsKey = booleanPreferencesKey("haptics")
    private val fontSizeKey = stringPreferencesKey("font_size")
    private val densityKey = stringPreferencesKey("density")
    private val accentKey = stringPreferencesKey("accent_id")
    private val bgKey = stringPreferencesKey("bg_id")
    private val animAuroraKey = booleanPreferencesKey("anim_aurora")
    private val animStarsKey = booleanPreferencesKey("anim_stars")
    private val animTransitionsKey = booleanPreferencesKey("anim_transitions")
    private val animSkyTouchKey = booleanPreferencesKey("anim_sky_touch")
    private val aoAssistirKey = stringPreferencesKey("qualidade_ao_assistir")
    private val aoTransmitirKey = stringPreferencesKey("qualidade_ao_transmitir")

    val prefs: Flow<AppPrefs> = dataStore.data.map { p ->
        AppPrefs(
            reduceMotion = p[reduceMotionKey] ?: false,
            haptics = p[hapticsKey] ?: true,
            fontSize = FontSizePref.from(p[fontSizeKey]),
            density = DensityPref.from(p[densityKey]),
            accentId = p[accentKey] ?: "white",
            bgId = p[bgKey] ?: "void",
            animAurora = p[animAuroraKey] ?: true,
            animStars = p[animStarsKey] ?: true,
            animTransitions = p[animTransitionsKey] ?: true,
            animSkyTouch = p[animSkyTouchKey] ?: true,
            aoAssistir = QualidadeAoAssistir.from(p[aoAssistirKey]),
            aoTransmitir = QualidadeAoTransmitir.from(p[aoTransmitirKey]),
        )
    }

    suspend fun setReduceMotion(v: Boolean) = dataStore.edit { it[reduceMotionKey] = v }
    suspend fun setHaptics(v: Boolean) = dataStore.edit { it[hapticsKey] = v }
    suspend fun setAnimAurora(v: Boolean) = dataStore.edit { it[animAuroraKey] = v }
    suspend fun setAnimStars(v: Boolean) = dataStore.edit { it[animStarsKey] = v }
    suspend fun setAnimTransitions(v: Boolean) = dataStore.edit { it[animTransitionsKey] = v }
    suspend fun setAnimSkyTouch(v: Boolean) = dataStore.edit { it[animSkyTouchKey] = v }
    suspend fun setFontSize(v: FontSizePref) = dataStore.edit { it[fontSizeKey] = v.id }
    suspend fun setDensity(v: DensityPref) = dataStore.edit { it[densityKey] = v.id }
    suspend fun setAccent(id: String) = dataStore.edit { it[accentKey] = id }
    suspend fun setBg(id: String) = dataStore.edit { it[bgKey] = id }
    suspend fun setAoAssistir(v: QualidadeAoAssistir) = dataStore.edit { it[aoAssistirKey] = v.id }
    suspend fun setAoTransmitir(v: QualidadeAoTransmitir) = dataStore.edit { it[aoTransmitirKey] = v.id }
    suspend fun setTheme(accentId: String, bgId: String) = dataStore.edit {
        it[accentKey] = accentId
        it[bgKey] = bgId
    }
}
