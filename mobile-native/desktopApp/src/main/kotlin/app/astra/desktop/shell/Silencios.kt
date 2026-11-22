package app.astra.desktop.shell

import app.astra.mobile.core.network.NotificationApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal data class Silencios(
    val orbitas: Set<String>,
    val constelacoes: Set<String>,
    val forcados: Set<String>,
)

internal suspend fun buscarSilencios(notifApi: NotificationApi): Silencios = coroutineScope {
    val doCanal = async { notifApi.channelNotifPrefs().data.orEmpty() }
    val daConstelacao = async { notifApi.serverNotifPrefs().data.orEmpty() }
    val canais = doCanal.await()
    Silencios(
        orbitas = canais.filter { it.mode == "mute" }.map { it.channelId }.toSet(),
        constelacoes = daConstelacao.await().filter { it.mode == "mute" }.map { it.serverId }.toSet(),
        forcados = canais.filter { it.mode == "all" }.map { it.channelId }.toSet(),
    )
}
