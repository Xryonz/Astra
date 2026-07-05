package app.astra.mobile.feature.server.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.network.ServerApi
import app.astra.mobile.core.upload.ImageEncoder
import app.astra.mobile.feature.server.domain.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServerEditViewModel @Inject constructor(
    private val repository: ServerRepository,
    private val serverApi: ServerApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val serverId: String = savedStateHandle["serverId"] ?: ""

    private val _state = MutableStateFlow(ServerEditUiState())
    val state = _state.asStateFlow()

    init {
        load()
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
                                bannerUrl = s.bannerUrl.orEmpty(), origBanner = s.bannerUrl.orEmpty(),
                                description = s.description.orEmpty(), origDescription = s.description.orEmpty(),
                                retentionDays = s.messageRetentionDays ?: 0, origRetention = s.messageRetentionDays ?: 0,
                                isPublic = s.isPublic, origPublic = s.isPublic,
                                inviteCode = s.inviteCode,
                            )
                        }
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun onName(v: String) = _state.update { it.copy(name = v, saved = false, error = null) }
    fun onDescription(v: String) = _state.update { it.copy(description = v.take(200), saved = false, error = null) }
    fun onRetention(v: Int) = _state.update { it.copy(retentionDays = v, saved = false, error = null) }
    fun onPublic(v: Boolean) = _state.update { it.copy(isPublic = v, saved = false, error = null) }
    fun removeBanner() = _state.update { it.copy(bannerUrl = "", saved = false, error = null) }

    fun uploadIcon(bytes: ByteArray, mime: String) {
        _state.update { it.copy(uploadingIcon = true, error = null, saved = false) }
        viewModelScope.launch {
            ImageEncoder.toDataUri(bytes, mime, ICON_DIM, ICON_GIF_MAX)
                .onSuccess { uri -> _state.update { it.copy(uploadingIcon = false, iconUrl = uri) } }
                .onFailure { e -> _state.update { it.copy(uploadingIcon = false, error = e.message) } }
        }
    }

    fun uploadBanner(bytes: ByteArray, mime: String) {
        _state.update { it.copy(uploadingBanner = true, error = null, saved = false) }
        viewModelScope.launch {
            ImageEncoder.toDataUri(bytes, mime, BANNER_DIM, BANNER_GIF_MAX)
                .onSuccess { uri -> _state.update { it.copy(uploadingBanner = false, bannerUrl = uri) } }
                .onFailure { e -> _state.update { it.copy(uploadingBanner = false, error = e.message) } }
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
                // "" limpa o banner (explicitNulls=false omitiria null); backend guarda ""
                // e o cliente renderiza vazio como sem-banner.
                bannerUrl = s.bannerUrl.takeIf { it != s.origBanner },
                description = s.description.trim().takeIf { it != s.origDescription },
                messageRetentionDays = s.retentionDays.takeIf { it != s.origRetention },
                isPublic = s.isPublic.takeIf { it != s.origPublic },
            )
                .onSuccess { srv ->
                    _state.update {
                        it.copy(
                            saving = false, saved = true,
                            name = srv.name, origName = srv.name,
                            iconUrl = srv.iconUrl.orEmpty(), origIcon = srv.iconUrl.orEmpty(),
                            bannerUrl = srv.bannerUrl.orEmpty(), origBanner = srv.bannerUrl.orEmpty(),
                            description = srv.description.orEmpty(), origDescription = srv.description.orEmpty(),
                            retentionDays = srv.messageRetentionDays ?: 0, origRetention = srv.messageRetentionDays ?: 0,
                            isPublic = srv.isPublic, origPublic = srv.isPublic,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(saving = false, error = e.message) } }
        }
    }

    fun regenerateInvite() {
        if (_state.value.regenerating) return
        _state.update { it.copy(regenerating = true, error = null) }
        viewModelScope.launch {
            try {
                val code = serverApi.regenerateInvite(serverId).data?.inviteCode
                _state.update { it.copy(regenerating = false, inviteCode = code ?: it.inviteCode) }
            } catch (e: Exception) {
                _state.update { it.copy(regenerating = false, error = "Nao foi possivel regenerar o convite") }
            }
        }
    }

    private companion object {
        const val ICON_DIM = 512
        const val ICON_GIF_MAX = 4_500_000
        const val BANNER_DIM = 1024
        const val BANNER_GIF_MAX = 7_500_000
    }
}
