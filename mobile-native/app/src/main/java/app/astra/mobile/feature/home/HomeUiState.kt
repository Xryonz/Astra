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
    // channelIds nao-lidos (dot na lista de orbitas inline).
    val channelUnread: Set<String> = emptySet(),
    val activeVoice: List<ActiveVoiceRoom> = emptyList(),
    // null = painel de Sussurros (DMs); senao = painel de canais da Constelacao.
    val selectedServerId: String? = null,
    // id do user logado — detecta se sou dono da Constelacao selecionada.
    val myId: String? = null,
    val myName: String = "",
    val myUsername: String = "",
    val myAvatar: String? = null,
    val myBanner: String? = null,
    val myBannerColor: String? = null,
    val myBio: String? = null,
    val myPronouns: String? = null,
    val myCreatedAt: String? = null,
    val myStatus: UserStatus = UserStatus.ONLINE,
    // FAB "nova mensagem" (abrir DM por @username)
    val opening: Boolean = false,
    val openError: String? = null,
    // Popup de forjar constelacao/aglomerado
    val creating: Boolean = false,
    val createError: String? = null,
)
