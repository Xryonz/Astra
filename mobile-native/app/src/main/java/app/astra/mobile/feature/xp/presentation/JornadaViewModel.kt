package app.astra.mobile.feature.xp.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.network.dto.PainelMissoesDto
import app.astra.mobile.core.network.dto.ProgressoDto
import app.astra.mobile.core.xp.Estrelas
import app.astra.mobile.core.xp.Missoes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JornadaViewModel @Inject constructor(
    private val missoes: Missoes,
    private val estrelas: Estrelas,
) : ViewModel() {

    val painel: StateFlow<PainelMissoesDto?> = missoes.painel
    val progresso: StateFlow<ProgressoDto> = estrelas.progresso

    private val _resgatando = MutableStateFlow(false)
    val resgatando: StateFlow<Boolean> = _resgatando.asStateFlow()

    init { recarregar() }

    fun recarregar() {
        viewModelScope.launch { missoes.recarregar() }
        viewModelScope.launch { estrelas.recarregar() }
    }

    fun resgatar(id: String) {
        viewModelScope.launch {
            missoes.resgatar(id)
            estrelas.recarregar()
        }
    }

    fun resgatarTudo() {
        if (_resgatando.value) return
        _resgatando.value = true
        viewModelScope.launch {
            missoes.resgatarTudo()
            estrelas.recarregar()
            _resgatando.value = false
        }
    }
}
