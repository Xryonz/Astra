package app.astra.mobile.feature.profile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.feature.friends.domain.model.Presence
import app.astra.mobile.feature.profile.domain.model.MutualServer
import app.astra.mobile.feature.profile.domain.model.UserStatus
import app.astra.mobile.ui.components.AlcaDaFolha
import app.astra.mobile.ui.components.AstraButton
import app.astra.mobile.ui.components.AstraButtonVariant
import app.astra.mobile.ui.components.BotaoDeTexto
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.FolhaQueSobe
import app.astra.mobile.ui.components.rememberEstadoDaFolha
import app.astra.mobile.ui.theme.astraColors
import coil3.compose.AsyncImage

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
    val folha = rememberEstadoDaFolha(aoFechar)

    LaunchedEffect(Unit) {
        viewModel.conversa.collect { c -> folha.fechar { aoAbrirConversa(c) } }
    }

    FolhaQueSobe(folha, rotuloDoFundo = "Fechar perfil") {
        ConteudoDaFolha(
            estado = estado,
            initialName = viewModel.initialName,
            aoTentarDeNovo = viewModel::load,
            aoSussurrar = { viewModel.abrirConversa(chamar = false) },
            aoChamar = { viewModel.abrirConversa(chamar = true) },
            aoEditarPerfil = { folha.fechar(aoEditarPerfil) },
            aoAbrirOrbita = { id -> folha.fechar { aoAbrirOrbita(id) } },
        )
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
                    AlcaDaFolha(Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
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
