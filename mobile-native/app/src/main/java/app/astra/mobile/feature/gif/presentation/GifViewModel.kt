package app.astra.mobile.feature.gif.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.network.GifApi
import app.astra.mobile.core.network.dto.GifResultDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GifUiState(
    val enabled: Boolean? = null,
    val query: String = "",
    val results: List<GifResultDto> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class GifViewModel @Inject constructor(
    private val api: GifApi,
) : ViewModel() {
    private val _state = MutableStateFlow(GifUiState())
    val state = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val ok = runCatching { api.enabled().data?.enabled ?: false }.getOrDefault(false)
            _state.update { it.copy(enabled = ok) }
            if (ok) loadFeatured()
        }
    }

    private suspend fun loadFeatured() {
        _state.update { it.copy(loading = true, error = null) }
        runCatching { api.featured().data?.results.orEmpty() }
            .onSuccess { r -> _state.update { it.copy(results = r, loading = false) } }
            .onFailure { _state.update { it.copy(loading = false, error = "Falha ao carregar GIFs") } }
    }

    fun onQuery(q: String) {
        _state.update { it.copy(query = q) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            val term = q.trim()
            if (term.length < 2) {
                if (_state.value.enabled == true) loadFeatured()
                return@launch
            }
            _state.update { it.copy(loading = true, error = null) }
            runCatching { api.search(term).data?.results.orEmpty() }
                .onSuccess { r -> _state.update { it.copy(results = r, loading = false) } }
                .onFailure { _state.update { it.copy(loading = false, error = "Falha na busca") } }
        }
    }
}
