package app.astra.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.astra.desktop.ui.UserStatus

object EstadoNaBandeja {

    var atual: UserStatus? by mutableStateOf(null)
        private set

    private var escolher: ((UserStatus) -> Unit)? = null

    fun assumir(status: UserStatus, aoEscolher: (UserStatus) -> Unit) {
        atual = status
        escolher = aoEscolher
    }

    fun largar() {
        atual = null
        escolher = null
    }

    fun escolher(status: UserStatus) {
        escolher?.invoke(status)
    }
}
