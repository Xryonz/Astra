package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class TranslateRequest(val text: String, val targetLang: String = "pt")

@Serializable
data class TranslateResultDto(val translation: String = "", val cached: Boolean = false)
