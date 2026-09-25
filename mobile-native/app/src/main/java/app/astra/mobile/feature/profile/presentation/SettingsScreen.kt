package app.astra.mobile.feature.profile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.ui.AstraCopy
import app.astra.mobile.ui.theme.astraColors
import com.composables.icons.lucide.Accessibility
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.MonitorSmartphone
import com.composables.icons.lucide.Paintbrush
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Shield
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.User
import com.composables.icons.lucide.X
import java.text.Normalizer

private class LinhaDeAjuste(
    val titulo: String,
    val icone: ImageVector,
    val palavras: String,
    val aoTocar: () -> Unit,
)

private class GrupoDeAjustes(val titulo: String, val linhas: List<LinhaDeAjuste>)

private fun semAcento(texto: String): String =
    Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenNameColors: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenVoz: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenData: () -> Unit,
    onOpenWishing: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    var busca by rememberSaveable { mutableStateOf("") }
    val grupos = remember {
        listOf(
            GrupoDeAjustes(
                "Sua conta",
                listOf(
                    LinhaDeAjuste("Perfil", Lucide.Pencil, "foto banner recado bio pronomes fonte tema editar", onOpenProfile),
                    LinhaDeAjuste("Conta", Lucide.User, "nome usuário email senha", onOpenAccount),
                    LinhaDeAjuste("Cores do nome", Lucide.Palette, "cor constelação cargo", onOpenNameColors),
                    LinhaDeAjuste("Sessões", Lucide.MonitorSmartphone, "dispositivos aparelhos conectados sair", onOpenSessions),
                    LinhaDeAjuste("Dados e privacidade", Lucide.Shield, "exportar apagar excluir conta", onOpenData),
                ),
            ),
            GrupoDeAjustes(
                "Aplicativo",
                listOf(
                    LinhaDeAjuste("Aparência", Lucide.Paintbrush, "tema cor destaque fundo fonte tamanho densidade", onOpenAppearance),
                    LinhaDeAjuste("Voz", Lucide.Mic, "call chamada transmissão tela qualidade microfone", onOpenVoz),
                    LinhaDeAjuste("Notificações", Lucide.Bell, "avisos menções sussurros horário silencioso", onOpenNotifications),
                    LinhaDeAjuste("Acessibilidade", Lucide.Accessibility, "movimento vibração animação aurora estrelas", onOpenAccessibility),
                ),
            ),
            GrupoDeAjustes(
                "Comunidade",
                listOf(LinhaDeAjuste("Estrela Cadente", Lucide.Sparkles, "ideias sugestões pedidos", onOpenWishing)),
            ),
            GrupoDeAjustes(
                "Astra",
                listOf(LinhaDeAjuste("Sobre", Lucide.Info, "versão atualização atualizar", onOpenAbout)),
            ),
        )
    }
    val termo = semAcento(busca.trim())
    val visiveis = if (termo.isEmpty()) {
        grupos
    } else {
        grupos.mapNotNull { g ->
            val achadas = g.linhas.filter { semAcento(it.titulo + " " + it.palavras).contains(termo) }
            if (achadas.isEmpty()) null else GrupoDeAjustes(g.titulo, achadas)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack)
                    .semantics { contentDescription = "Fechar" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Lucide.X, contentDescription = null, tint = astraColors.text1, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("Configurações", style = MaterialTheme.typography.titleLarge, color = astraColors.text1)
        }

        CampoDeBusca(busca, { busca = it }, Modifier.padding(horizontal = 16.dp, vertical = 10.dp))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            visiveis.forEach { grupo ->
                Text(
                    grupo.titulo,
                    style = MaterialTheme.typography.labelLarge,
                    color = astraColors.text3,
                    modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
                )
                CartaoDoGrupo(grupo.linhas)
            }
            if (visiveis.isEmpty()) {
                Text(
                    "Nada encontrado para “${busca.trim()}”.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = astraColors.text3,
                    modifier = Modifier.padding(start = 4.dp, top = 24.dp),
                )
            }
            if (termo.isEmpty()) {
                Spacer(Modifier.height(24.dp))
                val forma = RoundedCornerShape(12.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(forma)
                        .background(astraColors.raised)
                        .border(1.dp, astraColors.danger.copy(alpha = 0.4f), forma)
                        .clickable(onClick = viewModel::logout)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Lucide.LogOut, contentDescription = null, tint = astraColors.danger, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(AstraCopy.Action.logout, style = MaterialTheme.typography.titleMedium, color = astraColors.danger)
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun CampoDeBusca(valor: String, aoMudar: (String) -> Unit, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(forma)
            .background(astraColors.raised)
            .border(1.dp, astraColors.border, forma)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.Search, contentDescription = null, tint = astraColors.text3, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (valor.isEmpty()) {
                Text("Buscar", style = MaterialTheme.typography.bodyLarge, color = astraColors.text3)
            }
            BasicTextField(
                value = valor,
                onValueChange = aoMudar,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = astraColors.text1),
                cursorBrush = SolidColor(astraColors.accent),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Buscar nas configurações" },
            )
        }
    }
}

@Composable
private fun CartaoDoGrupo(linhas: List<LinhaDeAjuste>) {
    val forma = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(astraColors.raised)
            .border(1.dp, astraColors.border, forma)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        linhas.forEach { linha ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = linha.aoTocar)
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(linha.icone, contentDescription = null, tint = astraColors.text1, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(16.dp))
                Text(
                    linha.titulo,
                    style = MaterialTheme.typography.bodyLarge,
                    color = astraColors.text1,
                    modifier = Modifier.weight(1f),
                )
                Icon(Lucide.ChevronRight, contentDescription = null, tint = astraColors.text3, modifier = Modifier.size(18.dp))
            }
        }
    }
}
