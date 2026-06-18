package app.astra.mobile.feature.dm.domain

import app.astra.mobile.feature.dm.domain.model.Conversation
import app.astra.mobile.feature.dm.domain.model.DmMessage
import app.astra.mobile.feature.dm.domain.model.MessagesPage
import app.astra.mobile.feature.dm.domain.model.OpenedConversation
import kotlinx.coroutines.flow.Flow

interface DmRepository {
    suspend fun conversations(): Result<List<Conversation>>

    suspend fun messages(conversationId: String, cursor: String?): Result<MessagesPage>

    suspend fun send(conversationId: String, content: String): Result<DmMessage>

    suspend fun delete(conversationId: String, messageId: String): Result<Unit>

    suspend fun open(username: String): Result<OpenedConversation>

    fun joinConversation(conversationId: String)

    fun leaveConversation(conversationId: String)

    /** new_dm em tempo real, ja filtrado por conversa e mapeado pro dominio. */
    fun incomingMessages(conversationId: String): Flow<DmMessage>

    /** dm_deleted em tempo real — emite o id da mensagem removida. */
    fun deletedMessages(conversationId: String): Flow<String>
}
