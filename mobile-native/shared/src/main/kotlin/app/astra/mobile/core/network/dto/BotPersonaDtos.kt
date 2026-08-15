package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

// A aparencia EFETIVA de uma persona (o que esta no codigo + o que o dono mexeu
// por cima). `personalizado` diz quais campos de fato foram trocados, pra a tela
// so oferecer "voltar ao original" onde ha para onde voltar.
@Serializable
data class BotPersonaDto(
    val chave: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val bannerColor: String? = null,
    val bannerScale: Int = 100,
    val bannerPositionY: Int = 50,
    val personalizado: BotPersonaFlags = BotPersonaFlags(),
)

@Serializable
data class BotPersonaFlags(
    val displayName: Boolean = false,
    val avatarUrl: Boolean = false,
    val bannerUrl: Boolean = false,
    val bannerColor: Boolean = false,
    val bannerScale: Boolean = false,
    val bannerPositionY: Boolean = false,
)

@Serializable
data class BotPersonasWrapper(val personas: List<BotPersonaDto> = emptyList())

// Campo nulo = NAO MEXI. Com `encodeDefaults=false` no Json do app, nulo nem
// chega a ser serializado, entao o corpo carrega so o que foi trocado.
//
// LIMITE CONHECIDO: por isso mesmo, esta rota nao tem como dizer "desfaz e volta
// pro que esta no codigo" — nulo omitido e "nao mexi", e o servidor nao consegue
// distinguir os dois. O servidor JA aceita o null explicito (as colunas sao
// nullable de proposito); falta so um caminho no cliente que consiga envia-lo.
@Serializable
data class BotPersonaPatch(
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val bannerColor: String? = null,
    val bannerScale: Int? = null,
    val bannerPositionY: Int? = null,
)
