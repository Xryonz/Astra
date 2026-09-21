package app.astra.mobile.feature.profile.presentation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import app.astra.mobile.BuildConfig
import app.astra.mobile.core.update.CuidadorDeAtualizacao
import app.astra.mobile.core.update.EstadoDaVersao
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.components.AstraButton
import app.astra.mobile.ui.components.AstraButtonVariant
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.astraColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PISO_DA_BUSCA = 1_800L
private val ETAPAS_DA_BUSCA = listOf("Consultando o repositório…", "Comparando versões…")

@Composable
fun TelaSobre(onBack: () -> Unit) {
    val estado by CuidadorDeAtualizacao.estado.collectAsState()
    val contexto = LocalContext.current
    val navegador = LocalUriHandler.current
    val forma = RoundedCornerShape(8.dp)

    CosmicBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            EditorialTopBar(title = "Sobre", marginalia = "versão e atualizações", onBack = onBack)

            Spacer(Modifier.height(8.dp))
            MarginaliaLabel("versão", Modifier.padding(start = 22.dp, bottom = 8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .clip(forma)
                    .background(astraColors.raised)
                    .border(1.dp, astraColors.border, forma)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Astra para Android",
                    style = MaterialTheme.typography.titleMedium,
                    color = astraColors.text1,
                    modifier = Modifier.weight(1f),
                )
                Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodyMedium, color = astraColors.text2)
            }

            Spacer(Modifier.height(20.dp))
            MarginaliaLabel("atualizações", Modifier.padding(start = 22.dp, bottom = 8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .clip(forma)
                    .background(astraColors.raised)
                    .border(1.dp, astraColors.border, forma)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "O Astra procura versões novas quando você volta ao app, no máximo a cada 20 minutos. " +
                        "Você também pode procurar agora.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = astraColors.text2,
                )

                when (val e = estado) {
                    is EstadoDaVersao.EmDia -> Situacao(
                        if (e.conferidoEm == 0L) {
                            "Nenhuma consulta feita ainda."
                        } else {
                            "Você está na ${BuildConfig.VERSION_NAME}, a mais nova publicada. " +
                                "Conferido ${haQuantoTempo(e.conferidoEm)}."
                        },
                    )
                    EstadoDaVersao.SemConexao -> Situacao(
                        "Não foi possível falar com o GitHub. Verifique a conexão e tente de novo.",
                    )
                    is EstadoDaVersao.SemEspaco -> Situacao(
                        "Falta espaço no aparelho para baixar a ${e.versao}. Libere espaço e procure de novo.",
                    )
                    is EstadoDaVersao.Baixando -> {
                        Situacao("Baixando a ${e.versao}… ${(e.progresso * 100).toInt()}%")
                        BarraDeProgresso(e.progresso)
                    }
                    is EstadoDaVersao.Pronta -> {
                        Situacao("A ${e.versao} foi baixada.")
                        AstraButton(
                            text = "Instalar agora",
                            onClick = { CuidadorDeAtualizacao.instalarAgora(contexto) },
                        )
                        Text(
                            "O Astra fecha para trocar de versão. Depois, é só abrir de novo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = astraColors.text3,
                        )
                    }
                    is EstadoDaVersao.Interrompida -> {
                        Situacao(
                            e.motivo?.let { "A ${e.versao} não foi instalada: $it." }
                                ?: "A instalação automática da ${e.versao} falhou duas vezes.",
                        )
                        AstraButton(
                            text = "Abrir página da versão",
                            onClick = { runCatching { navegador.openUri(e.pagina) } },
                            variant = AstraButtonVariant.Ghost,
                        )
                    }
                }

                BotaoProcurarAtualizacoes { CuidadorDeAtualizacao.procurarAgora(contexto) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Situacao(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodyMedium, color = astraColors.text1)
}

@Composable
private fun BarraDeProgresso(progresso: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(astraColors.overlay),
    ) {
        Box(Modifier.fillMaxWidth(progresso.coerceIn(0f, 1f)).fillMaxHeight().background(astraColors.accent))
    }
}

@Composable
private fun BotaoProcurarAtualizacoes(procurar: suspend () -> Unit) {
    val escopo = rememberCoroutineScope()
    var procurando by remember { mutableStateOf(false) }
    var etapa by remember { mutableIntStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AstraButton(
            text = if (procurando) ETAPAS_DA_BUSCA[etapa] else "Procurar atualizações",
            variant = AstraButtonVariant.Ghost,
            onClick = {
                if (procurando) return@AstraButton
                procurando = true
                etapa = 0
                escopo.launch {
                    val comecou = System.currentTimeMillis()
                    val trabalho = launch { runCatching { procurar() } }
                    delay(PISO_DA_BUSCA / ETAPAS_DA_BUSCA.size)
                    etapa = 1
                    trabalho.join()
                    val resta = PISO_DA_BUSCA - (System.currentTimeMillis() - comecou)
                    if (resta > 0) delay(resta)
                    procurando = false
                }
            },
        )
        if (procurando) BarraDeVarredura()
    }
}

@Composable
private fun BarraDeVarredura() {
    val semMovimento = LocalAppPrefs.current.reduceMotion
    val cor = astraColors.accentDim
    val trilho = astraColors.border
    if (semMovimento) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(cor))
        return
    }
    val transicao = rememberInfiniteTransition(label = "varredura")
    val posicao by transicao.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing), RepeatMode.Restart),
        label = "posicao",
    )
    Canvas(Modifier.fillMaxWidth().height(2.dp)) {
        drawRect(color = trilho, size = size)
        val largura = size.width * 0.35f
        val x = posicao * (size.width + largura) - largura
        val inicio = x.coerceAtLeast(0f)
        drawRect(
            color = cor,
            topLeft = Offset(inicio, 0f),
            size = Size((x + largura).coerceAtMost(size.width) - inicio, size.height),
        )
    }
}

private fun haQuantoTempo(quando: Long): String {
    val minutos = (System.currentTimeMillis() - quando) / 60_000
    return when {
        minutos < 1L -> "agora mesmo"
        minutos < 60L -> "há $minutos min"
        else -> "há ${minutos / 60} h"
    }
}
