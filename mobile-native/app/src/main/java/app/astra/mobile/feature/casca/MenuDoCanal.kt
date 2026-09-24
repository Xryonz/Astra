package app.astra.mobile.feature.casca

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.mobile.feature.server.domain.model.Channel
import app.astra.mobile.ui.components.AlcaDaFolha
import app.astra.mobile.ui.components.AstraSwitch
import app.astra.mobile.ui.components.FolhaQueSobe
import app.astra.mobile.ui.components.rememberEstadoDaFolha
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.astraColors
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.BellOff
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Volume2

@Composable
fun MenuDoCanal(
    canal: Channel,
    silenciado: Boolean,
    naoLido: Boolean,
    podeMexerNaBot: Boolean,
    aoFechar: () -> Unit,
    aoSilenciar: (Boolean) -> Unit,
    aoMarcarComoLido: () -> Unit,
    aoMudarBot: (Boolean) -> Unit,
    aoMudarRespostas: (Boolean) -> Unit,
) {
    val folha = rememberEstadoDaFolha(aoFechar)

    FolhaQueSobe(folha, rotuloDoFundo = "Fechar o menu da órbita") {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
        ) {
            AlcaDaFolha(Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canal.isVoice) {
                    Icon(
                        Lucide.Volume2,
                        contentDescription = null,
                        tint = astraColors.text2,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Text("#", style = MaterialTheme.typography.headlineSmall, color = astraColors.text3)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    canal.name,
                    fontFamily = DmSerif,
                    style = MaterialTheme.typography.headlineSmall,
                    color = astraColors.text1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(16.dp))
            Bloco {
                AcaoDoMenu(
                    icone = if (silenciado) Lucide.Bell else Lucide.BellOff,
                    titulo = if (silenciado) "Reativar avisos" else "Silenciar órbita",
                    apoio = if (silenciado) "volta a avisar de mensagem nova" else "some dos avisos, continua aqui",
                    aoTocar = { folha.fechar { aoSilenciar(!silenciado) } },
                )
                if (!canal.isVoice) {
                    AcaoDoMenu(
                        icone = Lucide.Check,
                        titulo = "Marcar como lido",
                        apoio = if (naoLido) "tira o ponto da lista" else "já está em dia",
                        ativo = naoLido,
                        aoTocar = { folha.fechar { aoMarcarComoLido() } },
                    )
                }
            }

            if (podeMexerNaBot && !canal.isVoice) {
                Spacer(Modifier.height(14.dp))
                Bloco {
                    ChaveDoMenu(
                        icone = Lucide.Sparkles,
                        titulo = "A bot atende aqui",
                        apoio = "responde aos comandos nesta órbita",
                        ligada = canal.botAtende != false,
                        aoMudar = aoMudarBot,
                    )
                    ChaveDoMenu(
                        icone = Lucide.Check,
                        titulo = "Guardar as respostas",
                        apoio = "sem isso, a resposta some depois de um tempo",
                        ligada = canal.guardaAsRespostas,
                        aoMudar = aoMudarRespostas,
                    )
                }
            }
        }
    }
}

@Composable
private fun Bloco(conteudo: @Composable () -> Unit) {
    val forma = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(astraColors.raised)
            .border(1.dp, astraColors.border, forma)
            .padding(vertical = 4.dp),
        content = { conteudo() },
    )
}

@Composable
private fun AcaoDoMenu(
    icone: ImageVector,
    titulo: String,
    apoio: String,
    aoTocar: () -> Unit,
    ativo: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = ativo, onClick = aoTocar)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icone,
            contentDescription = null,
            tint = if (ativo) astraColors.text2 else astraColors.text3,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titulo, fontSize = 15.sp, color = if (ativo) astraColors.text1 else astraColors.text3)
            Text(apoio, fontSize = 12.sp, color = astraColors.text3)
        }
    }
}

@Composable
private fun ChaveDoMenu(
    icone: ImageVector,
    titulo: String,
    apoio: String,
    ligada: Boolean,
    aoMudar: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { aoMudar(!ligada) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icone, contentDescription = null, tint = astraColors.text2, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titulo, fontSize = 15.sp, color = astraColors.text1)
            Text(apoio, fontSize = 12.sp, color = astraColors.text3)
        }
        Spacer(Modifier.width(10.dp))
        AstraSwitch(checked = ligada, onCheckedChange = aoMudar)
    }
}
