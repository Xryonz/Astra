import rateLimit from 'express-rate-limit'
import type { Request, Response } from 'express'

function userOrIpKey(req: Request, _res: Response): string {
  const userId = (req as any).userId
  if (userId) return `u:${userId}`

  return `ip:${req.ip ?? 'unknown'}`
}

// Store EM MEMORIA (default do express-rate-limit, sem `store`). Antes era backed
// no Redis: cada request pagava 3 comandos (INCR+PEXPIRE+PTTL) e o globalLimiter
// roda em TODA request — multiplicado pelos polls de fundo (presenca de voz 5-10s,
// notificacoes 60s), isso sozinho estourava os 500k comandos/mes do Upstash free e
// derrubava o chat de canal. No Render free e 1 INSTANCIA so, entao um contador
// distribuido no Redis nao agrega correcao nenhuma sobre o contador em processo.
// Trade-off: o contador zera num restart (cosmetico — janela nova, nao brecha de
// seguranca) e nao sincroniza entre instancias (nao ha mais de uma hoje; revisitar
// se um dia escalar horizontalmente). Zero comando Redis, mesma protecao.
export const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 10,
  message: { error: 'Muitas tentativas. Tente novamente em 15 minutos.' },
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator: userOrIpKey,
})

// Upload: cap dedicado por usuario/IP. Cada request pode trazer 10 arquivos de
// 25MB (memoryStorage) -> sem isto, um cliente martelava /upload e estourava a
// RAM da instancia. 20/min segura o burst sem atrapalhar uso normal.
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
