package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

// Espelha o Progresso de apps/api/src/lib/xp.ts.
//
// O NIVEL vem pronto do servidor em vez de ser calculado aqui. Duplicar a curva no
// cliente daria duas contas da mesma coisa, e no dia em que a taxa fosse ajustada o
// app mostraria um nivel e o servidor pagaria outro.
@Serializable
data class ProgressoDto(
    val xp: Int = 0,
    val nivel: Int = 0,
    // Quanto ja andou DENTRO do nivel atual, e quanto o nivel custa por inteiro.
    // A fracao do anel e noNivel/paraOProximo.
    val noNivel: Int = 0,
    val paraOProximo: Int = 100,
    val brilho: Int = 0,
)

// Chega pelo socket (`xp_gain`) a cada ganho, com o progresso ja atualizado — o
// anel nunca precisa voltar no servidor perguntar quanto ficou.
@Serializable
data class GanhoXpDto(
    val ganho: Int = 0,
    val origem: String = "",          // "mensagem" | "call"
    val subiuDeNivel: Boolean = false,
    val brilhoGanho: Int = 0,
    val progresso: ProgressoDto = ProgressoDto(),
)

@Serializable
data class TrilhaTierDto(
    val nivel: Int = 0,
    val custo: Int = 0,
    val brilho: Int = 0,
)

@Serializable
data class RegrasXpDto(
    val porMensagem: Int = 0,
    val porMinutoCall: Int = 0,
    val trilha: List<TrilhaTierDto> = emptyList(),
)
