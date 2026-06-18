package app.astra.mobile.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sessao persistida em DataStore (substitui o localStorage/keystore do app web).
 * accessToken rotaciona a cada /refresh; refreshToken e a fonte de verdade.
 */
@Singleton
class TokenStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val accessKey = stringPreferencesKey("access_token")
    private val refreshKey = stringPreferencesKey("refresh_token")
    private val userIdKey = stringPreferencesKey("user_id")

    val accessToken: Flow<String?> = dataStore.data.map { it[accessKey] }
    val refreshToken: Flow<String?> = dataStore.data.map { it[refreshKey] }

    /** Leitura pontual (usada pelo interceptor sincrono). */
    suspend fun currentAccess(): String? = dataStore.data.first()[accessKey]
    suspend fun currentRefresh(): String? = dataStore.data.first()[refreshKey]

    /** Id do usuario logado — pra marcar "minhas" mensagens no chat. */
    suspend fun currentUserId(): String? = dataStore.data.first()[userIdKey]

    suspend fun save(access: String, refresh: String) {
        dataStore.edit { it[accessKey] = access; it[refreshKey] = refresh }
    }

    suspend fun setUserId(id: String) {
        dataStore.edit { it[userIdKey] = id }
    }

    suspend fun setAccess(access: String) {
        dataStore.edit { it[accessKey] = access }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(accessKey); it.remove(refreshKey); it.remove(userIdKey) }
    }
}
