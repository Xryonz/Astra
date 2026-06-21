package app.astra.mobile.feature.auth.domain

import app.astra.mobile.feature.auth.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    // Fonte de verdade da sessao: emite true enquanto houver refresh token salvo.
    val isLoggedIn: Flow<Boolean>

    suspend fun login(email: String, password: String): Result<AuthUser>

    suspend fun register(
        displayName: String,
        username: String,
        email: String,
        password: String,
    ): Result<AuthUser>

    // OAuth Google: o deep link astra://auth/callback traz so o refresh token.
    // Aqui trocamos por access + setamos a sessao (isLoggedIn vira true).
    suspend fun completeGoogleLogin(refreshToken: String): Result<Unit>

    suspend fun logout()
}
