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

// Redis aqui é cache + presença — NUNCA crítico. Se o servidor estiver fora ou
// capado (ex.: limite de requests do plano free do Upstash), o comando rejeita
// com um ReplyError POR-COMANDO — que o `redis.on('error')` (só nível de conexão)
// NÃO captura. Uma dessas rejeições sem catch (ex.: o refreshPresence fire-and-
// forget do heartbeat do socket) virava unhandledRejection -> process.exit(1) ->
// crash-loop, e a API INTEIRA caía por causa do cache. Por isso todo helper aqui
// é à prova de falha: swallow + fallback seguro. Redis fora = presença/cache off,
// mas a API segue no ar. O /health ainda reporta redis:{ok:false} (visibilidade).
export async function setUserOnline(userId: string, status: PresenceStatus = 'ONLINE'): Promise<void> {
  try { await redis.setex(presenceKeys.user(userId), PRESENCE_TTL, status) } catch { /* cache off */ }
}

export async function setUserOffline(userId: string): Promise<void> {
  try { await redis.del(presenceKeys.user(userId)) } catch { /* cache off */ }
}

export async function refreshPresence(userId: string): Promise<void> {
  try { await redis.expire(presenceKeys.user(userId), PRESENCE_TTL) } catch { /* cache off */ }
}

export async function getUserStatus(userId: string): Promise<PresenceStatus | null> {
  try {
    const v = await redis.get(presenceKeys.user(userId))
    return (v as PresenceStatus | null) ?? null
  } catch {
    return null
  }
}

export async function isUserOnline(userId: string): Promise<boolean> {
  try {
    return (await redis.exists(presenceKeys.user(userId))) === 1
  } catch {
    return false
  }
}

// Cache EM PROCESSO do resultado de isTokenBlacklisted. Esse check rodava um
// comando Redis em TODA request autenticada (auth.ts) — de longe o maior
// consumidor da quota do Upstash (o que estourou os 500k). Com um TTL curto,
// leituras repetidas do mesmo jti (o caso comum: um usuário faz N requests com o
// mesmo token) viram 1 comando a cada BLACKLIST_CACHE_MS. blacklistToken atualiza
// o cache local na hora -> logout instantâneo nesta instância (no free tier é 1
// instância só). Miss propaga a revogação em no máx BLACKLIST_CACHE_MS (aceitável:
// o access token já tem exp curto próprio).
const BLACKLIST_CACHE_MS = 30_000
const BLACKLIST_CACHE_MAX = 10_000
const blacklistCache = new Map<string, { revoked: boolean; exp: number }>()

function rememberBlacklist(jti: string, revoked: boolean, ttlMs: number): void {
  // Poda preguiçosa: só quando o mapa cresce, varre e tira os expirados (evita
  // vazamento de memória em uptime longo sem precisar de um timer).
  if (blacklistCache.size > BLACKLIST_CACHE_MAX) {
    const now = Date.now()
    for (const [k, v] of blacklistCache) if (v.exp <= now) blacklistCache.delete(k)
  }
  blacklistCache.set(jti, { revoked, exp: Date.now() + ttlMs })
}

export async function blacklistToken(jti: string, expiresInSeconds: number): Promise<void> {
  rememberBlacklist(jti, true, expiresInSeconds * 1000) // logout instantâneo local
  try { await redis.setex(`blacklist:token:${jti}`, expiresInSeconds, '1') } catch { /* best-effort */ }
}

// FAIL-OPEN (decisão do dono): sem Redis, trata o token como NÃO-revogado -> a API
// segue no ar em vez de deslogar todo mundo quando o cache cai. Trade-off aceito:
// um token revogado ainda vale até expirar sozinho (TTL curto). Blacklist é
// best-effort por design (o token já tem exp curto próprio).
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
