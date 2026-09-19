package app.astra.mobile.feature.auth.domain

import app.astra.mobile.feature.auth.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    val isLoggedIn: Flow<Boolean>

    suspend fun login(email: String, password: String): Result<AuthUser>

    suspend fun register(
        displayName: String,
        username: String,
        email: String,
        password: String,
    ): Result<AuthUser>

    suspend fun completeGoogleLogin(refreshToken: String): Result<Unit>

    suspend fun logout()
}
