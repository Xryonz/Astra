package app.astra.desktop.voice

import kotlin.time.TimeMark
import kotlin.time.TimeSource

class ResumoDaCall {
    @Volatile private var inicio: TimeMark = TimeSource.Monotonic.markNow()
    @Volatile private var quedasDoMotor = 0
    @Volatile private var quedasDaConexao = 0
    @Volatile private var maiorPerdaDaVoz = 0
    @Volatile private var transmitiuATela = false

    fun comecar() {
        inicio = TimeSource.Monotonic.markNow()
        quedasDoMotor = 0
        quedasDaConexao = 0
        maiorPerdaDaVoz = 0
        transmitiuATela = false
    }

    fun motorCaiu() {
        quedasDoMotor++
    }

    fun conexaoCaiu() {
        quedasDaConexao++
    }

    fun perdaDaVoz(perda: Int) {
        if (perda > maiorPerdaDaVoz) maiorPerdaDaVoz = perda
    }

    fun transmitiu() {
        transmitiuATela = true
    }

    fun linha(): String {
        val duracao = inicio.elapsedNow().toComponents { minutos, segundos, _ -> "$minutos min $segundos s" }
        val versao = System.getProperty("astra.version") ?: "dev"
        val tela = if (transmitiuATela) "sim" else "não"
        return "resumo ($versao): $duracao; quedas do motor: $quedasDoMotor; " +
            "quedas da conexão: $quedasDaConexao; maior perda da voz: $maiorPerdaDaVoz%; transmitiu a tela: $tela"
    }
}
