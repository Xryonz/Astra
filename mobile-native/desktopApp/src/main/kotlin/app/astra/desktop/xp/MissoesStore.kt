package app.astra.desktop.xp

import app.astra.desktop.net.DesktopSocket
import app.astra.mobile.core.network.MissionApi
import app.astra.mobile.core.network.dto.MissaoConcluidaDto
import app.astra.mobile.core.network.dto.PainelMissoesDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class MissoesStore(
    private val api: MissionApi,
    private val socket: DesktopSocket,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _painel = MutableStateFlow<PainelMissoesDto?>(null)
    val painel: StateFlow<PainelMissoesDto?> = _painel.asStateFlow()

    private val _concluidas = MutableSharedFlow<MissaoConcluidaDto>(extraBufferCapacity = 8)
    val concluidas: SharedFlow<MissaoConcluidaDto> = _concluidas.asSharedFlow()

    fun iniciar(escopo: CoroutineScope) {
        escopo.launch {
            socket.missaoConcluida.collect { cru ->
                val m = runCatching { json.decodeFromString<MissaoConcluidaDto>(cru) }.getOrNull()
                    ?: return@collect
                _painel.value = _painel.value?.marcarConcluida(m.id)
                _concluidas.tryEmit(m)
            }
        }
    }

    suspend fun recarregar() {
        val p = withContext(Dispatchers.IO) { runCatching { api.painel().data }.getOrNull() } ?: return
        _painel.value = p
    }

    suspend fun resgatar(id: String): Boolean {
        val feito = withContext(Dispatchers.IO) { runCatching { api.resgatar(id).data }.getOrNull() }
        if (feito == null) { recarregar(); return false }
        _painel.value = _painel.value?.marcarResgatada(id)
        return true
    }

    suspend fun resgatarTudo(): Int {
        val feitos = withContext(Dispatchers.IO) { runCatching { api.resgatarTudo().data?.resgates }.getOrNull() }
        if (feitos == null) { recarregar(); return 0 }
        _painel.value = feitos.fold(_painel.value) { p, r -> p?.marcarResgatada(r.id) }
        return feitos.size
    }

    fun limpar() {
        _painel.value = null
    }
}

fun PainelMissoesDto.quantasProntas(): Int =
    diarias.itens.count { it.resgatavel } +
        (if (diarias.bonus.resgatavel) 1 else 0) +
        semanais.itens.count { it.resgatavel } +
        conquistas.itens.count { it.resgatavel }

private fun PainelMissoesDto.marcarResgatada(id: String): PainelMissoesDto = copy(
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

private fun PainelMissoesDto.marcarConcluida(id: String): PainelMissoesDto = copy(
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
