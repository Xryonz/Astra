package app.astra.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.EaseOutSoft
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.dto.MemberRoleDto
import app.astra.mobile.core.network.dto.MutualServerDto
import app.astra.mobile.core.network.dto.ProfileUserDto
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.X
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// O CARTAO DE PERFIL — um so, duas variantes.
//
// POR QUE ISTO EXISTE: o cartao era desenhado em ARQUIVOS diferentes, e as copias
// divergiam. Primeiro foram duas (o popup e a previa das Configuracoes). Depois de
// unificar essas duas, sobrou a divergencia MAIOR: o "cartao completo" de verdade
// era o ProfilePage — outro composable, com secoes que o COMPLETO daqui nao tinha
// (sobre / membro / servidores em comum). Ou seja, a previa chamada "cartao
// completo" mostrava um cartao que ninguem via, e as duas previas saiam quase
// iguais entre si, porque a de cima era so a de baixo com numeros maiores.
//
// Agora o COMPLETO daqui E o cartao do perfil completo. O ProfilePage virou so a
// moldura (fundo escuro, animacao de entrada, rolagem) em volta deste desenho.
// A previa nao pode mentir porque e literalmente a mesma funcao.

enum class CardVariante {
    /** O que abre ao clicar num avatar: compacto, sem secoes. */
    NORMAL,

    /** O perfil completo: avatar grande, "sobre", "membro", "em comum". */
    COMPLETO,
}

// Largura natural do cartao completo. Estreito de proposito: com 440 ele ficava
// deitado — muita largura pra pouca altura — e cartao de pessoa e EM PE. Estreitar
// tambem empurra o texto pra baixo, o que alonga sozinho.
val LARGURA_CARTAO_COMPLETO = 330.dp

// OS DOIS CARTOES USAM A MESMA PROPORCAO DE BANNER, e isto nao e escolha de
// gosto — e obrigacao.
//
// O recorte e ASSADO na imagem que sobe (ImageCrop): o arquivo salvo JA tem a
// proporcao ProfileBannerAspect, e o `scale`/`positionY` ficam em 100/50. Uma
// faixa com outra proporcao nao tem como exibir esse arquivo direito: ou sobra
// tarja, ou corta. E o zoom nao salva, porque o problema e o FORMATO da caixa,
// nao o tamanho da imagem dentro dela.
//
// Eu tinha posto 2.6 aqui pra alongar o cartao completo no eixo Y. Funcionou
// pra altura e quebrou todo banner ja salvo — a troca nao vale. A altura do
// cartao completo vem do conteudo (nome maior, secoes), que e de onde ela
// deveria ter vindo desde o comeco.

// Largura do cartao compacto (o que abre ao clicar num avatar).
//
// IGUAL a do completo (pedido do dono). Antes eram 320 e 330 — dez pixels de
// diferenca que ninguem enxerga isolado, mas que apareciam justamente onde doem:
// os dois lado a lado na previa das Configuracoes, um levemente mais estreito que
// o outro sem motivo nenhum. Mesma pessoa, mesmo cartao, mesma largura.
val LARGURA_CARTAO_NORMAL = LARGURA_CARTAO_COMPLETO

// Os dados que o cartao desenha. Existe pra a previa poder montar um cartao a
// partir do RASCUNHO (campo a campo, ao vivo, antes de salvar) usando exatamente
// o mesmo caminho do cartao real.
data class DadosDoCartao(
    val nome: String,
    val username: String,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val bannerColor: String? = null,
    val bannerPositionY: Int = 50,
    val bannerScale: Int = 100,
    val pronomes: String? = null,
    val bio: String? = null,
    val statusEmoji: String? = null,
    val recado: String? = null,
    val fonte: String? = null,
    val status: String? = null,
    val criadoEm: String? = null,
)

fun ProfileUserDto.paraCartao() = DadosDoCartao(
    nome = displayName ?: username,
    username = username,
    avatarUrl = avatarUrl,
    bannerUrl = bannerUrl,
    bannerColor = bannerColor,
    bannerPositionY = bannerPositionY ?: 50,
    bannerScale = bannerScale ?: 100,
    pronomes = pronouns,
    bio = bio,
    statusEmoji = statusEmoji,
    recado = customStatus,
    fonte = displayFont,
    status = effectiveStatus,
    criadoEm = createdAt,
)

@Composable
fun ProfileCard(
    dados: DadosDoCartao,
    variante: CardVariante = CardVariante.NORMAL,
    modifier: Modifier = Modifier,
    servidoresEmComum: List<MutualServerDto> = emptyList(),
    // Quantos amigos voces tem em comum. So aparece no compacto, como texto.
    amigosEmComum: Int = 0,
    // Cargos da pessoa NESTA constelação. Vazio quando nao ha constelação em
    // maos (cartao aberto pelo chat) — a secao some junto.
    cargos: List<MemberRoleDto> = emptyList(),
    // Botao de fechar no canto do banner. null = sem botao (o popup pequeno e a
    // previa fecham por fora).
    aoFechar: (() -> Unit)? = null,
    // Fileira de acoes redondas no canto do banner (chamar, adicionar amigo…).
    // Fica ao lado do fechar, na mesma linha.
    acoesNoBanner: (@Composable RowScope.() -> Unit)? = null,
    // A previa desliga a coreografia: ela recompoe a cada tecla digitada, e a
    // cascata reiniciando a cada letra transformaria a previa num pisca-pisca.
    animar: Boolean = true,
    rodape: @Composable (() -> Unit)? = null,
) {
    val completo = variante == CardVariante.COMPLETO
    val recuoH = if (completo) 20.dp else 16.dp
    val aspectoBanner = ProfileBannerAspect

    Column(
        modifier
            .clip(RoundedCornerShape(if (completo) 16.dp else 12.dp))
            // Gradiente CONTINUO: uma peca so, do topo do banner ao pe do cartao.
            // O aspecto vai junto — o veu tem que comecar a escurecer onde a faixa
            // de VERDADE acaba, senao volta a aparecer a linha dura entre as duas.
            .profileCardBackdrop(dados.bannerColor, aspectoBanner)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(if (completo) 16.dp else 12.dp)),
    ) {
        Box {
            // css = null de proposito: quem pinta o gradiente e o cartao inteiro. Se
            // a faixa repintasse por conta, o gradiente "recomecaria" e o corte
            // apareceria.
            ProfileBanner(
                css = null,
                imageUrl = dados.bannerUrl,
                positionY = dados.bannerPositionY,
                scale = dados.bannerScale,
                fallback = bannerBackdrop(dados.bannerUrl),
                modifier = Modifier.fillMaxWidth().aspectRatio(aspectoBanner),
            )
            if (completo && animar) {
                BannerSweep(dados.username, Modifier.fillMaxWidth().aspectRatio(aspectoBanner))
            }
            if (aoFechar != null || acoesNoBanner != null) {
                Row(
                    Modifier.align(Alignment.TopEnd).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    acoesNoBanner?.invoke(this)
                    if (aoFechar != null) {
                        val fecharSrc = remember { MutableInteractionSource() }
                        Box(
                            Modifier
                                .clickScale(fecharSrc, formaDoFoco = CircleShape)
                                .clip(CircleShape)
                                .background(Obsidian.void.copy(alpha = 0.5f))
                                .clickable(interactionSource = fecharSrc, indication = null) { aoFechar() },
                            contentAlignment = Alignment.Center,
                        ) {
                            LIcon(Lucide.X, tint = Obsidian.text2, size = 15.dp, rotulo = "Fechar", modifier = Modifier.padding(5.dp))
                        }
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = recuoH)) {
            AvatarDoCartao(dados, completo, animar)
            Column(Modifier.offset(y = if (completo) (-24).dp else (-40).dp)) {
                if (completo) CorpoCompleto(dados, servidoresEmComum, animar, rodape)
                else CorpoCompacto(dados, amigosEmComum, servidoresEmComum, cargos, rodape)
                Spacer(Modifier.height(if (completo) 20.dp else 12.dp))
            }
        }
    }
}

// O avatar pisa no banner. No completo ele "pipoca" com overshoot depois que o
// cartao assenta — o unico momento bouncy do perfil, de proposito: um so chama
// atencao, varios viram brinquedo.
@Composable
private fun AvatarDoCartao(dados: DadosDoCartao, completo: Boolean, animar: Boolean) {
    // 99->88 e 72->64. A foto tinha crescido junto com o cartao e nao voltou a
    // encolher quando o cartao ficou mais estreito e mais alto: a 99 ela ocupava
    // quase um terco da largura, e um retrato desse tamanho rouba a atencao do
    // nome, que e o que a pessoa foi ali ler.
    //
    // O vao entre a foto e o nome NAO muda com isto: a caixa da foto continua no
    // fluxo com a altura dela e o texto vem logo depois, entao encolher a foto
    // sobe o bloco inteiro sem abrir buraco.
    val px = if (completo) 88 else 64
    val reduzir = LocalReduceMotion.current
    val pop = remember(dados.username, completo) {
        Animatable(if (reduzir || !completo || !animar) 1f else 0f)
    }
    LaunchedEffect(dados.username, completo) {
        if (pop.value < 1f) {
            delay(200)
            pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium))
        }
    }
    Box(
        Modifier
            // Metade do avatar pra fora e o que da a sensacao de "colado" sem
            // cobrir a faixa inteira.
            .offset(y = if (completo) (-42).dp else (-px / 2 - 4).dp)
            .graphicsLayer {
                alpha = pop.value.coerceIn(0f, 1f)
                val s = 0.6f + 0.4f * pop.value
                scaleX = s
                scaleY = s
            }
            // Anel do fundo do cartao ao redor do avatar: e o que separa a foto do
            // banner quando as duas sao claras.
            .clip(CircleShape)
            .background(Obsidian.raised)
            .padding(3.dp),
    ) {
        DesktopAvatar(dados.avatarUrl, dados.nome, px)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CorpoCompacto(
    dados: DadosDoCartao,
    amigosEmComum: Int,
    servidoresEmComum: List<MutualServerDto>,
    cargos: List<MemberRoleDto>,
    rodape: @Composable (() -> Unit)?,
) {
    NomeELinha(dados, tamanhoNome = 19, tamanhoPonto = 10)

    // O QUE VOCES TEM EM COMUM, numa linha so. No cartao completo isto vira duas
    // secoes com os icones das constelacoes; aqui e um resumo — a graca do cartao
    // pequeno e caber, e uma grade de icones aberta sobre a lista de membros e
    // exatamente a poluicao que o dono pediu pra evitar.
    val vinculos = buildList {
        if (amigosEmComum > 0) {
            add(if (amigosEmComum == 1) "1 amigo em comum" else "$amigosEmComum amigos em comum")
        }
        if (servidoresEmComum.isNotEmpty()) {
            add(
                if (servidoresEmComum.size == 1) "1 constelação em comum"
                else "${servidoresEmComum.size} constelações em comum",
            )
        }
    }
    if (vinculos.isNotEmpty()) {
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LIcon(Lucide.Users, tint = Obsidian.text3, size = 12.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                vinculos.joinToString(" · "),
                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (!dados.recado.isNullOrBlank() || !dados.statusEmoji.isNullOrBlank()) {
        Spacer(Modifier.height(9.dp))
        Text(recadoInteiro(dados), style = TextStyle(color = Obsidian.text2, fontSize = 12.sp))
    }
    if (!dados.bio.isNullOrBlank()) {
        Spacer(Modifier.height(10.dp))
        // Faixa propria pra bio: separa "quem e" de "o que escreveu" sem titulo.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Obsidian.void.copy(alpha = 0.38f))
                .padding(horizontal = 11.dp, vertical = 9.dp),
        ) {
            Text(
                dados.bio.orEmpty(),
                style = TextStyle(color = Obsidian.text2, fontSize = 12.sp, lineHeight = 17.sp),
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
        }
    }

    // CARGOS. So chegam quando o cartao abre pelo painel de membros — de dentro
    // do chat nao ha constelacao em maos, e cargo e por constelacao. Cartao sem a
    // secao e melhor que cartao com uma secao vazia dizendo "sem cargos".
    if (cargos.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Obsidian.void.copy(alpha = 0.38f))
                .padding(horizontal = 11.dp, vertical = 9.dp),
        ) {
            Text(
                "CARGOS",
                style = TextStyle(color = Obsidian.text3, fontSize = 9.sp, letterSpacing = 1.sp),
            )
            Spacer(Modifier.height(7.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                cargos.forEach { EtiquetaDeCargo(it) }
            }
        }
    }

    membroDesde(dados.criadoEm)?.let { desde ->
        Spacer(Modifier.height(9.dp))
        Text(
            "nas estrelas desde $desde",
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
            maxLines = 1,
        )
    }

    if (rodape != null) {
        Spacer(Modifier.height(12.dp))
        rodape()
    }
}

// Etiqueta de cargo: bolinha na cor do cargo + nome. A cor entra no PONTO, e nao
// no texto nem no fundo — cargo com cor viva viraria a coisa mais berrante do
// cartao, competindo com o nome, que e o que a pessoa foi ali ler.
@Composable
private fun EtiquetaDeCargo(cargo: MemberRoleDto) {
    val cor = memberRoleColor(cargo.color) ?: Obsidian.text3
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(cor))
        Spacer(Modifier.width(5.dp))
        Text(
            cargo.name,
            style = TextStyle(color = Obsidian.text2, fontSize = 11.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CorpoCompleto(
    dados: DadosDoCartao,
    servidoresEmComum: List<MutualServerDto>,
    animar: Boolean,
    rodape: @Composable (() -> Unit)?,
) {
    val chave = dados.username
    // Secoes entram em cascata (fade + subida, uma depois da outra).
    val cascata: @Composable (Int, @Composable () -> Unit) -> Unit = { i, conteudo ->
        if (animar) CascadeIn(i, chave, stepMs = 40L, startDelayMs = 220L) { conteudo() } else conteudo()
    }

    cascata(0) { NomeELinha(dados, tamanhoNome = 24, tamanhoPonto = 12, so = Parte.NOME) }
    cascata(1) { NomeELinha(dados, tamanhoNome = 24, tamanhoPonto = 12, so = Parte.ARROBA) }
    if (!dados.recado.isNullOrBlank() || !dados.statusEmoji.isNullOrBlank()) {
        Spacer(Modifier.height(12.dp))
        cascata(2) {
            Text(recadoInteiro(dados), style = TextStyle(color = Obsidian.text2, fontSize = 13.sp))
        }
    }
    if (!dados.bio.isNullOrBlank()) {
        Spacer(Modifier.height(14.dp))
        cascata(3) {
            Secao("sobre") {
                Text(dados.bio.orEmpty(), style = TextStyle(color = Obsidian.text2, fontSize = 13.sp, lineHeight = 19.sp))
            }
        }
    }
    membroDesde(dados.criadoEm)?.let { desde ->
        Spacer(Modifier.height(14.dp))
        cascata(4) {
            Secao("membro") {
                Text("nas estrelas desde $desde", style = TextStyle(color = Obsidian.text2, fontSize = 13.sp))
            }
        }
    }
    if (servidoresEmComum.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        cascata(5) {
            Secao("servidores em comum · ${servidoresEmComum.size}") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    servidoresEmComum.forEachIndexed { i, s -> MutualChip(s, if (animar) i else CHIP_STAGGER_MAX) }
                }
            }
        }
    }
    if (rodape != null) {
        Spacer(Modifier.height(18.dp))
        cascata(6) { rodape() }
    }
}

private enum class Parte { TUDO, NOME, ARROBA }

// Nome + bolinha de status, e a linha do @usuario com os pronomes. As duas juntas
// no compacto; separadas no completo, porque la cada uma entra num tempo.
@Composable
private fun NomeELinha(
    dados: DadosDoCartao,
    tamanhoNome: Int,
    tamanhoPonto: Int,
    so: Parte = Parte.TUDO,
) {
    if (so != Parte.ARROBA) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                dados.nome,
                style = TextStyle(
                    color = Obsidian.text1,
                    fontSize = tamanhoNome.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = profileFontFamily(dados.fonte),
                ),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(6.dp))
            StatusDot(
                status = userStatus(dados.status),
                size = tamanhoPonto.dp,
                cutoutColor = Obsidian.raised,
            )
        }
    }
    if (so != Parte.NOME) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "@${dados.username}",
                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, fontFamily = DmMono),
            )
            if (!dados.pronomes.isNullOrBlank()) {
                Text("  ·  ${dados.pronomes}", style = TextStyle(color = Obsidian.text3, fontSize = 11.sp))
            }
        }
    }
}

private fun recadoInteiro(dados: DadosDoCartao) = listOfNotNull(
    dados.statusEmoji?.ifBlank { null },
    dados.recado?.ifBlank { null },
).joinToString(" ")

// Cada secao do cartao virou um CARTAO, e nao mais um bloco antecedido por
// traco. Com tres ou quatro secoes seguidas, o traco em cima de cada uma
// desenhava uma grade — o olho lia tabela, nao perfil.
//
// `hover` e nao `raised`: o cartao de perfil ja mora num popup em `overlay`, e
// subir pra `raised` seria DESCER na rampa (raised vem antes de overlay). Este e
// o degrau seguinte de verdade.
@Composable
private fun Secao(titulo: String, conteudo: @Composable () -> Unit) {
    CartaoInterno(fundo = Obsidian.hover, padding = PaddingValues(horizontal = 11.dp, vertical = 9.dp)) {
        Text(
            titulo.uppercase(),
            style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(6.dp))
        conteudo()
    }
}

private const val CHIP_STAGGER_MAX = 10

@Composable
private fun MutualChip(s: MutualServerDto, index: Int) {
    val reduce = LocalReduceMotion.current
    // 2o nivel de stagger: cada chip escala/aparece um tico depois do anterior.
    // Escala (nao translateY) — o FlowRow quebra linha, subir por linha ficaria
    // torto.
    val pop = remember(s.id) { Animatable(if (reduce || index !in 0 until CHIP_STAGGER_MAX) 1f else 0f) }
    LaunchedEffect(s.id) {
        if (pop.value < 1f) {
            delay(index * 18L)
            pop.animateTo(1f, tween(180, easing = EaseOutStd))
        }
    }
    val src = remember { MutableInteractionSource() }
    Row(
        Modifier
            .graphicsLayer {
                alpha = pop.value
                val sc = 0.85f + 0.15f * pop.value
                scaleX = sc
                scaleY = sc
            }
            .clickScale(src)
            .clip(RoundedCornerShape(9.dp))
            .background(Obsidian.overlay)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(9.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(Obsidian.raised), contentAlignment = Alignment.Center) {
            if (!s.iconUrl.isNullOrBlank()) {
                DesktopAvatar(s.iconUrl, s.name, 22)
            } else {
                Text(s.name.take(1).uppercase(), style = TextStyle(color = Obsidian.accent, fontSize = 11.sp, fontFamily = DmSerif))
            }
        }
        Spacer(Modifier.width(7.dp))
        Text(
            s.name,
            style = TextStyle(color = Obsidian.text2, fontSize = 12.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(96.dp),
        )
    }
}

// Sweep de luz ambar diagonal que atravessa o banner UMA vez na abertura (tipo
// alvorecer). Sutil (pico alpha 0.15), GPU-only, um-shot. Reduzir movimento = some.
@Composable
private fun BannerSweep(seedKey: Any?, modifier: Modifier = Modifier) {
    if (LocalReduceMotion.current) return
    val sweep = remember(seedKey) { Animatable(0f) }
    LaunchedEffect(seedKey) {
        delay(90)
        sweep.animateTo(1f, tween(650, easing = EaseOutSoft))
    }
    Box(
        modifier
            .clipToBounds()
            .graphicsLayer {
                translationX = (sweep.value * 1.8f - 0.5f) * size.width
                rotationZ = -16f
            }
            .drawBehind {
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Obsidian.accent.copy(alpha = 0.15f), Color.Transparent),
                    ),
                    size = Size(size.width * 0.4f, size.height * 2f),
                )
            },
    )
}

// createdAt (ISO) -> "julho de 2026" (pt-BR). Falha silenciosa: sem data, sem linha.
private fun membroDesde(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return runCatching {
        val date = Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate()
        date.format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale("pt", "BR")))
    }.getOrNull()
}
