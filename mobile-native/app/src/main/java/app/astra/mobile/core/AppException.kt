package app.astra.mobile.core

class ApiException(message: String, val code: String? = null) : Exception(message)
