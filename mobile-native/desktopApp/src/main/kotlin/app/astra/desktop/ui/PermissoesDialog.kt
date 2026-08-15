package app.astra.desktop.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text

// Aviso de "o Windows está deixando?" na primeira abertura de quem JÁ TINHA conta
// — quem cria conta agora vê a mesma lista dentro das boas-vindas, e quem quiser
// rever encontra em Configurações > Permissões.
//
// Existe porque no Windows não há janelinha de permissão: com a privacidade
// fechada o microfone entrega silêncio calado, e o sintoma chega como "meu mic
// não funciona no Astra" — sem log, sem erro, sem pista. Melhor descobrir aqui
// que no meio da primeira conversa.
//
// A lista em si mora em PainelDePermissoes (usada também nas boas-vindas e nas
// configurações). Aqui só a moldura e o texto de contexto.

@Composable
fun PermissoesDialog(onTestarAviso: () -> Unit, onClose: () -> Unit) {
    DialogShell(onClose = onClose) {
        Text(
            "Antes da primeira call",
            style = TextStyle(color = Obsidian.text1, fontSize = 19.sp, fontFamily = DmSerif),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "O Windows controla quem pode usar microfone e câmera, e quando ele bloqueia não avisa " +
                "ninguém — o som simplesmente não chega. Conferi agora para você não descobrir no meio da conversa.",
            style = TextStyle(color = Obsidian.text3, fontSize = 12.sp, lineHeight = 17.sp),
        )
        Spacer(Modifier.height(16.dp))

        PainelDePermissoes(onTestarAviso = onTestarAviso)

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            Text(
                "entendi",
                style = TextStyle(color = Obsidian.accent, fontSize = 12.5.sp),
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .border(1.dp, Obsidian.accentDim, RoundedCornerShape(9.dp))
                    .clickable { onClose() }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}
