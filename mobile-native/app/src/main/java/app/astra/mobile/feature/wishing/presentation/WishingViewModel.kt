package app.astra.mobile.feature.wishing.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.network.WishApi
import app.astra.mobile.core.network.dto.PostWishRequest
import app.astra.mobile.core.network.dto.WishDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PAGE = 20
const val WISH_MIN = 4
const val WISH_MAX = 500

data class WishingUiState(
    val loading: Boolean = true,
    val items: List<WishDto> = emptyList(),
    val nextCursor: String? = null,
    val loadingMore: Boolean = false,
    val input: String = "",
    val posting: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class WishingViewModel @Inject constructor(
    private val api: WishApi,
) : ViewModel() {
    private val _state = MutableStateFlow(WishingUiState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { api.wishes(PAGE, null).data }
                .onSuccess { page ->
                    _state.update { it.copy(loading = false, items = page?.items.orEmpty(), nextCursor = page?.nextCursor) }
                }
                .onFailure { _state.update { it.copy(loading = false, error = "Falha ao ler o ceu") } }
        }
    }

    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        if (_state.value.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            runCatching { api.wishes(PAGE, cursor).data }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            loadingMore = false,
                            items = it.items + page?.items.orEmpty(),
                            nextCursor = page?.nextCursor,
                        )
                    }
                }
                .onFailure { _state.update { it.copy(loadingMore = false) } }
        }
    }

    fun onInput(v: String) = _state.update { it.copy(input = v.take(WISH_MAX), error = null) }

    fun post() {
        val text = _state.value.input.trim()
        if (text.length < WISH_MIN || _state.value.posting) return
        _state.update { it.copy(posting = true, error = null) }
        viewModelScope.launch {
            runCatching { api.post(PostWishRequest(text)) }
                .onSuccess {
                    _state.update { it.copy(posting = false, input = "") }
                    load()
                }
                .onFailure { _state.update { it.copy(posting = false, error = "Nao foi possivel pendurar o desejo") } }
        }
    }
}
