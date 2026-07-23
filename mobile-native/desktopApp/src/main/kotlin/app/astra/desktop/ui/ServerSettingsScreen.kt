package app.astra.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.profile.AvatarPicker
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.DmSerif
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.dto.ServerDto
import app.astra.mobile.core.network.dto.UpdateServerRequest
import com.composables.icons.lucide.Ban
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Shield
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Configuracoes da CONSTELACAO — takeover no mesmo idioma do SettingsScreen (nav
// de 220dp a esquerda, coluna de conteudo capada em 720dp), pra as duas telas de
// configuracao se lerem como a mesma coisa.
//
// Fatia 1: so "Visao geral". Cargos e Banimentos entram depois — as abas ja estao
// no enum, marcadas, pra a navegacao nao mudar de forma quando chegarem.
internal enum class ServerTab(val label: String, val sub: String, val icon: ImageVector, val ready: Boolean) {
    OVERVIEW("Visao geral", "nome, imagens e convite", Lucide.Info, true),
    ROLES("Cargos", "em breve", Lucide.Shield, false),
    BANS("Banimentos", "em breve", Lucide.Ban, false),
}

@Composable
fun ServerSettingsScreen(
    server: ServerDto,
    isOwner: Boolean,
    onClose: () -> Unit,
    onSave: (UpdateServerRequest, (String?) -> Unit) -> Unit,
    onRegenerateInvite: ((String?) -> Unit) -> Unit,
    onDelete: () -> Unit,
    onLeave: () -> Unit,
) {
    var tab by remember { mutableStateOf(ServerTab.OVERVIEW) }

    // ESC fecha — mesmo contrato do SettingsScreen.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { onClose(); true } else false
            },
    ) {
        // Veu sobre o ceu da janela: a aurora continua viva por baixo, sem pintar
        // uma nova (mesmo motivo do SettingsScreen).
        Box(Modifier.matchParentSize().background(Obsidian.base.copy(alpha = 0.5f)))
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier.width(220.dp).fillMaxHeight().padding(horizontal = 12.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    server.name,
                    style = TextStyle(color = Obsidian.text1, fontSize = 18.sp, fontFamily = DmSerif),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp),
                )
                Text(
                    "constelacao",
                    style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
                    modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
                )
                ServerTab.entries.forEach { t ->
                    ServerNavRow(t, active = t == tab, onClick = { if (t.ready) tab = t })
                }
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                Column(
                    Modifier.align(Alignment.TopStart).widthIn(max = 720.dp).fillMaxWidth()
                        .fillMaxHeight().verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp, vertical = 22.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tab.label,
                            style = TextStyle(color = Obsidian.text1, fontSize = 26.sp, fontFamily = DmSerif),
                            modifier = Modifier.weight(1f),
                        )
                        val hov = remember { MutableInteractionSource() }
                        val h by hov.collectIsHoveredAsState()
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(if (h) Obsidian.hover else Obsidian.overlay)
                                .border(1.dp, Obsidian.borderMid, CircleShape)
                                .hoverable(hov)
                                .clickable(onClick = onClose),
                            contentAlignment = Alignment.Center,
                        ) {
                            LIcon(Lucide.X, tint = Obsidian.text2, size = 15.dp)
                        }
                    }
                    Spacer(Modifier.height(20.dp))

                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.98f))
                                .togetherWith(fadeOut(tween(120)))
                        },
                        label = "serverSection",
                    ) { current ->
                        // Column: sem ela o AnimatedContent empilha os filhos no
                        // mesmo Y (a mesma armadilha do SettingsScreen).
                        Column(Modifier.fillMaxWidth()) {
                            when (current) {
                                ServerTab.OVERVIEW -> OverviewSection(
                                    server, isOwner, onSave, onRegenerateInvite, onDelete, onLeave,
                                )
                                else -> Text(
                                    "em construcao",
                                    style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewSection(
    server: ServerDto,
    isOwner: Boolean,
    onSave: (UpdateServerRequest, (String?) -> Unit) -> Unit,
    onRegenerateInvite: ((String?) -> Unit) -> Unit,
    onDelete: () -> Unit,
    onLeave: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    // Rascunho rechaveado pelo servidor: salvar recarrega a lista e o valor novo
    // desce por aqui.
    var name by remember(server) { mutableStateOf(server.name) }
    var description by remember(server) { mutableStateOf(server.description.orEmpty()) }
    var iconUrl by remember(server) { mutableStateOf(server.iconUrl) }
    var bannerUrl by remember(server) { mutableStateOf(server.bannerUrl) }
    var isPublic by remember(server) { mutableStateOf(server.isPublic) }
    var retention by remember(server) { mutableStateOf(server.messageRetentionDays ?: 0) }

    var saving by remember { mutableStateOf(false) }
    var busyIcon by remember { mutableStateOf(false) }
    var busyBanner by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var confirmRegen by remember { mutableStateOf(false) }
    var confirmDanger by remember { mutableStateOf(false) }
    var regenerating by remember { mutableStateOf(false) }

    val dirty = name.trim() != server.name ||
        description.trim() != server.description.orEmpty().trim() ||
        iconUrl != server.iconUrl ||
        bannerUrl != server.bannerUrl ||
        isPublic != server.isPublic ||
        retention != (server.messageRetentionDays ?: 0)

    // ---- Identidade ----
    FieldLabel("icone")
    Row(verticalAlignment = Alignment.CenterVertically) {
        ServerIconPreview(iconUrl, name)
        Spacer(Modifier.width(16.dp))
        Column {
            SmallButton(if (busyIcon) "processando…" else "trocar icone", accent = true) {
                if (busyIcon) return@SmallButton
                val file = AvatarPicker.choose("Escolher icone") ?: return@SmallButton
                busyIcon = true
                msg = null
                scope.launch {
                    // Decodificar/reduzir e pesado -> fora da thread de UI.
                    val r = withContext(Dispatchers.IO) { AvatarPicker.encode(file) }
                    busyIcon = false
                    r.onSuccess { iconUrl = it }
                        .onFailure { msg = "nao deu pra ler essa imagem" to false }
                }
            }
            if (!iconUrl.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                SmallButton("remover", accent = false) { iconUrl = null }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "a imagem e reduzida pra 512px e vira parte da constelacao (maximo 5MB).",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )

    SettingsDivider()
    FieldLabel("nome")
    PlainField(name, "nome da constelacao") { name = it.take(100) }
    Spacer(Modifier.height(12.dp))
    FieldLabel("descricao")
    PlainField(description, "do que e essa constelacao?", multiline = true) { description = it.take(300) }

    SettingsDivider()
    FieldLabel("banner")
    ProfileBanner(
        css = null,
        imageUrl = bannerUrl,
        positionY = 50,
        scale = 100,
        fallback = Obsidian.overlay,
        modifier = Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp)),
    )
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallButton(if (busyBanner) "processando…" else "subir banner", accent = true) {
            if (busyBanner) return@SmallButton
            val file = AvatarPicker.choose("Escolher banner") ?: return@SmallButton
            busyBanner = true
            msg = null
            scope.launch {
                val r = withContext(Dispatchers.IO) { AvatarPicker.encode(file, AvatarPicker.BANNER_DIM) }
                busyBanner = false
                r.onSuccess { bannerUrl = it }
                    .onFailure { msg = "nao deu pra ler essa imagem" to false }
            }
        }
        if (!bannerUrl.isNullOrBlank()) {
            SmallButton("remover banner", accent = false) { bannerUrl = null }
        }
    }

    // ---- Convite ----
    SettingsDivider()
    FieldLabel("convite")
    Row(Modifier.widthIn(max = 460.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Obsidian.raised)
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                server.inviteCode ?: "sem convite",
                style = TextStyle(color = Obsidian.text1, fontSize = 13.sp, fontFamily = DmMono),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        server.inviteCode?.let { code ->
            SmallButton("copiar", accent = false) {
                clipboard.setText(AnnotatedString(code))
                msg = "convite copiado" to true
            }
            Spacer(Modifier.width(6.dp))
        }
        SmallButton(if (regenerating) "gerando…" else "regenerar", accent = true) {
            if (!regenerating) confirmRegen = true
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "regenerar invalida o convite atual — quem tiver o link antigo nao entra mais.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )
    if (confirmRegen) {
        ConfirmPopup(
            message = "gerar um convite novo? o link atual para de funcionar.",
            confirmLabel = "gerar novo",
            onConfirm = {
                confirmRegen = false
                regenerating = true
                msg = null
                onRegenerateInvite { err ->
                    regenerating = false
                    msg = (err ?: "convite novo gerado") to (err == null)
                }
            },
            onDismiss = { confirmRegen = false },
        )
    }

    // ---- Descoberta e retencao ----
    SettingsDivider()
    ToggleRow(
        "Constelacao publica",
        "aparece na Descoberta pra quem procura onde entrar",
        isPublic,
    ) { isPublic = it }

    Spacer(Modifier.height(14.dp))
    FieldLabel("apagar mensagens automaticamente")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RETENTION_OPTIONS.forEach { (days, label) ->
            ChoiceChip(label, selected = retention == days) { retention = days }
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        if (retention == 0) "as mensagens ficam pra sempre."
        else "mensagens com mais de $retention dia(s) somem sozinhas — nao da pra recuperar.",
        style = TextStyle(color = if (retention == 0) Obsidian.text3 else Obsidian.warning, fontSize = 11.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )

    // ---- Salvar ----
    Spacer(Modifier.height(22.dp))
    msg?.let { (text, ok) ->
        Text(
            text,
            style = TextStyle(color = if (ok) Obsidian.success else Obsidian.danger, fontSize = 12.sp),
            modifier = Modifier.widthIn(max = 460.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallButton(if (saving) "salvando…" else "salvar", accent = true) {
            if (saving || !dirty) return@SmallButton
            saving = true
            msg = null
            onSave(
                UpdateServerRequest(
                    name = name.trim().ifBlank { null },
                    iconUrl = iconUrl ?: "",
                    bannerUrl = bannerUrl ?: "",
                    description = description.trim(),
                    // 0 = "pra sempre"; o backend traduz 0 em null.
                    messageRetentionDays = retention,
                    isPublic = isPublic,
                ),
            ) { err ->
                saving = false
                msg = (err ?: "constelacao salva") to (err == null)
            }
        }
        if (dirty && !saving) {
            SmallButton("descartar", accent = false) {
                name = server.name
                description = server.description.orEmpty()
                iconUrl = server.iconUrl
                bannerUrl = server.bannerUrl
                isPublic = server.isPublic
                retention = server.messageRetentionDays ?: 0
                msg = null
            }
        }
    }
    if (!dirty && msg == null) {
        Spacer(Modifier.height(6.dp))
        Text("nada mudou ainda.", style = TextStyle(color = Obsidian.text3, fontSize = 11.sp))
    }

    // ---- Zona de perigo ----
    SettingsDivider()
    FieldLabel("zona de perigo")
    Text(
        if (isOwner) "excluir apaga a constelacao pra todo mundo. Nao da pra desfazer."
        else "sair remove teu acesso; pra voltar precisa de convite.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )
    Spacer(Modifier.height(10.dp))
    DangerButton(if (isOwner) "excluir constelacao" else "sair da constelacao") { confirmDanger = true }
    if (confirmDanger) {
        ConfirmPopup(
            message = if (isOwner) "excluir ${server.name}? apaga pra todos — nao da pra desfazer."
            else "sair de ${server.name}?",
            confirmLabel = if (isOwner) "excluir" else "sair",
            onConfirm = {
                confirmDanger = false
                if (isOwner) onDelete() else onLeave()
            },
            onDismiss = { confirmDanger = false },
        )
    }
    Spacer(Modifier.height(24.dp))
}

// 1 dia entra a pedido do dono: canais bem efemeros. O aviso em ambar ao lado
// deixa claro que e destrutivo e silencioso.
private val RETENTION_OPTIONS = listOf(
    0 to "nunca",
    1 to "1 dia",
    7 to "7 dias",
    30 to "30 dias",
    90 to "90 dias",
)

@Composable
private fun ServerNavRow(tab: ServerTab, active: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) Obsidian.active else if (hov) Obsidian.hover else androidx.compose.ui.graphics.Color.Transparent)
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LIcon(
            tab.icon,
            tint = when {
                !tab.ready -> Obsidian.text3
                active -> Obsidian.accent
                else -> Obsidian.text3
            },
            size = 15.dp,
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                tab.label,
                style = TextStyle(
                    // Aba nao pronta fica visivelmente apagada: mostra pra onde a
                    // tela vai crescer sem prometer que ja funciona.
                    color = when {
                        !tab.ready -> Obsidian.text3
                        active -> Obsidian.text1
                        else -> Obsidian.text2
                    },
                    fontSize = 13.sp,
                ),
            )
            Text(tab.sub, style = TextStyle(color = Obsidian.text3, fontSize = 10.sp))
        }
    }
}

@Composable
private fun ServerIconPreview(url: String?, name: String) {
    Box(
        Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Text(
                name.take(1).uppercase(),
                style = TextStyle(color = Obsidian.text2, fontSize = 22.sp, fontFamily = DmSerif),
            )
        } else {
            AstraImage(url, null, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun PlainField(
    value: String,
    placeholder: String,
    multiline: Boolean = false,
    onChange: (String) -> Unit,
) {
    Box(
        Modifier
            .widthIn(max = 460.dp)
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
            singleLine = !multiline,
            maxLines = if (multiline) 4 else 1,
            textStyle = TextStyle(color = Obsidian.text1, fontSize = 13.sp),
            cursorBrush = SolidColor(Obsidian.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Text(
        label,
        style = TextStyle(color = if (selected) Obsidian.accent else Obsidian.text2, fontSize = 12.sp),
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) Obsidian.active else if (hov) Obsidian.hover else Obsidian.raised)
            .border(
                1.dp,
                if (selected) Obsidian.accent.copy(alpha = 0.55f) else Obsidian.borderDim,
                RoundedCornerShape(7.dp),
            )
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun SmallButton(label: String, accent: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = TextStyle(color = if (accent) Obsidian.accent else Obsidian.text2, fontSize = 13.sp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (accent) Obsidian.accentDim else Obsidian.borderDim, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun DangerButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = TextStyle(color = Obsidian.danger, fontSize = 13.sp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Obsidian.danger.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
