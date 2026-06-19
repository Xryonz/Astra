package app.astra.mobile.feature.server.domain

import app.astra.mobile.feature.server.domain.model.Server
import app.astra.mobile.feature.server.domain.model.ServerMember
import kotlinx.coroutines.flow.Flow

interface ServerRepository {
    suspend fun servers(): Result<List<Server>>
    suspend fun createServer(name: String): Result<Server>
    suspend fun members(serverId: String): Result<List<ServerMember>>
    /** { channelId -> lastReadAtISO } do user atual. */
    suspend fun channelReads(): Result<Map<String, String>>
    /** channel_activity ao vivo: emite o channelId que recebeu mensagem nova. */
    fun channelActivity(): Flow<String>
}
