package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.AuthData
import app.astra.mobile.core.network.dto.LoginRequest
import app.astra.mobile.core.network.dto.RegisterRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    // Response<> (e nao o tipo cru) pra ler o errorBody no 401 e mostrar a
    // mensagem amigavel que o backend manda ("E-mail ou senha incorretos").
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<ApiEnvelope<AuthData>>

    // 201 com o mesmo envelope do login (user + tokens). 409 = e-mail/username
    // em uso; 400 = validacao. Ambos mandam { error } amigavel no corpo.
    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<ApiEnvelope<AuthData>>
}
