package app.astra.desktop

import java.io.File

object Instalacao {

    const val PASTA_FIXA = "atual"
    const val SUFIXO_DA_RESERVA = ".antiga"

    private const val LANCADOR = "launch.vbs"
    private const val EXECUTAVEL = "Astra.exe"
    private const val ICONE = "astra.ico"
    private const val PASTA_DAS_VERSOES = "versions"
    private const val PASTA_DOS_ZIPS = "zips"
    private const val PREFIXO_DO_PALCO = ".astra-"
    private const val NIVEIS_ATE_A_RAIZ = 3

    val exe: File? by lazy {
        System.getProperty("jpackage.app-path")
            ?.takeIf { it.endsWith(".exe", ignoreCase = true) }
            ?.let(::File)
            ?.takeIf { it.isFile }
    }

    val imagem: File? by lazy { exe?.parentFile }

    val raiz: File? by lazy {
        var pasta = imagem?.parentFile
        var restam = NIVEIS_ATE_A_RAIZ
        while (pasta != null && restam > 0) {
            if (File(pasta, LANCADOR).isFile) return@lazy pasta
            pasta = pasta.parentFile
            restam--
        }
        null
    }

    val fixa: File? by lazy { raiz?.let { File(it, PASTA_FIXA) } ?: imagem }

    val reserva: File? by lazy {
        fixa?.let { alvo ->
            alvo.parentFile?.let { File(it, alvo.name + SUFIXO_DA_RESERVA) }
        }
    }

    val noLugarCerto: Boolean
        get() {
            val onde = imagem ?: return false
            val devia = fixa ?: return false
            return mesmaPasta(onde, devia)
        }

    val exeFixo: File? get() = fixa?.let { File(it, EXECUTAVEL) }

    fun palcoPara(versao: String): File? {
        raiz?.let { return File(File(it, PASTA_DAS_VERSOES), versao) }
        val vizinha = fixa?.parentFile ?: return null
        return File(vizinha, "$PREFIXO_DO_PALCO$versao")
    }

    fun pastaDosZips(): File? {
        raiz?.let { return File(it, PASTA_DOS_ZIPS) }
        val vizinha = fixa?.parentFile ?: return null
        return File(vizinha, "$PREFIXO_DO_PALCO$PASTA_DOS_ZIPS")
    }

    fun icone(): File? = raiz?.let { File(it, ICONE) }?.takeIf { it.isFile }

    fun lancador(): File? = raiz?.let { File(it, LANCADOR) }?.takeIf { it.isFile }

    data class Alvo(
        val programa: String,
        val argumentos: String,
        val pasta: String,
        val simbolo: String,
    )

    fun alvoDoAtalho(): Alvo? {
        val vbs = lancador()
        val comLancador = raiz
        if (vbs != null && comLancador != null) {
            val janelas = System.getenv("SystemRoot") ?: "C:\\Windows"
            return Alvo(
                programa = "$janelas\\System32\\wscript.exe",
                argumentos = "\"${vbs.absolutePath}\"",
                pasta = comLancador.absolutePath,
                simbolo = icone()?.absolutePath ?: exe?.absolutePath.orEmpty(),
            )
        }
        val programa = exeFixo?.absolutePath ?: return null
        val pasta = fixa?.absolutePath ?: return null
        return Alvo(programa, "", pasta, icone()?.absolutePath ?: programa)
    }

    fun descartaveis(): List<File> {
        val lista = ArrayList<File>()
        reserva?.let { if (it.isDirectory) lista.add(it) }
        val comLancador = raiz
        if (comLancador != null) {
            File(comLancador, PASTA_DAS_VERSOES).listFiles()?.filterTo(lista) { it.isDirectory }
            File(comLancador, PASTA_DOS_ZIPS).listFiles()?.filterTo(lista) { it.isFile }
            val pastaDeVersao = Regex("""^\d+\.\d+\.\d+$""")
            comLancador.listFiles()?.filterTo(lista) { it.isDirectory && pastaDeVersao.matches(it.name) }
        } else {
            val vizinha = fixa?.parentFile
            val zips = pastaDosZips()
            vizinha?.listFiles()?.filterTo(lista) {
                it.isDirectory && it.name.startsWith(PREFIXO_DO_PALCO) && !mesmaPasta(it, zips)
            }
            zips?.listFiles()?.filterTo(lista) { it.isFile }
        }
        return lista
    }

    fun mesmaPasta(a: File?, b: File?): Boolean {
        if (a == null || b == null) return false
        return runCatching {
            a.canonicalPath.equals(b.canonicalPath, ignoreCase = true)
        }.getOrDefault(false)
    }
}
