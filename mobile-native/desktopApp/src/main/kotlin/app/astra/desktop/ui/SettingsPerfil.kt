package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.astra.desktop.profile.AvatarPicker
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.ui.theme.Tipo
import app.astra.mobile.core.network.UserApi
import app.astra.mobile.core.network.dto.CustomStatusRequest
import app.astra.mobile.core.network.dto.ProfileUserDto
import app.astra.mobile.core.network.dto.UpdateProfileRequest
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Crop
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Move
import com.composables.icons.lucide.SmilePlus
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Upload
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import retrofit2.HttpException

@Composable
internal fun ProfileSection(
    me: ProfileUserDto?,
    draft: ProfileDraft,
    onChange: (ProfileDraft) -> Unit,
    onSaved: () -> Unit,
    acoesDoCartao: AcoesDoCartao,
) {
    val scope = rememberCoroutineScope()
    var busyAvatar by remember { mutableStateOf(false) }
    var busyBanner by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var cropAvatar by remember { mutableStateOf<CropSource?>(null) }
    var cropBanner by remember { mutableStateOf<CropSource?>(null) }

    fun escolherAvatar() {
        val file = AvatarPicker.choose() ?: return
        busyAvatar = true
        msg = null
        scope.launch {
            val animated = withContext(Dispatchers.IO) { ImageCrop.isAnimated(file) }
            if (!animated) {
                busyAvatar = false
                cropAvatar = CropSource.Local(file)
                return@launch
            }
            val r = withContext(Dispatchers.IO) { AvatarPicker.encode(file) }
            busyAvatar = false
            r.onSuccess { onChange(draft.copy(avatarUrl = it)) }
                .onFailure { msg = "não foi possível ler essa imagem" to false }
        }
    }

    if (busyAvatar || busyBanner) {
        Text(
            "lendo a imagem…",
            style = Tipo.rotulo,
            modifier = Modifier.widthIn(max = 460.dp),
        )
    }
    SideEffect {
        acoesDoCartao.foto = {
            val atual = draft.avatarUrl
            buildList {
                add(MenuEntry.Item("trocar imagem", icon = Lucide.Upload) { escolherAvatar() })
                if (atual != null && !ImageCrop.isAnimated(atual)) {
                    add(MenuEntry.Item("reenquadrar", icon = Lucide.Crop) {
                        cropAvatar = CropSource.Remote(atual)
                    })
                }
                if (atual != null) {
                    add(MenuEntry.Separator)
                    add(MenuEntry.Item("remover", danger = true, icon = Lucide.Trash2) {
                        onChange(draft.copy(avatarUrl = null))
                    })
                }
            }
        }
    }

    SettingsDivider()
    ProfileField("nome", draft.displayName, me?.username ?: "seu nome") {
        onChange(draft.copy(displayName = it))
    }
    Spacer(Modifier.height(12.dp))
    ProfileField("pronomes", draft.pronouns, "ele/dela/elu…", max = 40) {
        onChange(draft.copy(pronouns = it))
    }
    Spacer(Modifier.height(12.dp))
    ProfileField("bio", draft.bio, "fale de você", multiline = true, max = 300) {
        onChange(draft.copy(bio = it))
    }

    var resizeOpen by remember { mutableStateOf(false) }
    fun escolherBanner() {
        val file = AvatarPicker.choose("Escolher banner") ?: return
        busyBanner = true
        msg = null
        scope.launch {
            val animated = withContext(Dispatchers.IO) { ImageCrop.isAnimated(file) }
            if (!animated) {
                busyBanner = false
                cropBanner = CropSource.Local(file)
                return@launch
            }
            val r = withContext(Dispatchers.IO) {
                AvatarPicker.encodeComMedidas(file, AvatarPicker.BANNER_DIM)
            }
            busyBanner = false
            r.onSuccess { img ->
                onChange(
                    draft.copy(
                        bannerUrl = img.dataUri,
                        bannerPositionY = 50,
                        bannerScale = AvatarPicker.zoomQueCobre(img.largura, img.altura, ProfileBannerAspect),
                    ),
                )
            }
                .onFailure { msg = "não foi possível ler essa imagem" to false }
        }
    }
    SideEffect {
        acoesDoCartao.banner = {
            val atual = draft.bannerUrl
            buildList {
                add(MenuEntry.Item("trocar imagem", icon = Lucide.Upload) { escolherBanner() })
                if (!atual.isNullOrBlank()) {
                    if (ImageCrop.isAnimated(atual)) {
                        add(MenuEntry.Item("reposicionar", icon = Lucide.Move) { resizeOpen = true })
                    } else {
                        add(MenuEntry.Item("reenquadrar", icon = Lucide.Crop) {
                            cropBanner = CropSource.Remote(atual)
                        })
                    }
                    add(MenuEntry.Separator)
                    add(MenuEntry.Item("remover", danger = true, icon = Lucide.Trash2) {
                        onChange(draft.copy(bannerUrl = null))
                    })
                }
            }
        }
    }
    if (resizeOpen && !draft.bannerUrl.isNullOrBlank()) {
        ResizeBannerDialog(
            draft = draft,
            username = me?.username ?: "você",
            onSave = { posY, scale -> onChange(draft.copy(bannerPositionY = posY, bannerScale = scale)) },
            onClose = { resizeOpen = false },
        )
    }
    cropAvatar?.let { src ->
        CropDialog(
            source = src,
            aspect = 1f,
            round = true,
            title = "recortar avatar",
            outW = ImageCrop.AVATAR_OUT_W,
            onApply = { onChange(draft.copy(avatarUrl = it)) },
            onClose = { cropAvatar = null },
        )
    }
    cropBanner?.let { src ->
        CropDialog(
            source = src,
            aspect = ProfileBannerAspect,
            round = false,
            title = "recortar banner",
            outW = ImageCrop.BANNER_OUT_W,
            onApply = { onChange(draft.copy(bannerUrl = it, bannerPositionY = 50, bannerScale = 100)) },
            onClose = { cropBanner = null },
        )
    }
    Spacer(Modifier.height(14.dp))
    FieldLabel("cor do perfil")
    ColorPickerButton(draft.bannerColor) {
        onChange(draft.copy(bannerColor = it, profileTheme = it))
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "a cor atravessa o cartao inteiro. com imagem de banner, ela aparece do banner para baixo.",
        style = Tipo.apoio,
        modifier = Modifier.widthIn(max = 420.dp),
    )

    SettingsDivider()
    FieldLabel("fonte do seu nome")
    FontPicker(draft.displayFont) { onChange(draft.copy(displayFont = it)) }

    SettingsDivider()
    FieldLabel("recado")
    Row(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StatusEmojiButton(draft.statusEmoji) { onChange(draft.copy(statusEmoji = it)) }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Obsidian.raised)
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (draft.customStatus.isEmpty()) {
                Text("Como foi seu dia?", style = TextStyle(color = Obsidian.text3, fontSize = 13.sp))
            }
            BasicTextField(
                value = draft.customStatus,
                onValueChange = { onChange(draft.copy(customStatus = it.take(100))) },
                singleLine = true,
                textStyle = Tipo.corpo,
                cursorBrush = SolidColor(Obsidian.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    Spacer(Modifier.height(16.dp))
    msg?.let { (text, ok) ->
        Text(text, style = TextStyle(color = if (ok) Obsidian.success else Obsidian.danger, fontSize = 12.sp))
    }
}

@Composable
internal fun ProfileSaveButton(
    me: ProfileUserDto?,
    draft: ProfileDraft,
    onChange: (ProfileDraft) -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val koin = GlobalContext.get()
    val scope = rememberCoroutineScope()
    val original = remember(me) { ProfileDraft.from(me) }
    var saving by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val dirty = draft != original
    Column(modifier) {
        msg?.let { (text, ok) ->
            Text(text, style = TextStyle(color = if (ok) Obsidian.success else Obsidian.danger, fontSize = 12.sp))
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AboutButton(if (saving) "salvando…" else "salvar", accent = true, icone = Lucide.Check) {
                if (saving || !dirty) return@AboutButton
                saving = true
                msg = null
                scope.launch {
                    val api = koin.get<UserApi>()
                    val r = runCatching {
                        if (draft.customStatus.trim() != original.customStatus.trim()) {
                            api.setCustomStatus(CustomStatusRequest(draft.customStatus.trim()))
                        }
                        api.updateProfile(
                            UpdateProfileRequest(
                                displayName = draft.displayName.trim().ifBlank { null },
                                pronouns = draft.pronouns.trim(),
                                bio = draft.bio.trim(),
                                avatarUrl = draft.avatarUrl,
                                statusEmoji = draft.statusEmoji,
                                bannerUrl = draft.bannerUrl ?: "",
                                bannerColor = draft.bannerColor,
                                bannerPositionY = draft.bannerPositionY,
                                bannerScale = draft.bannerScale,
                                profileTheme = draft.profileTheme,
                                displayFont = draft.displayFont,
                            ),
                        )
                    }
                    saving = false
                    if (r.isSuccess) { msg = "perfil salvo" to true; onSaved() }
                    else msg = saveErrorMessage(r.exceptionOrNull()) to false
                }
            }
            if (dirty && !saving) {
                AboutButton("descartar", accent = false) { onChange(original); msg = null }
            }
        }
        if (!dirty && msg == null) {
            Spacer(Modifier.height(6.dp))
            Text("nada mudou ainda.", style = Tipo.apoio)
        }
    }
}

private fun saveErrorMessage(t: Throwable?): String {
    val http = t as? HttpException ?: return "sem conexão com o servidor"
    if (http.code() == 413) return "a imagem ficou grande demais — escolha uma menor ou dê menos zoom"
    val body = runCatching { http.response()?.errorBody()?.string() }.getOrNull()
    val parsed = body?.let {
        runCatching { Regex("\"error\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1) }.getOrNull()
    }
    return parsed?.takeIf { it.isNotBlank() } ?: "não foi possível salvar (erro ${http.code()})"
}

private const val ZOOM_MIN = 50
private const val ZOOM_MAX = 300

@Composable
internal fun LinhaDeVolume(rotulo: String, valor: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.widthIn(max = 460.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            rotulo,
            style = Tipo.rotulo,
            modifier = Modifier.width(150.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(22.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        onChange(((change.position.x / size.width).coerceIn(0f, 1f) * 100).roundToInt())
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val fracao = (valor / 100f).coerceIn(0f, 1f)
            Box(
                Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                    .background(Obsidian.void.copy(alpha = 0.6f)),
            )
            Box(
                Modifier.fillMaxWidth(fracao).height(5.dp).clip(RoundedCornerShape(3.dp))
                    .background(Obsidian.accent),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (fracao > 0f) Spacer(Modifier.weight(fracao))
                Box(
                    Modifier.size(14.dp).clip(CircleShape)
                        .background(Obsidian.accent).border(2.dp, Obsidian.raised, CircleShape),
                )
                if (fracao < 1f) Spacer(Modifier.weight(1f - fracao))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "$valor%",
            style = TextStyle(color = Obsidian.text2, fontSize = 11.sp),
            modifier = Modifier.width(34.dp),
        )
    }
}

@Composable
private fun ZoomTrack(scale: Int, onChange: (Int) -> Unit) {
    val faixa = (ZOOM_MAX - ZOOM_MIN).toFloat()
    val pct = ((scale - ZOOM_MIN) / faixa).coerceIn(0f, 1f)
    Row(
        Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("zoom", style = Tipo.apoio, modifier = Modifier.width(42.dp))
        Box(
            Modifier
                .weight(1f)
                .height(22.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val f = (change.position.x / size.width).coerceIn(0f, 1f)
                        onChange(ZOOM_MIN + (f * faixa).toInt())
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                    .background(Obsidian.void.copy(alpha = 0.6f)),
            )
            Box(
                Modifier.fillMaxWidth(pct).height(5.dp).clip(RoundedCornerShape(3.dp))
                    .background(Obsidian.accent),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val f = pct.coerceIn(0f, 1f)
                if (f > 0f) Spacer(Modifier.weight(f))
                Box(
                    Modifier.size(14.dp).clip(CircleShape)
                        .background(Obsidian.accent).border(2.dp, Obsidian.raised, CircleShape),
                )
                if (f < 1f) Spacer(Modifier.weight(1f - f))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text("${scale}%", style = TextStyle(color = Obsidian.text2, fontSize = 11.sp))
    }
}

@Composable
private fun ResizeBannerDialog(
    draft: ProfileDraft,
    username: String,
    onSave: (posY: Int, scale: Int) -> Unit,
    onClose: () -> Unit,
) {
    var posY by remember { mutableStateOf(draft.bannerPositionY) }
    var scl by remember { mutableStateOf(draft.bannerScale) }
    val name = draft.displayName.ifBlank { username }
    DialogShell(onClose = onClose, largura = 360.dp) {
        Column {
                Text(
                    "redimensionar banner",
                    style = TextStyle(color = Obsidian.text1, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "é isto que os outros veem. arraste na imagem para enquadrar.",
                    style = Tipo.apoio,
                )
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Obsidian.overlay)
                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp)),
                ) {
                    ProfileBanner(
                        css = draft.bannerColor,
                        imageUrl = draft.bannerUrl,
                        positionY = posY,
                        scale = scl,
                        fallback = Obsidian.overlay,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(ProfileBannerAspect)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .pointerInput(Unit) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    posY = (posY - drag.y / 0.9f).toInt().coerceIn(0, 100)
                                }
                            },
                    )
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Box(Modifier.offset(y = (-30).dp)) {
                            DesktopAvatar(draft.avatarUrl, name, 72)
                        }
                        Column(Modifier.offset(y = (-8).dp)) {
                            Text(
                                name,
                                style = TextStyle(
                                    color = Obsidian.text1, fontSize = 18.sp, fontWeight = FontWeight.Medium,
                                    fontFamily = profileFontFamily(draft.displayFont),
                                ),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "@$username",
                                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, fontFamily = DmMono),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                ZoomTrack(scl) { scl = it }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AboutButton("cancelar", accent = false) { onClose() }
                    AboutButton("salvar", accent = true) { onSave(posY, scl); onClose() }
                }
        }
    }
}

@Composable
private fun ColorPickerButton(selected: String?, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val hov = remember { MutableInteractionSource() }
    val h by hov.collectIsHoveredAsState()
    Box {
        Row(
            Modifier
                .widthIn(max = 420.dp)
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
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp, 22.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .drawBehind {
                        drawRect(bannerBrush(selected, size.width, size.height, Obsidian.overlay))
                    }
                    .border(1.dp, Obsidian.borderDim, RoundedCornerShape(5.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                colorLabel(selected),
                style = TextStyle(color = Obsidian.text2, fontSize = 12.sp, fontFamily = DmMono),
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
                offset = IntOffset(0, 44),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .popupReveal()
                        .width(390.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Obsidian.overlay)
                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                ) {
                    FieldLabel("código hex")
                    HexField(selected, onPick)
                    Spacer(Modifier.height(12.dp))
                    CartaoInterno(fundo = Obsidian.hover, padding = PaddingValues(10.dp)) {
                        FieldLabel("gradientes")
                        Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                            GradientGrid(selected) { onPick(it); open = false }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HexField(selected: String?, onPick: (String) -> Unit) {
    var text by remember(selected) {
        mutableStateOf(selected?.trim()?.takeIf { it.startsWith("#") }?.removePrefix("#").orEmpty())
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("#", style = TextStyle(color = Obsidian.text3, fontSize = 13.sp, fontFamily = DmMono))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) {
                Text(
                    "c9a96e",
                    style = TextStyle(color = Obsidian.text3, fontSize = 13.sp, fontFamily = DmMono),
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { raw ->
                    val clean = raw.filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
                        .lowercase().take(6)
                    text = clean
                    if (clean.length == 6) onPick("#$clean")
                },
                singleLine = true,
                textStyle = TextStyle(color = Obsidian.text1, fontSize = 13.sp, fontFamily = DmMono),
                cursorBrush = SolidColor(Obsidian.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(5.dp))
                .drawBehind {
                    val css = if (text.length == 6) "#$text" else null
                    drawRect(bannerBrush(css, size.width, size.height, Obsidian.overlay))
                }
                .border(1.dp, Obsidian.borderDim, RoundedCornerShape(5.dp)),
        )
    }
}

private fun colorLabel(css: String?): String {
    val raw = css?.trim().orEmpty()
    if (raw.isEmpty()) return "padrao"
    BANNER_GRADIENTS.find { it.css == raw }?.let { return it.label.lowercase() }
    return if (raw.startsWith("#")) raw.lowercase() else "gradiente proprio"
}

@Composable
private fun GradientGrid(selected: String?, onPick: (String) -> Unit) {
    Column(
        Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BANNER_GRADIENTS.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { g ->
                    val active = selected == g.css
                    Box(
                        Modifier
                            .weight(1f)
                            .height(30.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .drawBehind { drawRect(bannerBrush(g.css, size.width, size.height, Obsidian.overlay)) }
                            .border(
                                if (active) 2.dp else 1.dp,
                                if (active) Obsidian.accent else Obsidian.borderDim,
                                RoundedCornerShape(7.dp),
                            )
                            .clickable { onPick(g.css) },
                    )
                }
                repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun FontPicker(selected: String?, onPick: (String) -> Unit) {
    val current = selected ?: "serif"
    val cur = PROFILE_FONTS.find { it.id == current } ?: PROFILE_FONTS.first()
    var open by remember { mutableStateOf(false) }
    val hov = remember { MutableInteractionSource() }
    val h by hov.collectIsHoveredAsState()
    Box {
        Row(
            Modifier
                .widthIn(max = 420.dp)
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
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                cur.label,
                style = TextStyle(color = Obsidian.text1, fontSize = 15.sp, fontFamily = cur.family),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            LIcon(Lucide.ChevronDown, tint = Obsidian.text3, size = 14.dp)
        }
        if (open) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, 46),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .popupReveal()
                        .width(390.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Obsidian.overlay)
                        .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp))
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    PROFILE_FONTS.forEach { f ->
                        val active = f.id == current
                        val rowHov = remember { MutableInteractionSource() }
                        val rh by rowHov.collectIsHoveredAsState()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) Obsidian.active else if (rh) Obsidian.hover else Color.Transparent)
                                .hoverable(rowHov)
                                .clickable { onPick(f.id); open = false }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                f.label,
                                style = TextStyle(
                                    color = if (active) Obsidian.text1 else Obsidian.text2,
                                    fontSize = 15.sp,
                                    fontFamily = f.family,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            if (active) {
                                Spacer(Modifier.width(8.dp))
                                LIcon(Lucide.Check, tint = Obsidian.accent, size = 15.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProfileField(
    label: String,
    value: String,
    placeholder: String,
    multiline: Boolean = false,
    max: Int = 190,
    onChange: (String) -> Unit,
) {
    FieldLabel(label)
    Box(
        Modifier
            .widthIn(max = 420.dp)
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
            onValueChange = { onChange(it.take(max)) },
            singleLine = !multiline,
            textStyle = TextStyle(color = Obsidian.text1, fontSize = 13.sp, lineHeight = 18.sp),
            cursorBrush = SolidColor(Obsidian.accent),
            modifier = if (multiline) Modifier.fillMaxWidth().height(70.dp) else Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StatusEmojiButton(current: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Obsidian.raised)
                .border(1.dp, if (open) Obsidian.accent else Obsidian.borderDim, RoundedCornerShape(8.dp))
                .clickable { if (current.isNotBlank()) onPick("") else open = true },
            contentAlignment = Alignment.Center,
        ) {
            if (current.isBlank()) {
                LIcon(Lucide.SmilePlus, tint = Obsidian.text3, size = 18.dp)
            } else {
                Text(current, style = TextStyle(fontSize = 18.sp))
            }
        }
        if (open) {
            Popup(
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                PopupReveal {
                    ReactionPicker(onPick = { onPick(it); open = false })
                }
            }
        }
    }
}
