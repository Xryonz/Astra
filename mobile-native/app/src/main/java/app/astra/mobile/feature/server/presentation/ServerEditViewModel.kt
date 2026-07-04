package app.astra.mobile.feature.server.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.network.BadgesApi
import app.astra.mobile.core.network.ServerApi
import app.astra.mobile.core.network.dto.CreateBadgeRequest
import app.astra.mobile.core.network.dto.GrantBadgeRequest
import app.astra.mobile.core.upload.ImageEncoder
import app.astra.mobile.feature.server.domain.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServerEditViewModel @Inject constructor(
    private val repository: ServerRepository,
    private val badgesApi: BadgesApi,
    private val serverApi: ServerApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val serverId: String = savedStateHandle["serverId"] ?: ""

    private val _state = MutableStateFlow(ServerEditUiState())
    val state = _state.asStateFlow()

    init {
        load()
        loadBadges()
    }

    private fun loadBadges() {
        viewModelScope.launch {
            val badgesD = async { runCatching { badgesApi.serverBadges(serverId).data.orEmpty() }.getOrDefault(emptyList()) }
            val membersD = async { runCatching { serverApi.members(serverId).data.orEmpty() }.getOrDefault(emptyList()) }
            val badges = badgesD.await().map {
                ServerBadgeUi(it.id, it.name, it.icon, it.color, it.description, it.grantedUserIds.toSet())
            }
            val members = membersD.await().map {
                BadgeMemberUi(it.userId, it.user.displayName ?: it.user.username, it.user.avatarUrl)
            }
            _state.update { it.copy(badges = badges, members = members) }
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

    private fun load() {
        viewModelScope.launch {
            repository.servers()
                .onSuccess { list ->
                    val s = list.find { it.id == serverId }
                    if (s == null) {
                        _state.update { it.copy(loading = false, error = "Constelacao nao encontrada") }
                    } else {
                        _state.update {
                            it.copy(
                                loading = false,
                                name = s.name, origName = s.name,
                                iconUrl = s.iconUrl.orEmpty(), origIcon = s.iconUrl.orEmpty(),
                                isPublic = s.isPublic, origPublic = s.isPublic,
                            )
                        }
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun onName(v: String) = _state.update { it.copy(name = v, saved = false, error = null) }

    fun onPublic(v: Boolean) = _state.update { it.copy(isPublic = v, saved = false, error = null) }

    fun uploadIcon(bytes: ByteArray, mime: String) {
        _state.update { it.copy(uploadingIcon = true, error = null, saved = false) }
        viewModelScope.launch {
            ImageEncoder.toDataUri(bytes, mime, ICON_DIM, ICON_GIF_MAX)
                .onSuccess { uri -> _state.update { it.copy(uploadingIcon = false, iconUrl = uri) } }
                .onFailure { e -> _state.update { it.copy(uploadingIcon = false, error = e.message) } }
        }
    }

    fun save() {
        val s = _state.value
        if (s.saving || !s.dirty || s.name.isBlank()) return
        _state.update { it.copy(saving = true, error = null, saved = false) }
        viewModelScope.launch {
            repository.updateServer(
                id = serverId,
                name = s.name.trim().takeIf { it != s.origName },
                iconUrl = s.iconUrl.takeIf { it != s.origIcon },
                isPublic = s.isPublic.takeIf { it != s.origPublic },
            )
                .onSuccess { srv ->
                    _state.update {
                        it.copy(
                            saving = false, saved = true,
                            name = srv.name, origName = srv.name,
                            iconUrl = srv.iconUrl.orEmpty(), origIcon = srv.iconUrl.orEmpty(),
                            isPublic = srv.isPublic, origPublic = srv.isPublic,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(saving = false, error = e.message) } }
        }
    }

    private companion object {
        const val ICON_DIM = 512
        const val ICON_GIF_MAX = 4_500_000
    }
}
