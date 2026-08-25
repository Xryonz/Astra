package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ApagarContaRequest(
    val confirmacao: String,
    val password: String? = null,
)

@Serializable
data class ContaApagadaDto(val apagada: Boolean = false)

@Serializable
data class RecusaDeApagar(
    val error: String? = null,
    val constelacoes: List<ConstelacaoPresaDto> = emptyList(),
)

@Serializable
data class ConstelacaoPresaDto(val id: String, val name: String)

@Serializable
data class LogoutRequest(val refreshToken: String)
