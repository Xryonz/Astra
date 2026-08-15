package app.astra.mobile.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerDto(
    val id: String,
    val name: String,
    val iconUrl: String? = null,
    val bannerUrl: String? = null,
    val bannerPositionY: Int = 50,
    val bannerScale: Int = 100,
    val iconScale: Int = 100,
    val description: String? = null,
    val messageRetentionDays: Int? = null,
    val ownerId: String? = null,
    val inviteCode: String? = null,
    val isPublic: Boolean = false,
    val isGroup: Boolean = false,
    // Órbita onde a bot fala sem ser chamada. null = ela escolhe sozinha.
    val botNoticeChannelId: String? = null,
    val channels: List<ChannelDto> = emptyList(),
    val categories: List<CategoryDto> = emptyList(),
    @SerialName("_count") val count: ServerCountDto? = null,
)

@Serializable
data class ChannelDto(
    val id: String,
    val name: String,
    val type: String = "TEXT",
    val isPrivate: Boolean = false,
    val categoryId: String? = null,
    val position: Int = 0,
    val lastMessageAt: String? = null,
    // null = "nao decidi": herda da categoria e, sem categoria, fica LIGADO.
    // Nao e o mesmo que false — sem o nulo nao daria pra desligar uma categoria
    // inteira e reativar uma orbita dentro dela.
    val botEnabled: Boolean? = null,
    // Guardar a conversa com a bot (comando + resposta) no historico da orbita.
    // Sem nulo: aqui nao ha heranca de categoria, cada orbita decide a sua. O
    // default true vale tambem pro backend antigo, que nao manda este campo.
    val botKeepReplies: Boolean = true,
)

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    val position: Int = 0,
    val botEnabled: Boolean? = null,
)

@Serializable
data class ServerCountDto(val members: Int = 0, val online: Int = 0)

@Serializable
data class CreateServerRequest(val name: String, val isGroup: Boolean = false)

@Serializable
data class UpdateServerRequest(
    val name: String? = null,
    val iconUrl: String? = null,
    val bannerUrl: String? = null,
    val bannerPositionY: Int? = null,
    val bannerScale: Int? = null,
    val iconScale: Int? = null,
    val description: String? = null,
    val messageRetentionDays: Int? = null,
    val isPublic: Boolean? = null,
    // Órbita dos avisos da bot. Campo nulo não vai no corpo (encodeDefaults=false),
    // então "voltar ao automático" se manda como STRING VAZIA — o backend traduz
    // vazio em nulo. Sem essa distinção não haveria como desfazer a escolha.
    val botNoticeChannelId: String? = null,
)

@Serializable
data class InviteCodeResponse(val inviteCode: String)

@Serializable
data class EmojiDto(
    val id: String,
    val serverId: String = "",
    val name: String,
    val url: String,
)

@Serializable
data class RenameEmojiRequest(val name: String)

@Serializable
data class ChannelVisibilityDto(
    val isPrivate: Boolean = false,
    val roleIds: List<String> = emptyList(),
)

@Serializable
data class ChannelVisibilityRequest(
    val isPrivate: Boolean,
    val roleIds: List<String> = emptyList(),
)

@Serializable
data class UpdateChannelNameRequest(val name: String)

// Liga/desliga a bot NESTA orbita. So true/false: com explicitNulls=false o null
// sumiria do JSON e o backend leria "nao mudar" — entao "voltar a herdar da
// categoria" nao tem como ser expresso por aqui, e a decisao vira definitiva
// pra esta orbita. E o comportamento certo mesmo: quem decidiu na mao, decidiu.
@Serializable
data class UpdateChannelBotRequest(val botEnabled: Boolean)

// Guardar (ou nao) a conversa com a bot nesta orbita.
@Serializable
data class UpdateChannelKeepRequest(val botKeepReplies: Boolean)

// Reordenar / mover canal (drag na sidebar). O backend (PATCH .../channels/:cid) aceita
// name/categoryId/position. position = ordem na secao; categoryId != null MOVE pra dentro
// da categoria. categoryId fica null (default) no reorder simples e, com explicitNulls=false
// (AppModule), e OMITIDO -> backend mantem a categoria atual. NAO da pra mandar null explicito
// (mover pra "solta") por causa disso — caso deferido.
@Serializable
data class MoveChannelRequest(val position: Int, val categoryId: String? = null)

@Serializable
data class CreateChannelRequest(
    val name: String,
    val type: String = "TEXT",
    val categoryId: String? = null,
)

@Serializable
data class CreateCategoryRequest(val name: String)

@Serializable
data class UpdateCategoryRequest(
    val name: String? = null,
    val position: Int? = null,
    val botEnabled: Boolean? = null,
)

@Serializable
data class ServerMemberDto(
    val id: String = "",
    val userId: String,
    val role: String = "MEMBER",
    val nameColor: String? = null,
    val user: MemberUserDto,
    val roles: List<MemberRoleDto> = emptyList(),
    val topColor: String? = null,
)

@Serializable
data class MemberRoleDto(
    val id: String,
    val name: String,
    val color: String? = null,
    val iconUrl: String? = null,
    val position: Int = 0,
    val hoist: Boolean = false,
)

@Serializable
data class MemberUserDto(
    val username: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
data class PresenceUpdateDto(val userId: String, val status: String = "OFFLINE")

// `activity` nulo = a pessoa parou de mostrar (desligou, fechou o app, ou o
// registro venceu no servidor). Nulo e string vazia significam a mesma coisa aqui,
// e quem consome trata os dois como "apaga a linha".
@Serializable
data class ActivityUpdateDto(val userId: String, val activity: String? = null)

// Aviso de que algo mudou numa constelacao (server_channels / server_members /
// server_joined). So carrega o id: e um PING pra refazer a busca, nao um delta —
// canal privado faz cada membro ver uma lista diferente, e so o backend sabe qual.
@Serializable
data class ServerScopedEventDto(
    val serverId: String,
    // So no server_left: 'expulso' | 'banido' | 'saiu'. Com default porque os
    // outros eventos que usam este DTO (server_gone, roles, etc.) nao mandam.
    val motivo: String = "saiu",
    val reason: String? = null,
)

// Um comando do bot, como o backend descreve (lib/bot.ts).
@Serializable
data class BotCommandDto(
    val name: String,
    val description: String = "",
    val category: String = "",
)

@Serializable
data class MyPermsDto(
    val isOwner: Boolean = false,
    val isAdmin: Boolean = false,
    val permissions: List<String> = emptyList(),
)

@Serializable
data class MemberRoleRequest(val role: String)

@Serializable
data class MemberRoleResponse(val id: String, val role: String)

@Serializable
data class BanRequest(val userId: String, val reason: String? = null)

@Serializable
data class BanDto(
    val id: String,
    val userId: String,
    val reason: String? = null,
    val createdAt: String? = null,
    val user: MemberUserDto,
)

@Serializable
data class RoleDto(
    val id: String,
    val name: String,
    val color: String? = null,
    val iconUrl: String? = null,
    val position: Int = 0,
    val hoist: Boolean = false,
    val permissions: List<String> = emptyList(),
)

@Serializable
data class RoleRequest(
    val name: String,
    val color: String? = null,
    val iconUrl: String? = null,
    val permissions: List<String> = emptyList(),
    val hoist: Boolean = false,
)
