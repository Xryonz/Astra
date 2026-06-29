package app.astra.mobile.core.network

import app.astra.mobile.core.data.TokenStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenRefresher @Inject constructor(
    private val tokenStore: TokenStore,
    private val refreshApi: RefreshApi,
) {
    private val mutex = Mutex()

    suspend fun refresh(staleAccess: String?): String? = mutex.withLock {
        val current = tokenStore.currentAccess()

        if (!current.isNullOrBlank() && current != staleAccess) return@withLock current

        val refreshToken = tokenStore.currentRefresh() ?: return@withLock null
        val newTokens = try {
            refreshApi.refresh("Bearer $refreshToken").data
        } catch (e: HttpException) {

            if (e.code() == 401) tokenStore.clear()
            return@withLock null
        } catch (e: Exception) {

            return@withLock null
        }
        if (newTokens == null) return@withLock null

        tokenStore.save(newTokens.accessToken, newTokens.refreshToken)
        newTokens.accessToken
    }
}
