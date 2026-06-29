import Redis from 'ioredis'

export const redis = new Redis(process.env.REDIS_URL ?? 'redis://localhost:6379', {
  maxRetriesPerRequest: 3,
  enableReadyCheck: true,
  retryStrategy(times) {
    if (times > 10) {
      console.error('[Redis] Falha ao reconectar após 10 tentativas')
      return null
    }
    return Math.min(times * 100, 3000)
  },
})

redis.on('connect', () => console.log('[Redis] Conectado'))
redis.on('error', (err) => console.error('[Redis] Erro:', err.message))

const PRESENCE_TTL = 60

export type PresenceStatus = 'ONLINE' | 'IDLE' | 'DND' | 'INVISIBLE'

export const presenceKeys = {
  user: (userId: string) => `presence:user:${userId}`,
}

export async function setUserOnline(userId: string, status: PresenceStatus = 'ONLINE'): Promise<void> {
  await redis.setex(presenceKeys.user(userId), PRESENCE_TTL, status)
}

export async function setUserOffline(userId: string): Promise<void> {
  await redis.del(presenceKeys.user(userId))
}

export async function refreshPresence(userId: string): Promise<void> {
  await redis.expire(presenceKeys.user(userId), PRESENCE_TTL)
}

export async function getUserStatus(userId: string): Promise<PresenceStatus | null> {
  const v = await redis.get(presenceKeys.user(userId))
  if (!v) return null

  return v as PresenceStatus
}

export async function isUserOnline(userId: string): Promise<boolean> {
  const result = await redis.exists(presenceKeys.user(userId))
  return result === 1
}

export async function blacklistToken(jti: string, expiresInSeconds: number): Promise<void> {
  await redis.setex(`blacklist:token:${jti}`, expiresInSeconds, '1')
}

export async function isTokenBlacklisted(jti: string): Promise<boolean> {
  const result = await redis.exists(`blacklist:token:${jti}`)
  return result === 1
}
