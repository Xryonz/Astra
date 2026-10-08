package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.Arranque
import app.astra.desktop.FocoDoSistema
import app.astra.desktop.Vigia
import app.astra.desktop.voice.SidecarDeVoz
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.ui.theme.Tipo
import org.jetbrains.skiko.GraphicsApi
import kotlin.system.exitProcess

object DesenhoDaJanela {
    var api by mutableStateOf<GraphicsApi?>(null)

    val peloProcessador: Boolean
        get() = api?.name?.startsWith("SOFTWARE") == true || Arranque.modoSeguro
}

@Composable
fun AvisoDeDesenhoPeloProcessador(aoDispensar: () -> Unit) {
    val modoSeguro = Arranque.modoSeguro
    var falha by remember { mutableStateOf<String?>(null) }
    val explicacao = when {
        Arranque.desistiuDaPlaca ->
            "A placa de vídeo falhou em duas aberturas seguidas, então o Astra está desenhando pelo " +
                "processador, e as animações ficam lentas. Atualize o driver da placa de vídeo e tente de novo."
        modoSeguro ->
            "A última abertura não conseguiu desenhar pela placa de vídeo, então esta usa o processador, e " +
                "as animações ficam lentas. Na próxima abertura o Astra tenta a placa de novo."
        else ->
            "O Windows não entregou a placa de vídeo ao Astra, então ele está desenhando pelo processador, e " +
                "as animações ficam lentas. Atualizar o driver da placa de vídeo costuma resolver."
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Obsidian.overlay.copy(alpha = 0.96f))
            .border(1.dp, Obsidian.borderMid, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "✦ desenhando pelo processador",
                style = TextStyle(color = Obsidian.accent, fontSize = 14.sp, fontFamily = DmSerif),
                modifier = Modifier.weight(1f),
            )
            val src = remember { MutableInteractionSource() }
            Text(
                "entendi",
                style = Tipo.descricao,
                modifier = Modifier
                    .clickScale(src)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(interactionSource = src, indication = null, onClick = aoDispensar)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(explicacao, style = TextStyle(color = Obsidian.text2, fontSize = 12.sp, lineHeight = 17.sp))
        if (modoSeguro) {
            Spacer(Modifier.height(10.dp))
            val src = remember { MutableInteractionSource() }
            Text(
                falha ?: "reabrir usando a placa de vídeo",
                style = TextStyle(color = if (falha == null) Obsidian.accent else Obsidian.text3, fontSize = 12.sp),
                modifier = Modifier
                    .clickScale(src)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, if (falha == null) Obsidian.accentDim else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable(interactionSource = src, indication = null, enabled = falha == null) {
                        falha = reabrirUsandoAPlaca()
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

private fun reabrirUsandoAPlaca(): String {
    if (!Arranque.sairDoModoSeguro()) {
        return "não foi possível gravar a escolha — tente em Configurações, na aba Diagnóstico"
    }
    FocoDoSistema.cederAFrenteAQualquerUm()
    if (Vigia.reabrir()) {
        SidecarDeVoz.encerrarTodos(prazoMs = 3_000L)
        exitProcess(0)
    }
    return "não foi possível reabrir — feche e abra o Astra para usar a placa de vídeo"
}
