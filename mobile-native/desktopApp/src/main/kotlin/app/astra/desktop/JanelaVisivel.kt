package app.astra.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object JanelaVisivel {
    private val _ativa = MutableStateFlow(true)
    val ativa: StateFlow<Boolean> = _ativa.asStateFlow()

    fun marcar(v: Boolean) {
        _ativa.value = v
    }
}
