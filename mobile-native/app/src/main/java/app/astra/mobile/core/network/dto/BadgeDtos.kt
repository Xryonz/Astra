package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

// GET /users/:id/badges -> { global: [...], server: [...] }.
// Globais sao derivadas no backend (Bot, Pioneiro); as de servidor tem origem.
@Serializable
data class UserBadgesDto(
    val global: List<BadgeDto> = emptyList(),
    val server: List<ServerGrantedBadgeDto> = emptyList(),
)

@Serializable
data class BadgeDto(
    val id: String,
    val name: String,
    val icon: String,
    val color: String? = null,
    val description: String? = null,
)

@Serializable
data class ServerGrantedBadgeDto(
    val badgeId: String,
    val name: String,
    val icon: String,
    val color: String? = null,
    val description: String? = null,
    val serverId: String,
    val serverName: String? = null,
)

// GET /servers/:id/badges (gestao): badge + quem ja tem.
@Serializable
data class ServerBadgeDto(
    val id: String,
    val serverId: String,
    val name: String,
    val icon: String,
    val color: String? = null,
    val description: String? = null,
    val grantedUserIds: List<String> = emptyList(),
)

@Serializable
data class CreateBadgeRequest(
    val name: String,
    val icon: String,
    val color: String? = null,
    val description: String? = null,
)

@Serializable
data class GrantBadgeRequest(
    val userId: String,
)

// Limpar = mandar "" (o backend faz trim() || null). Nao usar null aqui:
// explicitNulls=false omitiria a chave e o Zod exige ela presente.
@Serializable
data class CustomStatusRequest(
    val customStatus: String,
)
