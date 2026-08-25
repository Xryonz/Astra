package app.astra.desktop.net

import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

private val ESPERAS_MS = longArrayOf(
    1_000L, 3_000L, 6_000L, 12_000L, 20_000L, 30_000L, 45_000L, 60_000L,
)
val TENTATIVAS = ESPERAS_MS.size + 1

data class Falha(val motivo: String, val permanente: Boolean)

private fun classificar(t: Throwable, oQue: String): Falha = when (t) {
    is HttpException -> when (t.code()) {
        401 -> Falha("Sua sessão expirou. Entre de novo.", permanente = true)
        403 -> Falha("Você não tem acesso a $oQue.", permanente = true)
        404 -> Falha("$oQue não existe mais.", permanente = true)
        429 -> Falha("Muitas chamadas seguidas. Aguarde um instante.", permanente = false)
        else -> Falha("O servidor respondeu com erro ${t.code()}.", permanente = t.code() < 500)
    }
    is IOException -> Falha(
        "Sem conexão com o servidor" + (t.message?.take(90)?.let { " ($it)" } ?: "") + ".",
        permanente = false,
    )
    is SerializationException -> Falha(
        "O servidor respondeu algo que não deu para ler (deve estar reiniciando).",
        permanente = false,
    )
    else -> Falha(
        "Não foi possível carregar $oQue (${t::class.simpleName}).",
        permanente = false,
    )
}

suspend fun <T> insistir(
    oQue: String,
    aoTentarDeNovo: (Int) -> Unit = {},
    bloco: suspend () -> T,
): Result<T> {
    var ultima = Falha("Não foi possível carregar $oQue.", permanente = false)
    var ilegiveis = 0
    repeat(TENTATIVAS) { tentativa ->
        runCatching { bloco() }
            .onSuccess { return Result.success(it) }
            .onFailure { t ->
                RedeLog.falhou(oQue, tentativa + 1, t)
                if (t is SerializationException && ++ilegiveis >= 2) {
                    return Result.failure(FalhaDeRede(classificar(t, oQue)))
                }
                ultima = classificar(t, oQue)
                if (ultima.permanente) return Result.failure(FalhaDeRede(ultima))
            }
        if (tentativa < TENTATIVAS - 1) {
            aoTentarDeNovo(tentativa + 2)
            delay(ESPERAS_MS[tentativa])
        }
    }
    return Result.failure(FalhaDeRede(ultima))
}

class FalhaDeRede(val falha: Falha) : Exception(falha.motivo)

suspend fun <T : Any> insistindoOuNulo(oQue: String, bloco: suspend () -> T?): T? =
    insistir(oQue) { bloco() ?: error("resposta vazia") }.getOrNull()
