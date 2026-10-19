package app.astra.desktop.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.astra.desktop.ui.theme.Text

@Composable
fun PermissoesDialog(onTestarAviso: () -> Unit, onClose: () -> Unit) {
    Dialogo(titulo = "antes da primeira call", aoFechar = onClose, largura = LarguraDoDialogo.MEDIA) { fechar ->
        val focoInicial = rememberFocoAoAbrir()
        Spacer(Modifier.height(8.dp))
        Text(
            "o Windows controla quem pode usar o microfone e não avisa quando bloqueia: " +
                "o som simplesmente não chega. O Astra conferiu agora para você não descobrir o bloqueio no meio da conversa.",
            style = TextoDoDialogo,
        )
        Spacer(Modifier.height(16.dp))

        PainelDePermissoes(onTestarAviso = onTestarAviso)

        RodapeDoDialogo {
            BotaoDeDialogo("entendi", TipoDeBotao.PRINCIPAL, onClick = fechar, foco = focoInicial)
        }
    }
}
