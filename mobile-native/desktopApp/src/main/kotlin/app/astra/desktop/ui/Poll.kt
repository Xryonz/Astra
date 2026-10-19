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
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
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
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import java.time.Duration
import java.time.Instant
import app.astra.desktop.ui.theme.Tipo

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
            style = Tipo.nota,
        )

        Spacer(Modifier.height(10.dp))
        poll.options.forEach { op ->
            val votei = myId != null && myId in op.votes
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
                style = Tipo.nota,
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

private val PRAZOS = listOf<Pair<String, Int?>>(
    "sem prazo" to null,
    "1h" to 1,
    "6h" to 6,
    "1 dia" to 24,
    "3 dias" to 72,
    "1 semana" to 168,
)

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
    val opcoes = remember { mutableStateListOf("", "") }
    var multipla by remember { mutableStateOf(false) }
    var prazo by remember { mutableStateOf(0) }

    val validas = opcoes.count { it.isNotBlank() }
    val podeCriar = pergunta.trim().length >= 3 && validas >= 2

    Dialogo(titulo = "criar enquete", aoFechar = onClose, largura = LarguraDoDialogo.MEDIA) { fechar ->
        val focoInicial = rememberFocoAoAbrir()
        Spacer(Modifier.height(8.dp))
        Text(
            "em $canalNome",
            style = TextoDoDialogo,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(16.dp))
        RotuloDeCampo("pergunta")
        CampoDeDialogo(
            pergunta,
            { pergunta = it.take(MAX_PERGUNTA) },
            dica = "o que você quer perguntar?",
            foco = focoInicial,
        )

        Spacer(Modifier.height(14.dp))
        RotuloDeCampo("opções")
        opcoes.forEachIndexed { i, valor ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CampoDeDialogo(
                    valor,
                    { opcoes[i] = it.take(MAX_OPCAO) },
                    Modifier.weight(1f),
                    dica = "opção ${i + 1}",
                )
                if (opcoes.size > 2) {
                    Spacer(Modifier.width(6.dp))
                    BotaoIcone(Lucide.X, dica = "remover a opção ${i + 1}", danger = true) { opcoes.removeAt(i) }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        if (opcoes.size < MAX_OPCOES) {
            BotaoDeDialogo("adicionar opção", TipoDeBotao.SECUNDARIO, onClick = { opcoes.add("") })
        }

        Spacer(Modifier.height(14.dp))
        CaixaDeMarcar("deixar escolher mais de uma", multipla) { multipla = !multipla }

        Spacer(Modifier.height(14.dp))
        RotuloDeCampo("prazo")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PRAZOS.forEachIndexed { i, (rotulo, _) ->
                EscolhaDoDialogo(rotulo, ativa = prazo == i, onClick = { prazo = i })
            }
        }

        RodapeDoDialogo {
            BotaoDeDialogo("cancelar", TipoDeBotao.SECUNDARIO, onClick = fechar)
            BotaoDeDialogo(
                "criar",
                TipoDeBotao.PRINCIPAL,
                onClick = {
                    onCriar(pergunta.trim(), opcoes.toList(), multipla, PRAZOS[prazo].second)
                    onClose()
                },
                ligado = podeCriar,
            )
        }
    }
}

@Composable
private fun CaixaDeMarcar(rotulo: String, marcada: Boolean, onClick: () -> Unit) {
    val toque = remember { MutableInteractionSource() }
    Row(
        Modifier
            .clickScale(toque)
            .defaultMinSize(minHeight = 28.dp)
            .clip(RoundedCornerShape(8.dp))
            .semantics { toggleableState = ToggleableState(marcada) }
            .clickable(interactionSource = toque, indication = null, role = Role.Checkbox, onClick = onClick)
            .padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (marcada) Obsidian.accent.copy(alpha = 0.22f) else Color.Transparent)
                .border(1.dp, if (marcada) Obsidian.accent else Obsidian.borderMid, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (marcada) LIcon(Lucide.Check, tint = Obsidian.accent, size = 11.dp)
        }
        Spacer(Modifier.width(8.dp))
        Text(rotulo, style = Tipo.rotulo)
    }
}
