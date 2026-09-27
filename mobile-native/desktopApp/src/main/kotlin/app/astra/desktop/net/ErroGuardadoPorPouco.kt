package app.astra.desktop.net

import coil3.network.CacheStrategy
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.request.Options

private const val VALIDADE_DO_ERRO_DURADOURO_MS = 10 * 60 * 1000L
private const val VALIDADE_DO_ERRO_PASSAGEIRO_MS = 30 * 1000L
private val ERROS_DURADOUROS = setOf(404, 410)

class ErroGuardadoPorPouco : CacheStrategy {

    override suspend fun read(
        cacheResponse: NetworkResponse,
        networkRequest: NetworkRequest,
        options: Options,
    ): CacheStrategy.ReadResult {
        if (cacheResponse.code in 200..299) return CacheStrategy.ReadResult(cacheResponse)
        val validade = if (cacheResponse.code in ERROS_DURADOUROS) {
            VALIDADE_DO_ERRO_DURADOURO_MS
        } else {
            VALIDADE_DO_ERRO_PASSAGEIRO_MS
        }
        val erroRecente = System.currentTimeMillis() - cacheResponse.responseMillis < validade
        return if (erroRecente) CacheStrategy.ReadResult(cacheResponse) else CacheStrategy.ReadResult(networkRequest)
    }

    override suspend fun write(
        cacheResponse: NetworkResponse?,
        networkRequest: NetworkRequest,
        networkResponse: NetworkResponse,
        options: Options,
    ): CacheStrategy.WriteResult = CacheStrategy.WriteResult(networkResponse)
}
