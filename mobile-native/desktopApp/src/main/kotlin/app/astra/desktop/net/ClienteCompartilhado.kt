package app.astra.desktop.net

import okhttp3.Dispatcher
import okhttp3.OkHttpClient

object ClienteCompartilhado {
    private const val PEDIDOS_POR_ENDERECO = 16

    val base: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dispatcher(Dispatcher().apply { maxRequestsPerHost = PEDIDOS_POR_ENDERECO })
            .build()
    }
}
