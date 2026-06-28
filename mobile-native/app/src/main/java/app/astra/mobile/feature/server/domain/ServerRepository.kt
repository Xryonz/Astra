package app.astra.mobile.feature.server.domain

import app.astra.mobile.feature.server.domain.model.Server
import app.astra.mobile.feature.server.domain.model.ServerMember
import kotlinx.coroutines.flow.Flow

interface ServerRepository {
    suspend fun servers(): Result<List<Server>>
    suspend fun createServer(name: String, isGroup: Boolean = false): Result<Server>
    /** Edita nome/icone/visibilidade da Constelacao (so dono/admin no backend). */
    suspend fun updateServer(id: String, name: String?, iconUrl: String?, isPublic: Boolean?): Result<Server>
    suspend fun members(serverId: String): Result<List<ServerMember>>
    /** { channelId -> lastReadAtISO } do user atual. */
    suspend fun channelReads(): Result<Map<String, String>>
    /** { channelId -> userIds } — quem esta em cada canal de voz agora. */
    suspend fun voicePresence(channelIds: List<String>): Result<Map<String, List<String>>>

    // ── Gestao de categorias/canais (owner ou MANAGE_CHANNELS no backend) ──
    suspend fun createCategory(serverId: String, name: String): Result<Unit>
    suspend fun renameCategory(serverId: String, categoryId: String, name: String): Result<Unit>
    suspend fun deleteCategory(serverId: String, categoryId: String): Result<Unit>
    /** Cria canal e, se categoryId != null, ja move pra essa categoria. */
    suspend fun createChannel(serverId: String, name: String, isVoice: Boolean, categoryId: String?): Result<Unit>
    /** Move canal; categoryId == null = sem categoria. */
    suspend fun moveChannel(serverId: String, channelId: String, categoryId: String?): Result<Unit>
    /** channel_activity ao vivo: emite o channelId que recebeu mensagem nova. */
    fun channelActivity(): Flow<String>
}
