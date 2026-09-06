package app.astra.desktop

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream

object Saida {
    private const val FILE = "saida.txt"
    private const val ANTERIOR = "saida-anterior.txt"
    private const val TETO_BYTES = 2L * 1024 * 1024

    fun capturar() = runCatching {
        val arquivo = File(CrashLog.dataDir(), FILE)
        if (arquivo.exists()) {
            runCatching { arquivo.copyTo(File(CrashLog.dataDir(), ANTERIOR), overwrite = true) }
        }
        val destino = PrintStream(Limitado(FileOutputStream(arquivo, false), TETO_BYTES), true, "UTF-8")
        System.setOut(Espelho(System.out, destino))
        System.setErr(Espelho(System.err, destino))
    }

    private class Espelho(val original: PrintStream, val copia: PrintStream) :
        PrintStream(original, true, "UTF-8") {
        override fun write(b: Int) {
            original.write(b)
            runCatching { copia.write(b) }
        }

        override fun write(buf: ByteArray, off: Int, len: Int) {
            original.write(buf, off, len)
            runCatching { copia.write(buf, off, len) }
        }

        override fun flush() {
            original.flush()
            runCatching { copia.flush() }
        }
    }

    private class Limitado(val alvo: OutputStream, val teto: Long) : OutputStream() {
        private var escritos = 0L

        override fun write(b: Int) {
            if (escritos >= teto) return
            alvo.write(b)
            escritos++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (escritos >= teto) return
            val cabe = minOf(len.toLong(), teto - escritos).toInt()
            alvo.write(b, off, cabe)
            escritos += cabe
        }

        override fun flush() = alvo.flush()
        override fun close() = alvo.close()
    }
}
