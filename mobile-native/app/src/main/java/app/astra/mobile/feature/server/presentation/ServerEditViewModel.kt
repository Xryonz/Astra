package app.astra.mobile.feature.server.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.upload.ImageUploader
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
    private val uploader: ImageUploader,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val serverId: String = savedStateHandle["serverId"] ?: ""

    private val _state = MutableStateFlow(ServerEditUiState())
    val state = _state.asStateFlow()

    init { load() }

    // Acha a Constelacao na lista (GET /api/servers ja traz nome + icone).
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
                            )
                        }
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun onName(v: String) = _state.update { it.copy(name = v, saved = false, error = null) }

    // Upload do icone (limite 5MB, igual Capacitor). Preenche; SALVAR persiste.
    fun uploadIcon(bytes: ByteArray, mime: String, filename: String) {
        if (bytes.size > MAX_ICON) {
            _state.update { it.copy(error = "Icone maior que 5MB.") }
            return
        }
        _state.update { it.copy(uploadingIcon = true, error = null, saved = false) }
        viewModelScope.launch {
            uploader.upload(bytes, mime, filename)
                .onSuccess { url -> _state.update { it.copy(uploadingIcon = false, iconUrl = url) } }
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
            )
                .onSuccess { srv ->
                    _state.update {
                        it.copy(
                            saving = false, saved = true,
                            name = srv.name, origName = srv.name,
                            iconUrl = srv.iconUrl.orEmpty(), origIcon = srv.iconUrl.orEmpty(),
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(saving = false, error = e.message) } }
        }
    }

    private companion object {
        const val MAX_ICON = 5 * 1024 * 1024
    }
}
