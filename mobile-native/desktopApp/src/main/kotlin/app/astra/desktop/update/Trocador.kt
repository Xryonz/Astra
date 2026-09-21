package app.astra.desktop.update

import app.astra.desktop.ARG_POS_ATUALIZACAO
import app.astra.desktop.CrashLog
import app.astra.desktop.Instalacao
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalTime
import java.util.concurrent.TimeUnit

private const val ARG_INSTALAR_NO_LUGAR = "--instalar-no-lugar"
private const val ARG_FIXA = "--fixa="
private const val ARG_ANTERIOR = "--anterior="
private const val ARG_VERSAO = "--versao="

private const val EXECUTAVEL = "Astra.exe"
private const val DIARIO = "troca.log"
private const val PREFIXO = "astra-troca-"
private const val SUFIXO = ".vbs"
private const val VALIDADE_DO_ROTEIRO_MS = 60L * 60_000L

private const val PRAZO_DO_SINAL_MS = 20_000L
private const val PRAZO_DA_SAIDA_S = 60L
private const val PRAZO_DO_ENCERRAMENTO_S = 10L
private const val PRAZO_DOS_VIZINHOS_MS = 15_000L
private const val TENTATIVAS_DE_MOVER = 20
private const val PAUSA_MS = 500L
private const val PAUSA_DO_SINAL_MS = 150L
private const val NOMES_DE_RESERVA = 40

internal object Trocador {

    fun precisaTrocar(nova: File): Boolean {
        if (!noWindows()) return false
        val fixa = Instalacao.fixa ?: return false
        return !Instalacao.mesmaPasta(nova, fixa)
    }

    fun preparar(nova: File, versao: String): Boolean {
        val fixa = Instalacao.fixa ?: return false
        val copia = copiaDe(fixa)
        anotar("preparando a troca para $versao", recomecar = true)
        if (copia.exists() && !copia.deleteRecursively()) {
            anotar("sobrou uma copia anterior que nao sai")
            return false
        }
        val pronta = runCatching {
            espelhar(nova, copia)
            arquivos(nova) == arquivos(copia) && File(copia, EXECUTAVEL).isFile
        }.getOrDefault(false)
        if (!pronta) {
            anotar("a copia da versao nova nao ficou identica")
            copia.deleteRecursively()
        }
        return pronta
    }

    fun trocar(nova: File, versao: String): Boolean {
        val fixa = Instalacao.fixa ?: return false
        val eu = ProcessHandle.current().pid()
        val sinal = sinalDe(eu).apply { delete() }
        val ajudante = runCatching {
            ProcessBuilder(
                File(nova, EXECUTAVEL).absolutePath,
                ARG_INSTALAR_NO_LUGAR,
                ARG_FIXA + fixa.absolutePath,
                ARG_ANTERIOR + eu,
                ARG_VERSAO + versao,
            ).directory(fixa.parentFile ?: nova.parentFile)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        }.getOrNull()
        if (ajudante == null) {
            anotar("a versao nova nao abriu")
            desistirDaCopia(fixa)
            return false
        }
        val fim = System.nanoTime() + PRAZO_DO_SINAL_MS * 1_000_000L
        while (System.nanoTime() < fim) {
            if (sinal.isFile) {
                sinal.delete()
                return true
            }
            if (!ajudante.isAlive) break
            Thread.sleep(PAUSA_DO_SINAL_MS)
        }
        anotar("a versao nova nao confirmou que assumiu a troca")
        ajudante.descendants().forEach { it.destroyForcibly() }
        ajudante.destroyForcibly()
        desistirDaCopia(fixa)
        return false
    }

    fun executarSePedido(args: Array<String>): Boolean {
        if (ARG_INSTALAR_NO_LUGAR !in args) return false
        val fixa = valorDe(args, ARG_FIXA)?.let(::File)
        val anterior = valorDe(args, ARG_ANTERIOR)?.toLongOrNull()
        val versao = valorDe(args, ARG_VERSAO)
        if (fixa == null || anterior == null || versao == null) return true
        anotar("a versao $versao assumiu a troca")
        runCatching { sinalDe(anterior).writeText(versao) }
        instalar(fixa, anterior, versao)
        return true
    }

    private fun instalar(fixa: File, anterior: Long, versao: String) {
        val copia = copiaDe(fixa)
        esperarSair(anterior)
        sinalDe(anterior).delete()
        liberarPasta(fixa)
        if (!File(copia, EXECUTAVEL).isFile) {
            anotar("a copia da versao nova sumiu")
            abrir(fixa, null)
            return
        }
        val reserva = nomeLivre(File(fixa.parentFile, fixa.name + Instalacao.SUFIXO_DA_RESERVA))
        if (reserva == null || !mover(fixa, reserva)) {
            anotar("a versao anterior nao saiu do lugar")
            copia.deleteRecursively()
            abrir(fixa, null)
            return
        }
        val assumiu = mover(copia, fixa)
        if (assumiu && abrir(fixa, "$ARG_POS_ATUALIZACAO=$versao")) {
            anotar("versao $versao no lugar")
            return
        }
        anotar("a versao nova nao assumiu; devolvendo a anterior")
        if (assumiu) mover(fixa, copia)
        val devolvida = !fixa.exists() && mover(reserva, fixa)
        if (!abrir(if (devolvida) fixa else reserva, null)) anotar("nao consegui reabrir o Astra")
        copia.deleteRecursively()
    }

    private fun esperarSair(pid: Long) {
        val processo = ProcessHandle.of(pid).orElse(null) ?: return
        if (esperar(processo, PRAZO_DA_SAIDA_S)) return
        anotar("a versao anterior nao fechou em ${PRAZO_DA_SAIDA_S}s; encerrando")
        processo.destroyForcibly()
        esperar(processo, PRAZO_DO_ENCERRAMENTO_S)
    }

    private fun esperar(processo: ProcessHandle, segundos: Long): Boolean = runCatching {
        processo.onExit().get(segundos, TimeUnit.SECONDS)
        true
    }.getOrDefault(!processo.isAlive)

    private fun liberarPasta(fixa: File) {
        val prefixo = fixa.absolutePath.trimEnd(File.separatorChar) + File.separator
        val eu = ProcessHandle.current().pid()
        fun presos() = ProcessHandle.allProcesses()
            .filter { p ->
                p.pid() != eu && p.info().command().map { it.startsWith(prefixo, ignoreCase = true) }.orElse(false)
            }
            .toList()
        val fim = System.nanoTime() + PRAZO_DOS_VIZINHOS_MS * 1_000_000L
        while (presos().isNotEmpty() && System.nanoTime() < fim) Thread.sleep(PAUSA_MS)
        presos()
            .filterNot { File(it.info().command().orElse("")).name.equals(EXECUTAVEL, ignoreCase = true) }
            .forEach {
                anotar("encerrando ${it.info().command().orElse("?")}")
                it.destroyForcibly()
            }
    }

    private fun espelhar(origem: File, destino: File) {
        val base = origem.toPath()
        origem.walkTopDown().forEach { item ->
            val alvo = destino.toPath().resolve(base.relativize(item.toPath()))
            if (item.isDirectory) {
                Files.createDirectories(alvo)
            } else {
                runCatching { Files.createLink(alvo, item.toPath()) }
                    .getOrElse { Files.copy(item.toPath(), alvo, StandardCopyOption.COPY_ATTRIBUTES) }
            }
        }
    }

    private fun arquivos(pasta: File): Map<String, Long> {
        val base = pasta.toPath()
        return pasta.walkTopDown()
            .filter { it.isFile }
            .associate { base.relativize(it.toPath()).toString() to it.length() }
    }

    private fun nomeLivre(preferida: File): File? =
        (sequenceOf(preferida) + (1..NOMES_DE_RESERVA).asSequence().map { File(preferida.parentFile, "${preferida.name}-$it") })
            .firstOrNull { !it.exists() }

    private fun mover(de: File, para: File): Boolean {
        repeat(TENTATIVAS_DE_MOVER) {
            if (runCatching { Files.move(de.toPath(), para.toPath()) }.isSuccess) return true
            Thread.sleep(PAUSA_MS)
        }
        anotar("nao consegui mover ${de.name} para ${para.name}")
        return false
    }

    private fun abrir(pasta: File, marca: String?): Boolean = runCatching {
        val exe = File(pasta, EXECUTAVEL)
        if (!exe.isFile) return false
        ProcessBuilder(listOfNotNull(exe.absolutePath, marca))
            .directory(pasta.parentFile ?: pasta)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        true
    }.getOrDefault(false)

    private fun desistirDaCopia(fixa: File) {
        copiaDe(fixa).deleteRecursively()
    }

    private fun copiaDe(fixa: File) = File(fixa.parentFile, fixa.name + Instalacao.SUFIXO_DA_COPIA)

    private fun sinalDe(pid: Long) = File(CrashLog.dataDir(), "troca-$pid.pronta")

    private fun valorDe(args: Array<String>, prefixo: String): String? =
        args.firstOrNull { it.startsWith(prefixo) }?.removePrefix(prefixo)?.takeIf { it.isNotBlank() }

    private fun anotar(texto: String, recomecar: Boolean = false) {
        runCatching {
            val linha = "${LocalTime.now().withNano(0)} $texto\n"
            val diario = File(CrashLog.dataDir(), DIARIO)
            if (recomecar) diario.writeText(linha) else diario.appendText(linha)
        }
    }

    fun limparRoteiros() {
        val corte = System.currentTimeMillis() - VALIDADE_DO_ROTEIRO_MS
        pastaDosRoteiros().listFiles()
            ?.filter { it.isFile && it.name.startsWith(PREFIXO) && it.name.endsWith(SUFIXO) }
            ?.filter { it.lastModified() < corte }
            ?.forEach { runCatching { it.delete() } }
    }

    private fun pastaDosRoteiros(): File =
        File(System.getProperty("java.io.tmpdir") ?: ".")

    private fun noWindows(): Boolean =
        System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
}
