package app.astra.desktop.shell

import app.astra.mobile.core.network.FriendApi
import app.astra.mobile.core.network.ServerApi
import app.astra.mobile.core.network.dto.ConversationDto
import app.astra.mobile.core.network.dto.ServerDto
import app.astra.shared.AstraShared
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private const val LADO_DA_DECODIFICACAO = 96

class PreCarregadorDeImagens(
    private val serverApi: ServerApi,
    private val friendApi: FriendApi,
) {
    private val guardadas = ConcurrentHashMap.newKeySet<String>()
    private val constelacoesVistas = ConcurrentHashMap.newKeySet<String>()
    private val umPorVez = Mutex()

    suspend fun preCarregar(servers: List<ServerDto>, dms: List<ConversationDto>) = withContext(Dispatchers.IO) {
        umPorVez.withLock { preCarregarUmPorVez(servers, dms) }
    }

    private suspend fun preCarregarUmPorVez(servers: List<ServerDto>, dms: List<ConversationDto>) {
        baixar(servers.flatMap { listOf(it.iconUrl, it.bannerUrl) } + dms.map { it.otherUser?.avatarUrl })
        runCatching { friendApi.friends().data.orEmpty() }.getOrNull()?.let { amigos ->
            baixar(amigos.map { it.user.avatarUrl })
        }
        for (s in servers) {
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
        val carregador = SingletonImageLoader.get(PlatformContext.INSTANCE)
        val disco = carregador.diskCache
        for (endereco in enderecos.distinct()) {
            if (endereco.isNullOrBlank() || endereco.startsWith("data:") || endereco in guardadas) continue
            val completo = if (endereco.startsWith("/")) AstraShared.BASE_URL.trimEnd('/') + endereco else endereco
            if (disco?.openSnapshot(completo)?.use { true } == true) continue
            val pedido = ImageRequest.Builder(PlatformContext.INSTANCE)
                .data(endereco)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .size(LADO_DA_DECODIFICACAO)
                .build()
            if (carregador.execute(pedido) !is ErrorResult) guardadas += endereco
        }
    }
}
