package app.astra.mobile.feature.notifications.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.network.NotificationsApi
import app.astra.mobile.core.network.dto.NotificationItemDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject

// Item ja "achatado" pro UI: payload JsonObject -> campos lenientes (null-safe),
// cobre os 4 tipos (mention/dm/reply/reaction) sem um modelo por tipo.
data class NotificationRow(
    val id: String,
    val type: String,
    val authorName: String,
    val authorAvatar: String?,
    val preview: String,
    val channelId: String?,
    val channelName: String?,
    val serverName: String?,
    val conversationId: String?,
    val emoji: String?,
    val unread: Boolean,
    val createdAt: String,
)

data class NotificationsFeedState(
    val loading: Boolean = true,
    val items: List<NotificationRow> = emptyList(),
    val nextCursor: String? = null,
    val loadingMore: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class NotificationsFeedViewModel @Inject constructor(
    private val api: NotificationsApi,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsFeedState())
    val state: StateFlow<NotificationsFeedState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val page = api.feed().data
                _state.update {
                    it.copy(
                        loading = false,
                        items = page?.items?.map(::toRow).orEmpty(),
                        nextCursor = page?.nextCursor,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Sem conexao com o servidor") }
            }
        }
    }

    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        if (_state.value.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val page = api.feed(cursor = cursor).data
                _state.update {
                    it.copy(
                        loadingMore = false,
                        items = it.items + page?.items?.map(::toRow).orEmpty(),
                        nextCursor = page?.nextCursor,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    fun markAllRead() {
        _state.update { s -> s.copy(items = s.items.map { it.copy(unread = false) }) }
        viewModelScope.launch {
            try { api.markAllRead() } catch (_: Exception) {}
        }
    }

    fun markRead(id: String) {
        _state.update { s -> s.copy(items = s.items.map { if (it.id == id) it.copy(unread = false) else it }) }
        viewModelScope.launch {
            try { api.markRead(id) } catch (_: Exception) {}
        }
    }

    private fun toRow(dto: NotificationItemDto): NotificationRow {
        fun str(key: String): String? =
            dto.payload?.get(key)?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
        return NotificationRow(
            id = dto.id,
            type = dto.type,
            authorName = str("authorName") ?: "Alguém",
            authorAvatar = str("authorAvatar"),
            preview = str("preview").orEmpty(),
            channelId = str("channelId"),
            channelName = str("channelName"),
            serverName = str("serverName"),
            conversationId = str("conversationId"),
            emoji = str("emoji"),
            unread = dto.readAt == null,
            createdAt = dto.createdAt,
        )
    }
}
