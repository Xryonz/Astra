package app.astra.desktop.net

import app.astra.desktop.CrashLog
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object RedeLog {
    private val hora = DateTimeFormatter.ofPattern("HH:mm:ss")
    private const val TETO_BYTES = 128 * 1024

    private val arquivo: File by lazy { File(CrashLog.dataDir(), "rede.txt") }

    fun falhou(oQue: String, tentativa: Int, erro: Throwable) {
        runCatching {
            if (arquivo.length() > TETO_BYTES) arquivo.writeText("")
            val causa = erro.message?.take(160).orEmpty()
            arquivo.appendText(
                "${LocalTime.now().format(hora)}  $oQue  tentativa $tentativa  " +
                    "${erro::class.simpleName}${if (causa.isBlank()) "" else ": $causa"}\n",
            )
        }
    }

    fun imagemMorreu(url: String) {
        runCatching {
            if (arquivo.length() > TETO_BYTES) arquivo.writeText("")
            arquivo.appendText("${LocalTime.now().format(hora)}  imagem nao carregou  ${url.take(200)}\n")
        }
    }
}
