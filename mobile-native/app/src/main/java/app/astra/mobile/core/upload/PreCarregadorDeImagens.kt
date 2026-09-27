package app.astra.mobile.core.upload

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import app.astra.mobile.BuildConfig
import app.astra.mobile.core.network.DmApi
import app.astra.mobile.core.network.FriendsApi
import app.astra.mobile.core.network.ServerApi
import app.astra.mobile.core.realtime.ConnectionState
import app.astra.mobile.core.realtime.SocketManager
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val ESPERA_DEPOIS_DE_CONECTAR_MS = 8_000L
private const val LADO_DA_DECODIFICACAO = 96

@Singleton
class PreCarregadorDeImagens @Inject constructor(
    @ApplicationContext private val contexto: Context,
    private val socket: SocketManager,
    private val serverApi: ServerApi,
    private val dmApi: DmApi,
    private val friendsApi: FriendsApi,
) {
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val guardadas = ConcurrentHashMap.newKeySet<String>()
    private val constelacoesVistas = ConcurrentHashMap.newKeySet<String>()
    private val iniciado = AtomicBoolean(false)

    fun iniciar() {
        if (!iniciado.compareAndSet(false, true)) return
        escopo.launch {
            socket.state.collectLatest { estado ->
                if (estado != ConnectionState.Connected) return@collectLatest
                withContext(Dispatchers.Main) { RodadaDasImagens.proxima() }
                delay(ESPERA_DEPOIS_DE_CONECTAR_MS)
                preCarregar()
            }
        }
    }

    private suspend fun preCarregar() {
        if (!redeSemCusto()) return
        val servers = runCatching { serverApi.servers().data.orEmpty() }.getOrNull() ?: return
        val dms = runCatching { dmApi.conversations().data.orEmpty() }.getOrDefault(emptyList())
        baixar(servers.flatMap { listOf(it.iconUrl, it.bannerUrl) } + dms.map { it.otherUser?.avatarUrl })
        runCatching { friendsApi.friends().data.orEmpty() }.getOrNull()?.let { amigos ->
            baixar(amigos.map { it.user.avatarUrl })
        }
        for (s in servers) {
            if (!redeSemCusto()) return
            if (!constelacoesVistas.add(s.id)) continue
            val membros = runCatching { serverApi.members(s.id).data.orEmpty() }.getOrNull()
            if (membros == null) {
                constelacoesVistas.remove(s.id)
                continue
            }
            baixar(membros.map { it.user.avatarUrl })
        }
    }

    private suspend fun baixar(enderecos: List<String?>) {
        val carregador = SingletonImageLoader.get(contexto)
        val disco = carregador.diskCache
        for (endereco in enderecos.distinct()) {
            if (endereco.isNullOrBlank() || endereco.startsWith("data:") || endereco in guardadas) continue
            if (!redeSemCusto()) return
            val completo = if (endereco.startsWith("/")) BuildConfig.BASE_URL.trimEnd('/') + endereco else endereco
            if (disco?.openSnapshot(completo)?.use { true } == true) continue
            val pedido = ImageRequest.Builder(contexto)
                .data(endereco)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .size(LADO_DA_DECODIFICACAO)
                .build()
            if (carregador.execute(pedido) !is ErrorResult) guardadas += endereco
        }
    }

    private fun redeSemCusto(): Boolean {
        val conexoes = contexto.getSystemService<ConnectivityManager>() ?: return false
        val rede = conexoes.getNetworkCapabilities(conexoes.activeNetwork) ?: return false
        return rede.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
