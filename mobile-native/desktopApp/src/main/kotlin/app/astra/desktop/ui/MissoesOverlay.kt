package app.astra.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.xp.MissoesStore
import app.astra.mobile.core.network.dto.ItemMissaoDto
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import kotlinx.coroutines.delay
import org.koin.core.context.GlobalContext

// A TELA DE MISSOES.
//
// Tres blocos com ritmos diferentes, na ordem em que importam: o que vira hoje, o
// que vira na semana, e o que nunca vira. Quem abre isto quer saber "o que da pra
// fazer agora" — e por isso as diarias vem primeiro, mesmo pagando menos.
//
// Sem abas por enquanto. O passe e a colecao vao morar aqui quando existirem;
// desenhar abas vazias agora seria prometer duas telas que ainda nao ha.

private const val MIN_MS  = 60_000L
private const val HORA_MS = 3_600_000L
private const val DIA_MS  = 86_400_000L

// "renova em 6h" e melhor que uma hora exata: ninguem planeja o dia pelo minuto em
// que a missao vira, mas todo mundo entende "ainda da tempo".
private fun faltando(alvoMs: Long, agoraMs: Long): String {
    val d = alvoMs - agoraMs
    return when {
        d <= 0        -> "renovando…"
        d >= DIA_MS   -> "renova em ${d / DIA_MS}d"
        d >= HORA_MS  -> "renova em ${d / HORA_MS}h"
        d >= MIN_MS   -> "renova em ${d / MIN_MS}min"
        else          -> "renova já já"
    }
}

@Composable
fun MissoesOverlay(onClose: () -> Unit) {
    val store = remember { GlobalContext.get().get<MissoesStore>() }
    val painel by store.painel.collectAsState()

    // Busca toda vez que abre. O socket mantem o "concluida" em dia sozinho, mas o
    // progresso PARCIAL (2 de 5) so a busca sabe — e a hora de saber e agora, que e
    // quando alguem esta olhando.
    LaunchedEffect(Unit) { store.recarregar() }

    // Um tique por minuto so pra contagem regressiva. Mais rapido que isso seria
    // recompor a tela inteira pra mudar um texto que nem muda.
    var agora by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(MIN_MS); agora = System.currentTimeMillis() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Obsidian.void.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .padding(top = 72.dp, bottom = 40.dp)
                .width(520.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Obsidian.overlay)
                .border(1.dp, Obsidian.borderMid, RoundedCornerShape(14.dp))
                // Engole o clique: sem isto, clicar dentro do cartao fecharia a tela
                // pelo backdrop de tras.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "missões",
                    style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif),
                )
                Spacer(Modifier.weight(1f))
                BotaoFechar(onClose)
            }
            HairRule()

            val p = painel
            if (p == null) {
                Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    Text("carregando…", style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
                }
                return@Column
            }

            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Secao("hoje", faltando(p.diarias.renovaEm, agora))
                p.diarias.itens.forEach { LinhaDeMissao(it) }
                // O bonus fica visualmente preso as tres de cima (sem espaco antes):
                // ele nao e uma quarta missao, e a consequencia daquelas tres.
                LinhaDeMissao(p.diarias.bonus, bonus = true)

                Spacer(Modifier.height(22.dp))
                Secao("esta semana", faltando(p.semanais.renovaEm, agora))
                p.semanais.itens.forEach { LinhaDeMissao(it) }

                Spacer(Modifier.height(22.dp))
                Secao("conquistas", "não expiram")
                p.conquistas.itens.forEach { LinhaDeMissao(it) }
            }
        }
    }
}

@Composable
private fun Secao(titulo: String, direita: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            titulo,
            style = TextStyle(
                color = Obsidian.text2, fontSize = 11.sp,
                fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp,
            ),
        )
        Spacer(Modifier.weight(1f))
        Text(direita, style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, fontFamily = DmMono))
    }
}

// Uma missao. A barra de progresso e uma HAIRLINE debaixo do titulo, nao um bloco:
// oito barras grossas empilhadas viram um painel de jogo mobile, e a estetica daqui
// e editorial. A linha fina diz a mesma coisa e some quando nao e olhada.
@Composable
private fun LinhaDeMissao(m: ItemMissaoDto, bonus: Boolean = false) {
    val hover = remember { MutableInteractionSource() }
    val sobHover by hover.collectIsHoveredAsState()
    val fracao by animateFloatAsState(
        if (m.alvo <= 0) 0f else (m.progresso.toFloat() / m.alvo).coerceIn(0f, 1f),
        tween(420),
        label = "progressoMissao",
    )
    val corTitulo = when {
        m.concluida -> Obsidian.text3      // feito sai do caminho, nao vira troféu
        sobHover    -> Obsidian.text1
        else        -> Obsidian.text2
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .hoverable(hover)
            .background(if (sobHover) Obsidian.hover.copy(alpha = 0.5f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Marcador(m.concluida)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                m.titulo,
                style = TextStyle(color = corTitulo, fontSize = 12.5.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            // Trilho + preenchimento na fase de DESENHO. Um Box com width animada
            // relayoutaria a linha 60x por segundo durante a animacao.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .drawBehind {
                        drawRoundRectSimples(Obsidian.borderDim.copy(alpha = 0.55f), size.width, size.height)
                        if (fracao > 0f) {
                            drawRoundRectSimples(
                                if (m.concluida) Obsidian.accent.copy(alpha = 0.45f) else Obsidian.accent,
                                size.width * fracao, size.height,
                            )
                        }
                    },
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            if (m.concluida) "" else "${m.progresso}/${m.alvo}",
            style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, fontFamily = DmMono),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "+${m.xp}",
            style = TextStyle(
                color = if (m.concluida) Obsidian.text3 else Obsidian.accent,
                fontSize = if (bonus) 12.sp else 11.sp,
                fontFamily = DmMono,
                fontWeight = if (bonus) FontWeight.Medium else FontWeight.Normal,
            ),
            modifier = Modifier.width(44.dp),
        )
    }
}

// Circulo vazio -> circulo cheio com tique. O estado "feito" precisa ser legivel de
// relance, sem ler o texto: e assim que alguem varre a lista procurando o que falta.
@Composable
private fun Marcador(concluida: Boolean) {
    Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
        if (concluida) {
            Box(
                Modifier.size(16.dp).clip(CircleShape).background(Obsidian.accent),
                contentAlignment = Alignment.Center,
            ) {
                LIcon(Lucide.Check, tint = Obsidian.textInv, size = 10.dp)
            }
        } else {
            Box(
                Modifier
                    .size(15.dp)
                    .clip(CircleShape)
                    .border(1.dp, Obsidian.borderMid, CircleShape),
            )
        }
    }
}

@Composable
private fun BotaoFechar(onClose: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier
            .size(30.dp)
            .clickScale(interaction)
            .clip(RoundedCornerShape(7.dp))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        LIcon(Lucide.X, tint = if (hovered) Obsidian.text1 else Obsidian.text3, size = 14.dp)
    }
}

// Retangulo arredondado alinhado a esquerda, na fase de desenho. Existe pra a barra
// nao precisar de um Canvas nem de um Box com width variavel.
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoundRectSimples(
    cor: Color, largura: Float, altura: Float,
) {
    if (largura <= 0f) return
    drawRoundRect(
        color = cor,
        topLeft = Offset.Zero,
        size = Size(largura, altura),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(altura / 2f),
    )
}
