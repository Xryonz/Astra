package app.astra.desktop.net

import okhttp3.OkHttpClient

object ClienteCompartilhado {
    val base: OkHttpClient by lazy { OkHttpClient() }
}
