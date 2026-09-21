package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class RelatoDto(
    val id: String,
    val instalacao: String = "",
    val versao: String = "",
    val so: String? = null,
    val tipo: String = "",
    val mensagem: String? = null,
    val rastro: String = "",
    val createdAt: String? = null,
)
