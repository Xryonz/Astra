package app.astra.mobile.feature.dm.domain

import app.astra.mobile.feature.dm.domain.model.Conversation
import app.astra.mobile.feature.dm.domain.model.DmMessage
import app.astra.mobile.feature.dm.domain.model.MessagesPage
import app.astra.mobile.feature.dm.domain.model.OpenedConversation
import app.astra.mobile.feature.dm.domain.model.TypingUser
import kotlinx.coroutines.flow.Flow

interface DmRepository {
    suspend fun conversations(): Result<List<Conversation>>

    /**
     * SSOT: a UI observa o cache (Room). Enquanto coletado, tambem dreno o socket
     * (new_dm/dm_deleted) pra dentro do Room — a tela so olha o banco.
     */
    fun observeMessages(conversationId: String): Flow<List<DmMessage>>

    /** Busca o historico (REST) e grava no Room; a UI atualiza pelo observeMessages. */
    suspend fun messages(conversationId: String, cursor: String?): Result<MessagesPage>

    suspend fun send(conversationId: String, content: String, replyToId: String? = null): Result<DmMessage>

    suspend fun delete(conversationId: String, messageId: String): Result<Unit>

    suspend fun open(username: String): Result<OpenedConversation>

    fun joinConversation(conversationId: String)

    fun leaveConversation(conversationId: String)

    fun typingEvents(conversationId: String): Flow<TypingUser>
    fun startTyping(conversationId: String)
    fun stopTyping(conversationId: String)

    /** { conversationId -> meu lastReadAtISO (ou null) }. */
    suspend fun dmReads(): Result<Map<String, String?>>
    suspend fun markRead(conversationId: String): Result<Unit>
    /** new_dm de OUTRA pessoa ao vivo: emite o conversationId. */
    fun incomingConversations(): Flow<String>
}
