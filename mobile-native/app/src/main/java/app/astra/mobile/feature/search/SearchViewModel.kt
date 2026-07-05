package app.astra.mobile.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.network.SearchApi
import app.astra.mobile.core.network.dto.SearchResultsDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: SearchResultsDto = SearchResultsDto(),
) {
    val empty: Boolean
        get() = results.messages.isEmpty() && results.channels.isEmpty() &&
            results.users.isEmpty() && results.servers.isEmpty()
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchApi: SearchApi,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state = _state.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            queryFlow
                .debounce(300)
                .distinctUntilChanged()
                .collect { q -> run(q) }
        }
    }

    fun onQuery(q: String) {
        _state.update { it.copy(query = q) }
        queryFlow.value = q
    }

    private suspend fun run(q: String) {
        val term = q.trim()
        if (term.length < 2) {
            _state.update { it.copy(loading = false, results = SearchResultsDto()) }
            return
        }
        _state.update { it.copy(loading = true) }
        try {
            val res = searchApi.search(term).data ?: SearchResultsDto()
            // Ignora resposta de uma query ja superada.
            if (_state.value.query.trim() == term) {
                _state.update { it.copy(loading = false, results = res) }
            }
        } catch (e: Exception) {
            if (_state.value.query.trim() == term) {
                _state.update { it.copy(loading = false, results = SearchResultsDto()) }
            }
        }
    }
}
