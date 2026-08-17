package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class UserWrapper(val user: ProfileUserDto)

// A SACOLA DE PREFERÊNCIAS DA CONTA (/api/profile/preferences).
//
// `JsonObject` cru, e não uma classe com `accent` e `bg`, por um motivo concreto:
// o servidor **substitui** a sacola inteira (`set({ preferences: serialized })`),
// ele não faz merge. Uma classe tipada mandaria de volta só os campos que ela
// conhece — e o dia em que o site guardasse uma chave nova, o desktop a apagaria
// no primeiro clique de tema. Guardando o objeto inteiro, chave alheia sobrevive
// ao ir e voltar mesmo sem ninguém aqui saber o que ela significa.
@Serializable
data class PreferenciasWrapper(
    val preferences: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class PreferenciasRequest(
    val preferences: JsonObject,
)

@Serializable
data class ProfileUserDto(
    val id: String,
    val username: String,
    val displayName: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val bannerUrl: String? = null,
    val bannerColor: String? = null,
    val pronouns: String? = null,
    val statusEmoji: String? = null,
    val customStatus: String? = null,
    val hasPassword: Boolean = true,
    val createdAt: String? = null,
    val effectiveStatus: String? = null,
    val profileTheme: String? = null,
    val bannerPositionY: Int? = null,
    val bannerScale: Int? = null,
    val displayFont: String? = null,
    // Quem pode ABRIR sussurro comigo: all | shared | friends. So vem no MEU
    // perfil -- o de outra pessoa nao carrega isto.
    val dmPrivacy: String? = null,
    val onboardedAt: String? = null,
    val emailVerifiedAt: String? = null,
)

@Serializable
data class ProfileViewWrapper(
    val user: ProfileUserDto,
    val mutualServers: List<MutualServerDto> = emptyList(),
    // Quantos amigos voce e a outra pessoa tem em comum. Zero quando o perfil e
    // o SEU: "voce tem 4 amigos em comum com voce mesmo" nao quer dizer nada.
    val mutualFriends: Int = 0,
    // Os ROSTOS desses amigos, ate oito. A contagem acima continua sendo a
    // verdadeira — e por ela que a tela diz "+12" sem baixar doze fotos.
    val mutualFriendsList: List<UserDto> = emptyList(),
)

@Serializable
data class MutualServerDto(
    val id: String,
    val name: String,
    val iconUrl: String? = null,
    val isGroup: Boolean = false,
    val role: String = "MEMBER",
)

@Serializable
data class UpdateProfileRequest(
    val displayName: String? = null,
    val username: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val bannerColor: String? = null,
    val pronouns: String? = null,
    val dmPrivacy: String? = null,
    val profileTheme: String? = null,
    val bannerPositionY: Int? = null,
    val bannerScale: Int? = null,
    val displayFont: String? = null,
    // O PATCH /api/profile ja aceitava statusEmoji; so faltava aqui. O RECADO
    // (customStatus) NAO vem por aqui — tem rota propria (/api/friends/custom-status).
    val statusEmoji: String? = null,
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)

@Serializable
data class SetPasswordRequest(
    val newPassword: String,
)

@Serializable
data class SetStatusRequest(val status: String)
