package app.astra.mobile.core.xp

import app.astra.mobile.core.network.MissionApi
import app.astra.mobile.core.network.dto.MissaoConcluidaDto
import app.astra.mobile.core.network.dto.PainelMissoesDto
import app.astra.mobile.core.realtime.SocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Missoes @Inject constructor(
    private val api: MissionApi,
    socketManager: SocketManager,
) {
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    private val _painel = MutableStateFlow<PainelMissoesDto?>(null)
    val painel: StateFlow<PainelMissoesDto?> = _painel.asStateFlow()

    private val _concluidas = MutableSharedFlow<MissaoConcluidaDto>(extraBufferCapacity = 8)
    val concluidas: SharedFlow<MissaoConcluidaDto> = _concluidas.asSharedFlow()

    init {
        escopo.launch {
            socketManager.missaoConcluida.collect { cru ->
                val feita = runCatching { json.decodeFromString<MissaoConcluidaDto>(cru) }.getOrNull()
                    ?: return@collect
                _painel.value = _painel.value?.comMissaoConcluida(feita.id)
                _concluidas.tryEmit(feita)
            }
        }
    }

    suspend fun recarregar() {
        val lido = runCatching { api.painel().data }.getOrNull() ?: return
        _painel.value = lido
    }

    suspend fun resgatar(id: String): Boolean {
        val feito = runCatching { api.resgatar(id).data }.getOrNull()
        if (feito == null) {
            recarregar()
            return false
        }
        _painel.value = _painel.value?.comMissaoResgatada(id)
        return true
    }

    suspend fun resgatarTudo(): Int {
        val feitos = runCatching { api.resgatarTudo().data?.resgates }.getOrNull()
        if (feitos == null) {
            recarregar()
            return 0
        }
        _painel.value = feitos.fold(_painel.value) { painel, resgate -> painel?.comMissaoResgatada(resgate.id) }
        return feitos.size
    }
}

fun PainelMissoesDto.quantasProntas(): Int =
    diarias.itens.count { it.resgatavel } +
        (if (diarias.bonus.resgatavel) 1 else 0) +
        semanais.itens.count { it.resgatavel } +
        conquistas.itens.count { it.resgatavel }

private fun PainelMissoesDto.comMissaoResgatada(id: String): PainelMissoesDto = copy(
    diarias = diarias.copy(
        itens = diarias.itens.map { if (it.id == id) it.copy(resgatada = true) else it },
        bonus = if (diarias.bonus.id == id) diarias.bonus.copy(resgatada = true) else diarias.bonus,
    ),
    semanais = semanais.copy(
        itens = semanais.itens.map { if (it.id == id) it.copy(resgatada = true) else it },
    ),
    conquistas = conquistas.copy(
        itens = conquistas.itens.map { if (it.id == id) it.copy(resgatada = true) else it },
    ),
)

private fun PainelMissoesDto.comMissaoConcluida(id: String): PainelMissoesDto = copy(
    diarias = diarias.copy(
        itens = diarias.itens.map { if (it.id == id) it.copy(progresso = it.alvo, concluida = true) else it },
        bonus = if (diarias.bonus.id == id) diarias.bonus.copy(progresso = diarias.bonus.alvo, concluida = true)
        else diarias.bonus,
    ),
    semanais = semanais.copy(
        itens = semanais.itens.map { if (it.id == id) it.copy(progresso = it.alvo, concluida = true) else it },
    ),
    conquistas = conquistas.copy(
        itens = conquistas.itens.map { if (it.id == id) it.copy(progresso = it.alvo, concluida = true) else it },
    ),
)
