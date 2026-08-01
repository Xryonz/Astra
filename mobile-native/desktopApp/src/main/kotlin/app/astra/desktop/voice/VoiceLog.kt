package app.astra.desktop.voice

import app.astra.desktop.CrashLog
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Diario da call.
//
// POR QUE ISTO EXISTE: "ninguem me escuta e eu nao escuto ninguem" pode quebrar em
// oito lugares diferentes — a lib nativa, o token do backend, o signaling, o join,
// a captura do mic, a publicacao da track, a assinatura do audio do outro, a saida
// de som. Todos dao exatamente o mesmo sintoma: silencio. E o codigo ja sabia de
// qual se tratava — so nao contava pra ninguem.
//
// Aqui cada etapa deixa uma linha. Com isso, "nao funciona" vira "parou no passo
// 4", que e a diferenca entre consertar em minutos e chutar por semanas.
//
// Grava em arquivo de proposito: na maioria das vezes quem esta com o problema e
// um amigo, do outro lado, e pedir print da tela nunca traz a informacao certa.
// O arquivo pode ser mandado inteiro.
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
        // Best-effort: diagnostico nunca pode derrubar a call.
        runCatching {
            val f = File(CrashLog.dataDir(), ARQUIVO)
            // Comeca do zero quando passa de ~200KB: e diario de depuracao, nao
            // historico eterno, e arquivo gigante ninguem manda.
            if (f.length() > 200_000) f.writeText("")
            f.appendText("${HORA.format(Instant.ofEpochMilli(agora))}  $texto\n")
        }
    }

    fun linhas(): List<Pair<Long, String>> = synchronized(recentes) { recentes.toList() }

    fun arquivo(): File = File(CrashLog.dataDir(), ARQUIVO)
}
