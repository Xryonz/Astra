package app.astra.desktop.prefs

import app.astra.desktop.auth.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Preferencias LOCAIS do desktop (nao vao pro backend): movimento e toasts da
// bandeja. Persistem no ui.properties (mesmo arquivo da ultima selecao, que
// sobrevive a logout). StateFlow pra UI e shell reagirem na hora que muda.
class DesktopPrefs(private val store: SessionStore) {
    data class Prefs(
        // Reduz/desliga as animacoes de fundo (aurora, cascata, pulsos).
        val reduceMotion: Boolean = false,
        // Toast na bandeja quando chega DM / atividade de canal (janela oculta).
        val notifyDms: Boolean = true,
        val notifyChannels: Boolean = true,
    )

    private val _state = MutableStateFlow(read())
    val state = _state.asStateFlow()

    // Ausente = default (ligado pros toasts, desligado pro reduceMotion).
    private fun read() = Prefs(
        reduceMotion = store.uiPref("reduceMotion") == "1",
        notifyDms = store.uiPref("notifyDms") != "0",
        notifyChannels = store.uiPref("notifyChannels") != "0",
    )

    private fun persist(key: String, on: Boolean) = store.setUiPref(key, if (on) "1" else "0")

    fun setReduceMotion(v: Boolean) {
        persist("reduceMotion", v)
        _state.update { it.copy(reduceMotion = v) }
    }

    fun setNotifyDms(v: Boolean) {
        persist("notifyDms", v)
        _state.update { it.copy(notifyDms = v) }
    }

    fun setNotifyChannels(v: Boolean) {
        persist("notifyChannels", v)
        _state.update { it.copy(notifyChannels = v) }
    }
}
