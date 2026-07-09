package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.ChangePasswordRequest
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
}
