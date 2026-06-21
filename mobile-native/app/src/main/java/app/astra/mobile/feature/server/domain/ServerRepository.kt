package app.astra.mobile.feature.server.domain

import app.astra.mobile.feature.server.domain.model.Server
import app.astra.mobile.feature.server.domain.model.ServerMember
import kotlinx.coroutines.flow.Flow

interface ServerRepository {
    suspend fun servers(): Result<List<Server>>
    suspend fun createServer(name: String, isGroup: Boolean = false): Result<Server>
    /** Edita nome/icone da Constelacao (so dono/admin no backend). */
    suspend fun updateServer(id: String, name: String?, iconUrl: String?): Result<Server>
    suspend fun members(serverId: String): Result<List<ServerMember>>
    /** { channelId -> lastReadAtISO } do user atual. */
    suspend fun channelReads(): Result<Map<String, String>>
    /** { channelId -> userIds } — quem esta em cada canal de voz agora. */
    suspend fun voicePresence(channelIds: List<String>): Result<Map<String, List<String>>>
    /** channel_activity ao vivo: emite o channelId que recebeu mensagem nova. */
    fun channelActivity(): Flow<String>
}
