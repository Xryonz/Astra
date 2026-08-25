package app.astra.desktop.voice

import app.astra.desktop.CrashLog
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object VoiceLog {
    private const val ARQUIVO = "voz.txt"
    private const val MAX_LINHAS = 120
    private val HORA = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    private val recentes = ArrayDeque<Pair<Long, String>>()

    fun nota(texto: String) {
        val agora = System.currentTimeMillis()
        synchronized(recentes) {
            recentes.addLast(agora to texto)
            while (recentes.size > MAX_LINHAS) recentes.removeFirst()
        }
        runCatching {
            val f = File(CrashLog.dataDir(), ARQUIVO)
            if (f.length() > 200_000) f.writeText("")
            f.appendText("${HORA.format(Instant.ofEpochMilli(agora))}  $texto\n")
        }
    }

    fun linhas(): List<Pair<Long, String>> = synchronized(recentes) { recentes.toList() }

    fun arquivo(): File = File(CrashLog.dataDir(), ARQUIVO)
}
