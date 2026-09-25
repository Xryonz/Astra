package app.astra.mobile.feature.profile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.core.data.QualidadeAoAssistir
import app.astra.mobile.core.data.QualidadeAoTransmitir
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.astraColors
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide

@Composable
fun VozScreen(
    onBack: () -> Unit,
    viewModel: PreferencesViewModel = hiltViewModel(),
) {
    val prefs = LocalAppPrefs.current

    CosmicBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            EditorialTopBar(title = "Voz", marginalia = "qualidade da imagem", onBack = onBack)

            Spacer(Modifier.height(8.dp))
            MarginaliaLabel("ao assistir a uma transmissão", Modifier.padding(start = 22.dp, bottom = 8.dp))
            CartaoDeEscolhas(
                opcoes = QualidadeAoAssistir.entries,
                escolhida = prefs.aoAssistir,
                titulo = { it.rotulo },
                apoio = { it.explicacao },
                aoEscolher = viewModel::setAoAssistir,
            )

            Spacer(Modifier.height(22.dp))
            MarginaliaLabel("ao transmitir a sua tela", Modifier.padding(start = 22.dp, bottom = 8.dp))
            CartaoDeEscolhas(
                opcoes = QualidadeAoTransmitir.entries,
                escolhida = prefs.aoTransmitir,
                titulo = { it.rotulo },
                apoio = { "cerca de ${it.kbps / 1_000} Mbps" },
                aoEscolher = viewModel::setAoTransmitir,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Quanto mais nítida, mais dados e bateria a transmissão consome.",
                style = MaterialTheme.typography.bodySmall,
                color = astraColors.text3,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun <T> CartaoDeEscolhas(
    opcoes: List<T>,
    escolhida: T,
    titulo: (T) -> String,
    apoio: (T) -> String,
    aoEscolher: (T) -> Unit,
) {
    val forma = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .clip(forma)
            .background(astraColors.raised)
            .border(1.dp, astraColors.border, forma)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        opcoes.forEach { opcao ->
            val esta = opcao == escolhida
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { aoEscolher(opcao) }
                    .semantics {
                        role = Role.RadioButton
                        selected = esta
                        contentDescription = titulo(opcao)
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        titulo(opcao),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (esta) astraColors.text1 else astraColors.text2,
                    )
                    MarginaliaLabel(apoio(opcao))
                }
                if (esta) {
                    Spacer(Modifier.width(12.dp))
                    Icon(Lucide.Check, contentDescription = null, tint = astraColors.accent, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
