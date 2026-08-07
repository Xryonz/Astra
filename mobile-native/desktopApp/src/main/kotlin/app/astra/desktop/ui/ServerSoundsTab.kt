package app.astra.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.desktop.ui.theme.DmMono
import app.astra.desktop.ui.theme.Obsidian
import app.astra.desktop.ui.theme.Text
import app.astra.desktop.voice.ConversorDeSom
import app.astra.desktop.voice.SoundboardPlayer
import app.astra.mobile.core.network.SoundApi
import app.astra.mobile.core.network.UploadApi
import app.astra.mobile.core.network.dto.CriarSomRequest
import app.astra.mobile.core.network.dto.ServerSoundDto
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Upload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.koin.core.context.GlobalContext

// EFEITOS SONOROS DA CONSTELACAO.
//
// O arquivo escolhido e convertido pra WAV com o ffmpeg que o app JA empacota pra
// transmissao de tela. Isso NAO perde qualidade: decodificar um MP3 e uma operacao
// exata, e o WAV guarda exatamente o que saiu do decodificador. O que se perde e
// quando se RE-ENCODA (MP3 -> MP3), e nao e o caso aqui.
//
// Converter existe por um motivo pratico: o JDK toca WAV sozinho, sem biblioteca
// nenhuma. Aceitar MP3 direto exigiria embarcar um decodificador so pra isso.

@Composable
internal fun SoundsSection(serverId: String, podeGerenciar: Boolean) {
    val soundApi = remember { GlobalContext.get().get<SoundApi>() }
    val uploadApi = remember { GlobalContext.get().get<UploadApi>() }
    val escopo = rememberCoroutineScope()

    var sons by remember(serverId) { mutableStateOf<List<ServerSoundDto>>(emptyList()) }
    var msg by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var ocupado by remember { mutableStateOf(false) }

    suspend fun recarregar() {
        runCatching { soundApi.listar(serverId).sounds }
            .onSuccess { sons = it }
            .onFailure { msg = "não deu pra carregar os sons" to false }
    }
    LaunchedEffect(serverId) { recarregar() }

    FieldLabel("sons desta constelação")
    Spacer(Modifier.height(8.dp))

    if (sons.isEmpty()) {
        Text(
            "nenhum som ainda.",
            style = TextStyle(color = Obsidian.text3, fontSize = 12.sp),
        )
    }

    sons.forEach { som ->
        LinhaDeSom(
            som = som,
            podeApagar = podeGerenciar,
            onTocar = { SoundboardPlayer.tocar(som.url) },
            onApagar = {
                escopo.launch {
                    runCatching { soundApi.apagar(serverId, som.id) }
                        .onSuccess { recarregar(); msg = "som apagado" to true }
                        .onFailure { msg = "não deu pra apagar" to false }
                }
            },
        )
        Spacer(Modifier.height(4.dp))
    }

    if (podeGerenciar) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotaoIcone(Lucide.Upload, "subir um som", accent = true, ocupado = ocupado) {
                escopo.launch {
                    val arquivo = withContext(Dispatchers.IO) { chooseFiles().firstOrNull() } ?: return@launch
                    ocupado = true
                    msg = null
                    val r = withContext(Dispatchers.IO) {
                        runCatching {
                            val wav = ConversorDeSom.paraWav(arquivo)
                                ?: error("não consegui converter esse arquivo")
                            val parte = MultipartBody.Part.createFormData(
                                "file", wav.name,
                                wav.asRequestBody("audio/wav".toMediaType()),
                            )
                            val enviado = uploadApi.upload(parte).data?.attachments?.firstOrNull()
                                ?: error("o servidor não devolveu o arquivo")
                            // O nome do som vem do ARQUIVO, sem extensao: e o que a
                            // pessoa reconhece na lista, e pedir pra digitar de novo
                            // seria burocracia pra repetir o que ela ja escolheu.
                            val nome = arquivo.nameWithoutExtension.take(40).ifBlank { "som" }
                            soundApi.criar(
                                serverId,
                                CriarSomRequest(
                                    name = nome,
                                    url = enviado.url,
                                    durationMs = ConversorDeSom.duracaoMs(wav),
                                ),
                            )
                            wav.delete()
                        }
                    }
                    ocupado = false
                    r.onSuccess { recarregar(); msg = "som adicionado" to true }
                        .onFailure { msg = (it.message ?: "não deu pra subir") to false }
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "qualquer formato serve — o Astra converte sem perder qualidade.",
                style = TextStyle(color = Obsidian.text3, fontSize = 11.sp),
            )
        }
    }

    msg?.let { (t, ok) ->
        Spacer(Modifier.height(10.dp))
        Text(t, style = TextStyle(color = if (ok) Obsidian.success else Obsidian.danger, fontSize = 12.sp))
    }
}

@Composable
private fun LinhaDeSom(
    som: ServerSoundDto,
    podeApagar: Boolean,
    onTocar: () -> Unit,
    onApagar: () -> Unit,
) {
    val src = remember(som.id) { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Row(
        Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (hov) Obsidian.hover.copy(alpha = 0.5f) else Color.Transparent)
            .border(1.dp, Obsidian.borderDim, RoundedCornerShape(8.dp))
            .hoverable(src)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BotaoIcone(Lucide.Play, "ouvir") { onTocar() }
        Spacer(Modifier.width(10.dp))
        Text(
            som.name,
            style = TextStyle(color = Obsidian.text1, fontSize = 13.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (som.durationMs > 0) {
            Text(
                "%.1fs".format(som.durationMs / 1000f),
                style = TextStyle(color = Obsidian.text3, fontSize = 10.sp, fontFamily = DmMono),
            )
            Spacer(Modifier.width(10.dp))
        }
        if (podeApagar) BotaoIcone(Lucide.Trash2, "apagar som", danger = true) { onApagar() }
    }
}
