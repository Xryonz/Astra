import { and, eq, sql } from 'drizzle-orm'
import { db } from '../db'
import { channels, messages, servers } from '../db/schema'
import { servidorDeSocket } from './realtime'
import { membrosQueVeemCanal } from './permissions'
import { getCachedMembers } from './membersCache'

async function ultimaMensagemDe(channelId: string): Promise<Date | null> {
  const [linha] = await db.select({ quando: sql<Date | null>`MAX(${messages.createdAt})` })
    .from(messages)
    .where(eq(messages.channelId, channelId))
  return linha?.quando ?? null
}

export async function canalMudou(serverId: string, channelId: string): Promise<void> {
  const io = servidorDeSocket()
  if (!io) return

  const [canal] = await db.select().from(channels)
    .where(and(eq(channels.id, channelId), eq(channels.serverId, serverId)))
    .limit(1)
  if (!canal) return

  const enriquecido = { ...canal, lastMessageAt: await ultimaMensagemDe(channelId) }

  if (!canal.isPrivate) {
    io.to(`server:${serverId}`).emit('server_channel_upserted', { serverId, canal: enriquecido })
    return
  }

  const [srv] = await db.select({ ownerId: servers.ownerId }).from(servers)
    .where(eq(servers.id, serverId)).limit(1)
  if (!srv) return

  const membros = (await getCachedMembers(serverId)).map((m) => m.userId)
  const veem = await membrosQueVeemCanal(channelId, true, srv.ownerId, membros)

  for (const userId of membros) {
    if (veem.has(userId)) {
      io.to(`user:${userId}`).emit('server_channel_upserted', { serverId, canal: enriquecido })
    } else {
      io.to(`user:${userId}`).emit('server_channel_gone', { serverId, channelId })
    }
  }
}

export function canalSumiu(serverId: string, channelId: string): void {
  servidorDeSocket()?.to(`server:${serverId}`).emit('server_channel_gone', { serverId, channelId })
}

export function categoriaMudou(serverId: string, categoria: unknown): void {
  servidorDeSocket()?.to(`server:${serverId}`).emit('server_category_upserted', { serverId, categoria })
}

export function categoriaSumiu(serverId: string, categoryId: string): void {
  servidorDeSocket()?.to(`server:${serverId}`).emit('server_category_gone', { serverId, categoryId })
}

export async function constelacaoMudou(serverId: string): Promise<void> {
  const io = servidorDeSocket()
  if (!io) return
  const [srv] = await db.select().from(servers).where(eq(servers.id, serverId)).limit(1)
  if (!srv) return
  io.to(`server:${serverId}`).emit('server_meta', { serverId, constelacao: srv })
}
