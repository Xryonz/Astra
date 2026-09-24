package app.astra.mobile.feature.server.presentation

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.ui.components.AstraSwitch
import app.astra.mobile.ui.components.AuthErrorBox
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.astraColors
import zed.rainxch.rikkaui.components.ui.toast.LocalToastHostState
import zed.rainxch.rikkaui.components.ui.toast.ToastVariant

@Composable
fun BotDaOrbitaScreen(
    onBack: () -> Unit,
    viewModel: BotDaOrbitaViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val toast = LocalToastHostState.current
    LaunchedEffect(state.aviso) {
        state.aviso?.let {
            toast.show(it, variant = ToastVariant.Destructive)
            viewModel.esquecerAviso()
        }
    }

    val porCategoria = remember(state.comandos) { state.comandos.groupBy { it.categoria } }

    CosmicBackground {
        Column(Modifier.fillMaxSize()) {
            EditorialTopBar(
                title = "Bot",
                marginalia = "${state.comandos.size - state.desligados.size} de ${state.comandos.size} ligados",
                onBack = onBack,
            )

            when {
                state.carregando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CosmicSpinner() }
                state.erro != null -> Box(Modifier.padding(20.dp)) { AuthErrorBox(state.erro!!) }
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    item {
                        Text(
                            "Desligar um comando vale só nesta constelação. Quem chamar recebe um aviso curto em vez de silêncio.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = astraColors.text3,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                        )
                    }
                    porCategoria.forEach { (categoria, itens) ->
                        item(key = "titulo-$categoria") {
                            MarginaliaLabel(categoria.ifBlank { "comandos" }, Modifier.padding(start = 4.dp, bottom = 6.dp))
                        }
                        item(key = "grupo-$categoria") {
                            val forma = RoundedCornerShape(12.dp)
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(forma)
                                    .border(1.dp, astraColors.border, forma)
                                    .padding(vertical = 4.dp),
                            ) {
                                itens.forEach { comando ->
                                    LinhaDeComando(
                                        titulo = comando.rotulo,
                                        apoio = comando.descricao,
                                        ligado = comando.chave !in state.desligados,
                                        aoMudar = { viewModel.alternar(comando.chave, it) },
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun LinhaDeComando(
    titulo: String,
    apoio: String,
    ligado: Boolean,
    aoMudar: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { aoMudar(!ligado) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titulo, fontSize = 15.sp, color = astraColors.text1)
            if (apoio.isNotBlank()) Text(apoio, fontSize = 12.sp, color = astraColors.text3)
        }
        Spacer(Modifier.width(10.dp))
        AstraSwitch(checked = ligado, onCheckedChange = aoMudar)
    }
}
