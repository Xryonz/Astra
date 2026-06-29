
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

export async function invalidateMembersCache(serverId: string): Promise<void> {
  await redis.del(key(serverId))
}
