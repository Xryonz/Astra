package app.astra.mobile.feature.server.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.data.TokenStore
import app.astra.mobile.core.network.ServerApi
import app.astra.mobile.core.network.dto.BanRequest
import app.astra.mobile.core.network.dto.MemberRoleRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MemberUi(
    val memberId: String,
    val userId: String,
    val name: String,
    val username: String,
    val avatarUrl: String?,
    val role: String, // OWNER | ADMIN | MEMBER
)

data class ServerMembersUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val isOwner: Boolean = false,
    val canKick: Boolean = false,
    val canBan: Boolean = false,
    val myUserId: String? = null,
    val members: List<MemberUi> = emptyList(),
    val actionError: String? = null,
)

@HiltViewModel
class ServerMembersViewModel @Inject constructor(
    private val serverApi: ServerApi,
    private val tokenStore: TokenStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val serverId: String = savedStateHandle["serverId"] ?: ""

    private val _state = MutableStateFlow(ServerMembersUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val membersD = async { runCatching { serverApi.members(serverId).data.orEmpty() } }
            val permsD = async { runCatching { serverApi.myPerms(serverId).data } }
            val myId = tokenStore.currentUserId()
            val members = membersD.await().getOrNull()
            if (members == null) {
                _state.update { it.copy(loading = false, error = "Sem conexao com o servidor") }
                return@launch
            }
            val perms = permsD.await().getOrNull()
            _state.update {
                it.copy(
                    loading = false,
                    myUserId = myId,
                    isOwner = perms?.isOwner == true,
                    canKick = perms?.isOwner == true || perms?.isAdmin == true ||
                        perms?.permissions.orEmpty().contains("KICK_MEMBERS"),
                    canBan = perms?.isOwner == true ||
                        perms?.permissions.orEmpty().contains("BAN_MEMBERS"),
                    members = members.map { m ->
                        MemberUi(
                            memberId = m.id,
                            userId = m.userId,
                            name = m.user.displayName ?: m.user.username,
                            username = m.user.username,
                            avatarUrl = m.user.avatarUrl,
                            role = m.role,
                        )
                    },
                )
            }
        }
    }

    fun setAdmin(memberId: String, admin: Boolean) {
        val prev = _state.value.members
        val role = if (admin) "ADMIN" else "MEMBER"
        _state.update {
            it.copy(members = it.members.map { m -> if (m.memberId == memberId) m.copy(role = role) else m })
        }
        viewModelScope.launch {
            try {
                serverApi.setMemberRole(serverId, memberId, MemberRoleRequest(role))
            } catch (e: Exception) {
                _state.update { it.copy(members = prev, actionError = "Nao foi possivel mudar o papel") }
            }
        }
    }

    fun kick(memberId: String) {
        val prev = _state.value.members
        _state.update { it.copy(members = it.members.filterNot { m -> m.memberId == memberId }) }
        viewModelScope.launch {
            try {
                serverApi.kickMember(serverId, memberId)
            } catch (e: Exception) {
                _state.update { it.copy(members = prev, actionError = "Nao foi possivel expulsar") }
            }
        }
    }

    fun ban(userId: String, reason: String?) {
        val prev = _state.value.members
        _state.update { it.copy(members = it.members.filterNot { m -> m.userId == userId }) }
        viewModelScope.launch {
            try {
                serverApi.banMember(serverId, BanRequest(userId, reason?.trim()?.ifBlank { null }))
            } catch (e: Exception) {
                _state.update { it.copy(members = prev, actionError = "Nao foi possivel banir") }
            }
        }
    }

    fun clearActionError() = _state.update { it.copy(actionError = null) }
}
