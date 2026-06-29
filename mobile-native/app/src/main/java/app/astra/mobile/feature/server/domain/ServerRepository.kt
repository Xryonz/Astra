package app.astra.mobile.feature.server.domain

import app.astra.mobile.feature.server.domain.model.Server
import app.astra.mobile.feature.server.domain.model.ServerMember
import kotlinx.coroutines.flow.Flow

interface ServerRepository {
    suspend fun servers(): Result<List<Server>>
    suspend fun createServer(name: String, isGroup: Boolean = false): Result<Server>

    suspend fun updateServer(id: String, name: String?, iconUrl: String?, isPublic: Boolean?): Result<Server>
    suspend fun members(serverId: String): Result<List<ServerMember>>

    suspend fun channelReads(): Result<Map<String, String>>

    suspend fun voicePresence(channelIds: List<String>): Result<Map<String, List<String>>>

    suspend fun createChannel(serverId: String, name: String, isVoice: Boolean): Result<Unit>

    fun channelActivity(): Flow<String>
}
