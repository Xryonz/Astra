package app.astra.desktop.net

import app.astra.shared.AstraShared
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object Servidor {
    enum class Estado { CONFERINDO, NO_AR, ACORDANDO }

    private val _estado = MutableStateFlow(Estado.CONFERINDO)
    val estado: StateFlow<Estado> = _estado

    private val _esperandoHa = MutableStateFlow(0)
    val esperandoHa: StateFlow<Int> = _esperandoHa

    private val cliente by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    private val url = AstraShared.BASE_URL.trimEnd('/') + "/health"

    private var vigiando = false

    fun vigiar(escopo: CoroutineScope) {
        if (vigiando) return
        vigiando = true
        escopo.launch {
            val inicio = System.currentTimeMillis()
            var primeira = true
            while (isActive) {
                if (bateu()) {
                    _estado.value = Estado.NO_AR
                    return@launch
                }
                if (!primeira) _estado.value = Estado.ACORDANDO
                primeira = false
                _esperandoHa.value = ((System.currentTimeMillis() - inicio) / 1000).toInt()
                delay(2_000)
            }
        }
    }

    private suspend fun bateu(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            cliente.newCall(Request.Builder().url(url).build()).execute().use {
                it.code == 200 || it.code == 503
            }
        }.getOrDefault(false)
    }
}
