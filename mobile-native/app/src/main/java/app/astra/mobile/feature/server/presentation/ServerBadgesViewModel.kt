package app.astra.mobile.feature.server.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.network.BadgesApi
import app.astra.mobile.core.network.ServerApi
import app.astra.mobile.core.network.dto.CreateBadgeRequest
import app.astra.mobile.core.network.dto.GrantBadgeRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServerBadgesUiState(
    val loading: Boolean = true,
    val badges: List<ServerBadgeUi> = emptyList(),
    val members: List<BadgeMemberUi> = emptyList(),
    val badgeError: String? = null,
)

@HiltViewModel
class ServerBadgesViewModel @Inject constructor(
    private val badgesApi: BadgesApi,
    private val serverApi: ServerApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val serverId: String = savedStateHandle["serverId"] ?: ""

    private val _state = MutableStateFlow(ServerBadgesUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val badgesD = async { runCatching { badgesApi.serverBadges(serverId).data.orEmpty() }.getOrDefault(emptyList()) }
            val membersD = async { runCatching { serverApi.members(serverId).data.orEmpty() }.getOrDefault(emptyList()) }
            val badges = badgesD.await().map {
                ServerBadgeUi(it.id, it.name, it.icon, it.color, it.description, it.grantedUserIds.toSet())
            }
            val members = membersD.await().map {
                BadgeMemberUi(it.userId, it.user.displayName ?: it.user.username, it.user.avatarUrl)
            }
            _state.update { it.copy(loading = false, badges = badges, members = members) }
        }
    }

    fun createBadge(name: String, icon: String, color: String?, description: String?) {
        if (name.isBlank() || icon.isBlank()) return
        viewModelScope.launch {
            try {
                val b = badgesApi.createBadge(
                    serverId,
                    CreateBadgeRequest(name.trim(), icon.trim(), color, description?.trim()?.ifBlank { null }),
                ).data ?: return@launch
                _state.update {
                    it.copy(badges = it.badges + ServerBadgeUi(b.id, b.name, b.icon, b.color, b.description, emptySet()), badgeError = null)
                }
            } catch (e: Exception) {
                _state.update { it.copy(badgeError = "Nao foi possivel criar a insignia") }
            }
        }
    }

    fun deleteBadge(badgeId: String) {
        val prev = _state.value.badges
        _state.update { it.copy(badges = it.badges.filterNot { b -> b.id == badgeId }) }
        viewModelScope.launch {
            try {
                badgesApi.deleteBadge(serverId, badgeId)
            } catch (e: Exception) {
                _state.update { it.copy(badges = prev, badgeError = "Nao foi possivel apagar") }
            }
        }
    }

    fun toggleGrant(badgeId: String, userId: String, grant: Boolean) {
        val prev = _state.value.badges
        _state.update {
            it.copy(badges = it.badges.map { b ->
                if (b.id != badgeId) b
                else b.copy(grantedUserIds = if (grant) b.grantedUserIds + userId else b.grantedUserIds - userId)
            })
        }
        viewModelScope.launch {
            try {
                if (grant) badgesApi.grantBadge(serverId, badgeId, GrantBadgeRequest(userId))
                else badgesApi.revokeBadge(serverId, badgeId, userId)
            } catch (e: Exception) {
                _state.update { it.copy(badges = prev, badgeError = "Nao foi possivel atualizar") }
            }
        }
    }
}
