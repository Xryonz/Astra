package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

// Apagar conta. `confirmacao` e o proprio @ digitado a mao; `password` so existe
// pra quem tem senha (conta de Google nao tem o que conferir).
@Serializable
data class ApagarContaRequest(
    val confirmacao: String,
    val password: String? = null,
)

@Serializable
data class ContaApagadaDto(val apagada: Boolean = false)

// O 409 de "voce ainda e dono de constelacao" NAO e um erro qualquer: ele carrega
// a lista do que resolver, e a tela mostra os nomes. Por isso tem forma propria em
// vez de virar um texto solto.
@Serializable
data class RecusaDeApagar(
    val error: String? = null,
    val constelacoes: List<ConstelacaoPresaDto> = emptyList(),
)

@Serializable
data class ConstelacaoPresaDto(val id: String, val name: String)
