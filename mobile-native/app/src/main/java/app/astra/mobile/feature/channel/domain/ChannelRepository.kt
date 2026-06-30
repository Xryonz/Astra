package app.astra.mobile.feature.channel.domain

import app.astra.mobile.core.model.Attachment
import app.astra.mobile.feature.channel.domain.model.ChannelMessage
import app.astra.mobile.feature.channel.domain.model.ChannelMessagesPage
import app.astra.mobile.feature.channel.domain.model.MessageEdit
import app.astra.mobile.feature.channel.domain.model.TypingUser
import kotlinx.coroutines.flow.Flow

interface ChannelRepository {

    fun observeMessages(channelId: String): Flow<List<ChannelMessage>>
    suspend fun messages(channelId: String, cursor: String?): Result<ChannelMessagesPage>
    suspend fun send(channelId: String, content: String, replyToId: String? = null, attachments: List<Attachment> = emptyList()): Result<ChannelMessage>
    suspend fun edit(channelId: String, messageId: String, content: String): Result<Unit>
    suspend fun delete(channelId: String, messageId: String): Result<Unit>
    suspend fun react(channelId: String, messageId: String, emoji: String): Result<Unit>
    fun joinChannel(channelId: String)
    fun leaveChannel(channelId: String)
    fun typingEvents(channelId: String): Flow<TypingUser>
    fun startTyping(channelId: String)
    fun stopTyping(channelId: String)
    suspend fun pin(channelId: String, messageId: String, pinned: Boolean): Result<Unit>
    suspend fun pinnedMessages(channelId: String): Result<List<ChannelMessage>>
    suspend fun markRead(channelId: String): Result<Unit>
    suspend fun editHistory(channelId: String, messageId: String): Result<List<MessageEdit>>
    suspend fun createPoll(channelId: String, question: String, options: List<String>, allowMultiple: Boolean, durationHours: Int?): Result<Unit>
    suspend fun votePoll(channelId: String, messageId: String, optionId: String): Result<Unit>
    suspend fun closePoll(channelId: String, messageId: String): Result<Unit>
}
