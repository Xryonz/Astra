package app.astra.mobile.feature.voice.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.realtime.DmCallInvite
import app.astra.mobile.core.realtime.SocketManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IncomingCallViewModel @Inject constructor(
    private val socketManager: SocketManager,
) : ViewModel() {

    private val _incoming = MutableStateFlow<DmCallInvite?>(null)
    val incoming: StateFlow<DmCallInvite?> = _incoming.asStateFlow()

    private var timeout: Job? = null

    init {
        viewModelScope.launch {
            socketManager.dmCallInvite.collect { inv ->
                _incoming.value = inv
                timeout?.cancel()
                timeout = viewModelScope.launch {
                    delay(30_000)
                    if (_incoming.value?.conversationId == inv.conversationId) _incoming.value = null
                }
            }
        }
        viewModelScope.launch {
            socketManager.dmCallReject.collect { convId ->
                if (_incoming.value?.conversationId == convId) {
                    timeout?.cancel()
                    _incoming.value = null
                }
            }
        }
    }

    fun accept(): DmCallInvite? {
        val inv = _incoming.value ?: return null
        socketManager.sendDmCallAccept(inv.conversationId, inv.fromUserId)
        timeout?.cancel()
        _incoming.value = null
        return inv
    }

    fun reject() {
        val inv = _incoming.value ?: return
        socketManager.sendDmCallReject(inv.conversationId, inv.fromUserId)
        timeout?.cancel()
        _incoming.value = null
    }
}
