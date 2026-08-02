package app.astra.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.voice.Acesso
import app.astra.desktop.voice.Checagem
import app.astra.desktop.voice.Permissao
import app.astra.desktop.voice.PermissoesWindows
import com.composables.icons.lucide.BellRing
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.MonitorUp
import com.composables.icons.lucide.Volume2
import com.composables.icons.lucide.Webcam
import com.composables.icons.lucide.Wifi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// A lista de permissões — UMA implementação, três telas: boas-vindas (1º acesso),
// Configurações > Permissões (quem já usava o app, ou quem pulou) e o aviso da
// primeira abertura. Manter três cópias faria os textos divergirem na primeira
// vez que um deles mudasse.
//
// Sobre o botão "permitir": no Windows, aplicativo de área de trabalho NÃO
// consegue pedir permissão — não existe a janelinha do navegador. Quem manda é um
// interruptor global do sistema. Então o botão faz o mais próximo possível disso:
//
//   • leva direto à página exata das Configurações do Windows (não à raiz, onde
//     a pessoa teria que caçar); e
//   • FICA CONFERINDO sozinho enquanto ela mexe lá.
//
// Esse segundo ponto é o que faz a tela valer a pena. Sem ele a pessoa liga o
// interruptor, volta pro Astra e encontra o mesmo vermelho de antes — e conclui
// que não adiantou. Com ele a linha vira verde sozinha, que é a prova de que
// funcionou.
//
// A exceção é "Avisos": ali não há interruptor pra ligar, o Windows só registra o
// app quando ele manda o primeiro aviso. Então permitir MANDA um aviso.

private const val ESPERA_MS = 2_000L
private const val TENTATIVAS = 25

@Composable
fun PainelDePermissoes(
    onTestarAviso: () -> Unit,
    modifier: Modifier = Modifier,
    /** true = mostra o estado atual até nas linhas que já estão OK (Configurações). */
    detalhado: Boolean = true,
) {
    val itens = remember { mutableStateListOf<Checagem>() }
    var conferindo by remember { mutableStateOf(true) }
    // Quem está sendo vigiado agora (permitir clicado, esperando o Windows mudar).
    val vigiando = remember { mutableStateListOf<Permissao>() }
    val escopo = rememberCoroutineScope()

    suspend fun conferirTudo() {
        conferindo = true
        val r = withContext(Dispatchers.IO) { PermissoesWindows.todas() }
        itens.clear()
        itens.addAll(r)
        conferindo = false
    }

    LaunchedEffect(Unit) { conferirTudo() }

    fun permitir(c: Checagem) {
        if (vigiando.contains(c.permissao)) return
        // Avisos não têm interruptor: o Windows só registra o app no primeiro
        // aviso. Mandar um é literalmente o ato de permitir.
        if (c.permissao == Permissao.AVISOS && c.acesso == Acesso.PENDENTE) onTestarAviso()
        else PermissoesWindows.abrirAjustes(c.ajustes ?: return)

        vigiando.add(c.permissao)
        escopo.launch {
            try {
                repeat(TENTATIVAS) {
                    delay(ESPERA_MS)
                    val novo = withContext(Dispatchers.IO) { PermissoesWindows.uma(c.permissao) }
                    val i = itens.indexOfFirst { it.permissao == novo.permissao }
                    if (i >= 0) itens[i] = novo
                    if (novo.acesso == Acesso.OK) return@launch
                }
            } finally {
                vigiando.remove(c.permissao)
            }
        }
    }

    Column(modifier) {
        if (conferindo && itens.isEmpty()) {
            Text("conferindo…", style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
        }
        itens.forEachIndexed { i, c ->
            LinhaPermissao(
                c = c,
                atraso = i * 55,
                esperando = vigiando.contains(c.permissao),
                detalhado = detalhado,
                onPermitir = { permitir(c) },
            )
            if (i < itens.lastIndex) Spacer(Modifier.height(7.dp))
        }
        if (itens.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            val src = remember { MutableInteractionSource() }
            Text(
                if (conferindo) "conferindo…" else "conferir tudo de novo",
                style = TextStyle(color = Obsidian.text3, fontSize = 11.5.sp),
                modifier = Modifier
                    .alpha(if (conferindo) 0.5f else 1f)
                    .clip(RoundedCornerShape(7.dp))
                    .clickable(interactionSource = src, indication = null, enabled = !conferindo) {
                        escopo.launch { conferirTudo() }
                    }
                    .padding(vertical = 3.dp),
            )
        }
    }
}

private fun icone(p: Permissao): ImageVector = when (p) {
    Permissao.MICROFONE -> Lucide.Mic
    Permissao.SOM -> Lucide.Volume2
    Permissao.CAMERA -> Lucide.Webcam
    Permissao.TELA -> Lucide.MonitorUp
    Permissao.REDE -> Lucide.Wifi
    Permissao.AVISOS -> Lucide.BellRing
}

// PENDENTE fica CINZA de propósito: ninguém negou nada, o Windows só ainda não
// perguntou. Pintar de amarelo faria a tela parecer cheia de problemas na
// primeira abertura — e uma tela que grita por tudo ensina a ignorar o grito.
private fun cor(a: Acesso): Color = when (a) {
    Acesso.OK -> Obsidian.success
    Acesso.BLOQUEADO -> Obsidian.danger
    Acesso.MUDO, Acesso.SEM_APARELHO -> Obsidian.warning
    Acesso.PENDENTE -> Obsidian.text3
}

@Composable
private fun LinhaPermissao(
    c: Checagem,
    atraso: Int,
    esperando: Boolean,
    detalhado: Boolean,
    onPermitir: () -> Unit,
) {
    // Entram em cascata: a lista aparecendo de uma vez parece recarregamento, uma
    // atrás da outra parece conferência acontecendo. Só na primeira montagem — a
    // chave não inclui o acesso, senão a linha piscaria a cada re-conferida.
    var visivel by remember(c.permissao) { mutableStateOf(false) }
    LaunchedEffect(c.permissao) { delay(atraso.toLong()); visivel = true }
    val fade by animateFloatAsState(if (visivel) 1f else 0f, tween(220))
    val tom = cor(c.acesso)

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
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(tom.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            LIcon(icone(c.permissao), tint = tom, size = 14.dp)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                c.permissao.titulo,
                style = TextStyle(color = Obsidian.text1, fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                c.permissao.oQueE,
                style = TextStyle(color = Obsidian.text3, fontSize = 11.5.sp, lineHeight = 16.sp),
            )
            // O estado atual só entra quando acrescenta algo: nas boas-vindas uma
            // linha verde já diz tudo com o botão "permitido", e repetir "ouvindo
            // normalmente" em seis linhas vira parede de texto.
            if (detalhado || c.acesso != Acesso.OK) {
                Spacer(Modifier.height(4.dp))
                Text(
                    c.explica,
                    style = TextStyle(
                        color = if (c.acesso == Acesso.OK || c.acesso == Acesso.PENDENTE) Obsidian.text3 else tom,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                    ),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        BotaoPermitir(c, esperando, onPermitir)
    }
}

@Composable
private fun BotaoPermitir(c: Checagem, esperando: Boolean, onClick: () -> Unit) {
    // Sem nada pra abrir e sem nada pra ligar, o botão viraria um clique que não
    // faz nada. "Transmitir a tela" é o caso: no Windows ela não pede permissão
    // nenhuma, e fingir um cadeado aqui seria teatro.
    val temAcao = c.ajustes != null || c.permissao == Permissao.AVISOS
    val pronto = c.acesso == Acesso.OK
    val rotulo = when {
        pronto && c.permissao == Permissao.TELA -> "não precisa"
        pronto -> "permitido"
        esperando -> "esperando…"
        !temAcao -> "—"
        else -> "permitir"
    }
    val ativo = !pronto && !esperando && temAcao

    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    val forma = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .clip(forma)
            .background(if (ativo && hov) Obsidian.accent.copy(alpha = 0.12f) else Color.Transparent)
            .border(1.dp, if (ativo) Obsidian.accentDim else Obsidian.borderDim, forma)
            .hoverable(src, enabled = ativo)
            .clickable(interactionSource = src, indication = null, enabled = ativo, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            rotulo,
            style = TextStyle(
                color = when {
                    pronto -> Obsidian.success
                    ativo -> Obsidian.accent
                    else -> Obsidian.text3
                },
                fontSize = 11.5.sp,
            ),
        )
    }
}
