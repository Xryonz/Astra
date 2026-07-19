package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.RevokeOthersRequest
import app.astra.mobile.core.network.dto.RevokeOthersResponse
import app.astra.mobile.core.network.dto.SessionsResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

// Sessoes ativas = os logins vivos da conta (um refresh token cada). Serve pra
// ver de onde a conta esta logada e derrubar o que nao reconhecer.
interface SessionApi {

    @GET("api/sessions")
    suspend fun list(): ApiEnvelope<SessionsResponse>

    @DELETE("api/sessions/{id}")
    suspend fun revoke(@Path("id") id: String)

    // Derruba todas MENOS a atual — por isso manda o refresh token desta sessao.
    @POST("api/sessions/revoke-others")
    suspend fun revokeOthers(@Body body: RevokeOthersRequest): ApiEnvelope<RevokeOthersResponse>
}
