package app.astra.mobile.feature.channel.domain

import app.astra.mobile.feature.channel.domain.model.ChannelMessage
import app.astra.mobile.feature.channel.domain.model.ChannelMessagesPage
import kotlinx.coroutines.flow.Flow

interface ChannelRepository {
    suspend fun messages(channelId: String, cursor: String?): Result<ChannelMessagesPage>
    suspend fun send(channelId: String, content: String): Result<ChannelMessage>
    fun joinChannel(channelId: String)
    fun leaveChannel(channelId: String)
    fun incomingMessages(channelId: String): Flow<ChannelMessage>
    fun deletedMessages(channelId: String): Flow<String>
}
