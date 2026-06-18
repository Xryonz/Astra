package app.astra.mobile.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.realtime.SocketManager
import app.astra.mobile.feature.auth.domain.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Gate de sessao no topo da app. isLoggedIn = null enquanto o DataStore ainda
 * nao respondeu — evita o flash da tela de login num cold start ja autenticado.
 * Tambem orquestra o socket: conecta ao logar, desconecta ao sair.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    repository: AuthRepository,
    socketManager: SocketManager,
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean?> = repository.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            repository.isLoggedIn.distinctUntilChanged().collect { logged ->
                if (logged) socketManager.connect() else socketManager.disconnect()
            }
        }
    }
}
