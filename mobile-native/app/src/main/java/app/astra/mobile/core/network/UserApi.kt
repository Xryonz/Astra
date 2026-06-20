package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.ChangePasswordRequest
import app.astra.mobile.core.network.dto.ProfileViewWrapper
import app.astra.mobile.core.network.dto.UpdateProfileRequest
import app.astra.mobile.core.network.dto.UserWrapper
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface UserApi {
    // Perfil do usuario logado (inclui email + hasPassword).
    @GET("api/auth/me")
    suspend fun me(): ApiEnvelope<UserWrapper>

    // Perfil publico de qualquer usuario (+ servidores em comum + status).
    @GET("api/profile/{id}")
    suspend fun profile(@Path("id") id: String): ApiEnvelope<ProfileViewWrapper>

    // Edita o proprio perfil. 409 = username em uso (erro amigavel no corpo).
    @PATCH("api/profile")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): ApiEnvelope<UserWrapper>

    // Troca de senha. Sem tipo de retorno = ignora o corpo; 401/400 viram
    // HttpException com mensagem amigavel no errorBody.
    @POST("api/auth/password")
    suspend fun changePassword(@Body body: ChangePasswordRequest)
}
