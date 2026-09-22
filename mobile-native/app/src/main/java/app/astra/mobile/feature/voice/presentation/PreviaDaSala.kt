package app.astra.mobile.feature.voice.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.astra.mobile.core.voice.SalasDeVoz
import app.astra.mobile.core.voice.VoiceManager
import app.astra.mobile.feature.server.domain.ServerRepository
import app.astra.mobile.feature.server.domain.model.ServerMember
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.ui.components.AlcaDaFolha
import app.astra.mobile.ui.components.AstraAvatar
import app.astra.mobile.ui.components.AstraButton
import app.astra.mobile.ui.components.FolhaQueSobe
import app.astra.mobile.ui.components.rememberEstadoDaFolha
import app.astra.mobile.ui.theme.DmSerif
import app.astra.mobile.ui.theme.astraColors
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Volume2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PessoaNaSala(val id: String, val nome: String, val foto: String?)

data class PreviaUiState(
    val pessoas: List<PessoaNaSala> = emptyList(),
    val outraCall: String? = null,
    val jaEstouAqui: Boolean = false,
)

@HiltViewModel
class PreviaDaSalaViewModel @Inject constructor(
    private val voiceManager: VoiceManager,
    private val serverRepository: ServerRepository,
    salasDeVoz: SalasDeVoz,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val salaId: String = savedStateHandle["channelId"] ?: ""
    val nome: String = savedStateHandle["name"] ?: ""
    val orbitaId: String = savedStateHandle["serverId"] ?: ""

    private val membros = MutableStateFlow<Map<String, ServerMember>>(emptyMap())

    val state: StateFlow<PreviaUiState> =
        combine(salasDeVoz.quem, membros, voiceManager.state) { quem, porId, voz ->
            PreviaUiState(
                pessoas = quem[salaId].orEmpty().map { id ->
                    val m = porId[id]
                    PessoaNaSala(id, m?.name ?: "Alguém", m?.avatarUrl)
                },
                outraCall = voz.sala?.takeIf { voz.naCall && it.id != salaId }?.nome,
                jaEstouAqui = voz.naCall && voz.sala?.id == salaId,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreviaUiState())

    init {
        viewModelScope.launch { salasDeVoz.carregar(listOf(salaId)) }
        if (orbitaId.isNotBlank()) {
            viewModelScope.launch {
                serverRepository.members(orbitaId).onSuccess { lista -> membros.value = lista.associateBy { it.userId } }
            }
        }
    }

    fun entrar() = voiceManager.entrar(VoiceManager.TIPO_CANAL, salaId, nome, orbitaId.ifBlank { null })
}

@Composable
fun PreviaDaSala(
    aoFechar: () -> Unit,
    aoEntrar: () -> Unit,
    viewModel: PreviaDaSalaViewModel = hiltViewModel(),
) {
    val estado by viewModel.state.collectAsState()
    val contexto = LocalContext.current
    val folha = rememberEstadoDaFolha(aoFechar)
    val semMovimento = LocalAppPrefs.current.reduceMotion
    var negado by remember { mutableStateOf(false) }

    val entrarDeVez = {
        viewModel.entrar()
        folha.fechar(aoEntrar)
    }
    val pedirMicrofone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { liberado ->
        if (liberado) entrarDeVez() else negado = true
    }

    FolhaQueSobe(folha, rotuloDoFundo = "Fechar a prévia da sala") {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 20.dp)
                .then(if (semMovimento) Modifier else Modifier.animateContentSize(tween(220))),
        ) {
            AlcaDaFolha(Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.Volume2, contentDescription = null, tint = astraColors.text2, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    viewModel.nome,
                    fontFamily = DmSerif,
                    style = MaterialTheme.typography.headlineSmall,
                    color = astraColors.text1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            val n = estado.pessoas.size
            Text(
                when (n) {
                    0 -> "Ninguém na sala ainda."
                    1 -> "1 pessoa na sala"
                    else -> "$n pessoas na sala"
                },
                fontSize = 13.sp,
                color = astraColors.text3,
            )

            if (estado.pessoas.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                val forma = RoundedCornerShape(8.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(forma)
                        .background(astraColors.raised)
                        .border(1.dp, astraColors.border, forma)
                        .padding(vertical = 6.dp),
                ) {
                    estado.pessoas.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AstraAvatar(p.foto, p.nome, size = 30)
                            Spacer(Modifier.width(12.dp))
                            Text(p.nome, fontSize = 15.sp, color = astraColors.text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            estado.outraCall?.let { outra ->
                Spacer(Modifier.height(14.dp))
                Text("Ao entrar, você sai de $outra.", fontSize = 13.sp, color = astraColors.text2)
            }
            if (negado) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "O microfone está bloqueado para o Astra. Libere nas configurações do Android para entrar na call.",
                    fontSize = 13.sp,
                    color = astraColors.danger,
                )
            }

            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AstraButton(
                    text = when {
                        estado.jaEstouAqui -> "Voltar para a call"
                        estado.outraCall != null -> "Trocar de sala"
                        else -> "Entrar"
                    },
                    onClick = {
                        val temMicrofone = ContextCompat.checkSelfPermission(contexto, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        if (temMicrofone) entrarDeVez() else pedirMicrofone.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
