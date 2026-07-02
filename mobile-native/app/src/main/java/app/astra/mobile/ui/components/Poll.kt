package app.astra.mobile.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.theme.EaseOutSoft
import app.astra.mobile.ui.theme.astraColors
import zed.rainxch.rikkaui.components.ui.input.Input
import zed.rainxch.rikkaui.components.ui.input.InputAnimation
import kotlin.math.roundToInt

// @Immutable: instavel so pela List<PollOptionUi>; PollUi e recriado a cada
// poll_updated, nunca mutado -> destrava skip no MessageBubble/PollCard.
@Immutable
data class PollUi(
    val question: String,
    val options: List<PollOptionUi>,
    val allowMultiple: Boolean,
    val expiresAt: String?,
    val closed: Boolean,
)

data class PollOptionUi(
    val id: String,
    val text: String,
    val votes: Int,
    val mine: Boolean,
)

@Composable
fun PollCard(
    poll: PollUi,
    maxWidth: Dp,
    canClose: Boolean,
    onVote: (String) -> Unit,
    onClose: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val total = poll.options.sumOf { it.votes }
    val (expired, expiryLabel) = remember(poll.expiresAt) { pollExpiry(poll.expiresAt) }
    val closed = poll.closed || expired

    Column(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .clip(shape)
            .background(astraColors.raised)
            .border(1.dp, astraColors.borderMid, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = poll.question,
                style = MaterialTheme.typography.titleSmall,
                color = astraColors.text1,
                modifier = Modifier.weight(1f),
            )
            if (closed) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "encerrada",
                    style = MaterialTheme.typography.labelSmall,
                    color = astraColors.text3,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, astraColors.border, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        poll.options.forEach { opt ->
            val pct = if (total > 0) opt.votes.toFloat() / total else 0f
            PollOptionRow(opt, pct, closed) { onVote(opt.id) }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = buildString {
                    append(if (total == 1) "1 voto" else "$total votos")
                    if (poll.allowMultiple) append(" · múltipla")
                },
                style = MaterialTheme.typography.labelSmall,
                color = astraColors.text3,
            )
            Spacer(Modifier.weight(1f))
            if (poll.expiresAt != null && expiryLabel.isNotEmpty()) {
                Text(
                    text = if (expired) expiryLabel else "encerra em $expiryLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = astraColors.text3,
                )
            }
            if (canClose && !closed) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "encerrar",
                    style = MaterialTheme.typography.labelSmall,
                    color = astraColors.text3,
                    modifier = Modifier.clickable(onClick = onClose),
                )
            }
        }
    }
}

@Composable
private fun PollOptionRow(
    opt: PollOptionUi,
    pct: Float,
    closed: Boolean,
    onVote: () -> Unit,
) {
    val prefs = LocalAppPrefs.current
    val shape = RoundedCornerShape(9.dp)
    val fill by animateFloatAsState(
        targetValue = pct.coerceIn(0f, 1f),
        animationSpec = tween(if (prefs.reduceMotion) 0 else 420, easing = EaseOutSoft),
        label = "pollbar",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, if (opt.mine) astraColors.accent else astraColors.border, shape)
            .then(if (!closed) Modifier.clickable(onClick = onVote) else Modifier),
    ) {
        Row(Modifier.matchParentSize()) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fill)
                    .background(astraColors.accent.copy(alpha = if (opt.mine) 0.20f else 0.08f)),
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (opt.mine) {
                Text("✓", color = astraColors.accent, fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = opt.text,
                style = MaterialTheme.typography.bodyMedium,
                color = astraColors.text1,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${opt.votes} · ${(pct * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = astraColors.text3,
            )
        }
    }
}

private val POLL_DURATIONS: List<Pair<String, Int?>> = listOf(
    "sem limite" to null,
    "1h" to 1,
    "6h" to 6,
    "24h" to 24,
    "3d" to 72,
    "7d" to 168,
)

@Composable
fun PollComposer(
    onCreate: (question: String, options: List<String>, allowMultiple: Boolean, durationHours: Int?) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    var question by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    var allowMultiple by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf<Int?>(24) }

    val filled = options.map { it.trim() }.filter { it.isNotEmpty() }
    val canSubmit = question.trim().length >= 3 && filled.size >= 2

    val panelShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { onClose() } }
            .imePadding(),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(panelShape)
                .background(astraColors.raised)
                .border(1.dp, astraColors.borderMid, panelShape)
                .pointerInput(Unit) { detectTapGestures {} }
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MarginaliaLabel("nova enquete", color = astraColors.accent)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "×",
                    style = MaterialTheme.typography.titleLarge,
                    color = astraColors.text2,
                    modifier = Modifier.clickable(onClick = onClose).padding(horizontal = 8.dp),
                )
            }

            FieldLabel("pergunta")
            Input(
                value = question,
                onValueChange = { question = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "O que você quer perguntar?",
                singleLine = false,
                animation = InputAnimation.Glow,
            )

            FieldLabel("opções")
            options.forEachIndexed { i, opt ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Input(
                        value = opt,
                        onValueChange = { v -> options = options.mapIndexed { idx, o -> if (idx == i) v else o } },
                        modifier = Modifier.weight(1f),
                        placeholder = "Opção ${i + 1}",
                        singleLine = true,
                        animation = InputAnimation.Glow,
                    )
                    if (options.size > 2) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "×",
                            style = MaterialTheme.typography.titleMedium,
                            color = astraColors.text3,
                            modifier = Modifier
                                .clickable { options = options.filterIndexed { idx, _ -> idx != i } }
                                .padding(horizontal = 6.dp),
                        )
                    }
                }
            }
            if (options.size < 8) {
                Text(
                    text = "+ adicionar opção",
                    style = MaterialTheme.typography.labelLarge,
                    color = astraColors.accent,
                    modifier = Modifier.clickable { options = options + "" },
                )
            }

            FieldLabel("duração")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                POLL_DURATIONS.forEach { (label, value) ->
                    PollPill(label = label, selected = duration == value) { duration = value }
                }
            }

            Row(
                modifier = Modifier.clickable { allowMultiple = !allowMultiple },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (allowMultiple) astraColors.accent else Color.Transparent)
                        .border(1.dp, if (allowMultiple) astraColors.accent else astraColors.border, RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (allowMultiple) Text("✓", color = astraColors.textInv, fontSize = 12.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "permitir múltipla escolha",
                    style = MaterialTheme.typography.bodyMedium,
                    color = astraColors.text2,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (canSubmit) astraColors.accent else astraColors.base)
                    .border(1.dp, if (canSubmit) Color.Transparent else astraColors.borderMid, RoundedCornerShape(12.dp))
                    .clickable(enabled = canSubmit) {
                        onCreate(question.trim(), filled, allowMultiple, duration)
                        onClose()
                    }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Criar enquete",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (canSubmit) astraColors.textInv else astraColors.text3,
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = astraColors.text3,
    )
}

@Composable
private fun PollPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) astraColors.accent.copy(alpha = 0.16f) else astraColors.base)
            .border(1.dp, if (selected) astraColors.accent else astraColors.border, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) astraColors.accent else astraColors.text2,
        )
    }
}

private fun pollExpiry(iso: String?): Pair<Boolean, String> {
    if (iso == null) return false to ""
    return runCatching {
        val then = java.time.Instant.parse(iso)
        val secs = java.time.Duration.between(java.time.Instant.now(), then).seconds
        if (secs <= 0) {
            true to "expirada"
        } else {
            val hours = secs / 3600
            when {
                hours >= 24 -> false to "${hours / 24}d"
                hours >= 1 -> false to "${hours}h"
                else -> false to "${secs / 60}m"
            }
        }
    }.getOrDefault(false to "")
}
