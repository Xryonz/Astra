import Redis from 'ioredis'

// ioredis reconecta automaticamente — configuramos limites
// para não tentar reconectar infinitamente em produção
export const redis = new Redis(process.env.REDIS_URL ?? 'redis://localhost:6379', {
  maxRetriesPerRequest: 3,
  enableReadyCheck: true,
  retryStrategy(times) {
    if (times > 10) {
      console.error('[Redis] Falha ao reconectar após 10 tentativas')
      return null // Para de tentar
    }
    return Math.min(times * 100, 3000) // Backoff exponencial até 3s
  },
})

redis.on('connect', () => console.log('[Redis] Conectado'))
redis.on('error', (err) => console.error('[Redis] Erro:', err.message))

// ─────────────────────────────────────────────
// HELPERS DE PRESENÇA
// TTL de 60s: o cliente deve renovar a cada 30s via heartbeat
// ─────────────────────────────────────────────

const PRESENCE_TTL = 60 // segundos

export type PresenceStatus = 'ONLINE' | 'IDLE' | 'DND' | 'INVISIBLE'

export const presenceKeys = {
  user: (userId: string) => `presence:user:${userId}`,
}

/** Stores chosen status. INVISIBLE = aparece offline pros outros mas socket conectado. */
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
  // INVISIBLE tratado como offline pro mundo externo (a chamada interna decide se filtra)
  return v as PresenceStatus
}

export async function isUserOnline(userId: string): Promise<boolean> {
  const result = await redis.exists(presenceKeys.user(userId))
  return result === 1
}

// ─────────────────────────────────────────────
// HELPERS DE REFRESH TOKEN (blacklist)
// Armazena tokens revogados pelo tempo restante de expiração
// ─────────────────────────────────────────────

export async function blacklistToken(jti: string, expiresInSeconds: number): Promise<void> {
  await redis.setex(`blacklist:token:${jti}`, expiresInSeconds, '1')
}

export async function isTokenBlacklisted(jti: string): Promise<boolean> {
  const result = await redis.exists(`blacklist:token:${jti}`)
  return result === 1
}
