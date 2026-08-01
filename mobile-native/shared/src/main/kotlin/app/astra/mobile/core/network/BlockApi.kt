package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.BlockedUserDto
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

// Bloqueio de pessoa. Bloquear desfaz a amizade e some com a conversa da barra
// lateral — o backend cuida disso junto, num pedido so, senao a interface teria
// que orquestrar tres chamadas e dava pra parar no meio.
interface BlockApi {
    @GET("api/blocks")
    suspend fun blocked(): ApiEnvelope<List<BlockedUserDto>>

    @POST("api/blocks/{userId}")
    suspend fun block(@Path("userId") userId: String)

    @DELETE("api/blocks/{userId}")
    suspend fun unblock(@Path("userId") userId: String)
}
