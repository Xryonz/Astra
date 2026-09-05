package app.astra.desktop.net

import app.astra.mobile.core.network.dto.ApiError
import kotlinx.serialization.json.Json
import retrofit2.HttpException

fun mensagemDaApi(json: Json, t: Throwable?, reserva: String): String {
    val http = t as? HttpException ?: return "$reserva — sem conexão"
    val dito = runCatching { http.response()?.errorBody()?.string() }.getOrNull()
        ?.let { runCatching { json.decodeFromString<ApiError>(it) }.getOrNull() }
    return dito?.error?.takeIf { it.isNotBlank() } ?: "$reserva (erro ${http.code()})"
}
