package app.astra.desktop.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object QuemFala {
    private val _alguem = MutableStateFlow(false)
    val alguem: StateFlow<Boolean> = _alguem
    fun marcar(v: Boolean) { _alguem.value = v }
}
