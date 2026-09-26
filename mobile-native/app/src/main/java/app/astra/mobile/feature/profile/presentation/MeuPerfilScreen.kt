package app.astra.mobile.feature.profile.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.astra.mobile.feature.friends.domain.model.Friend
import app.astra.mobile.feature.profile.domain.model.UserStatus
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.components.AstraButton
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.ItemDeMenu
import app.astra.mobile.ui.theme.astraColors
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATA_POR_EXTENSO = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR"))

fun dataPorExtenso(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return runCatching {
        OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(DATA_POR_EXTENSO)
    }.getOrElse { iso.take(10) }
}

private val OPCOES_DE_STATUS = listOf(
    "Disponível" to UserStatus.ONLINE,
    "Ausente" to UserStatus.IDLE,
    "Não perturbe" to UserStatus.DND,
    "Invisível" to UserStatus.INVISIBLE,
)

@Composable
fun MeuPerfilScreen(
    aoFechar: () -> Unit,
    aoEditar: () -> Unit,
    aoAbrirAmigos: () -> Unit,
    aoAbrirConfiguracoes: () -> Unit,
    viewModel: MeuPerfilViewModel = hiltViewModel(),
) {
    val estado by viewModel.state.collectAsState()
    LifecycleResumeEffect(Unit) {
        viewModel.recarregar()
        onPauseOrDispose { }
    }
    var menuDeStatus by remember { mutableStateOf(false) }
    val p = estado.perfil

    FundoDoTema(p?.profileTheme, Modifier.fillMaxSize().background(astraColors.void)) {
        if (p == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CosmicSpinner() }
            BotaoFechar(aoFechar, Modifier.statusBarsPadding().padding(12.dp))
            return@FundoDoTema
        }
        val topo = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Box {
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
                        status = p.status,
                        emblemas = estado.emblemas,
                    ),
                    alturaDoBanner = 132.dp + topo,
                    textoSemRecado = "Defina um recado",
                    aoTocarNaFoto = { menuDeStatus = true },
                    rotuloDoToqueNaFoto = "mudar seu estado",
                    aoTocarNoRecado = aoEditar,
                    sobreOBanner = { BotaoFechar(aoFechar, Modifier.statusBarsPadding().padding(12.dp)) },
                )
                DropdownMenu(
                    expanded = menuDeStatus,
                    onDismissRequest = { menuDeStatus = false },
                    offset = DpOffset(16.dp, 0.dp),
                    shape = RoundedCornerShape(16.dp),
                    containerColor = astraColors.overlay,
                    border = BorderStroke(1.dp, astraColors.border),
                ) {
                    OPCOES_DE_STATUS.forEach { (rotulo, status) ->
                        ItemDeMenu(
                            text = { Text(rotulo, color = astraColors.text1) },
                            onClick = { menuDeStatus = false; viewModel.trocarStatus(status) },
                        )
                    }
                }
            }

            AstraButton(
                text = "Editar perfil",
                onClick = aoEditar,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            )

            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                p.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                    CartaoDeSecao("Bio") {
                        Text(bio, style = MaterialTheme.typography.bodyMedium, color = astraColors.text1)
                    }
                }
                dataPorExtenso(p.createdAt)?.let { desde ->
                    CartaoDeSecao("Estrela desde") {
                        Text(desde, style = MaterialTheme.typography.bodyMedium, color = astraColors.text1)
                    }
                }
                CartaoDeAmigos(estado.amigos, aoAbrirAmigos)
            }
            Spacer(Modifier.height(120.dp))
        }

        BarraFlutuante(
            aoAbrirConfiguracoes = aoAbrirConfiguracoes,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun CartaoDeAmigos(amigos: List<Friend>, aoAbrir: () -> Unit) {
    CartaoDeSecao(titulo = null, aoTocar = aoAbrir) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (amigos.isEmpty()) "Amigos" else "Amigos · ${amigos.size}",
                style = MaterialTheme.typography.titleSmall,
                color = astraColors.text1,
                modifier = Modifier.weight(1f),
            )
            val rostos = amigos.take(5)
            Box(Modifier.width((28 + (rostos.size - 1).coerceAtLeast(0) * 20).dp)) {
                rostos.forEachIndexed { i, amigo ->
                    Box(
                        Modifier
                            .offset(x = (i * 20).dp)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(astraColors.raised)
                            .border(2.dp, astraColors.raised, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        AstraAvatar(amigo.avatarUrl, amigo.displayName, size = 26)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(Lucide.ChevronRight, contentDescription = null, tint = astraColors.text3, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun BarraFlutuante(aoAbrirConfiguracoes: () -> Unit, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .clip(forma)
            .background(astraColors.overlay)
            .border(1.dp, astraColors.borderMid, forma)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = aoAbrirConfiguracoes)
                .padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Lucide.Settings, contentDescription = null, tint = astraColors.text1, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text("Configurações", style = MaterialTheme.typography.labelMedium, color = astraColors.text1)
        }
    }
}
