package app.astra.mobile.feature.home

import app.astra.mobile.feature.dm.domain.model.Conversation
import app.astra.mobile.feature.profile.domain.model.UserStatus
import app.astra.mobile.feature.server.domain.model.Server

// Um canal de voz com gente dentro agora — card da faixa "na voz".
data class ActiveVoiceRoom(
    val channelId: String,
    val channelName: String,
    val serverId: String,
    val serverName: String,
    val count: Int,
)

data class HomeUiState(
    val loading: Boolean = true,
    val servers: List<Server> = emptyList(),
    val dms: List<Conversation> = emptyList(),
    val unread: Set<String> = emptySet(),
    val activeVoice: List<ActiveVoiceRoom> = emptyList(),
    val myName: String = "",
    val myAvatar: String? = null,
    val myStatus: UserStatus = UserStatus.ONLINE,
    // FAB "nova mensagem" (abrir DM por @username)
    val opening: Boolean = false,
    val openError: String? = null,
)
