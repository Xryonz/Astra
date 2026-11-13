package app.astra.desktop.voice

import app.astra.desktop.auth.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.context.GlobalContext
import kotlin.concurrent.thread

enum class PedidoAoWindows { NENHUM, PERGUNTANDO, LIBERANDO }

object PreparoDaVoz {
    private const val REDE_PEDIDA = "firewall:pedido"
    private const val REDE_DISPENSADA = "firewall:dispensado"
    private const val MICROFONE_DISPENSADO = "microfone:dispensado"

    private val store: SessionStore get() = GlobalContext.get().get()

    private val _pedido = MutableStateFlow(PedidoAoWindows.NENHUM)
    val pedido = _pedido.asStateFlow()

    fun precisaLiberarARede(): Boolean {
        if (store.uiPref(REDE_PEDIDA) == "1" || redeDispensada()) return false
        val rede = PermissoesWindows.rede()
        return rede.podeResolver && rede.acesso != Acesso.OK
    }

    fun pedirALiberacao() {
        store.setUiPref(REDE_PEDIDA, "1")
        _pedido.value = PedidoAoWindows.PERGUNTANDO
        thread(isDaemon = true, name = "astra-firewall") {
            runCatching {
                PermissoesWindows.liberarNoFirewall(aoAceitar = {
                    _pedido.compareAndSet(PedidoAoWindows.PERGUNTANDO, PedidoAoWindows.LIBERANDO)
                })
            }
            _pedido.value = PedidoAoWindows.NENHUM
        }
    }

    fun redeDispensada(): Boolean = store.uiPref(REDE_DISPENSADA) == "1"

    fun dispensarARede() = store.setUiPref(REDE_DISPENSADA, "1")

    fun microfoneComProblema(): Checagem? =
        if (store.uiPref(MICROFONE_DISPENSADO) == "1") null else PermissoesWindows.microfoneSemAbrir()

    fun dispensarOMicrofone() = store.setUiPref(MICROFONE_DISPENSADO, "1")
}
