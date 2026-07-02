package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class NotificationPrefsDto(
    val mentions: Boolean = true,
    val dms: Boolean = true,
    val reactions: Boolean = true,
    val replies: Boolean = true,
    val sounds: Boolean = true,
    val desktop: Boolean = true,
    val quietStart: Int? = null,
    val quietEnd: Int? = null,
)

@Serializable
data class NotificationPrefsResponse(
    val prefs: NotificationPrefsDto,
)

// Merge parcial no backend; com explicitNulls=false, null aqui = campo omitido =
// "nao mexe". Pra LIMPAR quiet hours e preciso null EXPLICITO no JSON -> usar o
// overload updatePrefsRaw(JsonObject) com JsonNull.
@Serializable
data class UpdateNotificationPrefsRequest(
    val mentions: Boolean? = null,
    val dms: Boolean? = null,
    val reactions: Boolean? = null,
    val replies: Boolean? = null,
    val sounds: Boolean? = null,
    val quietStart: Int? = null,
    val quietEnd: Int? = null,
)

@Serializable
data class NotificationItemDto(
    val id: String,
    val type: String,
    val payload: JsonObject? = null,
    val readAt: String? = null,
    val createdAt: String,
)

@Serializable
data class NotificationsPageDto(
    val items: List<NotificationItemDto>,
    val nextCursor: String? = null,
)

@Serializable
data class UnreadCountDto(val count: Int)

@Serializable
data class FcmTokenRequest(val token: String, val platform: String = "android")
