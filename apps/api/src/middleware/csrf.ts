import { Request, Response, NextFunction } from 'express'
import { env } from '../lib/env'
import { isAllowedOrigin } from '../lib/allowedOrigins'

const LOCALHOST_RE = /^http:\/\/(localhost|127\.0\.0\.1):\d+$/

export function requireSameOrigin(req: Request, res: Response, next: NextFunction) {
  const origin  = req.headers.origin
  const referer = req.headers.referer
  const expected = env.CLIENT_URL

  if (isAllowedOrigin(origin)) return next()

  if (!origin && referer) {
    if (referer.startsWith(expected)) return next()
    if (env.NODE_ENV === 'development' && LOCALHOST_RE.test(referer.split('/').slice(0, 3).join('/'))) {
      return next()
    }
  }

  return res.status(403).json({ error: 'Origin inválido', code: 'CSRF_BLOCKED' })
}
