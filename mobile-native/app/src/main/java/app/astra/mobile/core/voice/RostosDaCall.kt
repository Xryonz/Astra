package app.astra.mobile.core.voice

import app.astra.mobile.feature.profile.domain.UserRepository
import app.astra.mobile.feature.server.domain.ServerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class Rosto(val nome: String, val foto: String?)

@Singleton
class RostosDaCall @Inject constructor(
    voiceManager: VoiceManager,
    private val serverRepository: ServerRepository,
    private val userRepository: UserRepository,
) {
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _rostos = MutableStateFlow<Map<String, Rosto>>(emptyMap())
    val rostos: StateFlow<Map<String, Rosto>> = _rostos.asStateFlow()

    init {
        escopo.launch {
            voiceManager.state.map { it.sala }.distinctUntilChanged().collectLatest { sala ->
                if (sala == null) {
                    _rostos.value = emptyMap()
                    return@collectLatest
                }
                val lidos = HashMap<String, Rosto>()
                sala.orbitaId?.let { orbita ->
                    serverRepository.members(orbita).getOrNull()?.forEach { lidos[it.userId] = Rosto(it.name, it.avatarUrl) }
                }
                userRepository.me().getOrNull()?.let { eu ->
                    lidos[eu.id] = Rosto(eu.displayName.ifBlank { eu.username }, eu.avatarUrl)
                }
                _rostos.value = lidos
            }
        }
    }

    companion object {
        fun rostoPadrao(sala: SalaDaCall?): Rosto =
            if (sala?.tipo == VoiceManager.TIPO_SUSSURRO) Rosto(sala.nome, sala.fotoDoSussurro)
            else Rosto("Alguém", null)
    }
}
