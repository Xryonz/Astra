package app.astra.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.astra.desktop.ui.theme.Obsidian
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// O GATO DO ASTRA — o "pet" que estava anotado como uma palavra só no ESTADO.md.
//
// Ele anda livre por cima da interface inteira (escolha do dono) e é desenhado em
// VETOR, não em sprite. O dono pediu um bicho concreto, e concreto é sobre ser
// reconhecível, não sobre ser bitmap: um gato de traço fecha as duas coisas ao
// mesmo tempo — dá pra dizer "é um gato" à primeira olhada, e ele recolore junto do
// tema, escala em qualquer monitor e não soma um byte ao instalador.
//
// TRÊS REGRAS QUE NÃO SE QUEBRAM, porque ele passa por cima de tudo:
//
// 1. **Não intercepta ponteiro.** Um bicho que anda sobre a conversa e engole
//    cliques é um bug com pelo. O `pointerInput` vazio abaixo existe pra deixar
//    isso explícito no código, e a camada inteira é irmã do conteúdo, nunca pai.
// 2. **Dorme quando ninguém vê.** Janela oculta ou minimizada e ele para de vez —
//    não é só invisível, é sem quadro nenhum. Gato de enfeite não tem o direito de
//    custar bateria enquanto o app está na bandeja.
// 3. **Reduzir movimento tira ele da tela.** Não "diminui": tira. O recurso inteiro
//    é movimento contínuo, e é justamente isso que a pessoa desligou. Fingir que
//    obedece com uma animação mais lenta seria pior que ignorar.
//
// TENSÃO COM A NORMA DO APP, dita em voz alta em vez de escondida: a norma diz
// "movimento é sinal, não enfeite; repouso deliberadamente quieto". Um gato que
// caminha é enfeite contínuo por definição, e contraria isso. O acordo é o ritmo:
// ele passa a MAIOR PARTE do tempo sentado (pausas de 4 a 13 segundos) e caminha em
// trechos curtos, para não virar um piscar de canto de olho que ensina o olho a
// ignorar o resto do app. Mais interruptor próprio, para quem discordar do acordo.

enum class PetEvento { MENSAGEM, CALL }

object Pet {
    private val _evento = MutableSharedFlow<PetEvento>(extraBufferCapacity = 4)
    val evento = _evento.asSharedFlow()

    fun mensagemNova() { _evento.tryEmit(PetEvento.MENSAGEM) }
    fun entrouEmCall() { _evento.tryEmit(PetEvento.CALL) }
}

private enum class Estado { SENTADO, ANDANDO, ESPREGUICANDO }

private const val FPS = 30
private val LARGURA_GATO = 34.dp

@Composable
fun GatoDoAstra(ligado: Boolean) {
    val reduzir = LocalReduceMotion.current
    val janelaAtiva = LocalWindowActive.current
    // As três condições numa só: `ligado` é a escolha, `reduzir` é a necessidade e
    // `janelaAtiva` é a economia. Qualquer uma delas falsa e nem a camada existe —
    // sair da composição é mais barato que desenhar nada.
    if (!ligado || reduzir || !janelaAtiva) return

    var area by remember { mutableStateOf(IntSize.Zero) }
    var x by remember { mutableStateOf(-1f) }
    var y by remember { mutableStateOf(-1f) }
    var alvoX by remember { mutableStateOf(0f) }
    var alvoY by remember { mutableStateOf(0f) }
    var estado by remember { mutableStateOf(Estado.SENTADO) }
    var olhandoPraDireita by remember { mutableStateOf(true) }
    var passo by remember { mutableStateOf(0f) }
    var espera by remember { mutableStateOf(2f) }
    var piscada by remember { mutableStateOf(0f) }
    var animacaoDeEvento by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        Pet.evento.collect { ev ->
            // Reagir a evento vale mais que continuar o passeio: o gato para o que
            // estava fazendo. É a única hora em que ele compete por atenção, e é
            // justamente quando a atenção já foi chamada por outra coisa.
            animacaoDeEvento = if (ev == PetEvento.CALL) 1.6f else 1f
            estado = Estado.SENTADO
            espera = 1.2f
        }
    }

    LaunchedEffect(area) {
        if (area.width <= 0) return@LaunchedEffect
        if (x < 0f) { x = area.width * 0.5f; y = area.height * 0.72f }
        while (true) {
            val inicio = System.nanoTime()
            val dt = 1f / FPS

            piscada += dt
            if (animacaoDeEvento > 0f) animacaoDeEvento = (animacaoDeEvento - dt).coerceAtLeast(0f)

            when (estado) {
                Estado.SENTADO, Estado.ESPREGUICANDO -> {
                    espera -= dt
                    if (espera <= 0f) {
                        // Alvo em qualquer lugar, menos a faixa de cima: ali moram a
                        // barra de título e o cabeçalho do canal, e um gato sentado
                        // em cima do nome da conversa atrapalha a leitura da única
                        // linha que diz onde você está.
                        alvoX = Random.nextFloat() * (area.width - 80) + 40
                        alvoY = area.height * (0.35f + Random.nextFloat() * 0.55f)
                        olhandoPraDireita = alvoX > x
                        estado = Estado.ANDANDO
                    }
                }
                Estado.ANDANDO -> {
                    val dx = alvoX - x
                    val dy = alvoY - y
                    val dist = kotlin.math.hypot(dx, dy)
                    if (dist < 4f) {
                        estado = if (Random.nextFloat() < 0.25f) Estado.ESPREGUICANDO else Estado.SENTADO
                        // Pausa LONGA de propósito (4 a 13 segundos). Um gato que
                        // anda sem parar vira ruído periférico; um que passa a maior
                        // parte do tempo parado vira presença.
                        espera = 4f + Random.nextFloat() * 9f
                        passo = 0f
                    } else {
                        val v = 26f * dt
                        x += dx / dist * v
                        y += dy / dist * v
                        passo += dt * 7f
                    }
                }
            }
            esperarPeloTeto(FPS, inicio)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { area = it }
            // NÃO CONSOME NADA. Um `pointerInput` que só suspende para sempre não
            // registra gesto nenhum, então clique, hover e rolagem atravessam a
            // camada e chegam na conversa por baixo.
            .pointerInput(Unit) { },
    ) {
        if (area.width <= 0 || x < 0f) return@Box
        Canvas(Modifier.fillMaxSize()) {
            val esc = LARGURA_GATO.toPx() / 34f
            translate(x, y) {
                desenharGato(
                    esc = esc,
                    paraDireita = olhandoPraDireita,
                    andando = estado == Estado.ANDANDO,
                    passo = passo,
                    olhoFechado = (piscada % 4.2f) < 0.13f,
                    animacaoDeEvento = animacaoDeEvento,
                    pelo = Obsidian.text2,
                    detalhe = Obsidian.accent,
                )
            }
        }
    }
}

// O gato, em traço. Corpo e cabeça são curvas fechadas; patas e cauda são linha.
//
// Ele é desenhado olhando pra DIREITA e espelhado quando anda pra esquerda — meia
// figura pela metade do trabalho, e o espelho é exato porque nenhuma parte do
// desenho depende de qual lado é qual.
private fun DrawScope.desenharGato(
    esc: Float,
    paraDireita: Boolean,
    andando: Boolean,
    passo: Float,
    olhoFechado: Boolean,
    animacaoDeEvento: Float,
    pelo: Color,
    detalhe: Color,
) {
    val lado = if (paraDireita) 1f else -1f
    // Sobe e desce meio pixel por passada: é o que faz o caminhar parecer caminhar
    // em vez de deslizar. Parado, some.
    val balanco = if (andando) sin(passo * 2f) * 0.8f * esc else 0f
    // Ao reagir, ele estica pra cima (olhando pro que aconteceu).
    val esticar = animacaoDeEvento * 3f * esc
    val traco = Stroke(width = 1.6f * esc)

    translate(0f, balanco - esticar) {
        // ---- cauda: sobe e ondula; ao reagir, levanta de vez ----
        val cauda = Path().apply {
            moveTo(-11f * esc * lado, -6f * esc)
            val altura = if (animacaoDeEvento > 0f) -20f else -12f
            cubicTo(
                -19f * esc * lado, -8f * esc,
                -20f * esc * lado, altura * esc + sin(passo) * 2f * esc,
                -14f * esc * lado, (altura - 4f) * esc,
            )
        }
        drawPath(cauda, pelo, style = traco)

        // ---- corpo ----
        val corpo = Path().apply {
            moveTo(-12f * esc * lado, -5f * esc)
            cubicTo(
                -13f * esc * lado, -12f * esc,
                2f * esc * lado, -13f * esc,
                4f * esc * lado, -7f * esc,
            )
            lineTo(4f * esc * lado, -1f * esc)
            lineTo(-12f * esc * lado, -1f * esc)
            close()
        }
        drawPath(corpo, pelo.copy(alpha = 0.22f))
        drawPath(corpo, pelo, style = traco)

        // ---- patas: alternam quando anda ----
        val passoFrente = if (andando) sin(passo) * 2.4f * esc else 0f
        val passoTras = if (andando) sin(passo + 3.14f) * 2.4f * esc else 0f
        for ((baseX, desloc) in listOf(
            (2f * esc * lado) to passoFrente,
            (-2f * esc * lado) to passoTras,
            (-8f * esc * lado) to passoFrente,
            (-11f * esc * lado) to passoTras,
        )) {
            drawLine(
                pelo,
                Offset(baseX, -1f * esc),
                Offset(baseX + desloc, 3f * esc),
                strokeWidth = 1.5f * esc,
            )
        }

        // ---- cabeça ----
        val cabecaX = 8f * esc * lado
        val cabecaY = -11f * esc
        drawCircle(pelo.copy(alpha = 0.22f), radius = 5.2f * esc, center = Offset(cabecaX, cabecaY))
        drawCircle(pelo, radius = 5.2f * esc, center = Offset(cabecaX, cabecaY), style = traco)

        // orelhas: dois triângulos. Ao reagir elas se levantam (o gato "escutou").
        val perk = animacaoDeEvento * 1.4f * esc
        for (dir in listOf(-1f, 1f)) {
            val ox = cabecaX + dir * 3.2f * esc
            val orelha = Path().apply {
                moveTo(ox - 2f * esc, cabecaY - 3.4f * esc)
                lineTo(ox + 0.4f * esc * dir, cabecaY - 7.4f * esc - perk)
                lineTo(ox + 2f * esc, cabecaY - 3.4f * esc)
                close()
            }
            drawPath(orelha, pelo.copy(alpha = 0.22f))
            drawPath(orelha, pelo, style = traco)
        }

        // olhos: o accent do tema entra AQUI e em nenhum outro lugar do bicho —
        // pouca área, muito significado. É o que faz o gato parecer vivo, e é a
        // regra 60-30-10 aplicada a um desenho de 34dp.
        val olhoY = cabecaY - 0.6f * esc
        for (dir in listOf(-1f, 1f)) {
            val ox = cabecaX + dir * 2f * esc
            if (olhoFechado) {
                drawLine(
                    detalhe,
                    Offset(ox - 1.1f * esc, olhoY),
                    Offset(ox + 1.1f * esc, olhoY),
                    strokeWidth = 1.2f * esc,
                )
            } else {
                drawCircle(detalhe, radius = 1.15f * esc, center = Offset(ox, olhoY))
            }
        }

        // focinho
        drawLine(
            pelo,
            Offset(cabecaX + 3.4f * esc * lado, cabecaY + 2f * esc),
            Offset(cabecaX + 5.2f * esc * lado, cabecaY + 2.6f * esc),
            strokeWidth = 1.2f * esc,
        )

        // ---- faíscas do evento: some junto com a reação ----
        if (animacaoDeEvento > 0f) {
            val a = (animacaoDeEvento).coerceIn(0f, 1f)
            for (i in 0 until 3) {
                val ang = (i * 2.4f) + animacaoDeEvento * 2f
                val r = (7f + i * 2.5f) * esc
                drawCircle(
                    detalhe.copy(alpha = a * 0.75f),
                    radius = 0.9f * esc,
                    center = Offset(
                        cabecaX + cos(ang) * r,
                        cabecaY - 8f * esc + sin(ang) * r * 0.5f - abs(sin(ang)) * 2f * esc,
                    ),
                )
            }
        }
    }
}
