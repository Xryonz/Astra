/**
 * membersCache — cache Redis de `[{userId, username}]` por servidor.
 *
 * Por quê: parseMentions (hot path do chat) precisa resolver @username
 * → userId em todo envio. Sem cache, é 1 DB hit por mensagem.
 *
 * Estratégia: cache write-through com TTL 60s. Invalidação manual em
 * todas as rotas que mutam membership (invite accept, kick, leave, ban,
 * add-via-friend). Pior caso de stale: 60s — aceitável.
 */
import { db } from '../db'
import { eq } from 'drizzle-orm'
import { serverMembers, users } from '../db/schema'
import { redis } from './redis'

const TTL_SECONDS = 60
const key = (serverId: string) => `members:list:${serverId}`

export interface CachedMember {
  userId:   string
  username: string
}

export async function getCachedMembers(serverId: string): Promise<CachedMember[]> {
  const cached = await redis.get(key(serverId))
  if (cached) {
    try { return JSON.parse(cached) as CachedMember[] } catch {}
  }
  const rows = await db.select({ userId: serverMembers.userId, username: users.username })
    .from(serverMembers)
    .innerJoin(users, eq(users.id, serverMembers.userId))
    .where(eq(serverMembers.serverId, serverId))
  await redis.setex(key(serverId), TTL_SECONDS, JSON.stringify(rows))
  return rows
}

/** Invalida cache imediatamente. Chamar após qualquer mudança de membership. */
export async function invalidateMembersCache(serverId: string): Promise<void> {
  await redis.del(key(serverId))
}
