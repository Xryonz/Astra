package app.astra.mobile.feature.casca

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.astra.mobile.feature.home.HomeViewModel
import app.astra.mobile.feature.profile.domain.model.UserStatus
import app.astra.mobile.ui.components.EmptyState
import app.astra.mobile.ui.theme.astraColors
import app.astra.mobile.ui.components.ItemDeMenu

@Composable
fun Casca(
    aoAbrirCanal: (id: String, nome: String, orbitaId: String) -> Unit,
    aoAbrirSussurro: (id: String, nome: String) -> Unit,
    aoAbrirAjustesDaOrbita: (serverId: String) -> Unit,
    aoEntrarNaVoz: (canalId: String, nome: String, orbitaId: String) -> Unit,
    aoAbrirBusca: () -> Unit,
    aoAbrirAmigos: () -> Unit,
    aoAbrirAvisos: () -> Unit,
    aoAbrirPerfil: () -> Unit,
    aoEditarPerfil: () -> Unit,
    aoAbrirDescobrir: () -> Unit,
    aoAbrirConvite: () -> Unit,
    aoAbrirOnboarding: () -> Unit,
    aoAbrirVerificacao: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val estado by viewModel.state.collectAsState()
    var menuDeStatus by remember { mutableStateOf(false) }
    var menuDeAdicionar by remember { mutableStateOf(false) }
    var forjando by remember { mutableStateOf(false) }
    var forjaDeGrupo by remember { mutableStateOf(false) }
    var novoSussurro by remember { mutableStateOf(false) }
    var folhaDoPerfil by remember { mutableStateOf(false) }
    var ultimaOrbita by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(estado.selectedServerId) {
        estado.selectedServerId?.let { ultimaOrbita = it }
    }

    LaunchedEffect(estado.needsOnboarding) {
        if (estado.needsOnboarding) {
            viewModel.consumeOnboarding()
            aoAbrirOnboarding()
        }
    }
    LaunchedEffect(estado.needsEmailVerify) {
        if (estado.needsEmailVerify) {
            viewModel.consumeEmailVerify()
            aoAbrirVerificacao()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.opened.collect { conversa ->
            novoSussurro = false
            aoAbrirSussurro(conversa.conversationId, conversa.otherName)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.serverCreated.collect { orbita ->
            forjando = false
            viewModel.selectServer(orbita.id)
        }
    }

    val dono = LocalLifecycleOwner.current
    DisposableEffect(dono) {
        val observador = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshProfile()
                viewModel.refreshServers()
                viewModel.refreshNotifications()
            }
        }
        dono.lifecycle.addObserver(observador)
        onDispose { dono.lifecycle.removeObserver(observador) }
    }

    val orbitaAberta = estado.servers.firstOrNull { it.id == estado.selectedServerId }
    val emSussurros = orbitaAberta == null

    Column(
        Modifier
            .fillMaxSize()
            .background(astraColors.base)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Box {
            ColunaDeOrbitas(
                orbitas = estado.servers,
                orbitaAberta = estado.selectedServerId,
                emSussurros = emSussurros,
                naoLidas = estado.channelUnread,
                silenciadas = estado.mutedServers,
                aoAbrirSussurros = { viewModel.selectServer(null) },
                aoAbrirOrbita = { viewModel.selectServer(it) },
                aoSegurarOrbita = aoAbrirAjustesDaOrbita,
                aoAdicionar = { menuDeAdicionar = true },
            )
            DropdownMenu(
                expanded = menuDeAdicionar,
                onDismissRequest = { menuDeAdicionar = false },
                modifier = Modifier.background(astraColors.overlay),
            ) {
                ItemDoMenu("Forjar constelação") { menuDeAdicionar = false; forjaDeGrupo = false; forjando = true }
                ItemDoMenu("Forjar aglomerado") { menuDeAdicionar = false; forjaDeGrupo = true; forjando = true }
                ItemDoMenu("Entrar por convite") { menuDeAdicionar = false; aoAbrirConvite() }
                ItemDoMenu("Descobrir") { menuDeAdicionar = false; aoAbrirDescobrir() }
            }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 16.dp))
                    .background(astraColors.base)
                    .border(1.dp, astraColors.border, RoundedCornerShape(topStart = 16.dp)),
            ) {
                if (orbitaAberta != null) {
                    PainelDeCanais(
                        orbita = orbitaAberta,
                        canalAberto = null,
                        naoLidos = estado.channelUnread,
                        vozAtiva = estado.activeVoice,
                        aoAbrirCanal = { canal ->
                            viewModel.markChannelSeen(canal.id)
                            aoAbrirCanal(canal.id, canal.name, orbitaAberta.id)
                        },
                        aoEntrarNaVoz = { canal -> aoEntrarNaVoz(canal.id, canal.name, orbitaAberta.id) },
                        aoBuscar = aoAbrirBusca,
                        aoAbrirAjustes = { aoAbrirAjustesDaOrbita(orbitaAberta.id) },
                    )
                } else if (estado.loading && estado.dms.isEmpty() && estado.servers.isEmpty()) {
                    EmptyState(line = "Abrindo o céu", hint = "um instante")
                } else {
                    PainelDeSussurros(
                        sussurros = estado.dms,
                        naoLidos = estado.unread,
                        silenciados = estado.mutedConvs,
                        aoAbrir = { conversa ->
                            viewModel.markSeen(conversa.id)
                            aoAbrirSussurro(conversa.id, conversa.otherName)
                        },
                        aoBuscar = aoAbrirBusca,
                        aoAbrirAmigos = aoAbrirAmigos,
                        aoNovoSussurro = { novoSussurro = true },
                        aoSilenciar = { conversa, silenciar ->
                            viewModel.silenciarConversa(conversa.id, silenciar)
                        },
                        aoFechar = { conversa -> viewModel.fecharConversa(conversa.id) },
                        pedidos = estado.pedidosDeAmizade,
                    )
                }
            }
        }

        Box {
            BarraPessoal(
                nome = estado.myName.ifBlank { estado.myUsername },
                avatar = estado.myAvatar,
                status = estado.myStatus,
                recado = estado.myCustomStatus,
                avisos = estado.unreadNotifs,
                aoTocar = { folhaDoPerfil = true },
                aoSegurar = { menuDeStatus = true },
                aoAbrirAvisos = aoAbrirAvisos,
                aoDeslizar = {
                    if (emSussurros) {
                        val volta = ultimaOrbita?.takeIf { alvo -> estado.servers.any { it.id == alvo } }
                        viewModel.selectServer(volta ?: estado.servers.firstOrNull()?.id)
                    } else {
                        viewModel.selectServer(null)
                    }
                },
            )
            DropdownMenu(
                expanded = menuDeStatus,
                onDismissRequest = { menuDeStatus = false },
                modifier = Modifier.background(astraColors.overlay),
            ) {
                ItemDeStatus("Disponível", UserStatus.ONLINE, viewModel) { menuDeStatus = false }
                ItemDeStatus("Ausente", UserStatus.IDLE, viewModel) { menuDeStatus = false }
                ItemDeStatus("Não perturbe", UserStatus.DND, viewModel) { menuDeStatus = false }
                ItemDeStatus("Invisível", UserStatus.INVISIBLE, viewModel) { menuDeStatus = false }
            }
        }
    }

    DialogoDeForjar(
        aberto = forjando,
        grupo = forjaDeGrupo,
        criando = estado.creating,
        erro = estado.createError,
        aoConfirmar = { nome -> viewModel.createServer(nome, forjaDeGrupo) },
        aoFechar = { forjando = false; viewModel.clearCreateError() },
    )

    DialogoDeNovoSussurro(
        aberto = novoSussurro,
        abrindo = estado.opening,
        erro = estado.openError,
        aoConfirmar = { usuario -> viewModel.openConversation(usuario) },
        aoFechar = { novoSussurro = false; viewModel.clearOpenError() },
    )

    if (folhaDoPerfil) {
        FolhaDoPerfil(
            nome = estado.myName.ifBlank { estado.myUsername },
            usuario = estado.myUsername,
            avatar = estado.myAvatar,
            banner = estado.myBanner,
            corDoBanner = estado.myBannerColor,
            status = estado.myStatus,
            recado = estado.myCustomStatus,
            pronomes = estado.myPronouns,
            bio = estado.myBio,
            emblemas = estado.myBadges,
            aoEditar = { folhaDoPerfil = false; aoEditarPerfil() },
            aoTrocarStatus = { menuDeStatus = true },
            aoAbrirAjustes = { folhaDoPerfil = false; aoAbrirPerfil() },
            aoFechar = { folhaDoPerfil = false },
        )
    }

    if (estado.needsPassword) {
        PortaoDeSenha(
            salvando = estado.pwSaving,
            erro = estado.pwError,
            aoEnviar = { senha, confirmacao -> viewModel.setPassword(senha, confirmacao) },
        )
    }
}

@Composable
private fun ItemDoMenu(rotulo: String, aoTocar: () -> Unit) {
    ItemDeMenu(
        text = { Text(rotulo, color = astraColors.text1) },
        onClick = aoTocar,
    )
}

@Composable
private fun ItemDeStatus(
    rotulo: String,
    status: UserStatus,
    viewModel: HomeViewModel,
    aoEscolher: () -> Unit,
) {
    ItemDeMenu(
        text = { Text(rotulo, color = astraColors.text1) },
        onClick = {
            viewModel.setStatus(status)
            aoEscolher()
        },
    )
}
