package app.astra.desktop.ui

import androidx.compose.animation.core.Animatable
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

// Cascata de entrada. 40ms e o intervalo em que as linhas ainda leem como uma
// sequencia; abaixo disso viram um bloco so, acima viram uma fila.
private const val ATRASO_MS = 40L
private const val TETO_CASCATA = 11

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
fun MissoesOverlay(me: ProfileUserDto?, onClose: () -> Unit) {
    val store = remember { GlobalContext.get().get<MissoesStore>() }
    val painel by store.painel.collectAsState()
    val xpStore = remember { GlobalContext.get().get<XpStore>() }
    val progresso by xpStore.progresso.collectAsState()
    val visualXp = rememberVisualDeXp(xpStore)
    LaunchedEffect(Unit) { xpStore.recarregar() }

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
                // "Sua jornada", e não mais "missões": a tela deixou de ser só a
                // lista de tarefas quando ganhou o estado da conta em cima.
                Text(
                    "sua jornada",
                    style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif),
                )
                Spacer(Modifier.weight(1f))
                BotaoFechar(onClose)
            }
            HairRule()

            // CARTÃO DENTRO DO CARTÃO: o estado da conta mora num degrau acima do
            // painel, e é o que separa "quem você é aqui" da lista do que falta
            // fazer — sem precisar de traço entre os dois.
            EstadoDaConta(me, progresso, visualXp)

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
                // A ordem CONTINUA de bloco pra bloco: a cascata varre o painel
                // inteiro de cima a baixo, uma vez. Reiniciar em cada secao faria
                // tres animacoes competindo, e o olho perderia o fio.
                val depoisDoBonus = p.diarias.itens.size + 1
                val depoisDaSemana = depoisDoBonus + p.semanais.itens.size

                Secao("hoje", faltando(p.diarias.renovaEm, agora))
                p.diarias.itens.forEachIndexed { i, m -> LinhaDeMissao(m, i) }
                // O bonus fica visualmente preso as tres de cima (sem espaco antes):
                // ele nao e uma quarta missao, e a consequencia daquelas tres.
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

// O estado da conta: quem você é, em que nível está e quanto falta pro próximo.
//
// O anel em volta da foto é o MESMO do rodapé (anelDeXp + rememberVisualDeXp), e
// isso importa: a pessoa vê aquele anel o dia inteiro no canto da tela sem saber
// o que ele mede. Aqui ele aparece grande, ao lado do número — é onde o anel
// finalmente se explica.
//
// A barra embaixo repete a mesma fração de propósito. O anel é bonito e vago; a
// barra com "340 / 500" é a leitura exata. Quem quer sentir olha o anel, quem
// quer saber lê a barra.
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
                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
                maxLines = 1,
            )
            Spacer(Modifier.height(8.dp))
            // Trilho fino, do mesmo vocabulário da barra do gate de boot.
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
                style = TextStyle(color = Obsidian.text3, fontSize = 10.sp),
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

// Uma missao. A barra de progresso e uma HAIRLINE debaixo do titulo, nao um bloco:
// oito barras grossas empilhadas viram um painel de jogo mobile, e a estetica daqui
// e editorial. A linha fina diz a mesma coisa e some quando nao e olhada.
@Composable
private fun LinhaDeMissao(m: ItemMissaoDto, ordem: Int, bonus: Boolean = false) {
    val hover = remember { MutableInteractionSource() }
    val sobHover by hover.collectIsHoveredAsState()
    val reduzir = LocalReduceMotion.current
    val alvoFracao = if (m.alvo <= 0) 0f else (m.progresso.toFloat() / m.alvo).coerceIn(0f, 1f)

    // Cascata: cada linha entra ATRASO_MS depois da de cima, subindo 8dp. O teto
    // existe porque a lista de conquistas cresce — sem ele, a decima quinta linha
    // entraria meio segundo depois da primeira e a "cascata" viraria espera.
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
        // A barra corre DEPOIS que a linha pousa. Ver o progresso acontecer e o
        // ponto da coisa, e ele se perde se disputar atencao com a entrada.
        fracao.animateTo(alvoFracao, tween(480, easing = EaseOutSoft))
    }

    val corTitulo = when {
        m.concluida -> Obsidian.text3      // feito sai do caminho, nao vira troféu
        sobHover    -> Obsidian.text1
        else        -> Obsidian.text2
    }

    Row(
        Modifier
            .fillMaxWidth()
            // Fase de desenho: a cascata nao recompoe a linha, so redesenha.
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
            // Trilho + preenchimento na fase de DESENHO. Um Box com width animada
            // relayoutaria a linha 60x por segundo durante a animacao.
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
