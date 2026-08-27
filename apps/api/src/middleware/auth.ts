import { Request, Response, NextFunction } from 'express'
import { eq } from 'drizzle-orm'
import { verifyAccessToken } from '../lib/jwt'
import { isTokenBlacklisted } from '../lib/redis'
import { isMailEnabled } from '../lib/mailer'
import { db } from '../db'
import { users } from '../db/schema'

declare global {
  namespace Express {
    interface Request {
      userId?: string
      jti?: string
    }
  }
}

function extractBearer(authHeader: string | undefined): string | null {
  if (!authHeader) return null

  const match = authHeader.match(/^Bearer\s+(\S+)$/i)
  return match ? match[1] : null
}

export async function requireAuth(req: Request, res: Response, next: NextFunction) {
  const token = extractBearer(req.headers.authorization)

  if (!token) {
    return res.status(401).json({ error: 'Token não fornecido', code: 'NO_TOKEN' })
  }

  try {
    const payload = verifyAccessToken(token)

    const revoked = await isTokenBlacklisted(payload.jti)
    if (revoked) {
      return res.status(401).json({ error: 'Token revogado', code: 'TOKEN_REVOKED' })
    }

    req.userId = payload.userId
    req.jti    = payload.jti
    next()
  } catch (err: any) {

    const isExpired = err?.name === 'TokenExpiredError'
    return res.status(401).json({
      error: isExpired ? 'Token expirado' : 'Token inválido',
      code:  isExpired ? 'TOKEN_EXPIRED'  : 'TOKEN_INVALID',
    })
  }
}

export async function requireEmailVerified(req: Request, res: Response, next: NextFunction) {
  if (!isMailEnabled()) return next()

  const [user] = await db.select({ emailVerifiedAt: users.emailVerifiedAt })
    .from(users).where(eq(users.id, req.userId!)).limit(1)

  if (!user) return res.status(401).json({ error: 'Sessão inválida', code: 'NO_USER' })
  if (user.emailVerifiedAt) return next()

  return res.status(403).json({
    error: 'Confirme seu e-mail para continuar',
    code:  'EMAIL_NOT_VERIFIED',
  })
}

