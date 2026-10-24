package app.astra.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.ui.theme.Tipo
import app.astra.desktop.voice.AparelhoDeAudio
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.dto.ChangePasswordRequest
import app.astra.mobile.core.network.dto.SetPasswordRequest
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

@Composable
internal fun DialogoDeSenha(hasPassword: Boolean, onClose: () -> Unit) {
    val koin = GlobalContext.get()
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    val podeSalvar = !busy && next.length >= 8 && next == confirm && (!hasPassword || current.isNotBlank())

    fun salvar() {
        if (!podeSalvar) return
        busy = true
        erro = null
        scope.launch {
            val r = runCatching {
                val api = koin.get<UserApi>()
                if (hasPassword) api.changePassword(ChangePasswordRequest(current, next))
                else api.setPassword(SetPasswordRequest(next))
            }
            busy = false
            if (r.isSuccess) onClose()
            else erro = if (hasPassword) "não foi possível trocar. Confira a senha atual." else "não foi possível salvar. Tente de novo."
        }
    }

    Dialogo(titulo = if (hasPassword) "trocar de senha" else "definir senha", aoFechar = onClose) { fechar ->
        val focoInicial = rememberFocoAoAbrir()
        Spacer(Modifier.height(8.dp))
        Text(
            if (hasPassword) "informe a senha atual e escolha a nova."
            else "escolha uma senha para entrar também por e-mail.",
            style = TextoDoDialogo,
        )
        Spacer(Modifier.height(16.dp))

        if (hasPassword) {
            RotuloDeCampo("senha atual")
            CampoDeDialogo(
                current,
                { current = it; erro = null },
                dica = "senha atual",
                aoEnviar = ::salvar,
                foco = focoInicial,
                segredo = true,
            )
            Spacer(Modifier.height(14.dp))
        }
        RotuloDeCampo("nova senha")
        CampoDeDialogo(
            next,
            { next = it; erro = null },
            dica = "nova senha",
            aoEnviar = ::salvar,
            foco = focoInicial.takeIf { !hasPassword },
            segredo = true,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "ao menos 8 caracteres.",
            style = Tipo.apoio,
        )
        Spacer(Modifier.height(14.dp))
        RotuloDeCampo("confirmar nova senha")
        CampoDeDialogo(
            confirm,
            { confirm = it; erro = null },
            dica = "confirmar nova senha",
            aoEnviar = ::salvar,
            segredo = true,
        )

        if (confirm.isNotEmpty() && confirm != next) {
            Spacer(Modifier.height(6.dp))
            RecadoDoDialogo("as duas não são iguais.")
        }
        erro?.let {
            Spacer(Modifier.height(10.dp))
            RecadoDoDialogo(it)
        }

        RodapeDoDialogo {
            BotaoDeDialogo("cancelar", TipoDeBotao.SECUNDARIO, onClick = fechar, ligado = !busy)
            BotaoDeDialogo(
                if (busy) "salvando…" else "salvar",
                TipoDeBotao.PRINCIPAL,
                onClick = ::salvar,
                ligado = podeSalvar,
            )
        }
    }
}

@Composable
internal fun PasswordField(placeholder: String, value: String, onChange: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = TextStyle(color = Obsidian.text3, fontSize = 13.sp))
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            textStyle = Tipo.corpo,
            cursorBrush = SolidColor(Obsidian.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun ReadRow(label: String, value: String) {
    Row(Modifier.widthIn(max = 360.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Tipo.descricao, modifier = Modifier.width(80.dp))
        Text(value, style = Tipo.corpo)
    }
}

@Composable
internal fun NavRow(icon: ImageVector, label: String, sub: String, active: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(
        if (active) Obsidian.active else if (hovered) Obsidian.hover else androidx.compose.ui.graphics.Color.Transparent,
        tween(120),
    )
    val border by animateColorAsState(
        when {
            active -> Obsidian.accent.copy(alpha = 0.45f)
            hovered -> Obsidian.borderMid
            else -> Obsidian.borderDim.copy(alpha = 0.55f)
        },
        tween(120),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clickScale(interaction)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LIcon(
            icon,
            tint = if (active || hovered) Obsidian.text1 else Obsidian.text3,
            size = 16.dp,
        )
        Spacer(Modifier.width(11.dp))
        Column {
            Text(
                label,
                style = TextStyle(
                    color = if (active || hovered) Obsidian.text1 else Obsidian.text2,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                ),
            )
            Text(sub, style = Tipo.nota)
        }
    }
}

@Composable
internal fun DeviceDropdown(
    devices: List<AparelhoDeAudio>,
    selected: String?,
    onPick: (String?) -> Unit,
) {
    val nomeAtual = devices.firstOrNull { it.id == selected }?.nome
    var open by remember { mutableStateOf(false) }
    val hov = remember { MutableInteractionSource() }
    val h by hov.collectIsHoveredAsState()
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(if (h) Obsidian.hover else Obsidian.raised)
                .border(
                    1.dp,
                    if (open) Obsidian.accent.copy(alpha = 0.55f) else Obsidian.borderDim,
                    RoundedCornerShape(9.dp),
                )
                .hoverable(hov)
                .clickable { open = !open }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                nomeAtual ?: "padrão do Windows",
                style = Tipo.corpo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            LIcon(Lucide.ChevronDown, tint = Obsidian.text3, size = 14.dp)
        }
        if (open) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, with(LocalDensity.current) { 52.dp.roundToPx() }),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .popupReveal()
                        .widthIn(min = 240.dp, max = 640.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Obsidian.overlay)
                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
                        .padding(4.dp),
                ) {
                    DeviceRow("padrão do Windows", selected == null) { onPick(null); open = false }
                    devices.forEach { d ->
                        DeviceRow(d.nome, selected == d.id) { onPick(d.id); open = false }
                    }
                    if (devices.isEmpty()) {
                        DeviceRow("procurando os aparelhos…", false) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(label: String, active: Boolean, onClick: () -> Unit) {
    val hov = remember { MutableInteractionSource() }
    val h by hov.collectIsHoveredAsState()
    Text(
        label,
        style = TextStyle(color = if (active) Obsidian.accent else Obsidian.text2, fontSize = 12.sp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(if (h) Obsidian.hover else Obsidian.overlay)
            .hoverable(hov)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
internal fun TituloExplicavel(titulo: String, explicacao: String) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    val cor by animateColorAsState(if (hov) Obsidian.accent else Obsidian.text3, tween(140))

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.hoverable(src),
        ) {
            Text(
                titulo,
                style = TextStyle(color = Obsidian.text1, fontSize = 17.sp, fontFamily = DmSerif),
            )
            Spacer(Modifier.width(7.dp))
            Box(Modifier.size(4.dp).clip(CircleShape).background(cor))
        }
        if (hov) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, 30),
                properties = PopupProperties(focusable = false),
            ) {
                Text(
                    explicacao,
                    style = TextStyle(color = Obsidian.text2, fontSize = 11.5.sp, lineHeight = 17.sp),
                    modifier = Modifier
                        .widthIn(max = 380.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Obsidian.overlay)
                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(9.dp))
                        .padding(horizontal = 13.dp, vertical = 11.dp),
                )
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
internal fun InfoNote(title: String, body: String) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(Obsidian.overlay.copy(alpha = 0.5f))
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(9.dp))
            .clickable { open = !open }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LIcon(Lucide.Info, tint = Obsidian.accent, size = 14.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = Tipo.rotulo,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (open) "−" else "+",
                style = TextStyle(color = Obsidian.text3, fontSize = 13.sp),
            )
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, lineHeight = 17.sp),
            )
        }
    }
}
