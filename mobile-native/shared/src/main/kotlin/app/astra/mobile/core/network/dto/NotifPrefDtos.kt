package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

// Modos: "all" | "mentions" | "mute". Resolucao no backend: pref explicita do
// canal (mesmo "all") > pref do servidor > "all".
@Serializable
data class ChannelNotifPrefDto(
    val channelId: String,
    val mode: String,
)

@Serializable
data class ServerNotifPrefDto(
    val serverId: String,
    val mode: String,
)

@Serializable
data class NotifModeRequest(
    val mode: String,
)

// AVISOS DA CONTA — o que o SERVIDOR decide, e não esta máquina.
//
// Não confundir com os interruptores do DesktopPrefs: aqueles mandam no balão da
// bandeja deste computador. Estes decidem se o aviso CHEGA A EXISTIR — o servidor
// consulta antes de gravar a notificação, então desligar "reações" aqui apaga a
// reação do sino, do push e do celular de uma vez.
//
// `sounds` NÃO está aqui de propósito: o campo existe no backend, é guardado e
// devolvido, e nenhuma linha do servidor o lê. Expor um interruptor morto é pior
// que não ter interruptor — a pessoa desliga, o som continua, e ela conclui que o
// app mente.
@Serializable
data class AvisosDaContaDto(
    val mentions: Boolean = true,
    val dms: Boolean = true,
    val reactions: Boolean = true,
    val replies: Boolean = true,
    // Governa o PUSH (navegador e celular), apesar do nome. Não tem efeito no
    // aviso deste app, que chega por socket.
    val desktop: Boolean = true,
    val quietStart: Int? = null,
    val quietEnd: Int? = null,
)

@Serializable
data class AvisosDaContaResposta(
    val prefs: AvisosDaContaDto = AvisosDaContaDto(),
)

// PEDIDO SEM VALOR PADRÃO EM NENHUM CAMPO, e isso é obrigatório, não estilo.
//
// O Json do app é `Json { ignoreUnknownKeys = true; explicitNulls = false }`, e o
// `encodeDefaults` do kotlinx é FALSO por omissão: todo campo igual ao seu próprio
// default sai do JSON. Como o servidor faz `{ ...atual, ...patch }`, um campo
// ausente significa "não mexe". Se `mentions` tivesse default `true`, RELIGAR
// menções mandaria um corpo sem `mentions` e o servidor manteria o `false` — o
// interruptor voltaria sozinho, e o motivo seria invisível.
//
// A HORA VAI COMO -1 E NÃO COMO NULO pelo mesmo tipo de motivo: `explicitNulls =
// false` apaga nulo na saída, então "limpar o descanso" nunca chegaria. Mandar
// sentinela é o que este repositório já faz no `botNoticeChannelId` (string vazia
// = voltar ao automático); o servidor traduz de volta.
@Serializable
data class AvisosDaContaRequest(
    val mentions: Boolean,
    val dms: Boolean,
    val reactions: Boolean,
    val replies: Boolean,
    val desktop: Boolean,
    val quietStart: Int,
    val quietEnd: Int,
) {
    companion object {
        /** -1 = sem horário de descanso. Ver o comentário da classe. */
        const val SEM_HORA = -1

        fun de(p: AvisosDaContaDto) = AvisosDaContaRequest(
            mentions = p.mentions,
            dms = p.dms,
            reactions = p.reactions,
            replies = p.replies,
            desktop = p.desktop,
            quietStart = p.quietStart ?: SEM_HORA,
            quietEnd = p.quietEnd ?: SEM_HORA,
        )
    }
}
