package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

// Efeito sonoro da constelacao (soundboard).
@Serializable
data class ServerSoundDto(
    val id: String = "",
    val serverId: String = "",
    val name: String = "",
    val url: String = "",
    // Gravado no cadastro em vez de lido do arquivo na hora de desenhar a lista:
    // abrir cada WAV do bucket so pra saber quanto dura seria uma requisicao por
    // som a cada abertura do painel.
    val durationMs: Int = 0,
)

@Serializable
data class SoundsResponse(val sounds: List<ServerSoundDto> = emptyList())

@Serializable
data class CriarSomRequest(
    val name: String,
    val url: String,
    val durationMs: Int = 0,
)

// Corpo do POST /play. O canal vai no corpo porque quem toca escolhe a ORBITA em
// que esta, e ela nao aparece na rota (a rota e do som, nao do canal).
@Serializable
data class TocarSomRequest(val channelId: String)

// Evento de socket "soundboard_play": o servidor nao manda audio, manda o aviso.
// Cada cliente na sala baixa e toca o arquivo — assim todo mundo ouve o ORIGINAL,
// sem passar pelo codec de voz.
@Serializable
data class SoundboardPlayDto(
    val channelId: String = "",
    val soundId: String = "",
    val name: String = "",
    val url: String = "",
    val byUserId: String = "",
)
