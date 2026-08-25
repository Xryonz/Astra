package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProgressoDto(
    val xp: Int = 0,
    val nivel: Int = 0,
    val noNivel: Int = 0,
    val paraOProximo: Int = 100,
    val brilho: Int = 0,
)

@Serializable
data class GanhoXpDto(
    val ganho: Int = 0,
    val origem: String = "",
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
