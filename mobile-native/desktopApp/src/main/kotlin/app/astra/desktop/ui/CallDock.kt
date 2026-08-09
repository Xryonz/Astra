package app.astra.desktop.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.voice.VoiceEngine
import app.astra.desktop.voice.VoiceStatus
import app.astra.mobile.core.network.dto.ChannelDto
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Maximize2
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.MicOff
import com.composables.icons.lucide.PhoneOff
import kotlin.math.roundToInt

// Card flutuante da call — aparece quando você esta conectado mas navegou pra
// outra tela. Espelha o VoiceCallPanel do web (arrastavel, avatares de quem
// fala, mic/desligar, botao de voltar pro palco) com tres cortes de custo que
// pesavam la:
//
//  1. O web roda uma animação infinita POR PARTICIPANTE (o anel de quem fala) e
//     mais uma no ponto pulsante. Aqui existe UMA transicao infinita so, e o
//     valor dela e lido dentro do drawBehind — muda o desenho, não a composicao.
//  2. O anel de "falando" e pintado (drawCircle no draw scope), não um layout a
//     mais por avatar entrando e saindo da arvore.
//  3. Parado (ninguem falando) NENHUM frame e pedido: a transicao infinita so e
//     criada quando ha alguem falando, e some junto.
//
// Arrastar guarda a posição em memoria (não persiste entre sessões de propósito:
// menos I/O e o card sempre volta pro canto conhecido ao reabrir o app).
@Composable
fun BoxScope.CallDock(
    channel: ChannelDto,
    engine: VoiceEngine,
    meName: String,
    meAvatar: String?,
    onExpand: () -> Unit,
    onLeave: () -> Unit,
) {
    val status by engine.status.collectAsState()
    val inicio by engine.inicio.collectAsState()
    val micOn by engine.micOn.collectAsState()

    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }

    // O ARRASTO PARA NA BORDA. Nada segurava o cartao: dava pra empurrar ele pra
    // fora da janela e perder de vista o unico botao de desligar que existe depois
    // que navegar deixou de desconectar. Sumir com o controle da call e pior do que
    // qualquer limitacao de onde ele pode ficar.
    //
    // O limite e conferido nos DOIS lugares de proposito: no gesto (senao o dx
    // acumula pra sempre e voltar exige arrastar a mesma distancia de volta) e na
    // hora de posicionar (senao encolher a janela deixaria o cartao do lado de fora
    // sem ninguem ter arrastado nada).
    var meu by remember { mutableStateOf(IntSize.Zero) }
    var pai by remember { mutableStateOf(IntSize.Zero) }
    val densidade = LocalDensity.current
    val folga = with(densidade) { 8.dp.toPx() }      // margem minima ate a borda
    val descanso = with(densidade) { 18.dp.toPx() }  // onde ele nasce (padding abaixo)

    // minOf(): antes da primeira medida o pai e 0x0, e ai o minimo passaria do
    // maximo — coerceIn com faixa invertida estoura.
    fun faixa(tamanhoMeu: Int, tamanhoPai: Int): ClosedFloatingPointRange<Float> {
        val maximo = descanso - folga
        val minimo = folga + descanso + tamanhoMeu - tamanhoPai
        return minOf(minimo.toFloat(), maximo)..maximo
    }

    val connected = status as? VoiceStatus.Connected
    val speakers = connected?.others?.filter { it.speaking }.orEmpty()
    val anySpeaking = speakers.isNotEmpty() || connected?.mySpeaking == true
    val count = (connected?.others?.size ?: 0) + if (connected != null) 1 else 0

    Box(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 18.dp, bottom = 18.dp)
            .offset {
                IntOffset(
                    dx.coerceIn(faixa(meu.width, pai.width)).roundToInt(),
                    dy.coerceIn(faixa(meu.height, pai.height)).roundToInt(),
                )
            }
            .onPlaced { c -> c.parentLayoutCoordinates?.size?.let { pai = it } }
            .onSizeChanged { meu = it }
            .width(232.dp)
            // O card sumia no fundo, e por dois motivos somados: ele flutua sobre a
            // AURORA (que e escura e viva, entao nao serve de contraste estavel) e
            // usava `overlay` — um tom que existe justamente pra encostar no fundo,
            // nao pra se destacar dele.
            //
            // Tres coisas resolvem, e as tres fazem falta separadas: sombra (e o que
            // diz "isto esta FLUTUANDO", e nao havia nenhuma), uma superficie um
            // degrau mais clara, e opacidade cheia — a 0.94 a aurora atravessava o
            // card e mexia por baixo do texto.
            .shadow(20.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Obsidian.hover)
            .border(1.dp, Obsidian.borderMid, RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    dx = (dx + drag.x).coerceIn(faixa(meu.width, pai.width))
                    dy = (dy + drag.y).coerceIn(faixa(meu.height, pai.height))
                }
            },
    ) {
        Column {
            // Cabecalho: ponto vivo + de onde vem o audio + quantos estão na sala.
            Row(
                Modifier.fillMaxWidth().padding(start = 11.dp, end = 7.dp, top = 9.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiveDot(anySpeaking)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            status is VoiceStatus.Connecting -> "conectando…"
                            status is VoiceStatus.Failed -> "sinal caiu"
                            speakers.isNotEmpty() -> "${speakers.first().label} falando"
                            else -> channel.name
                        },
                        style = TextStyle(color = Obsidian.text1, fontSize = 12.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val tempo by lembrarTempoDeCall(inicio)
                    Text(
                        when {
                            connected == null -> "…"
                            tempo.isNotEmpty() -> "$count na sala · $tempo"
                            else -> "$count na sala"
                        },
                        style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, fontFamily = DmMono),
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(6.dp))
                DockIcon(Lucide.Maximize2, tint = Obsidian.text2, onClick = onExpand)
            }
            // Quem esta na sala (pedido do dono): so o "N na sala" não dizia QUEM. Cada
            // rosto entra estourando e sai encolhendo (PopIn) — a chave e a identity, e
            // por isso que trocar de gente anima em vez de so trocar a imagem.
            if (connected != null) {
                val faces = remember(connected, meName, meAvatar) {
                    listOf(DockFace("me", meName, meAvatar, connected.mySpeaking)) +
                        connected.others.map { DockFace(it.identity, it.label, it.avatarUrl, it.speaking) }
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 11.dp, end = 11.dp, bottom = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    faces.take(6).forEach { f ->
                        key(f.id) {
                            PopIn {
                                DockFaceAvatar(f)
                            }
                        }
                    }
                    if (faces.size > 6) {
                        Text(
                            "+${faces.size - 6}",
                            style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, fontFamily = DmMono),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Acoes: calar/abrir mic e desligar. Desligar e a UNICA saida da call
            // agora que navegar não desconecta mais.
            //
            // Faixa propria em vez de traco em cima: os dois botoes aqui embaixo
            // sao a unica parte CLICAVEL do dock, e uma superficie propria diz
            // isso melhor do que uma linha.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Obsidian.overlay)
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DockAction(
                    icon = if (micOn) Lucide.Mic else Lucide.MicOff,
                    tint = if (micOn) Obsidian.text1 else Obsidian.danger,
                    bg = if (micOn) Obsidian.raised else Obsidian.danger.copy(alpha = 0.16f),
                    onClick = engine::toggleMic,
                )
                Spacer(Modifier.width(10.dp))
                DockAction(
                    icon = Lucide.PhoneOff,
                    tint = Obsidian.textInv,
                    bg = Obsidian.danger,
                    onClick = onLeave,
                )
            }
        }
    }
}

private data class DockFace(
    val id: String,
    val label: String,
    val avatarUrl: String?,
    val speaking: Boolean,
)

// Rosto na mini-tela: 22dp. Quem esta FALANDO ganha um anel ambar (mesma leitura
// do palco cheio), animado — assim da pra ver quem fala sem abrir a call.
@Composable
private fun DockFaceAvatar(f: DockFace) {
    val ring by animateFloatAsState(
        targetValue = if (f.speaking) 1f else 0f,
        animationSpec = tween(160),
        label = "dockSpeak",
    )
    Box(
        Modifier
            .size(26.dp)
            .border(
                width = 2.dp,
                color = Obsidian.accent.copy(alpha = 0.9f * ring),
                shape = CircleShape,
            )
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        DesktopAvatar(f.avatarUrl, f.label, 22)
    }
}

// Ponto de "ao vivo". Pulsa SO enquanto alguem fala; em silencio e um circulo
// estatico e nenhum frame e pedido (o web pulsa pra sempre).
@Composable
private fun LiveDot(active: Boolean) {
    if (!active) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Obsidian.success))
        return
    }
    val t = rememberInfiniteTransition(label = "dock-live")
    val p by t.animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(760), RepeatMode.Reverse),
        label = "dock-pulse",
    )
    // Le `p` dentro do draw: pulsa sem recompor a linha inteira do cabecalho.
    Box(
        Modifier.size(12.dp).drawBehind {
            drawCircle(Obsidian.success.copy(alpha = 0.22f * p), radius = size.minDimension / 2f * p)
            drawCircle(Obsidian.success, radius = 4.dp.toPx())
        },
    )
}

@Composable
private fun DockIcon(icon: ImageVector, tint: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier
            .size(24.dp)
            .clickScale(interaction)
            .clip(RoundedCornerShape(6.dp))
            .background(if (hovered) Obsidian.hover else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LIcon(icon, tint = if (hovered) Obsidian.text1 else tint, size = 13.dp)
    }
}

@Composable
private fun DockAction(icon: ImageVector, tint: Color, bg: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val s by animateFloatAsState(if (hovered) 1.08f else 1f, tween(120), label = "dockAct")
    Box(
        Modifier
            .scale(s)
            .clickScale(interaction)
            .size(34.dp)
            .clip(CircleShape)
            .background(bg)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LIcon(icon, tint = tint, size = 15.dp)
    }
}
