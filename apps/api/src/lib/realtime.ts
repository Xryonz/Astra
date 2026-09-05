import type { Server as SocketServer } from 'socket.io'
import { salasQueMeVeem } from './quemMeVe'

let io: SocketServer | null = null

export function attachRealtime(server: SocketServer) { io = server }

export function servidorDeSocket(): SocketServer | null { return io }

export function joinedServer(userId: string, serverId: string) {
  io?.to(`user:${userId}`).emit('server_joined', { serverId })
}

export type PerfilPublico = {
  username: string
  displayName: string | null
  avatarUrl: string | null
  displayFont: string | null
}

export async function avisarQuemMeVe(
  userId: string,
  evento: string,
  dados: unknown,
): Promise<void> {
  const servidor = io
  if (!servidor) return
  const salas = await salasQueMeVeem(userId)
  if (!salas || !salas.length) {
    servidor.emit(evento, dados)
    return
  }
  let alvo = servidor.to(salas[0])
  for (const sala of salas.slice(1)) alvo = alvo.to(sala)
  alvo.emit(evento, dados)
}

export function profileChanged(userId: string, publico?: PerfilPublico): Promise<void> {
  return avisarQuemMeVe(userId, 'profile_updated', publico ? { userId, publico } : { userId })
}

export function amizadeMudou(a: string, b: string, motivo: 'pedido' | 'aceito' | 'removido') {
  for (const id of new Set([a, b])) {
    io?.to(`user:${id}`).emit('friends_changed', { motivo })
  }
}

export function entregarSussurro(
  server: SocketServer,
  conversationId: string,
  participantes: ReadonlyArray<string | null | undefined>,
  evento: string,
  dados: unknown,
) {
  let alvo = server.to(`dm:${conversationId}`)
  for (const id of new Set(participantes.filter(Boolean) as string[])) {
    alvo = alvo.to(`user:${id}`)
  }
  alvo.emit(evento, dados)
}

export function xpGanho(userId: string, payload: unknown) {
  io?.to(`user:${userId}`).emit('xp_gain', payload)
}

export function missaoConcluida(userId: string, payload: unknown) {
  io?.to(`user:${userId}`).emit('mission_done', payload)
}

export function presenceChanged(userId: string, status: string): Promise<void> {
  return avisarQuemMeVe(userId, 'presence_update', {
    userId,
    status: status === 'INVISIBLE' ? 'OFFLINE' : status,
  })
}

export function serverGone(serverId: string) {
  io?.to(`server:${serverId}`).emit('server_gone', { serverId })
}

export function leftServer(
  userId: string,
  serverId: string,
  motivo: 'expulso' | 'banido' | 'saiu' = 'saiu',
  reason?: string | null,
) {
  io?.to(`user:${userId}`).emit('server_left', { serverId, motivo, reason: reason ?? null })
}
