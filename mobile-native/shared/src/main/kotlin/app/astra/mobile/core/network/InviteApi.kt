package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.InvitePreviewDto
import app.astra.mobile.core.network.dto.ServerDto
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Path

// Convite por CODIGO. A previa (`preview`) e publica no backend — não exige login —
// mas aqui ela sobe pelo cliente autenticado mesmo, que e o unico que existe depois
// do gate. `join` devolve o servidor JA com os canais, entao quem chama pode
// selecionar direto sem esperar o proximo carregamento da lista.
interface InviteApi {
    @GET("api/invites/{code}")
    suspend fun preview(@Path("code") code: String): ApiEnvelope<InvitePreviewDto>

    @POST("api/invites/{code}/join")
    suspend fun join(@Path("code") code: String): ApiEnvelope<ServerDto>
}
