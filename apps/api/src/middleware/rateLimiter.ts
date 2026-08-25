import rateLimit from 'express-rate-limit'
import type { Request, Response } from 'express'

function userOrIpKey(req: Request, _res: Response): string {
  const userId = (req as any).userId
  if (userId) return `u:${userId}`

  return `ip:${req.ip ?? 'unknown'}`
}

export const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 10,
  message: { error: 'Muitas tentativas. Tente novamente em 15 minutos.' },
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator: userOrIpKey,
})

export const uploadLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 20,
  message: { error: 'Muitos uploads. Aguarde um momento.' },
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator: userOrIpKey,
})

export const messageLimiter = rateLimit({
  windowMs: 10 * 1000,
  max: 20,
  message: { error: 'Você está enviando mensagens rápido demais.' },
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator: userOrIpKey,
})

export const globalLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 200,
  message: { error: 'Muitas requisições. Aguarde um momento.' },
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator: userOrIpKey,
})
