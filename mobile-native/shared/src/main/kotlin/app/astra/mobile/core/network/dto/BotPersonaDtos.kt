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
// E por isso que "voltar ao original" NAO pode ser um nulo: ele sairia igual a
// "nao mexi" e o servidor nao teria como distinguir os dois. Daí `limpar`, que
// lista pelo NOME os campos que devem voltar ao que esta no codigo.
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

// Um comando como ele aparece na tela de configuração da constelação: a chave
// estável (que vai pro banco), o nome curto e a categoria pra agrupar. Sem prefixo
// e sem exemplo — ali a pergunta é "isto fica ligado?", não "como se usa".
@Serializable
data class BotComandoDto(
    val chave: String,
    val rotulo: String,
    val categoria: String = "",
    val descricao: String = "",
)

@Serializable
data class BotCatalogoWrapper(val comandos: List<BotComandoDto> = emptyList())
