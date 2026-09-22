package app.astra.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.auth.SessionStore
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.ui.theme.Tipo

object NovidadesDaVersao {
    private const val CHAVE = "novidadesVistas"

    fun ler(versao: String): List<String>? {
        val texto = runCatching {
            NovidadesDaVersao::class.java.getResourceAsStream("/novidades.txt")
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        }.getOrNull() ?: return null
        val linhas = texto.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (linhas.firstOrNull() != "versao $versao") return null
        return linhas.drop(1).takeIf { it.isNotEmpty() }
    }

    fun paraMostrar(store: SessionStore, versao: String, veioDeAtualizacao: Boolean): List<String>? {
        val vista = store.uiPref(CHAVE)
        if (vista == versao) return null
        if (vista == null && !veioDeAtualizacao) {
            marcarVistas(store, versao)
            return null
        }
        return ler(versao) ?: run {
            marcarVistas(store, versao)
            null
        }
    }

    fun marcarVistas(store: SessionStore, versao: String) = store.setUiPref(CHAVE, versao)
}

@Composable
fun CartaoDeNovidades(versao: String, itens: List<String>, aoFechar: () -> Unit) {
    DialogShell(aoFechar, largura = 420.dp) {
        Text(
            "o que mudou na $versao",
            style = TextStyle(color = Obsidian.text1, fontSize = 15.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itens.forEach { item ->
                Row {
                    Text("·", style = TextStyle(color = Obsidian.accent, fontSize = 13.sp))
                    Spacer(Modifier.width(8.dp))
                    Text(item, style = Tipo.corpo)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        DialogButton("entendi", accent = true) { aoFechar() }
    }
}
