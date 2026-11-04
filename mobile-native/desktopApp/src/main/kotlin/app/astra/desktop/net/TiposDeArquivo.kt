package app.astra.desktop.net

import java.io.File

private val TIPO_POR_EXTENSAO = mapOf(
    "png" to "image/png",
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "gif" to "image/gif",
    "webp" to "image/webp",
    "avif" to "image/avif",
    "mp4" to "video/mp4",
    "webm" to "video/webm",
    "mov" to "video/quicktime",
    "mp3" to "audio/mpeg",
    "wav" to "audio/wav",
    "ogg" to "audio/ogg",
    "m4a" to "audio/x-m4a",
    "aac" to "audio/aac",
    "pdf" to "application/pdf",
    "txt" to "text/plain",
    "zip" to "application/zip",
    "json" to "application/json",
)

private val TIPOS_DE_EMOJI_E_FIGURINHA = setOf("image/png", "image/gif", "image/webp", "image/jpeg")

internal fun tipoPelaExtensao(arquivo: File): String? = TIPO_POR_EXTENSAO[arquivo.extension.lowercase()]

internal fun tipoDeEmojiOuFigurinha(arquivo: File): String =
    tipoPelaExtensao(arquivo)?.takeIf { it in TIPOS_DE_EMOJI_E_FIGURINHA } ?: error("use PNG, GIF ou WebP")
