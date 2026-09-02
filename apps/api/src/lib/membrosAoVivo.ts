import { and, eq } from 'drizzle-orm'
import { db } from '../db'
import { serverMembers, users } from '../db/schema'
import { servidorDeSocket } from './realtime'
import { redis, presenceKeys } from './redis'

async function presencaDe(userId: string): Promise<string> {
  try {
    const s = await redis.get(presenceKeys.user(userId))
    return !s || s === 'INVISIBLE' ? 'OFFLINE' : s
  } catch {
    return 'OFFLINE'
  }
}

export async function membroEntrou(serverId: string, userId: string): Promise<void> {
  const io = servidorDeSocket()
  if (!io) return

  const [membro] = await db.select({
    id:        serverMembers.id,
    userId:    serverMembers.userId,
    serverId:  serverMembers.serverId,
    role:      serverMembers.role,
    nameColor: serverMembers.nameColor,
    joinedAt:  serverMembers.joinedAt,
    user: {
      id:          users.id,
      username:    users.username,
      displayName: users.displayName,
      avatarUrl:   users.avatarUrl,
      bio:         users.bio,
      displayFont: users.displayFont,
    },
  })
    .from(serverMembers)
    .innerJoin(users, eq(users.id, serverMembers.userId))
    .where(and(eq(serverMembers.serverId, serverId), eq(serverMembers.userId, userId)))
    .limit(1)

  if (!membro) return

  io.to(`server:${serverId}`).emit('server_member_added', {
    serverId,
    membro: { ...membro, roles: [], topColor: null },
    presenca: await presencaDe(userId),
  })
}

export function membroSaiu(serverId: string, userId: string): void {
  servidorDeSocket()?.to(`server:${serverId}`).emit('server_member_removed', { serverId, userId })
}

export function membroMudouDeCargo(serverId: string, memberId: string, role: string): void {
  servidorDeSocket()?.to(`server:${serverId}`).emit('server_member_role', { serverId, memberId, role })
}
