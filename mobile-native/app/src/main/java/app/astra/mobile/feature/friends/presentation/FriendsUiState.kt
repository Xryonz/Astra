package app.astra.mobile.feature.friends.presentation

import app.astra.mobile.feature.friends.domain.model.Friend
import app.astra.mobile.feature.friends.domain.model.FriendRequest

enum class FriendsTab { EM_ORBITA, AMIGOS, PEDIDOS }

data class FriendsUiState(
    val loading: Boolean = true,
    val tab: FriendsTab = FriendsTab.EM_ORBITA,
    val friends: List<Friend> = emptyList(),
    val incoming: List<FriendRequest> = emptyList(),
    val outgoing: List<FriendRequest> = emptyList(),
    val error: String? = null,

    val adding: Boolean = false,
    val addError: String? = null,
)
