package app.astra.mobile.core.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

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

    @Query("UPDATE messages SET content = :content, edited = 1 WHERE id = :id")
    suspend fun applyEdit(id: String, content: String)

    @Query("UPDATE messages SET pinned = :pinned WHERE id = :id")
    suspend fun applyPinned(id: String, pinned: Boolean)

    @Query("UPDATE messages SET reactionsJson = :json WHERE id = :id")
    suspend fun applyReactions(id: String, json: String?)

    @Query("UPDATE messages SET pollJson = :json WHERE id = :id")
    suspend fun applyPoll(id: String, json: String?)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearConversation(conversationId: String)

    @Query("DELETE FROM messages")
    suspend fun clearAll()
}
