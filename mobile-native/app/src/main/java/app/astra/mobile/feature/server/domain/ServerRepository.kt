package app.astra.mobile.feature.server.domain

import app.astra.mobile.feature.server.domain.model.Server

interface ServerRepository {
    suspend fun servers(): Result<List<Server>>
    suspend fun createServer(name: String): Result<Server>
}
