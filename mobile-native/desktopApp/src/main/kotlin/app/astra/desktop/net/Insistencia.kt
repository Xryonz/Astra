package app.astra.desktop.net

import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException

// POLITICA UNICA DE REPETICAO do app.
//
// Existia uma copia disto no ChatVm e outra no ShellVm, e as duas erravam a MESMA
// conta: tres tentativas com espera de 1,5s e 4s. Isso da 5,5 segundos de janela —
// contra um servidor que, no plano free do Render, dorme depois de 15min parado e
// acorda em ATE ~50 SEGUNDOS. As tres tentativas queimavam nos primeiros seis
// segundos do sono, a tela cravava "não foi possível carregar" e ninguem tentava
// de novo. O servidor acordava meio minuto depois, sozinho, sem plateia.
//
// A janela agora cobre o sono inteiro. E a espera cresce rapido de proposito:
// insistir de segundo em segundo contra um servidor que ainda esta subindo so
// gasta tentativa e enche o log dele.
private val ESPERAS_MS = longArrayOf(1_000L, 3_000L, 6_000L, 12_000L, 20_000L, 30_000L)
val TENTATIVAS = ESPERAS_MS.size + 1   // 7 tentativas, ~72s de espera somada

// Por que a chamada falhou, do jeito que a tela pode dizer em voz alta.
//
// `permanente` e a parte que faltava: repetir sete vezes um 403 nao muda o 403 —
// so faz a pessoa esperar 72 segundos por uma resposta que o servidor ja tinha
// dado na primeira. Erro permanente sai do laco na hora, com o motivo real.
data class Falha(val motivo: String, val permanente: Boolean)

private fun classificar(t: Throwable, oQue: String): Falha = when (t) {
    is HttpException -> when (t.code()) {
        401 -> Falha("Sua sessão expirou. Entre de novo.", permanente = true)
        403 -> Falha("Você não tem acesso a $oQue.", permanente = true)
        404 -> Falha("$oQue não existe mais.", permanente = true)
        429 -> Falha("Muitas chamadas seguidas. Aguarde um instante.", permanente = false)
        else -> Falha("O servidor respondeu com erro ${t.code()}.", permanente = t.code() < 500)
    }
    // Timeout, DNS, conexao recusada, 502/503 do roteador do Render enquanto a
    // instancia sobe — tudo isto passa. E o caso que a insistencia existe pra cobrir.
    is IOException -> Falha("Sem conexão com o servidor.", permanente = false)
    else -> Falha("Não foi possível carregar $oQue.", permanente = false)
}

// Insiste ate conseguir, ate esbarrar num erro permanente, ou ate acabar a janela.
//
// `aoTentarDeNovo` recebe o numero da PROXIMA tentativa (2..N) antes de cada
// espera: e por ele que a tela troca o vermelho de "falhou" pelo texto honesto de
// "o servidor está acordando". Sem isso a espera longa vira tela travada, que e
// pior que o erro rapido que ela veio consertar.
suspend fun <T> insistir(
    oQue: String,
    aoTentarDeNovo: (Int) -> Unit = {},
    bloco: suspend () -> T,
): Result<T> {
    var ultima = Falha("Não foi possível carregar $oQue.", permanente = false)
    repeat(TENTATIVAS) { tentativa ->
        runCatching { bloco() }
            .onSuccess { return Result.success(it) }
            .onFailure { t ->
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

// Atalho pra quem so quer o valor ou null (o boot, que tolera falha parcial).
suspend fun <T : Any> insistindoOuNulo(oQue: String, bloco: suspend () -> T?): T? =
    insistir(oQue) { bloco() ?: error("resposta vazia") }.getOrNull()
