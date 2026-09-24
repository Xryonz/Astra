package app.astra.mobile.feature.profile.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
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
import app.astra.mobile.feature.friends.domain.model.Presence
import app.astra.mobile.feature.profile.domain.model.UserStatus
import app.astra.mobile.ui.components.AlcaDaFolha
import app.astra.mobile.ui.components.AstraButton
import app.astra.mobile.ui.components.AstraButtonVariant
import app.astra.mobile.ui.components.BotaoDeTexto
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.FolhaQueSobe
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.components.rememberEstadoDaFolha
import app.astra.mobile.ui.theme.astraColors
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Users

fun Presence.comoStatus(): UserStatus = when (this) {
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
    aoVerCompleto: () -> Unit,
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
            aoVerCompleto = { folha.fechar(aoVerCompleto) },
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
    aoVerCompleto: () -> Unit,
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

            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LinhaDeVinculos(v.amigosEmComum, v.mutual.size)

                p.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                    CartaoDeSecao("Bio") {
                        Text(
                            bio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = astraColors.text1,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                mesPorExtenso(p.createdAt)?.let { desde ->
                    MarginaliaLabel("nas estrelas desde $desde")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                    )
                }
                AstraButton(
                    text = "Ver perfil completo",
                    onClick = aoVerCompleto,
                    variant = AstraButtonVariant.Ghost,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun LinhaDeVinculos(amigos: Int, orbitas: Int) {
    val partes = buildList {
        if (amigos > 0) add(if (amigos == 1) "1 amigo em comum" else "$amigos amigos em comum")
        if (orbitas > 0) add(if (orbitas == 1) "1 constelação em comum" else "$orbitas constelações em comum")
    }
    if (partes.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Lucide.Users, contentDescription = null, tint = astraColors.text3, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            partes.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = astraColors.text2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
