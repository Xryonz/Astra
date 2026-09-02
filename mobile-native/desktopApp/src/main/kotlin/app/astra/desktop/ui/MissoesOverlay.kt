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
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
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
import app.astra.mobile.core.network.dto.ItemMissaoDto
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import kotlinx.coroutines.delay
import org.koin.core.context.GlobalContext
import app.astra.desktop.ui.theme.Tipo

private const val MIN_MS  = 60_000L
private const val HORA_MS = 3_600_000L
private const val DIA_MS  = 86_400_000L

private const val ATRASO_MS = 40L
private const val TETO_CASCATA = 11

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

                Secao("hoje", faltando(p.diarias.renovaEm, agora))
                p.diarias.itens.forEachIndexed { i, m -> LinhaDeMissao(m, i) }
                LinhaDeMissao(p.diarias.bonus, depoisDoBonus - 1, bonus = true)

                Spacer(Modifier.height(22.dp))
                Secao("esta semana", faltando(p.semanais.renovaEm, agora))
                p.semanais.itens.forEachIndexed { i, m -> LinhaDeMissao(m, depoisDoBonus + i) }

                Spacer(Modifier.height(22.dp))
                Secao("conquistas", "não expiram")
                p.conquistas.itens.forEachIndexed { i, m -> LinhaDeMissao(m, depoisDaSemana + i) }
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
                style = TextStyle(color = Obsidian.text1, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "nível ${p.nivel} · ${p.xp} de brilho acumulado",
                style = Tipo.apoio,
                maxLines = 1,
            )
            Spacer(Modifier.height(8.dp))
            val fracao = fracaoDe(p)
            Canvas(Modifier.fillMaxWidth().height(3.dp)) {
                val r = CornerRadius(size.height / 2f)
                drawRoundRect(color = Obsidian.void, size = size, cornerRadius = r)
                val w = (size.width * fracao).coerceIn(0f, size.width)
                if (w > 0f) {
                    drawRoundRect(
                        color = Obsidian.accent,
                        size = androidx.compose.ui.geometry.Size(w, size.height),
                        cornerRadius = r,
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                "${p.noNivel} / ${p.paraOProximo} para o nível ${p.nivel + 1}",
                style = Tipo.nota,
                maxLines = 1,
            )
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

@Composable
private fun LinhaDeMissao(m: ItemMissaoDto, ordem: Int, bonus: Boolean = false) {
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
        Marcador(m.concluida)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                m.titulo,
                style = TextStyle(color = corTitulo, fontSize = 12.5.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
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
