package app.astra.desktop.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object Transmitindo {
    private val _ativo = MutableStateFlow(false)
    val ativo: StateFlow<Boolean> = _ativo
    fun marcar(v: Boolean) { _ativo.value = v }
}
