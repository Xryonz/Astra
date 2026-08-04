import type { Server as SocketServer } from 'socket.io'

// Eventos de socket disparados por rotas HTTP.
//
// O realtime so tinha salas de canal e de DM — nada que valesse pra constelacao
// inteira tinha por onde ser transmitido. Resultado: criar um canal so aparecia
// pros OUTROS quando eles reabriam o app. A sala `server:<id>` nasce em
// config/socket.ts; aqui ficam os disparos.
//
// Estas rotas sao routers `const` (nao factories `create...Router(io)` como
// messages/dm), entao o io chega por setter no boot em vez de por parametro.
// Tudo aqui e opcional: sem io, nada quebra — so nao ha aviso ao vivo.
let io: SocketServer | null = null

export function attachRealtime(server: SocketServer) { io = server }

// A lista de canais/categorias mudou -> todo mundo da constelacao refaz o
// GET /servers. Refetch em vez de delta de proposito: posicao, visibilidade e
// permissao sao decididas no backend, e um merge no cliente erraria em canal
// privado (cada um ve uma lista diferente).
export function channelsChanged(serverId: string) {
  io?.to(`server:${serverId}`).emit('server_channels', { serverId })
}

// Entrou/saiu gente: o painel de membros de quem ja esta dentro recarrega.
// Separado do de canais porque sao listas diferentes no cliente.
export function membersChanged(serverId: string) {
  io?.to(`server:${serverId}`).emit('server_members', { serverId })
}

// Alguem foi ADICIONADO a uma constelacao. Isso interessa a UMA pessoa (a rail
// dela ganha uma constelacao nova), entao vai pra sala pessoal — e nao pra do
// servidor, onde ela ainda nem entrou.
export function joinedServer(userId: string, serverId: string) {
  io?.to(`user:${userId}`).emit('server_joined', { serverId })
}

// Trocou de status pela ROTA HTTP (o seletor no rodape do desktop). O caminho por
// socket (`set_status`) ja avisava todo mundo; este nao — quem mudava pra ocupado
// continuava aparecendo online pros outros ate eles recarregarem a tela.
// INVISIBLE vira OFFLINE pra fora: e o ponto inteiro de ser invisivel.
// Alguem editou o proprio perfil (nome, avatar, banner, cor, recado, fonte).
// Global de proposito: a mesma pessoa aparece na lista de membros, na barra de
// sussurros, no autor de cada mensagem e no cartao de perfil — rastrear quem ve
// quem custaria mais do que um evento minusculo que cada cliente ignora se nao
// tiver essa pessoa na tela. So o id vai: quem se importa rebusca.
export function profileChanged(userId: string) {
  io?.emit('profile_updated', { userId })
}

// Ganhou XP. Vai so pra quem ganhou: progressao e assunto de uma pessoa so, e
// mandar pra sala do servidor faria todo mundo receber um evento por mensagem de
// todo mundo. O payload ja leva o progresso inteiro pra o anel do rodape nao ter
// que voltar no servidor perguntar quanto ficou.
export function xpGanho(userId: string, payload: unknown) {
  io?.to(`user:${userId}`).emit('xp_gain', payload)
}

// Fechou uma missao. Evento separado do xp_gain porque sao coisas diferentes na
// tela: o XP move o anel em silencio, a missao aparece com nome e recompensa. Vem um
// de cada — quem fecha as tres do dia recebe quatro eventos (as tres + o bonus), e a
// fila do toast e que resolve mostrar um por vez.
export function missaoConcluida(userId: string, payload: unknown) {
  io?.to(`user:${userId}`).emit('mission_done', payload)
}

export function presenceChanged(userId: string, status: string) {
  io?.emit('presence_update', { userId, status: status === 'INVISIBLE' ? 'OFFLINE' : status })
}

// A CONSTELACAO em si mudou: nome, icone, banner, descricao. Aparece na rail, no
// cabecalho e no cartao de convite ao mesmo tempo — mais barato mandar refazer a
// busca do que tentar remendar cada lugar.
export function serverUpdated(serverId: string) {
  io?.to(`server:${serverId}`).emit('server_updated', { serverId })
}

// A constelacao deixou de existir. Tem que sair ANTES do delete no banco: depois
// dele nao ha mais como saber quem era membro. Sem isto, todo mundo ficava com um
// icone fantasma na rail que so sumia no proximo boot — e clicar nele dava erro.
export function serverGone(serverId: string) {
  io?.to(`server:${serverId}`).emit('server_gone', { serverId })
}

// Cargos mexeram (criou, renomeou, mudou cor/permissao, deu ou tirou de alguem).
// Mexe em duas telas: o painel de cargos e a lista de membros (cor do nome e
// agrupamento por cargo). Um ping so; cada tela rebusca o que usa.
export function rolesChanged(serverId: string) {
  io?.to(`server:${serverId}`).emit('server_roles', { serverId })
}

// Perdi acesso a uma constelacao (sai, fui expulso ou banido). Vai pra sala
// pessoal porque, no caso do banimento, a pessoa ja foi tirada da sala do
// servidor — mandar pra la seria falar pra uma sala em que ela nao esta mais.
// `motivo` existe pra a pessoa saber O QUE aconteceu com ela. Sem isso a
// constelacao simplesmente sumia da tela e ela ficava sem saber se foi expulsa,
// banida, se saiu sem querer ou se a constelacao acabou — e as quatro pedem
// reacoes diferentes (voltar a entrar, pedir desculpa, nada).
// 'saiu' e o default porque leftServer tambem cobre a saida voluntaria, que nao
// e penalidade nenhuma e nao deve gerar aviso.
export function leftServer(
  userId: string,
  serverId: string,
  motivo: 'expulso' | 'banido' | 'saiu' = 'saiu',
  reason?: string | null,
) {
  io?.to(`user:${userId}`).emit('server_left', { serverId, motivo, reason: reason ?? null })
}
