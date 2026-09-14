package app.astra.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.xp.XpStore
import app.astra.mobile.core.network.dto.ProfileUserDto
import app.astra.mobile.core.network.dto.ProgressoDto
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.EaseOutSoft
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.xp.MissoesStore
import app.astra.desktop.xp.quantasProntas
import app.astra.mobile.core.network.dto.ItemMissaoDto
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Medal
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.X
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import app.astra.desktop.ui.theme.Tipo

private const val MIN_MS  = 60_000L
private const val HORA_MS = 3_600_000L
private const val DIA_MS  = 86_400_000L

private const val ATRASO_MS = 40L
private const val TETO_CASCATA = 11

private val LARGURA_DA_RECOMPENSA = 52.dp

private val RESPIRO_ENTRE_GRUPOS = 12.dp

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
fun MissoesOverlay(me: ProfileUserDto?, onClose: () -> Unit) {
    val store = remember { GlobalContext.get().get<MissoesStore>() }
    val painel by store.painel.collectAsState()
    val xpStore = remember { GlobalContext.get().get<XpStore>() }
    val progresso by xpStore.progresso.collectAsState()
    val visualXp = rememberVisualDeXp(xpStore)
    LaunchedEffect(Unit) { xpStore.recarregar() }

    LaunchedEffect(Unit) { store.recarregar() }

    var agora by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(MIN_MS); agora = System.currentTimeMillis() }
    }

    val escopo = rememberCoroutineScope()
    var resgatandoTudo by remember { mutableStateOf(false) }
    val resgatarUma: (String) -> Unit = { id -> escopo.launch { store.resgatar(id) } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Obsidian.void.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .semCursorDeClique(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .padding(top = 72.dp, bottom = 40.dp)
                .width(520.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Obsidian.overlay)
                .border(1.dp, Obsidian.borderMid, RoundedCornerShape(14.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "sua jornada",
                    style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif),
                )
                Spacer(Modifier.weight(1f))
                BotaoFechar(onClose)
            }
            EstadoDaConta(me, progresso, visualXp)

            val prontas = painel?.quantasProntas() ?: 0
            if (prontas > 0) {
                Spacer(Modifier.height(12.dp))
                FaixaDeResgate(prontas, resgatandoTudo) {
                    resgatandoTudo = true
                    escopo.launch { store.resgatarTudo(); resgatandoTudo = false }
                }
            }

            val p = painel
            if (p == null) {
                Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    Text("carregando…", style = Tipo.descricao)
                }
                return@Column
            }

            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                val depoisDoBonus = p.diarias.itens.size + 1
                val depoisDaSemana = depoisDoBonus + p.semanais.itens.size

                Grupo("hoje", faltando(p.diarias.renovaEm, agora)) {
                    p.diarias.itens.forEachIndexed { i, m -> LinhaDeMissao(m, i, resgatarUma) }
                    LinhaDeMissao(p.diarias.bonus, depoisDoBonus - 1, resgatarUma, bonus = true)
                }
                Spacer(Modifier.height(RESPIRO_ENTRE_GRUPOS))
                Grupo("esta semana", faltando(p.semanais.renovaEm, agora)) {
                    p.semanais.itens.forEachIndexed { i, m -> LinhaDeMissao(m, depoisDoBonus + i, resgatarUma) }
                }
                Spacer(Modifier.height(RESPIRO_ENTRE_GRUPOS))
                Grupo("conquistas", "não expiram") {
                    p.conquistas.itens.forEachIndexed { i, m ->
                        LinhaDeMissao(m, depoisDaSemana + i, resgatarUma, conquista = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun EstadoDaConta(me: ProfileUserDto?, p: ProgressoDto, visual: VisualDeXp) {
    val nome = me?.displayName ?: me?.username ?: "você"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.anelDeXp(
                fracao = visual.fracao,
                aceso = visual.aceso,
                varredura = visual.varredura,
                cor = Obsidian.accent,
                trilho = Obsidian.borderDim,
                espessura = 2.5.dp,
            ),
        ) {
            DesktopAvatar(me?.avatarUrl, nome, 44)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                nome,
                style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("nível ${p.nivel}", style = Tipo.apoio, maxLines = 1)
                Spacer(Modifier.width(12.dp))
                Moeda(null, "${p.xp}", Obsidian.text2, "de brilho")
                Spacer(Modifier.width(12.dp))
                Moeda(Lucide.Star, "${p.estrelas}", Obsidian.text2, "estrelas")
            }
            Spacer(Modifier.height(8.dp))
            BarraDeXpEmPixels(fracaoDe(p), Obsidian.accent, Modifier.fillMaxWidth().height(11.dp))
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Moeda(
                    null,
                    "${p.noNivel} / ${p.paraOProximo}",
                    Obsidian.text3,
                    "de brilho",
                    10.sp,
                )
                Spacer(Modifier.width(6.dp))
                Text("para o nível ${p.nivel + 1}", style = Tipo.nota, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Grupo(titulo: String, direita: String, conteudo: @Composable () -> Unit) {
    val forma = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, forma)
            .padding(horizontal = 10.dp, vertical = 11.dp),
    ) {
        Secao(titulo, direita)
        conteudo()
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

@Composable
private fun LinhaDeMissao(
    m: ItemMissaoDto,
    ordem: Int,
    onResgatar: (String) -> Unit,
    bonus: Boolean = false,
    conquista: Boolean = false,
) {
    val hover = remember { MutableInteractionSource() }
    val sobHover by hover.collectIsHoveredAsState()
    val reduzir = LocalReduceMotion.current
    val alvoFracao = if (m.alvo <= 0) 0f else (m.progresso.toFloat() / m.alvo).coerceIn(0f, 1f)

    val entrada = remember { Animatable(if (reduzir) 1f else 0f) }
    val fracao = remember { Animatable(if (reduzir) alvoFracao else 0f) }
    var jaEntrou by remember { mutableStateOf(reduzir) }
    LaunchedEffect(alvoFracao, reduzir) {
        if (reduzir) {
            entrada.snapTo(1f); fracao.snapTo(alvoFracao); jaEntrou = true
            return@LaunchedEffect
        }
        if (!jaEntrou) {
            delay(ordem.coerceAtMost(TETO_CASCATA) * ATRASO_MS)
            entrada.animateTo(1f, tween(240, easing = EaseOutStd))
            jaEntrou = true
        }
        fracao.animateTo(alvoFracao, tween(480, easing = EaseOutSoft))
    }

    val corTitulo = when {
        m.concluida -> Obsidian.text3
        sobHover    -> Obsidian.text1
        else        -> Obsidian.text2
    }

    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = entrada.value
                translationY = (1f - entrada.value) * 8.dp.toPx()
            }
            .clip(RoundedCornerShape(8.dp))
            .hoverable(hover)
            .background(if (sobHover) Obsidian.hover.copy(alpha = 0.5f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Marcador(m.concluida, conquista)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    m.titulo,
                    style = TextStyle(color = corTitulo, fontSize = 12.5.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!m.concluida) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${m.progresso}/${m.alvo}",
                        style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, fontFamily = DmMono),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .drawBehind {
                        drawRoundRectSimples(Obsidian.borderDim.copy(alpha = 0.55f), size.width, size.height)
                        val f = fracao.value
                        if (f > 0f) {
                            drawRoundRectSimples(
                                if (m.concluida) Obsidian.accent.copy(alpha = 0.45f) else Obsidian.accent,
                                size.width * f, size.height,
                            )
                        }
                    },
            )
        }
        Spacer(Modifier.width(12.dp))
        if (m.resgatavel) {
            BotaoDeResgate("+${m.xp}") { onResgatar(m.id) }
        } else {
            val cor = if (m.concluida) Obsidian.text3 else Obsidian.accent
            Row(Modifier.width(LARGURA_DA_RECOMPENSA), verticalAlignment = Alignment.CenterVertically) {
                BrilhoEmPixels(cor, 11.dp, "de brilho")
                Spacer(Modifier.width(3.dp))
                Text(
                    "+${m.xp}",
                    style = TextStyle(
                        color = cor,
                        fontSize = if (bonus) 12.sp else 11.sp,
                        fontFamily = DmMono,
                        fontWeight = if (bonus) FontWeight.Medium else FontWeight.Normal,
                    ),
                )
            }
        }
    }
}

@Composable
private fun Moeda(
    icone: androidx.compose.ui.graphics.vector.ImageVector?,
    valor: String,
    cor: Color,
    rotulo: String,
    tamanho: androidx.compose.ui.unit.TextUnit = 11.sp,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icone == null) BrilhoEmPixels(cor, 12.dp, rotulo)
        else LIcon(icone, tint = cor, size = 12.dp, rotulo = rotulo)
        Spacer(Modifier.width(4.dp))
        Text(valor, style = TextStyle(color = cor, fontSize = tamanho, fontFamily = DmMono))
    }
}

@Composable
private fun BotaoDeResgate(rotulo: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val sobHover by src.collectIsHoveredAsState()
    val forma = RoundedCornerShape(7.dp)
    Box(
        Modifier
            .width(44.dp)
            .height(24.dp)
            .clickScale(src, formaDoFoco = forma)
            .clip(forma)
            .background(if (sobHover) Obsidian.text1 else Obsidian.accent)
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            rotulo,
            style = TextStyle(
                color = Obsidian.textInv, fontSize = 11.sp,
                fontFamily = DmMono, fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun FaixaDeResgate(quantas: Int, ocupado: Boolean, onResgatarTudo: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val sobHover by src.collectIsHoveredAsState()
    val forma = RoundedCornerShape(9.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(forma)
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.accentDim, forma)
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (quantas == 1) "1 missão esperando por você" else "$quantas missões esperando por você",
            style = TextStyle(color = Obsidian.text1, fontSize = 12.5.sp),
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .height(26.dp)
                .clickScale(src, formaDoFoco = RoundedCornerShape(7.dp))
                .clip(RoundedCornerShape(7.dp))
                .background(if (sobHover) Obsidian.text1 else Obsidian.accent)
                .hoverable(src)
                .clickable(interactionSource = src, indication = null, enabled = !ocupado, onClick = onResgatarTudo)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (ocupado) "resgatando…" else "resgatar tudo",
                style = TextStyle(color = Obsidian.textInv, fontSize = 11.5.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

@Composable
private fun Marcador(concluida: Boolean, conquista: Boolean) {
    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
        when {
            conquista && concluida -> Box(
                Modifier.size(18.dp).clip(CircleShape).background(Obsidian.accent),
                contentAlignment = Alignment.Center,
            ) {
                LIcon(Lucide.Medal, tint = Obsidian.textInv, size = 12.dp, rotulo = "conquistada")
            }
            conquista -> LIcon(Lucide.Medal, tint = Obsidian.borderMid, size = 16.dp)
            concluida -> Box(
                Modifier.size(16.dp).clip(CircleShape).background(Obsidian.accent),
                contentAlignment = Alignment.Center,
            ) {
                LIcon(Lucide.Check, tint = Obsidian.textInv, size = 10.dp, rotulo = "concluída")
            }
            else -> Box(
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
        LIcon(
            Lucide.X,
            tint = if (hovered) Obsidian.text1 else Obsidian.text3,
            size = 14.dp,
            rotulo = "fechar",
        )
    }
}

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
