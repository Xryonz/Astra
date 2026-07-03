package app.astra.mobile.feature.home

import app.astra.mobile.feature.dm.domain.model.Conversation
import app.astra.mobile.feature.profile.domain.model.UserStatus
import app.astra.mobile.feature.server.domain.model.Server

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

    val channelUnread: Set<String> = emptySet(),
    val activeVoice: List<ActiveVoiceRoom> = emptyList(),

    val selectedServerId: String? = null,

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

    val opening: Boolean = false,
    val openError: String? = null,

    val creating: Boolean = false,
    val createError: String? = null,

    val manageError: String? = null,

    val unreadNotifs: Int = 0,

    // true 1x quando o me() chega sem onboardedAt -> Home dispara o onboarding.
    val needsOnboarding: Boolean = false,

    // true 1x quando o me() chega sem emailVerifiedAt -> tela de codigo.
    val needsEmailVerify: Boolean = false,

    // Conta Google sem senha -> overlay obrigatorio de criar senha.
    val needsPassword: Boolean = false,
    val pwSaving: Boolean = false,
    val pwError: String? = null,
)
