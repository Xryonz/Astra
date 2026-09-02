package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.ApagarContaRequest
import app.astra.mobile.core.network.dto.ContaApagadaDto
import app.astra.mobile.core.network.dto.LogoutRequest
import app.astra.mobile.core.network.dto.AuthData
import app.astra.mobile.core.network.dto.LoginRequest
import app.astra.mobile.core.network.dto.RegisterRequest
import app.astra.mobile.core.network.dto.EmailPendenteDto
import app.astra.mobile.core.network.dto.EmailTrocadoDto
import app.astra.mobile.core.network.dto.TrocarEmailRequest
import app.astra.mobile.core.network.dto.TrocarUsuarioRequest
import app.astra.mobile.core.network.dto.UsuarioTrocadoDto
import app.astra.mobile.core.network.dto.VerifyEmailRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<ApiEnvelope<AuthData>>

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<ApiEnvelope<AuthData>>

    @POST("api/auth/onboarded")
    suspend fun markOnboarded()

    @POST("api/auth/email/verify")
    suspend fun verifyEmail(@Body body: VerifyEmailRequest)

    @POST("api/auth/email/resend")
    suspend fun resendEmailCode()

    @POST("api/auth/email/change")
    suspend fun trocarEmail(@Body body: TrocarEmailRequest): ApiEnvelope<EmailPendenteDto>

    @POST("api/auth/email/change/confirm")
    suspend fun confirmarTrocaDeEmail(@Body body: VerifyEmailRequest): ApiEnvelope<EmailTrocadoDto>

    @POST("api/auth/username")
    suspend fun trocarUsuario(@Body body: TrocarUsuarioRequest): ApiEnvelope<UsuarioTrocadoDto>

    @POST("api/auth/logout")
    suspend fun logout(@Body body: LogoutRequest)

    @POST("api/auth/apagar-conta")
    suspend fun apagarConta(@Body body: ApagarContaRequest): Response<ApiEnvelope<ContaApagadaDto>>
}
