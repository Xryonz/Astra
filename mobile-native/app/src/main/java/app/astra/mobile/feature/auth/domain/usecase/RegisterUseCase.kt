package app.astra.mobile.feature.auth.domain.usecase

import app.astra.mobile.core.ApiException
import app.astra.mobile.feature.auth.domain.AuthRepository
import app.astra.mobile.feature.auth.domain.model.AuthUser
import javax.inject.Inject

class RegisterUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    private val usernameRegex = Regex("^[a-z0-9_]+$")

    suspend operator fun invoke(
        displayName: String,
        username: String,
        email: String,
        password: String,
    ): Result<AuthUser> {
        if (displayName.isBlank()) return fail("Informe um nome de exibicao")
        if (username.length < 3) return fail("Username precisa de 3+ caracteres")
        if (!username.matches(usernameRegex)) return fail("Username: so minusculas, numeros e _")
        if (email.isBlank()) return fail("Informe seu e-mail")
        if (password.length < 8) return fail("Senha precisa de 8+ caracteres")
        return repository.register(displayName, username, email, password)
    }

    private fun fail(msg: String) = Result.failure<AuthUser>(ApiException(msg))
}
