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

export const activityKeys = {
  user: (userId: string) => `activity:user:${userId}`,
}

export const vozKeys = {
  membro: (channelId: string, userId: string) => `voz:${channelId}:${userId}`,
  daSala: (channelId: string) => `voz:${channelId}:*`,
}

export const VOZ_TTL_SEGUNDOS = 60

export type Atividade = { texto: string; desde: number }

export function leAtividade(cru: string | null | undefined): Atividade | null {
  if (!cru) return null
  const corte = cru.indexOf('|')
  if (corte < 0) return { texto: cru, desde: Date.now() }
  const texto = cru.slice(corte + 1)
  if (!texto) return null
  const desde = Number(cru.slice(0, corte))
  return { texto, desde: Number.isFinite(desde) && desde > 0 ? desde : Date.now() }
}

export async function setUserActivity(userId: string, texto: string): Promise<Atividade | null> {
  try {
    const chave = activityKeys.user(userId)
    const anterior = leAtividade(await redis.get(chave))
    const desde = anterior?.texto === texto ? anterior.desde : Date.now()
    await redis.setex(chave, PRESENCE_TTL, `${desde}|${texto}`)
    return { texto, desde }
  } catch {
    return { texto, desde: Date.now() }
  }
}

export async function clearUserActivity(userId: string): Promise<void> {
  try { await redis.del(activityKeys.user(userId)) } catch {  }
}

export async function setUserOnline(userId: string, status: PresenceStatus = 'ONLINE'): Promise<void> {
  try { await redis.setex(presenceKeys.user(userId), PRESENCE_TTL, status) } catch {  }
}

export async function setUserOffline(userId: string): Promise<void> {
  try { await redis.del(presenceKeys.user(userId)) } catch {  }
}

export async function refreshPresence(userId: string): Promise<void> {
  try { await redis.expire(presenceKeys.user(userId), PRESENCE_TTL) } catch {  }
}

export async function getUserStatus(userId: string): Promise<PresenceStatus | null> {
  try {
    const v = await redis.get(presenceKeys.user(userId))
    return (v as PresenceStatus | null) ?? null
  } catch {
    return null
  }
}

const BLACKLIST_CACHE_MS = 30_000
const BLACKLIST_CACHE_MAX = 10_000
const blacklistCache = new Map<string, { revoked: boolean; exp: number }>()

function rememberBlacklist(jti: string, revoked: boolean, ttlMs: number): void {
  if (blacklistCache.size > BLACKLIST_CACHE_MAX) {
    const now = Date.now()
    for (const [k, v] of blacklistCache) if (v.exp <= now) blacklistCache.delete(k)
  }
  blacklistCache.set(jti, { revoked, exp: Date.now() + ttlMs })
}

export async function blacklistToken(jti: string, expiresInSeconds: number): Promise<void> {
  rememberBlacklist(jti, true, expiresInSeconds * 1000) 
  try { await redis.setex(`blacklist:token:${jti}`, expiresInSeconds, '1') } catch {  }
}

export async function isTokenBlacklisted(jti: string): Promise<boolean> {
  const cached = blacklistCache.get(jti)
  if (cached && cached.exp > Date.now()) return cached.revoked
  try {
    const revoked = (await redis.exists(`blacklist:token:${jti}`)) === 1
    rememberBlacklist(jti, revoked, BLACKLIST_CACHE_MS)
    return revoked
  } catch {
    return false
  }
}
