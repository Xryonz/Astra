package app.astra.desktop.net

import okhttp3.Dispatcher
import okhttp3.OkHttpClient

object ClienteCompartilhado {
    val base: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .apply { if (RelogioDeRede.ligado) eventListener(RelogioDeRede.ouvinte) }
            .build()
    }

    fun comFilaPropria(): OkHttpClient.Builder = base.newBuilder().dispatcher(Dispatcher())
}
