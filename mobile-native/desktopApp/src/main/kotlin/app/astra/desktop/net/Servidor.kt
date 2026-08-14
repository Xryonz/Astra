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

// A HOSPEDAGEM DORME, e ate agora o app nao contava isso pra ninguem.
//
// O plano gratuito do Render desliga a instancia depois de 15 minutos sem nenhuma
// requisicao e religa na proxima -- e religar leva perto de um minuto. Do lado de ca
// isso aparecia como o Astra inteiro parado: sem erro, sem barra, sem explicacao. Os
// timeouts do OkHttp ja eram folgados o bastante pra sobreviver (60s de connect), entao
// o app SEMPRE acabava entrando; so que durante esse minuto a unica leitura possivel pra
// quem esta olhando e "travou".
//
// Este objeto nao acelera nada. A espera e exatamente a mesma. Ele so pergunta ao
// /health -- que nao precisa de conta -- e deixa a tela dizer o que esta havendo.
// Espera explicada e espera; espera calada e defeito.
object Servidor {
    enum class Estado { CONFERINDO, NO_AR, ACORDANDO }

    private val _estado = MutableStateFlow(Estado.CONFERINDO)
    val estado: StateFlow<Estado> = _estado

    // Segundos desde o inicio da espera. A tela mostra o numero subindo: e a prova de
    // que alguem ainda esta tentando, que um circulo girando nao da.
    private val _esperandoHa = MutableStateFlow(0)
    val esperandoHa: StateFlow<Int> = _esperandoHa

    // Cliente PROPRIO e curto de proposito. O cliente do app espera 60s no connect, e
    // esperar 60s pra so entao avisar "estou esperando" derrota o proposito inteiro:
    // aqui o que importa e a PRIMEIRA falha, nao a ultima. Teto de 8s por tentativa,
    // sem interceptador nenhum -- esta chamada nao leva token e nao precisa de identidade.
    private val cliente by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    // ATENCAO ao montar esta URL: em http:// a Cloudflare que fica na frente do Render
    // responde 301 SOZINHA, na borda, sem encostar na instancia. Um vigia apontado pro
    // http ve "301, tudo certo" pra sempre enquanto o servidor dorme do outro lado --
    // foi essa a armadilha que fez um cron externo jurar que estava funcionando. https,
    // sempre.
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
                // A primeira falha ainda nao e noticia: pode ser um segundo de rede. So a
                // partir da segunda tentativa a tela fala -- senao um soluco de wifi
                // acusaria "servidor dormindo" e o aviso perderia o valor.
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
                // 503 tambem conta: significa que a API respondeu e so o banco ou o Redis
                // estao fora. O processo esta de pe -- que e o que esta pergunta apura.
                it.code == 200 || it.code == 503
            }
        }.getOrDefault(false)
    }
}
