import rateLimit, { type Options } from 'express-rate-limit'
import { redis } from '../lib/redis'
import type { Request, Response } from 'express'

function userOrIpKey(req: Request, _res: Response): string {
  const userId = (req as any).userId
  if (userId) return `u:${userId}`

  return `ip:${req.ip ?? 'unknown'}`
}

class RedisStore {
  prefix: string
  windowMs!: number
  // Fallback em memoria (por processo) pra quando o Redis cair.
  private mem = new Map<string, { count: number; reset: number }>()

  constructor(prefix: string) { this.prefix = prefix }
  init(opts: Options) { this.windowMs = opts.windowMs }

  async increment(key: string): Promise<{ totalHits: number; resetTime: Date }> {
    const k = `rl:${this.prefix}:${key}`
    try {

      const multi = redis.multi()
      multi.incr(k)
      multi.pexpire(k, this.windowMs, 'NX' as any)
      multi.pttl(k)
      const res = await multi.exec()
      const hits = Number(res?.[0]?.[1] ?? 1)
      const ttl  = Number(res?.[2]?.[1] ?? this.windowMs)
      return { totalHits: hits, resetTime: new Date(Date.now() + ttl) }
    } catch {
      // Redis fora (queda/cota Upstash): NAO liberar geral — o fail-open deixava
      // brute-force de login passar. Conta numa janela em memoria do processo ->
      // fail-CLOSED. Render free = 1 instancia, entao cobre o caso real.
      const now = Date.now()
      const e = this.mem.get(k)
      if (!e || e.reset <= now) {
        if (this.mem.size > 10_000) for (const [mk, v] of this.mem) if (v.reset <= now) this.mem.delete(mk)
        this.mem.set(k, { count: 1, reset: now + this.windowMs })
        return { totalHits: 1, resetTime: new Date(now + this.windowMs) }
      }
      e.count++
      return { totalHits: e.count, resetTime: new Date(e.reset) }
    }
  }

  async decrement(key: string): Promise<void> {
    try { await redis.decr(`rl:${this.prefix}:${key}`) } catch {}
  }

  async resetKey(key: string): Promise<void> {
    try { await redis.del(`rl:${this.prefix}:${key}`) } catch {}
  }
}

export const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 10,
  message: { error: 'Muitas tentativas. Tente novamente em 15 minutos.' },
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator: userOrIpKey,
  store: new RedisStore('auth') as any,
})

export const messageLimiter = rateLimit({
  windowMs: 10 * 1000,
  max: 20,
  message: { error: 'Você está enviando mensagens rápido demais.' },
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator: userOrIpKey,
  store: new RedisStore('msg') as any,
})

export const globalLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 200,
  message: { error: 'Muitas requisições. Aguarde um momento.' },
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator: userOrIpKey,
  store: new RedisStore('global') as any,
})
