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
)

@Singleton
class PreferencesStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val reduceMotionKey = booleanPreferencesKey("reduce_motion")
    private val hapticsKey = booleanPreferencesKey("haptics")
    private val fontSizeKey = stringPreferencesKey("font_size")
    private val densityKey = stringPreferencesKey("density")

    val prefs: Flow<AppPrefs> = dataStore.data.map { p ->
        AppPrefs(
            reduceMotion = p[reduceMotionKey] ?: false,
            haptics = p[hapticsKey] ?: true,
            fontSize = FontSizePref.from(p[fontSizeKey]),
            density = DensityPref.from(p[densityKey]),
        )
    }

    suspend fun setReduceMotion(v: Boolean) = dataStore.edit { it[reduceMotionKey] = v }
    suspend fun setHaptics(v: Boolean) = dataStore.edit { it[hapticsKey] = v }
    suspend fun setFontSize(v: FontSizePref) = dataStore.edit { it[fontSizeKey] = v.id }
    suspend fun setDensity(v: DensityPref) = dataStore.edit { it[densityKey] = v.id }
}
