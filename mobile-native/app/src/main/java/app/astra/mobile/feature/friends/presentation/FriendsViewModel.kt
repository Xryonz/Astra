package app.astra.mobile.feature.friends.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.feature.friends.domain.FriendsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val repo: FriendsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FriendsUiState())
    val state = _state.asStateFlow()

    private val _added = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val added = _added.asSharedFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {

            val f = async { repo.friends() }
            val inc = async { repo.incoming() }
            val out = async { repo.outgoing() }
            val fr = f.await(); val ir = inc.await(); val or = out.await()
            _state.update {
                it.copy(
                    loading = false,
                    friends = fr.getOrDefault(emptyList()),
                    incoming = ir.getOrDefault(emptyList()),
                    outgoing = or.getOrDefault(emptyList()),

                    error = if (fr.isFailure && ir.isFailure && or.isFailure) {
                        fr.exceptionOrNull()?.message ?: "Erro ao carregar"
                    } else null,
                )
            }
        }
    }

    fun selectTab(t: FriendsTab) = _state.update { it.copy(tab = t) }

    fun sendRequest(username: String) {
        if (username.isBlank() || _state.value.adding) return
        _state.update { it.copy(adding = true, addError = null) }
        viewModelScope.launch {
            repo.sendRequest(username)
                .onSuccess {
                    _state.update { it.copy(adding = false) }
                    _added.tryEmit(Unit)
                    load()
                }
                .onFailure { e -> _state.update { it.copy(adding = false, addError = e.message) } }
        }
    }

    fun accept(friendshipId: String) {
        viewModelScope.launch {
            repo.accept(friendshipId)
                .onSuccess { load() }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun remove(friendshipId: String) {
        viewModelScope.launch {
            repo.remove(friendshipId)
                .onSuccess { load() }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun clearAddError() = _state.update { it.copy(addError = null) }
}
