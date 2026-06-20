package app.astra.mobile.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// GET /api/invites/:code -> preview publico do servidor por tras do convite.
@Serializable
data class InvitePreviewDto(
    val id: String,
    val name: String,
    val iconUrl: String? = null,
    val bannerUrl: String? = null,
    val isGroup: Boolean = false,
    @SerialName("_count") val count: ServerCountDto? = null,
)
