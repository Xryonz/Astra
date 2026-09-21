package app.astra.mobile.feature.server.domain

import app.astra.mobile.feature.server.domain.model.Server
import app.astra.mobile.feature.server.domain.model.ServerMember
import kotlinx.coroutines.flow.Flow

interface ServerRepository {
    suspend fun servers(): Result<List<Server>>
    suspend fun createServer(name: String, isGroup: Boolean = false): Result<Server>

    suspend fun updateServer(
        id: String,
        name: String? = null,
        iconUrl: String? = null,
        isPublic: Boolean? = null,
        bannerUrl: String? = null,
        description: String? = null,
        messageRetentionDays: Int? = null,
    ): Result<Server>
    suspend fun members(serverId: String): Result<List<ServerMember>>

    suspend fun channelReads(): Result<Map<String, String>>

    suspend fun voicePresence(channelIds: List<String>): Result<Map<String, List<String>>>

    suspend fun createChannel(serverId: String, name: String, isVoice: Boolean): Result<Unit>

    suspend fun leaveServer(serverId: String): Result<Unit>

    fun channelActivity(): Flow<String>
}
