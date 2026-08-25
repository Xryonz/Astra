package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.AtividadeDto
import app.astra.mobile.core.network.dto.ChangePasswordRequest
import app.astra.mobile.core.network.dto.CustomStatusRequest
import app.astra.mobile.core.network.dto.PreferenciasRequest
import app.astra.mobile.core.network.dto.PreferenciasWrapper
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
import retrofit2.http.Query

interface UserApi {

    @GET("api/auth/me")
    suspend fun me(): ApiEnvelope<UserWrapper>

    @GET("api/profile/{id}")
    suspend fun profile(@Path("id") id: String): ApiEnvelope<ProfileViewWrapper>

    @PATCH("api/profile")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): ApiEnvelope<UserWrapper>

    @POST("api/auth/password")
    suspend fun changePassword(@Body body: ChangePasswordRequest)

    @POST("api/auth/password/set")
    suspend fun setPassword(@Body body: SetPasswordRequest)

    @PATCH("api/profile/status")
    suspend fun setStatus(@Body body: SetStatusRequest)

    @GET("api/profile/presence")
    suspend fun presence(@Query("ids") ids: String): ApiEnvelope<Map<String, String>>

    @GET("api/profile/activity")
    suspend fun activity(@Query("ids") ids: String): ApiEnvelope<Map<String, AtividadeDto>>

    @GET("api/profile/preferences")
    suspend fun preferencias(): ApiEnvelope<PreferenciasWrapper>

    @PATCH("api/profile/preferences")
    suspend fun salvarPreferencias(@Body body: PreferenciasRequest): ApiEnvelope<PreferenciasWrapper>

    @PATCH("api/friends/custom-status")
    suspend fun setCustomStatus(@Body body: CustomStatusRequest)
}
