package app.astra.mobile.feature.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import app.astra.mobile.core.update.AvisoDeAtualizacao
import app.astra.mobile.core.update.CuidadorDeAtualizacao
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.components.AstraButton
import app.astra.mobile.ui.components.AstraButtonVariant
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.astraColors

@Composable
fun CartaoDeAtualizacao(modifier: Modifier = Modifier) {
    val aviso by CuidadorDeAtualizacao.aviso.collectAsState()
    var ultimo by remember { mutableStateOf(aviso) }
    LaunchedEffect(aviso) { if (aviso != null) ultimo = aviso }
    val mostrado = aviso ?: ultimo

    val contexto = LocalContext.current
    val navegador = LocalUriHandler.current
    val semMovimento = LocalAppPrefs.current.reduceMotion

    AnimatedVisibility(
        visible = aviso != null,
        modifier = modifier,
        enter = if (semMovimento) fadeIn(tween(120)) else fadeIn(tween(220)) + expandVertically(tween(220)),
        exit = if (semMovimento) fadeOut(tween(90)) else fadeOut(tween(160)) + shrinkVertically(tween(200)),
    ) {
        val (titulo, texto, acao) = when (val a = mostrado) {
            AvisoDeAtualizacao.PedirPermissao -> Triple(
                "Atualizações automáticas",
                "Libere a instalação para o Astra se manter atualizado sozinho.",
                "Liberar",
            )
            is AvisoDeAtualizacao.Pronta -> Triple(
                "Versão nova pronta",
                "A versão ${a.versao} já foi baixada. Instalar leva um toque.",
                "Instalar",
            )
            is AvisoDeAtualizacao.Falhou -> Triple(
                "Atualização interrompida",
                a.motivo?.let { "A versão ${a.versao} não foi instalada: $it. Baixe pela página da versão." }
                    ?: "A instalação automática da versão ${a.versao} falhou duas vezes. Baixe pela página da versão.",
                "Abrir página",
            )
            null -> return@AnimatedVisibility
        }

        val forma = RoundedCornerShape(8.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(forma)
                .background(astraColors.raised)
                .border(1.dp, astraColors.border, forma)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MarginaliaLabel("atualizações")
            Text(titulo, style = MaterialTheme.typography.titleMedium, color = astraColors.text1)
            Text(texto, style = MaterialTheme.typography.bodyMedium, color = astraColors.text2)
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AstraButton(
                    text = acao,
                    onClick = {
                        when (val a = mostrado) {
                            AvisoDeAtualizacao.PedirPermissao -> runCatching {
                                contexto.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:${contexto.packageName}"),
                                    ),
                                )
                            }
                            is AvisoDeAtualizacao.Pronta -> CuidadorDeAtualizacao.instalarAgora(contexto)
                            is AvisoDeAtualizacao.Falhou -> runCatching { navegador.openUri(a.pagina) }
                            null -> Unit
                        }
                    },
                )
                AstraButton(
                    text = "Agora não",
                    onClick = { CuidadorDeAtualizacao.adiar(contexto) },
                    variant = AstraButtonVariant.Ghost,
                )
            }
        }
    }
}
