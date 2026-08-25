package app.astra.desktop.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.astra.desktop.prefs.AvisosDaConta
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.dto.AvisosDaContaDto
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

@Composable
internal fun BlocoDeAjustes(
    titulo: String,
    explicacao: String,
    conteudo: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Obsidian.raised.copy(alpha = 0.22f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            titulo.uppercase(),
            style = TextStyle(
                color = Obsidian.text2, fontSize = 10.sp,
                letterSpacing = 1.sp, fontWeight = FontWeight.Medium,
            ),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            explicacao,
            style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, lineHeight = 15.sp),
            modifier = Modifier.widthIn(max = 460.dp),
        )
        Spacer(Modifier.height(12.dp))
        conteudo()
    }
}

@Composable
internal fun AvisosDaContaBloco() {
    val avisos = remember { GlobalContext.get().get<AvisosDaConta>() }
    val estado by avisos.estado.collectAsState()
    val escopo = rememberCoroutineScope()
    var erro by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { avisos.carregar() }

    fun aplicar(novo: AvisosDaContaDto) {
        escopo.launch {
            erro = null
            avisos.salvar(novo).onFailure { erro = "não foi possível salvar — a mudança voltou atrás" }
        }
    }

    BlocoDeAjustes(
        "na sua conta, em todo lugar",
        "decidem se o aviso chega a existir. Desligar aqui apaga o aviso do sino, " +
            "do celular e do navegador junto — não é só nesta máquina.",
    ) {
        ToggleRow(
            "Menções", "quando alguém escreve o seu @",
            estado.mentions,
        ) { aplicar(estado.copy(mentions = it)) }
        ToggleRow(
            "Sussurros", "mensagem privada",
            estado.dms,
        ) { aplicar(estado.copy(dms = it)) }
        ToggleRow(
            "Reações", "quando reagem a algo que você escreveu",
            estado.reactions,
        ) { aplicar(estado.copy(reactions = it)) }
        ToggleRow(
            "Respostas", "quando respondem a sua mensagem",
            estado.replies,
        ) { aplicar(estado.copy(replies = it)) }
        ToggleRow(
            "Avisos no celular e no navegador",
            "o push. Não muda nada neste app, que recebe por conexão direta",
            estado.desktop,
        ) { aplicar(estado.copy(desktop = it)) }

        SettingsDivider()
        DescansoDaConta(estado) { aplicar(it) }

        erro?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = TextStyle(color = Obsidian.danger, fontSize = 12.sp))
        }
    }
}

@Composable
private fun DescansoDaConta(estado: AvisosDaContaDto, onMudar: (AvisosDaContaDto) -> Unit) {
    val inicio = estado.quietStart
    val fim = estado.quietEnd
    val ligado = inicio != null && fim != null

    Text("Horário de descanso", style = TextStyle(color = Obsidian.text1, fontSize = 13.sp))
    Spacer(Modifier.height(2.dp))
    Text(
        "o servidor deixa de empurrar aviso nessa faixa, e este app para de tocar.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
    )
    Spacer(Modifier.height(10.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("das", style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
        Spacer(Modifier.width(8.dp))
        SeletorDeHora(inicio ?: 23) { onMudar(estado.copy(quietStart = it, quietEnd = fim ?: 7)) }
        Spacer(Modifier.width(8.dp))
        Text("às", style = TextStyle(color = Obsidian.text3, fontSize = 12.sp))
        Spacer(Modifier.width(8.dp))
        SeletorDeHora(fim ?: 7) { onMudar(estado.copy(quietStart = inicio ?: 23, quietEnd = it)) }
        if (ligado) {
            Spacer(Modifier.width(12.dp))
            TextoClicavel("desligar") { onMudar(estado.copy(quietStart = null, quietEnd = null)) }
        }
    }

    Spacer(Modifier.height(10.dp))
    Text(
        frasesDoDescanso(inicio, fim),
        style = TextStyle(color = if (ligado) Obsidian.text2 else Obsidian.text3, fontSize = 11.sp, lineHeight = 16.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )
}

private fun frasesDoDescanso(inicio: Int?, fim: Int?): String = when {
    inicio == null || fim == null -> "desligado — todo aviso passa em qualquer hora."
    inicio == fim -> "em silêncio o dia inteiro."
    inicio > fim -> "em silêncio das ${inicio}h às ${fim}h — atravessa a meia-noite."
    else -> "em silêncio das ${inicio}h às ${fim}h."
}

@Composable
private fun SeletorDeHora(hora: Int, onEscolher: (Int) -> Unit) {
    var aberto by remember { mutableStateOf(false) }
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()

    Box {
        Text(
            "%02dh".format(hora),
            style = TextStyle(color = Obsidian.text1, fontSize = 12.sp, fontFamily = DmMono),
            modifier = Modifier
                .clickScale(src)
                .clip(RoundedCornerShape(7.dp))
                .background(if (hov) Obsidian.hover else Obsidian.void.copy(alpha = 0.55f))
                .hoverable(src)
                .clickable(interactionSource = src, indication = null) { aberto = !aberto }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        if (aberto) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, 34),
                onDismissRequest = { aberto = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Obsidian.overlay)
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for (linha in 0 until 4) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (col in 0 until 6) {
                                val h = linha * 6 + col
                                CelulaDeHora(h, h == hora) { onEscolher(h); aberto = false }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CelulaDeHora(hora: Int, ativa: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Text(
        "%02d".format(hora),
        style = TextStyle(
            color = if (ativa) Obsidian.textInv else Obsidian.text2,
            fontSize = 11.sp, fontFamily = DmMono,
        ),
        modifier = Modifier
            .width(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    ativa -> Obsidian.accent
                    hov -> Obsidian.hover
                    else -> Obsidian.raised.copy(alpha = 0.4f)
                },
            )
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(vertical = 7.dp),
    )
}

@Composable
private fun TextoClicavel(rotulo: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Text(
        rotulo,
        style = TextStyle(
            color = if (hov) Obsidian.text1 else Obsidian.text3,
            fontSize = 11.sp,
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}
