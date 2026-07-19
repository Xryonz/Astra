import { and, eq } from 'drizzle-orm'
import { db } from '../db'
import { mutedMembers } from '../db/schema'
import { redis } from './redis'

// Calibragem: o messageLimiter (middleware/rateLimiter) ja barra em 20 msg/10s
// com um 429 passageiro que se cura sozinho. Este detector e a rede DEPOIS dela,
// pra flood sustentado — entao o teto tem que ficar ABAIXO do limiter mas longe
// da conversa normal. Estava em 5/10s, ou seja 4x mais rigido que o limiter:
// mandar 6 mensagens rapidas num canal auto-silenciava por 5 minutos, e como
// nenhuma rota chamava unmuteUser, so restava esperar.
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
  const count = await redis.incr(key)
  if (count === 1) await redis.expire(key, WINDOW_SECONDS)
  return { spamDetected: count > MAX_MESSAGES, messageCount: count }
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

  await redis.setex(muteKey(userId, serverId), ttl, '1')
}

export async function isUserMuted(
  userId: string,
  serverId: string
): Promise<boolean> {
  if (!serverId || typeof serverId !== 'string') return false

  const cached = await redis.exists(muteKey(userId, serverId))
  if (cached) return true

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
  if (ttl > 0) await redis.setex(muteKey(userId, serverId), ttl, '1')

  return true
}

export async function unmuteUser(userId: string, serverId: string): Promise<void> {
  await db.delete(mutedMembers)
    .where(and(eq(mutedMembers.userId, userId), eq(mutedMembers.serverId, serverId)))
  await redis.del(muteKey(userId, serverId))
}

export async function getMuteExpiry(userId: string, serverId: string): Promise<number> {
  if (!serverId || typeof serverId !== 'string') return 0

  const ttl = await redis.ttl(muteKey(userId, serverId))
  if (ttl > 0) return ttl

  const [mute] = await db.select().from(mutedMembers)
    .where(and(eq(mutedMembers.userId, userId), eq(mutedMembers.serverId, serverId)))
    .limit(1)
  if (!mute || mute.expiresAt < new Date()) return 0

  const secondsLeft = Math.floor((mute.expiresAt.getTime() - Date.now()) / 1000)
  if (secondsLeft > 0) await redis.setex(muteKey(userId, serverId), secondsLeft, '1')
  return Math.max(0, secondsLeft)
}

