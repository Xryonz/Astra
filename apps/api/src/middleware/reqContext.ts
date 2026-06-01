/**
 * Per-request context: gera/propaga X-Request-Id e seta AsyncLocalStorage
 * pro logger anexar o reqId em toda linha durante o handling.
 *
 * Honra header X-Request-Id se cliente mandou (útil pra correlation com proxy/CDN).
 */
import type { Request, Response, NextFunction } from 'express'
import crypto from 'crypto'
import { runWithLogContext } from '../lib/logger'

declare global {
  namespace Express {
    interface Request {
      reqId?: string
    }
  }
}

export function reqContext(req: Request, res: Response, next: NextFunction): void {
  const incoming = req.header('x-request-id')
  // Aceita só ids "safe" (alphanum + dash) pra evitar log injection
  const reqId = incoming && /^[a-zA-Z0-9-]{1,64}$/.test(incoming)
    ? incoming
    : crypto.randomBytes(8).toString('hex')

  req.reqId = reqId
  res.setHeader('x-request-id', reqId)

  runWithLogContext({ reqId, userId: req.userId }, () => next())
}
