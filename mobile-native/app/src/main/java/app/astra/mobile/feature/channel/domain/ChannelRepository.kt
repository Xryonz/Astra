package app.astra.mobile.feature.channel.domain

import app.astra.mobile.feature.channel.domain.model.ChannelMessage
import app.astra.mobile.feature.channel.domain.model.ChannelMessagesPage
import app.astra.mobile.feature.channel.domain.model.MessageReaction
import app.astra.mobile.feature.channel.domain.model.TypingUser
import kotlinx.coroutines.flow.Flow

interface ChannelRepository {
    suspend fun messages(channelId: String, cursor: String?): Result<ChannelMessagesPage>
    suspend fun send(channelId: String, content: String, replyToId: String? = null): Result<ChannelMessage>
    suspend fun edit(channelId: String, messageId: String, content: String): Result<Unit>
    suspend fun delete(channelId: String, messageId: String): Result<Unit>
    suspend fun react(channelId: String, messageId: String, emoji: String): Result<Unit>
    fun joinChannel(channelId: String)
    fun leaveChannel(channelId: String)
    fun incomingMessages(channelId: String): Flow<ChannelMessage>
    fun deletedMessages(channelId: String): Flow<String>
    fun editedMessages(channelId: String): Flow<Pair<String, String>>
    fun reactionUpdates(channelId: String): Flow<Pair<String, List<MessageReaction>>>
    fun typingEvents(channelId: String): Flow<TypingUser>
    fun startTyping(channelId: String)
    fun stopTyping(channelId: String)
    suspend fun pin(channelId: String, messageId: String, pinned: Boolean): Result<Unit>
    suspend fun pinnedMessages(channelId: String): Result<List<ChannelMessage>>
    /** message_pinned -> (messageId, pinned) do canal certo. */
    fun pinnedUpdates(channelId: String): Flow<Pair<String, Boolean>>
}
