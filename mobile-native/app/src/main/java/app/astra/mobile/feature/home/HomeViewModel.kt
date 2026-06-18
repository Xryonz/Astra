package app.astra.mobile.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.realtime.ConnectionState
import app.astra.mobile.core.realtime.SocketManager
import app.astra.mobile.feature.auth.domain.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: AuthRepository,
    socketManager: SocketManager,
) : ViewModel() {
    val socketState: StateFlow<ConnectionState> = socketManager.state

    fun logout() = viewModelScope.launch { repository.logout() }
}
