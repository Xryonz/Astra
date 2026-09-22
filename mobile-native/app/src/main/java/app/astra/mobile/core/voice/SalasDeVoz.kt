package app.astra.mobile.core.voice

import app.astra.mobile.core.realtime.SocketManager
import app.astra.mobile.feature.server.domain.ServerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SalasDeVoz @Inject constructor(
    private val serverRepository: ServerRepository,
    socketManager: SocketManager,
) {
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _quem = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val quem: StateFlow<Map<String, List<String>>> = _quem.asStateFlow()

    init {
        escopo.launch {
            socketManager.voicePresence.collect { mudanca ->
                if (mudanca.entrou) entrou(mudanca.salaId, mudanca.userId) else saiu(mudanca.salaId, mudanca.userId)
            }
        }
    }

    suspend fun carregar(salaIds: Collection<String>) {
        if (salaIds.isEmpty()) return
        val lidas = serverRepository.voicePresence(salaIds.toList()).getOrNull() ?: return
        _quem.update { atual -> atual - salaIds.toSet() + lidas.filterValues { it.isNotEmpty() } }
    }

    fun entrou(salaId: String, userId: String?) {
        if (userId.isNullOrBlank()) return
        _quem.update { atual ->
            val lista = atual[salaId].orEmpty()
            if (userId in lista) atual else atual + (salaId to lista + userId)
        }
    }

    fun saiu(salaId: String, userId: String?) {
        if (userId.isNullOrBlank()) return
        _quem.update { atual ->
            val lista = atual[salaId].orEmpty() - userId
            if (lista.isEmpty()) atual - salaId else atual + (salaId to lista)
        }
    }
}
