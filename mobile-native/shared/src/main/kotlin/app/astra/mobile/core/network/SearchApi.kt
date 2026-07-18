package app.astra.mobile.core.network

import app.astra.mobile.core.network.dto.ApiEnvelope
import app.astra.mobile.core.network.dto.SearchResultsDto
import retrofit2.http.GET
import retrofit2.http.Query

interface SearchApi {
    // scope: all | messages | channels | users. q precisa >= 2 chars (senao vazio).
    @GET("api/search")
    suspend fun search(
        @Query("q") q: String,
        @Query("scope") scope: String = "all",
    ): ApiEnvelope<SearchResultsDto>
}
