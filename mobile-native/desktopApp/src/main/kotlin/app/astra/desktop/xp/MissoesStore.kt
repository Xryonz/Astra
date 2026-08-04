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

// Missoes, do lado do app.
//
// Duas coisas diferentes, de proposito:
//
//   `painel`     — ESTADO. O que a tela desenha. Buscado ao abrir a tela.
//   `concluidas` — EVENTO. "fechei a missao X" ACONTECE. Num StateFlow, fechar duas
//                  missoes iguais seguidas seria o mesmo valor e o segundo aviso
//                  nunca apareceria.
//
// O painel se atualiza sozinho quando um evento chega, sem voltar no servidor: o
// evento diz qual missao fechou, e marcar aquela linha como concluida da o mesmo
// resultado que refazer a busca inteira. O progresso PARCIAL (2 de 5) e a unica
// coisa que so a busca sabe — e ela roda toda vez que a tela abre, que e o unico
// momento em que alguem esta olhando.
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

    // Falha em silencio: a tela mostra o vazio dela. Missao nunca pode virar erro na
    // cara de quem so queria ver o progresso.
    suspend fun recarregar() {
        val p = withContext(Dispatchers.IO) { runCatching { api.painel().data }.getOrNull() } ?: return
        _painel.value = p
    }

    fun limpar() {
        _painel.value = null
    }
}

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
