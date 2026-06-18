package app.astra.mobile.core

/**
 * Erro de aplicacao com mensagem ja amigavel pro usuario.
 * Vive em core/ (neutro) pra domain e data dependerem dele sem cruzar camadas.
 */
class ApiException(message: String, val code: String? = null) : Exception(message)
