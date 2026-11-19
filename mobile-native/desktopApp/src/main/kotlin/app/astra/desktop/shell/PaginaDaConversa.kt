package app.astra.desktop.shell

import app.astra.mobile.core.network.ChannelApi
import app.astra.mobile.core.network.DmApi
import app.astra.mobile.core.network.dto.ChannelMessageDto
import app.astra.mobile.core.network.dto.DmMessageDto
import app.astra.mobile.core.network.dto.MsgAuthorDto

private const val PAGE = 50

internal suspend fun buscarPaginaDaConversa(
    alvo: ChatTarget,
    cursor: String?,
    channelApi: ChannelApi,
    dmApi: DmApi,
    myId: String?,
): Pair<List<ChatMessage>, String?> = when (alvo) {
    is ChatTarget.Channel -> channelApi.messages(alvo.id, cursor, PAGE).data
        .let { p -> p?.items.orEmpty().map { it.paraMensagem(myId) } to p?.nextCursor }
    is ChatTarget.Dm -> dmApi.messages(alvo.id, cursor, PAGE).data
        .let { p -> p?.items.orEmpty().map { it.paraMensagem(myId) } to p?.nextCursor }
}

private fun MsgAuthorDto?.name(fallbackId: String, myId: String?): String =
    this?.displayName ?: this?.username ?: if (fallbackId == myId) "você" else "alguém"

internal fun ChannelMessageDto.paraMensagem(myId: String?): ChatMessage {
    val autor = authorId.ifBlank { author?.id.orEmpty() }
    return ChatMessage(
        id = id, content = content, authorId = autor,
        authorName = author.name(autor, myId), authorAvatar = author?.avatarUrl,
        authorFont = author?.displayFont,
        createdAt = createdAt,
        mine = autor.isNotBlank() && autor == myId,
        edited = edited,
        pinned = pinned,
        reactions = reactions, mentions = mentions, replyTo = replyTo,
        attachments = attachments,
        poll = poll,
        clientNonce = clientNonce,
        kind = kind,
    )
}

internal fun DmMessageDto.paraMensagem(myId: String?) = ChatMessage(
    id = id, content = content, authorId = senderId,
    authorName = author.name(senderId, myId), authorAvatar = author?.avatarUrl,
    authorFont = author?.displayFont,
    createdAt = createdAt,
    mine = senderId == myId,
    replyTo = replyTo,
    attachments = attachments,
    clientNonce = clientNonce,
    call = call,
)
