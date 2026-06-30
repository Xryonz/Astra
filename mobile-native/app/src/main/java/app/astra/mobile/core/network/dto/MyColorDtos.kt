package app.astra.mobile.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class MyColorRequest(val nameColor: String?)

@Serializable
data class MyColorResponse(val nameColor: String? = null)
