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
    MD("md", "Padrao", 1.0f),
    LG("lg", "Grande", 1.12f),
    XL("xl", "Maior", 1.25f);

    companion object {
        fun from(id: String?): FontSizePref = entries.firstOrNull { it.id == id } ?: MD
    }
}

enum class DensityPref(val id: String, val label: String, val topDp: Int, val groupedTopDp: Int) {
    COMPACT("compact", "Compacta", 5, 1),
    COMFORTABLE("comfortable", "Confortavel", 10, 2),
    SPACIOUS("spacious", "Espacosa", 16, 4);

    companion object {
        fun from(id: String?): DensityPref = entries.firstOrNull { it.id == id } ?: COMFORTABLE
    }
}

data class AppPrefs(
    val reduceMotion: Boolean = false,
    val haptics: Boolean = true,
    val fontSize: FontSizePref = FontSizePref.MD,
    val density: DensityPref = DensityPref.COMFORTABLE,
    val accentId: String = "white",
    val bgId: String = "void",
    // Toggles especificos de animacao (so valem quando o mestre reduceMotion esta off).
    val animAurora: Boolean = true,
    val animStars: Boolean = true,
    val animTransitions: Boolean = true,
    val animSkyTouch: Boolean = true,
) {
    // Efetivo = mestre desligado E o toggle especifico ligado.
    val auroraOn: Boolean get() = !reduceMotion && animAurora
    val starsOn: Boolean get() = !reduceMotion && animStars
    val transitionsOn: Boolean get() = !reduceMotion && animTransitions
    // Toque vive dentro do shader da aurora: sem aurora nao tem efeito.
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
    suspend fun setTheme(accentId: String, bgId: String) = dataStore.edit {
        it[accentKey] = accentId
        it[bgKey] = bgId
    }
}
