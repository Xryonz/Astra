import rateLimit from 'express-rate-limit'

// ─────────────────────────────────────────────
// AUTH — restrito: previne brute force
// ─────────────────────────────────────────────
export const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutos
  max: 10,                   // 10 tentativas por janela
  message: { error: 'Muitas tentativas. Tente novamente em 15 minutos.' },
  standardHeaders: true,
  legacyHeaders: false,
})

// ─────────────────────────────────────────────
// MENSAGENS — mais permissivo, mas com limite
// ─────────────────────────────────────────────
export const messageLimiter = rateLimit({
  windowMs: 10 * 1000, // 10 segundos
  max: 20,             // 20 mensagens por 10s (2/s)
  message: { error: 'Você está enviando mensagens rápido demais.' },
  standardHeaders: true,
  legacyHeaders: false,
})

// ─────────────────────────────────────────────
// GERAL — proteção global de API
// ─────────────────────────────────────────────
export const globalLimiter = rateLimit({
  windowMs: 60 * 1000, // 1 minuto
  max: 200,
  message: { error: 'Muitas requisições. Aguarde um momento.' },
  standardHeaders: true,
  legacyHeaders: false,
})
