package app.astra.mobile.feature.casca

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import app.astra.mobile.BuildConfig
import app.astra.mobile.feature.home.HomeViewModel
import app.astra.mobile.feature.xp.presentation.EstrelasViewModel
import app.astra.mobile.feature.xp.presentation.MoedasDeBrilho
import app.astra.mobile.feature.xp.presentation.anelDeEstrelas
import app.astra.mobile.feature.xp.presentation.lembrarVisualDasEstrelas
import app.astra.mobile.feature.server.presentation.shareInviteLink
import app.astra.mobile.ui.LocalAppPrefs
import app.astra.mobile.core.update.Novidades
import app.astra.mobile.ui.components.AstraDialog
import app.astra.mobile.ui.components.EmptyState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.astra.mobile.ui.theme.astraColors
import app.astra.mobile.ui.components.ItemDeMenu
import app.astra.mobile.ui.components.MenuDeEstado
import zed.rainxch.rikkaui.components.ui.toast.LocalToastHostState

private const val CHAVE_DOS_SUSSURROS = "sussurros"
private const val TROCA_DE_PAINEL_MS = 280

@Composable
fun Casca(
    aoAbrirCanal: (id: String, nome: String, orbitaId: String) -> Unit,
    aoAbrirSussurro: (id: String, nome: String) -> Unit,
    aoAbrirAjustesDaOrbita: (serverId: String) -> Unit,
    aoEntrarNaVoz: (canalId: String, nome: String, orbitaId: String) -> Unit,
    aoAbrirCall: () -> Unit,
    aoAbrirJornada: () -> Unit,
    aoAbrirBusca: () -> Unit,
    aoAbrirAmigos: () -> Unit,
    aoAbrirAvisos: () -> Unit,
    aoAbrirPerfil: () -> Unit,
    aoAbrirDescobrir: () -> Unit,
    aoAbrirConvite: () -> Unit,
    aoAbrirOnboarding: () -> Unit,
    aoAbrirVerificacao: () -> Unit,
    orbitaPedida: String? = null,
    aoAtenderPedido: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    estrelasViewModel: EstrelasViewModel = hiltViewModel(),
) {
    val progressoDasEstrelas = estrelasViewModel.progresso.collectAsState()
    val progresso = progressoDasEstrelas.value
    val missoesProntas by estrelasViewModel.prontas.collectAsState()
    val visualDasEstrelas = lembrarVisualDasEstrelas(progressoDasEstrelas, estrelasViewModel.estrelas)
    LaunchedEffect(orbitaPedida) {
        val id = orbitaPedida ?: return@LaunchedEffect
        aoAtenderPedido()
        viewModel.selectServer(id)
    }
    val estado by viewModel.state.collectAsState()
    var menuDeStatus by remember { mutableStateOf(false) }
    var menuDeAdicionar by remember { mutableStateOf(false) }
    var forjando by remember { mutableStateOf(false) }
    var forjaDeGrupo by remember { mutableStateOf(false) }
    var novoSussurro by remember { mutableStateOf(false) }
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
                estrelasViewModel.recarregar()
            }
        }
        dono.lifecycle.addObserver(observador)
        onDispose { dono.lifecycle.removeObserver(observador) }
    }

    val orbitaAberta = estado.servers.firstOrNull { it.id == estado.selectedServerId }
    val emSussurros = orbitaAberta == null
    val orbitasNaOrdem = remember(estado.servers, estado.ordemDasOrbitas) {
        naOrdemEscolhida(estado.servers, estado.ordemDasOrbitas)
    }
    var ultimoCanal by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var canalNoMenuId by remember { mutableStateOf<String?>(null) }
    val canalNoMenu = canalNoMenuId?.let { id -> orbitaAberta?.channels?.firstOrNull { it.id == id } }
    val contexto = LocalContext.current
    val semMovimento = LocalAppPrefs.current.reduceMotion
    val aviso = LocalToastHostState.current
    LaunchedEffect(estado.manageError) {
        val erro = estado.manageError ?: return@LaunchedEffect
        viewModel.clearManageError()
        aviso.show(erro)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(astraColors.void)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Box {
            ColunaDeOrbitas(
                orbitas = orbitasNaOrdem,
                orbitaAberta = estado.selectedServerId,
                emSussurros = emSussurros,
                sussurroNaoLido = estado.unread.isNotEmpty(),
                naoLidas = remember(estado.servers, estado.channelUnread) {
                    estado.servers.filter { o -> o.channels.any { it.id in estado.channelUnread } }.mapTo(HashSet()) { it.id }
                },
                silenciadas = estado.mutedServers,
                aoAbrirSussurros = { viewModel.selectServer(null) },
                aoAbrirOrbita = { viewModel.selectServer(it) },
                aoSegurarOrbita = aoAbrirAjustesDaOrbita,
                aoReordenar = viewModel::guardarOrdemDasOrbitas,
                aoAdicionar = { menuDeAdicionar = true },
            )
            DropdownMenu(
                expanded = menuDeAdicionar,
                onDismissRequest = { menuDeAdicionar = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = astraColors.overlay,
                border = BorderStroke(1.dp, astraColors.border),
                offset = DpOffset(x = 8.dp, y = 0.dp),
            ) {
                ItemDoMenu("Forjar constelação") { menuDeAdicionar = false; forjaDeGrupo = false; forjando = true }
                ItemDoMenu("Forjar aglomerado") { menuDeAdicionar = false; forjaDeGrupo = true; forjando = true }
                ItemDoMenu("Entrar por convite") { menuDeAdicionar = false; aoAbrirConvite() }
                ItemDoMenu("Descobrir") { menuDeAdicionar = false; aoAbrirDescobrir() }
            }
            }

            val formaDoPainel = RoundedCornerShape(22.dp)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(top = 6.dp, bottom = 6.dp, end = 6.dp)
                    .clip(formaDoPainel)
                    .background(astraColors.base)
                    .border(1.dp, astraColors.border, formaDoPainel),
            ) {
                AnimatedContent(
                    targetState = orbitaAberta?.id ?: CHAVE_DOS_SUSSURROS,
                    transitionSpec = {
                        if (semMovimento) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            fadeIn(tween(TROCA_DE_PAINEL_MS)) togetherWith fadeOut(tween(TROCA_DE_PAINEL_MS))
                        }
                    },
                    label = "painel",
                ) { chave ->
                val orbita = estado.servers.firstOrNull { it.id == chave }
                if (orbita != null) {
                    PainelDeCanais(
                        orbita = orbita,
                        canalAberto = ultimoCanal[orbita.id],
                        naoLidos = estado.channelUnread,
                        naVoz = remember(estado.naVoz, estado.membrosDaOrbita, estado.myId, estado.myName, estado.myAvatar) {
                            estado.naVoz.mapValues { (_, ids) ->
                                ids.map { id ->
                                    val membro = estado.membrosDaOrbita[id]
                                    val souEu = id == estado.myId
                                    PessoaNaVoz(
                                        id = id,
                                        nome = when {
                                            souEu -> estado.myName.ifBlank { estado.myUsername }
                                            else -> membro?.name ?: "Alguém"
                                        },
                                        foto = if (souEu) estado.myAvatar else membro?.avatarUrl,
                                        souEu = souEu,
                                    )
                                }
                            }
                        },
                        recolhidas = estado.categoriasRecolhidas,
                        podeArrumar = estado.podeArrumar,
                        aoAbrirCanal = { canal ->
                            ultimoCanal = ultimoCanal + (orbita.id to canal.id)
                            viewModel.markChannelSeen(canal.id)
                            aoAbrirCanal(canal.id, canal.name, orbita.id)
                        },
                        aoEntrarNaVoz = { canal -> aoEntrarNaVoz(canal.id, canal.name, orbita.id) },
                        aoSegurarCanal = { canal -> canalNoMenuId = canal.id },
                        aoBuscar = aoAbrirBusca,
                        aoConvidar = orbita.inviteCode?.let { codigo ->
                            { shareInviteLink(contexto, BuildConfig.BASE_URL.trimEnd('/') + "/i/" + codigo) }
                        },
                        aoAbrirAjustes = { aoAbrirAjustesDaOrbita(orbita.id) },
                        aoAlternarCategoria = viewModel::alternarCategoria,
                        aoReordenarCanais = { ids -> viewModel.reordenarCanais(orbita.id, ids) },
                        aoMoverParaCategoria = { canalId, categoriaId ->
                            viewModel.moverCanalParaCategoria(orbita.id, canalId, categoriaId)
                        },
                        aoReordenarCategorias = { ids -> viewModel.reordenarCategorias(orbita.id, ids) },
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
        }

        FaixaDaCall(aoAbrir = aoAbrirCall)

        Box {
            MoedasDeBrilho(
                estrelas = estrelasViewModel.estrelas,
                modifier = Modifier.align(Alignment.TopCenter),
            )
            BarraPessoal(
                nome = estado.myName.ifBlank { estado.myUsername },
                avatar = estado.myAvatar,
                status = estado.myStatus,
                recado = estado.myCustomStatus,
                avisos = estado.unreadNotifs,
                nivel = progresso.nivel,
                missoesProntas = missoesProntas,
                anel = Modifier.anelDeEstrelas(visualDasEstrelas, astraColors.accent, astraColors.border),
                aoTocar = aoAbrirPerfil,
                aoSegurar = { menuDeStatus = true },
                aoAbrirAvisos = aoAbrirAvisos,
                aoAbrirJornada = aoAbrirJornada,
                aoAbrirStatus = { menuDeStatus = true },
                aoDeslizar = {
                    if (emSussurros) {
                        val volta = ultimaOrbita?.takeIf { alvo -> estado.servers.any { it.id == alvo } }
                        viewModel.selectServer(volta ?: estado.servers.firstOrNull()?.id)
                    } else {
                        viewModel.selectServer(null)
                    }
                },
                menuDoEstado = {
                    MenuDeEstado(
                        aberto = menuDeStatus,
                        atual = estado.myStatus,
                        aoEscolher = { escolhido ->
                            viewModel.setStatus(escolhido)
                            menuDeStatus = false
                        },
                        aoFechar = { menuDeStatus = false },
                        deslocamento = DpOffset(0.dp, 14.dp),
                    )
                },
            )
        }
    }

    canalNoMenu?.let { canal ->
        val orbitaDoMenu = orbitaAberta?.id
        MenuDoCanal(
            canal = canal,
            silenciado = canal.id in estado.mutedChannels,
            naoLido = canal.id in estado.channelUnread,
            podeMexerNaBot = estado.podeArrumar,
            aoFechar = { canalNoMenuId = null },
            aoSilenciar = { silenciar -> viewModel.silenciarCanal(canal.id, silenciar) },
            aoMarcarComoLido = { viewModel.marcarCanalComoLido(canal.id) },
            aoMudarBot = { atende -> orbitaDoMenu?.let { viewModel.botAtendeNoCanal(it, canal.id, atende) } },
            aoMudarRespostas = { guardar -> orbitaDoMenu?.let { viewModel.guardarRespostasDaBot(it, canal.id, guardar) } },
        )
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

    var novidades by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(Unit) { novidades = withContext(Dispatchers.IO) { Novidades.paraMostrar(contexto) } }
    val itensNovos = novidades
    if (itensNovos != null && !estado.needsPassword) {
        val fechar = {
            Novidades.marcarVistas(contexto)
            novidades = null
        }
        AstraDialog(
            open = true,
            onDismiss = fechar,
            title = "O que mudou na ${BuildConfig.VERSION_NAME}",
            confirmText = "Entendi",
            onConfirm = fechar,
            dismissText = null,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itensNovos.forEach { item ->
                    Row {
                        Text("·", color = astraColors.accent, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(8.dp))
                        Text(item, color = astraColors.text2, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
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
