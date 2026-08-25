package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

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

@Serializable
data class BotPersonaPatch(
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val bannerColor: String? = null,
    val bannerScale: Int? = null,
    val bannerPositionY: Int? = null,
    val limpar: List<String>? = null,
)

@Serializable
data class BotComandoDto(
    val chave: String,
    val rotulo: String,
    val categoria: String = "",
    val descricao: String = "",
)

@Serializable
data class BotCatalogoWrapper(val comandos: List<BotComandoDto> = emptyList())
