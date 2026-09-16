package app.astra.desktop.update

import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.HexFormat

private val CHAVES_DE_ASSINATURA = listOf(
    "MCowBQYDK2VwAyEAK+0eWgfZGpa9hCWPlPTKupANPhQ3vqIbhzvgGsf6xFY=",
)

private const val CABECALHO = "astra-manifesto 1"
private const val PREFIXO_DA_VERSAO = "versao "
private const val SEPARADOR = "  "
private const val TAMANHO_DO_HASH = 64

internal class AssinaturaInvalida : SecurityException("a atualização não passou na verificação de assinatura")

internal class Manifesto private constructor(private val hashes: Map<String, String>) {

    fun conferir(pacote: File) {
        val raiz = pacote.toPath()
        val encontrados = pacote.walkTopDown()
            .filter { it.isFile }
            .associateBy { raiz.relativize(it.toPath()).joinToString("/") }
        if (encontrados.keys != hashes.keys) throw AssinaturaInvalida()
        for ((relativo, arquivo) in encontrados) {
            if (sha256(arquivo) != hashes[relativo]) throw AssinaturaInvalida()
        }
    }

    companion object {

        fun ler(texto: ByteArray, assinatura: ByteArray, versao: String): Manifesto {
            if (!assinadoPorChaveConfiavel(texto, assinatura)) throw AssinaturaInvalida()
            val linhas = String(texto, Charsets.UTF_8).lines().filter { it.isNotEmpty() }
            if (linhas.getOrNull(0) != CABECALHO) throw AssinaturaInvalida()
            if (linhas.getOrNull(1) != PREFIXO_DA_VERSAO + versao) throw AssinaturaInvalida()
            val hashes = HashMap<String, String>()
            for (linha in linhas.drop(2)) {
                val hash = linha.substringBefore(SEPARADOR)
                val relativo = linha.substringAfter(SEPARADOR, "")
                val caminhoValido = relativo.isNotEmpty() && relativo.split('/').none { it.isEmpty() || it == ".." }
                if (hash.length != TAMANHO_DO_HASH || !caminhoValido) throw AssinaturaInvalida()
                if (hashes.put(relativo, hash) != null) throw AssinaturaInvalida()
            }
            return Manifesto(hashes)
        }

        private fun assinadoPorChaveConfiavel(texto: ByteArray, assinatura: ByteArray): Boolean {
            val bruta = runCatching {
                Base64.getDecoder().decode(String(assinatura, Charsets.US_ASCII).trim())
            }.getOrNull() ?: return false
            val fabrica = KeyFactory.getInstance("Ed25519")
            return CHAVES_DE_ASSINATURA.any { publica ->
                runCatching {
                    Signature.getInstance("Ed25519").run {
                        initVerify(fabrica.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publica))))
                        update(texto)
                        verify(bruta)
                    }
                }.getOrDefault(false)
            }
        }

        private fun sha256(arquivo: File): String {
            val resumo = MessageDigest.getInstance("SHA-256")
            arquivo.inputStream().buffered().use { entrada ->
                val balde = ByteArray(64 * 1024)
                while (true) {
                    val lidos = entrada.read(balde)
                    if (lidos < 0) break
                    resumo.update(balde, 0, lidos)
                }
            }
            return HexFormat.of().formatHex(resumo.digest())
        }
    }
}
