package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class UploadResponse(val attachments: List<AttachmentDto> = emptyList())

@Serializable
data class AttachmentDto(
    val url: String,
    // Versao pequena (~720px) da mesma imagem. A bolha do chat mostra ESTA; o
    // original so e baixado ao abrir em tela cheia. Nula quando a imagem ja era
    // pequena — ai `url` serve pros dois.
    val thumbUrl: String? = null,
    val type: String? = null,
    val name: String? = null,
    val size: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val blurhash: String? = null,
    val duration: Int? = null,
    // Marca de FIGURINHA: desenha em tamanho fixo e sem abrir em tela cheia, em
    // vez de virar mais uma imagem grande e clicavel. Precisa existir tambem no
    // AttachmentSchema do backend (packages/types) — o Zod apaga chave que o
    // schema nao declara, e a marca sumiria em silencio no caminho.
    val sticker: Boolean? = null,
)
