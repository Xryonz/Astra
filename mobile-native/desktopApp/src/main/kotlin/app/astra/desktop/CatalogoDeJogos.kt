package app.astra.desktop

import java.util.Locale

object CatalogoDeJogos {
    private const val RECURSO = "/jogos.tsv"
    private const val RECURSO_DE_ARTE = "/jogos-arte.tsv"
    private const val ROTA_DA_ARTE = "/api/activity-art/"

    private class Entrada(val sufixo: String, val jogo: String) {
        val temCaminho = sufixo.indexOf('/') >= 0
    }

    private val porNomeDeArquivo: Map<String, List<Entrada>> by lazy { carregar() }

    private val artePorJogo: Map<String, String> by lazy { carregarArtes() }

    val tamanho: Int get() = porNomeDeArquivo.size

    fun arteDe(jogo: String?): String? {
        val nome = jogo?.trim().orEmpty()
        if (nome.isEmpty()) return null
        val identidade = artePorJogo[nome] ?: return null
        return ROTA_DA_ARTE + identidade + ".webp"
    }

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

    private fun carregarArtes(): Map<String, String> {
        val fluxo = CatalogoDeJogos::class.java.getResourceAsStream(RECURSO_DE_ARTE) ?: return emptyMap()
        val mapa = HashMap<String, String>(16384)

        fluxo.bufferedReader().useLines { linhas ->
            for (linha in linhas) {
                if (linha.isEmpty() || linha[0] == '#') continue
                val primeiro = linha.indexOf('\t')
                if (primeiro <= 0) continue
                val segundo = linha.indexOf('\t', primeiro + 1)
                if (segundo <= primeiro + 1 || segundo == linha.length - 1) continue

                val jogo = linha.substring(0, primeiro)
                val aplicativo = linha.substring(primeiro + 1, segundo)
                val impressao = linha.substring(segundo + 1)
                mapa[jogo] = "$aplicativo/$impressao"
            }
        }
        return mapa
    }
}
