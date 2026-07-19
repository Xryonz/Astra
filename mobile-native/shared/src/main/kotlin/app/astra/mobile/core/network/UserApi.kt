package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.ChangePasswordRequest
import app.astra.mobile.core.network.dto.CustomStatusRequest
import app.astra.mobile.core.network.dto.ProfileViewWrapper
import app.astra.mobile.core.network.dto.SetPasswordRequest
import app.astra.mobile.core.network.dto.SetStatusRequest
import app.astra.mobile.core.network.dto.UpdateProfileRequest
import app.astra.mobile.core.network.dto.UserWrapper
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface UserApi {

    @GET("api/auth/me")
    suspend fun me(): ApiEnvelope<UserWrapper>

    @GET("api/profile/{id}")
    suspend fun profile(@Path("id") id: String): ApiEnvelope<ProfileViewWrapper>

    @PATCH("api/profile")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): ApiEnvelope<UserWrapper>

    @POST("api/auth/password")
    suspend fun changePassword(@Body body: ChangePasswordRequest)

    // Cria a PRIMEIRA senha (contas Google sem senha); backend rejeita se ja tem.
    @POST("api/auth/password/set")
    suspend fun setPassword(@Body body: SetPasswordRequest)

    @PATCH("api/profile/status")
    suspend fun setStatus(@Body body: SetStatusRequest)

    // Recado (custom status). Mora sob /api/friends no backend, mas e edicao de
    // perfil — fica aqui pra o desktop nao precisar de uma FriendsApi so por isto
    // (a do Android vive no modulo :app). Limpar = mandar "".
    @PATCH("api/friends/custom-status")
    suspend fun setCustomStatus(@Body body: CustomStatusRequest)
}
