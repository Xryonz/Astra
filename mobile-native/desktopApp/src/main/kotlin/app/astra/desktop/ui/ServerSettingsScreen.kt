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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
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
import app.astra.mobile.core.network.dto.BanDto
import app.astra.mobile.core.network.dto.RoleDto
import app.astra.mobile.core.network.dto.RoleRequest
import app.astra.mobile.core.network.dto.ServerDto
import app.astra.mobile.core.network.dto.ServerMemberDto
import app.astra.mobile.core.network.dto.UpdateServerRequest
import com.composables.icons.lucide.Ban
import com.composables.icons.lucide.Crop
import com.composables.icons.lucide.LoaderCircle
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Upload
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Shield
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Configuracoes da CONSTELACAO — takeover no mesmo idioma do SettingsScreen (nav
// de 220dp a esquerda, coluna de conteudo capada em 720dp), pra as duas telas de
// configuração se lerem como a mesma coisa.
//
// As tres abas estão prontas. `ready` fica no enum de proposito: e o interruptor
// pra listar uma aba futura apagada e inerte, sem mudar a forma da navegacao.
internal enum class ServerTab(val label: String, val sub: String, val icon: ImageVector, val ready: Boolean) {
    OVERVIEW("Visao geral", "nome, imagens e convite", Lucide.Info, true),
    ROLES("Cargos", "permissões e cor do nome", Lucide.Shield, true),
    BANS("Banimentos", "quem não pode voltar", Lucide.Ban, true),
}

@Composable
fun ServerSettingsScreen(
    server: ServerDto,
    isOwner: Boolean,
    members: List<ServerMemberDto>,
    myPermissions: Set<String>,
    onClose: () -> Unit,
    onSave: (UpdateServerRequest, (String?) -> Unit) -> Unit,
    onRegenerateInvite: ((String?) -> Unit) -> Unit,
    onDelete: () -> Unit,
    onLeave: () -> Unit,
    onLoadRoles: ((List<RoleDto>?, String?) -> Unit) -> Unit,
    onSaveRole: (String?, RoleRequest, (String?) -> Unit) -> Unit,
    onDeleteRole: (String, (String?) -> Unit) -> Unit,
    onToggleMemberRole: (String, String, Boolean, (String?) -> Unit) -> Unit,
    onLoadBans: ((List<BanDto>?, String?) -> Unit) -> Unit,
    onUnban: (String, (String?) -> Unit) -> Unit,
) {
    var tab by remember { mutableStateOf(ServerTab.OVERVIEW) }

    // Cargos vivem aqui (não no ShellUiState): so esta tela usa. Recarrega quando
    // a aba abre e depois de cada mudanca, pra a lista refletir o servidor.
    var roles by remember(server.id) { mutableStateOf<List<RoleDto>?>(null) }
    var rolesError by remember(server.id) { mutableStateOf<String?>(null) }
    fun reloadRoles() = onLoadRoles { list, err -> roles = list; rolesError = err }

    var bans by remember(server.id) { mutableStateOf<List<BanDto>?>(null) }
    var bansError by remember(server.id) { mutableStateOf<String?>(null) }
    fun reloadBans() = onLoadBans { list, err -> bans = list; bansError = err }

    // Busca so quando a aba entra em cena: quem so mexe na Visao geral não paga
    // por cargos nem banimentos.
    LaunchedEffect(server.id, tab) {
        when (tab) {
            ServerTab.ROLES -> reloadRoles()
            ServerTab.BANS -> reloadBans()
            else -> {}
        }
    }

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
                    "constelação",
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
                                ServerTab.ROLES -> RolesSection(
                                    roles = roles,
                                    members = members,
                                    myPermissions = myPermissions,
                                    amOwner = isOwner,
                                    error = rolesError,
                                    // Recarrega depois de cada mudanca: posição e
                                    // permissões efetivas vem do servidor (o backend
                                    // filtra o que você não pode conceder).
                                    onSave = { id, body, cb ->
                                        onSaveRole(id, body) { err -> if (err == null) reloadRoles(); cb(err) }
                                    },
                                    onDelete = { id, cb ->
                                        onDeleteRole(id) { err -> if (err == null) reloadRoles(); cb(err) }
                                    },
                                    onToggleMember = onToggleMemberRole,
                                )
                                ServerTab.BANS -> BansSection(
                                    bans = bans,
                                    error = bansError,
                                    // Recarrega ao revogar: a linha some da lista.
                                    onUnban = { uid, cb ->
                                        onUnban(uid) { err -> if (err == null) reloadBans(); cb(err) }
                                    },
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
    var bannerPositionY by remember(server) { mutableStateOf(server.bannerPositionY) }
    var bannerScale by remember(server) { mutableStateOf(server.bannerScale) }
    var iconScale by remember(server) { mutableStateOf(server.iconScale) }

    var saving by remember { mutableStateOf(false) }
    var busyIcon by remember { mutableStateOf(false) }
    var busyBanner by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var confirmRegen by remember { mutableStateOf(false) }
    var confirmDanger by remember { mutableStateOf(false) }
    var regenerating by remember { mutableStateOf(false) }
    // Recorte estilo Discord: a fonte aberta no modal (arquivo novo ou a imagem
    // já salva pra reenquadrar). null = modal fechado.
    var cropIcon by remember { mutableStateOf<CropSource?>(null) }
    var cropBanner by remember { mutableStateOf<CropSource?>(null) }

    val dirty = name.trim() != server.name ||
        description.trim() != server.description.orEmpty().trim() ||
        iconUrl != server.iconUrl ||
        bannerUrl != server.bannerUrl ||
        isPublic != server.isPublic ||
        retention != (server.messageRetentionDays ?: 0) ||
        bannerPositionY != server.bannerPositionY ||
        bannerScale != server.bannerScale ||
        iconScale != server.iconScale

    // Form (esquerda) + card de previa ao vivo (direita). O form segue no fluxo
    // scrollavel do pai; a previa acompanha no topo-direita.
    // O vao e generoso de proposito: a previa NAO e um campo do formulario, e a 48dp
    // ela parecia mais uma coluna do form do que o resultado dele.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(80.dp)) {
      Column(Modifier.weight(1f)) {
    // ---- Identidade ----
    FieldLabel("ícone")
    Row(verticalAlignment = Alignment.CenterVertically) {
        ServerIconPreview(iconUrl, name, iconScale)
        Spacer(Modifier.width(16.dp))
        Column {
            SmallButton(if (busyIcon) "processando…" else "trocar ícone", accent = true) {
                if (busyIcon) return@SmallButton
                val file = AvatarPicker.choose("Escolher ícone") ?: return@SmallButton
                busyIcon = true
                msg = null
                scope.launch {
                    // Ler o arquivo e pesado -> fora da thread de UI. Animado não
                    // pode ser assado num recorte: vai pro caminho antigo (a
                    // animação sobrevive, o enquadramento fica em metadado).
                    val animated = withContext(Dispatchers.IO) { ImageCrop.isAnimated(file) }
                    if (!animated) {
                        busyIcon = false
                        cropIcon = CropSource.Local(file)
                        return@launch
                    }
                    val r = withContext(Dispatchers.IO) { AvatarPicker.encode(file) }
                    busyIcon = false
                    r.onSuccess { iconUrl = it }
                        .onFailure { msg = "não deu pra ler essa imagem" to false }
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
        "a imagem e reduzida pra 512px e vira parte da constelação (máximo 5MB).",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )
    // Estatico agora e ASSADO no recorte, entao o zoom em metadado so vale pro
    // animado (o unico que não pode ser assado). Estatico ganha "reenquadrar".
    val iconNow = iconUrl
    if (!iconNow.isNullOrBlank()) {
        Spacer(Modifier.height(10.dp))
        if (ImageCrop.isAnimated(iconNow)) {
            ServerZoomTrack(iconScale) { iconScale = it }
        } else {
            SmallButton("reenquadrar", accent = false) { cropIcon = CropSource.Remote(iconNow) }
        }
    }

    SettingsDivider()
    FieldLabel("nome")
    PlainField(name, "nome da constelação") { name = it.take(100) }
    Spacer(Modifier.height(12.dp))
    FieldLabel("descrição")
    PlainField(description, "do que e essa constelação?", multiline = true) { description = it.take(300) }

    SettingsDivider()
    FieldLabel("banner")
    val bannerNow = bannerUrl
    val bannerAnimated = ImageCrop.isAnimated(bannerNow)
    ProfileBanner(
        css = null,
        imageUrl = bannerUrl,
        positionY = bannerPositionY,
        scale = bannerScale,
        fallback = Obsidian.overlay,
        modifier = Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            // Proporcao UNICA (editor = previa = card da Descoberta): so assim o
            // recorte assado cai exato nos tres.
            .aspectRatio(ServerBannerAspect)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(10.dp))
            .then(
                // Arrastar na vertical reposiciona — so pro ANIMADO, que não pode
                // ser assado. O estatico já vem recortado, arrastar so desalinharia.
                if (!bannerAnimated) Modifier
                else Modifier.pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        bannerPositionY = (bannerPositionY - drag.y / 1.4f).toInt().coerceIn(0, 100)
                    }
                },
            ),
    )
    if (bannerAnimated) {
        Spacer(Modifier.height(6.dp))
        Text("arraste na imagem pra enquadrar.", style = TextStyle(color = Obsidian.text3, fontSize = 11.sp))
        Spacer(Modifier.height(10.dp))
        ServerZoomTrack(bannerScale) { bannerScale = it }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Ícones em vez de texto: os três nomes por extenso ocupavam a largura toda e
        // o mais comprido ainda quebrava em duas linhas. O nome de cada um aparece ao
        // parar o mouse — em botão que apaga, descobrir clicando sairia caro.
        BotaoIcone(
            icone = if (busyBanner) Lucide.LoaderCircle else Lucide.Upload,
            dica = if (busyBanner) "processando…" else "subir banner",
            accent = true,
        ) {
            if (busyBanner) return@BotaoIcone
            val file = AvatarPicker.choose("Escolher banner") ?: return@BotaoIcone
            busyBanner = true
            msg = null
            scope.launch {
                val animated = withContext(Dispatchers.IO) { ImageCrop.isAnimated(file) }
                if (!animated) {
                    busyBanner = false
                    cropBanner = CropSource.Local(file)
                    return@launch
                }
                val r = withContext(Dispatchers.IO) { AvatarPicker.encode(file, AvatarPicker.BANNER_DIM) }
                busyBanner = false
                r.onSuccess { bannerUrl = it; bannerPositionY = 50; bannerScale = 100 }
                    .onFailure { msg = "não deu pra ler essa imagem" to false }
            }
        }
        if (!bannerNow.isNullOrBlank()) {
            if (!bannerAnimated) {
                BotaoIcone(Lucide.Crop, "reenquadrar") { cropBanner = CropSource.Remote(bannerNow) }
            }
            BotaoIcone(Lucide.Trash2, "remover banner", danger = true) { bannerUrl = null }
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
        "regenerar invalida o convite atual — quem tiver o link antigo não entra mais.",
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
        "Constelação pública",
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
        else "mensagens com mais de $retention dia(s) somem sozinhas — não da pra recuperar.",
        style = TextStyle(color = if (retention == 0) Obsidian.text3 else Obsidian.warning, fontSize = 11.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )

    // ---- Zona de perigo ----
    SettingsDivider()
    FieldLabel("zona de perigo")
    Text(
        if (isOwner) "excluir apaga a constelação pra todo mundo. Não da pra desfazer."
        else "sair remove teu acesso; pra voltar precisa de convite.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
        modifier = Modifier.widthIn(max = 460.dp),
    )
    Spacer(Modifier.height(10.dp))
    DangerButton(if (isOwner) "excluir constelação" else "sair da constelação") { confirmDanger = true }
    if (confirmDanger) {
        ConfirmPopup(
            message = if (isOwner) "excluir ${server.name}? apaga pra todos — não da pra desfazer."
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
      } // fim do form (coluna esquerda)
      // Coluna direita: card de previa ao vivo -> legenda "PREVIA" ABAIXO dele ->
      // ações de salvar/descartar (ficam junto do que elas afetam).
      Column(
          modifier = Modifier.padding(top = 26.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
      ) {
          ServerConfigPreview(
              name = name,
              description = description,
              iconUrl = iconUrl,
              bannerUrl = bannerUrl,
              bannerPositionY = bannerPositionY,
              bannerScale = bannerScale,
              iconScale = iconScale,
              channelCount = server.channels.size,
          )
          Spacer(Modifier.height(10.dp))
          Text(
              "PREVIA",
              style = TextStyle(color = Obsidian.text3, fontSize = 9.sp, letterSpacing = 1.5.sp),
          )
          Spacer(Modifier.height(18.dp))
          msg?.let { (text, ok) ->
              Text(
                  text,
                  style = TextStyle(color = if (ok) Obsidian.success else Obsidian.danger, fontSize = 12.sp),
                  modifier = Modifier.widthIn(max = 248.dp),
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
                          bannerPositionY = bannerPositionY,
                          bannerScale = bannerScale,
                          iconScale = iconScale,
                          description = description.trim(),
                          // 0 = "pra sempre"; o backend traduz 0 em null.
                          messageRetentionDays = retention,
                          isPublic = isPublic,
                      ),
                  ) { err ->
                      saving = false
                      msg = (err ?: "constelação salva") to (err == null)
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
                      bannerPositionY = server.bannerPositionY
                      bannerScale = server.bannerScale
                      iconScale = server.iconScale
                      msg = null
                  }
              }
          }
          if (!dirty && msg == null) {
              Spacer(Modifier.height(6.dp))
              Text("nada mudou ainda.", style = TextStyle(color = Obsidian.text3, fontSize = 11.sp))
          }
      }
    } // fim do Row form+previa

    // Modais de recorte (Popup: sobem no nivel da janela, não estorvam o layout).
    cropIcon?.let { src ->
        CropDialog(
            source = src,
            aspect = 1f,
            round = false,
            title = "recortar ícone",
            outW = ImageCrop.AVATAR_OUT_W,
            onApply = { iconUrl = it; iconScale = 100 },
            onClose = { cropIcon = null },
        )
    }
    cropBanner?.let { src ->
        CropDialog(
            source = src,
            aspect = ServerBannerAspect,
            round = false,
            title = "recortar banner",
            outW = ImageCrop.BANNER_OUT_W,
            onApply = { bannerUrl = it; bannerPositionY = 50; bannerScale = 100 },
            onClose = { cropBanner = null },
        )
    }
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
                    // Aba não pronta fica visivelmente apagada: mostra pra onde a
                    // tela vai crescer sem prometer que já funciona.
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
private fun ServerIconPreview(url: String?, name: String, iconScale: Int = 100, size: Dp = 64.dp) {
    val corner = size * 0.28f
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Text(
                name.take(1).uppercase(),
                style = TextStyle(color = Obsidian.text2, fontSize = (size.value * 0.34f).sp, fontFamily = DmSerif),
            )
        } else {
            // iconScale (100..300%): zoom centrado dentro do recorte (o clip do Box corta o excesso).
            AstraImage(
                url, null,
                Modifier.fillMaxSize().graphicsLayer {
                    val s = iconScale / 100f
                    scaleX = s; scaleY = s
                },
            )
        }
    }
}

// Zoom (100..300%): trilha arrastavel simples. Espelha o ZoomTrack das configs de
// usuário (pequena demais pra virar componente compartilhado por enquanto).
@Composable
private fun ServerZoomTrack(scale: Int, onChange: (Int) -> Unit) {
    val pct = ((scale - 100) / 200f).coerceIn(0f, 1f)
    Row(
        Modifier.widthIn(max = 460.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("zoom", style = TextStyle(color = Obsidian.text3, fontSize = 11.sp), modifier = Modifier.width(42.dp))
        Box(
            Modifier
                .weight(1f)
                .height(22.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val f = (change.position.x / size.width).coerceIn(0f, 1f)
                        onChange((100 + f * 200).toInt())
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Obsidian.void.copy(alpha = 0.6f)))
            Box(Modifier.fillMaxWidth(pct).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Obsidian.accent))
            // Alca agarravel no fim do preenchido (spacers pesados = centro na fracao,
            // sem clipar nas pontas; arrastar em qualquer ponto do trilho também move).
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val f = pct.coerceIn(0f, 1f)
                if (f > 0f) Spacer(Modifier.weight(f))
                Box(Modifier.size(14.dp).clip(CircleShape).background(Obsidian.accent).border(2.dp, Obsidian.raised, CircleShape))
                if (f < 1f) Spacer(Modifier.weight(1f - f))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text("${scale}%", style = TextStyle(color = Obsidian.text2, fontSize = 11.sp))
    }
}

// Previa ao vivo da config (direita): a constelação como aparece pros outros —
// banner enquadrado (posição/zoom), ícone sobreposto (zoom), nome e nº de canais.
@Composable
private fun ServerConfigPreview(
    name: String,
    description: String,
    iconUrl: String?,
    bannerUrl: String?,
    bannerPositionY: Int,
    bannerScale: Int,
    iconScale: Int,
    channelCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(248.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Obsidian.raised.copy(alpha = 0.6f))
            .border(1.dp, Obsidian.borderMid, RoundedCornerShape(14.dp)),
    ) {
        Box {
            ProfileBanner(
                css = null,
                imageUrl = bannerUrl,
                positionY = bannerPositionY,
                scale = bannerScale,
                fallback = Obsidian.overlay,
                modifier = Modifier.fillMaxWidth().aspectRatio(ServerBannerAspect),
            )
            Box(Modifier.align(Alignment.BottomStart).padding(start = 14.dp).offset(y = 26.dp)) {
                ServerIconPreview(iconUrl, name, iconScale, 54.dp)
            }
        }
        Spacer(Modifier.height(32.dp)) // espaco pro ícone sobreposto
        Column(Modifier.padding(horizontal = 14.dp).padding(bottom = 14.dp)) {
            Text(
                name.ifBlank { "constelação" },
                style = TextStyle(color = Obsidian.text1, fontSize = 15.sp, fontFamily = DmSerif),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "$channelCount ${if (channelCount == 1) "canal" else "canais"}",
                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
            )
            if (description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    description,
                    style = TextStyle(color = Obsidian.text2, fontSize = 12.sp, lineHeight = 16.sp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
