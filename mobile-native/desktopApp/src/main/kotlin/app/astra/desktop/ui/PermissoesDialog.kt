package app.astra.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.voice.Acesso
import app.astra.desktop.voice.Checagem
import app.astra.desktop.voice.PermissoesWindows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Tela de "o Windows está deixando?" — aparece uma vez, na primeira abertura, e
// depois só quando a pessoa pedir (Configurações > Voz).
//
// Existe porque no Windows não há janelinha de permissão: com a privacidade
// fechada o microfone entrega silêncio calado, e o sintoma chega como "meu mic
// não funciona no Astra" — sem log, sem erro, sem pista. Aqui a checagem é feita
// ANTES da primeira call, e cada linha com problema leva direto pra página certa
// das Configurações do Windows, em vez de mandar procurar.
//
// A checagem abre o microfone de verdade por ~400ms, então roda fora da thread
// da interface: ela travaria a janela pelo tempo do teste.

@Composable
fun PermissoesDialog(onClose: () -> Unit) {
    var itens by remember { mutableStateOf<List<Checagem>>(emptyList()) }
    var conferindo by remember { mutableStateOf(true) }
    val escopo = androidx.compose.runtime.rememberCoroutineScope()

    fun conferir() {
        conferindo = true
        escopo.launch {
            val r = withContext(Dispatchers.IO) { PermissoesWindows.todas() }
            itens = r
            conferindo = false
        }
    }

    LaunchedEffect(Unit) { conferir() }

    DialogShell(onClose = onClose) {
        Text(
            "Antes da primeira call",
            style = TextStyle(color = Obsidian.text1, fontSize = 19.sp, fontFamily = DmSerif),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "O Windows controla quem pode usar microfone e câmera, e quando ele bloqueia não avisa " +
                "ninguém — o som simplesmente não chega. Conferi agora pra você não descobrir no meio da conversa.",
            style = TextStyle(color = Obsidian.text3, fontSize = 12.sp, lineHeight = 17.sp),
        )
        Spacer(Modifier.height(16.dp))

        if (conferindo && itens.isEmpty()) {
            Text("conferindo…", style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
        } else {
            itens.forEachIndexed { i, c ->
                LinhaPermissao(c, atraso = i * 60)
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            BotaoLeve(if (conferindo) "conferindo…" else "conferir de novo", enabled = !conferindo) { conferir() }
            Spacer(Modifier.weight(1f))
            BotaoLeve("entendi", destaque = true) { onClose() }
        }
    }
}

@Composable
private fun LinhaPermissao(c: Checagem, atraso: Int) {
    // Entram em cascata: a lista aparecendo de uma vez parece recarregamento, uma
    // atrás da outra parece conferência acontecendo.
    var visivel by remember(c.titulo, c.acesso) { mutableStateOf(false) }
    LaunchedEffect(c.titulo, c.acesso) { delay(atraso.toLong()); visivel = true }
    val fade by animateFloatAsState(if (visivel) 1f else 0f, tween(220))

    val cor = when (c.acesso) {
        Acesso.OK -> Obsidian.success
        Acesso.MUDO, Acesso.SEM_APARELHO -> Obsidian.accent
        Acesso.BLOQUEADO -> Obsidian.danger
    }
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(fade)
            .clip(RoundedCornerShape(10.dp))
            .background(Obsidian.void.copy(alpha = 0.35f))
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
            .padding(11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 4.dp).size(7.dp).clip(CircleShape).background(cor))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                c.titulo,
                style = TextStyle(color = Obsidian.text1, fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                c.explica,
                style = TextStyle(color = Obsidian.text3, fontSize = 11.5.sp, lineHeight = 16.sp),
            )
            // O botão só existe quando há o que consertar — em linha verde ele só
            // convidaria a mexer no que já está certo.
            if (c.acesso != Acesso.OK && c.ajustes != null) {
                Spacer(Modifier.height(7.dp))
                Text(
                    "abrir a configuração do Windows",
                    style = TextStyle(color = Obsidian.accent, fontSize = 11.5.sp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .border(1.dp, Obsidian.accentDim, RoundedCornerShape(7.dp))
                        .clickable { PermissoesWindows.abrirAjustes(c.ajustes) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun BotaoLeve(
    texto: String,
    destaque: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Text(
        texto,
        style = TextStyle(
            color = if (destaque) Obsidian.accent else Obsidian.text2,
            fontSize = 12.5.sp,
        ),
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(9.dp))
            .border(1.dp, if (destaque) Obsidian.accentDim else Obsidian.borderDim, RoundedCornerShape(9.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}
