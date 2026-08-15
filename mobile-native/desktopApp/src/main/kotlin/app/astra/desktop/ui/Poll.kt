package app.astra.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.shell.expirada
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.EaseOutSoft
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.dto.PollDto
import java.time.Duration
import java.time.Instant

// ENQUETE.
//
// O backend ja tinha tudo (criar, votar, encerrar, e o evento poll_updated); o
// desktop e que nao sabia DESENHAR — entao o item "criar enquete" ficou fora do
// menu '+' ate agora, porque botao que cria mensagem que o app nao exibe e pior
// que a ausencia do botao.
//
// Duas decisoes que valem registro:
//
// 1. O RESULTADO APARECE SEMPRE, mesmo pra quem ainda nao votou. Enquete de chat
//    nao e urna: as pessoas conversam sobre ela na mesma tela. Esconder a parcial
//    ate votar (padrao de pesquisa seria) so cria o "vota qualquer coisa pra ver".
//
// 2. A barra fica DENTRO da linha da opcao, nao embaixo. Uma linha por opcao le
//    mais rapido que duas, e o chat e uma coluna estreita disputada por mensagem.

// ---- Enquete desenhada na mensagem ----

@Composable
fun PollBlock(
    poll: PollDto,
    myId: String?,
    podeEncerrar: Boolean,
    onVote: (String) -> Unit,
    onClose: () -> Unit,
) {
    val total = remember(poll) { poll.options.sumOf { it.votes.size } }
    val fim = remember(poll) { expirada(poll) }
    val travada = poll.closed || fim
    val meuVoto = remember(poll, myId) { myId != null && poll.options.any { myId in it.votes } }

    Column(
        Modifier
            .widthIn(max = 420.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
            .padding(13.dp),
    ) {
        Text(
            poll.question,
            style = TextStyle(color = Obsidian.text1, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 19.sp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            if (poll.allowMultiple) "escolha quantas quiser" else "escolha uma",
            style = TextStyle(color = Obsidian.text3, fontSize = 10.sp),
        )

        Spacer(Modifier.height(10.dp))
        poll.options.forEach { op ->
            val votei = myId != null && myId in op.votes
            // Sem voto nenhum, todas as barras ficam vazias — 1/n daria a impressao
            // falsa de empate ja votado.
            val fracao = if (total == 0) 0f else op.votes.size.toFloat() / total
            PollOptionRow(
                texto = op.text,
                votos = op.votes.size,
                fracao = fracao,
                votei = votei,
                travada = travada,
                onClick = { onVote(op.id) },
            )
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildString {
                    append(if (total == 1) "1 voto" else "$total votos")
                    when {
                        poll.closed -> append("  ·  encerrada")
                        fim -> append("  ·  prazo esgotado")
                        else -> restante(poll)?.let { append("  ·  $it") }
                    }
                    if (meuVoto && !travada) append("  ·  clique de novo para tirar o voto")
                },
                style = TextStyle(color = Obsidian.text3, fontSize = 10.sp),
            )
            if (podeEncerrar && !travada) {
                Spacer(Modifier.width(10.dp))
                val src = remember { MutableInteractionSource() }
                val hov by src.collectIsHoveredAsState()
                Text(
                    "encerrar",
                    style = TextStyle(color = if (hov) Obsidian.danger else Obsidian.text3, fontSize = 10.sp),
                    modifier = Modifier
                        .hoverable(src)
                        .clickable(interactionSource = src, indication = null, onClick = onClose),
                )
            }
        }
    }
}

@Composable
private fun PollOptionRow(
    texto: String,
    votos: Int,
    fracao: Float,
    votei: Boolean,
    travada: Boolean,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    val reduce = LocalReduceMotion.current
    // A barra ANIMA ate a nova largura: com voto ao vivo de varias pessoas, um
    // salto seco parece falha de render. Curta o bastante pra nao atrasar a
    // leitura do resultado.
    val largura by animateFloatAsState(
        targetValue = fracao.coerceIn(0f, 1f),
        animationSpec = tween(if (reduce) 0 else 320, easing = EaseOutSoft),
        label = "pollBar",
    )
    val borda = when {
        votei -> Obsidian.accentDim
        hov && !travada -> Obsidian.borderMid
        else -> Obsidian.borderDim
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Obsidian.overlay)
            .border(1.dp, borda, RoundedCornerShape(8.dp))
            .then(
                if (travada) Modifier
                else Modifier
                    .hoverable(src)
                    .clickable(interactionSource = src, indication = null, onClick = onClick),
            ),
    ) {
        // Preenchimento proporcional ATRAS do texto. Quem votou ganha o accent;
        // as outras ficam num cinza que so marca a proporcao sem competir.
        Box(
            Modifier
                .fillMaxWidth(largura)
                .fillMaxHeight()
                .background(if (votei) Obsidian.accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f)),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (votei) {
                Text("●", style = TextStyle(color = Obsidian.accent, fontSize = 8.sp))
                Spacer(Modifier.width(7.dp))
            }
            Text(
                texto,
                style = TextStyle(color = if (votei) Obsidian.text1 else Obsidian.text2, fontSize = 12.sp),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "$votos",
                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, fontFamily = DmMono),
            )
        }
    }
}

// "termina em 3h" / "termina em 12min". Sem prazo = null (a linha nem aparece).
private fun restante(poll: PollDto): String? {
    val fim = poll.expiresAt ?: return null
    val instante = runCatching { Instant.parse(fim) }.getOrNull() ?: return null
    val d = Duration.between(Instant.now(), instante)
    if (d.isNegative) return null
    val dias = d.toDays()
    val horas = d.toHours()
    return when {
        dias >= 1 -> "termina em ${dias}d"
        horas >= 1 -> "termina em ${horas}h"
        else -> "termina em ${d.toMinutes().coerceAtLeast(1)}min"
    }
}

// ---- Criar enquete ----

private val PRAZOS = listOf<Pair<String, Int?>>(
    "sem prazo" to null,
    "1h" to 1,
    "6h" to 6,
    "1 dia" to 24,
    "3 dias" to 72,
    "1 semana" to 168,
)

// Espelho dos limites do backend (routes/polls.ts). Bater aqui evita mandar uma
// enquete que ja se sabe que volta com 400.
private const val MAX_OPCOES = 8
private const val MAX_PERGUNTA = 300
private const val MAX_OPCAO = 80

@Composable
fun CriarEnqueteDialog(
    canalNome: String,
    onCriar: (pergunta: String, opcoes: List<String>, multipla: Boolean, prazoHoras: Int?) -> Unit,
    onClose: () -> Unit,
) {
    var pergunta by remember { mutableStateOf("") }
    // Comeca com duas linhas vazias: e o minimo que o backend aceita, entao a
    // forma da enquete ja aparece pronta em vez de exigir dois cliques em "+".
    val opcoes = remember { mutableStateListOf("", "") }
    var multipla by remember { mutableStateOf(false) }
    var prazo by remember { mutableStateOf(0) }

    val validas = opcoes.count { it.isNotBlank() }
    val podeCriar = pergunta.trim().length >= 3 && validas >= 2

    DialogShell(onClose) {
        Text(
            "criar enquete",
            style = TextStyle(color = Obsidian.text1, fontSize = 15.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            "em $canalNome",
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(16.dp))
        Text("pergunta", style = TextStyle(color = Obsidian.text2, fontSize = 12.sp))
        Spacer(Modifier.height(7.dp))
        DialogField(pergunta, "o que você quer perguntar?", { pergunta = it.take(MAX_PERGUNTA) })

        Spacer(Modifier.height(14.dp))
        Text("opções", style = TextStyle(color = Obsidian.text2, fontSize = 12.sp))
        Spacer(Modifier.height(7.dp))
        opcoes.forEachIndexed { i, valor ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    DialogField(valor, "opção ${i + 1}", { opcoes[i] = it.take(MAX_OPCAO) })
                }
                // Abaixo de 3 nao ha o que remover: 2 e o minimo do backend, e um
                // botao que sempre falha e ruido.
                if (opcoes.size > 2) {
                    Spacer(Modifier.width(6.dp))
                    BotaoGlifo("×") { opcoes.removeAt(i) }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        if (opcoes.size < MAX_OPCOES) {
            val src = remember { MutableInteractionSource() }
            val hov by src.collectIsHoveredAsState()
            Text(
                "+ adicionar opção",
                style = TextStyle(color = if (hov) Obsidian.accent else Obsidian.text3, fontSize = 11.sp),
                modifier = Modifier
                    .hoverable(src)
                    .clickable(interactionSource = src, indication = null) { opcoes.add("") }
                    .padding(vertical = 3.dp),
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Caixinha(multipla) { multipla = !multipla }
            Spacer(Modifier.width(8.dp))
            Text(
                "deixar escolher mais de uma",
                style = TextStyle(color = Obsidian.text2, fontSize = 12.sp),
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { multipla = !multipla },
            )
        }

        Spacer(Modifier.height(14.dp))
        Text("prazo", style = TextStyle(color = Obsidian.text2, fontSize = 12.sp))
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PRAZOS.forEachIndexed { i, (rotulo, _) ->
                Pastilha(rotulo, ativa = prazo == i) { prazo = i }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DialogButton("cancelar", accent = false) { onClose() }
            DialogButton("criar", accent = podeCriar) {
                if (podeCriar) {
                    onCriar(pergunta.trim(), opcoes.toList(), multipla, PRAZOS[prazo].second)
                    onClose()
                }
            }
        }
    }
}

@Composable
private fun BotaoGlifo(glifo: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (hov) Obsidian.danger else Obsidian.borderDim, RoundedCornerShape(8.dp))
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glifo, style = TextStyle(color = if (hov) Obsidian.danger else Obsidian.text3, fontSize = 13.sp))
    }
}

@Composable
private fun Caixinha(marcada: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(16.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (marcada) Obsidian.accent.copy(alpha = 0.22f) else Color.Transparent)
            .border(1.dp, if (marcada) Obsidian.accent else Obsidian.borderMid, RoundedCornerShape(5.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (marcada) Text("✓", style = TextStyle(color = Obsidian.accent, fontSize = 10.sp))
    }
}

@Composable
private fun Pastilha(rotulo: String, ativa: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Text(
        rotulo,
        style = TextStyle(color = if (ativa) Obsidian.accent else Obsidian.text3, fontSize = 11.sp),
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .border(
                1.dp,
                if (ativa) Obsidian.accentDim else if (hov) Obsidian.borderMid else Obsidian.borderDim,
                RoundedCornerShape(7.dp),
            )
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    )
}
