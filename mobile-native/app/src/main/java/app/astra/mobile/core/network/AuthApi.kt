package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.AuthData
import app.astra.mobile.core.network.dto.LoginRequest
import app.astra.mobile.core.network.dto.RegisterRequest
import app.astra.mobile.core.network.dto.VerifyEmailRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<ApiEnvelope<AuthData>>

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<ApiEnvelope<AuthData>>

    // Marca o onboarding cosmico como visto (idempotente no backend).
    @POST("api/auth/onboarded")
    suspend fun markOnboarded()

    // Confirma o codigo de 6 digitos mandado pro email no registro.
    @POST("api/auth/email/verify")
    suspend fun verifyEmail(@Body body: VerifyEmailRequest)

    // Reenvia o codigo (backend auto-verifica se o mailer estiver desligado).
    @POST("api/auth/email/resend")
    suspend fun resendEmailCode()
}
