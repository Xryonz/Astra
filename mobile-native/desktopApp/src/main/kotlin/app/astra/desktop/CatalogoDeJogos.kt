package app.astra.desktop

import java.util.Locale

object CatalogoDeJogos {
    private const val RECURSO = "/jogos.tsv"

    private class Entrada(val sufixo: String, val jogo: String) {
        val temCaminho = sufixo.indexOf('/') >= 0
    }

    private val porNomeDeArquivo: Map<String, List<Entrada>> by lazy { carregar() }

    val tamanho: Int get() = porNomeDeArquivo.size

    fun jogoDe(caminho: String): String? {
        val trilha = caminho.lowercase(Locale.ROOT).replace('\\', '/')
        val arquivo = trilha.substringAfterLast('/')
        if (arquivo.isEmpty()) return null

        val candidatos = porNomeDeArquivo[arquivo] ?: return null

        val peloCaminho = candidatos
            .filter { it.temCaminho && trilha.endsWith("/" + it.sufixo) }
            .maxByOrNull { it.sufixo.length }
        if (peloCaminho != null) return peloCaminho.jogo

        if (candidatos.none { !it.temCaminho }) return null

        val unico = candidatos[0].jogo
        return if (candidatos.all { it.jogo == unico }) unico else null
    }

    private fun carregar(): Map<String, List<Entrada>> {
        val fluxo = CatalogoDeJogos::class.java.getResourceAsStream(RECURSO) ?: return emptyMap()
        val mapa = HashMap<String, MutableList<Entrada>>(16384)

        fluxo.bufferedReader().useLines { linhas ->
            for (linha in linhas) {
                if (linha.isEmpty() || linha[0] == '#') continue
                val corte = linha.indexOf('\t')
                if (corte <= 0 || corte == linha.length - 1) continue

                val sufixo = linha.substring(0, corte)
                val jogo = linha.substring(corte + 1)
                val chave = sufixo.substringAfterLast('/')
                if (chave.isEmpty()) continue

                mapa.getOrPut(chave) { ArrayList(1) }.add(Entrada(sufixo, jogo))
            }
        }
        return mapa
    }
}
