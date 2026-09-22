package app.astra.mobile.feature.profile.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.feature.friends.domain.model.Presence
import app.astra.mobile.feature.profile.domain.model.MutualServer
import app.astra.mobile.feature.profile.domain.model.UserStatus
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.components.AstraButton
import app.astra.mobile.ui.components.AstraButtonVariant
import app.astra.mobile.ui.components.BotaoDeTexto
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.theme.EaseOutSoft
import app.astra.mobile.ui.theme.astraColors
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

private const val SUBIDA_MS = 320
private const val DESCIDA_MS = 220
private const val FRACAO_QUE_FECHA = 0.25f
private const val VELOCIDADE_QUE_FECHA = 1_500f
private const val ESCURO_MAXIMO = 0.55f

private fun Presence.comoStatus(): UserStatus = when (this) {
    Presence.ONLINE -> UserStatus.ONLINE
    Presence.IDLE -> UserStatus.IDLE
    Presence.DND -> UserStatus.DND
    Presence.OFFLINE -> UserStatus.OFFLINE
}

@Composable
fun FolhaDePerfil(
    aoFechar: () -> Unit,
    aoEditarPerfil: () -> Unit,
    aoAbrirConversa: (ConversaAberta) -> Unit,
    aoAbrirOrbita: (String) -> Unit,
    viewModel: UserProfileViewModel = hiltViewModel(),
) {
    val estado by viewModel.state.collectAsState()
    val semMovimento = LocalAppPrefs.current.reduceMotion
    val escopo = rememberCoroutineScope()
    val janela = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect { janela?.setDimAmount(0f) }

    var altura by remember { mutableIntStateOf(0) }
    var y by remember { mutableFloatStateOf(Float.NaN) }
    var saindo by remember { mutableStateOf(false) }

    fun fechar(depois: () -> Unit = aoFechar) {
        if (saindo) return
        saindo = true
        escopo.launch {
            if (!semMovimento && altura > 0 && !y.isNaN()) {
                animate(y, altura.toFloat(), animationSpec = tween(DESCIDA_MS, easing = EaseOutSoft)) { v, _ -> y = v }
            }
            depois()
        }
    }

    fun assentar(velocidade: Float) {
        if (altura <= 0 || y.isNaN()) return
        if (y > altura * FRACAO_QUE_FECHA || velocidade > VELOCIDADE_QUE_FECHA) {
            fechar()
        } else {
            escopo.launch { animate(y, 0f, animationSpec = tween(DESCIDA_MS, easing = EaseOutSoft)) { v, _ -> y = v } }
        }
    }

    LaunchedEffect(altura) {
        if (altura <= 0 || !y.isNaN()) return@LaunchedEffect
        if (semMovimento) {
            y = 0f
        } else {
            y = altura.toFloat()
            animate(y, 0f, animationSpec = tween(SUBIDA_MS, easing = EaseOutSoft)) { v, _ -> y = v }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.conversa.collect { c -> fechar { aoAbrirConversa(c) } }
    }

    BackHandler { fechar() }

    val conexao = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f && !y.isNaN() && y > 0f) {
                    val novo = (y + available.y).coerceAtLeast(0f)
                    val usado = novo - y
                    y = novo
                    return Offset(0f, usado)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput && !y.isNaN()) {
                    y += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!y.isNaN() && y > 0f) {
                    assentar(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val visivel = if (altura <= 0 || y.isNaN()) 0f else (1f - y / altura).coerceIn(0f, 1f)
    val alturaMaxima = (LocalConfiguration.current.screenHeightDp * 0.9f).dp

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(astraColors.void.copy(alpha = ESCURO_MAXIMO * visivel))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { fechar() }
                .semantics { contentDescription = "Fechar perfil" },
        )
        val forma = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = alturaMaxima)
                .onSizeChanged { altura = it.height }
                .graphicsLayer { translationY = if (y.isNaN()) size.height else y }
                .clip(forma)
                .background(astraColors.base)
                .border(1.dp, astraColors.border, forma)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .nestedScroll(conexao)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            ConteudoDaFolha(
                estado = estado,
                initialName = viewModel.initialName,
                aoTentarDeNovo = viewModel::load,
                aoSussurrar = { viewModel.abrirConversa(chamar = false) },
                aoChamar = { viewModel.abrirConversa(chamar = true) },
                aoEditarPerfil = { fechar(aoEditarPerfil) },
                aoAbrirOrbita = { id -> fechar { aoAbrirOrbita(id) } },
            )
        }
    }
}

@Composable
private fun ConteudoDaFolha(
    estado: UserProfileUiState,
    initialName: String,
    aoTentarDeNovo: () -> Unit,
    aoSussurrar: () -> Unit,
    aoChamar: () -> Unit,
    aoEditarPerfil: () -> Unit,
    aoAbrirOrbita: (String) -> Unit,
) {
    val v = estado.view
    if (v == null) {
        Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
            if (estado.loading) {
                CosmicSpinner()
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        estado.error ?: "Não foi possível abrir o perfil de $initialName.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = astraColors.text2,
                        textAlign = TextAlign.Center,
                    )
                    BotaoDeTexto(onClick = aoTentarDeNovo) { Text("Tentar de novo", color = astraColors.accent) }
                }
            }
        }
        return
    }
    val p = v.profile
    FundoDoTema(p.profileTheme) {
        Column {
            CabecaDoPerfil(
                p = PerfilVisivel(
                    nome = p.displayName.ifBlank { p.username },
                    usuario = p.username,
                    avatar = p.avatarUrl,
                    banner = p.bannerUrl,
                    corDoBanner = p.bannerColor,
                    bannerY = p.bannerPositionY,
                    bannerEscala = p.bannerScale,
                    fonte = p.displayFont,
                    pronomes = p.pronouns,
                    recado = p.customStatus,
                    status = v.presence.comoStatus(),
                    emblemas = estado.badges,
                ),
                alturaDoBanner = 104.dp,
                fundoDoAnel = astraColors.base,
                sobreOBanner = {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(astraColors.text3.copy(alpha = 0.6f)),
                    )
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (estado.souEu) {
                    AstraButton(text = "Editar perfil", onClick = aoEditarPerfil, modifier = Modifier.weight(1f))
                } else {
                    AstraButton(
                        text = "Sussurrar",
                        onClick = aoSussurrar,
                        loading = estado.abrindoConversa,
                        modifier = Modifier.weight(1f),
                    )
                    AstraButton(
                        text = "Chamar",
                        onClick = aoChamar,
                        enabled = !estado.abrindoConversa,
                        variant = AstraButtonVariant.Ghost,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            estado.erroDaConversa?.let { erro ->
                Text(
                    erro,
                    style = MaterialTheme.typography.bodySmall,
                    color = astraColors.danger,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                )
            }

            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                p.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                    CartaoDeSecao("Bio") {
                        Text(bio, style = MaterialTheme.typography.bodyMedium, color = astraColors.text1)
                    }
                }
                if (v.mutual.isNotEmpty()) {
                    CartaoDeSecao("Em comum") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            v.mutual.take(8).forEach { orbita -> IconeEmComum(orbita) { aoAbrirOrbita(orbita.id) } }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun IconeEmComum(orbita: MutualServer, aoTocar: () -> Unit) {
    val forma = RoundedCornerShape(10.dp)
    var semImagem by remember(orbita.iconUrl) { mutableStateOf(orbita.iconUrl == null) }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(forma)
            .background(astraColors.overlay)
            .border(1.dp, astraColors.border, forma)
            .clickable(onClick = aoTocar)
            .semantics { contentDescription = "Abrir ${orbita.name}" },
        contentAlignment = Alignment.Center,
    ) {
        if (!semImagem) {
            AsyncImage(
                model = orbita.iconUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { semImagem = true },
                modifier = Modifier.size(40.dp),
            )
        } else {
            Text(
                orbita.name.take(2).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = astraColors.text2,
            )
        }
    }
}
