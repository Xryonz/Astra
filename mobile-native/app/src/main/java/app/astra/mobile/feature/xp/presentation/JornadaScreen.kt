package app.astra.mobile.feature.xp.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.astra.mobile.core.network.dto.ItemMissaoDto
import app.astra.mobile.core.network.dto.PainelMissoesDto
import app.astra.mobile.core.network.dto.ProgressoDto
import app.astra.mobile.core.xp.quantasProntas
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.components.CosmicBackground
import app.astra.mobile.ui.components.CosmicSpinner
import app.astra.mobile.ui.components.EditorialTopBar
import app.astra.mobile.ui.components.MarginaliaLabel
import app.astra.mobile.ui.theme.DmMono
import app.astra.mobile.ui.theme.EaseOutSoft
import app.astra.mobile.ui.theme.astraColors
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Medal
import com.composables.icons.lucide.Star
import kotlinx.coroutines.delay

@Composable
fun JornadaScreen(
    onBack: () -> Unit,
    viewModel: JornadaViewModel = hiltViewModel(),
) {
    val painel by viewModel.painel.collectAsState()
    val progresso by viewModel.progresso.collectAsState()
    val resgatando by viewModel.resgatando.collectAsState()

    CosmicBackground {
        Column(Modifier.fillMaxSize()) {
            EditorialTopBar(title = "Sua jornada", marginalia = "missões e brilho", onBack = onBack)
            val p = painel
            if (p == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CosmicSpinner() }
                return@Column
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
            ) {
                CartaoDoBrilho(progresso)

                val prontas = p.quantasProntas()
                if (prontas > 0) {
                    Spacer(Modifier.height(14.dp))
                    FaixaDeResgate(prontas, resgatando, viewModel::resgatarTudo)
                }

                val agora = agoraQuePassa()
                Grupo("hoje", faltando(p.diarias.renovaEm, agora)) {
                    p.diarias.itens.forEach { LinhaDeMissao(it, false, viewModel::resgatar) }
                    if (p.diarias.bonus.id.isNotBlank()) LinhaDeMissao(p.diarias.bonus, false, viewModel::resgatar)
                }
                Grupo("esta semana", faltando(p.semanais.renovaEm, agora)) {
                    p.semanais.itens.forEach { LinhaDeMissao(it, false, viewModel::resgatar) }
                }
                Grupo("conquistas", "não expiram") {
                    p.conquistas.itens.forEach { LinhaDeMissao(it, true, viewModel::resgatar) }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun agoraQuePassa(): Long {
    var agora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            agora = System.currentTimeMillis()
        }
    }
    return agora
}

private fun faltando(renovaEm: Long, agora: Long): String {
    if (renovaEm <= 0) return ""
    val falta = renovaEm - agora
    if (falta <= 0) return "renovando"
    val minutos = falta / 60_000
    val horas = minutos / 60
    return when {
        horas >= 24 -> "renova em ${horas / 24}d"
        horas >= 1 -> "renova em ${horas}h"
        else -> "renova em ${minutos.coerceAtLeast(1)} min"
    }
}

@Composable
private fun CartaoDoBrilho(p: ProgressoDto) {
    val forma = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(forma)
            .background(astraColors.raised)
            .border(1.dp, astraColors.border, forma)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "nível ${p.nivel}",
                style = MaterialTheme.typography.titleMedium,
                color = astraColors.text1,
                modifier = Modifier.weight(1f),
            )
            Icon(Lucide.Star, contentDescription = "estrelas", tint = astraColors.accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text("${p.estrelas}", fontFamily = DmMono, fontSize = 13.sp, color = astraColors.text2)
        }
        Spacer(Modifier.height(10.dp))
        val cheio = if (p.paraOProximo <= 0) 0f else (p.noNivel.toFloat() / p.paraOProximo).coerceIn(0f, 1f)
        BarraDeProgresso({ cheio }, concluida = false, altura = 5.dp)
        Spacer(Modifier.height(8.dp))
        MarginaliaLabel("${p.noNivel} de ${p.paraOProximo} de brilho para o próximo nível")
    }
}

@Composable
private fun FaixaDeResgate(quantas: Int, ocupado: Boolean, aoResgatarTudo: () -> Unit) {
    val forma = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(astraColors.raised)
            .border(1.dp, astraColors.accentDim, forma)
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (quantas == 1) "1 missão esperando por você" else "$quantas missões esperando por você",
            style = MaterialTheme.typography.bodyMedium,
            color = astraColors.text1,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(astraColors.accent)
                .clickable(enabled = !ocupado, onClick = aoResgatarTudo)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (ocupado) "resgatando…" else "resgatar tudo",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = astraColors.textInv,
            )
        }
    }
}

@Composable
private fun Grupo(titulo: String, apoio: String, conteudo: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(22.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        MarginaliaLabel(titulo)
        if (apoio.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(apoio, fontFamily = DmMono, fontSize = 11.sp, color = astraColors.text3)
        }
    }
    Spacer(Modifier.height(8.dp))
    val forma = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(astraColors.raised)
            .border(1.dp, astraColors.border, forma)
            .padding(vertical = 4.dp),
        content = conteudo,
    )
}

@Composable
private fun LinhaDeMissao(m: ItemMissaoDto, conquista: Boolean, aoResgatar: (String) -> Unit) {
    val semMovimento = LocalAppPrefs.current.reduceMotion
    val fracao = remember(m.id) { Animatable(if (semMovimento) fracaoDe(m) else 0f) }
    LaunchedEffect(m.progresso, m.alvo) {
        if (semMovimento) fracao.snapTo(fracaoDe(m))
        else fracao.animateTo(fracaoDe(m), tween(480, easing = EaseOutSoft))
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp)
            .semantics { contentDescription = descricaoDaMissao(m) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Marcador(m.concluida, conquista)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    m.titulo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (m.concluida) astraColors.text3 else astraColors.text1,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!m.concluida) {
                    Spacer(Modifier.width(8.dp))
                    Text("${m.progresso}/${m.alvo}", fontFamily = DmMono, fontSize = 11.sp, color = astraColors.text3)
                }
            }
            Spacer(Modifier.height(7.dp))
            BarraDeProgresso({ fracao.value }, m.concluida)
        }
        Spacer(Modifier.width(12.dp))
        if (m.resgatavel) {
            Box(
                Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(astraColors.accent)
                    .clickable { aoResgatar(m.id) }
                    .padding(horizontal = 12.dp)
                    .semantics { contentDescription = "Resgatar ${m.titulo}" },
                contentAlignment = Alignment.Center,
            ) {
                Text("+${m.xp}", fontFamily = DmMono, fontSize = 12.sp, color = astraColors.textInv)
            }
        } else {
            Text(
                "+${m.xp}",
                fontFamily = DmMono,
                fontSize = 12.sp,
                color = if (m.concluida) astraColors.text3 else astraColors.accent,
            )
        }
    }
}

private fun fracaoDe(m: ItemMissaoDto): Float =
    if (m.alvo <= 0) 0f else (m.progresso.toFloat() / m.alvo).coerceIn(0f, 1f)

private fun descricaoDaMissao(m: ItemMissaoDto): String = buildString {
    append(m.titulo)
    append(", ")
    append(if (m.concluida) "concluída" else "${m.progresso} de ${m.alvo}")
    if (m.resgatavel) append(", pronta para resgatar")
}

@Composable
private fun BarraDeProgresso(fracao: () -> Float, concluida: Boolean, altura: Dp = 3.dp) {
    val trilho = astraColors.border
    val cheio = if (concluida) astraColors.accent.copy(alpha = 0.45f) else astraColors.accent
    Box(
        Modifier
            .fillMaxWidth()
            .height(altura)
            .drawBehind {
                drawRoundRect(color = trilho, size = size)
                val f = fracao().coerceIn(0f, 1f)
                if (f > 0f) {
                    drawRoundRect(color = cheio, size = size.copy(width = size.width * f))
                }
            },
    )
}

@Composable
private fun Marcador(concluida: Boolean, conquista: Boolean) {
    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
        when {
            conquista && concluida -> Box(
                Modifier.size(20.dp).clip(CircleShape).background(astraColors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Lucide.Medal, contentDescription = null, tint = astraColors.textInv, modifier = Modifier.size(13.dp))
            }
            conquista -> Icon(Lucide.Medal, contentDescription = null, tint = astraColors.borderMid, modifier = Modifier.size(17.dp))
            concluida -> Box(
                Modifier.size(18.dp).clip(CircleShape).background(astraColors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Lucide.Check, contentDescription = null, tint = astraColors.textInv, modifier = Modifier.size(11.dp))
            }
            else -> Box(Modifier.size(16.dp).clip(CircleShape).border(1.dp, astraColors.borderMid, CircleShape))
        }
    }
}
