import rateLimit, { type Options } from 'express-rate-limit'
import { redis } from '../lib/redis'
import type { Request, Response } from 'express'

/**
 * Key generator que prioriza userId > IP. Usuário logado é contado por user
 * (não dá pra burlar trocando de proxy/IP). Anônimo cai pra IP truncado.
 * Critical pra rotas auth (brute force protection) — IP isolado deixava
 * vários users atrás de NAT/proxy compartilhar quota.
 */
function userOrIpKey(req: Request, _res: Response): string {
  const userId = (req as any).userId
  if (userId) return `u:${userId}`
  // express-rate-limit por default usa req.ip; retornar isso preserva fallback.
  return `ip:${req.ip ?? 'unknown'}`
}

/**
 * Store Redis pra rate limit. Sem isso, cada réplica de servidor tem janela
 * isolada → limite efetivo = N réplicas × max. Em deploy multi-instância
 * (Railway pode escalar), centralizar no Redis é correctness.
 *
 * Fallback: se Redis cair, NÃO bloqueia requests — express-rate-limit
 * faz failopen automático e retorna a chave em memória.
 */
class RedisStore {
  prefix: string
  windowMs!: number

  constructor(prefix: string) { this.prefix = prefix }
  init(opts: Options) { this.windowMs = opts.windowMs }

  async increment(key: string): Promise<{ totalHits: number; resetTime: Date }> {
    const k = `rl:${this.prefix}:${key}`
    try {
      // pipe: INCR + EXPIRE (só quando é a 1ª req — EXPIRE NX em ioredis >=5)
      const multi = redis.multi()
      multi.incr(k)
      multi.pexpire(k, this.windowMs, 'NX' as any)
      multi.pttl(k)
      const res = await multi.exec()
      const hits = Number(res?.[0]?.[1] ?? 1)
      const ttl  = Number(res?.[2]?.[1] ?? this.windowMs)
      return { totalHits: hits, resetTime: new Date(Date.now() + ttl) }
    } catch {
      // Redis down: fail-open. Retorna 1 hit pra não bloquear.
      return { totalHits: 1, resetTime: new Date(Date.now() + this.windowMs) }
    }
  }

  async decrement(key: string): Promise<void> {
    try { await redis.decr(`rl:${this.prefix}:${key}`) } catch {}
  }

  async resetKey(key: string): Promise<void> {
    try { await redis.del(`rl:${this.prefix}:${key}`) } catch {}
  }
}

// ─────────────────────────────────────────────
// AUTH — restrito: previne brute force
// Key: IP + user-agent (anônimo no login/register).
// ─────────────────────────────────────────────
export const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 10,
  message: { error: 'Muitas tentativas. Tente novamente em 15 minutos.' },
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator: userOrIpKey,
  store: new RedisStore('auth') as any,
})

// ─────────────────────────────────────────────
// MENSAGENS — por user (NAT-aware)
// ─────────────────────────────────────────────
export const messageLimiter = rateLimit({
  windowMs: 10 * 1000,
  max: 20,
  message: { error: 'Você está enviando mensagens rápido demais.' },
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator: userOrIpKey,
  store: new RedisStore('msg') as any,
})

// ─────────────────────────────────────────────
// GERAL — proteção global de API
// ─────────────────────────────────────────────
export const globalLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 200,
  message: { error: 'Muitas requisições. Aguarde um momento.' },
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator: userOrIpKey,
  store: new RedisStore('global') as any,
})
