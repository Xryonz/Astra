package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChannelMessageDto(
    val id: String,
    val content: String = "",
    // COM DEFAULT, de proposito. Campo obrigatorio aqui significa: qualquer
    // mensagem que chegue sem ele explode a desserializacao, o runCatching de quem
    // escuta o socket engole a excecao e a mensagem some SEM SINAL NENHUM. Foi
    // exatamente o que aconteceu com a bot: o backend mandava so o objeto `author`
    // aninhado, e o desktop descartou toda resposta dela por meses.
    // Quem le deve preferir authorId e cair pro author?.id quando vier vazio.
    val authorId: String = "",
    val channelId: String,
    val authorColor: String? = null,
    val createdAt: String? = null,
    val edited: Boolean = false,
    val pinned: Boolean = false,
    val reactions: List<ReactionDto> = emptyList(),
    // Ids de quem foi mencionado. O backend ja resolvia "@usuario" -> userId desde
    // sempre (lib/mentions.ts) e mandava isto no payload; o desktop e que descartava
    // o campo, entao mencao chegava e ninguem via.
    val mentions: List<String> = emptyList(),
    val replyTo: ReplyToDto? = null,
    val author: MsgAuthorDto? = null,
    val attachments: List<AttachmentDto> = emptyList(),
    val poll: PollDto? = null,
    // Eco do nonce que o cliente mandou no fast_send_text: o backend devolve no
    // broadcast pra o autor casar a mensagem real com a bolha otimista dele.
    val clientNonce: String? = null,
)

@Serializable
data class ReactionDto(
    val emoji: String,
    val count: Int = 0,
    val users: List<String> = emptyList(),
)

@Serializable
data class ReactResultDto(
    val action: String = "",
    val reactions: List<ReactionDto> = emptyList(),
)

@Serializable
data class ReactionUpdateDto(
    val messageId: String,
    val channelId: String,
    val reactions: List<ReactionDto> = emptyList(),
)

@Serializable
data class ChannelMessagesPageDto(
    val items: List<ChannelMessageDto> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
)

@Serializable
data class SendChannelRequest(
    val content: String,
    val replyToId: String? = null,
    val attachments: List<AttachmentDto> = emptyList(),
)

@Serializable
data class EditChannelRequest(val content: String)

@Serializable
data class MessageEditDto(
    val id: String,
    val content: String = "",
    val editedAt: String? = null,
)

@Serializable
data class ReactRequest(val emoji: String)

// Payloads dos eventos de socket do backend (messages.ts).
@Serializable
data class MessageEditedEventDto(
    val messageId: String,
    val channelId: String,
    val content: String = "",
    val edited: Boolean = true,
)

@Serializable
data class MessageDeletedEventDto(
    val messageId: String,
    val channelId: String,
)

// user_typing traz username; user_stopped_typing vem sem (mesmo shape serve).
@Serializable
data class ChannelTypingEventDto(
    val userId: String,
    val username: String? = null,
    val channelId: String,
)

// channel_activity vai pra sala pessoal user:{id} de cada membro do servidor.
@Serializable
data class ChannelActivityEventDto(
    val channelId: String,
    val lastMessageAt: String? = null,
    // QUEM e O QUÊ, para o aviso de mensagem nova. Antes o evento trazia só o id do
    // canal, e por isso o aviso do desktop era obrigado a dizer "nova mensagem" — o
    // bastante para interromper e insuficiente para decidir se valia ser interrompido.
    //
    // Todos opcionais porque o mesmo evento é a fonte da BOLINHA de não-lido, e essa
    // parte não pode depender de campo nenhum: um servidor mais antigo (ou um caminho
    // que ainda não enriquece) continua acendendo o não-lido normalmente, só sem
    // conteúdo no aviso.
    val channelName: String? = null,
    val serverName: String? = null,
    val authorName: String? = null,
    val authorAvatar: String? = null,
    val preview: String? = null,
)

// Alguem entrou (joined=true) ou saiu de um canal de voz. Delta imediato pra barra
// lateral; a rota /voice/presence continua sendo a fonte autoritativa.
@Serializable
data class VoicePresenceEventDto(
    val channelId: String,
    val userId: String,
    val joined: Boolean = false,
)
