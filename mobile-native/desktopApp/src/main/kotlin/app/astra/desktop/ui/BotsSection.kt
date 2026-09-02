package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.profile.AvatarPicker
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.mobile.core.network.BotPersonaApi
import app.astra.mobile.core.network.dto.BotPersonaDto
import app.astra.mobile.core.network.dto.BotPersonaPatch
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.Upload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import app.astra.desktop.ui.theme.Tipo

@Composable
fun BotsSection() {
    val api = remember { GlobalContext.get().get<BotPersonaApi>() }
    val scope = rememberCoroutineScope()
    var personas by remember { mutableStateOf<List<BotPersonaDto>>(emptyList()) }
    var erro by remember { mutableStateOf<String?>(null) }
    var carregando by remember { mutableStateOf(true) }

    suspend fun recarregar() {
        runCatching { api.personas().data?.personas.orEmpty() }
            .onSuccess { personas = it; erro = null }
            .onFailure { erro = "não foi possível carregar as bots." }
        carregando = false
    }
    LaunchedEffect(Unit) { recarregar() }

    fun aplicar(chave: String, patch: BotPersonaPatch) {
        scope.launch {
            runCatching { api.ajustar(chave, patch) }
                .onSuccess { recarregar() }
                .onFailure { erro = "não foi possível salvar." }
        }
    }

    FieldLabel("bots")
    Text(
        "a Sparkle e a Sparxie dividem uma conta só e trocam de turno: a Sparxie cobre sexta e sábado. " +
            "o que você mexer aqui vale em todas as constelações.",
        style = TextStyle(color = Obsidian.text3, fontSize = 11.sp, lineHeight = 16.sp),
        modifier = Modifier.widthIn(max = 520.dp),
    )
    Spacer(Modifier.height(16.dp))

    when {
        carregando -> Text("carregando…", style = Tipo.descricao)
        erro != null -> Text(erro!!, style = Tipo.erro)
        else -> personas.forEach { p ->
            CartaoDaBot(p, aoMudar = { aplicar(p.chave, it) })
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun CartaoDaBot(p: BotPersonaDto, aoMudar: (BotPersonaPatch) -> Unit) {
    val scope = rememberCoroutineScope()
    var ocupado by remember { mutableStateOf(false) }

    var zoom by remember(p.chave, p.bannerScale) { mutableStateOf(p.bannerScale) }
    var posY by remember(p.chave, p.bannerPositionY) { mutableStateOf(p.bannerPositionY) }

    fun escolher(banner: Boolean) {
        val file = AvatarPicker.choose(if (banner) "Banner da ${p.displayName}" else "Foto da ${p.displayName}")
            ?: return
        ocupado = true
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                AvatarPicker.encodeComMedidas(file, if (banner) AvatarPicker.BANNER_DIM else 1024)
            }
            ocupado = false
            r.onSuccess { img ->
                if (banner) {
                    val cobre = if (img.largura > 0) {
                        AvatarPicker.zoomQueCobre(img.largura, img.altura, ProfileBannerAspect)
                    } else zoom
                    zoom = cobre
                    aoMudar(BotPersonaPatch(bannerUrl = img.dataUri, bannerScale = cobre, bannerPositionY = 50))
                } else {
                    aoMudar(BotPersonaPatch(avatarUrl = img.dataUri))
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Obsidian.raised)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(12.dp)),
    ) {
        Box {
            ProfileBanner(
                css = null,
                imageUrl = p.bannerUrl,
                positionY = posY,
                scale = zoom,
                fallback = bannerBackdrop(p.bannerUrl),
                modifier = Modifier.fillMaxWidth().aspectRatio(ProfileBannerAspect),
            )
            FotoEditavel(
                forma = RectangleShape,
                rotulo = "trocar o banner da ${p.displayName}",
                glifo = 22.dp,
                acoes = {
                    buildList {
                        add(MenuEntry.Item("trocar imagem", icon = Lucide.Upload) { escolher(true) })
                        if (p.personalizado.bannerUrl) {
                            add(MenuEntry.Separator)
                            add(MenuEntry.Item("voltar ao original", icon = Lucide.RotateCcw) {
                                aoMudar(BotPersonaPatch(limpar = listOf("bannerUrl")))
                            })
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().aspectRatio(ProfileBannerAspect),
            ) {}
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .offset(y = (-26).dp)
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Obsidian.raised)
                    .padding(3.dp),
            ) {
                FotoEditavel(
                    forma = CircleShape,
                    rotulo = "trocar a foto da ${p.displayName}",
                    glifo = 16.dp,
                    acoes = {
                        buildList {
                            add(MenuEntry.Item("trocar imagem", icon = Lucide.Upload) { escolher(false) })
                            if (p.personalizado.avatarUrl) {
                                add(MenuEntry.Separator)
                                add(MenuEntry.Item("voltar ao original", icon = Lucide.RotateCcw) {
                                    aoMudar(BotPersonaPatch(limpar = listOf("avatarUrl")))
                                })
                            }
                        }
                    },
                    modifier = Modifier.size(52.dp),
                ) { aceso ->
                    DesktopAvatar(p.avatarUrl, p.displayName, 52)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.offset(y = (-8).dp)) {
                Text(p.displayName, style = TextStyle(color = Obsidian.text1, fontSize = 15.sp))
                Text(
                    if (ocupado) "lendo a imagem…"
                    else if (p.chave == "sparxie") "de plantão sexta e sábado"
                    else "de plantão de domingo a quinta",
                    style = Tipo.apoio,
                )
            }
        }
        Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
            TrilhaDaBot("zoom", zoom, 100, 300) { zoom = it }
            Spacer(Modifier.height(8.dp))
            TrilhaDaBot("altura", posY, 0, 100) { posY = it }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BotaoSalvarEnquadramento(
                    mudou = zoom != p.bannerScale || posY != p.bannerPositionY,
                ) { aoMudar(BotPersonaPatch(bannerScale = zoom, bannerPositionY = posY)) }
                if (p.personalizado.bannerScale || p.personalizado.bannerPositionY) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "voltar ao original",
                        style = Tipo.descricao,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                aoMudar(BotPersonaPatch(limpar = listOf("bannerScale", "bannerPositionY")))
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrilhaDaBot(rotulo: String, valor: Int, min: Int, max: Int, aoMudar: (Int) -> Unit) {
    val f = ((valor - min).toFloat() / (max - min)).coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(rotulo, style = Tipo.apoio, modifier = Modifier.width(48.dp))
        Box(
            Modifier
                .weight(1f)
                .height(22.dp)
                .pointerInput(min, max) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val x = (change.position.x / size.width).coerceIn(0f, 1f)
                        aoMudar((min + x * (max - min)).toInt())
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Obsidian.void.copy(alpha = 0.6f)))
            Box(Modifier.fillMaxWidth(f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Obsidian.accent))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (f > 0f) Spacer(Modifier.weight(f))
                Box(Modifier.size(14.dp).clip(CircleShape).background(Obsidian.accent).border(2.dp, Obsidian.raised, CircleShape))
                if (f < 1f) Spacer(Modifier.weight(1f - f))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text("$valor", style = TextStyle(color = Obsidian.text2, fontSize = 11.sp), modifier = Modifier.width(34.dp))
    }
}

@Composable
private fun BotaoSalvarEnquadramento(mudou: Boolean, aoClicar: () -> Unit) {
    Text(
        if (mudou) "salvar enquadramento" else "enquadramento salvo",
        style = TextStyle(color = if (mudou) Obsidian.accent else Obsidian.text3, fontSize = 12.sp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (mudou) Obsidian.accentDim else Obsidian.borderDim, RoundedCornerShape(8.dp))
            .clickable(enabled = mudou, onClick = aoClicar)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}
