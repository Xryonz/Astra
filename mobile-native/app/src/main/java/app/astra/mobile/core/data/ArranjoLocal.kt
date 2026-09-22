package app.astra.mobile.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArranjoLocal @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val ordemKey = stringPreferencesKey("ordem_das_constelacoes")
    private val recolhidasKey = stringSetPreferencesKey("categorias_recolhidas")

    val ordemDasConstelacoes: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[ordemKey]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
    }

    val categoriasRecolhidas: Flow<Set<String>> = dataStore.data.map { it[recolhidasKey].orEmpty() }

    suspend fun guardarOrdem(ids: List<String>) {
        dataStore.edit { it[ordemKey] = ids.joinToString(",") }
    }

    suspend fun alternarCategoria(id: String) {
        dataStore.edit { prefs ->
            val atuais = prefs[recolhidasKey].orEmpty()
            prefs[recolhidasKey] = if (id in atuais) atuais - id else atuais + id
        }
    }
}
