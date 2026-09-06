package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ItemMissaoDto(
    val id: String = "",
    val titulo: String = "",
    val alvo: Int = 1,
    val xp: Int = 0,
    val progresso: Int = 0,
    val concluida: Boolean = false,
    val resgatada: Boolean = false,
) {
    val resgatavel: Boolean get() = concluida && !resgatada
}

@Serializable
data class GrupoDiarioDto(
    val renovaEm: Long = 0,
    val itens: List<ItemMissaoDto> = emptyList(),
    val bonus: ItemMissaoDto = ItemMissaoDto(),
)

@Serializable
data class GrupoSemanalDto(
    val renovaEm: Long = 0,
    val itens: List<ItemMissaoDto> = emptyList(),
)

@Serializable
data class GrupoConquistasDto(
    val itens: List<ItemMissaoDto> = emptyList(),
)

@Serializable
data class PainelMissoesDto(
    val diarias: GrupoDiarioDto = GrupoDiarioDto(),
    val semanais: GrupoSemanalDto = GrupoSemanalDto(),
    val conquistas: GrupoConquistasDto = GrupoConquistasDto(),
)

@Serializable
data class MissaoConcluidaDto(
    val id: String = "",
    val titulo: String = "",
    val xp: Int = 0,
    val tipo: String = "",
)

@Serializable
data class ResgateDto(
    val id: String = "",
    val titulo: String = "",
    val xp: Int = 0,
    val tipo: String = "",
)

@Serializable
data class ResgatesDto(
    val resgates: List<ResgateDto> = emptyList(),
)
