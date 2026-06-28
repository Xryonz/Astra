package app.astra.mobile.core.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Acesso ao cache de mensagens. `observe` devolve um Flow — a UI re-renderiza
 * sozinha quando o cache muda (socket/REST escrevem, a tela so observa). Upsert
 * porque a mesma mensagem pode chegar pelo historico (REST) e pelo socket.
 */
@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observe(conversationId: String): Flow<List<MessageEntity>>

    @Upsert
    suspend fun upsert(messages: List<MessageEntity>)

    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String)

    // Updates pontuais pros eventos ao vivo de canal (sem reescrever a linha toda).
    @Query("UPDATE messages SET content = :content, edited = 1 WHERE id = :id")
    suspend fun applyEdit(id: String, content: String)

    @Query("UPDATE messages SET pinned = :pinned WHERE id = :id")
    suspend fun applyPinned(id: String, pinned: Boolean)

    @Query("UPDATE messages SET reactionsJson = :json WHERE id = :id")
    suspend fun applyReactions(id: String, json: String?)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearConversation(conversationId: String)

    // Logout: zera o cache pra mensagens nao vazarem pra proxima conta.
    @Query("DELETE FROM messages")
    suspend fun clearAll()
}
