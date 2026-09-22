package app.astra.mobile.feature.xp.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.network.dto.ProgressoDto
import app.astra.mobile.core.xp.Estrelas
import app.astra.mobile.core.xp.Missoes
import app.astra.mobile.core.xp.quantasProntas
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EstrelasViewModel @Inject constructor(
    val estrelas: Estrelas,
    private val missoes: Missoes,
) : ViewModel() {

    val progresso: StateFlow<ProgressoDto> = estrelas.progresso

    val prontas: StateFlow<Int> = missoes.painel
        .map { it?.quantasProntas() ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init { recarregar() }

    fun recarregar() {
        viewModelScope.launch { estrelas.recarregar() }
        viewModelScope.launch { missoes.recarregar() }
    }
}
