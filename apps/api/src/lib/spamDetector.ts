import { and, eq } from 'drizzle-orm'
import { db } from '../db'
import { mutedMembers } from '../db/schema'
import { redis } from './redis'

const WINDOW_SECONDS = 10
const MAX_MESSAGES = 15
const MUTE_SECONDS = 60

const spamKey = (userId: string, channelId: string) =>
  `spam:${userId}:${channelId}`

const muteKey = (userId: string, serverId: string) =>
  `muted:${userId}:${serverId}`

export async function trackMessage(
  userId: string,
  channelId: string
): Promise<{ spamDetected: boolean; messageCount: number }> {
  const key = spamKey(userId, channelId)
  try {
    const count = await redis.incr(key)
    if (count === 1) await redis.expire(key, WINDOW_SECONDS)
    return { spamDetected: count > MAX_MESSAGES, messageCount: count }
  } catch {
    return { spamDetected: false, messageCount: 0 }
  }
}

export async function muteUser(
  userId: string,
  serverId: string,
  botUserId: string,
  reason = 'Spam automatico'
): Promise<void> {
  if (!serverId || typeof serverId !== 'string') return

  const expiresAt = new Date(Date.now() + MUTE_SECONDS * 1000)
  const ttl = Math.max(1, Math.floor((expiresAt.getTime() - Date.now()) / 1000))

  await db.insert(mutedMembers).values({
    userId, serverId, mutedById: botUserId, reason, expiresAt,
  })
  .onConflictDoUpdate({
    target: [mutedMembers.userId, mutedMembers.serverId],
    set: { mutedById: botUserId, reason, expiresAt, createdAt: new Date() },
  })

  try { await redis.setex(muteKey(userId, serverId), ttl, '1') } catch {  }
}

export async function isUserMuted(
  userId: string,
  serverId: string
): Promise<boolean> {
  if (!serverId || typeof serverId !== 'string') return false

  try {
    if (await redis.exists(muteKey(userId, serverId))) return true
  } catch {  }

  const [mute] = await db.select().from(mutedMembers)
    .where(and(eq(mutedMembers.userId, userId), eq(mutedMembers.serverId, serverId)))
    .limit(1)

  if (!mute) return false

  if (mute.expiresAt < new Date()) {
    await db.delete(mutedMembers)
      .where(and(eq(mutedMembers.userId, userId), eq(mutedMembers.serverId, serverId)))
    return false
  }

  const ttl = Math.floor((mute.expiresAt.getTime() - Date.now()) / 1000)
  if (ttl > 0) { try { await redis.setex(muteKey(userId, serverId), ttl, '1') } catch {  } }

  return true
}

export async function unmuteUser(userId: string, serverId: string): Promise<void> {
  await db.delete(mutedMembers)
    .where(and(eq(mutedMembers.userId, userId), eq(mutedMembers.serverId, serverId)))
  try { await redis.del(muteKey(userId, serverId)) } catch {  }
}

export async function getMuteExpiry(userId: string, serverId: string): Promise<number> {
  if (!serverId || typeof serverId !== 'string') return 0

  try {
    const ttl = await redis.ttl(muteKey(userId, serverId))
    if (ttl > 0) return ttl
  } catch {  }

  const [mute] = await db.select().from(mutedMembers)
    .where(and(eq(mutedMembers.userId, userId), eq(mutedMembers.serverId, serverId)))
    .limit(1)
  if (!mute || mute.expiresAt < new Date()) return 0

  const secondsLeft = Math.floor((mute.expiresAt.getTime() - Date.now()) / 1000)
  if (secondsLeft > 0) { try { await redis.setex(muteKey(userId, serverId), secondsLeft, '1') } catch {  } }
  return Math.max(0, secondsLeft)
}
