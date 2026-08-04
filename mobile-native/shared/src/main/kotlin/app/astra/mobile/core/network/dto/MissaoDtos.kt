package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

// Espelha o PainelDeMissoes de apps/api/src/lib/missoes.ts.
//
// O TITULO vem do servidor, nao de um mapa de ids aqui. Missao nova no catalogo
// aparece no app sem release — e o oposto disso seria a tela mostrar "d.orbitas7"
// pra quem atualizou o backend e nao o cliente.
@Serializable
data class ItemMissaoDto(
    val id: String = "",
    val titulo: String = "",
    val alvo: Int = 1,
    val xp: Int = 0,
    val progresso: Int = 0,
    val concluida: Boolean = false,
)

@Serializable
data class GrupoDiarioDto(
    // Instante (epoch ms) em que o grupo vira. A tela conta o tempo que falta a
    // partir dele em vez de receber "6h" pronto, que ficaria velho na primeira
    // hora com a janela aberta.
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

// Chega pelo socket (`mission_done`) no instante em que fecha.
@Serializable
data class MissaoConcluidaDto(
    val id: String = "",
    val titulo: String = "",
    val xp: Int = 0,
    val tipo: String = "",   // "diaria" | "semanal" | "conquista"
)
