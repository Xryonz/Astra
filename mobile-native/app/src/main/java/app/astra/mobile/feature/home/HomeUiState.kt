package app.astra.mobile.feature.home

import app.astra.mobile.feature.dm.domain.model.Conversation
import app.astra.mobile.feature.profile.domain.model.UserStatus
import app.astra.mobile.feature.server.domain.model.Server
import app.astra.mobile.ui.components.BadgeUi

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

    val mutedServers: Set<String> = emptySet(),
    val mutedChannels: Set<String> = emptySet(),
    val mutedConvs: Set<String> = emptySet(),

    val selectedServerId: String? = null,

    val myId: String? = null,
    val myName: String = "",
    val myUsername: String = "",
    val myAvatar: String? = null,
    val myBanner: String? = null,
    val myBannerColor: String? = null,
    val myFont: String = "serif",
    val myBio: String? = null,
    val myPronouns: String? = null,
    val myCreatedAt: String? = null,
    val myStatus: UserStatus = UserStatus.ONLINE,
    val myCustomStatus: String? = null,
    val myBadges: List<BadgeUi> = emptyList(),

    val opening: Boolean = false,
    val openError: String? = null,

    val creating: Boolean = false,
    val createError: String? = null,

    val manageError: String? = null,

    val unreadNotifs: Int = 0,

    val needsOnboarding: Boolean = false,

    val needsEmailVerify: Boolean = false,

    val needsPassword: Boolean = false,
    val pwSaving: Boolean = false,
    val pwError: String? = null,
)
