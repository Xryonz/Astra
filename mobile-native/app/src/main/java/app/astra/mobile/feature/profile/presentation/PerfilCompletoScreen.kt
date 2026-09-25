package app.astra.mobile.feature.profile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.core.network.dto.ProgressoDto
import app.astra.mobile.feature.profile.domain.model.AmigoEmComum
import app.astra.mobile.feature.xp.presentation.BarraDeProgresso
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.components.AstraButton
import app.astra.mobile.ui.components.AstraButtonVariant
import app.astra.mobile.ui.components.BotaoDeTexto
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.astraColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PerfilCompletoScreen(
    aoFechar: () -> Unit,
    aoEditarPerfil: () -> Unit,
    aoAbrirConversa: (ConversaAberta) -> Unit,
    aoAbrirOrbita: (String) -> Unit,
    viewModel: UserProfileViewModel = hiltViewModel(),
) {
    val estado by viewModel.state.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.carregarProgresso()
        viewModel.conversa.collect(aoAbrirConversa)
    }

    val v = estado.view
    FundoDoTema(v?.profile?.profileTheme, Modifier.fillMaxSize().background(astraColors.void)) {
        if (v == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (estado.loading) {
                    CosmicSpinner()
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            estado.error ?: "Não foi possível abrir o perfil de ${viewModel.initialName}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = astraColors.text2,
                            textAlign = TextAlign.Center,
                        )
                        BotaoDeTexto(onClick = viewModel::load) { Text("Tentar de novo", color = astraColors.accent) }
                    }
                }
            }
            BotaoFechar(aoFechar, Modifier.statusBarsPadding().padding(12.dp))
            return@FundoDoTema
        }

        val p = v.profile
        val topo = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
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
                alturaDoBanner = 132.dp + topo,
                sobreOBanner = { BotaoFechar(aoFechar, Modifier.statusBarsPadding().padding(12.dp)) },
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
                        onClick = { viewModel.abrirConversa(chamar = false) },
                        loading = estado.abrindoConversa,
                        modifier = Modifier.weight(1f),
                    )
                    AstraButton(
                        text = "Chamar",
                        onClick = { viewModel.abrirConversa(chamar = true) },
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
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                p.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                    CartaoDeSecao("Sobre") {
                        Text(bio, style = MaterialTheme.typography.bodyMedium, color = astraColors.text1)
                    }
                }
                dataPorExtenso(p.createdAt)?.let { desde ->
                    CartaoDeSecao("Estrela desde") {
                        Text(desde, style = MaterialTheme.typography.bodyMedium, color = astraColors.text1)
                    }
                }
                if (v.rostosEmComum.isNotEmpty()) {
                    CartaoDeSecao("Amigos em comum · ${v.amigosEmComum}") {
                        RostosEmComum(v.rostosEmComum, v.amigosEmComum)
                    }
                }
                if (v.mutual.isNotEmpty()) {
                    CartaoDeSecao("Constelações em comum · ${v.mutual.size}") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            v.mutual.forEach { orbita -> IconeDaOrbita(orbita) { aoAbrirOrbita(orbita.id) } }
                        }
                    }
                }
                estado.progresso?.let { CartaoDoNivel(it) }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RostosEmComum(rostos: List<AmigoEmComum>, total: Int) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        rostos.forEach { amigo ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                AstraAvatar(amigo.avatarUrl, amigo.nome, size = 28)
                Spacer(Modifier.width(7.dp))
                Text(
                    amigo.nome,
                    style = MaterialTheme.typography.bodySmall,
                    color = astraColors.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val sobrando = total - rostos.size
        if (sobrando > 0) {
            Row(Modifier.height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "+$sobrando",
                    style = MaterialTheme.typography.bodySmall,
                    color = astraColors.text3,
                )
            }
        }
    }
}

@Composable
private fun CartaoDoNivel(p: ProgressoDto) {
    CartaoDeSecao("Nível ${p.nivel}") {
        val cheio = if (p.paraOProximo <= 0) 0f else (p.noNivel.toFloat() / p.paraOProximo).coerceIn(0f, 1f)
        Spacer(Modifier.height(2.dp))
        BarraDeProgresso({ cheio }, concluida = false, altura = 5.dp)
        Spacer(Modifier.height(6.dp))
        MarginaliaLabel("${p.noNivel} de ${p.paraOProximo} de brilho para o próximo nível")
    }
}
