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

export function presenceChanged(userId: string, status: string) {
  io?.emit('presence_update', { userId, status: status === 'INVISIBLE' ? 'OFFLINE' : status })
}
