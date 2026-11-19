package app.astra.desktop.net

import app.astra.desktop.CrashLog
import okhttp3.Call
import okhttp3.EventListener
import java.io.File
import java.io.IOException
import java.lang.management.ManagementFactory

object RelogioDeRede {
    val ligado: Boolean = System.getProperty("astra.medir") != null

    private val arquivo: File by lazy { File(CrashLog.dataDir(), "relogio-de-rede.txt") }

    fun marcar(oQue: String) {
        if (!ligado) return
        val desdeOInicio = ManagementFactory.getRuntimeMXBean().uptime
        synchronized(this) { arquivo.appendText("$desdeOInicio\t$oQue\n") }
    }

    val ouvinte: EventListener = object : EventListener() {
        override fun callStart(call: Call) = marcar("começou ${call.request().url.encodedPath}")

        override fun callEnd(call: Call) = marcar("terminou ${call.request().url.encodedPath}")

        override fun callFailed(call: Call, ioe: IOException) = marcar("falhou ${call.request().url.encodedPath}")
    }
}
