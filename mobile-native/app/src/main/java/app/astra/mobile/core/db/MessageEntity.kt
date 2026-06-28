package app.astra.mobile.core.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Linha de mensagem cacheada (DM ou canal). `conversationId` e a chave da "sala"
 * (id da conversa de DM OU id do canal) — o cache nao distingue, e o repo que sabe.
 * `mine` NAO e guardado: depende do user logado, entao e computado na leitura
 * comparando `authorId` com o userId atual. `createdAt` e ISO-8601, que ordena
 * lexicograficamente = cronologicamente.
 */
@Entity(
    tableName = "messages",
    indices = [Index(value = ["conversationId", "createdAt"])],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val authorId: String?,
    val authorName: String,
    val authorAvatar: String?,
    val content: String,
    val createdAt: String?,
    val replyToAuthor: String? = null,
    val replyToContent: String? = null,
)
