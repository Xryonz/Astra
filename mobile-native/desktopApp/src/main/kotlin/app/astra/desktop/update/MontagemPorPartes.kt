package app.astra.desktop.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.CRC32
import java.util.zip.Inflater

private const val GUARDADO = 0
private const val ENCOLHIDO = 8

private const val MAIOR_PEDIDO = 8L * 1024 * 1024
private const val VAO_QUE_VALE_PULAR = 96L * 1024
private const val TAMANHO_DO_PEDACO = 2L * 1024 * 1024
private const val PEDIDOS_SIMULTANEOS = 4

private const val TETO_NA_MEMORIA = 64L * 1024 * 1024

private const val ASSINATURA_DO_CABECALHO_LOCAL = 0x04034b50
private const val CABECALHO_LOCAL_FIXO = 30
private const val FOLGA_DO_CABECALHO = 512
private const val REMONTAR_SE_BAIXAR_ATE = 0.8

private const val FATIA_DA_CONFERENCIA = 0.35f
private const val PASSO_MINIMO_DO_PROGRESSO = 0.002f

private val NOME_COM_RESUMO = Regex("""^(.+)-[0-9a-f]{16,64}\.jar$""")

private val despachoDosPedidos = Dispatchers.IO.limitedParallelism(PEDIDOS_SIMULTANEOS)

internal data class Montagem(
    val jaNoPalco: Int,
    val reaproveitados: Int,
    val remontados: Int,
    val baixados: Int,
    val bytesDaRede: Long,
    val bytesTotais: Long,
)

internal class MontagemPorPartes(http: OkHttpClient, url: String) {

    private val zip = ZipRemoto(http, url)

    private class Bloco(val de: Long, val ate: Long, val entradas: List<Pair<EntradaDoZip, String>>) {
        val tamanho: Long get() = ate - de
    }

    private class Grupo(
        val blocos: List<Bloco>,
        val jar: JarRemontavel? = null,
        val concluir: (Map<Bloco, ByteArray>) -> Boolean,
    ) {
        val tamanho: Long = blocos.sumOf { it.tamanho }
    }

    private class Trecho(val de: Long, val ate: Long, val noVelho: Long?)

    private class JarRemontavel(
        val entrada: EntradaDoZip,
        val rel: String,
        val velho: File,
        val inicio: Long,
        val trechos: List<Trecho>,
        val cauda: ByteArray,
        val caudaDe: Long,
        val blocos: List<Bloco>,
    )

    private class Progresso(private val aoAndar: (Float) -> Unit) {
        private var informado = -1f
        val chegados = AtomicLong()
        @Volatile var esperados = 0L

        @Synchronized
        fun avancar(p: Float) {
            if (p >= 1f || p - informado >= PASSO_MINIMO_DO_PROGRESSO) {
                informado = p
                aoAndar(p)
            }
        }

        fun chegaram(n: Int) {
            val total = chegados.addAndGet(n.toLong())
            val fracao = total.toFloat() / maxOf(esperados, total).coerceAtLeast(1L)
            avancar(FATIA_DA_CONFERENCIA + (1f - FATIA_DA_CONFERENCIA) * fracao)
        }
    }

    suspend fun montar(deOndeVem: File, paraOnde: File, aoAndar: (Float) -> Unit): Montagem? {
        val progresso = Progresso(aoAndar)
        val dir = zip.diretorio() ?: return null
        val arquivos = dir.entradas.filterNot { it.ehPasta }
        if (arquivos.isEmpty()) return null
        if (arquivos.any { it.metodo != GUARDADO && it.metodo != ENCOLHIDO }) return null

        val prefixo = prefixoDaRaiz(arquivos) ?: return null

        val alvos = arquivos.mapNotNull { e ->
            val rel = e.nome.removePrefix(prefixo)
            if (rel.isBlank() || rel.contains("..")) null else e to rel
        }
        if (alvos.isEmpty()) return null

        apagarSobras(paraOnde, alvos.mapTo(HashSet()) { it.second })

        var jaNoPalco = 0
        val reaproveitar = ArrayList<Pair<File, File>>()
        val baixar = ArrayList<Pair<EntradaDoZip, String>>()

        val totalAConferir = alvos.sumOf { (_, rel) ->
            tamanhoSeExistir(File(paraOnde, rel)) + tamanhoSeExistir(File(deOndeVem, rel))
        }
        var conferidos = 0L
        for ((e, rel) in alvos) {
            val noPalco = File(paraOnde, rel)
            val jaTenho = File(deOndeVem, rel)
            conferidos += tamanhoSeExistir(noPalco)
            if (confere(noPalco, e)) {
                jaNoPalco++
            } else {
                conferidos += tamanhoSeExistir(jaTenho)
                if (confere(jaTenho, e)) reaproveitar.add(jaTenho to noPalco) else baixar.add(e to rel)
            }
            if (totalAConferir > 0) {
                progresso.avancar(FATIA_DA_CONFERENCIA * (conferidos.toFloat() / totalAConferir))
            }
        }

        val remontaveis = ArrayList<JarRemontavel>()
        val inteiros = ArrayList<Pair<EntradaDoZip, String>>()
        for ((e, rel) in baixar) {
            val plano = if (e.metodo == GUARDADO && rel.endsWith(".jar")) planejarJar(e, rel, deOndeVem) else null
            if (plano != null) remontaveis.add(plano) else inteiros.add(e to rel)
        }
        if (inteiros.any { (e, _) -> e.comprimido > TETO_NA_MEMORIA }) return null

        val grupos = agrupar(inteiros, dir).map { grupoDoBloco(it, paraOnde) } +
            remontaveis.map { plano ->
                Grupo(plano.blocos, plano) { baixados -> remontar(plano, baixados, File(paraOnde, plano.rel)) }
            }
        val bytesDaRede = grupos.sumOf { it.tamanho }
        if (bytesDaRede >= zip.tamanho()) return null

        paraOnde.mkdirs()
        for ((origem, destino) in reaproveitar) {
            destino.parentFile?.mkdirs()
            if (!ligarOuCopiar(origem, destino)) return null
        }

        progresso.esperados = bytesDaRede
        progresso.avancar(if (bytesDaRede == 0L) 1f else FATIA_DA_CONFERENCIA)

        val pedacos = File(paraOnde.parentFile ?: paraOnde, ".pedacos-${zip.tamanho()}")
        val falharamNaRemontagem = ArrayList<JarRemontavel>()
        for (onda in emOndas(grupos)) {
            for (grupo in baixarEConcluir(onda, pedacos, progresso)) {
                falharamNaRemontagem.add(grupo.jar ?: return null)
            }
        }
        if (falharamNaRemontagem.isNotEmpty()) {
            val segundaChance = agrupar(falharamNaRemontagem.map { it.entrada to it.rel }, dir)
                .map { grupoDoBloco(it, paraOnde) }
            progresso.esperados += segundaChance.sumOf { it.tamanho }
            for (onda in emOndas(segundaChance)) {
                if (baixarEConcluir(onda, pedacos, progresso).isNotEmpty()) return null
            }
        }
        pedacos.deleteRecursively()
        progresso.avancar(1f)

        return Montagem(
            jaNoPalco = jaNoPalco,
            reaproveitados = reaproveitar.size,
            remontados = remontaveis.size - falharamNaRemontagem.size,
            baixados = inteiros.size + falharamNaRemontagem.size,
            bytesDaRede = progresso.chegados.get(),
            bytesTotais = zip.tamanho(),
        )
    }

    private fun grupoDoBloco(bloco: Bloco, paraOnde: File) = Grupo(listOf(bloco)) { baixados ->
        val bruto = baixados.getValue(bloco)
        bloco.entradas.all { (e, rel) ->
            val destino = File(paraOnde, rel)
            destino.parentFile?.mkdirs()
            escrever(bruto, (e.deslocamento - bloco.de).toInt(), e, destino)
        }
    }

    private fun emOndas(grupos: List<Grupo>): List<List<Grupo>> {
        val ondas = ArrayList<List<Grupo>>()
        var atual = ArrayList<Grupo>()
        var soma = 0L
        for (grupo in grupos) {
            if (atual.isNotEmpty() && soma + grupo.tamanho > TETO_NA_MEMORIA) {
                ondas.add(atual)
                atual = ArrayList()
                soma = 0L
            }
            atual.add(grupo)
            soma += grupo.tamanho
        }
        if (atual.isNotEmpty()) ondas.add(atual)
        return ondas
    }

    private suspend fun baixarEConcluir(
        onda: List<Grupo>,
        pedacos: File,
        progresso: Progresso,
    ): List<Grupo> = coroutineScope {
        val trabalho = coroutineContext.job
        onda.map { grupo ->
            async {
                val memoria = grupo.blocos.associateWith { ByteArray(it.tamanho.toInt()) }
                grupo.blocos.flatMap { bloco ->
                    (bloco.de until bloco.ate step TAMANHO_DO_PEDACO).map { de ->
                        val ate = minOf(de + TAMANHO_DO_PEDACO, bloco.ate)
                        async(despachoDosPedidos) {
                            val destino = memoria.getValue(bloco)
                            val noDestino = (de - bloco.de).toInt()
                            val quanto = (ate - de).toInt()
                            val guardado = File(pedacos, "$de-$ate")
                            if (guardado.length() == ate - de) {
                                guardado.inputStream().use { it.readNBytes(destino, noDestino, quanto) }
                                progresso.chegaram(quanto)
                            } else {
                                zip.preencher(de, ate, destino, noDestino, { trabalho.isActive }, progresso::chegaram)
                                guardar(guardado, destino, noDestino, quanto)
                            }
                        }
                    }
                }.awaitAll()
                grupo.takeUnless { it.concluir(memoria) }
            }
        }.awaitAll().filterNotNull()
    }

    private fun guardar(guardado: File, bruto: ByteArray, de: Int, quanto: Int) = runCatching {
        guardado.parentFile?.mkdirs()
        val provisorio = File(guardado.parentFile, guardado.name + ".parcial")
        provisorio.outputStream().use { it.write(bruto, de, quanto) }
        guardado.delete()
        provisorio.renameTo(guardado)
    }

    private fun agrupar(
        baixar: List<Pair<EntradaDoZip, String>>,
        dir: DiretorioDoZip,
    ): List<Bloco> {
        val ordenado = baixar.sortedBy { (e, _) -> e.deslocamento }
        val blocos = ArrayList<Bloco>()
        var atuais = ArrayList<Pair<EntradaDoZip, String>>()
        var de = 0L
        var ate = 0L

        fun fechar() {
            if (atuais.isNotEmpty()) blocos.add(Bloco(de, ate, atuais))
            atuais = ArrayList()
        }

        for (par in ordenado) {
            val (e, _) = par
            val fimDele = dir.fimDosDados[e.nome] ?: (e.deslocamento + 30 + e.comprimido + 4096)
            if (atuais.isEmpty()) {
                de = e.deslocamento
                ate = fimDele
                atuais.add(par)
                continue
            }
            val esticado = maxOf(ate, fimDele)
            val vao = e.deslocamento - ate
            if (vao <= VAO_QUE_VALE_PULAR && esticado - de <= MAIOR_PEDIDO) {
                ate = esticado
                atuais.add(par)
            } else {
                fechar()
                de = e.deslocamento
                ate = fimDele
                atuais.add(par)
            }
        }
        fechar()
        return blocos
    }

    private fun planejarJar(e: EntradaDoZip, rel: String, deOndeVem: File): JarRemontavel? {
        if (e.comprimido != e.cru || e.cru > TETO_NA_MEMORIA) return null
        val velho = parenteDoJar(File(deOndeVem, rel)) ?: return null
        val antigo = lerDiretorioLocal(velho) ?: return null

        val nomeLen = e.nome.toByteArray(Charsets.UTF_8).size
        val cabecalho = zip.faixa(
            e.deslocamento,
            minOf(e.deslocamento + CABECALHO_LOCAL_FIXO + nomeLen + FOLGA_DO_CABECALHO, zip.tamanho()),
        )
        if (cabecalho.size < CABECALHO_LOCAL_FIXO || lerInt(cabecalho, 0) != ASSINATURA_DO_CABECALHO_LOCAL) return null
        val inicio = e.deslocamento + CABECALHO_LOCAL_FIXO + lerCurto(cabecalho, 26) + lerCurto(cabecalho, 28)
        val tamanho = e.cru

        val rodapeDe = tamanho - minOf(tamanho, MAIOR_RODAPE.toLong())
        val rodape = zip.faixa(inicio + rodapeDe, inicio + tamanho)
        var centralBuscado = ByteArray(0)
        val novo = lerDiretorio(tamanho, rodape) { de, ate ->
            zip.faixa(inicio + de, inicio + ate).also { centralBuscado = it }
        } ?: return null
        if (novo.entradas.mapTo(HashSet()) { it.nome }.size != novo.entradas.size) return null

        val caudaDe = minOf(novo.ondeComecaOCentral, rodapeDe)
        val antesDoRodape = (rodapeDe - caudaDe).toInt()
        if (antesDoRodape > centralBuscado.size) return null
        val cauda = centralBuscado.copyOfRange(0, antesDoRodape) + rodape

        val antigos = antigo.entradas.associateBy { it.nome }
        val trechos = ArrayList<Trecho>()
        var cursor = 0L
        for (ent in novo.entradas.sortedBy { it.deslocamento }) {
            if (ent.deslocamento < cursor) return null
            if (ent.deslocamento > cursor) trechos.add(Trecho(cursor, ent.deslocamento, null))
            val fim = novo.fimDosDados.getValue(ent.nome)
            val a = antigos[ent.nome]
            val igual = a != null && a.metodo == ent.metodo && a.crc == ent.crc &&
                a.comprimido == ent.comprimido && a.cru == ent.cru &&
                antigo.fimDosDados.getValue(a.nome) - a.deslocamento == fim - ent.deslocamento
            trechos.add(Trecho(ent.deslocamento, fim, if (igual) a.deslocamento else null))
            cursor = fim
        }
        if (cursor != novo.ondeComecaOCentral) return null
        trechos.add(Trecho(cursor, tamanho, null))

        val faltam = trechos.filter { it.noVelho == null && it.de < caudaDe }
        val blocos = ArrayList<Bloco>()
        for (t in faltam) {
            val ultimo = blocos.lastOrNull()
            val de = inicio + t.de
            val ate = inicio + minOf(t.ate, caudaDe)
            if (ultimo != null && de - ultimo.ate <= VAO_QUE_VALE_PULAR && ate - ultimo.de <= MAIOR_PEDIDO) {
                blocos[blocos.lastIndex] = Bloco(ultimo.de, ate, emptyList())
            } else {
                blocos.add(Bloco(de, ate, emptyList()))
            }
        }
        val aBaixar = blocos.sumOf { it.tamanho } + cauda.size + cabecalho.size
        if (aBaixar > tamanho * REMONTAR_SE_BAIXAR_ATE) return null
        return JarRemontavel(e, rel, velho, inicio, trechos, cauda, caudaDe, blocos)
    }

    private fun remontar(plano: JarRemontavel, baixados: Map<Bloco, ByteArray>, destino: File): Boolean {
        destino.parentFile?.mkdirs()
        destino.delete()
        val confere = CRC32()
        var escritos = 0L
        val certo = runCatching {
            RandomAccessFile(plano.velho, "r").use { velho ->
                FileOutputStream(destino).buffered(1 shl 16).use { saida ->
                    val balde = ByteArray(1 shl 16)
                    for (t in plano.trechos) {
                        var de = t.de
                        if (t.noVelho != null) {
                            velho.seek(t.noVelho)
                            while (de < t.ate) {
                                val n = velho.read(balde, 0, minOf(t.ate - de, balde.size.toLong()).toInt())
                                if (n < 0) return@runCatching false
                                saida.write(balde, 0, n)
                                confere.update(balde, 0, n)
                                de += n
                            }
                        } else {
                            if (de < plano.caudaDe) {
                                val absoluto = plano.inicio + de
                                val bloco = plano.blocos.firstOrNull { absoluto >= it.de && absoluto < it.ate }
                                    ?: return@runCatching false
                                val bruto = baixados[bloco] ?: return@runCatching false
                                val ate = minOf(t.ate, plano.caudaDe)
                                val dentro = (absoluto - bloco.de).toInt()
                                val quanto = (ate - de).toInt()
                                if (dentro + quanto > bruto.size) return@runCatching false
                                saida.write(bruto, dentro, quanto)
                                confere.update(bruto, dentro, quanto)
                                de = ate
                            }
                            if (de < t.ate) {
                                val dentro = (de - plano.caudaDe).toInt()
                                val quanto = (t.ate - de).toInt()
                                saida.write(plano.cauda, dentro, quanto)
                                confere.update(plano.cauda, dentro, quanto)
                                de = t.ate
                            }
                        }
                        escritos += t.ate - t.de
                    }
                }
            }
            true
        }.getOrDefault(false)
        if (certo && escritos == plano.entrada.cru && confere.value == plano.entrada.crc) return true
        destino.delete()
        return false
    }

    private fun parenteDoJar(novo: File): File? {
        val pasta = novo.parentFile ?: return null
        val base = NOME_COM_RESUMO.matchEntire(novo.name)?.groupValues?.get(1) ?: return null
        val candidatos = pasta.listFiles { f ->
            f.isFile && NOME_COM_RESUMO.matchEntire(f.name)?.groupValues?.get(1) == base
        } ?: return null
        return candidatos.singleOrNull()
    }

    private fun apagarSobras(paraOnde: File, esperados: Set<String>) {
        if (!paraOnde.isDirectory) return
        val raiz = paraOnde.toPath()
        paraOnde.walkBottomUp().filter { it.isFile }.forEach { f ->
            if (raiz.relativize(f.toPath()).joinToString("/") !in esperados) f.delete()
        }
    }

    private fun escrever(bruto: ByteArray, dentro: Int, e: EntradaDoZip, destino: File): Boolean {
        if (dentro < 0 || dentro + 30 > bruto.size) return false
        val nomeLen = (bruto[dentro + 26].toInt() and 0xFF) or ((bruto[dentro + 27].toInt() and 0xFF) shl 8)
        val extraLen = (bruto[dentro + 28].toInt() and 0xFF) or ((bruto[dentro + 29].toInt() and 0xFF) shl 8)
        val inicio = dentro + 30 + nomeLen + extraLen
        val fim = inicio + e.comprimido.toInt()
        if (inicio < 0 || fim > bruto.size) return false

        val confere = CRC32()
        destino.delete()
        FileOutputStream(destino).buffered().use { saida ->
            if (e.metodo == GUARDADO) {
                saida.write(bruto, inicio, e.comprimido.toInt())
                confere.update(bruto, inicio, e.comprimido.toInt())
            } else {
                val soltador = Inflater(true)
                soltador.setInput(bruto, inicio, e.comprimido.toInt())
                val balde = ByteArray(64 * 1024)
                try {
                    while (!soltador.finished()) {
                        val n = soltador.inflate(balde)
                        if (n == 0 && (soltador.needsInput() || soltador.needsDictionary())) break
                        saida.write(balde, 0, n)
                        confere.update(balde, 0, n)
                    }
                } catch (_: Exception) {
                    return false
                } finally {
                    soltador.end()
                }
            }
        }
        if (confere.value != e.crc) {
            destino.delete()
            return false
        }
        return true
    }

    private fun ligarOuCopiar(origem: File, destino: File): Boolean = try {
        destino.delete()
        runCatching { Files.createLink(destino.toPath(), origem.toPath()) }
            .getOrElse { origem.copyTo(destino, overwrite = true) }
        destino.exists()
    } catch (_: IOException) {
        false
    }

    private fun confere(f: File, e: EntradaDoZip): Boolean =
        f.isFile && f.length() == e.cru && crcDoArquivo(f) == e.crc

    private fun tamanhoSeExistir(f: File): Long = if (f.isFile) f.length() else 0L

    private fun crcDoArquivo(f: File): Long {
        val crc = CRC32()
        f.inputStream().buffered().use { entrada ->
            val balde = ByteArray(64 * 1024)
            while (true) {
                val n = entrada.read(balde)
                if (n < 0) break
                crc.update(balde, 0, n)
            }
        }
        return crc.value
    }

    private fun prefixoDaRaiz(arquivos: List<EntradaDoZip>): String? {
        val exe = arquivos.firstOrNull { it.nome == "Astra.exe" || it.nome.endsWith("/Astra.exe" ) }
            ?: return null
        return exe.nome.removeSuffix("Astra.exe")
    }
}
