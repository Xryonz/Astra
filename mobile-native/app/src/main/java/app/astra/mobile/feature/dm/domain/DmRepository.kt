package app.astra.mobile.feature.dm.domain

import app.astra.mobile.core.model.Attachment
import app.astra.mobile.feature.dm.domain.model.Conversation
import app.astra.mobile.feature.dm.domain.model.DmMessage
import app.astra.mobile.feature.dm.domain.model.MessagesPage
import app.astra.mobile.feature.dm.domain.model.OpenedConversation
import app.astra.mobile.feature.dm.domain.model.TypingUser
import kotlinx.coroutines.flow.Flow

interface DmRepository {
    suspend fun conversations(): Result<List<Conversation>>

    fun observeMessages(conversationId: String): Flow<List<DmMessage>>

    suspend fun messages(conversationId: String, cursor: String?): Result<MessagesPage>

    suspend fun send(conversationId: String, content: String, replyToId: String? = null, attachments: List<Attachment> = emptyList()): Result<DmMessage>

    suspend fun delete(conversationId: String, messageId: String): Result<Unit>

    suspend fun open(username: String): Result<OpenedConversation>

    fun joinConversation(conversationId: String)

    fun leaveConversation(conversationId: String)

    fun typingEvents(conversationId: String): Flow<TypingUser>
    fun startTyping(conversationId: String)
    fun stopTyping(conversationId: String)

    suspend fun dmReads(): Result<Map<String, String?>>
    suspend fun markRead(conversationId: String): Result<Unit>

    suspend fun setMuted(conversationId: String, muted: Boolean): Result<Unit>

    fun incomingConversations(): Flow<String>
}
