package app.astra.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.EaseOutStd
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.ui.theme.Tipo
import app.astra.mobile.core.network.ServerApi
import app.astra.mobile.core.network.dto.MyColorRequest
import app.astra.mobile.core.network.dto.ProfileUserDto
import app.astra.mobile.core.network.dto.ServerDto
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

private val PREDEFINIDAS = listOf(
    "#c9a96e", "#9b7ac4", "#6aaeca", "#ca7a9b", "#6ec99b",
    "#e07a7a", "#7ac4c4", "#c4c47a", "#c47aaa", "#7ac4a0",
)

private enum class Movimento(val rotulo: String) {
    NENHUM("nenhum"),
    ARCO_IRIS("arco-íris"),
    VARREDURA("varredura"),
    PULSO("pulso"),
}

@Composable
internal fun CoresDoNomeSection(me: ProfileUserDto?) {
    val api = remember { GlobalContext.get().get<ServerApi>() }
    var constelacoes by remember { mutableStateOf<List<ServerDto>?>(null) }
    val atuais = remember { mutableStateMapOf<String, String?>() }
    var aberta by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        constelacoes = runCatching { api.servers().data.orEmpty() }.getOrNull() ?: emptyList()
    }

    Text(
        "sua cor vale por constelação — o mesmo nome pode ter cores diferentes em cada uma.",
        style = Tipo.apoio,
        modifier = Modifier.widthIn(max = 460.dp),
    )
    Spacer(Modifier.height(14.dp))

    val lista = constelacoes
    when {
        lista == null -> Text("carregando…", style = Tipo.descricao)
        lista.isEmpty() -> Text("você ainda não está em nenhuma constelação.", style = Tipo.descricao)
        else -> Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            lista.filterNot { it.isGroup }.forEach { srv ->
                LinhaDeConstelacao(
                    servidor = srv,
                    me = me,
                    corAtual = atuais[srv.id],
                    expandida = aberta == srv.id,
                    aoAlternar = { aberta = if (aberta == srv.id) null else srv.id },
                    aoDescobrir = { atuais[srv.id] = it },
                    aoSalvar = { atuais[srv.id] = it },
                )
            }
        }
    }
}

@Composable
private fun LinhaDeConstelacao(
    servidor: ServerDto,
    me: ProfileUserDto?,
    corAtual: String?,
    expandida: Boolean,
    aoAlternar: () -> Unit,
    aoDescobrir: (String?) -> Unit,
    aoSalvar: (String?) -> Unit,
) {
    val api = remember { GlobalContext.get().get<ServerApi>() }
    val scope = rememberCoroutineScope()
    var salvando by remember(servidor.id) { mutableStateOf(false) }
    var erro by remember(servidor.id) { mutableStateOf<String?>(null) }
    var rascunho by remember(servidor.id) { mutableStateOf<String?>(null) }
    var segundaCor by remember(servidor.id) { mutableStateOf<String?>(null) }
    var movimento by remember(servidor.id) { mutableStateOf(Movimento.NENHUM) }

    LaunchedEffect(servidor.id) {
        val vista = runCatching { api.myPerms(servidor.id).data?.nameColor }.getOrNull()
        aoDescobrir(vista)
    }

    val salva = remember(corAtual) { lerCorDoNome(corAtual) }
    LaunchedEffect(salva) {
        if (rascunho != null || salva == null) return@LaunchedEffect
        rascunho = emHex(salva.solida)
        segundaCor = when (salva) {
            is CorDoNome.Degrade -> emHex(salva.fim)
            is CorDoNome.Animada.Varredura -> emHex(salva.fim)
            else -> null
        }
        movimento = when (salva) {
            is CorDoNome.Animada.ArcoIris -> Movimento.ARCO_IRIS
            is CorDoNome.Animada.Varredura -> Movimento.VARREDURA
            is CorDoNome.Animada.Pulso -> Movimento.PULSO
            else -> Movimento.NENHUM
        }
    }

    val escolhida = rascunho
    val faltaSegunda = movimento == Movimento.VARREDURA && segundaCor == null
    val montada = when {
        escolhida == null || faltaSegunda -> null
        movimento == Movimento.ARCO_IRIS -> "anim:arcoiris:$escolhida"
        movimento == Movimento.PULSO -> "anim:pulso:$escolhida"
        movimento == Movimento.VARREDURA -> "anim:varredura:$escolhida:$segundaCor"
        segundaCor != null -> "gradient:0:$escolhida:$segundaCor"
        else -> escolhida
    }
    val amostra = lerCorDoNome(montada)

    val interacao = remember { MutableInteractionSource() }
    val hover by interacao.collectIsHoveredAsState()

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Obsidian.raised.copy(alpha = if (hover || expandida) 0.75f else 0.5f))
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickScale(interacao)
                .hoverable(interacao)
                .clickable(interactionSource = interacao, indication = null, onClick = aoAlternar)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DesktopAvatar(servidor.iconUrl, servidor.name, 26)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    servidor.name,
                    style = Tipo.corpo,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                NomeColorido(
                    texto = me?.displayName ?: me?.username ?: "seu nome",
                    cor = lerCorDoNome(corAtual),
                    padrao = Obsidian.text3,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            LIcon(
                Lucide.ChevronDown,
                tint = Obsidian.text3,
                size = 15.dp,
                rotulo = if (expandida) "fechar ${servidor.name}" else "escolher cor em ${servidor.name}",
            )
        }

        AnimatedVisibility(
            visible = expandida,
            enter = fadeIn(tween(140, easing = EaseOutStd)) +
                slideInVertically(tween(140, easing = EaseOutStd)) { -it / 4 },
        ) {
            Column(Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp)) {
                FieldLabel("cor")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PREDEFINIDAS.forEach { hex ->
                        Amostra(hex, selecionada = escolhida == hex) { rascunho = hex; erro = null }
                    }
                }

                Spacer(Modifier.height(12.dp))
                FieldLabel("segunda cor, para degradê")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Amostra(null, selecionada = segundaCor == null) { segundaCor = null }
                    PREDEFINIDAS.forEach { hex ->
                        Amostra(hex, selecionada = segundaCor == hex) { segundaCor = hex; erro = null }
                    }
                }

                Spacer(Modifier.height(12.dp))
                FieldLabel("movimento, ao passar o cursor")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Movimento.entries.forEach { m ->
                        PastilhaDeMovimento(m.rotulo, selecionada = movimento == m) {
                            movimento = m; erro = null
                        }
                    }
                }

                if (amostra != null) {
                    Spacer(Modifier.height(12.dp))
                    NomeColorido(
                        texto = me?.displayName ?: me?.username ?: "seu nome",
                        cor = amostra,
                        padrao = Obsidian.text1,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (movimento != Movimento.NENHUM) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "o movimento só acontece sob o cursor — em repouso, e no celular, o nome fica na cor parada.",
                            style = Tipo.apoio,
                            modifier = Modifier.widthIn(max = 380.dp),
                        )
                    }
                }

                if (faltaSegunda) {
                    Spacer(Modifier.height(8.dp))
                    Text("a varredura precisa de uma segunda cor.", style = Tipo.apoio)
                }

                erro?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = Tipo.erro)
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DialogButton("aplicar", accent = true, habilitado = montada != null && !salvando) {
                        salvando = true; erro = null
                        scope.launch {
                            val r = runCatching { api.setMyColor(servidor.id, MyColorRequest(montada)).data }
                            salvando = false
                            if (r.isSuccess) {
                                aoSalvar(r.getOrNull()?.nameColor)
                                rascunho = null
                                segundaCor = null
                                movimento = Movimento.NENHUM
                            } else {
                                erro = "Não foi possível aplicar essa cor"
                            }
                        }
                    }
                    DialogButton("voltar ao padrão", accent = false, habilitado = !salvando) {
                        salvando = true; erro = null
                        scope.launch {
                            val r = runCatching { api.setMyColor(servidor.id, MyColorRequest(null)).data }
                            salvando = false
                            if (r.isSuccess) {
                                aoSalvar(null)
                                rascunho = null
                                segundaCor = null
                                movimento = Movimento.NENHUM
                            } else {
                                erro = "Não foi possível limpar a cor"
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PastilhaDeMovimento(rotulo: String, selecionada: Boolean, aoTocar: () -> Unit) {
    val interacao = remember { MutableInteractionSource() }
    val hover by interacao.collectIsHoveredAsState()
    Box(
        Modifier
            .clickScale(interacao, formaDoFoco = RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    selecionada -> Obsidian.active
                    hover -> Obsidian.hover
                    else -> Color.Transparent
                },
            )
            .border(
                1.dp,
                if (selecionada) Obsidian.accent else Obsidian.borderDim,
                RoundedCornerShape(8.dp),
            )
            .hoverable(interacao)
            .clickable(interactionSource = interacao, indication = null, onClick = aoTocar)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            rotulo,
            style = TextStyle(
                color = if (selecionada) Obsidian.text1 else Obsidian.text3,
                fontSize = 11.sp,
            ),
        )
    }
}

@Composable
private fun Amostra(hex: String?, selecionada: Boolean, aoTocar: () -> Unit) {
    val interacao = remember { MutableInteractionSource() }
    val cor = hex?.let { lerCorDoNome(it)?.solida }
    Box(
        Modifier
            .size(24.dp)
            .clickScale(interacao, formaDoFoco = CircleShape)
            .clip(CircleShape)
            .background(cor ?: Color.Transparent)
            .border(
                if (selecionada) 2.dp else 1.dp,
                if (selecionada) Obsidian.accent else Obsidian.borderMid,
                CircleShape,
            )
            .hoverable(interacao)
            .clickable(interactionSource = interacao, indication = null, onClick = aoTocar),
        contentAlignment = Alignment.Center,
    ) {
        if (hex == null) {
            Box(Modifier.width(10.dp).height(1.dp).background(Obsidian.text3))
        }
    }
}
