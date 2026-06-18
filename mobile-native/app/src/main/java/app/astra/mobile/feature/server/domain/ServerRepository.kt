package app.astra.mobile.feature.server.domain

import app.astra.mobile.feature.server.domain.model.Server
import app.astra.mobile.feature.server.domain.model.ServerMember

interface ServerRepository {
    suspend fun servers(): Result<List<Server>>
    suspend fun createServer(name: String): Result<Server>
    suspend fun members(serverId: String): Result<List<ServerMember>>
}
