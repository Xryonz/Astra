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

// Ligacao recebida (lado de quem ATENDE) — vive no AstraApp pra tocar em qualquer
// tela. Espelha o IncomingCallModal do web: 30s de toque; reject do caller limpa.
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
            // Caller cancelou antes de atender.
            socketManager.dmCallReject.collect { convId ->
                if (_incoming.value?.conversationId == convId) {
                    timeout?.cancel()
                    _incoming.value = null
                }
            }
        }
    }

    // Devolve o convite pra tela navegar pro CallScreen.
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
