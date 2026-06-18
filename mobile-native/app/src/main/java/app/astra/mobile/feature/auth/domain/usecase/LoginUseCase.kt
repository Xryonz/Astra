package app.astra.mobile.feature.auth.domain.usecase

import app.astra.mobile.core.ApiException
import app.astra.mobile.feature.auth.domain.AuthRepository
import app.astra.mobile.feature.auth.domain.model.AuthUser
import javax.inject.Inject

/**
 * Validacao barata no cliente (campo vazio) antes de gastar uma request.
 * A validacao real (formato de email, credenciais) e do backend.
 */
class LoginUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String): Result<AuthUser> {
        if (email.isBlank()) return Result.failure(ApiException("Informe seu e-mail"))
        if (password.isBlank()) return Result.failure(ApiException("Informe sua senha"))
        return repository.login(email, password)
    }
}
