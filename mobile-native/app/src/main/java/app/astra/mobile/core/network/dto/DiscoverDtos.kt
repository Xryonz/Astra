package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class DiscoverServerDto(
    val id: String,
    val name: String,
    val iconUrl: String? = null,
    val bannerUrl: String? = null,
    val description: String? = null,
    val members: Int = 0,
)

@Serializable
data class DiscoverJoinDto(val ok: Boolean = false, val serverId: String? = null)
